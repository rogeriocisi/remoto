package br.remoto.legacy;

import br.remoto.core.scenario.Scenario;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Lê o banco HSQLDB do ReMoto original e exporta configurações como cenários JSON, sem o HSQLDB.
 *
 * <p>O banco é um arquivo de texto ({@code remoto.script}) com o {@code CREATE TABLE} de cada
 * tabela e um {@code INSERT} por linha, na ordem em que o banco as percorria. Cada valor vira o que
 * o JDBC devolvia ({@code Integer}, {@code BigDecimal}, texto, data e hora em texto), e as linhas
 * de cada tabela saem na ordem do arquivo, como saíam do {@code SELECT}. O arquivo nunca é
 * alterado, e a tabela USER (dados pessoais) nunca é exportada.
 */
public final class LegacyDatabase implements AutoCloseable {

    /** Tabelas compartilhadas por todas as configurações. */
    static final Set<String> GLOBAL_TABLES = Set.of("CATEGORY", "NUCLEUS", "JOINTS", "MU_POSITION");
    /** Nunca exportada (dados pessoais). */
    static final Set<String> SKIPPED_TABLES = Set.of("USER");

    public record ConfigurationInfo(int id, String owner, String name, String description,
                                    double durationMs, double stepMs) {
    }

    private record Table(String name, List<String> columns, List<String> types, List<List<Object>> rows) {
    }

    /** Em ordem de nome, como o banco listava as tabelas. */
    private final Map<String, Table> tables;

    private LegacyDatabase(Map<String, Table> tables) {
        this.tables = tables;
    }

    /**
     * @param databasePath caminho sem extensão, por exemplo {@code legacy-db/remoto}
     */
    public static LegacyDatabase open(Path databasePath) {
        Path script = Path.of(databasePath + ".script");
        if (!Files.isRegularFile(script)) {
            throw new IllegalArgumentException("banco legado não encontrado: " + script);
        }
        try {
            return new LegacyDatabase(parse(Files.readAllLines(script, StandardCharsets.ISO_8859_1)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<ConfigurationInfo> configurations() {
        Table t = table("CONFIGURATION");
        List<ConfigurationInfo> list = new ArrayList<>();
        for (List<Object> row : t.rows()) {
            Map<String, Object> r = named(t, row);
            list.add(new ConfigurationInfo(((Number) r.get("ID_CONFIGURATION")).intValue(), (String) r.get("CD_USER"),
                    (String) r.get("NM_CONFIGURATION"), (String) r.get("DS_CONFIGURATION"),
                    number(r.get("TFIN")), number(r.get("STEP"))));
        }
        list.sort(Comparator.comparingInt(ConfigurationInfo::id));
        return list;
    }

    public LegacyConfig export(int idConfiguration) {
        Map<String, LegacyConfig.Table> exported = new LinkedHashMap<>();
        for (Table t : tables.values()) {
            if (SKIPPED_TABLES.contains(t.name())) {
                continue;
            }
            int id = t.columns().indexOf("ID_CONFIGURATION");
            if (!GLOBAL_TABLES.contains(t.name()) && id < 0) {
                continue; // tabela que não pertence a nenhuma configuração
            }
            List<List<Object>> rows = new ArrayList<>();
            for (List<Object> row : t.rows()) {
                if (GLOBAL_TABLES.contains(t.name()) || ((Number) row.get(id)).intValue() == idConfiguration) {
                    rows.add(jsonRow(row));
                }
            }
            exported.put(t.name(), new LegacyConfig.Table(t.columns(), rows));
        }
        if (!exported.containsKey("CONFIGURATION") || exported.get("CONFIGURATION").rows().isEmpty()) {
            throw new IllegalArgumentException("configuração " + idConfiguration + " não existe no banco legado");
        }
        return new LegacyConfig(LegacyConfig.FORMAT, idConfiguration, exported);
    }

    /** Cenário pronto para o site ou a CLI, com a duração e o passo originais. */
    public Scenario exportScenario(int idConfiguration) {
        ConfigurationInfo info = configurations().stream()
                .filter(c -> c.id() == idConfiguration)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("configuração " + idConfiguration + " não existe"));
        String description = (info.description() == null || info.description().isBlank())
                ? "Configuração " + idConfiguration + " do ReMoto original."
                : info.description();
        return new Scenario(null, info.name(), description, Scenario.CURRENT_SCHEMA_VERSION, idConfiguration,
                info.durationMs(), info.stepMs(), List.of(), Scenario.ENGINE_LEGACY,
                LegacyJson.toMap(export(idConfiguration)));
    }

    @Override
    public void close() {
        // nada a liberar: o arquivo é lido inteiro na abertura
    }

    private Table table(String name) {
        Table t = tables.get(name);
        if (t == null) {
            throw new IllegalStateException("tabela " + name + " ausente no banco legado");
        }
        return t;
    }

    private static Map<String, Object> named(Table t, List<Object> row) {
        Map<String, Object> r = new LinkedHashMap<>();
        for (int i = 0; i < t.columns().size(); i++) {
            r.put(t.columns().get(i), row.get(i));
        }
        return r;
    }

    private static double number(Object value) {
        return value == null ? 0 : ((Number) value).doubleValue();
    }

    /** Como o export pelo JDBC gravava cada valor no JSON. */
    private static List<Object> jsonRow(List<Object> row) {
        List<Object> json = new ArrayList<>(row.size());
        for (Object value : row) {
            if (value instanceof BigDecimal bd) {
                json.add(bd.stripTrailingZeros().scale() <= 0 ? bd.setScale(0) : bd.stripTrailingZeros());
            } else {
                json.add(value);
            }
        }
        return json;
    }

    // ---- leitura do remoto.script ---------------------------------------------------------------

    private static Map<String, Table> parse(List<String> lines) {
        Map<String, Table> tables = new TreeMap<>();
        String create = "CREATE MEMORY TABLE ";
        String insert = "INSERT INTO ";
        for (String line : lines) {
            if (line.startsWith(create)) {
                int open = line.indexOf('(');
                String name = unqualified(line.substring(create.length(), open).trim());
                List<String> columns = new ArrayList<>();
                List<String> types = new ArrayList<>();
                for (String part : splitTopLevel(line.substring(open + 1, line.lastIndexOf(')')))) {
                    part = part.trim();
                    if (part.startsWith("PRIMARY KEY") || part.startsWith("FOREIGN KEY")
                            || part.startsWith("UNIQUE") || part.startsWith("CONSTRAINT")) {
                        continue;
                    }
                    String[] tokens = part.split("\\s+", 3);
                    columns.add(tokens[0]);
                    types.add(tokens[1].replaceAll("\\(.*", ""));
                }
                tables.put(name, new Table(name, List.copyOf(columns), List.copyOf(types), new ArrayList<>()));
            } else if (line.startsWith(insert)) {
                int values = line.indexOf(" VALUES(");
                String name = unqualified(line.substring(insert.length(), values).trim());
                Table t = tables.get(name);
                if (t == null) {
                    throw new IllegalStateException("INSERT numa tabela sem CREATE TABLE: " + name);
                }
                List<String> tokens = values(line.substring(values + " VALUES(".length(), line.lastIndexOf(')')));
                if (tokens.size() != t.columns().size()) {
                    throw new IllegalStateException("linha de " + name + " com " + tokens.size() + " valores");
                }
                List<Object> row = new ArrayList<>(tokens.size());
                for (int i = 0; i < tokens.size(); i++) {
                    row.add(value(t.types().get(i), tokens.get(i)));
                }
                t.rows().add(row);
            }
        }
        return tables;
    }

    private static String unqualified(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(dot + 1);
    }

    /** O que o {@code getObject} do JDBC devolvia para cada tipo de coluna. */
    private static Object value(String type, String token) {
        if (token.equals("NULL")) {
            return null;
        }
        if (token.startsWith("'")) {
            String text = token.substring(1, token.length() - 1).replace("''", "'");
            return type.equals("TIMESTAMP") ? Timestamp.valueOf(text).toString() : text;
        }
        return switch (type) {
            case "INTEGER", "SMALLINT", "TINYINT" -> Integer.valueOf(token);
            case "BIGINT" -> Long.valueOf(token);
            case "NUMERIC", "DECIMAL" -> new BigDecimal(token);
            case "DOUBLE", "FLOAT", "REAL" -> Double.valueOf(token);
            case "BOOLEAN" -> Boolean.valueOf(token);
            default -> token;
        };
    }

    /** Os valores de um VALUES(...): números, NULL e textos entre aspas (com '' para a aspa). */
    private static List<String> values(String text) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int start = i;
            if (text.charAt(i) == '\'') {
                i++;
                while (true) {
                    if (text.charAt(i) == '\'') {
                        if (i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    i++;
                }
            } else {
                while (i < text.length() && text.charAt(i) != ',') {
                    i++;
                }
            }
            tokens.add(text.substring(start, i).trim());
            i++; // a vírgula
        }
        return tokens;
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
