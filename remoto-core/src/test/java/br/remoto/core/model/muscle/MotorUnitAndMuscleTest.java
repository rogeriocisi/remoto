package br.remoto.core.model.muscle;

import br.remoto.core.model.random.Rng;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** A unidade motora (MUAP e posição) e o músculo SOCDS (abalo, saturação, soma). */
class MotorUnitAndMuscleTest {

    private static final double STEP = 0.05;

    private static MotorUnit unit(int index, String cd, double... spikes) {
        List<Double> train = new ArrayList<>();
        for (double s : spikes) {
            train.add(s);
        }
        return new MotorUnit(index, "S", cd, new MotorUnit.EndPlate() {
            public int size() {
                return train.size();
            }

            public double spike(int i) {
                return train.get(i);
            }
        });
    }

    @Test
    void muapDeSegundaOrdemTemPicoTresConstantesDepoisDoDisparo() {
        MotorUnit mu = unit(1, "MN S 1", 10);
        mu.setAmpEMG(2);
        mu.setLambdaEMG(1);
        mu.resetEmg(false);
        double peak = Double.NEGATIVE_INFINITY;
        double tPeak = 0;
        for (int i = 0; i < 400; i++) {
            double t = i * STEP;
            double v = mu.emg(t);
            if (v > peak) {
                peak = v;
                tPeak = t;
            }
        }
        // (1 − 2·u²)·e^(−u²), u = (t − 10 − 3λ)/λ: máximo 1 em t = 13 ms
        assertThat(peak).isCloseTo(2, within(1e-9));
        assertThat(tPeak).isCloseTo(13, within(STEP));
    }

    @Test
    void muapAtenuadoPelaDistanciaAtePele() {
        MotorUnit mu = unit(1, "MN S 1", 10);
        mu.setAmpEMG(2);
        mu.setLambdaEMG(1);
        mu.linearSpatialDistribution(10, 2, 5, 0, 1, 2);
        // a segunda de duas unidades fica na espessura do músculo: 10 mm
        assertThat(mu.posY()).isEqualTo(10);
        assertThat(mu.attenuationToSkin()).isCloseTo(Math.exp(-2), within(1e-12));
        mu.resetEmg(true);
        double peak = 0;
        for (int i = 0; i < 400; i++) {
            peak = Math.max(peak, mu.emg(i * STEP));
        }
        assertThat(peak).isCloseTo(2 * Math.exp(-2), within(1e-9));
    }

    @Test
    void posicaoSorteadaFicaDentroDoMusculo() {
        Rng.seed(5);
        try {
            for (int k = 0; k < 200; k++) {
                MotorUnit mu = unit(1, "MN S 1");
                mu.randomSpatialDistribution(10, 2, 5, 0.1);
                // círculo de raio 5 centrado a 7 mm da pele
                double dy = mu.posY() - 7;
                assertThat(dy * dy + mu.posZ() * mu.posZ()).isLessThanOrEqualTo(25 + 1e-9);
            }
        } finally {
            Rng.reset();
        }
    }

    @Test
    void umDisparoDaUmAbaloSaturado() {
        MotorUnit mu = unit(1, "MN S 1", 5);
        mu.setTpeak(40);
        mu.setGpeak(2);
        mu.setTwTet(3);
        mu.setB(0.5);
        CriticallyDampedMuscle muscle = new CriticallyDampedMuscle(new MotorUnit[] {mu}, STEP, STEP);
        assertThat(muscle.maximumMuscleForce()).isEqualTo(6);
        double peak = 0;
        double tPeak = 0;
        for (int i = 0; i < 4000; i++) {
            double t = i * STEP;
            muscle.step(t);
            if (muscle.force() > peak) {
                peak = muscle.force();
                tPeak = t;
            }
        }
        // o abalo normalizado chega a 1 no Tpeak; a saturação dá 2/(1 + e^(−0,5)) − 1
        double saturated = 2 / (1 + Math.exp(-0.5)) - 1;
        assertThat(peak).isCloseTo(saturated * 3 * 2, within(1e-3));
        assertThat(tPeak).isCloseTo(5 + 40, within(0.2));
        assertThat(muscle.lengthNorm()).isEqualTo(1);
        assertThat(muscle.stretchVelocity()).isZero();
    }

    @Test
    void semDisparosForcaZeroEUnidadesComMesmoCodigoContamAPrimeira() {
        MotorUnit a = unit(1, "MN S 1", 5);
        MotorUnit b = unit(2, "MN S 1", 5);
        for (MotorUnit u : new MotorUnit[] {a, b}) {
            u.setTpeak(40);
            u.setGpeak(1);
            u.setTwTet(1);
            u.setB(1);
        }
        CriticallyDampedMuscle twice = new CriticallyDampedMuscle(new MotorUnit[] {a, b}, STEP, STEP);
        CriticallyDampedMuscle once = new CriticallyDampedMuscle(new MotorUnit[] {unit(1, "MN S 1", 5)}, STEP, STEP);
        once.units()[0].setTpeak(40);
        once.units()[0].setGpeak(1);
        once.units()[0].setTwTet(1);
        once.units()[0].setB(1);
        CriticallyDampedMuscle silent = new CriticallyDampedMuscle(new MotorUnit[] {unit(1, "MN S 1")}, STEP, STEP);
        for (int i = 0; i < 2000; i++) {
            double t = i * STEP;
            twice.step(t);
            once.step(t);
            silent.step(t);
        }
        // a primeira unidade com o código entra duas vezes e avança o abalo dois passos por passo
        assertThat(twice.force()).isPositive();
        assertThat(silent.force()).isZero();
    }
}
