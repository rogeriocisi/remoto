package br.remoto.core.protocol;

import br.remoto.core.model.neuron.ContinuousMotoneuronMembrane;
import br.remoto.core.model.neuron.MembraneSettings;
import br.remoto.core.model.neuron.MotoneuronMembraneModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * As medidas de um motoneurônio isolado. Onde a resposta é conhecida de antemão — uma membrana
 * passiva, cuja resistência e constante de tempo saem direto dos parâmetros — é contra ela que
 * se compara; no resto, contra o que a eletrofisiologia espera.
 */
class MotoneuronCharacterizationTest {

    private static final double STEP = 0.05;

    private static MembraneSettings settings(double gammaCa) {
        return new MembraneSettings() {
            public double step() { return STEP; }
            public double stepByTwo() { return STEP / 2; }
            public double stepBySix() { return STEP / 6; }
            public double gammaCa() { return gammaCa; }
            public double motoneuronSomaRefractoryPeriod() { return 5; }
        };
    }

    @Test
    void aResistenciaDeEntradaDeUmaMembranaPassivaSaiDosParametros() {
        // Sem condutâncias ativas e sem acoplamento, Rn = 1/gLeakSoma
        MotoneuronCharacterization c = new MotoneuronCharacterization(
                () -> passiva(0.1, 0), STEP);
        assertThat(c.inputResistance()).isCloseTo(1 / 0.1, within(1e-6));

        MotoneuronCharacterization outra = new MotoneuronCharacterization(
                () -> passiva(0.25, 0), STEP);
        assertThat(outra.inputResistance()).isCloseTo(1 / 0.25, within(1e-6));
    }

    @Test
    void aConstanteDeTempoDeUmaMembranaPassivaSaiDosParametros() {
        // tau = C/g, aqui 2 nF / 0.1 uS = 20 ms
        MotoneuronCharacterization c = new MotoneuronCharacterization(
                () -> passiva(0.1, 0, 2.0), STEP);
        assertThat(c.timeConstant()).isCloseTo(20.0, within(2 * STEP));
    }

    @Test
    void comAcoplamentoAResistenciaCaiAbaixoDaDoSomaSozinho() {
        double soTheSoma = new MotoneuronCharacterization(() -> passiva(0.1, 0), STEP).inputResistance();
        double comDendrito = new MotoneuronCharacterization(() -> passiva(0.1, 0.2), STEP).inputResistance();
        assertThat(comDendrito).isLessThan(soTheSoma).isPositive();
    }

    @Test
    void aReobaseSeparaODisparoDoSilencio() {
        Supplier<MotoneuronMembraneModel> excitavel = MotoneuronCharacterizationTest::excitavel;
        MotoneuronCharacterization c = new MotoneuronCharacterization(excitavel, STEP);

        double rheobase = c.rheobase(40, 0.01);
        assertThat(rheobase).isNotNaN().isPositive();

        // logo acima dispara, logo abaixo não
        assertThat(c.firingRate(rheobase * 1.2)).isPositive();
        assertThat(c.firingRate(rheobase * 0.8)).isZero();
    }

    @Test
    void aCurvaFiNaoDecresceENaoPassaDoTetoDoRefratario() {
        MotoneuronCharacterization c = new MotoneuronCharacterization(
                MotoneuronCharacterizationTest::excitavel, STEP);
        List<MotoneuronCharacterization.Point> curva = c.fiCurve(10, 30, 5);

        assertThat(curva).hasSize(5);
        assertThat(curva.get(0).currentNa()).isEqualTo(10.0);
        assertThat(curva.get(4).currentNa()).isCloseTo(30.0, within(1e-9));

        List<Double> taxas = curva.stream().map(MotoneuronCharacterization.Point::rateHz).toList();
        for (int i = 1; i < taxas.size(); i++) {
            // no teto, os instantes de disparo ficam quantizados pelo passo de integração e a
            // taxa oscila na terceira casa; a tolerância cobre essa quantização, não o modelo
            assertThat(taxas.get(i)).isGreaterThanOrEqualTo(taxas.get(i - 1) * 0.999);
        }
        // o período refratário de 5 ms põe um teto de 200 disparos por segundo, e com estes
        // parâmetros provisórios a curva já chega nele em 10 nA: a faixa útil está abaixo disso
        assertThat(taxas).allSatisfy(r -> assertThat(r).isLessThanOrEqualTo(200.0));
        assertThat(taxas.get(0)).isGreaterThan(150.0);
    }

    @Test
    void aFaixaUtilDaCurvaFiFicaLogoAcimaDaReobase() {
        MotoneuronCharacterization c = new MotoneuronCharacterization(
                MotoneuronCharacterizationTest::excitavel, STEP);
        double rheobase = c.rheobase(40, 0.01);

        double logoAcima = c.firingRate(rheobase * 1.02);
        double bemAcima = c.firingRate(rheobase * 4);
        assertThat(logoAcima).isPositive();
        assertThat(bemAcima).isGreaterThan(logoAcima);
    }

    @Test
    void semCorrenteNaoHaReobaseNemDisparo() {
        MotoneuronCharacterization c = new MotoneuronCharacterization(
                () -> passiva(0.1, 0.2), STEP);
        assertThat(c.rheobase(40, 0.01)).isNaN();
        assertThat(c.firingRate(10)).isZero();
    }

    @Test
    void aAhpTemAmplitudeEDuracaoPositivasDepoisDeUmDisparo() {
        MotoneuronCharacterization c = new MotoneuronCharacterization(
                MotoneuronCharacterizationTest::excitavel, STEP);
        double[] ahp = c.afterHyperpolarization(30, 1, 200);

        assertThat(ahp[0]).isNotNaN().isPositive();
        assertThat(ahp[1]).isNotNaN().isPositive();
    }

    @Test
    void oConjuntoDeMedidasSaiCompleto() {
        MotoneuronCharacterization c = new MotoneuronCharacterization(
                MotoneuronCharacterizationTest::excitavel, STEP);
        MotoneuronCharacterization.Result r = c.all(40, 10, 20, 5);

        assertThat(r.inputResistanceMOhm()).isPositive();
        assertThat(r.timeConstantMs()).isPositive();
        assertThat(r.rheobaseNa()).isPositive();
        assertThat(r.fiCurve()).hasSize(3);
    }

    /** Membrana só com fuga no soma: a resposta é conhecida em forma fechada. */
    private static MotoneuronMembraneModel passiva(double gLeakSoma, double gCoupling) {
        return passiva(gLeakSoma, gCoupling, 1.0);
    }

    private static MotoneuronMembraneModel passiva(double gLeakSoma, double gCoupling, double capacitance) {
        ContinuousMotoneuronMembrane m = new ContinuousMotoneuronMembrane(settings(0));
        m.setCapacitanceSoma(capacitance);
        m.setGLeakSoma(gLeakSoma);
        m.setCapacitanceDend(1.0);
        m.setGLeakDend(0.05);
        m.setGCoupling(gCoupling);
        m.setThreshold(1000);
        m.setThresholdCa(1000);
        return m;
    }

    /** Membrana com condutâncias na ordem de grandeza de um motoneurônio do ReMoto. */
    private static MotoneuronMembraneModel excitavel() {
        ContinuousMotoneuronMembrane m = new ContinuousMotoneuronMembrane(settings(0.2));
        m.setCapacitanceSoma(1.0);
        m.setGLeakSoma(0.1);
        m.setCapacitanceDend(1.0);
        m.setGLeakDend(0.05);
        m.setGCoupling(0.2);
        m.setGNaBar(10.0);
        m.setGNapBar(0.05);
        m.setGKfBar(5.0);
        m.setGKsBar(1.0);
        m.setGCaLBar(0.1);
        m.setThreshold(30);
        m.setThresholdCa(1000);
        return m;
    }
}
