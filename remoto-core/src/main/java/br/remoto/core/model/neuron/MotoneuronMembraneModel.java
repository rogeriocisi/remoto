package br.remoto.core.model.neuron;

import java.util.function.DoubleUnaryOperator;

/**
 * A membrana de um motoneurônio: dois compartimentos acoplados, a corrente persistente de entrada
 * no dendrito e a corrente injetada em cada compartimento. O que {@link Membrane} define vale
 * aqui também.
 *
 * <p>O contrato é de dinâmica, não de formulação: o dendrito e o soma avançam um passo, o soma
 * informa se cruzou o limiar, e a PIC é consultada depois. Como nada aqui pressupõe canais de
 * pulso, uma membrana de cinética contínua implementa esta interface sem herdar de
 * {@link SpinalMembrane}; basta entrar na cláusula {@code permits}.
 *
 * <p>Fora daqui ficam os detalhes da formulação atual, como o canal de pulso do cálcio
 * ({@link MotoneuronMembrane#ca()}), que só quem conhece a implementação concreta usa.
 */
public sealed interface MotoneuronMembraneModel extends Membrane permits MotoneuronMembrane {

    /** Integra o dendrito por um passo, a partir de {@code t}. */
    void stepDendrite(double t);

    /**
     * Integra o soma por um passo, a partir de {@code t}.
     *
     * @return se o soma cruzou o limiar fora do período refratário; quem chama registra o disparo
     *         e chama {@link #fireSoma(double)}
     */
    boolean stepSoma(double t);

    /** Depois do soma: se as condições do modelo mandarem, inicia a PIC no dendrito. */
    void checkPersistentInwardCurrent(double t);

    /** Inicia a PIC no dendrito no instante {@code t}. */
    void startPersistentInwardCurrent(double t);

    /** Corrente injetada no soma e no dendrito; {@code null} = nenhuma. */
    void setInjection(DoubleUnaryOperator soma, DoubleUnaryOperator dendrite);

    /** Potencial do dendrito, medido a partir do repouso (mV). */
    double vd();

    void setVd(double vd);

    /** Capacitância do dendrito (nF). */
    double capacitanceDend();

    void setCapacitanceDend(double c);

    /** Condutância de fuga do dendrito (uS). */
    double gLeakDend();

    void setGLeakDend(double g);

    /** Condutância de acoplamento entre soma e dendrito (uS). */
    double gCoupling();

    void setGCoupling(double g);

    /** Limiar do soma que dispara a PIC (mV acima do repouso). */
    double thresholdCa();

    void setThresholdCa(double t);

    /** Instante do último início da PIC (ms), ou o "nunca" do modelo. */
    double tDendritePIC();

    void setTDendritePIC(double t);
}
