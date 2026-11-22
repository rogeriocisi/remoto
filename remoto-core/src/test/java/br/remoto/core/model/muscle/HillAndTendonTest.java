package br.remoto.core.model.muscle;

import br.remoto.core.model.random.Rng;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** O tendão e o músculo de Hill sem disparos. */
class HillAndTendonTest {

    private static final HillMuscle.Parameters SOL =
            new HillMuscle.Parameters(0.049, 28.3, 0.28, 3586, 0.526, 0.005, 5);

    @Test
    void forcaDoTendaoSegueALeiExponencialSuavizada() {
        Tendon tendon = new Tendon(1000, 0.28, 27.8, 0.0047, 0.964);
        double l0 = 1.033 * 0.28;
        tendon.step(0, 0.32, 0.049, 0);
        double length = 0.32 - 0.049;
        assertThat(tendon.length()).isCloseTo(length, within(1e-15));
        double norm = length / l0;
        double expected = 27.8 * 0.0047 * Math.log(Math.exp((norm - 0.964) / 0.0047) + 1) * 1000;
        assertThat(tendon.force()).isCloseTo(expected, within(1e-9));
    }

    @Test
    void comprimentoInicialFixoPorMusculo() {
        HillMuscle muscle = new HillMuscle("SOL", new MotorUnit[0], SOL, 0.05, 0.05, () -> 0);
        muscle.setInitialLengthNorm();
        assertThat(muscle.lengthNorm()).isEqualTo(0.97026);
        assertThat(muscle.length()).isEqualTo(0.97026 * 0.049);
        HillMuscle other = new HillMuscle("XX", new MotorUnit[0], SOL, 0.05, 0.05, () -> 0);
        other.setInitialLengthNorm();
        assertThat(other.lengthNorm()).isEqualTo(1);
    }

    @Test
    void cadaPassoTiraUmGeradorDerivadoDaAleatoriedade() {
        Rng.seed(9);
        try {
            HillMuscle muscle = new HillMuscle("SOL", new MotorUnit[0], SOL, 0.05, 0.05, () -> 100);
            muscle.setInitialLengthNorm();
            muscle.step(0);
            muscle.step(0.05);
            Random same = new Random(9);
            same.nextLong();
            same.nextLong();
            assertThat(Rng.random()).isEqualTo(same.nextDouble());
        } finally {
            Rng.reset();
        }
    }
}
