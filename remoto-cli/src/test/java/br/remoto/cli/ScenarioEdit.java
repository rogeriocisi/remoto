package br.remoto.cli;

import br.remoto.core.engine.legacy.LegacyConfig;
import br.remoto.core.engine.legacy.LegacyJson;
import br.remoto.core.scenario.Scenario;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Uma configuração do banco original sendo mudada para virar um cenário: as tabelas (cópias) e o
 * {@code settings}. Serve aos geradores dos cenários convertidos das versões em Fortran do ReMoto
 * ({@link ProjectFrExamples} e {@link AidpExamples}).
 */
final class ScenarioEdit {

    /** Um cenário e o nome do arquivo. */
    record Example(String file, Scenario scenario) {
    }

    private final Scenario base;
    private final LegacyConfig config;
    private final Map<String, LegacyConfig.Table> tables = new LinkedHashMap<>();
    final Map<String, Object> settings = new LinkedHashMap<>();
    private final Map<String, Integer> tractStd = new LinkedHashMap<>();
    private final Map<String, Object> tractSeries = new LinkedHashMap<>();

    ScenarioEdit(Scenario base) {
        this.base = base;
        this.config = LegacyJson.toConfig(base.legacy());
        config.tables().forEach((name, t) -> {
            List<List<Object>> rows = new ArrayList<>();
            t.rows().forEach(r -> rows.add(new ArrayList<>(r)));
            tables.put(name, new LegacyConfig.Table(t.columns(), rows));
        });
    }

    /** Só estes tipos de neurônio ficam ativos ("núcleo:tipo=quantidade"); os outros, desligados. */
    void active(String... types) {
        set("NEURON_TYPE", Map.of(), "ACTIVE", 0);
        for (String spec : types) {
            String[] nt = spec.split("[:=]");
            Map<String, Object> key = Map.of("CD_NUCLEUS", nt[0], "CD_NEURON_TYPE", nt[1]);
            set("NEURON_TYPE", key, "ACTIVE", 1);
            set("NEURON_TYPE", key, "QUANTITY", Integer.parseInt(nt[2]));
        }
    }

    /**
     * O trato com intervalos num processo gama (a ordem no settings, como o editor grava). O
     * intervalo médio é inteiro, como na coluna MEAN do banco original.
     */
    void gammaTract(String type, int isiMs, int order) {
        Map<String, Object> key = Map.of("CD_NUCLEUS", "DT", "CD_NEURON_TYPE", type);
        set("NEURON_TYPE", key, "DISTRIBUTION", "Gm");
        set("NEURON_TYPE", key, "MEAN", isiMs);
        tractStd.put("DT:" + type, order);
    }

    /**
     * O trato num processo gama com a taxa exata ({@code rateHz} disparos/s), que um intervalo
     * médio inteiro não dá: o intervalo médio 0 e um sinal constante com o alvo "rate", de 0 ao fim.
     */
    void rateTract(String type, double rateHz, int order, double durationMs) {
        gammaTract(type, 0, order);
        Map<String, Object> key = Map.of("CD_NUCLEUS", "DT", "CD_NEURON_TYPE", type);
        set("DESCENDING_COMMANDS", key, "CD_SIGNAL", "constant");
        set("DESCENDING_COMMANDS", key, "MOD_TYPE", "rate");
        set("DESCENDING_COMMANDS", key, "TINI", 0);
        set("DESCENDING_COMMANDS", key, "TFIN", durationMs);
        set("DESCENDING_COMMANDS", key, "AMP", rateHz);
    }

    /**
     * O trato com a taxa de cada axônio seguindo uma série de pontos ({@code points}: tempo (ms) e
     * taxa (disparos/s) alternados), num processo gama da ordem dada: o intervalo médio 0 e a
     * modulação "series" com o alvo "rate", como o editor grava.
     */
    void seriesTract(String type, int order, double[] points, double durationMs) {
        gammaTract(type, 0, order);
        Map<String, Object> key = Map.of("CD_NUCLEUS", "DT", "CD_NEURON_TYPE", type);
        set("DESCENDING_COMMANDS", key, "CD_SIGNAL", "series");
        set("DESCENDING_COMMANDS", key, "MOD_TYPE", "rate");
        set("DESCENDING_COMMANDS", key, "TINI", 0);
        set("DESCENDING_COMMANDS", key, "TFIN", durationMs);
        List<List<Double>> pairs = new ArrayList<>();
        for (int i = 0; i < points.length; i += 2) {
            pairs.add(List.of(points[i], points[i + 1]));
        }
        tractSeries.put("DT:" + type, pairs);
    }

    /** A velocidade de condução do axônio (m/s) do primeiro e do último neurônio do tipo. */
    void axonVelocity(String nucleus, String type, double from, double to) {
        Map<String, Object> key = Map.of("CD_NUCLEUS", nucleus, "CD_NEURON_TYPE", type);
        set("NEURON_TYPE", key, "AXON_VELOCITY1", from);
        set("NEURON_TYPE", key, "AXON_VELOCITY2", to);
    }

    void synapse(String pre, String nucleus, String type, String column, Object value) {
        set("CONDUCTANCE_TYPE", Map.of("CD_NUCLEUS_PRE", pre, "CD_NUCLEUS", nucleus, "CD_CONDUCTANCE_TYPE", type),
                column, value);
    }

    void dynamics(String pre, String nucleus, String type, String kind, double tau, double variationPercent) {
        Map<String, Object> key = Map.of("CD_NUCLEUS_PRE", pre, "CD_NUCLEUS", nucleus, "CD_CONDUCTANCE_TYPE", type);
        set("SYNAPTIC_DYNAMICS", key, "DYNAMIC_TYPE", kind);
        set("SYNAPTIC_DYNAMICS", key, "TAU", tau);
        set("SYNAPTIC_DYNAMICS", key, "VARIATION", variationPercent);
    }

    /** O nervo ativo, com pulsos de {@code start} a {@code stop} (ms), intensidade (mA) e frequência (Hz). */
    void nerve(String nerve, double start, double stop, double peak, double frequency) {
        Map<String, Object> key = Map.of("CD_NERVE", nerve);
        set("NERVE", key, "ACTIVE", 1);
        set("NERVE", key, "START", start);
        set("NERVE", key, "STOP", stop);
        set("NERVE", key, "PEAK", peak);
        set("NERVE", key, "FREQUENCY", frequency);
    }

    /** O sinal somado à frequência dos pulsos do nervo. */
    void nerveSignal(String nerve, String shape, double tini, double tfin, double amp) {
        Map<String, Object> key = Map.of("CD_NERVE", nerve);
        set("NERVE", key, "CD_SIGNAL", shape);
        set("NERVE", key, "TINI", tini);
        set("NERVE", key, "TFIN", tfin);
        set("NERVE", key, "AMP", amp);
    }

    void injectedCurrent(String nucleus, String type, String compartment, double nA) {
        Map<String, Object> key = Map.of("CD_NUCLEUS", nucleus, "CD_NEURON_TYPE", type);
        set("INJECTED_CURRENT", key, "ACTIVE", 1);
        set("INJECTED_CURRENT", key, "IMAX", nA);
        set("INJECTED_CURRENT", key, "COMPARTMENT", compartment);
        set("INJECTED_CURRENT", key, "CD_SIGNAL", "none");
    }

    Example example(String file, String name, double durationMs, double stepMs, String description) {
        Map<String, Object> key = Map.of();
        set("CONFIGURATION", key, "NM_CONFIGURATION", name);
        set("CONFIGURATION", key, "DS_CONFIGURATION", "");
        set("CONFIGURATION", key, "TFIN", durationMs);
        set("CONFIGURATION", key, "STEP", stepMs);
        Map<String, Object> all = new LinkedHashMap<>(settings);
        if (!tractStd.isEmpty()) {
            all.put("tractStd", new LinkedHashMap<>(tractStd));
        }
        if (!tractSeries.isEmpty()) {
            all.put("tractSeries", new LinkedHashMap<>(tractSeries));
        }
        LegacyConfig changed = new LegacyConfig(config.format(), config.idConfiguration(), tables,
                all.isEmpty() ? null : all);
        Scenario scenario = new Scenario(null, name, description, base.schemaVersion(), 1, durationMs, stepMs,
                Scenario.ENGINE_LEGACY, LegacyJson.toMap(changed));
        return new Example(file + ".json", scenario);
    }

    /** Muda a coluna em todas as linhas da tabela que batem com as chaves; pelo menos uma tem de bater. */
    private void set(String table, Map<String, Object> keys, String column, Object value) {
        LegacyConfig.Table t = tables.get(table);
        int c = t.columns().indexOf(column);
        if (c < 0) {
            throw new IllegalArgumentException(table + " não tem a coluna " + column);
        }
        int matched = 0;
        for (List<Object> row : t.rows()) {
            boolean match = true;
            for (Map.Entry<String, Object> k : keys.entrySet()) {
                match &= String.valueOf(row.get(t.columns().indexOf(k.getKey()))).equals(String.valueOf(k.getValue()));
            }
            if (match) {
                row.set(c, value);
                matched++;
            }
        }
        if (matched == 0) {
            throw new IllegalArgumentException("nenhuma linha de " + table + " com " + keys);
        }
    }
}
