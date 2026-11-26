package br.remoto.core.model.neuron;

import br.remoto.core.model.channel.PulseChannel;
import br.remoto.core.model.synapse.SynapseGroup;

import java.util.function.DoubleUnaryOperator;

/**
 * A membrana de um neurônio espinhal: potenciais, canais de pulso, sinapses, fuga e o
 * Runge-Kutta de 4ª ordem, como no ReMoto original. A identidade do neurônio, os trens de disparo
 * e o axônio ficam com quem a usa.
 *
 * <p>Os potenciais são medidos a partir do repouso (mV) e saturam entre {@link #P0} e {@link #ESE}.
 *
 * <p>Esta é a formulação de canais de pulso do ReMoto original. O que a simulação pede de
 * qualquer membrana está em {@link Membrane}; os canais {@link #na}, {@link #kf} e {@link #ks}
 * são daqui, e quem precisa deles trata esta classe pelo nome.
 */
public abstract sealed class SpinalMembrane implements Membrane
        permits MotoneuronMembrane, InterneuronMembrane {

    /** Potencial de repouso, referência das fugas (mV). */
    static final double E0 = 0.0;
    /** Limite inferior do potencial (mV). */
    static final double P0 = -16.0;
    /** Limite superior do potencial (mV). */
    static final double ESE = 120.0;
    /** "Nenhum disparo ainda": o mesmo -T_MAX do original. */
    static final double NEVER = -1_000_000;

    protected final MembraneSettings settings;

    protected double vs;
    protected double capacitanceSoma;
    protected double gLeakSoma;
    protected double threshold;
    protected double tSomaSpike;

    protected PulseChannel na;
    protected PulseChannel kf;
    protected PulseChannel ks;

    protected SynapseGroup dendExcit;
    protected SynapseGroup dendInhib;
    protected SynapseGroup somaExcit;
    protected SynapseGroup somaInhib;

    protected SpinalMembrane(MembraneSettings settings) {
        this.settings = settings;
    }

    /** Volta ao repouso: potencial em 0, nenhum disparo, canais fechados. */
    public void reset() {
        tSomaSpike = NEVER;
        vs = 0;
        if (kf != null) {
            kf.reset();
        }
        if (ks != null) {
            ks.reset();
        }
        if (na != null) {
            na.reset();
        }
    }

    /** Um disparo do soma no instante {@code t}: abre os canais de sódio e potássio. */
    public void fireSoma(double t) {
        tSomaSpike = t;
        na.start(t);
        kf.start(t);
        ks.start(t);
    }

    static double saturate(double v) {
        if (v < P0) {
            return P0;
        } else if (v > ESE) {
            return ESE;
        } else {
            return v;
        }
    }

    /** Corrente injetada no instante {@code t}, ou 0 sem injeção. */
    static double injected(DoubleUnaryOperator injection, double t) {
        return injection == null ? 0 : injection.applyAsDouble(t);
    }

    public double vs() {
        return vs;
    }

    public void setVs(double vs) {
        this.vs = vs;
    }

    public double capacitanceSoma() {
        return capacitanceSoma;
    }

    public void setCapacitanceSoma(double c) {
        this.capacitanceSoma = c;
    }

    public double gLeakSoma() {
        return gLeakSoma;
    }

    public void setGLeakSoma(double g) {
        this.gLeakSoma = g;
    }

    public double threshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public double tSomaSpike() {
        return tSomaSpike;
    }

    public void setTSomaSpike(double t) {
        this.tSomaSpike = t;
    }

    public PulseChannel na() {
        return na;
    }

    public void setNa(PulseChannel na) {
        this.na = na;
    }

    public PulseChannel kf() {
        return kf;
    }

    public void setKf(PulseChannel kf) {
        this.kf = kf;
    }

    public PulseChannel ks() {
        return ks;
    }

    public void setKs(PulseChannel ks) {
        this.ks = ks;
    }

    public SynapseGroup dendExcit() {
        return dendExcit;
    }

    public void setDendExcit(SynapseGroup g) {
        this.dendExcit = g;
    }

    public SynapseGroup dendInhib() {
        return dendInhib;
    }

    public void setDendInhib(SynapseGroup g) {
        this.dendInhib = g;
    }

    public SynapseGroup somaExcit() {
        return somaExcit;
    }

    public void setSomaExcit(SynapseGroup g) {
        this.somaExcit = g;
    }

    public SynapseGroup somaInhib() {
        return somaInhib;
    }

    public void setSomaInhib(SynapseGroup g) {
        this.somaInhib = g;
    }
}
