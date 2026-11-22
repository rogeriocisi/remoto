package br.remoto.core.model.joint;

import br.remoto.core.model.random.Rng;
import br.remoto.core.model.receptor.CragoTendonOrgan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Tornozelo isométrico, geometria dos musculotendões e o OTG de Crago. */
class AnkleAndTendonOrganTest {

    @Test
    void tornozeloIsometricoSomaForcaVezesBraco() {
        Rng.seed(1);
        try {
            IsometricAnkle ankle = new IsometricAnkle(10, 0);
            assertThat(ankle.angle()).isZero(); // antes do primeiro passo
            IsometricAnkle.Actuator sol = actuator(100, -0.05);
            IsometricAnkle.Actuator ta = actuator(20, 0.04);
            ankle.step(new IsometricAnkle.Actuator[] {sol, null, ta});
            assertThat(ankle.angle()).isCloseTo(Math.toRadians(10), within(1e-15));
            assertThat(ankle.torque()).isCloseTo(-5 + 0.8, within(1e-12));
        } finally {
            Rng.reset();
        }
    }

    private static IsometricAnkle.Actuator actuator(double force, double arm) {
        return new IsometricAnkle.Actuator() {
            public double force() {
                return force;
            }

            public double momentArm() {
                return arm;
            }
        };
    }

    @Test
    void geometriaDoSoleoEmZeroGrau() {
        MusculotendonGeometry sol = new MusculotendonGeometry("SOL");
        sol.updateLength(0, 0);
        sol.updateMomentArm(0, 0);
        assertThat(sol.length()).isEqualTo(0.32284);
        assertThat(sol.momentArm()).isEqualTo(-0.041363);
    }

    @Test
    void gastrocnemioSoTemCoeficientesComJoelhoEmZero() {
        MusculotendonGeometry mg = new MusculotendonGeometry("MG");
        mg.updateLength(0, 0);
        assertThat(mg.length()).isEqualTo(0.46349);
        mg.updateLength(0, Math.toRadians(90));
        assertThat(mg.length()).isZero();
        MusculotendonGeometry other = new MusculotendonGeometry("XX");
        other.updateLength(10, 0);
        assertThat(other.length()).isZero();
    }

    @Test
    void otgDeCragoComecaEmZeroETemGanhoUnitario() {
        double step = 0.05;
        CragoTendonOrgan gto = new CragoTendonOrgan(step, 2);
        gto.step(12);
        assertThat(gto.ibFiringRate()).isZero(); // a primeira chamada não usa a força
        for (int i = 0; i < 200_000; i++) {
            gto.step(12);
        }
        double expected = 40 * 60 * Math.log(12 / 4.0 + 1) / 2;
        assertThat(gto.ibFiringRate()).isCloseTo(expected, within(expected * 1e-6));
    }
}
