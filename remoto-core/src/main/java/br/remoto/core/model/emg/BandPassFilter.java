package br.remoto.core.model.emg;

/**
 * O passa-faixa do EMG da tela de resultados do ReMoto original (filtro "1st2nd"): um Butterworth
 * passa-baixas de 2ª ordem em paralelo com uma seção de 1ª ordem, discretizados por invariância ao
 * impulso. As contas são as do {@code ButterworthImpulseInvariance} original, com as mesmas
 * expressões; só os vetores do tamanho do sinal viraram as duas amostras anteriores.
 */
public final class BandPassFilter {

    private final double a11;
    private final double a21;
    private final double b01;
    private final double b11;
    private final double a12;
    private final double b02;

    private double y1Last;
    private double y1BeforeLast;
    private double y2Last;
    private double xLast;

    /**
     * @param fc1 frequência de corte inferior (Hz)
     * @param fc2 frequência de corte superior (Hz)
     * @param T   período de amostragem (s)
     */
    public BandPassFilter(double fc1, double fc2, double T) {
        double w1 = 2 * Math.PI * fc1;
        double w2 = 2 * Math.PI * fc2;

        double alfa = -(w1 * Math.pow(w2, 2)) / (Math.sqrt(2) * w2 * w1 - Math.pow(w1, 2) - Math.pow(w2, 2));
        double beta = -Math.pow(w2, 4) / (Math.sqrt(2) * w2 * w1 - Math.pow(w1, 2) - Math.pow(w2, 2));
        double gama = (w1 * Math.pow(w2, 2)) / (Math.sqrt(2) * w2 * w1 - Math.pow(w1, 2) - Math.pow(w2, 2));
        double w0 = w2 / Math.sqrt(2);
        double A = (Math.sqrt(2) * w2) / 2;
        double B = (beta * Math.sqrt(2)) / (alfa * w2) - 1;

        a11 = -2 * Math.exp(-A * T) * Math.cos(w0 * T);
        a21 = Math.exp(-2 * A * T);
        b01 = T * alfa;
        b11 = T * alfa * Math.exp(-A * T) * (-Math.cos(w0 * T) + B * Math.sin(w0 * T));
        a12 = -Math.exp(-w1 * T);
        b02 = T * gama;
    }

    /** A próxima amostra filtrada. */
    public double next(double x) {
        double y1 = -a11 * y1Last - a21 * y1BeforeLast + b01 * x + b11 * xLast;
        double y2 = -a12 * y2Last + b02 * x;
        y1BeforeLast = y1Last;
        y1Last = y1;
        y2Last = y2;
        xLast = x;
        return y1 + y2;
    }
}
