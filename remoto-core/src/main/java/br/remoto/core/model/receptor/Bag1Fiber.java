package br.remoto.core.model.receptor;

/**
 * Fibra bag1 (fusimotor dinâmico) do fuso de Mileusnic et al. (2006).
 *
 * <p>O corpo é o do ReMoto original, sem alteração nas expressões: integração de Euler da tensão
 * e da ativação fusimotora (o Runge-Kutta ficou comentado no original), com o passo em segundos.
 */
@SuppressWarnings("unused")
final class Bag1Fiber extends IntrafusalFiber {

    public static final double beta_0_bag1 = 0.0605;
    public static final double L_PR_N_bag1 = 0;
    private double G_primary_bag1;
    public static final double L_secondary_bag1 = 0;
    public static final double tau_bag1 = 0.149;
    public static final double freq_bag1 = 60;

    private double z = 0;

    private double k1_T = 0;
    private double k2_T = 0;
    private double k3_T = 0;
    private double k4_T = 0;

    private double k1_z = 0;
    private double k2_z = 0;
    private double k3_z = 0;
    private double k4_z = 0;

    Bag1Fiber(String cdMuscle, double step, double gamma_dyn, double gamma_static, double primaryBag1Gain) {
        super(cdMuscle, step, gamma_dyn, gamma_static);
        if (cdMuscle.equals("SOL") || cdMuscle.equals("MG") || cdMuscle.equals("LG") || cdMuscle.equals("TA"))
            this.G_primary_bag1 = primaryBag1Gain;
        else
            this.G_primary_bag1 = 0;
    }


    @Override
    public double calculateFiberTension(double t) {

        if (t == 0){
            intrafusalFiberTension = 0;
        }
        else{
            try
            {
                k1_T = f1(t                    , intrafusalFiberTension                     , z);

                intrafusalFiberTension =  intrafusalFiberTension + (time_step * (k1_T));

                k1_z = f2(t                    , intrafusalFiberTension                     , z);

                z = z + (time_step * (k1_z));

            }
            catch(Exception e)
            {
                System.out.println( "Fusimotor Activation: " + e.getMessage() );
            }
        }

        return intrafusalFiberTension;
    }

    @Override
    public double calculatePrimaryAfferentActivity(double t) {
        double afferentActivity;
        afferentActivity = G_primary_bag1 * ((intrafusalFiberTension/K_SR) - (L_SR_N - L_SR_0));
        if (afferentActivity < 0) afferentActivity = 0;
        return afferentActivity;
    }

    protected void betaAndGammaFuncs(double t){
        double f_dyn = fusimotorActivation;
        beta = beta_0_bag1 + beta_1 * f_dyn;
        GAMMA = GAMMA_1 * f_dyn;
    }

    public double calculateFusimotorActivation(double t){

        if (t == 0){
            fusimotorActivation = 0;
        }
        else{
            try
            {
                double k1_bag1 = Df_dynDt(t                    , fusimotorActivation);

                fusimotorActivation =  fusimotorActivation + (time_step * (k1_bag1));
            }
            catch(Exception e)
            {
                System.out.println( "Fusimotor Activation: " + e.getMessage() );
            }
        }

        return fusimotorActivation;
    }

    private double Df_dynDt(double t, double f_dyn){

        double term1 = Math.pow(gamma_dynamic, p)/tau_bag1;

        double term2 = Math.pow(gamma_dynamic, p) + Math.pow(freq_bag1, p);

        double term3 = f_dyn/tau_bag1;

        return (term1/term2) - term3;
    }

    private double f1(double t, double T, double z) {

        return z;
    }

    private double f2(double t, double T, double z) {

        betaAndGammaFuncs(t);

        double term1 = K_SR/M;

        double term2 = calculateC(stretchVelocity, z) * beta * Math.signum(stretchVelocity - z/K_SR) *
                        Math.pow(Math.abs(stretchVelocity - z/K_SR), a) * Math.abs(lengthNorm - L_SR_0 - T/K_SR - R);

        double term3 = K_PR * (lengthNorm - L_SR_0 - T/K_SR - L_PR_0);

        double term4 = M * stretchAcceleration;

        double term5 = GAMMA;

        double term6 = T;

        return term1 * (term2 + term3 + term4 + term5 - term6);
    }

    public double calculateSecondaryAfferentActivity(double t) {
        return 0;
    }
}
