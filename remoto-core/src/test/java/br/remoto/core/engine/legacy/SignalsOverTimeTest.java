package br.remoto.core.engine.legacy;

import br.remoto.core.model.joint.IsometricAnkle;
import br.remoto.core.scenario.Scenario;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Sinais no tempo que o site antigo não tinha (e o ReMoto em Fortran tem): o ângulo imposto ao
 * tornozelo, como uma perturbação, e o fusimotor modulado. Sem eles, nada muda (os golden masters
 * cobrem isso); com eles, o ângulo segue o sinal e os fusos respondem.
 */
class SignalsOverTimeTest {

    private static final double DURATION_MS = 300;
    private static LegacyDatabase db;

    @BeforeAll
    static void open() {
        db = LegacyDatabase.open(Path.of(System.getProperty("remoto.legacy.db")));
    }

    @AfterAll
    static void close() {
        db.close();
    }

    @Test
    void tornozeloSegueOAnguloInicialMaisOSinal() {
        IsometricAnkle ankle = new IsometricAnkle(10, 0, t -> t / 100);
        ankle.step(250, new IsometricAnkle.Actuator[0]);
        assertThat(Math.toDegrees(ankle.angle())).isCloseTo(12.5, within(1e-9));

        IsometricAnkle fixed = new IsometricAnkle(10, 0);
        fixed.step(250, new IsometricAnkle.Actuator[0]);
        assertThat(Math.toDegrees(fixed.angle())).isCloseTo(10, within(1e-9));
    }

    /** Uma rampa de 10° em 50 ms, a partir de 100 ms, mantida: um estiramento do tornozelo. */
    @Test
    void anguloImpostoMoveOTornozeloEOFusoResponde() {
        Map<String, Object> ramp = signal("ramp", 100, 10_000, 10, 50);

        Recorder imposed = run(Map.of("cdJointModel", "imposed", "cdMuscleModel", "hill", "jointAngleSignal", ramp));
        Recorder isometric = run(Map.of("cdJointModel", "isometric", "cdMuscleModel", "hill"));

        double[] angle = values(imposed, "articulacao-angulo");
        double[] fixedAngle = values(isometric, "articulacao-angulo");
        assertThat(last(angle) - last(fixedAngle)).isCloseTo(10, within(1e-6));
        assertThat(values(imposed, "fuso-ia-SOL")).isNotEqualTo(values(isometric, "fuso-ia-SOL"));
        assertThat(values(imposed, "musculo-comprimento-SOL")).isNotEqualTo(values(isometric, "musculo-comprimento-SOL"));
    }

    /**
     * O fusimotor sobe 60 spikes/s a partir de 100 ms. Até lá, nada muda; depois, o estático aumenta
     * o disparo dos II. O dinâmico muda os Ia, mas o sentido depende do laço fechado (o reflexo e o
     * fascículo do Hill encurtando); no fuso isolado ele aumenta a Ia no estiramento ({@code SpindleTest}).
     */
    @Test
    void fusimotorModuladoMudaOsFusosDepoisDoInicio() {
        Map<String, Object> step = signal("constant", 100, 10_000, 60, 0);

        Recorder constant = run(Map.of("cdMuscleModel", "hill"));
        Recorder statik = run(Map.of("cdMuscleModel", "hill", "gammaStaticSignal", step));
        Recorder dynamic = run(Map.of("cdMuscleModel", "hill", "gammaDynamicSignal", step));

        double[] ii = values(constant, "fuso-ii-SOL");
        double[] iiStatic = values(statik, "fuso-ii-SOL");
        double[] ia = values(constant, "fuso-ia-SOL");
        double[] iaDynamic = values(dynamic, "fuso-ia-SOL");
        int onset = ia.length / 3; // 100 ms
        assertThat(Arrays.copyOf(iiStatic, onset)).isEqualTo(Arrays.copyOf(ii, onset));
        assertThat(Arrays.copyOf(iaDynamic, onset)).isEqualTo(Arrays.copyOf(ia, onset));
        assertThat(mean(iiStatic, 1.0 / 3)).isGreaterThan(mean(ii, 1.0 / 3));
        assertThat(iaDynamic).isNotEqualTo(ia);
    }

    @Test
    void sinaisFicamNoSettingsEVoltamAoReabrir() {
        LegacyConfig original = db.export(17);
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("cdJointModel", "imposed");
        settings.put("jointAngleSignal", signal("sine", 0, 1000, 5, 0));
        settings.put("gammaStaticSignal", signal("ramp", 0, 1000, 40, 500));
        LegacyConfig with = new LegacyConfig(original.format(), original.idConfiguration(), original.tables(), settings);

        LegacyConfig stored = LegacyConfigurations.store(LegacyConfigurations.load(with), with);

        assertThat(stored.settings()).containsKeys("jointAngleSignal", "gammaStaticSignal")
                .doesNotContainKey("gammaDynamicSignal");
        assertThat(((Map<?, ?>) stored.settings().get("jointAngleSignal")).get("shape")).isEqualTo("sine");
        assertThat(((Map<?, ?>) stored.settings().get("gammaStaticSignal")).get("width")).isEqualTo(500.0);
        // sem sinais, o settings não ganha nada: os cenários antigos continuam iguais
        assertThat(LegacyConfigurations.store(LegacyConfigurations.load(original), original).settings())
                .doesNotContainKeys("jointAngleSignal", "gammaDynamicSignal", "gammaStaticSignal");
    }

    /** O ângulo imposto como série de pontos (a forma "series"): os pontos vão e voltam pelo settings. */
    @Test
    void anguloComoSerieDePontos() {
        LegacyConfig original = db.export(17);
        Map<String, Object> angle = signal("series", 0, 0, 0, 0);
        angle.put("points", java.util.List.of(java.util.List.of(0.0, 0.0), java.util.List.of(50.0, 4.0),
                java.util.List.of(100.0, 0.0)));
        Map<String, Object> settings = Map.of("cdJointModel", "imposed", "jointAngleSignal", angle);
        LegacyConfig with = new LegacyConfig(original.format(), original.idConfiguration(), original.tables(), settings);

        LegacyConfig stored = LegacyConfigurations.store(LegacyConfigurations.load(with), with);
        Map<?, ?> saved = (Map<?, ?>) stored.settings().get("jointAngleSignal");
        assertThat(saved.get("shape")).isEqualTo("series");
        assertThat(saved.get("points")).isEqualTo(angle.get("points"));

        Recorder r = run(Map.of("cdJointModel", "imposed", "cdMuscleModel", "hill", "jointAngleSignal", angle));
        double[] a = values(r, "articulacao-angulo");
        assertThat(java.util.Arrays.stream(a).max().orElseThrow()).isCloseTo(4, within(1e-6));
    }

    private static Map<String, Object> signal(String shape, double tini, double tfin, double amp, double width) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("shape", shape);
        s.put("tini", tini);
        s.put("tfin", tfin);
        s.put("amp", amp);
        s.put("freq", 2.0);
        s.put("width", width);
        return s;
    }

    private static Recorder run(Map<String, Object> settings) {
        LegacyConfig original = db.export(17);
        LegacyConfig config = new LegacyConfig(original.format(), original.idConfiguration(), original.tables(),
                settings);
        Scenario base = db.exportScenario(17).withDuration(DURATION_MS);
        Scenario scenario = new Scenario(null, base.name(), "", base.schemaVersion(), base.seed(), DURATION_MS,
                base.stepMs(), Scenario.ENGINE_LEGACY, LegacyJson.toMap(config));
        Recorder r = new Recorder();
        new LegacyEngine(1).run(scenario, r);
        return r;
    }

    private static double[] values(Recorder r, String name) {
        assertThat(r.outputs).containsKey(name);
        return r.outputs.get(name).lines().skip(1).mapToDouble(l -> Double.parseDouble(l.split(",")[1])).toArray();
    }

    private static double last(double[] v) {
        return v[v.length - 1];
    }

    /** Média do trecho final (a partir da fração {@code from} da série). */
    private static double mean(double[] v, double from) {
        return Arrays.stream(v, (int) (v.length * from), v.length).average().orElseThrow();
    }
}
