package br.remoto.core.model.muscle;

import br.remoto.core.model.random.Rng;

import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * Músculo do tipo Hill: elemento contrátil com relações força-comprimento e força-velocidade
 * separadas para fibras lentas (S) e rápidas (F), elemento elástico paralelo e viscosidade, em
 * série com o tendão e com ângulo de penação; a ativação de cada tipo vem dos abalos das unidades
 * motoras, normalizada pela força máxima.
 *
 * <p>Fiel ao ReMoto original:
 * <ul>
 *   <li>a dinâmica calcula os quatro estágios do Runge-Kutta, mas avança por Euler (só k1); como
 *   cada estágio grava força e aceleração, ficam as do 4º estágio;</li>
 *   <li>a cada passo cria um gerador derivado de {@link Rng} que não usa (o sorteio muda a
 *   sequência de aleatoriedade do resto da simulação);</li>
 *   <li>o comprimento inicial é fixo por músculo (tornozelo a 3 graus).</li>
 * </ul>
 */
public final class HillMuscle {

    static final double EPS0_M = 0.50;

    static final double MAXIMUM_VELOCITY_S = -7.88;
    static final double MAXIMUM_VELOCITY_F = -9.15;

    static final double AV0_S = -4.7;
    static final double AV1_S = 8.41;
    static final double AV2_S = -5.34;
    static final double BV_S = 0.35;
    static final double AV0_F = -1.53;
    static final double AV1_F = 0;
    static final double AV2_F = 0;
    static final double BV_F = 0.69;
    static final double CV0_S = 5.88;
    static final double CV1_S = 0;
    static final double CV0_F = -5.70;
    static final double CV1_F = 9.18;

    static final double B_S = 2.3;
    static final double W_S = 1.12;
    static final double P_S = 1.62;

    static final double B_F = 1.55;
    static final double W_F = 0.75;
    static final double P_F = 2.12;

    /** Parâmetros de HILL_TYPE_MUSCULOTENDON_PARAMETERS de um músculo. */
    public record Parameters(double optimalLength, double pennationAngle, double slackTendonLength,
            double maximumMuscleForce, double muscleMass, double viscosityCoeficient,
            double elasticCoeficientOfParallelElement) {
    }

    private final String cdMuscle;
    private final MotorUnit[] units;
    private final MotorUnit[] picked;
    private final AlphaTwitch[] activationFunction;
    private final double twitchStep;
    private final DoubleSupplier tendonForce;

    private final double step;
    private final double optimalLength;
    private final double pennationAngleAtOptimalLength;
    private final double slackTendonLength;
    private final double maximumMuscleForce;
    private final double mass;
    private final double skP;
    private final double sdP;

    private double forceParallelElement;
    private double forceViscousElement;
    private double forceActiveSType;
    private double forceActiveFType;

    private double activationNormSType;
    private double activationNormFType;
    private double activationNorm;

    private double length;
    private double lengthNorm;
    private double pennationAngle;
    private double velocity = 0;
    private double acceleration = 0;
    private double stretchVelocity = 0;
    private double stretchAcceleration = 0;
    private double force;
    private double forceNorm;

    /**
     * @param units as unidades do músculo, na ordem da configuração
     * @param twitchStep passo com que os abalos são amostrados (ms), o dos motoneurônios
     * @param step passo da simulação (ms)
     * @param tendonForce a força do tendão em série (N), lida no passo anterior
     */
    public HillMuscle(String cdMuscle, MotorUnit[] units, Parameters parameters, double twitchStep, double step,
            DoubleSupplier tendonForce) {
        this.cdMuscle = Objects.requireNonNull(cdMuscle, "músculo");
        this.units = Objects.requireNonNull(units, "unidades");
        this.twitchStep = twitchStep;
        this.tendonForce = Objects.requireNonNull(tendonForce, "força do tendão");

        this.activationFunction = new AlphaTwitch[units.length];
        for (int i = 0; i < units.length; i++) {
            activationFunction[i] = new AlphaTwitch();
        }
        this.picked = new MotorUnit[units.length];
        for (int i = 0; i < units.length; i++) {
            picked[i] = unitWithCd(units[i].cd());
        }

        this.step = step / 1000;
        this.optimalLength = parameters.optimalLength();
        this.pennationAngleAtOptimalLength = parameters.pennationAngle() * Math.PI / 180;
        this.slackTendonLength = parameters.slackTendonLength();
        this.maximumMuscleForce = parameters.maximumMuscleForce();
        this.mass = parameters.muscleMass();
        this.skP = parameters.elasticCoeficientOfParallelElement();
        this.sdP = parameters.viscosityCoeficient();
    }

    private MotorUnit unitWithCd(String cd) {
        for (MotorUnit unit : units) {
            if (cd.equals(unit.cd())) {
                return unit;
            }
        }
        return null;
    }

    /** O comprimento no instante inicial, fixo por músculo. */
    public void setInitialLengthNorm() {
        if (cdMuscle.equals("SOL")) {
            lengthNorm = 0.97026;
        } else if (cdMuscle.equals("MG")) {
            lengthNorm = 1.00972;
        } else if (cdMuscle.equals("LG")) {
            lengthNorm = 1.04775;
        } else if (cdMuscle.equals("TA")) {
            lengthNorm = 1.23948;
        } else {
            lengthNorm = 1;
        }
        length = lengthNorm * optimalLength;
    }

    /** Avança o músculo um passo em {@code t} (ms). */
    public void step(double t) {
        calculateActivation(t);

        pennationAngle = Math.asin(Math.sin(pennationAngleAtOptimalLength) / lengthNorm);

        calculateVelocityAndLength(t / 1000);

        lengthNorm = length / optimalLength;
        stretchVelocity = (velocity / optimalLength) / 1000;
        stretchAcceleration = (acceleration / optimalLength) / 1000000;
    }

    private double unitActivation(MotorUnit mu, double t) {
        if (mu == null) {
            return 0;
        }

        AlphaTwitch tw = activationFunction[mu.index() - 1];

        if (!tw.isStarted()) {
            tw.reset(twitchStep, mu.tpeak());
        }

        if (mu.endPlate().size() == 0) {
            return 0;
        }

        int indexSpike = mu.indexSpike();
        int iteration = mu.iteration();

        if (indexSpike < mu.endPlate().size()) {
            double tSpike = mu.endPlate().spike(indexSpike);
            mu.setTSpike(tSpike);

            if (t > tSpike) {
                mu.setIndexSpike(indexSpike + 1);
                tw.start(iteration);
            }
        }

        mu.setIteration(iteration + 1);
        iteration = mu.iteration();

        double f = tw.value(iteration);

        String type = mu.type();
        if ("S".equals(type) || "FR".equals(type) || "FF".equals(type)) {
            return (2 / (1 + Math.exp(-mu.b() * f)) - 1) * mu.twTet() * mu.gpeak();
        }
        return 0;
    }

    private void calculateActivation(double t) {
        double outputI = 0;
        double outputII = 0;
        double maximumForce = 0;

        for (int n = 0; n < units.length; n++) {
            maximumForce += units[n].gpeak() * units[n].twTet();

            if (units[n].type().equals("S")) {
                outputI += unitActivation(picked[n], t);
            } else {
                outputII += unitActivation(picked[n], t);
            }
        }

        activationNormSType = outputI / maximumForce;
        activationNormFType = outputII / maximumForce;
        activationNorm = activationNormSType + activationNormFType;
    }

    private double calculateForceParallelElement(double lengthNorm) {
        forceParallelElement = (Math.exp(skP * (lengthNorm - 1) / EPS0_M)) / (Math.exp(skP));
        return forceParallelElement;
    }

    private double calculateForceViscousElement(double lengthNorm, double stretchVelocity) {
        forceViscousElement = sdP * (stretchVelocity);
        return forceViscousElement;
    }

    private double calculateForceActiveSType(double lengthNorm, double stretchVelocity) {
        double aux1 = activationNormSType;
        double aux2 = forceLengthS(lengthNorm);
        double aux3 = forceVelocity(lengthNorm, stretchVelocity, true);
        forceActiveSType = aux1 * aux2 * aux3;
        return forceActiveSType;
    }

    private double calculateForceActiveFType(double lengthNorm, double stretchVelocity) {
        double aux1 = activationNormFType;
        double aux2 = forceLengthF(lengthNorm);
        double aux3 = forceVelocity(lengthNorm, stretchVelocity, false);
        forceActiveFType = aux1 * aux2 * aux3;
        return forceActiveFType;
    }

    private static double forceLengthS(double lengthNorm) {
        return Math.exp(-Math.pow(Math.abs((Math.pow(lengthNorm, B_S) - 1) / W_S), P_S));
    }

    private static double forceLengthF(double lengthNorm) {
        return Math.exp(-Math.pow(Math.abs((Math.pow(lengthNorm, B_F) - 1) / W_F), P_F));
    }

    private static double forceVelocity(double lengthNorm, double stretchVelocity, boolean slow) {
        double num;
        double den;

        if (stretchVelocity > 0.1) {
            if (slow) {
                num = BV_S - (AV0_S + AV1_S * lengthNorm + AV2_S * Math.pow(lengthNorm, 2)) * stretchVelocity;
                den = BV_S + stretchVelocity;
            } else {
                num = BV_F - (AV0_F + AV1_F * lengthNorm + AV2_F * Math.pow(lengthNorm, 2)) * stretchVelocity;
                den = BV_F + stretchVelocity;
            }
        } else {
            if (slow) {
                num = MAXIMUM_VELOCITY_S - stretchVelocity;
                den = MAXIMUM_VELOCITY_S + (stretchVelocity * (CV0_S + CV1_S * lengthNorm));
            } else {
                num = MAXIMUM_VELOCITY_F - stretchVelocity;
                den = MAXIMUM_VELOCITY_F + (stretchVelocity * (CV0_F + CV1_F * lengthNorm));
            }
        }

        return num / den;
    }

    private double calculateContractileElementForce(double lengthNorm, double stretchVelocity) {
        forceNorm = calculateForceParallelElement(lengthNorm)
                + calculateForceViscousElement(lengthNorm, stretchVelocity)
                + calculateForceActiveSType(lengthNorm, stretchVelocity)
                + calculateForceActiveFType(lengthNorm, stretchVelocity);

        return forceNorm * maximumMuscleForce;
    }

    private double calculateAcceleration(double length, double velocity) {
        force = calculateContractileElementForce(length / optimalLength, velocity / optimalLength);

        acceleration = (tendonForce.getAsDouble() * Math.cos(pennationAngle)
                - force * Math.pow(Math.cos(pennationAngle), 2)) / mass;

        return acceleration;
    }

    private void calculateVelocityAndLength(double t) {
        double k1_l = velocity;
        double k1_v = calculateAcceleration(length, velocity);

        double k2_l = velocity + (step / 2) * k1_v;
        double k2_v = calculateAcceleration(length + (step / 2) * k1_l, velocity + (step / 2) * k1_v);

        double k3_l = (velocity + (step / 2) * k2_v);
        double k3_v = calculateAcceleration(length + (step / 2) * k2_l, velocity + (step / 2) * k2_v);

        calculateAcceleration(length + step * k3_l, velocity + step * k3_v);

        length = length + (step * (k1_l));
        velocity = velocity + (step * (k1_v));

        Rng.newRandom();
    }

    public String cdMuscle() {
        return cdMuscle;
    }

    public MotorUnit[] units() {
        return units;
    }

    public double maximumMuscleForce() {
        return maximumMuscleForce;
    }

    public double optimalLength() {
        return optimalLength;
    }

    public double slackTendonLength() {
        return slackTendonLength;
    }

    public double mass() {
        return mass;
    }

    public double force() {
        return force;
    }

    public double forceNorm() {
        return forceNorm;
    }

    public double length() {
        return length;
    }

    public double lengthNorm() {
        return lengthNorm;
    }

    public double velocity() {
        return velocity;
    }

    public double acceleration() {
        return acceleration;
    }

    public double stretchVelocity() {
        return stretchVelocity;
    }

    public double stretchAcceleration() {
        return stretchAcceleration;
    }

    public double pennationAngle() {
        return pennationAngle;
    }

    public double forceParallelElement() {
        return forceParallelElement;
    }

    public double forceViscousElement() {
        return forceViscousElement;
    }

    public double forceActiveSType() {
        return forceActiveSType;
    }

    public double forceActiveFType() {
        return forceActiveFType;
    }

    public double activationNorm() {
        return activationNorm;
    }

    public double activationNormSType() {
        return activationNormSType;
    }

    public double activationNormFType() {
        return activationNormFType;
    }
}
