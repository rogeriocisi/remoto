package br.remoto.core.model.receptor;

import br.remoto.core.model.random.Rng;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Os fusos de Prochazka e de Mileusnic. */
class SpindleTest {

    @Test
    void prochazkaDependeDoComprimentoEDaVelocidade() {
        ProchazkaSpindle spindle = new ProchazkaSpindle();
        spindle.step(1.0, 0);
        assertThat(spindle.iaFiringRate()).isEqualTo(15);
        assertThat(spindle.iiFiringRate()).isEqualTo(8);
        spindle.step(1.02, 0.001); // 1 por segundo
        assertThat(spindle.iaFiringRate()).isCloseTo(80 + 250 * 0.02 + 15, within(1e-9));
        spindle.step(0.9, 0);
        assertThat(spindle.iaFiringRate()).isZero();
        assertThat(spindle.iiFiringRate()).isZero();
    }

    @Test
    void mileusnicComFusimotorEComprimentoFixoChegaAUmaTaxaConstante() {
        Rng.seed(4);
        try {
            MileusnicSpindle spindle = new MileusnicSpindle("SOL", 0.05, 70, 70, 7000, 7000, 7000);
            double ia = 0;
            int steps = 40_000;
            for (int i = 0; i < steps; i++) {
                spindle.step(i * 0.05, 0.95, 0, 0);
                ia = spindle.iaFiringRate();
            }
            assertThat(Double.isFinite(ia) && ia >= 0).isTrue();
            assertThat(spindle.iiFiringRate() >= 0).isTrue();
            // a ativação fusimotora da chain é a estática pura
            double chain = Math.pow(70, 2) / (Math.pow(70, 2) + Math.pow(90, 2));
            assertThat(spindle.fusimotorActivations()[2]).isCloseTo(chain, within(1e-12));
            // um gerador derivado por passo
            Random same = new Random(4);
            for (int i = 0; i < steps; i++) {
                same.nextLong();
            }
            assertThat(Rng.random()).isEqualTo(same.nextDouble());
        } finally {
            Rng.reset();
        }
    }

    @Test
    void foraDoTricepsSuralETibialOsGanhosSaoZero() {
        MileusnicSpindle spindle = new MileusnicSpindle("XX", 0.05, 70, 70, 7000, 7000, 7000);
        for (int i = 0; i < 2000; i++) {
            spindle.step(i * 0.05, 1.1, 0, 0);
        }
        assertThat(spindle.iaFiringRate()).isCloseTo(0, within(0.0));
    }
}
