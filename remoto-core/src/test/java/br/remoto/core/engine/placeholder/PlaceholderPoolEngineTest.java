package br.remoto.core.engine.placeholder;

import br.remoto.core.engine.ResultSink;
import br.remoto.core.engine.RunContext;
import br.remoto.core.engine.SimulationCancelledException;
import br.remoto.core.scenario.Nucleus;
import br.remoto.core.scenario.NucleusSpec;
import br.remoto.core.scenario.Scenario;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaceholderPoolEngineTest {

    private final PlaceholderPoolEngine engine = new PlaceholderPoolEngine();

    @Test
    void mesmaSeedProduzExatamenteOMesmoResultado() {
        Recorder a = run(scenario(42, 6.0));
        Recorder b = run(scenario(42, 6.0));

        assertThat(a.series.get("forca-SOL")).containsExactly(b.series.get("forca-SOL"));
        assertThat(a.spikeTimes.get("disparos-SOL")).containsExactly(b.spikeTimes.get("disparos-SOL"));
        assertThat(a.spikeUnits.get("disparos-SOL")).containsExactly(b.spikeUnits.get("disparos-SOL"));
    }

    @Test
    void seedDiferenteProduzResultadoDiferente() {
        Recorder a = run(scenario(1, 6.0));
        Recorder b = run(scenario(2, 6.0));

        assertThat(a.spikeTimes.get("disparos-SOL")).isNotEqualTo(b.spikeTimes.get("disparos-SOL"));
    }

    @Test
    void comandoMaiorRecrutaMaisUnidadesEGeraMaisForca() {
        Recorder fraco = run(scenario(7, 3.0));
        Recorder forte = run(scenario(7, 9.0));

        assertThat(distinctUnits(forte)).isGreaterThan(distinctUnits(fraco));
        assertThat(mean(forte.series.get("forca-SOL"))).isGreaterThan(mean(fraco.series.get("forca-SOL")));
    }

    @Test
    void cancelamentoInterrompeAExecucao() {
        RunContext cancelled = new RunContext() {
            @Override public void progress(double fraction) { }
            @Override public boolean isCancelled() { return true; }
            @Override public ResultSink results() { return new Recorder(); }
        };

        assertThatThrownBy(() -> engine.run(scenario(1, 6.0), cancelled))
                .isInstanceOf(SimulationCancelledException.class);
    }

    @Test
    void progressoTerminaEmUm() {
        Recorder recorder = run(scenario(3, 6.0));

        assertThat(recorder.progress).isNotEmpty();
        assertThat(recorder.progress.getLast()).isEqualTo(1.0);
        assertThat(recorder.progress).isSorted();
    }

    private static Scenario scenario(long seed, double drive) {
        return new Scenario("t", "teste", "", Scenario.CURRENT_SCHEMA_VERSION, seed, 300, 0.05,
                List.of(new NucleusSpec(Nucleus.SOL, 40, drive, 1.0)));
    }

    private Recorder run(Scenario scenario) {
        Recorder recorder = new Recorder();
        engine.run(scenario, recorder);
        return recorder;
    }

    private static long distinctUnits(Recorder r) {
        return java.util.Arrays.stream(r.spikeUnits.get("disparos-SOL")).distinct().count();
    }

    private static double mean(double[] values) {
        return java.util.Arrays.stream(values).average().orElse(0);
    }

    /** Contexto + destino em memória para testes. */
    static final class Recorder implements RunContext, ResultSink {
        final Map<String, double[]> series = new HashMap<>();
        final Map<String, double[]> spikeTimes = new HashMap<>();
        final Map<String, int[]> spikeUnits = new HashMap<>();
        final List<Double> progress = new ArrayList<>();

        @Override public void progress(double fraction) { progress.add(fraction); }
        @Override public boolean isCancelled() { return false; }
        @Override public ResultSink results() { return this; }

        @Override
        public void series(String name, String unit, double[] timeMs, double[] values) {
            series.put(name, values);
        }

        @Override
        public void spikes(String name, int[] units, double[] timeMs) {
            spikeUnits.put(name, units);
            spikeTimes.put(name, timeMs);
        }
    }
}
