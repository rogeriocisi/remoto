package br.remoto.core.model.stimulus;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** O limiar por carga com vazamento: a curva intensidade-duração de Lapicque. */
class LeakyChargeTest {

    private static final double STEP = 0.05;
    private static final double TAU = 0.5;
    private static final double THRESHOLD = 12; // o limiar de um pulso de 1 ms

    /** O maior valor equivalente durante um pulso retangular de {@code steps} passos. */
    private static double peak(double intensity, int steps) {
        LeakyCharge charge = new LeakyCharge(TAU, STEP);
        double peak = 0;
        for (int i = 0; i < steps; i++) {
            peak = Math.max(peak, charge.step(intensity));
        }
        for (int i = 0; i < 100; i++) {
            peak = Math.max(peak, charge.step(0));
        }
        return peak;
    }

    @Test
    void umPulsoDe1msNoLimiarChegaExatamenteAoLimiar() {
        assertThat(peak(THRESHOLD, 20)).isCloseTo(THRESHOLD, within(1e-9));
    }

    @Test
    void cadaDuracaoPrecisaDaIntensidadeDaCurva() {
        for (int steps : new int[] {2, 4, 10, 20, 40, 200}) {
            double duration = steps * STEP;
            double needed = LeakyCharge.threshold(THRESHOLD, duration, TAU);
            assertThat(peak(needed, steps)).as("%s ms", duration).isCloseTo(THRESHOLD, within(1e-9));
            assertThat(peak(needed * 0.99, steps)).isLessThan(THRESHOLD);
        }
        // pulsos curtos precisam de mais corrente; os longos, de menos que o de 1 ms
        assertThat(LeakyCharge.threshold(THRESHOLD, 0.1, TAU)).isGreaterThan(4 * THRESHOLD); // 4,8 vezes
        assertThat(LeakyCharge.threshold(THRESHOLD, 2, TAU)).isLessThan(THRESHOLD);
    }

    @Test
    void reobaseECronaxia() {
        double rheobase = LeakyCharge.threshold(THRESHOLD, 1e6, TAU);
        assertThat(rheobase).isCloseTo(THRESHOLD * (1 - Math.exp(-1 / TAU)), within(1e-9));
        // na cronaxia (tau ln 2), o dobro da reobase
        assertThat(LeakyCharge.threshold(THRESHOLD, TAU * Math.log(2), TAU)).isCloseTo(2 * rheobase, within(1e-9));
    }

    @Test
    void depoisDoPulsoACargaVaza() {
        LeakyCharge charge = new LeakyCharge(TAU, STEP);
        double end = 0;
        for (int i = 0; i < 20; i++) {
            end = charge.step(THRESHOLD);
        }
        double later = 0;
        for (int i = 0; i < 10; i++) { // 0,5 ms depois: caiu e^-1
            later = charge.step(0);
        }
        assertThat(later).isCloseTo(end * Math.exp(-0.5 / TAU), within(1e-9));
    }

    @Test
    void constanteDeTempoTemDeSerPositiva() {
        assertThatThrownBy(() -> new LeakyCharge(0, STEP)).isInstanceOf(IllegalArgumentException.class);
    }
}
