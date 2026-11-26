package br.remoto.core.protocol;

import br.remoto.core.engine.legacy.LegacyDatabase;
import br.remoto.core.engine.legacy.LegacyEngine;
import br.remoto.core.engine.legacy.LegacyJson;
import br.remoto.core.scenario.Scenario;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** Os protocolos em lote: o parâmetro, as medidas e os dois protocolos prontos. */
class SweepTest {

    private static LegacyDatabase db;
    private static Scenario s17;

    @BeforeAll
    static void open() {
        db = LegacyDatabase.open(Path.of(System.getProperty("remoto.legacy.db")));
        s17 = db.exportScenario(17);
    }

    @AfterAll
    static void close() {
        db.close();
    }

    @Test
    void parametroNumaCelulaDeTabela() {
        ScenarioParameter peak = ScenarioParameter.parse("NERVE:CD_NERVE=PTN:PEAK");
        Scenario changed = peak.apply(s17, 12.5);
        assertThat(((Number) peak.read(changed)).doubleValue()).isEqualTo(12.5);
        assertThat(((Number) peak.read(s17)).doubleValue()).isZero(); // o original não muda
        assertThat(((Number) ScenarioParameter.parse("NERVE:CD_NERVE=CPN:PEAK").read(changed)).doubleValue()).isZero();

        ScenarioParameter mean = ScenarioParameter.parse("NEURON_TYPE:CD_NUCLEUS=DT,CD_NEURON_TYPE=CM_ext:MEAN");
        assertThat(((Number) mean.read(mean.apply(s17, 6))).doubleValue()).isEqualTo(6);
        // a coluna inteira continua inteira
        ScenarioParameter quantity = ScenarioParameter.parse("NEURON_TYPE:CD_NUCLEUS=SOL,CD_NEURON_TYPE=S:QUANTITY");
        assertThat(quantity.read(quantity.apply(s17, 200))).isEqualTo(200);
        assertThat(ScenarioParameter.parse("NERVE:CD_NERVE=PTN:PEAK").read(peak.apply(s17, 14))).isEqualTo(14);

        assertThatThrownBy(() -> ScenarioParameter.parse("NERVO:CD_NERVE=PTN:PEAK").apply(s17, 1))
                .hasMessageContaining("tabela desconhecida");
        assertThatThrownBy(() -> ScenarioParameter.parse("NERVE:CD_NERVE=XX:PEAK").apply(s17, 1))
                .hasMessageContaining("nenhuma linha");
        assertThatThrownBy(() -> ScenarioParameter.parse("NERVE:CD_NERVE=PTN:PICO").apply(s17, 1))
                .hasMessageContaining("não tem a coluna");
        assertThatThrownBy(() -> ScenarioParameter.parse("NERVE:PEAK")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parametroNoSettingsNaSeedENaDuracao() {
        ScenarioParameter width = ScenarioParameter.parse("settings.nerveStimulus.PTN.pulseWidth");
        Scenario changed = width.apply(s17, 0.5);
        assertThat(((Number) width.read(changed)).doubleValue()).isEqualTo(0.5);
        Map<?, ?> settings = (Map<?, ?>) changed.legacy().get("settings");
        assertThat(((Map<?, ?>) settings.get("nerveStimulus")).get("PTN")).isInstanceOf(Map.class);

        assertThat(ScenarioParameter.parse("seed").apply(s17, 7).seed()).isEqualTo(7);
        assertThat(ScenarioParameter.parse("durationMs").apply(s17, 40).durationMs()).isEqualTo(40);
    }

    @Test
    void medidas() {
        Measure m = Measure.parse("M=p2p(emg-SOL,1,3)");
        assertThat(m.name()).isEqualTo("M");
        assertThat(m.output()).isEqualTo("emg-SOL");
        double[] t = {0, 1, 2, 3, 4};
        double[] v = {9, 1, -2, 4, -9};
        assertThat(m.ofSeries(t, v)).isEqualTo(6);
        assertThat(Measure.parse("mean(x)").ofSeries(t, v)).isCloseTo(0.6, within(1e-12));
        assertThat(Measure.parse("mean(x)").name()).isEqualTo("mean(x)");
        assertThat(Measure.parse("sd(x,1,3)").ofSeries(t, v)).isCloseTo(3, within(1e-12)); // 1, −2 e 4
        assertThat(Measure.parse("max(x,5,6)").ofSeries(t, v)).isNaN();
        Measure units = Measure.parse("units(disparos-SOL,0,10)");
        assertThat(units.ofSpikes(new int[] {1, 1, 2, 3}, new double[] {0, 5, 6, 11})).isEqualTo(2);
        assertThat(Measure.parse("count(disparos-SOL,0,10)").ofSpikes(new int[] {1, 1, 2, 3}, new double[] {0, 5, 6, 11}))
                .isEqualTo(2);
        assertThatThrownBy(() -> Measure.parse("media(x)")).hasMessageContaining("função desconhecida");
        assertThatThrownBy(() -> Measure.parse("p2p(x,5,1)")).hasMessageContaining("ao contrário");
        assertThatThrownBy(() -> Measure.parse("p2p x")).hasMessageContaining("medida inválida");
        assertThat(Sweep.range(5, 7, 0.5)).containsExactly(5.0, 5.5, 6.0, 6.5, 7.0);
    }

    /**
     * A curva H/M na configuração 17 (sóleo com aferentes Ia), com o estímulo do nervo tibial em
     * 5 ms: sem corrente, nada; com mais corrente, a onda M recruta mais motoneurônios, até todos.
     */
    @Test
    void curvaHM() {
        Scenario base = ScenarioParameter.parse("NERVE:CD_NERVE=PTN:START").apply(s17, 5);
        Protocols.Prepared hm = Protocols.hmCurve(base, "PTN", "SOL", List.of(0.0, 14.0, 40.0), null, null);
        assertThat(hm.scenario().durationMs()).isEqualTo(65);

        List<Sweep.Row> rows = hm.sweep().run(hm.scenario(), new LegacyEngine(1), null, null);

        assertThat(rows).extracting(Sweep.Row::value).containsExactly(0.0, 14.0, 40.0);
        double[] mUnits = rows.stream().mapToDouble(r -> r.measures()[2]).toArray();
        assertThat(mUnits[0]).isZero();
        assertThat(mUnits[1]).isGreaterThan(100);
        assertThat(mUnits[2]).isEqualTo(900);
        double[] mP2p = rows.stream().mapToDouble(r -> r.measures()[0]).toArray();
        assertThat(mP2p[2]).isGreaterThan(mP2p[1]).isGreaterThan(mP2p[0]);

        StringBuilder csv = new StringBuilder();
        try {
            hm.sweep().writeCsv(rows, csv);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        assertThat(csv.toString()).startsWith("NERVE:CD_NERVE=PTN:PEAK,M_p2p[mV],H_p2p[mV],M_motoneuronios,H_motoneuronios\n0,");
    }

    /**
     * O condicionamento do reflexo H na configuração 18 (sóleo com aferentes Ia, aferentes Ia do
     * tibial anterior e interneurônios Ia), com o pulso teste do nervo tibial em 100 ms, baixado
     * dos 50 mA gravados para 13, onde há reflexo H: o que varia é o intervalo CT, e no cenário de
     * cada simulação o pulso condicionante do fibular comum está CT ms antes do teste. A coluna do
     * parâmetro é o intervalo, não o instante.
     */
    @Test
    void condicionamentoTeste() {
        Scenario s18 = ScenarioParameter.parse("NERVE:CD_NERVE=PTN:PEAK").apply(db.exportScenario(18), 13);
        Protocols.Prepared ct = Protocols.conditioningTest(s18, "PTN", "CPN", "SOL", 15.0,
                List.of(0.0, 1.0, 20.0), null, null);

        assertThat(ct.scenario().durationMs()).isEqualTo(160);
        ScenarioParameter condStart = ScenarioParameter.parse("NERVE:CD_NERVE=CPN:START");
        ScenarioParameter condPeak = ScenarioParameter.parse("NERVE:CD_NERVE=CPN:PEAK");
        ScenarioParameter condActive = ScenarioParameter.parse("NERVE:CD_NERVE=CPN:ACTIVE");
        assertThat(((Number) condPeak.read(ct.scenario())).doubleValue()).isEqualTo(15);
        assertThat(((Number) condActive.read(ct.scenario())).intValue()).isEqualTo(1);
        // o pulso condicionante de cada simulação: CT ms antes do teste, que fica em 100 ms
        for (double interval : ct.sweep().values()) {
            Scenario one = ct.sweep().parameter().apply(ct.scenario(), interval);
            assertThat(((Number) condStart.read(one)).doubleValue()).as("CT de %s ms", interval)
                    .isEqualTo(100 - interval);
            // o pulso teste não se move
            assertThat(((Number) ScenarioParameter.parse("NERVE:CD_NERVE=PTN:START").read(one)).doubleValue())
                    .isEqualTo(100);
        }

        List<Sweep.Row> rows = ct.sweep().run(ct.scenario(), new LegacyEngine(1), null, null);
        assertThat(rows).extracting(Sweep.Row::value).containsExactly(0.0, 1.0, 20.0);
        // o reflexo H do pulso teste aparece nas três, e a inibição recíproca o derruba no
        // intervalo de 1 ms, que é onde o potencial inibitório encontra o excitatório
        double[] h = rows.stream().mapToDouble(r -> r.measures()[1]).toArray();
        for (double amplitude : h) {
            assertThat(amplitude).isGreaterThan(0);
        }
        assertThat(h[1]).isLessThan(h[0] / 10).isLessThan(h[2] / 10);
        // a onda M não muda: o condicionamento age na medula, não nos axônios motores
        double[] m = rows.stream().mapToDouble(r -> r.measures()[0]).toArray();
        assertThat(m[1]).isCloseTo(m[0], within(m[0] / 100));
        assertThat(m[2]).isCloseTo(m[0], within(m[0] / 100));

        StringBuilder csv = new StringBuilder();
        try {
            ct.sweep().writeCsv(rows, csv);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        assertThat(csv.toString()).startsWith("CT[ms],M_p2p[mV],H_p2p[mV],M_motoneuronios,H_motoneuronios\n0,");

        // um intervalo maior que o instante do pulso teste jogaria o condicionante para antes de zero
        assertThatThrownBy(() -> Protocols.conditioningTest(s18, "PTN", "CPN", "SOL", null, List.of(200.0), null, null))
                .hasMessageContaining("antes do início da simulação");
        assertThatThrownBy(() -> Protocols.conditioningTest(s18, "PTN", "PTN", "SOL", null, List.of(1.0), null, null))
                .hasMessageContaining("o mesmo");
    }

    /** Com o intervalo médio do trato maior, a força média cai. */
    @Test
    void variabilidadeDaForca() {
        Scenario base = s17.withDuration(200);
        Protocols.Prepared force = Protocols.forceVariability(base, "CM_ext", "SOL", List.of(3.0, 9.0), 100.0, 200.0);
        List<Sweep.Row> rows = force.sweep().run(force.scenario(), new LegacyEngine(1), null, null);
        assertThat(rows.get(0).measures()[0]).isGreaterThan(rows.get(1).measures()[0]);
        assertThat(rows.get(0).measures()[2]).isCloseTo(rows.get(0).measures()[1] / rows.get(0).measures()[0], within(1e-9));
        assertThat(LegacyJson.toConfig(force.scenario().legacy())).isNotNull();
    }
}
