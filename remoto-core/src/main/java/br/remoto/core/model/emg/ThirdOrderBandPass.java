package br.remoto.core.model.emg;

/**
 * O filtro "3rd order low - 3rd order high" da tela de resultados do site antigo
 * ({@code ButterworthBilinear}): um passa-baixa e um passa-alta de Butterworth de 3ª ordem em
 * série, discretizados pela transformação bilinear com pré-distorção da frequência de corte. As
 * três primeiras amostras de cada estágio ficam em zero, como no original.
 */
public final class ThirdOrderBandPass {

    private record Coefficients(double c0, double c1, double c2, double c3, double d1, double d2, double d3) {
    }

    private ThirdOrderBandPass() {
    }

    /**
     * @param fcLow  corte inferior (Hz), o do passa-alta
     * @param fcHigh corte superior (Hz), o do passa-baixa
     * @param period período de amostragem (s)
     */
    public static double[] bandPass(double[] x, double fcLow, double fcHigh, double period) {
        Coefficients low = coefficients(false, fcHigh, period);
        Coefficients high = coefficients(true, fcLow, period);
        double[] aux = new double[x.length];
        double[] y = new double[x.length];
        for (int i = 3; i < x.length; i++) {
            aux[i] = low.c0 * x[i] + low.c1 * x[i - 1] + low.c2 * x[i - 2] + low.c3 * x[i - 3]
                    + low.d1 * aux[i - 1] + low.d2 * aux[i - 2] + low.d3 * aux[i - 3];
            y[i] = high.c0 * aux[i] + high.c1 * aux[i - 1] + high.c2 * aux[i - 2] + high.c3 * aux[i - 3]
                    + high.d1 * y[i - 1] + high.d2 * y[i - 2] + high.d3 * y[i - 3];
        }
        return y;
    }

    private static Coefficients coefficients(boolean highPass, double fc, double period) {
        double wc = 2 * Math.PI * fc;
        double wcw = (2 / period) * Math.tan((wc * period) / 2);
        double a = wcw * period;
        double a0 = 8 + 8 * a + 4 * Math.pow(a, 2) + Math.pow(a, 3);
        double a1 = -24 - 8 * a + 4 * Math.pow(a, 2) + 3 * Math.pow(a, 3);
        double a2 = 24 - 8 * a - 4 * Math.pow(a, 2) + 3 * Math.pow(a, 3);
        double a3 = -8 + 8 * a - 4 * Math.pow(a, 2) + Math.pow(a, 3);
        double d1 = -a1 / a0;
        double d2 = -a2 / a0;
        double d3 = -a3 / a0;
        if (highPass) {
            return new Coefficients(8 / a0, -24 / a0, 24 / a0, -8 / a0, d1, d2, d3);
        }
        double c0 = Math.pow(a, 3) / a0;
        double c1 = (3 * Math.pow(a, 3)) / a0;
        return new Coefficients(c0, c1, c1, c0, d1, d2, d3);
    }
}
