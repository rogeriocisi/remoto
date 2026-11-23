package br.remoto.core.model.stimulus;

import br.remoto.core.model.signal.Modulation;
import br.remoto.core.model.signal.Modulation.Shape;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Os trens de pulsos da estimulação do nervo e a corrente injetada. */
class NerveStimulationTest {

    private static final double STEP = 0.05;
    private static final Modulation NO_SIGNAL = new Modulation(Shape.NONE, 0, 0, 0, 0, 0, 0, 0, 0);

    /** Os inícios dos pulsos (primeiro passo com intensidade) e a intensidade máxima vista. */
    private static List<Double> pulseStarts(NerveStimulation nerve, double tfin, double[] max) {
        List<Double> starts = new ArrayList<>();
        boolean on = false;
        int steps = (int) Math.round(tfin / STEP);
        for (int i = 0; i < steps; i++) {
            double t = i * STEP;
            nerve.step(t);
            double intensity = nerve.intensity();
            max[0] = Math.max(max[0], intensity);
            if (intensity > 0 && !on) {
                starts.add(t);
            }
            on = intensity > 0;
        }
        return starts;
    }

    @Test
    void semFrequenciaUmUnicoPulsoNoInicio() {
        NerveStimulation nerve = new NerveStimulation(100, 1000, 5, 0, NO_SIGNAL, false, 0, 0, 0, STEP);
        double[] max = {0};
        List<Double> starts = pulseStarts(nerve, 2000, max);
        assertThat(starts).hasSize(1);
        assertThat(starts.get(0)).isCloseTo(100, within(1e-9));
        assertThat(max[0]).isEqualTo(5);
    }

    @Test
    void pulsosNaFrequenciaDoNervo() {
        NerveStimulation nerve = new NerveStimulation(0, 1050, 5, 10, NO_SIGNAL, false, 0, 0, 0, STEP);
        double[] max = {0};
        List<Double> starts = pulseStarts(nerve, 2000, max);
        // 10 Hz até 1050 ms: pulsos a cada 100 ms, o último em 1000 ms
        assertThat(starts).hasSize(11);
        for (int k = 0; k < starts.size(); k++) {
            assertThat(starts.get(k)).isCloseTo(100.0 * k, within(STEP + 1e-9));
        }
        // fora da janela, nada
        nerve.step(1500);
        assertThat(nerve.intensity()).isZero();
    }

    @Test
    void oSinalSomaAFrequencia() {
        Modulation plus10Hz = new Modulation(Shape.CONSTANT, 0, 1000, 10, 0, 0, 0, 0, 0);
        NerveStimulation nerve = new NerveStimulation(0, 1000, 5, 10, plus10Hz, false, 0, 0, 0, STEP);
        double[] max = {0};
        List<Double> starts = pulseStarts(nerve, 1000, max);
        assertThat(starts).hasSize(20); // 20 Hz
        assertThat(nerve.period()).isEqualTo(50);
    }

    @Test
    void frequenciasLimitadas() {
        assertThat(new NerveStimulation(0, 1000, 5, 0.0005, NO_SIGNAL, false, 0, 0, 0, STEP).freq()).isZero();
        assertThat(new NerveStimulation(0, 1000, 5, 50_000, NO_SIGNAL, false, 0, 0, 0, STEP).freq()).isEqualTo(1000 / STEP);
        // sem frequência própria, o trem pareado usa a do nervo
        assertThat(new NerveStimulation(0, 1000, 5, 20, NO_SIGNAL, true, 10, 1, 0, STEP).freqPaired()).isEqualTo(20);
    }

    @Test
    void tremPareadoSomaNaIntensidade() {
        // 10 Hz; o pareado começa 50 ms depois, com amplitude 2
        NerveStimulation nerve = new NerveStimulation(0, 1000, 5, 10, NO_SIGNAL, true, 50, 2, 0, STEP);
        nerve.step(0);
        assertThat(nerve.intensity()).isEqualTo(5);
        nerve.step(50);
        assertThat(nerve.intensity()).isZero(); // o início exato do ciclo pareado fica de fora
        nerve.step(50.5);
        assertThat(nerve.intensity()).isEqualTo(2);
        nerve.step(52);
        assertThat(nerve.intensity()).isZero();
        nerve.step(150.5);
        assertThat(nerve.intensity()).isEqualTo(2);
    }

    @Test
    void correnteInjetadaSomaOSinal() {
        Modulation ramp = new Modulation(Shape.RAMP, 100, 1100, 4, 0, 200, 0, 0, 0);
        InjectedCurrent current = new InjectedCurrent(6, ramp);
        assertThat(current.value(50)).isEqualTo(6);
        assertThat(current.value(200)).isCloseTo(8, within(1e-12));
        assertThat(new InjectedCurrent(6, null).value(200)).isEqualTo(6);
    }
    /** A duração do pulso escolhida: o mesmo trem, com pulsos mais curtos ou mais longos. */
    @Test
    void duracaoDoPulsoEscolhida() {
        int[] onSteps = new int[3];
        double[] widths = {NerveStimulation.PULSE_WIDTH, 0.5, 2};
        for (int k = 0; k < widths.length; k++) {
            NerveStimulation nerve = new NerveStimulation(10, 1000, 5, 0, NO_SIGNAL, false, 0, 0, 0, STEP, widths[k]);
            for (int i = 0; i < 2000; i++) {
                nerve.step(i * STEP);
                onSteps[k] += nerve.intensity() > 0 ? 1 : 0;
            }
            assertThat(nerve.pulseWidth()).isEqualTo(widths[k]);
        }
        // de t a t + duração, inclusive (o original): 21 passos em 1 ms
        assertThat(onSteps[0]).isEqualTo(21);
        assertThat(onSteps[1]).isBetween(10, 11);
        assertThat(onSteps[2]).isBetween(40, 41);
        // o construtor antigo é o pulso de 1 ms
        NerveStimulation original = new NerveStimulation(10, 1000, 5, 0, NO_SIGNAL, false, 0, 0, 0, STEP);
        assertThat(original.pulseWidth()).isEqualTo(NerveStimulation.PULSE_WIDTH);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new NerveStimulation(10, 1000, 5, 0, NO_SIGNAL, false, 0, 0, 0, STEP, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
