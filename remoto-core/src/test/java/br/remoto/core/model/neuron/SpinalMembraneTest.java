package br.remoto.core.model.neuron;

import br.remoto.core.model.channel.GatedChannel;
import br.remoto.core.model.channel.SodiumChannel;
import br.remoto.core.model.synapse.SynapseGroup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** A membrana dos neurônios espinhais, sem rede em volta: repouso, carga por corrente e disparo. */
class SpinalMembraneTest {

    private static final double STEP = 0.05;

    private static final MembraneSettings SETTINGS = new MembraneSettings() {
        public double step() { return STEP; }
        public double stepByTwo() { return STEP / 2; }
        public double stepBySix() { return STEP / 6; }
        public double gammaCa() { return 0; }
        public double motoneuronSomaRefractoryPeriod() { return 5; }
    };

    @Test
    void semEntradaOMotoneuronioFicaEmRepouso() {
        MotoneuronMembrane mn = motoneuron(null);
        for (int i = 0; i < 2000; i++) {
            double t = i * STEP;
            mn.stepDendrite(t);
            assertThat(mn.stepSoma(t)).isFalse();
        }
        assertThat(mn.vs()).isZero();
        assertThat(mn.vd()).isZero();
    }

    @Test
    void abaixoDoLimiarOSomaCarregaAteOEquilibrio() {
        // corrente de 1 nA no soma: no equilíbrio, I = gLeakSoma·Vs + gCoupling·(Vs − Vd) e
        // gCoupling·(Vs − Vd) = gLeakDend·Vd
        MotoneuronMembrane mn = motoneuron(t -> 1.0);
        mn.setThreshold(1000);
        for (int i = 0; i < 40_000; i++) {
            double t = i * STEP;
            mn.stepDendrite(t);
            mn.stepSoma(t);
        }
        double gs = 0.1;
        double gd = 0.05;
        double gc = 0.2;
        double vd = gc / (gc + gd);
        double vs = 1.0 / (gs + gc - gc * vd);
        assertThat(mn.vs()).isCloseTo(vs, within(1e-6));
        assertThat(mn.vd()).isCloseTo(vd * vs, within(1e-6));
    }

    @Test
    void acimaDoLimiarDisparaERespeitaORefratario() {
        MotoneuronMembrane mn = motoneuron(t -> 20.0);
        mn.setThreshold(10);
        double lastSpike = Double.NEGATIVE_INFINITY;
        int spikes = 0;
        for (int i = 0; i < 4000; i++) {
            double t = i * STEP;
            mn.stepDendrite(t);
            if (mn.stepSoma(t)) {
                assertThat(t - lastSpike).isGreaterThan(5);
                mn.fireSoma(t);
                lastSpike = t;
                spikes++;
            }
        }
        assertThat(spikes).isGreaterThan(3);
    }

    @Test
    void interneuronioUsaORefratarioDoProprioTipo() {
        InterneuronMembrane in = new InterneuronMembrane(SETTINGS);
        in.setSomaRefractoryPeriod(15);
        configure(in);
        in.setInjection(t -> 20.0);
        in.setThreshold(10);
        in.reset();
        double lastSpike = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 4000; i++) {
            double t = i * STEP;
            if (in.stepSoma(t)) {
                assertThat(t - lastSpike).isGreaterThan(15);
                in.fireSoma(t);
                lastSpike = t;
            }
        }
        assertThat(lastSpike).isPositive();
    }

    private static MotoneuronMembrane motoneuron(java.util.function.DoubleUnaryOperator somaInjection) {
        MotoneuronMembrane mn = new MotoneuronMembrane(SETTINGS);
        configure(mn);
        mn.setCapacitanceDend(1.0);
        mn.setGLeakDend(0.05);
        mn.setGCoupling(0.2);
        mn.setThresholdCa(1000);
        mn.setCa(GatedChannel.calcium(0.1, 140, 1, 1, 1));
        mn.setInjection(somaInjection, null);
        mn.reset();
        return mn;
    }

    private static void configure(SpinalMembrane m) {
        m.setCapacitanceSoma(1.0);
        m.setGLeakSoma(0.1);
        m.setNa(new SodiumChannel(10, 120, 0.6, 22, 13, 0.5, 4));
        m.setKf(GatedChannel.fastPotassium(5, -10, 0.6, 1.5, 0.1));
        m.setKs(GatedChannel.slowPotassium(1, -10, 0.6, 1.5, 0.025));
        SynapseGroup none = new SynapseGroup(70, 1, 1, 1.1, 0.19, false);
        m.setDendExcit(none);
        m.setDendInhib(none);
        m.setSomaExcit(none);
        m.setSomaInhib(none);
    }
}
