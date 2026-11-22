package br.remoto.core.engine.legacy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converte entre o campo {@code legacy} do cenário (o mapa genérico lido do JSON) e
 * {@link LegacyConfig}.
 *
 * <p>Os números decimais viram {@code BigDecimal}, pelo mesmo {@code BigDecimal.valueOf} que o
 * leitor de JSON usava; inteiros, textos e nulos ficam como estão. Assim, gravar um cenário que
 * não foi editado devolve o mesmo JSON.
 */
public final class LegacyJson {

    private static final Set<String> KEYS = Set.of("format", "idConfiguration", "tables", "settings");
    private static final Set<String> TABLE_KEYS = Set.of("columns", "rows");

    private LegacyJson() {
    }

    /** @throws IllegalArgumentException se o campo não existe, tem outro formato ou está malformado */
    public static LegacyConfig toConfig(Map<String, Object> legacy) {
        if (legacy == null) {
            throw new IllegalArgumentException("cenário sem o campo legacy");
        }
        unknownKeys(legacy, KEYS, "legacy");
        Object format = legacy.get("format");
        if (!LegacyConfig.FORMAT.equals(format)) {
            throw new IllegalArgumentException("formato legacy não suportado: " + format);
        }
        Map<String, LegacyConfig.Table> tables = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : map(legacy.get("tables"), "legacy.tables").entrySet()) {
            tables.put(e.getKey(), table(e.getKey(), e.getValue()));
        }
        Map<String, Object> settings = new LinkedHashMap<>();
        map(legacy.get("settings"), "legacy.settings").forEach((k, v) -> settings.put(k, value(v)));
        return new LegacyConfig(LegacyConfig.FORMAT, integer(legacy.get("idConfiguration")), tables, settings);
    }

    /** O campo {@code legacy} do cenário; {@code settings} só aparece quando não está vazio. */
    public static Map<String, Object> toMap(LegacyConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("format", config.format());
        map.put("idConfiguration", config.idConfiguration());
        Map<String, Object> tables = new LinkedHashMap<>();
        config.tables().forEach((name, t) -> {
            Map<String, Object> table = new LinkedHashMap<>();
            table.put("columns", new ArrayList<>(t.columns()));
            List<Object> rows = new ArrayList<>(t.rows().size());
            t.rows().forEach(r -> rows.add(value(r)));
            table.put("rows", rows);
            tables.put(name, table);
        });
        map.put("tables", tables);
        if (!config.settings().isEmpty()) {
            Map<String, Object> settings = new LinkedHashMap<>();
            config.settings().forEach((k, v) -> settings.put(k, value(v)));
            map.put("settings", settings);
        }
        return map;
    }

    private static LegacyConfig.Table table(String name, Object raw) {
        Map<String, Object> t = map(raw, "legacy.tables." + name);
        unknownKeys(t, TABLE_KEYS, "legacy.tables." + name);
        List<String> columns = new ArrayList<>();
        for (Object c : list(t.get("columns"), "colunas de " + name)) {
            columns.add(c == null ? null : c.toString());
        }
        List<List<Object>> rows = new ArrayList<>();
        for (Object r : list(t.get("rows"), "linhas de " + name)) {
            List<Object> row = new ArrayList<>();
            for (Object v : list(r, "linha de " + name)) {
                row.add(value(v));
            }
            rows.add(row);
        }
        return new LegacyConfig.Table(columns, rows);
    }

    /** Decimal vira BigDecimal; listas e mapas são copiados com a mesma regra. */
    private static Object value(Object v) {
        if (v instanceof Double || v instanceof Float) {
            return BigDecimal.valueOf(((Number) v).doubleValue());
        }
        if (v instanceof List<?> l) {
            List<Object> copy = new ArrayList<>(l.size());
            l.forEach(x -> copy.add(value(x)));
            return copy;
        }
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> copy = new LinkedHashMap<>();
            m.forEach((k, x) -> copy.put(String.valueOf(k), value(x)));
            return copy;
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object v, String what) {
        if (v == null) {
            return Map.of();
        }
        if (!(v instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(what + " deveria ser um objeto");
        }
        return (Map<String, Object>) v;
    }

    private static List<?> list(Object v, String what) {
        if (v == null) {
            return List.of();
        }
        if (!(v instanceof List<?> l)) {
            throw new IllegalArgumentException(what + " deveria ser uma lista");
        }
        return l;
    }

    private static int integer(Object v) {
        if (v == null) {
            return 0;
        }
        try {
            return v instanceof Number n ? new BigDecimal(n.toString()).intValueExact()
                    : Integer.parseInt(v.toString().trim());
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("idConfiguration inválido: " + v, e);
        }
    }

    private static void unknownKeys(Map<String, Object> m, Set<String> known, String what) {
        for (String key : m.keySet()) {
            if (!known.contains(key)) {
                throw new IllegalArgumentException("campo desconhecido em " + what + ": " + key);
            }
        }
    }
}
