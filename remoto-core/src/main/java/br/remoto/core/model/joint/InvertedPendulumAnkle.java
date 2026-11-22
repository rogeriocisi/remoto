package br.remoto.core.model.joint;

import br.remoto.core.model.random.Rng;

import java.util.Random;

/**
 * Postura ereta como pêndulo invertido no tornozelo (60 kg, centro de massa a 0,85 m): torque
 * gravitacional, torque passivo viscoelástico da articulação, o torque dos músculos (dobrado, pelas
 * duas pernas) e um distúrbio gaussiano de 0,05 N·m. O ângulo só começa a ser integrado (Runge-Kutta
 * de 4ª ordem, em segundos) depois de 1 s.
 *
 * <p>Fiel ao ReMoto original: os torques gravitacional e passivo de cada estágio do Runge-Kutta usam
 * o ângulo e a velocidade do início do passo, e cada estágio sorteia o distúrbio com um gerador
 * derivado de {@link Rng}. O ângulo do joelho fica em graus, ao contrário do tornozelo isométrico.
 */
public final class InvertedPendulumAnkle {

    private static final double GRAVITY = 9.81;
    private static final double BODY_MASS = 60;
    private static final double BODY_HEIGHT = 0.50 * 1.70;
    private static final double BODY_INERTIA = (4.0 / 3.0) * BODY_MASS * Math.pow(BODY_HEIGHT, 2);
    private static final double JOINT_VISCOSITY = 5.81;
    private static final double JOINT_ELASTICITY = 0.65 * BODY_MASS * GRAVITY * BODY_HEIGHT;
    private static final double REF_ANGLE = 0 * (Math.PI / 180);
    private static final double DISTURBANCE_STD = 0.05;

    private final double jointAngle;
    private final double kneeAngle;
    private final double step;

    private double angle = 0 * (Math.PI / 180);
    private double velocity = 0;
    private double acceleration = 0;
    private double torque = 0;
    private double gravitationalTorque;
    private double passiveTorque;
    private double centerMass;
    private double centerPressure;
    private double disturbance;

    /**
     * @param jointAngle ângulo inicial do tornozelo (graus)
     * @param kneeAngle ângulo do joelho (graus)
     * @param step passo da simulação (ms)
     */
    public InvertedPendulumAnkle(double jointAngle, double kneeAngle, double step) {
        this.jointAngle = jointAngle;
        this.kneeAngle = kneeAngle;
        this.step = step / 1000;
    }

    /** Em t = 0, põe o pêndulo no ângulo inicial; os valores ficam para a amostragem do passo. */
    public void prepare(double t) {
        if (t == 0) {
            angle = jointAngle * (Math.PI / 180);
            gravitationalTorque = BODY_MASS * GRAVITY * BODY_HEIGHT * Math.sin(angle);
            passiveTorque = JOINT_VISCOSITY * velocity + JOINT_ELASTICITY * (angle - REF_ANGLE);
            centerMass = BODY_HEIGHT * Math.sin(angle);
            centerPressure = BODY_HEIGHT * Math.sin(angle);
        }
    }

    /** O torque total do passo anterior (N·m), como o original o mostrava. */
    public double jointTorque() {
        return gravitationalTorque + (2 * torque) - passiveTorque + disturbance;
    }

    /** Avança um passo: torque dos músculos e, depois de 1 s, a dinâmica do pêndulo. */
    public void advance(double t, IsometricAnkle.Actuator[] actuators) {
        double resultantMuscleTorque = 0;
        for (IsometricAnkle.Actuator a : actuators) {
            if (a != null) {
                resultantMuscleTorque += a.force() * a.momentArm();
            }
        }
        torque = resultantMuscleTorque;

        if (t >= 1000.0) {
            double ts = t / 1000;
            calculateAngle(ts);
            centerMass = BODY_HEIGHT * Math.sin(angle);
            centerPressure = BODY_HEIGHT * (Math.sin(angle)
                    * (1 + (BODY_INERTIA / (BODY_MASS * GRAVITY * BODY_HEIGHT)) * Math.pow(velocity, 2))
                    - (BODY_INERTIA / (BODY_MASS * GRAVITY * BODY_HEIGHT)) * Math.cos(angle) * acceleration);
        }
    }

    private void calculateAngle(double t) {
        double k1_a = velocity;
        double k1_v = calculateAcceleration();
        double k2_a = velocity + (step / 2) * k1_v;
        double k2_v = calculateAcceleration();
        double k3_a = (velocity + (step / 2) * k2_v);
        double k3_v = calculateAcceleration();
        double k4_a = (velocity + step * k3_v);
        double k4_v = calculateAcceleration();

        angle = angle + (step * (k1_a + 2 * k2_a + 2 * k3_a + k4_a) / 6);
        velocity = velocity + (step * (k1_v + 2 * k2_v + 2 * k3_v + k4_v) / 6);
    }

    private double calculateAcceleration() {
        gravitationalTorque = BODY_MASS * GRAVITY * BODY_HEIGHT * Math.sin(angle);
        passiveTorque = (JOINT_VISCOSITY * velocity) + (JOINT_ELASTICITY * (angle - REF_ANGLE));

        Random fRandom = Rng.newRandom();
        disturbance = DISTURBANCE_STD * fRandom.nextGaussian();

        acceleration = (gravitationalTorque + (2 * torque) - passiveTorque + disturbance) / BODY_INERTIA;
        return acceleration;
    }

    /** Ângulo do tornozelo (rad). */
    public double angle() {
        return angle;
    }

    /** Ângulo do joelho (graus). */
    public double kneeAngle() {
        return kneeAngle;
    }

    /** Velocidade angular (rad/s). */
    public double velocity() {
        return velocity;
    }

    public double acceleration() {
        return acceleration;
    }

    /** Torque dos músculos (N·m). */
    public double muscleTorque() {
        return torque;
    }

    public double gravitationalTorque() {
        return gravitationalTorque;
    }

    public double passiveTorque() {
        return passiveTorque;
    }

    public double centerMass() {
        return centerMass;
    }

    public double centerPressure() {
        return centerPressure;
    }

    public double disturbance() {
        return disturbance;
    }
}
