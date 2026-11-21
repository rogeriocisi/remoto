package br.remoto.core.model.tract;

import br.remoto.core.model.random.Draws;
import br.remoto.core.model.random.Rng;
import br.remoto.core.model.signal.Modulation;

/**
 * Disparos de um trato descendente ou de um ruído: um processo de Poisson, um processo gama de
 * ordem inteira (integra a taxa até um limiar sorteado) ou intervalos gaussianos, com a média
 * modulada por um sinal.
 *
 * <p>Fiel ao ReMoto original, inclusive onde ele surpreende:
 * <ul>
 *   <li>o limiar do processo gama é sorteado a cada {@link #reset()}, qualquer que seja a
 *   distribuição, e a integral da taxa não volta a zero no reset;</li>
 *   <li>o reset anota no trem do terminal um disparo inicial (em 0 ou no início do sinal) também
 *   nos processos de Poisson e gama, embora esse disparo não chegue às sinapses;</li>
 *   <li>nos intervalos gaussianos, o trem recebe o próximo disparo já sorteado, e o reset sorteia
 *   o primeiro intervalo duas vezes quando ele passa do início do sinal.</li>
 * </ul>
 */
public final class TractSpikeGenerator {

    /** Valor inicial do próximo disparo (ms), o T_MAX do original. */
    static final double T_MAX = 1000000;

    /** Limite superior da média do intervalo (ms) quando o sinal modula a taxa. */
    private static final double MAX_MEAN = 2000;

    /** Limite inferior da média do intervalo (ms): no máximo 2 kHz. */
    private static final double MIN_MEAN = 0.5;

    /** As distribuições dos intervalos, pelos códigos da configuração. */
    public enum Distribution {
        POISSON("P"),
        GAMMA("Gm"),
        GAUSSIAN("G"),
        /** Código desconhecido: o trato não dispara, mas o reset anota o disparo inicial. */
        OTHER(null);

        private final String code;

        Distribution(String code) {
            this.code = code;
        }

        /** O código desta distribuição na configuração; null para {@link #OTHER}. */
        public String code() {
            return code;
        }

        /** A distribuição de um código da configuração; o código não pode ser nulo. */
        public static Distribution of(String code) {
            if (code == null) {
                throw new NullPointerException("distribuição do trato nula");
            }
            for (Distribution d : values()) {
                if (code.equals(d.code)) {
                    return d;
                }
            }
            return OTHER;
        }
    }

    /** Para onde vão os disparos do trato. */
    public interface Output {

        /** O trato disparou em {@code t}: o disparo segue para as sinapses. */
        void fire(double t);

        /** Anota {@code t} no trem do terminal, que vai para as saídas. */
        void record(double t);

        /** Desfaz a primeira anotação do trem. */
        void unrecordFirst();
    }

    private final Distribution distribution;
    private final double mean;
    private final double std;
    private final Modulation signal;
    private final boolean modulatesRate;
    private final double integrationStep;
    private final Output output;

    private double tTerminalSpike;
    private boolean lastSpike;
    private double thres;
    private double yInt = 0;

    /**
     * @param mean intervalo médio entre disparos (ms); até {@link Modulation#T_TOLERANCE}, o trato
     *     só dispara dentro da janela do sinal
     * @param std desvio-padrão do intervalo gaussiano (ms); no processo gama, a ordem
     * @param signal o sinal que modula a média
     * @param modulatesRate se o sinal soma à taxa (Hz) em vez de ao intervalo (ms)
     * @param step passo de integração (ms)
     */
    public TractSpikeGenerator(Distribution distribution, double mean, double std, Modulation signal,
            boolean modulatesRate, double step, Output output) {
        if (distribution == null || signal == null || output == null) {
            throw new IllegalArgumentException("distribuição, sinal e saída são obrigatórios");
        }
        this.distribution = distribution;
        this.mean = mean;
        this.std = std;
        this.signal = signal;
        this.modulatesRate = modulatesRate;
        this.integrationStep = step;
        this.output = output;
    }

    /** Volta ao início da simulação: sorteia o limiar e o primeiro disparo. */
    public void reset() {
        thres = Draws.gamma(1 / std, std);

        tTerminalSpike = T_MAX;
        lastSpike = false;

        if (mean > Modulation.T_TOLERANCE) {
            // o primeiro disparo depois de t = 0; se passar do início do sinal, sorteia de novo
            // a partir dele
            tTerminalSpike = 0;
            addSpike(0);

            if (tTerminalSpike > signal.tini()) {
                output.unrecordFirst();

                tTerminalSpike = signal.tini();
                addSpike(signal.tini());
            }
        } else {
            tTerminalSpike = signal.tini();
            addSpike(signal.tini());
        }
    }

    /** Avança um passo a partir de {@code t}. */
    public void step(double t) {
        switch (distribution) {
            case POISSON -> poisson(t);
            case GAMMA -> gamma(t);
            case GAUSSIAN -> gaussian(t);
            case OTHER -> {
            }
        }
    }

    private void poisson(double t) {
        double instantMean = meanValue(t);
        double lambda = 1000 / instantMean;

        if (canFire(t)) {
            double probability = lambda * integrationStep / 1000;
            double aux = Rng.random();

            if (aux <= probability) {
                output.fire(t);
                tTerminalSpike = t;
                output.record(tTerminalSpike);
            }
        }
    }

    private void gamma(double t) {
        double instantMean = meanValue(t);
        double lambda = 1000 / instantMean;

        if (canFire(t)) {
            yInt = yInt + (lambda * (integrationStep / 1000));

            if (yInt >= thres) {
                tTerminalSpike = t;
                output.fire(t);
                yInt = 0;
                thres = Draws.gamma(1 / std, std);
                output.record(tTerminalSpike);
            }
        }
    }

    private void gaussian(double t) {
        if (t > tTerminalSpike && !lastSpike) {
            output.fire(t);

            if (canFire(t)) {
                addSpike(t);
            } else {
                lastSpike = true;
            }
        }
    }

    // com média positiva, sempre; sem ela, só dentro da janela aberta do sinal
    private boolean canFire(double t) {
        return mean > Modulation.T_TOLERANCE
                || (t > signal.tini() && t < signal.tfin() + Modulation.T_TOLERANCE);
    }

    // sorteia o próximo intervalo gaussiano e anota o próximo disparo
    private void addSpike(double t) {
        double instantMean = meanValue(t);

        if (distribution == Distribution.GAUSSIAN) {
            tTerminalSpike += Draws.gaussian(instantMean, std);
        }

        if (instantMean < integrationStep && tTerminalSpike > signal.tfin()) {
            lastSpike = true;
            return;
        }

        output.record(tTerminalSpike);
    }

    /** Intervalo médio entre disparos (ms) em {@code time}, com a modulação do sinal. */
    public double meanValue(double time) {
        double ret = mean;

        double modulationValue = signal.value(time);

        if (modulatesRate) {
            if (mean > integrationStep) {
                modulationValue += 1000 / mean; // [Hz]
            }

            if (modulationValue > integrationStep || modulationValue < -integrationStep) {
                ret = 1000 / modulationValue;
                if (ret > MAX_MEAN) {
                    ret = MAX_MEAN;
                }
            } else {
                ret = MAX_MEAN;
            }
        } else {
            ret += modulationValue;
        }

        if (ret < MIN_MEAN) {
            ret = MIN_MEAN;
        }

        return ret;
    }

    /** O próximo disparo (gaussiano) ou o último (Poisson e gama), em ms. */
    public double tTerminalSpike() {
        return tTerminalSpike;
    }

    public Distribution distribution() {
        return distribution;
    }

    public double mean() {
        return mean;
    }

    public double std() {
        return std;
    }

    public Modulation signal() {
        return signal;
    }
}
