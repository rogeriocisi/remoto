package br.remoto.core.model.muscle;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** O abalo depois de um disparo é a função alfa (t/T)·e^(1 − t/T), amostrada a cada passo. */
class AlphaTwitchTest {

    private static final double STEP = 0.05;
    private static final double TPEAK = 40;

    @Test
    void antesDoPrimeiroDisparoEZero() {
        AlphaTwitch twitch = new AlphaTwitch();
        twitch.reset(STEP, TPEAK);

        assertThat(twitch.isStarted()).isFalse();
        assertThat(twitch.value(10)).isZero();
    }

    @Test
    void depoisDeUmDisparoSegueAFuncaoAlfa() {
        AlphaTwitch twitch = new AlphaTwitch();
        twitch.reset(STEP, TPEAK);
        twitch.start(100);

        assertThat(twitch.isStarted()).isTrue();
        // o filtro avança um passo por chamada: é consultado em todos os passos, como no músculo
        for (int m = 1; m <= 4000; m++) {
            double t = m * STEP;
            double alpha = (t / TPEAK) * Math.exp(1 - t / TPEAK);
            assertThat(twitch.value(100 + m)).as("passo %d", m).isCloseTo(alpha, within(1e-9));
        }
    }

    @Test
    void oPicoEhUmNoInstanteTpeak() {
        AlphaTwitch twitch = new AlphaTwitch();
        twitch.reset(STEP, TPEAK);
        twitch.start(0);
        double peak = 0;
        int peakAt = 0;
        for (int i = 1; i < 4000; i++) {
            double v = twitch.value(i);
            if (v > peak) {
                peak = v;
                peakAt = i;
            }
        }
        assertThat(peak).isCloseTo(1.0, within(1e-6));
        assertThat(peakAt * STEP).isCloseTo(TPEAK, within(STEP));
    }
}
