package br.remoto.core.model.synapse;

/**
 * Um contato pré-sináptico dentro de um {@link SynapseGroup}: a condutância máxima dele, o
 * atraso e, para a agregação de Lytton, a fração ligada no fim do último pulso.
 */
public final class Synapse {

    final double gmax;
    final double delay;
    final SynapticDynamics dynamics;

    double ri;
    double t;
    double endOfPulse;
    boolean on;
    double lastSpike;
    double lastGmax;

    Synapse(double gmax, double delay, SynapticDynamics dynamics) {
        this.gmax = gmax;
        this.delay = delay;
        this.dynamics = dynamics;
        reset();
    }

    void reset() {
        ri = 0;
        t = 0;
        // só importa que seja negativo: "nenhum disparo ainda"
        lastSpike = Double.NEGATIVE_INFINITY;
        lastGmax = gmax;
        on = false;
    }

    /** Condutância máxima (µS). */
    public double gmax() {
        return gmax;
    }

    /** Atraso sináptico (ms). */
    public double delay() {
        return delay;
    }
}
