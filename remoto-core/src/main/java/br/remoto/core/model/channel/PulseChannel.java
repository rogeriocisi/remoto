package br.remoto.core.model.channel;

/**
 * Condutância iônica no modelo de pulsos (Destexhe, 1997), como no ReMoto original: cada disparo
 * do neurônio abre um pulso de duração fixa, e as comportas seguem soluções fechadas dentro e fora
 * dele, em vez das equações de Hodgkin-Huxley integradas a cada passo.
 *
 * <p>Duas famílias: {@link SodiumChannel}, com ativação e inativação (m³h), e
 * {@link GatedChannel}, com uma comporta elevada a um expoente (potássio rápido n⁴, potássio lento
 * q², cálcio persistente p).
 */
public abstract sealed class PulseChannel permits SodiumChannel, GatedChannel {

    /** Condutância máxima (µS). */
    protected final double gmax;
    /** Potencial de reversão (mV). */
    protected final double e;
    private final double pulseDuration;

    private boolean inPulse;
    private double t0;
    private double value;

    protected PulseChannel(double gmax, double e, double pulseDuration) {
        this.gmax = gmax;
        this.e = e;
        this.pulseDuration = pulseDuration;
    }

    /** Corrente (nA) com o potencial de membrana {@code v} (mV) no instante {@code t} (ms). */
    public final double current(double t, double v) {
        return value(t) * (e - v);
    }

    /** Condutância (µS) no instante {@code t}; o pulso termina sozinho depois da duração. */
    public final double value(double t) {
        boolean rising = false;
        if (inPulse) {
            if (t - t0 > pulseDuration) {
                changeState(t);
            } else {
                rising = true;
            }
        }
        double computed = conductance(t, rising);
        if (Double.isNaN(computed)) {
            // O original substituía pelo último valor calculado e seguia. Isso faz o único ponto
            // do modelo que detecta divergência numérica ser também o que a esconde: a condutância
            // congela e a simulação termina com um número que parece bom. Uma condutância NaN só
            // aparece com parâmetro impossível (beta <= 0, ou gmax 0 com a comporta divergindo),
            // e nesse caso o resultado não vale nada — melhor parar e dizer onde.
            throw new IllegalStateException("condutância NaN em " + getClass().getSimpleName()
                    + " no instante " + t + " ms (gmax=" + gmax + ", e=" + e
                    + ", duração do pulso=" + pulseDuration + " ms): confira os parâmetros do canal");
        }
        value = computed;
        return value;
    }

    /** Um disparo: começa o pulso. */
    public final void start(double t) {
        changeState(t);
    }

    /** A última condutância calculada (µS). */
    public final double lastValue() {
        return value;
    }

    public final void reset() {
        value = 0.0;
        inPulse = false;
        resetGates();
    }

    private void changeState(double t) {
        inPulse = !inPulse;
        t0 = t;
        changeGates(t);
    }

    /** gmax vezes o produto das comportas, com elas subindo ({@code rising}) ou decaindo. */
    abstract double conductance(double t, boolean rising);

    abstract void changeGates(double t);

    abstract void resetGates();
}
