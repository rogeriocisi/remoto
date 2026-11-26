package br.remoto.core.model.neuron;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * A membrana de cinética contínua, sem rede em volta. Onde há solução fechada, é contra ela que
 * se compara: o decaimento passivo do potencial e a trajetória de uma comporta com o potencial
 * congelado.
 */
class ContinuousMotoneuronMembraneTest {

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
    void oPotencialPassivoSegueAExponencialDaSolucaoFechada() {
        // Só a fuga: dV/dt = −(g/C)·V, logo V(t) = V₀·exp(−t·g/C)
        ContinuousMotoneuronMembrane mn = new ContinuousMotoneuronMembrane(settings(0));
        mn.setCapacitanceSoma(2.0);
        mn.setGLeakSoma(0.1);
        mn.setCapacitanceDend(1.0);
        mn.setGLeakDend(0.05);
        mn.setGCoupling(0);
        mn.setThreshold(1000);
        mn.reset();
        mn.setVs(10.0);

        double tau = 2.0 / 0.1;
        for (int i = 0; i < 200; i++) {
            mn.stepSoma(i * STEP);
            double esperado = 10.0 * Math.exp(-((i + 1) * STEP) / tau);
            assertThat(mn.vs()).isCloseTo(esperado, within(1e-9));
        }
    }

    @Test
    void comPotencialCongeladoAComportaSegueAExponencialDaSolucaoFechada() {
        // Euler exponencial é exato para v constante: L(t) = L∞ + (L₀ − L∞)·exp(−t/τ)
        ContinuousMotoneuronMembrane mn = passivo(0);
        mn.setLTau(25.0);
        mn.setVTraubDend(35.0);
        mn.setGCaLBar(0);          // sem corrente de cálcio o dendrito não se move
        mn.setGLeakDend(0);        // nem por fuga
        mn.setGCoupling(0);
        mn.reset();

        double lInicial = mn.gateL();
        double lFinal = 1 / (Math.exp(-((0 - 35.0) + 30.0)) + 1);
        for (int i = 0; i < 400; i++) {
            mn.stepDendrite(i * STEP);
            double esperado = lFinal + (lInicial - lFinal) * Math.exp(-((i + 1) * STEP) / 25.0);
            assertThat(mn.gateL()).isCloseTo(esperado, within(1e-12));
        }
        assertThat(mn.vd()).isZero();
    }

    @Test
    void noRepousoAsComportasFicamNoEstadoEstacionario() {
        ContinuousMotoneuronMembrane mn = passivo(0);
        mn.reset();

        // Valores do estado estacionário em v = 0, pelas taxas da formulação de origem
        assertThat(mn.gateM()).isCloseTo(0.0102, within(5e-4));
        assertThat(mn.gateH()).isCloseTo(0.9987, within(5e-4));
        assertThat(mn.gateN()).isCloseTo(0.0585, within(5e-4));
        assertThat(mn.gateP()).isCloseTo(0.0709, within(5e-4));
        assertThat(mn.gateR()).isCloseTo(0.0, within(1e-3));

        // e permanecem lá enquanto o potencial não se mover
        for (int i = 0; i < 500; i++) {
            mn.stepSoma(i * STEP);
        }
        assertThat(mn.gateH()).isCloseTo(0.9987, within(5e-4));
        assertThat(mn.gateM()).isCloseTo(0.0102, within(5e-4));
    }

    @Test
    void aComportaDoCalcioAbreMeioCaminhoNoPotencialDeMeiaAtivacao() {
        ContinuousMotoneuronMembrane mn = passivo(1);
        mn.setVTraubDend(35.0);
        mn.setLTau(0.001);         // rápida o bastante para seguir o potencial imposto
        mn.setGCaLBar(0);
        mn.setGLeakDend(0);
        mn.setGCoupling(0);
        mn.reset();

        // meia ativação em v = vTraubDend − 30 = 5 mV acima do repouso
        mn.setVd(5.0);
        for (int i = 0; i < 200; i++) {
            mn.stepDendrite(i * STEP);
        }
        assertThat(mn.gateL()).isCloseTo(0.5, within(1e-6));

        mn.setVd(15.0);
        for (int i = 0; i < 200; i++) {
            mn.stepDendrite(i * STEP);
        }
        assertThat(mn.gateL()).isGreaterThan(0.99);
    }

    @Test
    void aCorrenteDeCalcioEscalaComGama() {
        ContinuousMotoneuronMembrane semGama = passivo(0);
        ContinuousMotoneuronMembrane comGama = passivo(0.5);
        semGama.setGCaLBar(1.0);
        comGama.setGCaLBar(1.0);
        semGama.reset();
        comGama.reset();

        assertThat(semGama.gCaL()).isZero();
        assertThat(comGama.gCaL()).isCloseTo(0.5 * comGama.gateL(), within(1e-12));
    }

    @Test
    void aPicNaoEEventoENadaMudaAoConsultaLa() {
        ContinuousMotoneuronMembrane mn = passivo(1);
        mn.reset();
        double antes = mn.gateL();
        mn.checkPersistentInwardCurrent(10.0);
        assertThat(mn.gateL()).isEqualTo(antes);
        assertThat(mn.tDendritePIC()).isLessThan(0);

        // o registro existe para quem acompanha de fora, e não mexe na corrente
        mn.startPersistentInwardCurrent(10.0);
        assertThat(mn.tDendritePIC()).isEqualTo(10.0);
        assertThat(mn.gateL()).isEqualTo(antes);
    }

    @Test
    void comCorrenteSuficienteOSomaDisparaERespeitaORefratario() {
        ContinuousMotoneuronMembrane mn = excitavel();
        mn.setInjection(t -> 15.0, null);
        mn.reset();

        double ultimo = Double.NEGATIVE_INFINITY;
        int disparos = 0;
        for (int i = 0; i < 20_000; i++) {
            double t = i * STEP;
            mn.stepDendrite(t);
            if (mn.stepSoma(t)) {
                assertThat(t - ultimo).isGreaterThan(5);
                mn.fireSoma(t);
                ultimo = t;
                disparos++;
            }
        }
        assertThat(disparos).isGreaterThan(3);
    }

    @Test
    void semCorrenteNaoDispara() {
        ContinuousMotoneuronMembrane mn = excitavel();
        mn.reset();
        for (int i = 0; i < 20_000; i++) {
            double t = i * STEP;
            mn.stepDendrite(t);
            assertThat(mn.stepSoma(t)).isFalse();
        }
    }

    @Test
    void oVtrapEContinuoNoLimite() {
        // x/(exp(x/y) − 1) tende a y·(1 − x/2y) quando x → 0
        double y = 4.0;
        double perto = ContinuousMotoneuronMembrane.vtrap(1e-7, y);
        double longe = ContinuousMotoneuronMembrane.vtrap(1e-5, y);
        assertThat(perto).isCloseTo(y, within(1e-5));
        assertThat(perto).isCloseTo(longe, within(1e-4));
        assertThat(ContinuousMotoneuronMembrane.vtrap(0, y)).isCloseTo(y, within(1e-12));
    }

    /** Membrana sem condutâncias ativas: só o que o teste ligar se move. */
    private static ContinuousMotoneuronMembrane passivo(double gammaCa) {
        ContinuousMotoneuronMembrane mn = new ContinuousMotoneuronMembrane(settings(gammaCa));
        mn.setCapacitanceSoma(1.0);
        mn.setGLeakSoma(0.1);
        mn.setCapacitanceDend(1.0);
        mn.setGLeakDend(0.05);
        mn.setGCoupling(0.2);
        mn.setThreshold(1000);
        mn.setThresholdCa(1000);
        return mn;
    }

    /** Membrana com condutâncias na ordem de grandeza de um motoneurônio do ReMoto. */
    private static ContinuousMotoneuronMembrane excitavel() {
        ContinuousMotoneuronMembrane mn = new ContinuousMotoneuronMembrane(settings(0.2));
        mn.setCapacitanceSoma(1.0);
        mn.setGLeakSoma(0.1);
        mn.setCapacitanceDend(1.0);
        mn.setGLeakDend(0.05);
        mn.setGCoupling(0.2);
        mn.setGNaBar(10.0);
        mn.setGNapBar(0.05);
        mn.setGKfBar(5.0);
        mn.setGKsBar(1.0);
        mn.setGCaLBar(0.1);
        mn.setThreshold(30);
        mn.setThresholdCa(1000);
        return mn;
    }
}
