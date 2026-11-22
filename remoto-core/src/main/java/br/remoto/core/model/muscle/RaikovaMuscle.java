package br.remoto.core.model.muscle;

import java.util.Objects;

/**
 * Músculo de unidades motoras com o abalo de Raikova e Aladjov (2002): cada disparo soma
 * {@code p·t^m·e^(−k·t)}, com k, p e m tirados do tempo de contração e do tempo de meia
 * relaxação da unidade; a soma passa pela mesma saturação do SOCDS, com os parâmetros de Raikova.
 *
 * <p>Fiel ao ReMoto original: a cada passo os disparos da unidade são todos revisitados, e só
 * contam enquanto o último lido tiver menos de 10 meias relaxações; o comprimento fica em 1, como
 * no SOCDS.
 */
public final class RaikovaMuscle {

    private final MotorUnit[] units;
    private final MotorUnit[] picked;
    private final double[] k;
    private final double[] p;
    private final double[] m;
    private final double step;

    private CriticallyDampedMuscle.UnitForceListener listener;

    private double lengthNorm;
    private double lastNormLength = 0;
    private double lastStretchVelocity = 0;
    private double stretchVelocity = 0;
    private double stretchAcceleration = 0;
    private double force;

    /**
     * @param units as unidades do músculo, na ordem da configuração
     * @param step passo da simulação (ms), para as derivadas do comprimento
     */
    public RaikovaMuscle(MotorUnit[] units, double step) {
        this.units = Objects.requireNonNull(units, "unidades");
        this.step = step;
        this.k = new double[units.length];
        this.p = new double[units.length];
        this.m = new double[units.length];

        for (MotorUnit unit : units) {
            double tc = unit.tpeakRaikova();
            double thr = unit.tHalfRaikova();
            int index = unit.index() - 1;
            k[index] = Math.log(2) / (-tc * Math.log(thr / tc) + thr - tc);
            p[index] = Math.exp(-k[index] * tc * (Math.log(tc) - 1));
            m[index] = k[index] * tc;
        }

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

    public void setUnitForceListener(CriticallyDampedMuscle.UnitForceListener listener) {
        this.listener = listener;
    }

    /** Avança o músculo um passo em {@code t}: comprimento, derivadas e força. */
    public void step(double t) {
        lengthNorm = 1;

        if (t == 0) {
            stretchVelocity = 0;
            stretchAcceleration = 0;
        } else {
            stretchVelocity = (lengthNorm - lastNormLength) / step;
            stretchAcceleration = (stretchVelocity - lastStretchVelocity) / step;
        }
        lastNormLength = lengthNorm;
        lastStretchVelocity = stretchVelocity;

        force = muscleForce(t);
    }

    /** Força do músculo em {@code t} (N): avança todas as unidades. */
    public double muscleForce(double t) {
        double output = 0;

        for (MotorUnit unit : picked) {
            output += unitForce(unit, t);
        }

        return output;
    }

    /** Força da unidade em {@code t} (N): avança a unidade um passo. */
    public double unitForce(MotorUnit mu, double t) {
        double f = 0;
        double output = 0;

        if (mu == null) {
            return 0;
        }

        int count = mu.endPlate().size();

        if (count == 0) {
            notify(mu, t, 0);
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

        mu.setIteration(iteration + 1);

        String type = mu.type();
        boolean motoneuron = "S".equals(type) || "FR".equals(type) || "FF".equals(type);

        for (int s = 0; s < count; s++) {
            double spike = mu.endPlate().spike(s);

            if (t >= spike && t - tSpike < 10.0 * mu.tHalfRaikova()) {
                f += raikova(t - spike, mu.index());

                if (motoneuron) {
                    output = (2 / (1 + Math.exp(-mu.bRaikova() * f)) - 1) * mu.twTetRaikova() * mu.gpeakRaikova();
                }
            }
        }

        notify(mu, t, output);

        return output;
    }

    private double raikova(double t, int motorUnitIndex) {
        return p[motorUnitIndex - 1] * Math.pow(t, m[motorUnitIndex - 1]) * Math.exp(-k[motorUnitIndex - 1] * t);
    }

    private void notify(MotorUnit mu, double t, double force) {
        if (listener != null && mu.storedSignals()) {
            listener.unitForce(mu, t, force);
        }
    }

    public double force() {
        return force;
    }

    public double lengthNorm() {
        return lengthNorm;
    }

    public double stretchVelocity() {
        return stretchVelocity;
    }

    public double stretchAcceleration() {
        return stretchAcceleration;
    }
}
