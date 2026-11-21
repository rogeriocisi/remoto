package br.remoto.core.model.config;

/** Linha de INJECTED_CURRENT: a corrente injetada num tipo de neurônio. */
public record InjectedCurrentParams(
        String nucleus,
        String neuronType,
        String compartment,
        double imax,
        String signal,
        double tIni,
        double tFin,
        double amp,
        double freq,
        double width,
        boolean active,
        int index,
        double delay) {
}
