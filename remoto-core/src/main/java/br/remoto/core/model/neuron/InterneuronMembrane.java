package br.remoto.core.model.neuron;

import java.util.function.DoubleUnaryOperator;

/**
 * Interneurônio de um compartimento (célula de Renshaw, interneurônios Ia, Ib e do grupo II): soma
 * com os canais de pulso de sódio e potássio e as sinapses, que no original ficam nos grupos
 * "dendríticos" do neurônio, integrado por Runge-Kutta de 4ª ordem.
 */
public final class InterneuronMembrane extends SpinalMembrane {

    private double somaRefractoryPeriod;
    private DoubleUnaryOperator injection;

    public InterneuronMembrane(MembraneSettings settings) {
        super(settings);
    }

    /**
     * Integra o soma por um passo, a partir de {@code t}.
     *
     * @return se o soma cruzou o limiar fora do período refratário
     */
    public boolean stepSoma(double t) {
        double k1 = dVsDt(t, saturate(vs));
        double k2 = dVsDt(t + settings.stepByTwo(), saturate(vs + settings.stepByTwo() * k1));
        double k3 = dVsDt(t + settings.stepByTwo(), saturate(vs + settings.stepByTwo() * k2));
        double k4 = dVsDt(t + settings.step(), saturate(vs + settings.step() * k3));
        vs = saturate(vs + settings.stepBySix() * (k1 + 2 * k2 + 2 * k3 + k4));

        // a comparação é t > último + refratário, e não t − último > refratário como no
        // motoneurônio: em ponto flutuante as duas não são equivalentes, e cada uma fica como era
        return vs > threshold && t > tSomaSpike + somaRefractoryPeriod;
    }

    private double dVsDt(double t, double v) {
        double iIonic = 0;
        double iSynaptic = 0;
        double iLeak = gLeakSoma * (E0 - v);

        iIonic += na.current(t, v);
        iIonic += kf.current(t, v);
        iIonic += ks.current(t, v);

        iSynaptic += dendExcit.current(t, v);
        iSynaptic += dendInhib.current(t, v);

        double iInjected = injected(injection, t);

        // a ordem da soma é a do original, diferente da do motoneurônio
        return (iIonic + iLeak + iSynaptic + iInjected) / capacitanceSoma;
    }

    /** Corrente injetada; null = nenhuma. */
    public void setInjection(DoubleUnaryOperator injection) {
        this.injection = injection;
    }

    /** Período refratário do soma deste tipo de interneurônio (ms). */
    public double somaRefractoryPeriod() {
        return somaRefractoryPeriod;
    }

    public void setSomaRefractoryPeriod(double ms) {
        this.somaRefractoryPeriod = ms;
    }
}
