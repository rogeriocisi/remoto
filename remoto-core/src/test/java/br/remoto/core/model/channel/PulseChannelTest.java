package br.remoto.core.model.channel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Os canais de pulso seguem soluções fechadas: no pulso a comporta sobe para 1 com taxa alpha,
 * fora dele cai para 0 com taxa beta, sempre a partir do valor no instante da última troca.
 */
class PulseChannelTest {

    private static final double GMAX = 2.0;
    private static final double E = -10.0;
    private static final double PULSE = 0.6;
    private static final double ALPHA = 22.0;
    private static final double BETA = 4.0;

    @Test
    void semDisparoOPotassioFicaFechado() {
        GatedChannel k = GatedChannel.fastPotassium(GMAX, E, PULSE, ALPHA, BETA);

        assertThat(k.value(5)).isZero();
        assertThat(k.current(5, -70)).isZero();
    }

    @Test
    void oPotassioAbreNoPulsoEFechaDepois() {
        GatedChannel k = GatedChannel.fastPotassium(GMAX, E, PULSE, ALPHA, BETA);
        k.start(10);

        double n = 1 - Math.exp(-ALPHA * 0.3);
        assertThat(k.value(10.3)).isCloseTo(GMAX * Math.pow(n, 4), within(1e-12));
        assertThat(k.current(10.3, -70)).isCloseTo(GMAX * Math.pow(n, 4) * (E + 70), within(1e-12));

        // o pulso terminou: a comporta cai a partir do último valor calculado, n(10.3)
        k.value(10.7);
        assertThat(k.value(11.7)).isCloseTo(GMAX * Math.pow(n * Math.exp(-BETA * 1.0), 4), within(1e-12));
    }

    @Test
    void oSodioAtivaEInativaNoMesmoPulso() {
        SodiumChannel na = new SodiumChannel(GMAX, 50, PULSE, ALPHA, BETA, 0.5, 2.0);
        na.value(10); // h relaxa para 1 desde t = 0, como no original
        na.start(10);

        double m = 1 - Math.exp(-ALPHA * 0.2);
        double h = (1 - Math.exp(-0.5 * 10)) * Math.exp(-2.0 * 0.2);
        assertThat(na.value(10.2)).isCloseTo(GMAX * m * m * m * h, within(1e-12));
        assertThat(na.lastValue()).isEqualTo(na.value(10.2));
    }

    @Test
    void oCalcioEhLinearNaComporta() {
        GatedChannel ca = GatedChannel.calcium(GMAX, 140, PULSE, ALPHA, BETA);
        ca.start(0);

        assertThat(ca.value(0.4)).isCloseTo(GMAX * (1 - Math.exp(-ALPHA * 0.4)), within(1e-12));

        ca.reset();
        assertThat(ca.lastValue()).isZero();
    }
}
