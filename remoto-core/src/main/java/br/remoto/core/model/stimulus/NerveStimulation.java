package br.remoto.core.model.stimulus;

import br.remoto.core.model.signal.Modulation;

/**
 * Estimulação elétrica de um nervo: pulsos de {@code pulseWidth} ms ({@link #PULSE_WIDTH} no
 * original, que não deixava mudar) entre {@code tini} e
 * {@code tfin}, com o período modulado por um sinal, e opcionalmente um segundo trem de pulsos
 * (pareado) que começa {@code delayPaired} ms depois. A intensidade de cada passo vai para os
 * axônios dos motoneurônios e das fibras aferentes do nervo.
 *
 * <p>Fiel ao ReMoto original: o sinal soma à frequência (o período é {@code 1000/(freq + sinal)});
 * o trem principal avança um período sempre que o tempo se afasta do início do último pulso por
 * um período ou mais, e o pulso dura enquanto o tempo está a até {@code pulseWidth} desse
 * início, inclusive; o trem pareado é calculado pelos ciclos desde o seu próprio início.
 */
public final class NerveStimulation {

    /** Duração de um pulso (ms), o pulseWidth do original. */
    public static final double PULSE_WIDTH = 1.0;

    /** O T_MAX do original (ms): abaixo de 1000/T_MAX, a frequência é zero. */
    private static final double T_MAX = 1000000;

    private final double tini;
    private final double tfin;
    private final double amp;
    private final double freq;
    private final Modulation signal;

    private final boolean paired;
    private final double delayPaired;
    private final double ampPaired;
    private final double freqPaired;
    private final double pulseWidth;

    private double start;
    private double period;
    private double intensity;

    /**
     * @param freq frequência dos pulsos (Hz); abaixo de 1000/T_MAX vira 0 e acima de 1000/step,
     *     um pulso por passo
     * @param signal o sinal somado à frequência
     * @param freqPaired frequência do trem pareado (Hz); abaixo de 1000/T_MAX, a do trem principal
     * @param step passo de integração (ms)
     */
    public NerveStimulation(double tini, double tfin, double amp, double freq, Modulation signal,
            boolean paired, double delayPaired, double ampPaired, double freqPaired, double step) {
        this(tini, tfin, amp, freq, signal, paired, delayPaired, ampPaired, freqPaired, step, PULSE_WIDTH);
    }

    /**
     * @param pulseWidth duração de cada pulso (ms), maior que zero; o original usava
     *     {@link #PULSE_WIDTH}
     */
    public NerveStimulation(double tini, double tfin, double amp, double freq, Modulation signal,
            boolean paired, double delayPaired, double ampPaired, double freqPaired, double step,
            double pulseWidth) {
        if (signal == null) {
            throw new IllegalArgumentException("o sinal do nervo é obrigatório");
        }
        if (freq < 1000 / T_MAX) {
            freq = 0;
        } else if (freq > 1000 / step) {
            freq = 1000 / step;
        }
        if (freqPaired < 1000 / T_MAX) {
            freqPaired = freq;
        }

        this.tini = tini;
        this.tfin = tfin;
        this.amp = amp;
        this.freq = freq;
        this.signal = signal;
        this.paired = paired;
        this.delayPaired = delayPaired;
        this.ampPaired = ampPaired;
        this.freqPaired = freqPaired;
        if (!(pulseWidth > 0)) {
            throw new IllegalArgumentException("a duração do pulso deve ser maior que zero: " + pulseWidth);
        }
        this.pulseWidth = pulseWidth;
        this.start = tini;
    }

    /** Calcula a intensidade do estímulo em {@code t}. */
    public void step(double t) {
        intensity = 0;

        if (t < tini || t > tfin) {
            return;
        }

        period = 1000.0 / (freq + signal.value(t));

        if (Math.abs(t - start) >= period) {
            start = start + period;
        }

        if (Math.abs(t - start) <= pulseWidth) {
            intensity = amp;
        }

        if (paired) {
            stepPaired(t);
        }
    }

    private void stepPaired(double t) {
        double tIniPaired = tini + delayPaired;

        if (t < tIniPaired || t > tfin) {
            return;
        }

        double periodPaired = 1000.0 / freqPaired;
        int cycle = (int) ((t - tIniPaired) / periodPaired);
        double startPaired = tIniPaired + cycle * periodPaired;
        double finish = startPaired + pulseWidth;

        if (t > startPaired && t < finish + Modulation.T_TOLERANCE) {
            intensity += ampPaired;
        }
    }

    /** Intensidade do estímulo no último passo calculado. */
    public double intensity() {
        return intensity;
    }

    /** Período do trem principal (ms) no último passo calculado dentro de [tini, tfin]. */
    public double period() {
        return period;
    }

    /** Frequência dos pulsos (Hz), já limitada pelo passo. */
    public double freq() {
        return freq;
    }

    /** Duração de cada pulso (ms). */
    public double pulseWidth() {
        return pulseWidth;
    }

    /** Frequência do trem pareado (Hz). */
    public double freqPaired() {
        return freqPaired;
    }
}
