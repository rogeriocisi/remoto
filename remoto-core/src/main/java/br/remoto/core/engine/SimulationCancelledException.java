package br.remoto.core.engine;

/** Lançada pelo motor quando a execução é cancelada pelo usuário ou pelo servidor. */
public class SimulationCancelledException extends RuntimeException {

    public SimulationCancelledException() {
        super("simulação cancelada");
    }
}
