package br.remoto.core.model.tract;

import br.remoto.core.model.random.Rng;
import br.remoto.core.model.signal.Modulation;
import br.remoto.core.model.signal.Modulation.Shape;
import br.remoto.core.model.tract.TractSpikeGenerator.Distribution;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Taxas dos processos de disparo dos tratos e a modulação da média pelo sinal. */
class TractSpikeGeneratorTest {

    private static final double STEP = 0.05;
    private static final Modulation NO_SIGNAL = new Modulation(Shape.NONE, 0, 0, 0, 0, 0, 0, 0, 0);

    private final List<Double> fired = new ArrayList<>();
    private final List<Double> recorded = new ArrayList<>();

    private final TractSpikeGenerator.Output output = new TractSpikeGenerator.Output() {
        public void fire(double t) {
            fired.add(t);
        }

        public void record(double t) {
            recorded.add(t);
        }

        public void unrecordFirst() {
            recorded.remove(0);
        }
    };

    @BeforeEach
    void seed() {
        Rng.seed(42);
    }

    @AfterEach
    void unseed() {
        Rng.reset();
    }

    private void run(TractSpikeGenerator generator, double tfin) {
        generator.reset();
        int steps = (int) Math.round(tfin / STEP);
        for (int i = 0; i < steps; i++) {
            generator.step(i * STEP);
        }
    }

    @Test
    void poissonDisparaNaTaxaMedia() {
        // 10 ms de intervalo médio: 100 Hz, 1000 disparos em 10 s
        run(new TractSpikeGenerator(Distribution.POISSON, 10, 0, NO_SIGNAL, false, STEP, output), 10_000);
        assertThat(fired.size()).isBetween(880, 1120);
        // o reset anota um disparo em 0 que não chega às sinapses
        assertThat(recorded).hasSize(fired.size() + 1);
        assertThat(recorded.get(0)).isZero();
    }

    @Test
    void gamaIntegraATaxaAteOLimiar() {
        // ordem 4: intervalos com média 20 ms e coeficiente de variação 1/2
        run(new TractSpikeGenerator(Distribution.GAMMA, 20, 4, NO_SIGNAL, false, STEP, output), 20_000);
        assertThat(fired.size()).isBetween(920, 1080);
        double cv = coefficientOfVariation(fired);
        assertThat(cv).isCloseTo(0.5, within(0.06));
    }

    @Test
    void gaussianaAnotaOProximoDisparoSorteado() {
        run(new TractSpikeGenerator(Distribution.GAUSSIAN, 10, 1, NO_SIGNAL, false, STEP, output), 1_000);
        // cada disparo chega às sinapses no primeiro passo depois do tempo sorteado
        assertThat(fired.size()).isBetween(95, 101);
        for (int i = 0; i < fired.size(); i++) {
            assertThat(fired.get(i) - recorded.get(i)).isBetween(0.0, STEP + 1e-9);
        }
        // o trem tem também o próximo disparo, ainda por vir
        assertThat(recorded).hasSize(fired.size() + 1);
    }

    @Test
    void semMediaSoDisparaNaJanelaDoSinal() {
        // sinal constante de 5 ms entre 1 e 2 s, somado ao intervalo
        Modulation isi = new Modulation(Shape.CONSTANT, 1000, 2000, 5, 0, 0, 0, 0, 0);
        run(new TractSpikeGenerator(Distribution.POISSON, 0, 0, isi, false, STEP, output), 3_000);
        assertThat(fired).allSatisfy(t -> assertThat(t).isBetween(1000.0, 2000.001));
        assertThat(fired.size()).isBetween(160, 240);
    }

    @Test
    void sinalDeTaxaSomaHertz() {
        Modulation rate = new Modulation(Shape.CONSTANT, 1000, 2000, 50, 0, 0, 0, 0, 0);
        TractSpikeGenerator tract = new TractSpikeGenerator(Distribution.POISSON, 0, 0, rate, true, STEP, output);
        assertThat(tract.meanValue(1500)).isEqualTo(20); // 50 Hz
        assertThat(tract.meanValue(500)).isEqualTo(2000); // sem taxa: o limite superior

        TractSpikeGenerator withMean = new TractSpikeGenerator(Distribution.POISSON, 20, 0, rate, true, STEP, output);
        assertThat(withMean.meanValue(1500)).isEqualTo(10); // 50 Hz + 50 Hz
        assertThat(withMean.meanValue(500)).isEqualTo(20);
    }

    @Test
    void aMediaNaoPassaDe2kHz() {
        Modulation isi = new Modulation(Shape.CONSTANT, 0, 1000, -50, 0, 0, 0, 0, 0);
        TractSpikeGenerator tract = new TractSpikeGenerator(Distribution.POISSON, 10, 0, isi, false, STEP, output);
        assertThat(tract.meanValue(500)).isEqualTo(0.5);
    }

    private static double coefficientOfVariation(List<Double> spikes) {
        double sum = 0;
        double sumSq = 0;
        int n = spikes.size() - 1;
        for (int i = 1; i < spikes.size(); i++) {
            double isi = spikes.get(i) - spikes.get(i - 1);
            sum += isi;
            sumSq += isi * isi;
        }
        double mean = sum / n;
        return Math.sqrt(sumSq / n - mean * mean) / mean;
    }
}
