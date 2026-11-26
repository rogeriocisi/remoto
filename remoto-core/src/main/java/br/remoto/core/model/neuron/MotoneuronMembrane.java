package br.remoto.core.model.neuron;

import br.remoto.core.model.channel.PulseChannel;

import java.util.function.DoubleUnaryOperator;

/**
 * Motoneurônio de dois compartimentos (Cisi e Kohn, 2008): o soma, com os canais de pulso de sódio
 * e potássio e as sinapses somáticas, e o dendrito, com a corrente persistente de cálcio (PIC) e
 * as sinapses dendríticas, ligados por uma condutância de acoplamento.
 *
 * <p>A cada passo o dendrito é integrado primeiro, com o potencial do soma do passo anterior, e
 * depois o soma, com o dendrito já atualizado: dois Runge-Kutta de 4ª ordem em sequência, como no
 * original.
 */
public final class MotoneuronMembrane extends SpinalMembrane implements MotoneuronMembraneModel {

    /** Intervalo mínimo entre dois inícios da PIC (ms). */
    private static final double PIC_INTERVAL = 0.05;

    private double vd;
    private double capacitanceDend;
    private double gLeakDend;
    private double gCoupling;
    private double thresholdCa;
    private double tDendritePIC;
    private PulseChannel ca;
    private DoubleUnaryOperator somaInjection;
    private DoubleUnaryOperator dendriteInjection;

    public MotoneuronMembrane(MembraneSettings settings) {
        super(settings);
    }

    @Override
    public void reset() {
        super.reset();
        tDendritePIC = NEVER;
        vd = 0;
        if (ca != null) {
            ca.reset();
        }
    }

    /** Integra o dendrito por um passo, a partir de {@code t}. */
    public void stepDendrite(double t) {
        double k1 = dVdDt(t, saturate(vd));
        double k2 = dVdDt(t + settings.stepByTwo(), saturate(vd + settings.stepByTwo() * k1));
        double k3 = dVdDt(t + settings.stepByTwo(), saturate(vd + settings.stepByTwo() * k2));
        double k4 = dVdDt(t + settings.step(), saturate(vd + settings.step() * k3));
        vd = saturate(vd + settings.stepBySix() * (k1 + 2 * k2 + 2 * k3 + k4));
    }

    /**
     * Integra o soma por um passo, a partir de {@code t}.
     *
     * @return se o soma cruzou o limiar fora do período refratário; quem chama registra o disparo
     *         e chama {@link #fireSoma(double)}
     */
    public boolean stepSoma(double t) {
        double k1 = dVsDt(t, saturate(vs));
        double k2 = dVsDt(t + settings.stepByTwo(), saturate(vs + settings.stepByTwo() * k1));
        double k3 = dVsDt(t + settings.stepByTwo(), saturate(vs + settings.stepByTwo() * k2));
        double k4 = dVsDt(t + settings.step(), saturate(vs + settings.step() * k3));
        vs = saturate(vs + settings.stepBySix() * (k1 + 2 * k2 + 2 * k3 + k4));

        return vs > threshold && t - tSomaSpike > settings.motoneuronSomaRefractoryPeriod();
    }

    /** Depois do soma: com γCa > 0, o soma acima do limiar do cálcio inicia a PIC no dendrito. */
    public void checkPersistentInwardCurrent(double t) {
        if (settings.gammaCa() > 0) {
            if (vs > thresholdCa && t - tDendritePIC > PIC_INTERVAL) {
                startPersistentInwardCurrent(t);
            }
        }
    }

    public void startPersistentInwardCurrent(double t) {
        tDendritePIC = t;
        ca.start(t);
    }

    private double dVdDt(double t, double v) {
        double iIonic = 0;
        double iSynaptic = 0;

        if (settings.gammaCa() > 0) {
            iIonic += ca.current(t, v) * settings.gammaCa();
        }

        iSynaptic += dendExcit.current(t, v);
        iSynaptic += dendInhib.current(t, v);

        double iSoma = gCoupling * (vs - v);
        double iLeak = gLeakDend * (E0 - v);
        double iInjected = injected(dendriteInjection, t);

        // correntes em nA, capacitância em nF
        return (iIonic + iSynaptic + iSoma + iLeak + iInjected) / capacitanceDend;
    }

    private double dVsDt(double t, double v) {
        double iIonic = 0;
        double iSynaptic = 0;

        iIonic += na.current(t, v);
        iIonic += kf.current(t, v);
        iIonic += ks.current(t, v);

        iSynaptic += somaExcit.current(t, v);
        iSynaptic += somaInhib.current(t, v);

        double iDendrite = gCoupling * (vd - v);
        double iLeak = gLeakSoma * (E0 - v);
        double iInjected = injected(somaInjection, t);

        return (iIonic + iSynaptic + iDendrite + iLeak + iInjected) / capacitanceSoma;
    }

    /** Corrente injetada no soma e no dendrito; null = nenhuma. */
    public void setInjection(DoubleUnaryOperator soma, DoubleUnaryOperator dendrite) {
        this.somaInjection = soma;
        this.dendriteInjection = dendrite;
    }

    public double vd() {
        return vd;
    }

    public void setVd(double vd) {
        this.vd = vd;
    }

    public double capacitanceDend() {
        return capacitanceDend;
    }

    public void setCapacitanceDend(double c) {
        this.capacitanceDend = c;
    }

    public double gLeakDend() {
        return gLeakDend;
    }

    public void setGLeakDend(double g) {
        this.gLeakDend = g;
    }

    public double gCoupling() {
        return gCoupling;
    }

    public void setGCoupling(double g) {
        this.gCoupling = g;
    }

    public double thresholdCa() {
        return thresholdCa;
    }

    public void setThresholdCa(double t) {
        this.thresholdCa = t;
    }

    public double tDendritePIC() {
        return tDendritePIC;
    }

    public void setTDendritePIC(double t) {
        this.tDendritePIC = t;
    }

    /**
     * O último valor do canal de pulso do cálcio (µS). Aqui o ganho γ entra na corrente, não na
     * condutância, então o valor é o do canal, como o original gravava.
     */
    @Override
    public double calciumConductance() {
        return ca == null ? 0 : ca.lastValue();
    }

    public PulseChannel ca() {
        return ca;
    }

    public void setCa(PulseChannel ca) {
        this.ca = ca;
    }
}
