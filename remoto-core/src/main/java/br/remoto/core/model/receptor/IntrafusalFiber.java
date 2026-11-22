package br.remoto.core.model.receptor;

/**
 * Fibra intrafusal do fuso de Mileusnic et al. (2006): constantes do modelo e o estado comum
 * (comprimento normalizado, velocidade e aceleração de estiramento, ativação fusimotora e tensão).
 */
abstract class IntrafusalFiber {

    static final double K_SR = 10.4649;
    static final double K_PR = 0.1500;
    static final double M = 0.0002;
    static final double beta_1 = 0.2592;
    static final double beta_2_chain = -0.0690;
    static final double GAMMA_1 = 0.0289;
    static final double GAMMA_2_chain = 0.0954;
    static final double C_lengthening = 1;
    static final double C_shortening = 0.4200;
    static final double X_chain = 0.7;
    static final double L_SR_N = 0.0423;
    static final double a = 0.3;
    static final double R = 0.46;
    static final double L_SR_0 = 0.04;
    static final double L_PR_0 = 0.76;
    static final double L_secondary_chain = 0.04;
    static final double freq_chain = 90;
    static final double p = 2;

    protected final String cdMuscle;

    protected double gamma_static;
    protected double gamma_dynamic;
    protected double time_step;
    protected double fusimotorActivation;
    protected double intrafusalFiberTension;
    protected double beta;
    protected double GAMMA;

    protected double lengthNorm;
    protected double stretchVelocity;
    protected double stretchAcceleration;

    /** @param step passo da simulação (ms) */
    protected IntrafusalFiber(String cdMuscle, double step, double gamma_dyn, double gamma_static) {
        this.cdMuscle = cdMuscle;
        this.time_step = step / 1000;
        this.gamma_dynamic = gamma_dyn;
        this.gamma_static = gamma_static;
    }

    public abstract double calculateFusimotorActivation(double t);

    public abstract double calculatePrimaryAfferentActivity(double t);

    public abstract double calculateSecondaryAfferentActivity(double t);

    public abstract double calculateFiberTension(double t);

    protected abstract void betaAndGammaFuncs(double t);

    public double calculateC(double stretch_velocity, double dT) {
        if ((stretch_velocity - dT / K_SR) >= 0) return C_lengthening;
        else return C_shortening;
    }

    void setLengthNorm(double lengthNorm) {
        this.lengthNorm = lengthNorm;
    }

    void setStretchVelocity(double stretchVelocity) {
        this.stretchVelocity = stretchVelocity;
    }

    void setStretchAcceleration(double stretchAcceleration) {
        this.stretchAcceleration = stretchAcceleration;
    }

    double fusimotorActivation() {
        return fusimotorActivation;
    }

    double intrafusalFiberTension() {
        return intrafusalFiberTension;
    }
}
