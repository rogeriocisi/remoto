package br.remoto.core.model.muscle;

/**
 * O abalo de uma unidade motora: a resposta a cada disparo é uma função alfa,
 * (t/T)·e^(1 − t/T), com pico em T. Calculada como um filtro IIR de segunda ordem, invariante ao
 * impulso, cuja entrada é um impulso de área 1 por disparo.
 *
 * <p>Usado pelo músculo SOCDS (o abalo) e pelo Hill (a ativação). As contas são as do
 * {@code AlphaFunction} do ReMoto original, operação por operação.
 */
public final class AlphaTwitch {

    private static final int N = 2;

    private final int[] it = new int[N + 1];
    private final double[] x = new double[N + 1];
    private final double[] y = new double[N + 1];
    private double a1;
    private double a2;
    private double b1;
    private double step;
    private boolean started;

    /**
     * Volta ao repouso.
     *
     * @param step  passo de integração (ms)
     * @param tpeak instante do pico depois do disparo (ms)
     */
    public void reset(double step, double tpeak) {
        this.step = step;
        started = false;
        a1 = 2 * Math.exp(-step / tpeak);
        a2 = -Math.exp(-(2 * step) / tpeak);
        b1 = step * (1 * step / tpeak) * Math.exp(1 - (step / tpeak));
        for (int i = 0; i <= N; i++) {
            it[i] = 0;
            x[i] = 0;
            y[i] = 0;
        }
    }

    /** Se já houve algum disparo desde o último {@link #reset}. */
    public boolean isStarted() {
        return started;
    }

    /** Um disparo no passo {@code iteration}: o impulso entra na saída a partir do passo seguinte. */
    public void start(int iteration) {
        if (it[N] != iteration) {
            iterate(iteration);
        }
        started = true;
        x[N] = 1.0 / step;
        y[N] = a1 * y[N - 1] + a2 * y[N - 2] + b1 * x[N - 1];
    }

    /** O valor no passo {@code iteration} (0 antes do primeiro disparo). */
    public double value(int iteration) {
        if (it[N] != iteration) {
            iterate(iteration);
            if (!started) {
                return 0;
            }
            y[N] = a1 * y[N - 1] + a2 * y[N - 2] + b1 * x[N - 1];
        }
        return y[N];
    }

    private void iterate(int iteration) {
        for (int i = 0; i < N; i++) {
            it[i] = it[i + 1];
            x[i] = x[i + 1];
            y[i] = y[i + 1];
        }
        it[N] = iteration;
        x[N] = 0.0;
    }
}
