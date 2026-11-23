package br.remoto.core.model.receptor;

/**
 * Órgão tendinoso de Golgi agrupado (Crago et al., 1982): a força do tendão passa por uma
 * não linearidade estática {@code 60·ln(F/4 + 1)} Hz e por um filtro linear de 2ª ordem
 * (discretizado por Tustin no passo da simulação); a taxa resultante é dividida entre as fibras Ib
 * do núcleo.
 *
 * <p>Como no original, a primeira chamada (iteração 0) devolve 0 sem levar a força em conta, e,
 * sem fibras Ib, a divisão dá NaN e depois infinito; nenhuma fibra lê esse valor, e os resultados
 * não o mostram.
 */
public final class CragoTendonOrgan {

    private static final int N = 2;

    /** Ganho da não linearidade (Hz). */
    private static final double GR = 60;

    /** Força de referência da não linearidade (N). */
    private static final double FN = 4;

    private final int[] it = new int[N + 1];
    private final double[] x = new double[N + 1];
    private final double[] y = new double[N + 1];

    private final double a1;
    private final double a2;
    private final double b0;
    private final double b1;
    private final double b2;

    private final double afferentNumber;

    private int iteration;
    private double ibFiringRate;

    /**
     * @param step passo da simulação (ms)
     * @param afferentNumber fibras Ib ativas do núcleo, entre as quais a taxa se divide
     */
    public CragoTendonOrgan(double step, double afferentNumber) {
        double den = 0.4 * Math.pow(step, 2) + 4.4 * step + 4;
        a1 = (0.8 * Math.pow(step, 2) - 8) / den;
        a2 = (0.4 * Math.pow(step, 2) - 4.4 * step + 4) / den;
        b0 = (0.4 * Math.pow(step, 2) + 5.16 * step + 6.8) / den;
        b1 = (0.8 * Math.pow(step, 2) - 13.6) / den;
        b2 = (0.4 * Math.pow(step, 2) - 5.16 * step + 6.8) / den;
        this.afferentNumber = afferentNumber;
    }

    /** Calcula a taxa de cada fibra Ib a partir da força do tendão (N) neste passo. */
    public void step(double force) {
        ibFiringRate = value(iteration++, force) / afferentNumber;
    }

    private double value(int iteration, double force) {
        if (it[N] != iteration) {
            for (int i = 0; i < N; i++) {
                it[i] = it[i + 1];
                x[i] = x[i + 1];
                y[i] = y[i + 1];
            }
            it[N] = iteration;
            x[N] = 40 * (GR * Math.log(force / FN + 1));

            y[N] = -a1 * y[N - 1] - a2 * y[N - 2] + b0 * x[N] + b1 * x[N - 1] + b2 * x[N - 2];
        }
        return y[N];
    }

    /** Taxa de disparo (Hz) de cada fibra Ib no último passo. */
    public double ibFiringRate() {
        return ibFiringRate;
    }
}
