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
 * O comando de um trato como uma série de taxas qualquer (a forma "series"), que o original não
 * tinha. Com o alvo "rate" e o intervalo médio 0, a série é a própria taxa de cada axônio.
 */
class TractSeriesTest {

    private static final List<List<Double>> PROFILE = List.of(List.of(0.0, 0.0), List.of(100.0, 0.0),
            List.of(100.0, 200.0), List.of(200.0, 200.0), List.of(300.0, 0.0));
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
     * Os 100 axônios do CM_ext da configuração 17 (Poisson) seguindo 0 Hz até 100 ms, um degrau
     * para 200 Hz até 200 ms e uma rampa de volta a 0 em 300 ms: cerca de 2000 disparos no
     * platô e 1000 na rampa. Com taxa 0, o gerador usa o intervalo máximo (2 s), que dá poucos.
     */
    @Test
    void oTratoSegueASerieDeTaxas() {
        LegacyConfig original = db.export(17);
        Map<String, LegacyConfig.Table> tables = new LinkedHashMap<>(original.tables());
        set(tables, "NEURON_TYPE", "CD_NEURON_TYPE", "CM_ext", Map.of("MEAN", 0));
        set(tables, "DESCENDING_COMMANDS", "CD_NEURON_TYPE", "CM_ext", Map.of("CD_SIGNAL", "series", "MOD_TYPE", "rate"));
        LegacyConfig config = new LegacyConfig(original.format(), original.idConfiguration(), tables,
                Map.of("tractSeries", Map.of("DT:CM_ext", PROFILE)));

        Scenario base = db.exportScenario(17);
        Scenario scenario = new Scenario(null, base.name(), "", base.schemaVersion(), base.seed(), 300,
                base.stepMs(), Scenario.ENGINE_LEGACY, LegacyJson.toMap(config));
        Recorder r = new Recorder();
        new LegacyEngine(1).run(scenario, r);

        double[] t = r.outputs.get("disparos-DT-CM_ext").lines().skip(1)
                .mapToDouble(l -> Double.parseDouble(l.split(",")[0])).toArray();
        assertThat(count(t, 0, 100)).isLessThan(30);
        assertThat(count(t, 100, 200)).isBetween(1800, 2200);
        assertThat(count(t, 200, 300)).isBetween(850, 1150);
    }

    @Test
    void aSerieFicaNoSettingsEVoltaAoReabrir() {
        LegacyConfig original = db.export(17);
        LegacyConfig with = new LegacyConfig(original.format(), original.idConfiguration(), original.tables(),
                Map.of("tractSeries", Map.of("DT:CM_ext", PROFILE)));

        LegacyConfig stored = LegacyConfigurations.store(LegacyConfigurations.load(with), with);

        assertThat(stored.settings().get("tractSeries")).isEqualTo(Map.of("DT:CM_ext", PROFILE));
        assertThat(LegacyConfigurations.store(LegacyConfigurations.load(original), original).settings())
                .doesNotContainKey("tractSeries");

        LegacyConfig backwards = new LegacyConfig(original.format(), original.idConfiguration(), original.tables(),
                Map.of("tractSeries", Map.of("DT:CM_ext", List.of(List.of(10, 1), List.of(5, 2)))));
        assertThatThrownBy(() -> LegacyConfigurations.load(backwards)).isInstanceOf(IllegalArgumentException.class);
    }

    private static int count(double[] times, double from, double to) {
        int n = 0;
        for (double t : times) {
            if (t > from && t <= to) {
                n++;
            }
        }
        return n;
    }

    /** Muda colunas da linha da tabela em que {@code keyColumn} vale {@code key}. */
    private static void set(Map<String, LegacyConfig.Table> tables, String table, String keyColumn, String key,
            Map<String, Object> values) {
        LegacyConfig.Table t = tables.get(table);
        List<List<Object>> rows = new ArrayList<>();
        for (List<Object> row : t.rows()) {
            List<Object> copy = new ArrayList<>(row);
            if (key.equals(copy.get(t.columns().indexOf(keyColumn)))) {
                values.forEach((column, value) -> copy.set(t.columns().indexOf(column), value));
            }
            rows.add(copy);
        }
        tables.put(table, new LegacyConfig.Table(t.columns(), rows));
    }
}
