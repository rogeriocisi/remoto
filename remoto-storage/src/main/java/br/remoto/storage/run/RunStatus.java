package br.remoto.storage.run;

/** Ciclo de vida de uma execução (substitui as constantes {@code Simulation.SIM_*} do legado). */
public enum RunStatus {
    QUEUED,
    RUNNING,
    DONE,
    FAILED,
    CANCELED;

    public boolean isTerminal() {
        return this == DONE || this == FAILED || this == CANCELED;
    }
}
