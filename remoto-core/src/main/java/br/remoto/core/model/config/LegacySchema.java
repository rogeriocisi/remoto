package br.remoto.core.model.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * O esquema do banco HSQLDB do ReMoto original ({@code legacy-schema.sql}: colunas, tipos,
 * {@code NOT NULL} e chave primária de cada tabela) e o que o banco fazia ao guardar um valor.
 *
 * <p>Serve para gravar as tabelas de um cenário sem o banco, com o mesmo resultado que um
 * {@code INSERT} seguido de {@code SELECT}: {@code NUMERIC(128,4)} arredonda para 4 casas com a
 * metade para baixo, um decimal numa coluna {@code INTEGER} é truncado, e as linhas voltam na
 * ordem da chave primária (ou na de inserção, nas tabelas sem chave).
 */
public final class LegacySchema {

    public enum Type { TEXT, INTEGER, BIGINT, NUMERIC, TIMESTAMP }

    public record Column(String name, Type type, boolean notNull) {
    }

    /** @param primaryKey colunas da chave primária; vazia nas tabelas sem chave */
    public record Table(String name, List<Column> columns, List<String> primaryKey) {

        public List<String> columnNames() {
            return columns.stream().map(Column::name).toList();
        }

        public int indexOf(String column) {
            for (int i = 0; i < columns.size(); i++) {
                if (columns.get(i).name().equals(column)) {
                    return i;
                }
            }
            return -1;
        }

        /** A tabela pertence a uma configuração (tem ID_CONFIGURATION). */
        public boolean byConfiguration() {
            return indexOf("ID_CONFIGURATION") >= 0;
        }
    }

    private static final Map<String, Table> TABLES = load();

    private LegacySchema() {
    }

    /** As tabelas, em ordem de nome (a ordem em que o export do banco as listava). */
    public static List<Table> tables() {
        return TABLES.values().stream().sorted(Comparator.comparing(Table::name)).toList();
    }

    public static Optional<Table> table(String name) {
        return Optional.ofNullable(TABLES.get(name));
    }

    /**
     * Uma linha como o banco a guardaria e o export a devolveria: os valores convertidos para o
     * tipo de cada coluna, na forma em que vão para o JSON do cenário (texto, {@code Integer},
     * {@code Long}, {@code BigDecimal} sem zeros à direita, data e hora em texto).
     *
     * @param values coluna → valor; as colunas ausentes ficam nulas, como num INSERT sem elas
     * @throws IllegalArgumentException coluna desconhecida, nulo numa coluna NOT NULL ou valor inválido
     */
    public static List<Object> row(Table table, Map<String, ?> values) {
        for (String column : values.keySet()) {
            if (table.indexOf(column) < 0) {
                throw new IllegalArgumentException("coluna desconhecida em " + table.name() + ": " + column);
            }
        }
        List<Object> row = new ArrayList<>(table.columns().size());
        for (Column c : table.columns()) {
            Object value = stored(table, c, values.get(c.name()));
            if (value == null && c.notNull()) {
                throw new IllegalArgumentException("nulo na coluna NOT NULL " + table.name() + "." + c.name());
            }
            row.add(value);
        }
        return row;
    }

    /**
     * As linhas na ordem em que o banco as devolvia: a da chave primária, que é a ordem do índice
     * que ele percorre (texto por código de caractere, números pelo valor, nulo primeiro); nas
     * tabelas sem chave, a ordem de inserção.
     *
     * @throws IllegalArgumentException chave primária repetida, como no INSERT
     */
    public static List<List<Object>> inStoredOrder(Table table, List<List<Object>> rows) {
        List<List<Object>> sorted = new ArrayList<>(rows);
        if (table.primaryKey().isEmpty()) {
            return sorted;
        }
        int[] key = table.primaryKey().stream().mapToInt(table::indexOf).toArray();
        Set<List<Object>> seen = new HashSet<>();
        for (List<Object> row : rows) {
            List<Object> k = new ArrayList<>(key.length);
            for (int i : key) {
                Object v = row.get(i);
                k.add(v instanceof BigDecimal d ? d.stripTrailingZeros() : v);
            }
            if (!seen.add(k)) {
                throw new IllegalArgumentException("chave primária repetida em " + table.name() + ": " + k);
            }
        }
        Comparator<List<Object>> comparator = (a, b) -> 0;
        for (int i : key) {
            comparator = comparator.thenComparing((a, b) -> compareValues(a.get(i), b.get(i)));
        }
        sorted.sort(comparator);
        return sorted;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareValues(Object a, Object b) {
        if (a == null || b == null) {
            return a == null ? (b == null ? 0 : -1) : 1;
        }
        if (a instanceof Number x && b instanceof Number y) {
            return decimal(x).compareTo(decimal(y));
        }
        return ((Comparable) a).compareTo(b);
    }

    private static Object stored(Table table, Column column, Object value) {
        if (value == null) {
            return null;
        }
        try {
            return switch (column.type()) {
                case TEXT -> value instanceof BigDecimal d ? d.toPlainString() : value.toString();
                case INTEGER -> Math.toIntExact(integral(value));
                case BIGINT -> integral(value);
                case NUMERIC -> json(decimal(value).setScale(4, RoundingMode.HALF_DOWN));
                case TIMESTAMP -> timestamp(value);
            };
        } catch (ArithmeticException | IllegalArgumentException e) {
            throw new IllegalArgumentException("valor inválido em " + table.name() + "." + column.name() + ": "
                    + value, e);
        }
    }

    /** Decimal numa coluna inteira: truncado ({@code SQL CONVERT TRUNCATE}). */
    private static long integral(Object value) {
        if (value instanceof Boolean b) {
            return b ? 1 : 0;
        }
        return decimal(value).setScale(0, RoundingMode.DOWN).longValueExact();
    }

    /** O mesmo valor que o JDBC entregava ao banco: {@code setDouble} passa por {@code BigDecimal.valueOf}. */
    private static BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal d) {
            return d;
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return BigDecimal.valueOf(((Number) value).longValue());
        }
        if (value instanceof BigInteger i) {
            return new BigDecimal(i);
        }
        if (value instanceof Double || value instanceof Float) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        if (value instanceof Boolean b) {
            return b ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (value instanceof String s) {
            return new BigDecimal(s.trim());
        }
        throw new IllegalArgumentException("não numérico: " + value);
    }

    /** Como o export do banco gravava um NUMERIC no JSON: sem zeros à direita, inteiro sem casas. */
    private static BigDecimal json(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() <= 0 ? value.setScale(0) : stripped;
    }

    private static String timestamp(Object value) {
        if (value instanceof LocalDateTime t) {
            return Timestamp.valueOf(t).toString();
        }
        if (value instanceof Timestamp t) {
            return t.toString();
        }
        return Timestamp.valueOf(value.toString().trim()).toString();
    }

    // ---- leitura do legacy-schema.sql -----------------------------------------------------------

    private static Map<String, Table> load() {
        String text;
        try (InputStream in = LegacySchema.class.getResourceAsStream("legacy-schema.sql")) {
            if (in == null) {
                throw new IllegalStateException("legacy-schema.sql ausente");
            }
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Map<String, Table> tables = new LinkedHashMap<>();
        for (String line : text.split("\\R")) {
            line = line.trim();
            String prefix = "CREATE MEMORY TABLE ";
            if (!line.startsWith(prefix)) {
                continue;
            }
            int open = line.indexOf('(');
            String name = line.substring(prefix.length(), open).trim();
            String body = line.substring(open + 1, line.lastIndexOf(')'));
            List<Column> columns = new ArrayList<>();
            List<String> primaryKey = new ArrayList<>();
            for (String part : splitTopLevel(body)) {
                part = part.trim();
                if (part.startsWith("PRIMARY KEY(")) {
                    for (String c : part.substring("PRIMARY KEY(".length(), part.length() - 1).split(",")) {
                        primaryKey.add(c.trim());
                    }
                    continue;
                }
                String[] tokens = part.split("\\s+", 2);
                String rest = tokens[1];
                Type type = rest.startsWith("VARCHAR") ? Type.TEXT
                        : rest.startsWith("INTEGER") ? Type.INTEGER
                        : rest.startsWith("BIGINT") ? Type.BIGINT
                        : rest.startsWith("NUMERIC") ? Type.NUMERIC
                        : rest.startsWith("TIMESTAMP") ? Type.TIMESTAMP
                        : null;
                if (type == null) {
                    throw new IllegalStateException("tipo desconhecido em " + name + ": " + part);
                }
                boolean pk = rest.contains("PRIMARY KEY");
                columns.add(new Column(tokens[0], type, rest.contains("NOT NULL") || pk));
                if (pk) {
                    primaryKey.add(tokens[0]);
                }
            }
            tables.put(name, new Table(name, List.copyOf(columns), List.copyOf(primaryKey)));
        }
        return Collections.unmodifiableMap(tables);
    }

    private static List<String> splitTopLevel(String body) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(body.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(body.substring(start));
        return parts;
    }
}
