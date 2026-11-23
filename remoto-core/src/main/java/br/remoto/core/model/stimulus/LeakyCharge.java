package br.remoto.core.model.stimulus;

/**
 * Limiar por carga com vazamento, para o estímulo do nervo: o axônio integra a intensidade com
 * uma constante de tempo {@code tau} (a membrana perto do eletrodo, como um circuito RC) e dispara
 * quando a integral passa do limiar. Disso sai a curva intensidade-duração de Lapicque: um pulso de
 * duração {@code d} precisa de {@code I_reobase / (1 - exp(-d / tau))}, e a cronaxia é
 * {@code tau ln 2}. Pulsos curtos precisam de mais corrente; os longos, de pouco mais que a reobase.
 *
 * <p>O limiar da configuração (o do original, em corrente) continua valendo para um pulso de
 * {@link NerveStimulation#PULSE_WIDTH} ms: a integral é dividida por
 * {@code 1 - exp(-PULSE_WIDTH / tau)}, e é esse valor que o axônio compara com o seu limiar. A
 * integração é exata para a intensidade constante em cada passo; com o passo de 0,05 ms, o pulso
 * de 1 ms do original dura 21 passos, e o limiar efetivo fica cerca de 1% abaixo do nominal.
 */
public final class LeakyCharge {

    private final double decay;
    private final double normalization;
    private double charge;

    /**
     * @param tau constante de tempo intensidade-duração do axônio (ms), maior que zero
     * @param step passo de integração (ms)
     */
    public LeakyCharge(double tau, double step) {
        if (!(tau > 0)) {
            throw new IllegalArgumentException("a constante de tempo deve ser maior que zero: " + tau);
        }
        this.decay = Math.exp(-step / tau);
        this.normalization = 1 - Math.exp(-NerveStimulation.PULSE_WIDTH / tau);
    }

    /**
     * Integra a intensidade de um passo.
     *
     * @return a intensidade equivalente, a comparar com o limiar do axônio (a corrente de um pulso
     *     de 1 ms que levaria à mesma carga)
     */
    public double step(double intensity) {
        charge = intensity + (charge - intensity) * decay;
        return charge / normalization;
    }

    /**
     * O limiar teórico de um pulso retangular de {@code duration} ms, para um axônio cujo limiar
     * num pulso de 1 ms é {@code threshold1ms}.
     */
    public static double threshold(double threshold1ms, double duration, double tau) {
        return threshold1ms * (1 - Math.exp(-NerveStimulation.PULSE_WIDTH / tau)) / (1 - Math.exp(-duration / tau));
    }
}
