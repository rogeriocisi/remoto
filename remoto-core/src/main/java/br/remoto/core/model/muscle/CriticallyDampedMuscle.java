package br.remoto.core.model.muscle;

import java.util.Objects;

/**
 * Músculo de unidades motoras com abalo de 2ª ordem criticamente amortecido (SOCDS): a força de
 * cada unidade é o abalo ({@link AlphaTwitch}) somado a cada disparo na placa motora, saturado por
 * {@code 2/(1 + e^(−b·f)) − 1} e escalado pela razão tétano/abalo e pelo pico do abalo; a força do
 * músculo é a soma das unidades.
 *
 * <p>O comprimento normalizado fica em 1: o original somava ao do sóleo um triângulo de amplitude
 * zero. Velocidade e aceleração vêm da derivação numérica desse comprimento.
 */
public final class CriticallyDampedMuscle {

    /** Recebe a força de cada unidade marcada para guardar sinais, no passo em que é calculada. */
    public interface UnitForceListener {
        void unitForce(MotorUnit unit, double t, double force);
    }

    private final MotorUnit[] units;
    private final MotorUnit[] picked;
    private final AlphaTwitch[] twitch;
    private final double twitchStep;
    private final double step;
    private final double maximumMuscleForce;

    private UnitForceListener listener;

    private double lengthNorm;
    private double lastNormLength = 0;
    private double lastStretchVelocity = 0;
    private double stretchVelocity = 0;
    private double stretchAcceleration = 0;
    private double force;

    /**
     * @param units as unidades do músculo, na ordem da configuração
     * @param twitchStep passo com que os abalos são amostrados (ms), o dos motoneurônios
     * @param step passo da simulação (ms), para as derivadas do comprimento
     */
    public CriticallyDampedMuscle(MotorUnit[] units, double twitchStep, double step) {
        this.units = Objects.requireNonNull(units, "unidades");
        this.twitchStep = twitchStep;
        this.step = step;
        this.twitch = new AlphaTwitch[units.length];

        double maximum = 0;
        for (int i = 0; i < units.length; i++) {
            twitch[i] = new AlphaTwitch();
            maximum += units[i].gpeak() * units[i].twTet();
        }
        this.maximumMuscleForce = maximum;

        // a soma percorre as unidades pelo código: a primeira com o código de cada posição
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

    public void setUnitForceListener(UnitForceListener listener) {
        this.listener = listener;
    }

    /** Avança o músculo um passo em {@code t}: comprimento, derivadas e força. */
    public void step(double t) {
        lengthNorm = 1;
        differentiateLength(t);
        force = muscleForce(t);
    }

    private void differentiateLength(double t) {
        if (t == 0) {
            stretchVelocity = 0;
            stretchAcceleration = 0;
        } else {
            stretchVelocity = (lengthNorm - lastNormLength) / step;
            stretchAcceleration = (stretchVelocity - lastStretchVelocity) / step;
        }

        lastNormLength = lengthNorm;
        lastStretchVelocity = stretchVelocity;
    }

    /** Força do músculo em {@code t} (N): avança o abalo de todas as unidades. */
    public double muscleForce(double t) {
        double output = 0;

        for (MotorUnit unit : picked) {
            output += unitForce(unit, t);
        }

        return output;
    }

    /** Força da unidade em {@code t} (N): avança o abalo dela um passo. */
    public double unitForce(MotorUnit mu, double t) {
        if (mu == null) {
            return 0;
        }

        AlphaTwitch tw = twitch[mu.index() - 1];

        if (!tw.isStarted()) {
            tw.reset(twitchStep, mu.tpeak());
        }

        if (mu.endPlate().size() == 0) {
            notify(mu, t, 0);
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

        double output = 0;

        if (isMotoneuronType(mu.type())) {
            output = saturation(f, mu.b()) * mu.twTet() * mu.gpeak();
        }

        notify(mu, t, output);

        return output;
    }

    private static boolean isMotoneuronType(String type) {
        return "S".equals(type) || "FR".equals(type) || "FF".equals(type);
    }

    private static double saturation(double force, double b) {
        return 2 / (1 + Math.exp(-b * force)) - 1;
    }

    private void notify(MotorUnit mu, double t, double force) {
        if (listener != null && mu.storedSignals()) {
            listener.unitForce(mu, t, force);
        }
    }

    public MotorUnit[] units() {
        return units;
    }

    public double maximumMuscleForce() {
        return maximumMuscleForce;
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
