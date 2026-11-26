package br.remoto.core.protocol;

import br.remoto.core.engine.legacy.LegacyConfig;
import br.remoto.core.engine.legacy.LegacyJson;
import br.remoto.core.scenario.Scenario;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

/**
 * Um parâmetro de um cenário que um protocolo em lote varia, escrito como texto:
 * <ul>
 *   <li>uma célula das tabelas da configuração original: {@code TABELA:CHAVE=VALOR[,CHAVE=VALOR]:COLUNA},
 *   por exemplo {@code NERVE:CD_NERVE=PTN:PEAK} (a intensidade do estímulo do nervo tibial) ou
 *   {@code NEURON_TYPE:CD_NUCLEUS=DT,CD_NEURON_TYPE=CM_ext:MEAN} (o intervalo médio de um trato).
 *   Todas as linhas que batem com as chaves mudam;</li>
 *   <li>um valor do campo settings: {@code settings.} e o caminho, por exemplo
 *   {@code settings.nerveStimulus.PTN.pulseWidth};</li>
 *   <li>{@code seed} (repetições com sorteios diferentes) ou {@code durationMs}.</li>
 * </ul>
 */
public final class ScenarioParameter {

    private enum Kind {
        TABLE, SETTINGS, SEED, DURATION, MAPPED
    }

    private final String spec;
    private final Kind kind;
    private final String table;
    private final Map<String, String> keys;
    private final String column;
    private final List<String> path;
    private final ScenarioParameter target;
    private final DoubleUnaryOperator map;

    private ScenarioParameter(String spec, Kind kind, String table, Map<String, String> keys, String column,
            List<String> path) {
        this(spec, kind, table, keys, column, path, null, null);
    }

    private ScenarioParameter(String spec, Kind kind, String table, Map<String, String> keys, String column,
            List<String> path, ScenarioParameter target, DoubleUnaryOperator map) {
        this.spec = spec;
        this.kind = kind;
        this.table = table;
        this.keys = keys;
        this.column = column;
        this.path = path;
        this.target = target;
        this.map = map;
    }

    /**
     * Um parâmetro derivado: o lote varia o que {@code spec} nomeia (é o que sai na tabela de
     * medidas), e o cenário recebe {@code target} valendo {@code map} desse valor. É o caso do
     * intervalo condicionamento-teste, que no cenário é o instante do pulso condicionante, contado
     * para trás a partir do pulso teste.
     */
    public static ScenarioParameter mapped(String spec, ScenarioParameter target, DoubleUnaryOperator map) {
        if (spec == null || spec.isBlank() || target == null || map == null) {
            throw new IllegalArgumentException("o parâmetro derivado precisa de nome, alvo e função");
        }
        return new ScenarioParameter(spec, Kind.MAPPED, null, null, null, null, target, map);
    }

    /** @throws IllegalArgumentException se o texto não for um parâmetro */
    public static ScenarioParameter parse(String spec) {
        if (spec == null || spec.isBlank()) {
            throw new IllegalArgumentException("parâmetro vazio");
        }
        String s = spec.trim();
        if (s.equals("seed")) {
            return new ScenarioParameter(s, Kind.SEED, null, null, null, null);
        }
        if (s.equals("durationMs")) {
            return new ScenarioParameter(s, Kind.DURATION, null, null, null, null);
        }
        if (s.startsWith("settings.")) {
            List<String> path = List.of(s.substring("settings.".length()).split("\\."));
            if (path.stream().anyMatch(String::isBlank)) {
                throw new IllegalArgumentException("caminho do settings inválido: " + s);
            }
            return new ScenarioParameter(s, Kind.SETTINGS, null, null, null, path);
        }
        String[] parts = s.split(":");
        if (parts.length != 3 || parts[0].isBlank() || parts[2].isBlank()) {
            throw new IllegalArgumentException("parâmetro inválido: " + s + " (use TABELA:CHAVE=VALOR:COLUNA,"
                    + " settings.caminho, seed ou durationMs)");
        }
        Map<String, String> keys = new LinkedHashMap<>();
        for (String pair : parts[1].split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length != 2 || kv[0].isBlank()) {
                throw new IllegalArgumentException("chave inválida em " + s + ": " + pair);
            }
            keys.put(kv[0].trim(), kv[1].trim());
        }
        return new ScenarioParameter(s, Kind.TABLE, parts[0].trim(), keys, parts[2].trim(), null);
    }

    /** O parâmetro como foi escrito. */
    public String spec() {
        return spec;
    }

    /** O cenário com o parâmetro valendo {@code value}. */
    public Scenario apply(Scenario scenario, double value) {
        return switch (kind) {
            case SEED -> scenario.withSeed(Math.round(value));
            case DURATION -> scenario.withDuration(value);
            case MAPPED -> target.apply(scenario, map.applyAsDouble(value));
            case TABLE, SETTINGS -> withLegacy(scenario, legacy(scenario, value));
        };
    }

    /** O valor atual do parâmetro no cenário (na primeira linha que bate, numa célula de tabela). */
    public Object read(Scenario scenario) {
        return switch (kind) {
            case SEED -> scenario.seed();
            case DURATION -> scenario.durationMs();
            case MAPPED -> target.read(scenario);
            case SETTINGS -> {
                Object node = config(scenario).settings();
                for (String key : path) {
                    node = node instanceof Map<?, ?> m ? m.get(key) : null;
                }
                yield node;
            }
            case TABLE -> {
                LegacyConfig.Table t = table(config(scenario));
                int c = column(t);
                for (List<Object> row : t.rows()) {
                    if (matches(t, row)) {
                        yield row.get(c);
                    }
                }
                throw noRow(t);
            }
        };
    }

    private LegacyConfig legacy(Scenario scenario, double value) {
        LegacyConfig config = config(scenario);
        if (kind == Kind.SETTINGS) {
            Map<String, Object> settings = copy(config.settings());
            Map<String, Object> node = settings;
            for (int i = 0; i < path.size() - 1; i++) {
                Object next = node.get(path.get(i));
                Map<String, Object> child = next instanceof Map<?, ?> m ? copy(m) : new LinkedHashMap<>();
                node.put(path.get(i), child);
                node = child;
            }
            node.put(path.get(path.size() - 1), value);
            return new LegacyConfig(config.format(), config.idConfiguration(), config.tables(), settings);
        }
        LegacyConfig.Table t = table(config);
        int c = column(t);
        List<List<Object>> rows = new ArrayList<>();
        boolean any = false;
        for (List<Object> row : t.rows()) {
            List<Object> copy = new ArrayList<>(row);
            if (matches(t, row)) {
                copy.set(c, number(row.get(c), value));
                any = true;
            }
            rows.add(copy);
        }
        if (!any) {
            throw noRow(t);
        }
        Map<String, LegacyConfig.Table> tables = new LinkedHashMap<>(config.tables());
        tables.put(table, new LegacyConfig.Table(t.columns(), rows));
        return new LegacyConfig(config.format(), config.idConfiguration(), tables, config.settings());
    }

    private static LegacyConfig config(Scenario scenario) {
        if (scenario.legacy() == null) {
            throw new IllegalArgumentException("o cenário não tem a configuração do motor original");
        }
        return LegacyJson.toConfig(scenario.legacy());
    }

    private static Scenario withLegacy(Scenario s, LegacyConfig config) {
        return new Scenario(s.id(), s.name(), s.description(), s.schemaVersion(), s.seed(), s.durationMs(), s.stepMs(),
                s.engine(), LegacyJson.toMap(config));
    }

    private LegacyConfig.Table table(LegacyConfig config) {
        LegacyConfig.Table t = config.tables().get(table);
        if (t == null) {
            throw new IllegalArgumentException("tabela desconhecida: " + table + " (há " + config.tables().keySet() + ")");
        }
        for (String key : keys.keySet()) {
            if (!t.columns().contains(key)) {
                throw new IllegalArgumentException("a tabela " + table + " não tem a coluna " + key
                        + " (há " + t.columns() + ")");
            }
        }
        return t;
    }

    private int column(LegacyConfig.Table t) {
        int c = t.columns().indexOf(column);
        if (c < 0) {
            throw new IllegalArgumentException("a tabela " + table + " não tem a coluna " + column
                    + " (há " + t.columns() + ")");
        }
        return c;
    }

    private boolean matches(LegacyConfig.Table t, List<Object> row) {
        for (Map.Entry<String, String> key : keys.entrySet()) {
            Object cell = row.get(t.columns().indexOf(key.getKey()));
            if (!same(cell, key.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static boolean same(Object cell, String text) {
        if (cell instanceof Number n) {
            try {
                return n.doubleValue() == Double.parseDouble(text);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return String.valueOf(cell).equals(text);
    }

    private IllegalArgumentException noRow(LegacyConfig.Table t) {
        return new IllegalArgumentException("nenhuma linha da tabela " + table + " tem " + keys);
    }

    /** Um inteiro continua inteiro quando o valor é inteiro (a coluna QUANTITY, por exemplo). */
    private static Object number(Object old, double value) {
        if (old instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue()) && value == Math.rint(value)
                && Math.abs(value) < Integer.MAX_VALUE) {
            return (int) value;
        }
        return value;
    }

    private static Map<String, Object> copy(Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (map != null) {
            map.forEach((k, v) -> copy.put(String.valueOf(k), v));
        }
        return copy;
    }
}
