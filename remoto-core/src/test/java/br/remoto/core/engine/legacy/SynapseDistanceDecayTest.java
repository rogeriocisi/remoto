package br.remoto.core.engine.legacy;

import br.remoto.core.scenario.Scenario;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Conexões que enfraquecem com a distância em qualquer via (peso {@code dec / (dec + d²)}), que o
 * original só tinha entre células de Renshaw e motoneurônios. Sem decaimento, nada muda (os golden
 * masters cobrem isso).
 */
class SynapseDistanceDecayTest {

    private static final List<String> CM_EXT_TO_SOL =
            List.of("DT>SOL:TR CM_ext-MN S", "DT>SOL:TR CM_ext-MN FR", "DT>SOL:TR CM_ext-MN FF");
    private static LegacyDatabase db;

    @BeforeAll
    static void open() {
        db = LegacyDatabase.open(Path.of(System.getProperty("remoto.legacy.db")));
    }

    @AfterAll
    static void close() {
        db.close();
    }

    /**
     * Na configuração 17, os 100 axônios do trato CM_ext ficam espalhados pela coluna, sobre os
     * motoneurônios do sóleo: com decaimento, cada axônio fica com sinapses só nos motoneurônios
     * perto dele, e mais fracas. A parte do trato na condutância excitatória de cada motoneurônio (o
     * total menos o de uma rodada sem essas sinapses; o resto vem dos aferentes Ia) cai, e fica maior
     * no meio do núcleo, que tem axônios dos dois lados, que nas pontas.
     */
    @Test
    void comDecaimentoAsSinapsesDoTratoEnfraquecemComADistancia() {
        Map<String, Object> decays = new LinkedHashMap<>();
        CM_EXT_TO_SOL.forEach(k -> decays.put(k, 4.0));
        double[] without = gmaxExc(Map.of(), true);
        double[] uniform = minus(gmaxExc(Map.of(), false), without);
        double[] decaying = minus(gmaxExc(Map.of("synapseDistanceDecay", decays), false), without);

        assertThat(mean(uniform, 0, 900)).isGreaterThan(10); // µS do trato em cada motoneurônio
        assertThat(mean(decaying, 0, 900)).isLessThan(0.5 * mean(uniform, 0, 900));
        // os S vêm primeiro, das posições menores às maiores: o meio deles contra a ponta
        assertThat(mean(decaying, 375, 425)).isGreaterThan(1.3 * mean(decaying, 0, 50));
        assertThat(mean(uniform, 375, 425)).isLessThan(1.15 * mean(uniform, 0, 50));
    }

    @Test
    void decaimentoFicaNoSettingsEVoltaAoReabrir() {
        LegacyConfig original = db.export(17);
        Map<String, Object> settings = Map.of("synapseDistanceDecay", Map.of("DT>SOL:TR CM_ext-MN S", 2.5));
        LegacyConfig with = new LegacyConfig(original.format(), original.idConfiguration(), original.tables(), settings);

        LegacyConfig stored = LegacyConfigurations.store(LegacyConfigurations.load(with), with);

        assertThat(stored.settings().get("synapseDistanceDecay")).isEqualTo(Map.of("DT>SOL:TR CM_ext-MN S", 2.5));
        assertThat(LegacyConfigurations.store(LegacyConfigurations.load(original), original).settings())
                .doesNotContainKey("synapseDistanceDecay");

        LegacyConfig invalid = new LegacyConfig(original.format(), original.idConfiguration(), original.tables(),
                Map.of("synapseDistanceDecay", Map.of("DT>SOL:TR CM_ext-MN S", -1)));
        assertThatThrownBy(() -> LegacyConfigurations.load(invalid)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * A condutância excitatória total nos dendritos de cada motoneurônio do sóleo (µS), em 5 ms de
     * simulação; com {@code withoutTract}, sem as sinapses do CM_ext no sóleo (conectividade 0, que
     * não muda os sorteios).
     */
    private static double[] gmaxExc(Map<String, Object> settings, boolean withoutTract) {
        LegacyConfig original = db.export(17);
        Map<String, LegacyConfig.Table> tables = new LinkedHashMap<>(original.tables());
        if (withoutTract) {
            LegacyConfig.Table t = tables.get("CONDUCTANCE_TYPE");
            List<List<Object>> rows = new ArrayList<>();
            for (List<Object> row : t.rows()) {
                List<Object> copy = new ArrayList<>(row);
                if (CM_EXT_TO_SOL.contains(copy.get(t.columns().indexOf("CD_NUCLEUS_PRE")) + ">"
                        + copy.get(t.columns().indexOf("CD_NUCLEUS")) + ":" + copy.get(t.columns().indexOf("CD_CONDUCTANCE_TYPE")))) {
                    copy.set(t.columns().indexOf("CONNECTIVITY"), 0);
                }
                rows.add(copy);
            }
            tables.put("CONDUCTANCE_TYPE", new LegacyConfig.Table(t.columns(), rows));
        }
        LegacyConfig config = new LegacyConfig(original.format(), original.idConfiguration(), tables, settings);
        Scenario base = db.exportScenario(17);
        Scenario scenario = new Scenario(null, base.name(), "", base.schemaVersion(), base.seed(), 5,
                base.stepMs(), Scenario.ENGINE_LEGACY, LegacyJson.toMap(config));
        Recorder r = new Recorder();
        new LegacyEngine(1).run(scenario, r);

        String[] lines = r.outputs.get("tabela-resumo").split("\n");
        List<String> header = List.of(lines[0].split(","));
        int nucleus = header.indexOf("nucleo");
        int category = header.indexOf("categoria");
        int gmax = header.indexOf("gmax_exc_dendrito[uS]");
        List<Double> values = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String[] f = lines[i].split(",");
            if (f[nucleus].equals("SOL") && f[category].equals("MN")) {
                values.add(Double.parseDouble(f[gmax]));
            }
        }
        assertThat(values).hasSize(900);
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private static double[] minus(double[] a, double[] b) {
        double[] d = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            d[i] = a[i] - b[i];
        }
        return d;
    }

    private static double mean(double[] v, int from, int to) {
        return java.util.Arrays.stream(v, from, to).average().orElseThrow();
    }
}
