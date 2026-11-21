package br.remoto.core.model.channel;

/**
 * Uma comporta (m, h, n...) no modelo de pulsos: durante o pulso que segue um disparo ela relaxa
 * para 1 com taxa {@code alpha}; fora dele, para 0 com taxa {@code beta}. Cada trecho tem solução
 * fechada a partir do valor e do instante da última troca de estado.
 */
final class PulseGate {

    private final double alpha;
    private final double beta;
    private double value;
    private double v0;
    private double t0;

    PulseGate(double alpha, double beta) {
        this.alpha = alpha;
        this.beta = beta;
        reset();
    }

    /** Volta ao repouso. Como no original, o último valor calculado não é zerado. */
    void reset() {
        v0 = 0;
        t0 = 0;
    }

    /** Fora do pulso: decai para 0. */
    double decaying(double t) {
        value = v0 * Math.exp(-beta * (t - t0));
        return value;
    }

    /** Durante o pulso: sobe para 1. */
    double rising(double t) {
        value = 1 + (v0 - 1) * Math.exp(-alpha * (t - t0));
        return value;
    }

    /** O pulso começa ou termina: o trecho seguinte parte do valor atual. */
    void changeState(double t) {
        t0 = t;
        v0 = value;
    }
}
