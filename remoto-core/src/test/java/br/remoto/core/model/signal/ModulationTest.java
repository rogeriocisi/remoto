package br.remoto.core.model.signal;

import br.remoto.core.model.random.Rng;
import br.remoto.core.model.signal.Modulation.Shape;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** As formas dos sinais modulantes, dentro e fora da janela [tini, tfin]. */
class ModulationTest {

    @AfterEach
    void unseed() {
        Rng.reset();
    }

    private static Modulation of(Shape shape, double amp, double freq, double width) {
        return new Modulation(shape, 100, 1100, amp, freq, width, 0, 0, 0);
    }

    @Test
    void foraDaJanelaValeZero() {
        Modulation constant = of(Shape.CONSTANT, 5, 0, 0);
        assertThat(constant.value(99.9)).isZero();
        assertThat(constant.value(100)).isEqualTo(5);
        assertThat(constant.value(1100.0005)).isEqualTo(5);
        assertThat(constant.value(1100.002)).isZero();
    }

    @Test
    void codigosDaConfiguracao() {
        assertThat(Shape.of("sine")).isEqualTo(Shape.SINE);
        assertThat(Shape.of("stochastic")).isEqualTo(Shape.STOCHASTIC_GAMMA);
        assertThat(Shape.of("AM")).isEqualTo(Shape.AM);
        assertThat(Shape.of("desconhecido")).isEqualTo(Shape.NONE);
        assertThat(Shape.of(null)).isEqualTo(Shape.UNDEFINED);
        assertThat(of(Shape.NONE, 5, 10, 10).value(500)).isZero();
    }

    @Test
    void semCodigoFalhaSoDentroDaJanela() {
        Modulation undefined = of(Shape.UNDEFINED, 5, 0, 0);
        assertThat(undefined.value(50)).isZero();
        assertThatThrownBy(() -> undefined.value(500)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void senoideComAtrasoEDegrauDepoisDe120s() {
        Modulation sine = new Modulation(Shape.SINE, 0, 200_000, 2, 10, 0, 25, 0, 0);
        // 10 Hz, atraso de 25 ms (um quarto de período): em t = 50 ms, sen(π/2)
        assertThat(sine.value(50)).isCloseTo(2, within(1e-12));
        assertThat(sine.value(150_050)).isCloseTo(2 - 7, within(1e-9));
    }

    @Test
    void amModulaASenoide() {
        Modulation am = new Modulation(Shape.AM, 0, 10_000, 2, 10, 0, 0, 1, 0.5);
        double t = 25;
        double expected = 2 * Math.sin(2 * Math.PI * 10 * t / 1000) * (1 + 0.5 * Math.sin(2 * Math.PI * t / 1000));
        assertThat(am.value(t)).isCloseTo(expected, within(1e-12));
    }

    @Test
    void quadradaDisparaNoInicioDeCadaPeriodo() {
        Modulation square = of(Shape.SQUARE, 3, 10, 20); // período de 100 ms, pulso de 20 ms
        assertThat(square.value(100)).isZero(); // o início exato do ciclo fica de fora
        assertThat(square.value(110)).isEqualTo(3);
        assertThat(square.value(130)).isZero();
        assertThat(square.value(215)).isEqualTo(3);
    }

    @Test
    void customIgnoraAAmplitude() {
        Modulation custom = of(Shape.CUSTOM, 99, 10, 20);
        assertThat(custom.value(110)).isEqualTo(12.35);
        Modulation late = new Modulation(Shape.CUSTOM, 100, 20_000, 99, 10, 20, 0, 0, 0);
        assertThat(late.value(10_110)).isEqualTo(13.75);
    }

    @Test
    void rampaTrianguloETrapezio() {
        assertThat(of(Shape.RAMP, 4, 0, 200).value(200)).isCloseTo(2, within(1e-12));
        assertThat(of(Shape.RAMP, 4, 0, 200).value(400)).isEqualTo(4);
        assertThat(of(Shape.TRIANGLE, 4, 0, 200).value(300)).isCloseTo(4, within(1e-12));
        assertThat(of(Shape.TRIANGLE, 4, 0, 200).value(400)).isCloseTo(2, within(1e-12));
        assertThat(of(Shape.TRAPEZOID, 4, 0, 200).value(400)).isEqualTo(4);
        assertThat(of(Shape.TRAPEZOID, 4, 0, 200).value(600)).isCloseTo(2, within(1e-12));
    }

    @Test
    void aleatoriaEGamaSorteiamACadaAvaliacao() {
        Rng.seed(7);
        double random = of(Shape.RANDOM, 2, 0, 0).value(500);
        double gamma = of(Shape.STOCHASTIC_GAMMA, 1.5, 0, 3).value(500);

        Rng.seed(7);
        java.util.Random same = new java.util.Random(7);
        assertThat(random).isEqualTo(2 * same.nextDouble());
        double product = same.nextDouble() * same.nextDouble() * same.nextDouble();
        assertThat(gamma).isEqualTo(-1.5 * Math.log(product));
    }
}
