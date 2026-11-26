package br.remoto.core.model.neuron;

import br.remoto.core.model.synapse.SynapseGroup;

/**
 * O que a simulação pede de qualquer membrana, independente de como os canais iônicos são
 * modelados: o potencial do soma, o limiar, o instante do último disparo, as capacitâncias e
 * condutâncias de fuga do soma, e os quatro grupos de sinapses.
 *
 * <p>Deliberadamente fora daqui: os canais de pulso ({@code na}, {@code kf}, {@code ks}), que são
 * da formulação de {@link SpinalMembrane} e não de toda membrana, e tudo que pressupõe dois
 * compartimentos, que fica em {@link MotoneuronMembraneModel}. É essa separação que permite uma
 * segunda família de membrana — com cinética contínua, por exemplo — sem herdar os campos da
 * primeira.
 *
 * <p>Interface selada: uma família nova entra aqui ou em {@link MotoneuronMembraneModel}, de modo
 * que o compilador aponte todo lugar que precise tratá-la.
 */
public sealed interface Membrane permits SpinalMembrane, MotoneuronMembraneModel {

    /** Volta ao repouso: potencial em 0, nenhum disparo, canais fechados. */
    void reset();

    /** Um disparo do soma no instante {@code t}. */
    void fireSoma(double t);

    /** Potencial do soma, medido a partir do repouso (mV). */
    double vs();

    void setVs(double vs);

    /** Capacitância do soma (nF). */
    double capacitanceSoma();

    void setCapacitanceSoma(double c);

    /** Condutância de fuga do soma (uS). */
    double gLeakSoma();

    void setGLeakSoma(double g);

    /** Limiar de disparo do soma (mV acima do repouso). */
    double threshold();

    void setThreshold(double threshold);

    /** Instante do último disparo do soma (ms), ou o "nunca" do modelo. */
    double tSomaSpike();

    void setTSomaSpike(double t);

    SynapseGroup dendExcit();

    void setDendExcit(SynapseGroup g);

    SynapseGroup dendInhib();

    void setDendInhib(SynapseGroup g);

    SynapseGroup somaExcit();

    void setSomaExcit(SynapseGroup g);

    SynapseGroup somaInhib();

    void setSomaInhib(SynapseGroup g);
}
