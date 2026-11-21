package br.remoto.core.model.channel;

/**
 * Uma comporta elevada a um expoente, g = gmax·xᵏ, subindo durante o pulso e decaindo fora dele.
 * Cobre o potássio rápido (n⁴), o potássio lento (q²) e o cálcio persistente (p).
 */
public final class GatedChannel extends PulseChannel {

    private final PulseGate gate;
    private final int exponent;

    private GatedChannel(double gmax, double e, double pulseDuration, double alpha, double beta, int exponent) {
        super(gmax, e, pulseDuration);
        this.gate = new PulseGate(alpha, beta);
        this.exponent = exponent;
        reset();
    }

    /** Potássio rápido, retificador retardado: g = gmax·n⁴. */
    public static GatedChannel fastPotassium(double gmax, double e, double pulseDuration, double alpha, double beta) {
        return new GatedChannel(gmax, e, pulseDuration, alpha, beta, 4);
    }

    /** Potássio lento, da pós-hiperpolarização: g = gmax·q². */
    public static GatedChannel slowPotassium(double gmax, double e, double pulseDuration, double alpha, double beta) {
        return new GatedChannel(gmax, e, pulseDuration, alpha, beta, 2);
    }

    /** Cálcio persistente do dendrito (PIC): g = gmax·p. */
    public static GatedChannel calcium(double gmax, double e, double pulseDuration, double alpha, double beta) {
        return new GatedChannel(gmax, e, pulseDuration, alpha, beta, 1);
    }

    @Override
    double conductance(double t, boolean rising) {
        double x = rising ? gate.rising(t) : gate.decaying(t);
        // expoentes constantes, como no original, para a JIT especializar o Math.pow
        return switch (exponent) {
            case 4 -> gmax * Math.pow(x, 4);
            case 2 -> gmax * Math.pow(x, 2);
            default -> gmax * x;
        };
    }

    @Override
    void changeGates(double t) {
        gate.changeState(t);
    }

    @Override
    void resetGates() {
        gate.reset();
    }
}
