package br.remoto.core.model.joint;

import br.remoto.core.model.random.Rng;

import java.util.Random;

/**
 * Tarefa de posição do pé (Maurer e Peterka, 2005): o pé (2,01 kg a 0,052 m do tornozelo) contra
 * uma fração da contração voluntária máxima (79 N·m), com rigidez e viscosidade da articulação. O
 * ângulo só começa a ser integrado (Runge-Kutta de 4ª ordem, em segundos) depois de 1 s.
 *
 * <p>Fiel ao ReMoto original: o peso do pé entra multiplicado por zero, os torques de cada estágio
 * do Runge-Kutta usam o ângulo e a velocidade do início do passo, e cada estágio cria um gerador
 * derivado de {@link Rng} para um distúrbio de desvio-padrão zero. O ângulo do joelho fica em
 * graus.
 */
public final class PositionTaskAnkle {

    private static final double GRAVITY = 9.81;
    private static final double FOOT_MASS = 2.01;
    private static final double ANKLE_FOOT_DISTANCE = 0.052;
    private static final double FOOT_INERTIA = (4.0 / 3.0) * FOOT_MASS * Math.pow(ANKLE_FOOT_DISTANCE, 2);
    private static final double JOINT_VISCOSITY = 1.1;
    private static final double JOINT_ELASTICITY = 320;
    private static final double MAXIMUM_VOLUNTARY_TORQUE = 79;
    private static final double LEG_ANGLE = 90 * (Math.PI / 180);
    private static final double REF_ANGLE = 0 * (Math.PI / 180);
    private static final double DISTURBANCE_STD = 0;

    private final double jointAngle;
    private final double kneeAngle;
    private final double percentageOfMVC;
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
     * @param percentageOfMVC fração da contração voluntária máxima
     * @param step passo da simulação (ms)
     */
    public PositionTaskAnkle(double jointAngle, double kneeAngle, double percentageOfMVC, double step) {
        this.jointAngle = jointAngle;
        this.kneeAngle = kneeAngle;
        this.percentageOfMVC = percentageOfMVC;
        this.step = step / 1000;
    }

    /** Em t = 0, põe o pé no ângulo inicial; os valores ficam para a amostragem do passo. */
    public void prepare(double t) {
        if (t == 0) {
            angle = jointAngle * (Math.PI / 180);
            gravitationalTorque = 0 * FOOT_MASS * GRAVITY * ANKLE_FOOT_DISTANCE * Math.sin(LEG_ANGLE - angle)
                    - percentageOfMVC * MAXIMUM_VOLUNTARY_TORQUE * Math.cos(angle);
            passiveTorque = JOINT_VISCOSITY * velocity + JOINT_ELASTICITY * (angle - REF_ANGLE);
            centerMass = ANKLE_FOOT_DISTANCE * Math.sin(angle);
            centerPressure = ANKLE_FOOT_DISTANCE * Math.sin(angle);
        }
    }

    /** O torque resultante do passo anterior (N·m): aceleração vezes inércia do pé. */
    public double jointTorque() {
        return acceleration * FOOT_INERTIA;
    }

    /** Avança um passo: torque dos músculos e, depois de 1 s, a dinâmica do pé. */
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
            centerMass = ANKLE_FOOT_DISTANCE * Math.sin(angle);
            centerPressure = ANKLE_FOOT_DISTANCE * (Math.sin(angle)
                    * (1 + (FOOT_INERTIA / (FOOT_MASS * GRAVITY * ANKLE_FOOT_DISTANCE)) * Math.pow(velocity, 2))
                    - (FOOT_INERTIA / (FOOT_MASS * GRAVITY * ANKLE_FOOT_DISTANCE)) * Math.cos(angle) * acceleration);
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
        gravitationalTorque = 0 * FOOT_MASS * GRAVITY * ANKLE_FOOT_DISTANCE * Math.sin(LEG_ANGLE - angle)
                - percentageOfMVC * MAXIMUM_VOLUNTARY_TORQUE * Math.cos(angle);
        passiveTorque = (JOINT_VISCOSITY * velocity) + (JOINT_ELASTICITY * (angle - REF_ANGLE));

        Random fRandom = Rng.newRandom();
        disturbance = DISTURBANCE_STD * fRandom.nextGaussian();

        acceleration = (torque - gravitationalTorque - passiveTorque + disturbance) / FOOT_INERTIA;
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
