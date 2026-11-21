package br.remoto.core.model.config;

/** Linha de DESCENDING_COMMANDS: o sinal que modula um trato descendente. */
public record DescendingCommandParams(
        String nucleus,
        String neuronType,
        String signal,
        String modType,
        double tIni,
        double tFin,
        double amp,
        double freq,
        double width,
        double delay) {
}
