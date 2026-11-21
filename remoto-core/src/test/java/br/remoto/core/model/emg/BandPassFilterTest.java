package br.remoto.core.model.emg;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** O passa-faixa do EMG (50 a 1000 Hz): corta o nível constante e deixa passar o meio da faixa. */
class BandPassFilterTest {

    private static final double T = 0.05e-3; // passo de 0,05 ms

    @Test
    void quaseCortaONivelConstante() {
        BandPassFilter filter = new BandPassFilter(50, 1000, T);
        double y = 0;
        for (int i = 0; i < 40_000; i++) {
            y = filter.next(1.0);
        }
        // a discretização deixa um ganho DC de cerca de 0,8%, no filtro original também
        assertThat(Math.abs(y)).isLessThan(0.02);
    }

    @Test
    void deixaPassarOMeioDaFaixa() {
        assertThat(amplitude(250)).isBetween(0.7, 1.3);
        assertThat(amplitude(5)).isLessThan(0.3);
        assertThat(amplitude(8000)).isLessThan(0.3);
    }

    /** Amplitude de saída, em regime, para uma senoide de amplitude 1. */
    private static double amplitude(double hz) {
        BandPassFilter filter = new BandPassFilter(50, 1000, T);
        double max = 0;
        int n = 200_000;
        for (int i = 0; i < n; i++) {
            double y = filter.next(Math.sin(2 * Math.PI * hz * i * T));
            if (i > n / 2) {
                max = Math.max(max, Math.abs(y));
            }
        }
        return max;
    }
}
