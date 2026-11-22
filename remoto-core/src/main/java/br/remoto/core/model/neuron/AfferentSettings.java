package br.remoto.core.model.neuron;

/** Parâmetros da simulação que as fibras aferentes leem a cada passo. */
public interface AfferentSettings {

    /** Passo de integração (ms). */
    double step();

    /** Período refratário do axônio das fibras aferentes (ms). */
    double afferentAxonRefractoryPeriod();
}
