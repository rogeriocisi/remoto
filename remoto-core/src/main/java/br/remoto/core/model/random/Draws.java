package br.remoto.core.model.random;

/** Sorteios das distribuições usadas pelos modelos, sobre {@link Rng}. */
public final class Draws {

    private Draws() {
    }

    /**
     * Aproximação de uma gaussiana pela soma de 12 uniformes (Irwin-Hall), como no ReMoto
     * original: média {@code m}, desvio-padrão {@code dp}.
     */
    public static double gaussian(double m, double dp) {
        double aux = 0.0;
        for (int i = 1; i <= 12; i++) {
            aux += Rng.random();
        }
        return m + dp * (aux - 6.0);
    }

    /**
     * Gama de ordem inteira {@code k} e escala {@code a}, pelo produto de {@code k} uniformes.
     * Com {@code k} = 0 o produto fica 1 e o resultado é {@code -a·log(1)}; com {@code a}
     * infinito, isso dá NaN, como no original.
     */
    public static double gamma(double a, double k) {
        double aux = 1.0;
        for (int i = 1; i <= k; i++) {
            aux *= Rng.random();
        }
        return -a * Math.log(aux);
    }

    /** Uniforme entre {@code v1} e {@code v2}, em qualquer ordem; negativo vira 0. */
    public static double uniform(double v1, double v2) {
        double min;
        double max;
        if (v2 > v1) {
            min = v1;
            max = v2;
        } else {
            min = v2;
            max = v1;
        }
        double p = Rng.random();
        double value = min + (max - min) * p;
        if (value < 0) {
            value = 0;
        }
        return value;
    }
}
