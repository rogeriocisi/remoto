package br.remoto.core.model.random;

/**
 * Um parâmetro que varia ao longo de um pool de neurônios ou unidades motoras: {@code bias} vai
 * de 0 (o primeiro do pool, o menor) a 1 (o último).
 */
public final class Gradient {

    private Gradient() {
    }

    /**
     * Interpolação linear de {@code v1} (bias 0) a {@code v2} (bias 1); negativo vira 0. Quando
     * {@code v2} não é maior que {@code v1}, a conta parte do maior valor, como no original.
     */
    public static double linear(double v1, double v2, double bias) {
        double value;
        double min;
        double max;
        if (v2 > v1) {
            min = v1;
            max = v2;
            value = min + (max - min) * bias;
        } else {
            min = v2;
            max = v1;
            value = max - (max - min) * bias;
        }
        if (value < 0) {
            value = 0;
        }
        return value;
    }

    /** {@link #linear}, mais ruído gaussiano com coeficiente de variação {@code cv}. */
    public static double linearWithNoise(double v1, double v2, double bias, double cv) {
        double value = linear(v1, v2, bias);
        return Draws.gaussian(value, value * cv);
    }
}
