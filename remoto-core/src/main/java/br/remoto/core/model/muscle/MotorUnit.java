package br.remoto.core.model.muscle;

import br.remoto.core.model.random.Rng;

/**
 * Unidade motora: os disparos do motoneurônio na placa motora, os parâmetros do abalo de cada
 * modelo de músculo, o estado que esses modelos avançam a cada passo e o potencial de ação da
 * unidade (MUAP) no EMG de superfície, com a posição da unidade na secção do músculo.
 *
 * <p>O MUAP é uma função de Hermite-Rodriguez (Lo Conte et al.): de 1ª ordem nas unidades de
 * índice par, de 2ª nas de índice ímpar, alargado e atenuado com a distância até a pele (Fuglevand
 * et al., 1992; Hermens et al., 1992).
 */
public final class MotorUnit {

    /** O T_MAX do original (ms). */
    private static final double T_MAX = 1000000;

    /** Os disparos que chegam à placa motora, na ordem em que acontecem. */
    public interface EndPlate {

        int size();

        /** O i-ésimo disparo (ms). */
        double spike(int i);
    }

    private final int index;
    private final String type;
    private final String cd;
    private final EndPlate endPlate;
    private final int hrType;

    private boolean storedSignals;

    // abalo
    private double gpeak;
    private double gmax;
    private double tpeak;
    private double gpeakRaikova;
    private double tpeakRaikova;
    private double tHalfRaikova;
    private double b;
    private double twTet;
    private double bRaikova;
    private double twTetRaikova;

    // estado dos modelos de músculo
    private int indexSpike;
    private int iteration;
    private double tSpike;

    // EMG
    private double posY;
    private double posZ;
    private double attenuationToSkin;
    private double timeWidening;
    private double ampEMG;
    private double lambdaEMG;
    private boolean emgAttenuation;
    private int indexSpikeEmg;
    private double tSpikeEmg;

    /**
     * @param index índice do motoneurônio no núcleo, de 1 em diante
     * @param type tipo do motoneurônio (S, FR, FF)
     * @param cd código do motoneurônio
     */
    public MotorUnit(int index, String type, String cd, EndPlate endPlate) {
        if (endPlate == null) {
            throw new IllegalArgumentException("a placa motora é obrigatória");
        }
        this.index = index;
        this.type = type;
        this.cd = cd;
        this.endPlate = endPlate;
        this.hrType = index % 2 == 0 ? 1 : 2;
    }

    /** Recomeça a leitura dos disparos para o EMG. */
    public void resetEmg(boolean emgAttenuation) {
        this.emgAttenuation = emgAttenuation;

        indexSpikeEmg = 0;

        if (endPlate.size() > 0) {
            tSpikeEmg = endPlate.spike(0);
        } else {
            tSpikeEmg = T_MAX;
        }
    }

    /** A contribuição desta unidade para o EMG em {@code t} (mV). Avança um passo por chamada. */
    public double emg(double t) {
        double out = 0;

        int count = endPlate.size();
        if (count == 0) {
            return 0;
        }

        if (t > tSpikeEmg) {
            indexSpikeEmg++;

            if (count > indexSpikeEmg) {
                tSpikeEmg = endPlate.spike(indexSpikeEmg);
            } else {
                tSpikeEmg = T_MAX;
                indexSpikeEmg = count - 1;
            }
        }

        double timeCte = lambdaEMG;

        if (emgAttenuation) {
            timeCte *= timeWidening;
        }

        for (int n = indexSpikeEmg; n >= 0; n--) {
            double tPreviousSpike = endPlate.spike(n);

            if (tPreviousSpike > t) {
                continue;
            }

            double value;

            double tA = t - tPreviousSpike - 3 * timeCte;

            // o MUAP já voltou ao repouso
            if (tA > 6 * timeCte) {
                break;
            }

            double term = Math.pow(tA / timeCte, 2);

            if (hrType == 1) {
                double tp = timeCte / Math.sqrt(2);
                double aux = tp * Math.exp(-Math.pow(tp / timeCte, 2));
                value = (1 / aux) * tA * Math.exp(-term);
            } else {
                value = (1 - 2 * term) * Math.exp(-term);
            }

            out += ampEMG * value;
        }

        if (emgAttenuation) {
            out *= attenuationToSkin;
        }

        return out;
    }

    /**
     * Sorteia a posição da unidade num círculo da secção do músculo (desiste depois de 11
     * tentativas e fica no diâmetro horizontal).
     */
    public void randomSpatialDistribution(double muscleThickness, double skinLayer, double tauAttenuation,
            double cteWidening) {
        int cont = 0;
        double y = 2;
        double z = 2;

        while (Math.pow(y, 2) + Math.pow(z, 2) >= 1) {
            y = 2 * (0.5 - Rng.random());
            z = 2 * (0.5 - Rng.random());

            cont++;

            if (cont > 10) {
                z = 0;
                break;
            }
        }

        y = y * (muscleThickness / 2) + (muscleThickness / 2) + skinLayer;
        z *= (muscleThickness / 2);

        double distance = Math.sqrt(Math.pow(z, 2) + Math.pow(y, 2));

        place(y, z, distance, tauAttenuation, cteWidening);
    }

    /** Distribui as unidades em linha, da pele à espessura do músculo. */
    public void linearSpatialDistribution(double muscleThickness, double skinLayer, double tauAttenuation,
            double cteWidening, int muNumber, int muTotal) {
        double y = (double) muNumber / (muTotal - 1);
        double z = 0;

        y = y * (muscleThickness - skinLayer) + skinLayer;

        place(y, z, y, tauAttenuation, cteWidening);
    }

    /** Uma posição fixa, da configuração. */
    public void presetSpatialDistribution(double y, double z, double tauAttenuation, double cteWidening) {
        double distance = Math.sqrt(Math.pow(y, 2) + Math.pow(z, 2));

        place(y, z, distance, tauAttenuation, cteWidening);
    }

    private void place(double y, double z, double distance, double tauAttenuation, double cteWidening) {
        posY = y;
        posZ = z;

        attenuationToSkin = Math.exp(-distance / tauAttenuation);
        timeWidening = 1 + cteWidening * distance;
    }

    public int index() {
        return index;
    }

    public String type() {
        return type;
    }

    public String cd() {
        return cd;
    }

    public EndPlate endPlate() {
        return endPlate;
    }

    public boolean storedSignals() {
        return storedSignals;
    }

    public void setStoredSignals(boolean storedSignals) {
        this.storedSignals = storedSignals;
    }

    public double gpeak() {
        return gpeak;
    }

    public void setGpeak(double gpeak) {
        this.gpeak = gpeak;
    }

    public double gmax() {
        return gmax;
    }

    public void setGmax(double gmax) {
        this.gmax = gmax;
    }

    public double tpeak() {
        return tpeak;
    }

    public void setTpeak(double tpeak) {
        this.tpeak = tpeak;
    }

    public double gpeakRaikova() {
        return gpeakRaikova;
    }

    public void setGpeakRaikova(double gpeakRaikova) {
        this.gpeakRaikova = gpeakRaikova;
    }

    public double tpeakRaikova() {
        return tpeakRaikova;
    }

    public void setTpeakRaikova(double tpeakRaikova) {
        this.tpeakRaikova = tpeakRaikova;
    }

    public double tHalfRaikova() {
        return tHalfRaikova;
    }

    public void setTHalfRaikova(double tHalfRaikova) {
        this.tHalfRaikova = tHalfRaikova;
    }

    public double b() {
        return b;
    }

    public void setB(double b) {
        this.b = b;
    }

    public double twTet() {
        return twTet;
    }

    public void setTwTet(double twTet) {
        this.twTet = twTet;
    }

    public double bRaikova() {
        return bRaikova;
    }

    public void setBRaikova(double bRaikova) {
        this.bRaikova = bRaikova;
    }

    public double twTetRaikova() {
        return twTetRaikova;
    }

    public void setTwTetRaikova(double twTetRaikova) {
        this.twTetRaikova = twTetRaikova;
    }

    /** O próximo disparo a entrar no abalo (índice na placa motora). */
    public int indexSpike() {
        return indexSpike;
    }

    public void setIndexSpike(int indexSpike) {
        this.indexSpike = indexSpike;
    }

    /** Passos de abalo já calculados. */
    public int iteration() {
        return iteration;
    }

    public void setIteration(int iteration) {
        this.iteration = iteration;
    }

    public double tSpike() {
        return tSpike;
    }

    public void setTSpike(double tSpike) {
        this.tSpike = tSpike;
    }

    public double posY() {
        return posY;
    }

    public void setPosY(double posY) {
        this.posY = posY;
    }

    public double posZ() {
        return posZ;
    }

    public void setPosZ(double posZ) {
        this.posZ = posZ;
    }

    public double attenuationToSkin() {
        return attenuationToSkin;
    }

    public void setAttenuationToSkin(double attenuationToSkin) {
        this.attenuationToSkin = attenuationToSkin;
    }

    public double timeWidening() {
        return timeWidening;
    }

    public void setTimeWidening(double timeWidening) {
        this.timeWidening = timeWidening;
    }

    public double ampEMG() {
        return ampEMG;
    }

    public void setAmpEMG(double ampEMG) {
        this.ampEMG = ampEMG;
    }

    public double lambdaEMG() {
        return lambdaEMG;
    }

    public void setLambdaEMG(double lambdaEMG) {
        this.lambdaEMG = lambdaEMG;
    }

    public boolean emgAttenuation() {
        return emgAttenuation;
    }

    public void setEmgAttenuation(boolean emgAttenuation) {
        this.emgAttenuation = emgAttenuation;
    }
}
