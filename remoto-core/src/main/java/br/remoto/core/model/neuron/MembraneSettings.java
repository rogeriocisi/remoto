package br.remoto.core.model.neuron;

/**
 * Parâmetros da simulação que a membrana lê a cada passo, como o código original lia do objeto
 * de parâmetros avulsos: o passo de integração e as frações dele usadas no Runge-Kutta, o ganho da
 * corrente persistente de cálcio e o período refratário do soma dos motoneurônios.
 */
public interface MembraneSettings {

    /** Passo de integração h (ms). */
    double step();

    /** h/2 (ms). */
    double stepByTwo();

    /** h/6 (ms). */
    double stepBySix();

    /** Ganho γ da corrente persistente de cálcio do dendrito; 0 desliga a PIC. */
    double gammaCa();

    /** Período refratário do soma dos motoneurônios (ms). */
    double motoneuronSomaRefractoryPeriod();
}
