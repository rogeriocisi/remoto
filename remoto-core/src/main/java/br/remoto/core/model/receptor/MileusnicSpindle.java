package br.remoto.core.model.receptor;

import br.remoto.core.model.random.Rng;

import java.util.Random;

/**
 * Fuso muscular de Mileusnic et al. (2006): três fibras intrafusais (bag1, bag2 e chain) movidas
 * pelo comprimento do fascículo e pelos disparos fusimotores dinâmico e estático. A aferência
 * primária (Ia) combina as três com oclusão parcial: a maior das parcelas bag1 e bag2 + chain,
 * mais {@value #S} vezes a menor; a secundária (II) soma bag2 e chain.
 *
 * <p>Fiel ao ReMoto original: a cada cálculo da Ia, o fuso cria um gerador derivado de
 * {@link Rng} para um distúrbio de desvio-padrão zero (o sorteio muda a sequência de
 * aleatoriedade do resto da simulação).
 */
public final class MileusnicSpindle {

    /** Peso da parcela menor na oclusão parcial da Ia. */
    static final double S = 0.156;

    private final Bag1Fiber bag1;
    private final Bag2Fiber bag2;
    private final ChainFiber chain;

    private double iaFiringRate;
    private double iiFiringRate;

    /**
     * @param step passo da simulação (ms)
     * @param gammaDynamic disparo fusimotor dinâmico (Hz)
     * @param gammaStatic disparo fusimotor estático (Hz)
     */
    public MileusnicSpindle(String cdMuscle, double step, double gammaDynamic, double gammaStatic,
            double primaryBag1Gain, double primaryBag2AndChainGain, double secondaryBag2AndChainGain) {
        bag1 = new Bag1Fiber(cdMuscle, step, gammaDynamic, gammaStatic, primaryBag1Gain);
        bag2 = new Bag2Fiber(cdMuscle, step, gammaDynamic, gammaStatic, primaryBag2AndChainGain,
                secondaryBag2AndChainGain);
        chain = new ChainFiber(cdMuscle, step, gammaDynamic, gammaStatic);
    }

    /**
     * Avança o fuso um passo.
     *
     * @param t instante (ms)
     * @param fascicleLength comprimento normalizado do fascículo
     * @param velocity velocidade de estiramento (por ms)
     * @param acceleration aceleração de estiramento (por ms²)
     */
    public void step(double t, double fascicleLength, double velocity, double acceleration) {
        bag1.setLengthNorm(fascicleLength);
        bag2.setLengthNorm(fascicleLength);
        chain.setLengthNorm(fascicleLength);

        bag1.setStretchVelocity(velocity * 1000);
        bag2.setStretchVelocity(velocity * 1000);
        chain.setStretchVelocity(velocity * 1000);

        bag1.setStretchAcceleration(acceleration * 1000000);
        bag2.setStretchAcceleration(acceleration * 1000000);
        chain.setStretchAcceleration(acceleration * 1000000);

        double ts = t / 1000;

        bag1.calculateFusimotorActivation(ts);
        bag2.calculateFusimotorActivation(ts);
        chain.calculateFusimotorActivation(ts);

        bag1.calculateFiberTension(ts);
        bag2.calculateFiberTension(ts);
        chain.calculateFiberTension(ts);

        iaFiringRate = calculateIa(ts);
        iiFiringRate = calculateII(ts);
    }

    private double calculateIa(double t) {
        double primaryBag1 = bag1.calculatePrimaryAfferentActivity(t);
        double primaryBag2 = bag2.calculatePrimaryAfferentActivity(t);
        double primaryChain = chain.calculatePrimaryAfferentActivity(t);

        // distúrbio de frequência para testar ressonância estocástica, desligado (desvio 0)
        Random fRandom = Rng.newRandom();
        double stdDev = 0.0;
        double disturbanceFrequency = stdDev * fRandom.nextGaussian();
        if (disturbanceFrequency < 0) {
            disturbanceFrequency = 0;
        }

        double smaller;
        double larger;

        if (primaryBag1 >= (primaryBag2 + primaryChain)) {
            larger = primaryBag1;
            smaller = (primaryBag2 + primaryChain);
        } else {
            larger = (primaryBag2 + primaryChain);
            smaller = primaryBag1;
        }

        return S * smaller + larger + disturbanceFrequency;
    }

    private double calculateII(double t) {
        double secondaryBag2 = bag2.calculateSecondaryAfferentActivity(t);
        double secondaryChain = chain.calculateSecondaryAfferentActivity(t);

        return secondaryBag2 + secondaryChain;
    }

    /** Taxa da aferência primária (Hz) no último passo. */
    public double iaFiringRate() {
        return iaFiringRate;
    }

    /** Taxa da aferência secundária (Hz) no último passo. */
    public double iiFiringRate() {
        return iiFiringRate;
    }

    /** Ativação fusimotora das fibras bag1, bag2 e chain. */
    public double[] fusimotorActivations() {
        return new double[] {bag1.fusimotorActivation(), bag2.fusimotorActivation(), chain.fusimotorActivation()};
    }

    /** Tensão das fibras bag1, bag2 e chain. */
    public double[] fiberTensions() {
        return new double[] {bag1.intrafusalFiberTension(), bag2.intrafusalFiberTension(), chain.intrafusalFiberTension()};
    }
}
