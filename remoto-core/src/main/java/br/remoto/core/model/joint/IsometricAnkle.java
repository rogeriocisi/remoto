package br.remoto.core.model.joint;

import br.remoto.core.model.random.Rng;

/**
 * Tornozelo isométrico: o ângulo fica fixo e o torque é a soma, em cada músculo, da força vezes o
 * braço de momento.
 *
 * <p>Fiel ao ReMoto original: o construtor sorteia um número (resto de um distúrbio aleatório
 * desligado), e antes do primeiro passo o ângulo vale 0, de modo que os músculos veem o tornozelo
 * em 0 no instante inicial.
 */
public final class IsometricAnkle {

    /** Um músculo que age sobre o tornozelo. */
    public interface Actuator {

        /** Força (N) no último passo. */
        double force();

        /** Braço de momento (m) no último passo. */
        double momentArm();
    }

    private final double fixedAngle;
    private final double kneeAngle;

    private double angle = 0;
    private double torque = 0;

    /**
     * @param jointAngle ângulo do tornozelo (graus)
     * @param kneeAngle ângulo do joelho (graus)
     */
    public IsometricAnkle(double jointAngle, double kneeAngle) {
        Rng.random();

        this.fixedAngle = jointAngle * (Math.PI / 180);
        this.kneeAngle = kneeAngle * (Math.PI / 180);
    }

    /** Avança um passo: o ângulo fixo e o torque dos músculos (os nulos não contam). */
    public void step(Actuator[] actuators) {
        angle = fixedAngle;

        double resultantTorque = 0;
        for (Actuator a : actuators) {
            if (a != null) {
                resultantTorque += a.force() * a.momentArm();
            }
        }
        torque = resultantTorque;
    }

    /** Ângulo do tornozelo (rad). */
    public double angle() {
        return angle;
    }

    /** Ângulo do joelho (rad). */
    public double kneeAngle() {
        return kneeAngle;
    }

    /** Torque no tornozelo (N·m). */
    public double torque() {
        return torque;
    }
}
