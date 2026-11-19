package br.remoto.core.engine;

/** O que o motor enxerga do mundo externo durante uma execução. */
public interface RunContext {

    /** Fração concluída, de 0 a 1. */
    void progress(double fraction);

    boolean isCancelled();

    ResultSink results();

    default void checkCancelled() {
        if (isCancelled()) {
            throw new SimulationCancelledException();
        }
    }
}
