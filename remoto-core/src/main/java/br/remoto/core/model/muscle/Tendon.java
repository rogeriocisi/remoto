package br.remoto.core.model.muscle;

/**
 * Tendão não inervado em série com o músculo de Hill: o comprimento é o do musculotendão menos a
 * projeção do músculo, e a força segue {@code cT·kT·ln(e^((L/L0 − LrT)/kT) + 1)} vezes a força
 * máxima do músculo, com {@code L0 = 1,033 × comprimento de folga}.
 *
 * <p>Como no original, um distúrbio senoidal é calculado a cada passo e somado multiplicado por
 * zero.
 */
public final class Tendon {

    private final double muscleMaximumForce;
    private final double cT;
    private final double kT;
    private final double lrT;
    private final double l0T;

    private double length;
    private double lengthNorm;
    private double forceNorm = 0;
    private double force;

    /**
     * @param slackLength comprimento de folga do tendão (m)
     */
    public Tendon(double muscleMaximumForce, double slackLength, double cT, double kT, double lrT) {
        this.muscleMaximumForce = muscleMaximumForce;
        this.cT = cT;
        this.kT = kT;
        this.lrT = lrT;
        this.l0T = 1.033 * slackLength;
    }

    /**
     * @param t instante (ms)
     * @param musculotendonLength comprimento do musculotendão (m)
     * @param muscleLength comprimento do músculo (m)
     * @param pennationAngle ângulo de penação (rad)
     */
    public void step(double t, double musculotendonLength, double muscleLength, double pennationAngle) {
        double disturbance = 0.00005 * Math.sin(2 * Math.PI * 20 * t / 1000)
                * (1 + 0.5 * Math.sin(2 * Math.PI * t / 1000));

        length = musculotendonLength - muscleLength * Math.cos(pennationAngle) + 0 * disturbance;
        lengthNorm = length / l0T;
        forceNorm = cT * kT * Math.log(Math.exp((lengthNorm - lrT) / kT) + 1);
        force = forceNorm * muscleMaximumForce;
    }

    public double length() {
        return length;
    }

    public double lengthNorm() {
        return lengthNorm;
    }

    public double force() {
        return force;
    }
}
