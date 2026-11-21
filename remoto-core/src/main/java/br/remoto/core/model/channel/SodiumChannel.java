package br.remoto.core.model.channel;

/**
 * Sódio: ativação m e inativação h, g = gmax·m³·h. No pulso, m sobe e h cai; fora dele, o
 * contrário.
 */
public final class SodiumChannel extends PulseChannel {

    private final PulseGate m;
    private final PulseGate h;

    /**
     * @param gmax          condutância máxima (µS)
     * @param e             potencial de reversão (mV)
     * @param pulseDuration duração do pulso (ms)
     */
    public SodiumChannel(double gmax, double e, double pulseDuration,
                         double alphaM, double betaM, double alphaH, double betaH) {
        super(gmax, e, pulseDuration);
        this.m = new PulseGate(alphaM, betaM);
        this.h = new PulseGate(alphaH, betaH);
        reset();
    }

    @Override
    double conductance(double t, boolean rising) {
        double valueM;
        double valueH;
        if (rising) {
            valueM = m.rising(t);
            valueH = h.decaying(t);
        } else {
            valueM = m.decaying(t);
            valueH = h.rising(t);
        }
        return gmax * Math.pow(valueM, 3) * valueH;
    }

    @Override
    void changeGates(double t) {
        m.changeState(t);
        h.changeState(t);
    }

    @Override
    void resetGates() {
        m.reset();
        h.reset();
    }
}
