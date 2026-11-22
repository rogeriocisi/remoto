package br.remoto.core.model.muscle;

import java.util.Objects;

/**
 * Músculo de momentos de distribuição: a ativação de cada tipo de unidade (lenta e rápida) é a soma,
 * em todos os disparos, de {@code (e^(−t/τ1) − e^(−t/τ2))·(τ1 − τ2)}, com τ1 = 20 ms e τ2 = 15 ms.
 *
 * <p>Como no original, o modelo nunca chegou a calcular força: a força do músculo fica em 0, e só a
 * ativação e o estado das unidades avançam.
 */
public final class DistributionMomentsMuscle {

    private static final double TAU1 = 20;
    private static final double TAU2 = 15;

    private final MotorUnit[] units;
    private final MotorUnit[] picked;

    private double activationNormSType;
    private double activationNormFType;
    private double activationNorm;

    public DistributionMomentsMuscle(MotorUnit[] units) {
        this.units = Objects.requireNonNull(units, "unidades");
        this.picked = new MotorUnit[units.length];
        for (int i = 0; i < units.length; i++) {
            picked[i] = unitWithCd(units[i].cd());
        }
    }

    private MotorUnit unitWithCd(String cd) {
        for (MotorUnit unit : units) {
            if (cd.equals(unit.cd())) {
                return unit;
            }
        }
        return null;
    }

    /** Avança as unidades um passo em {@code t} e calcula a ativação. */
    public void step(double t) {
        double outputI = 0;
        double outputII = 0;

        for (int n = 0; n < units.length; n++) {
            if (units[n].type().equals("S")) {
                outputI += unitActivation(picked[n], t);
            } else {
                outputII += unitActivation(picked[n], t);
            }
        }

        activationNormSType = outputI;
        activationNormFType = outputII;
        activationNorm = activationNormSType + activationNormFType;
    }

    /**
     * A ativação das unidades lentas ({@code slow}) ou rápidas em {@code t}, recalculada: avança
     * todas as unidades mais um passo.
     */
    public double activation(double t, boolean slow) {
        double outputI = 0;
        double outputII = 0;

        for (int n = 0; n < units.length; n++) {
            if (units[n].type().equals("S")) {
                outputI += unitActivation(picked[n], t);
            } else {
                outputII += unitActivation(picked[n], t);
            }
        }

        return slow ? outputI : outputII;
    }

    private double unitActivation(MotorUnit mu, double t) {
        double activation = 0;

        if (mu == null) {
            return 0;
        }

        int count = mu.endPlate().size();

        if (count == 0) {
            return 0;
        }

        int indexSpike = mu.indexSpike();
        int iteration = mu.iteration();
        double tSpike = mu.tSpike();

        if (indexSpike < count) {
            tSpike = mu.endPlate().spike(indexSpike);
            mu.setTSpike(tSpike);

            if (t > tSpike) {
                mu.setIndexSpike(indexSpike + 1);
            }
        }

        for (int s = 0; s < count; s++) {
            double spike = mu.endPlate().spike(s);

            if (t >= spike && t - tSpike < 10.0 * mu.tHalfRaikova()) {
                activation += (Math.exp(-(t - spike) / TAU1) - Math.exp(-(t - spike) / TAU2)) * (TAU1 - TAU2);
            }
        }

        mu.setIteration(iteration + 1);

        return activation;
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
