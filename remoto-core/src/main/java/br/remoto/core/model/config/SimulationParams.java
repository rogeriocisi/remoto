package br.remoto.core.model.config;

/** Linha de CONFIGURATION: parâmetros gerais da simulação. */
public record SimulationParams(
        int id,
        String user,
        String name,
        String description,
        double tFin,
        double step,
        boolean merge,
        boolean keepProperties,
        boolean storeResults,
        boolean storeSignals) {
}
