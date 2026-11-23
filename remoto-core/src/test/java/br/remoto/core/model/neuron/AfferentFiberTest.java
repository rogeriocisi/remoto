package br.remoto.core.model.neuron;

import br.remoto.core.model.neuron.AfferentFiber.Kind;
import br.remoto.core.model.random.Rng;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** O axônio das fibras aferentes: estímulo do nervo, refratário, condução e disparo pelo receptor. */
class AfferentFiberTest {

    private static final double STEP = 0.05;
    private static final double REFRACTORY = 1.5;

    private static final AfferentSettings SETTINGS = new AfferentSettings() {
        public double step() {
            return STEP;
        }

        public double afferentAxonRefractoryPeriod() {
            return REFRACTORY;
        }
    };

    private final List<Double> delivered = new ArrayList<>();

    @BeforeEach
    void seed() {
        Rng.seed(3);
    }

    @AfterEach
    void unseed() {
        Rng.reset();
    }

    private AfferentFiber fiber(Kind kind) {
        AfferentFiber fiber = new AfferentFiber(kind, SETTINGS, delivered::add);
        fiber.setAxonThreshold(10);
        // 0,6 m até a medula a 60 m/s: 10 ms
        fiber.setLatency(60, 0.6, 0.12);
        fiber.reset();
        return fiber;
    }

    private void run(AfferentFiber fiber, double tfin, java.util.function.DoubleUnaryOperator stimulus) {
        int steps = (int) Math.round(tfin / STEP);
        for (int i = 0; i < steps; i++) {
            double t = i * STEP;
            fiber.deliver(t);
            fiber.generate(t, stimulus.applyAsDouble(t));
        }
    }

    @Test
    void oResetSorteiaOsLimiaresDosTresTipos() {
        Rng.seed(11);
        fiber(Kind.IA);
        Random same = new Random(11);
        for (int i = 0; i < 25 + 30 + 400; i++) {
            same.nextDouble();
        }
        assertThat(Rng.random()).isEqualTo(same.nextDouble());
    }

    @Test
    void latenciasArredondadasParaOPasso() {
        AfferentFiber fiber = fiber(Kind.OTHER);
        assertThat(fiber.latencyStimulusSpinal()).isCloseTo(10, within(1e-9));
        assertThat(fiber.latencyStimulusEndPlate()).isCloseTo(2, within(1e-9));
    }

    @Test
    void oEstimuloDoNervoChegaAMedulaDepoisDaLatencia() {
        AfferentFiber fiber = fiber(Kind.OTHER);
        // um pulso de 1 ms em t = 5 ms acima do limiar
        run(fiber, 100, t -> t >= 5 && t <= 6 ? 12 : 0);
        assertThat(fiber.axonSpikes()).hasSize(1);
        assertThat(fiber.axonSpikes()[0]).isCloseTo(5, within(1e-9));
        assertThat(delivered).hasSize(1);
        assertThat(delivered.get(0)).isCloseTo(15.05, within(1e-9));
    }

    @Test
    void abaixoDoLimiarDoNervoNaoDispara() {
        AfferentFiber fiber = fiber(Kind.OTHER);
        run(fiber, 100, t -> 9.9);
        assertThat(fiber.axonSpikes()).isEmpty();
    }

    @Test
    void estimuloContinuoDisparaACadaRefratario() {
        AfferentFiber fiber = fiber(Kind.OTHER);
        run(fiber, 100, t -> 20);
        double[] spikes = fiber.axonSpikes();
        assertThat(spikes.length).isBetween(60, 68);
        for (int i = 1; i < spikes.length; i++) {
            assertThat(spikes[i] - spikes[i - 1]).isGreaterThan(REFRACTORY);
        }
    }

    @Test
    void oReceptorAcimaDoRecrutamentoDisparaNaTaxaDoExcesso() {
        AfferentFiber fiber = fiber(Kind.IA);
        fiber.setRecruitmentThreshold(10);
        fiber.setFiringRate(() -> 50);
        run(fiber, 20_000, t -> 0);
        // (50 − 10) + 5 Hz: 22,2 ms para integrar o limiar, mais 3,5 ms parados, porque o disparo
        // é anotado 2 ms à frente (a latência até a placa) e o refratário de 1,5 ms conta dali:
        // cerca de 780 disparos em 20 s
        assertThat(fiber.axonSpikes().length).isBetween(730, 830);
    }

    @Test
    void oReceptorAbaixoDoRecrutamentoNaoDispara() {
        AfferentFiber fiber = fiber(Kind.II);
        fiber.setRecruitmentThreshold(60);
        fiber.setFiringRate(() -> 50);
        run(fiber, 2_000, t -> 0);
        assertThat(fiber.axonSpikes()).isEmpty();
    }
    @Test
    void comOrdemEscolhidaOResetSorteiaOLimiarDoTipoComEla() {
        Rng.seed(11);
        AfferentFiber fiber = new AfferentFiber(Kind.IB, SETTINGS, delivered::add, 4);
        fiber.reset();
        Random same = new Random(11);
        for (int i = 0; i < 25 + 4 + 400; i++) { // os outros dois sorteios continuam os do original
            same.nextDouble();
        }
        assertThat(Rng.random()).isEqualTo(same.nextDouble());
        assertThat(fiber.gammaOrder()).isEqualTo(4);
    }

    @Test
    void ordemZeroEhADoOriginal() {
        Rng.seed(5);
        AfferentFiber original = fiber(Kind.II);
        original.setFiringRate(() -> 50);
        run(original, 2_000, t -> 0);

        Rng.seed(5);
        AfferentFiber zero = new AfferentFiber(Kind.II, SETTINGS, t -> { }, 0);
        zero.setAxonThreshold(10);
        zero.setLatency(60, 0.6, 0.12);
        zero.reset();
        zero.setFiringRate(() -> 50);
        run(zero, 2_000, t -> 0);

        assertThat(zero.axonSpikes()).isEqualTo(original.axonSpikes()).isNotEmpty();
    }

    /** Com a taxa constante, a ordem maior deixa os intervalos mais regulares. */
    @Test
    void ordemMaiorDeixaODisparoMaisRegular() {
        double irregular = cv(1);
        double regular = cv(100);
        assertThat(irregular).isGreaterThan(0.6);
        assertThat(regular).isLessThan(0.15);
    }

    private double cv(int order) {
        AfferentFiber fiber = new AfferentFiber(Kind.IA, SETTINGS, t -> { }, order);
        fiber.setAxonThreshold(10);
        fiber.setLatency(60, 0.6, 0.12);
        fiber.reset();
        fiber.setFiringRate(() -> 50);
        run(fiber, 20_000, t -> 0);
        double[] spikes = fiber.axonSpikes();
        double[] isi = new double[spikes.length - 1];
        for (int i = 1; i < spikes.length; i++) {
            isi[i - 1] = spikes[i] - spikes[i - 1];
        }
        double mean = java.util.Arrays.stream(isi).average().orElseThrow();
        double var = java.util.Arrays.stream(isi).map(x -> (x - mean) * (x - mean)).sum() / (isi.length - 1);
        return Math.sqrt(var) / mean;
    }

    @Test
    void ordemNegativaEhRecusada() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new AfferentFiber(Kind.IA, SETTINGS, t -> { }, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
