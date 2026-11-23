package br.remoto.core.model.neuron;

import br.remoto.core.model.neuron.AxonNode.Kind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** O nó de Ranvier e o internó de Howells et al. (2012), no ponto do estímulo do nervo. */
class AxonNodeTest {

    private static final double STEP = 0.05;
    private static final double THRESHOLD = 10; // mA, num pulso de 1 ms

    /** Se um pulso de {@code steps} passos com {@code mA} faz o nó disparar (em até 5 ms). */
    private static boolean fires(Kind kind, double mA, int steps) {
        AxonNode node = new AxonNode(kind, STEP, THRESHOLD);
        for (int i = 0; i < steps + 100; i++) {
            if (node.step(i < steps ? mA : 0)) {
                return true;
            }
        }
        return false;
    }

    /** A menor intensidade (mA) que faz disparar um pulso de {@code steps} passos. */
    private static double threshold(Kind kind, int steps) {
        double lo = 0;
        double hi = 1000;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            if (fires(kind, mid, steps)) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return hi;
    }

    @Test
    void semEstimuloFicaNoRepouso() {
        for (Kind kind : Kind.values()) {
            AxonNode node = new AxonNode(kind, STEP, THRESHOLD);
            double rest = node.potential();
            for (int i = 0; i < 20_000; i++) {
                assertThat(node.step(0)).isFalse();
            }
            assertThat(node.potential()).isEqualTo(rest);
        }
        AxonNode motor = new AxonNode(Kind.MOTOR, STEP, THRESHOLD);
        assertThat(motor.potential()).isEqualTo(-84.4);
        assertThat(motor.internodePotential()).isEqualTo(-84.6);
        // as bombas que mantêm o repouso da tabela 1 (o artigo dá −33 e −7,9 pA; ver a classe)
        assertThat(motor.pumps()[0]).isCloseTo(-41.15, within(0.05));
        assertThat(motor.pumps()[1]).isCloseTo(-9.24, within(0.05));
    }

    @Test
    void oLimiarDaConfiguracaoValeParaUmPulsoDe1ms() {
        for (Kind kind : Kind.values()) {
            assertThat(fires(kind, THRESHOLD * 1.001, 20)).as("%s acima", kind).isTrue();
            assertThat(fires(kind, THRESHOLD * 0.999, 20)).as("%s abaixo", kind).isFalse();
        }
    }

    /**
     * A curva intensidade-duração sai do modelo: pela relação de Weiss entre os limiares de 0,2 e
     * 1 ms, a constante de tempo é de cerca de 0,4 ms no motor e 0,5 ms no sensitivo, que é a maior
     * (por isso pulsos longos favorecem os aferentes, e o reflexo H).
     */
    @Test
    void curvaIntensidadeDuracao() {
        double motor = tauSD(Kind.MOTOR);
        double sensory = tauSD(Kind.SENSORY);
        assertThat(motor).isBetween(0.3, 0.45);
        assertThat(sensory).isBetween(0.42, 0.6);
        assertThat(sensory).isGreaterThan(motor);
        assertThat(threshold(Kind.MOTOR, 2)).isGreaterThan(3 * THRESHOLD); // 0,1 ms
        assertThat(threshold(Kind.MOTOR, 40)).isLessThan(THRESHOLD); // 2 ms
    }

    private static double tauSD(Kind kind) {
        double i02 = threshold(kind, 4);
        double i1 = threshold(kind, 20);
        double rheobase = (i02 * 0.2 - i1 * 1.0) / (0.2 - 1.0);
        return i1 * 1.0 / rheobase - 1.0;
    }

    @Test
    void disparaUmPotencialDeAcaoEVoltaAoRepouso() {
        AxonNode node = new AxonNode(Kind.MOTOR, STEP, THRESHOLD);
        double peak = -100;
        int spikes = 0;
        for (int i = 0; i < 40_000; i++) { // 2 s
            if (node.step(i < 20 ? 2 * THRESHOLD : 0)) {
                spikes++;
            }
            peak = Math.max(peak, node.potential());
        }
        assertThat(spikes).isEqualTo(1);
        assertThat(peak).isGreaterThan(20);
        assertThat(node.potential()).isEqualTo(-84.4);
    }

    /** Dois pulsos curtos e fortes: 1 ms depois do primeiro disparo, o nó está refratário; 20 ms depois, não. */
    @Test
    void refratariedade() {
        double strong = 1.5 * threshold(Kind.MOTOR, 2);
        assertThat(spikes(strong, 20)).isEqualTo(1);
        assertThat(spikes(strong, 400)).isEqualTo(2);
    }

    private static int spikes(double mA, int gapSteps) {
        AxonNode node = new AxonNode(Kind.MOTOR, STEP, THRESHOLD);
        int spikes = 0;
        for (int i = 0; i < gapSteps + 200; i++) {
            boolean on = i < 2 || (i >= gapSteps && i < gapSteps + 2);
            if (node.step(on ? mA : 0)) {
                spikes++;
            }
        }
        return spikes;
    }

    @Test
    void limiarTemDeSerPositivo() {
        assertThatThrownBy(() -> new AxonNode(Kind.MOTOR, STEP, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
