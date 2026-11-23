package br.remoto.core.model.neuron;

import br.remoto.core.model.random.Draws;

import java.util.Arrays;
import java.util.function.DoubleSupplier;

/**
 * Fibra aferente (Ia e II do fuso, Ib do órgão tendinoso de Golgi): o axônio dispara quando o
 * estímulo do nervo passa do limiar ou quando a taxa do receptor, acima do limiar de recrutamento,
 * integrada num processo gama de ordem inteira, atinge o limiar sorteado (algoritmo de Gabbiani e
 * Cox, 2010). Cada disparo chega à medula depois da latência de condução.
 *
 * <p>Fiel ao ReMoto original:
 * <ul>
 *   <li>cada reset sorteia os limiares dos três tipos (ordens 25, 30 e 400), embora a fibra use só
 *   o do seu; a integral da taxa não volta a zero no reset;</li>
 *   <li>a taxa recebe a cada passo um ruído gaussiano de média 5 Hz e desvio 0,5 Hz;</li>
 *   <li>o disparo pelo receptor é anotado no axônio com a latência até a placa motora somada ao
 *   instante atual;</li>
 *   <li>depois do primeiro disparo, a fibra II sorteia o limiar com a ordem da Ia (25), não com a
 *   sua (400).</li>
 * </ul>
 *
 * <p>Com uma ordem escolhida (como no ReMoto em Fortran, por tipo e músculo), a fibra usa essa
 * ordem no seu limiar, no reset e depois de cada disparo; os outros dois sorteios do reset
 * continuam os do original. Ordem maior deixa o disparo mais regular: o coeficiente de variação
 * dos intervalos, com a taxa constante, é {@code 1/sqrt(ordem)}.
 */
public final class AfferentFiber {

    /** O T_MAX do original (ms). */
    private static final double T_MAX = 1000000;

    private static final double GAMMA_ORDER_IA = 25;
    private static final double GAMMA_ORDER_IB = 30;
    private static final double GAMMA_ORDER_II = 400;

    /** Taxa mínima (Hz) somada, com ruído, à taxa do receptor. */
    private static final double MINIMAL_FIRING = 5;

    /** Os tipos de fibra, pelos códigos da configuração. */
    public enum Kind {
        IA("Ia"),
        IB("Ib"),
        II("II"),
        /** Tipo sem receptor: só o nervo a faz disparar. */
        OTHER(null);

        private final String code;

        Kind(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        /** O tipo de um código da configuração; um código desconhecido não tem receptor. */
        public static Kind of(String code) {
            for (Kind kind : values()) {
                if (kind.code != null && kind.code.equals(code)) {
                    return kind;
                }
            }
            return OTHER;
        }
    }

    /** Para onde vão os disparos que chegam à medula. */
    public interface Output {

        /** Um disparo chegou à medula em {@code t}: segue para as sinapses e para as saídas. */
        void deliver(double t);
    }

    private final Kind kind;
    private final AfferentSettings settings;
    private final Output output;
    /** A ordem escolhida para esta fibra; 0 = as do original. */
    private final int gammaOrder;

    private DoubleSupplier firingRate;
    private double axonThreshold;
    private double recruitmentThreshold;
    private double latencyStimulusSpinal;
    private double latencyStimulusEndPlate;

    private double tAxonSpike;
    private double nextSpike;
    private int indexSpike;
    private double[] axonSpikes = new double[16];
    private int axonSpikeCount;

    private double thres;
    private double yInt = 0;

    public AfferentFiber(Kind kind, AfferentSettings settings, Output output) {
        this(kind, settings, output, 0);
    }

    /**
     * @param gammaOrder a ordem do processo gama do disparo pelo receptor, inteira; 0 para as do
     *     original
     */
    public AfferentFiber(Kind kind, AfferentSettings settings, Output output, int gammaOrder) {
        if (kind == null || settings == null || output == null) {
            throw new IllegalArgumentException("tipo, parâmetros e saída são obrigatórios");
        }
        if (gammaOrder < 0) {
            throw new IllegalArgumentException("a ordem gama deve ser 0 (a do original) ou positiva: " + gammaOrder);
        }
        this.kind = kind;
        this.settings = settings;
        this.output = output;
        this.gammaOrder = gammaOrder;
    }

    /** Volta ao início da simulação: sorteia os limiares e esvazia o axônio. */
    public void reset() {
        double thresIa = Draws.gamma(1 / order(Kind.IA, GAMMA_ORDER_IA), order(Kind.IA, GAMMA_ORDER_IA));
        double thresIb = Draws.gamma(1 / order(Kind.IB, GAMMA_ORDER_IB), order(Kind.IB, GAMMA_ORDER_IB));
        double thresII = Draws.gamma(1 / order(Kind.II, GAMMA_ORDER_II), order(Kind.II, GAMMA_ORDER_II));

        thres = switch (kind) {
            case IA -> thresIa;
            case IB -> thresIb;
            case II -> thresII;
            case OTHER -> 0;
        };

        axonSpikeCount = 0;

        tAxonSpike = -T_MAX;

        nextSpike = -T_MAX;
        indexSpike = 0;
    }

    /** Os disparos no ponto de estímulo do axônio (ms), na ordem em que ocorreram. */
    public double[] axonSpikeTimes() {
        return Arrays.copyOf(axonSpikes, axonSpikeCount);
    }

    /** Reserva espaço para {@code size} disparos no axônio. */
    public void ensureCapacity(int size) {
        if (axonSpikes.length < size) {
            axonSpikes = Arrays.copyOf(axonSpikes, size);
        }
    }

    /** Entrega à medula o próximo disparo do axônio, se a condução dele terminou até {@code t}. */
    public void deliver(double t) {
        if (indexSpike < axonSpikeCount) {
            nextSpike = axonSpikes[indexSpike] + latencyStimulusSpinal;

            if (t > nextSpike) {
                output.deliver(t);
                indexSpike++;
            }
        }
    }

    /**
     * Gera os disparos do axônio em {@code t}: pelo estímulo do nervo e, fora do refratário, pela
     * taxa do receptor.
     *
     * @param stimulus intensidade do estímulo elétrico do nervo neste passo
     */
    public void generate(double t, double stimulus) {
        double refractory = settings.afferentAxonRefractoryPeriod();

        if (stimulus > axonThreshold && t - tAxonSpike > refractory) {
            tAxonSpike = t;
            addAxonSpike(t);
        }

        if (kind != Kind.OTHER && t - tAxonSpike > settings.afferentAxonRefractoryPeriod()) {
            double rate = firingRate.getAsDouble();

            if (rate > recruitmentThreshold) {
                double lambda = (rate - recruitmentThreshold) + Draws.gaussian(MINIMAL_FIRING, 0.1 * MINIMAL_FIRING);

                yInt = yInt + (lambda * (settings.step() / 1000));

                if (yInt >= thres) {
                    tAxonSpike = t + latencyStimulusEndPlate;
                    yInt = 0;
                    if (gammaOrder > 0) {
                        thres = Draws.gamma(1.0 / gammaOrder, gammaOrder);
                    } else {
                        thres = switch (kind) {
                            case IA, II -> Draws.gamma(1 / GAMMA_ORDER_IA, GAMMA_ORDER_IA);
                            default -> Draws.gamma(1 / GAMMA_ORDER_IB, GAMMA_ORDER_IB);
                        };
                    }
                    addAxonSpike(tAxonSpike);
                }
            }
        }
    }

    /** A ordem do sorteio de {@code type} no reset: a escolhida, se for o tipo desta fibra. */
    private double order(Kind type, double original) {
        return gammaOrder > 0 && type == kind ? gammaOrder : original;
    }

    /** A ordem escolhida para esta fibra; 0 = as do original. */
    public int gammaOrder() {
        return gammaOrder;
    }

    private void addAxonSpike(double t) {
        if (axonSpikeCount == axonSpikes.length) {
            axonSpikes = Arrays.copyOf(axonSpikes, axonSpikeCount * 2);
        }
        axonSpikes[axonSpikeCount++] = t;
    }

    /**
     * Latências de condução, arredondadas para o passo: do ponto de estímulo à medula e à placa
     * motora.
     *
     * @param axonVelocity velocidade de condução (m/s); abaixo de 0,01, 0,01
     * @param stimulusSpinalDistance distância do estímulo à entrada na medula (m)
     * @param stimulusEndPlateDistance distância do estímulo à placa motora (m)
     */
    public void setLatency(double axonVelocity, double stimulusSpinalDistance, double stimulusEndPlateDistance) {
        if (axonVelocity < 0.01) {
            axonVelocity = 0.01;
        }

        latencyStimulusSpinal = (stimulusSpinalDistance / axonVelocity) * 1000.0;
        latencyStimulusEndPlate = (stimulusEndPlateDistance / axonVelocity) * 1000.0;

        latencyStimulusSpinal = Math.round(latencyStimulusSpinal / settings.step()) * settings.step();
        latencyStimulusEndPlate = Math.round(latencyStimulusEndPlate / settings.step()) * settings.step();
    }

    /** A taxa de disparo (Hz) do receptor desta fibra; null = sem receptor ligado. */
    public void setFiringRate(DoubleSupplier firingRate) {
        this.firingRate = firingRate;
    }

    public void setAxonThreshold(double axonThreshold) {
        this.axonThreshold = axonThreshold;
    }

    public double recruitmentThreshold() {
        return recruitmentThreshold;
    }

    public void setRecruitmentThreshold(double recruitmentThreshold) {
        this.recruitmentThreshold = recruitmentThreshold;
    }

    public double latencyStimulusSpinal() {
        return latencyStimulusSpinal;
    }

    public double latencyStimulusEndPlate() {
        return latencyStimulusEndPlate;
    }

    public Kind kind() {
        return kind;
    }

    /** Disparos anotados no axônio desde o último reset (ms). */
    public double[] axonSpikes() {
        return Arrays.copyOf(axonSpikes, axonSpikeCount);
    }
}
