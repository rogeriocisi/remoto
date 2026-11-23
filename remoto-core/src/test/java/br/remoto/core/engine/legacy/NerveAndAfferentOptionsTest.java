package br.remoto.core.engine.legacy;

import br.remoto.core.scenario.Scenario;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O pulso e o limiar do estímulo do nervo e a ordem gama dos aferentes, que o original fixava no
 * código (e o ReMoto em Fortran deixa escolher). Sem eles, nada muda (os golden masters cobrem
 * isso); com eles, o recrutamento pelo estímulo e a regularidade dos aferentes mudam.
 */
class NerveAndAfferentOptionsTest {

    private static final double STIMULUS_MS = 5;
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
     * Um pulso de 14 mA no nervo tibial posterior, em 5 ms, na configuração 17 (sóleo com
     * motoneurônios e aferentes Ia). Por corrente, a duração do pulso não muda quem dispara; por
     * carga, o pulso de 1 ms recruta quase os mesmos axônios, e o de 0,2 ms, nenhum.
     */
    @Test
    void limiarPorCargaDependeDaDuracaoDoPulso() {
        int[] current1 = recruited(null, 1);
        int[] current02 = recruited(null, 0.2);
        int[] charge1 = recruited("charge", 1);
        int[] charge02 = recruited("charge", 0.2);
        int[] none = recruited(null, 0);

        assertThat(current1[0]).isGreaterThan(100); // Ia
        assertThat(current1[1] - none[1]).isGreaterThan(100); // motoneurônios
        assertThat(current02[0]).isEqualTo(current1[0]);
        assertThat(charge1[0]).isBetween(current1[0], (int) (current1[0] * 1.1));
        assertThat(charge1[1] - none[1]).isBetween(current1[1] - none[1], (int) ((current1[1] - none[1]) * 1.1));
        assertThat(charge02[0]).isZero();
        assertThat(charge02[1]).isEqualTo(none[1]);
    }

    /**
     * Os Ia do sóleo disparando pelo fuso (músculo de Hill), com a ordem gama do original (25) e com
     * ordem 1 (Poisson): os intervalos de cada fibra ficam bem mais irregulares.
     */
    @Test
    void ordemGamaDosAferentesMudaARegularidade() {
        double original = meanCv(run(Map.of("cdMuscleModel", "hill"), 300, null));
        double poisson = meanCv(run(Map.of("cdMuscleModel", "hill", "afferentGammaOrder", Map.of("SOL:Ia", 1)), 300, null));
        assertThat(poisson).isGreaterThan(original * 1.5);
    }

    @Test
    void opcoesFicamNoSettingsEVoltamAoReabrir() {
        LegacyConfig original = db.export(17);
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("afferentGammaOrder", Map.of("SOL:Ia", 8));
        settings.put("nerveStimulus", Map.of("PTN", Map.of("pulseWidth", 0.5, "thresholdModel", "charge")));
        LegacyConfig with = new LegacyConfig(original.format(), original.idConfiguration(), original.tables(), settings);

        LegacyConfig stored = LegacyConfigurations.store(LegacyConfigurations.load(with), with);

        assertThat(stored.settings().get("afferentGammaOrder")).isEqualTo(Map.of("SOL:Ia", 8));
        Map<?, ?> ptn = (Map<?, ?>) ((Map<?, ?>) stored.settings().get("nerveStimulus")).get("PTN");
        assertThat(ptn.get("pulseWidth")).isEqualTo(0.5);
        assertThat(ptn.get("thresholdModel")).isEqualTo("charge");
        assertThat(ptn.get("tauMotor")).isEqualTo(0.45);
        assertThat(((Map<?, ?>) stored.settings().get("nerveStimulus")).containsKey("CPN")).isFalse();
        // sem opções, o settings não ganha nada
        assertThat(LegacyConfigurations.store(LegacyConfigurations.load(original), original).settings())
                .doesNotContainKeys("afferentGammaOrder", "nerveStimulus");
    }

    @Test
    void valoresInvalidosSaoRecusados() {
        LegacyConfig original = db.export(17);
        for (Map<String, Object> settings : List.<Map<String, Object>>of(
                Map.of("afferentGammaOrder", Map.of("SOL:Ia", -2)),
                Map.of("nerveStimulus", Map.of("PTN", Map.of("pulseWidth", 0))),
                Map.of("nerveStimulus", Map.of("PTN", Map.of("thresholdModel", "voltage"))),
                Map.of("nerveStimulus", Map.of("PTN", Map.of("tauSensory", -1))))) {
            LegacyConfig with = new LegacyConfig(original.format(), original.idConfiguration(), original.tables(), settings);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> LegacyConfigurations.load(with))
                    .as("%s", settings).isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Quantos axônios o pulso fez disparar: Ia (os disparos pelo estímulo) e motoneurônios (os que
     * têm disparo no terminal até 12 ms depois do pulso, os da onda M e os do comando descendente).
     *
     * @param pulseWidth 0 = sem estímulo
     */
    private static int[] recruited(String thresholdModel, double pulseWidth) {
        Map<String, Object> settings = new HashMap<>();
        if (thresholdModel != null || pulseWidth != 1) {
            Map<String, Object> ptn = new HashMap<>();
            ptn.put("pulseWidth", pulseWidth == 0 ? 1.0 : pulseWidth);
            if (thresholdModel != null) {
                ptn.put("thresholdModel", thresholdModel);
            }
            settings.put("nerveStimulus", Map.of("PTN", ptn));
        }
        Recorder r = run(settings, 20, pulseWidth == 0 ? null : 14.0);
        return new int[] {units(r, "disparos-estimulo-SOL-Ia", 0, 20), units(r, "disparos-SOL", STIMULUS_MS, STIMULUS_MS + 12)};
    }

    /** A configuração 17 com o settings dado e, se {@code amp} não for nulo, um pulso no PTN em 5 ms. */
    private static Recorder run(Map<String, Object> settings, double durationMs, Double amp) {
        LegacyConfig original = db.export(17);
        Map<String, LegacyConfig.Table> tables = new LinkedHashMap<>(original.tables());
        if (amp != null) {
            LegacyConfig.Table nerve = tables.get("NERVE");
            List<List<Object>> rows = new ArrayList<>();
            for (List<Object> row : nerve.rows()) {
                List<Object> copy = new ArrayList<>(row);
                if ("PTN".equals(copy.get(nerve.columns().indexOf("CD_NERVE")))) {
                    copy.set(nerve.columns().indexOf("ACTIVE"), 1);
                    copy.set(nerve.columns().indexOf("START"), STIMULUS_MS);
                    copy.set(nerve.columns().indexOf("STOP"), STIMULUS_MS + 3);
                    copy.set(nerve.columns().indexOf("PEAK"), amp);
                    copy.set(nerve.columns().indexOf("FREQUENCY"), 1);
                }
                rows.add(copy);
            }
            tables.put("NERVE", new LegacyConfig.Table(nerve.columns(), rows));
        }
        LegacyConfig config = new LegacyConfig(original.format(), original.idConfiguration(), tables, settings);
        Scenario base = db.exportScenario(17);
        Scenario scenario = new Scenario(null, base.name(), "", base.schemaVersion(), base.seed(), durationMs,
                base.stepMs(), Scenario.ENGINE_LEGACY, LegacyJson.toMap(config));
        Recorder r = new Recorder();
        new LegacyEngine(1).run(scenario, r);
        return r;
    }

    /** As unidades com algum disparo em (from, to]. */
    private static int units(Recorder r, String name, double from, double to) {
        String csv = r.outputs.get(name);
        if (csv == null) {
            return 0;
        }
        Set<Integer> units = new HashSet<>();
        csv.lines().skip(1).forEach(l -> {
            String[] f = l.split(",");
            double t = Double.parseDouble(f[0]);
            if (t > from && t <= to) {
                units.add(Integer.parseInt(f[1]));
            }
        });
        return units.size();
    }

    /** A média, entre as fibras Ia com 10 disparos ou mais, do coeficiente de variação dos intervalos. */
    private static double meanCv(Recorder r) {
        Map<Integer, List<Double>> trains = new HashMap<>();
        r.outputs.get("disparos-SOL-Ia").lines().skip(1).forEach(l -> {
            String[] f = l.split(",");
            trains.computeIfAbsent(Integer.parseInt(f[1]), k -> new ArrayList<>()).add(Double.parseDouble(f[0]));
        });
        double sum = 0;
        int n = 0;
        for (List<Double> train : trains.values()) {
            if (train.size() < 10) {
                continue;
            }
            train.sort(null);
            double[] isi = new double[train.size() - 1];
            for (int i = 1; i < train.size(); i++) {
                isi[i - 1] = train.get(i) - train.get(i - 1);
            }
            double mean = java.util.Arrays.stream(isi).average().orElseThrow();
            double var = java.util.Arrays.stream(isi).map(x -> (x - mean) * (x - mean)).sum() / (isi.length - 1);
            sum += Math.sqrt(var) / mean;
            n++;
        }
        assertThat(n).as("fibras Ia com disparos").isGreaterThan(20);
        return sum / n;
    }
}
