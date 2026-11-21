package br.remoto.core.model.config;

/** Linha de NERVE: um nervo e a estimulação elétrica dele. */
public record NerveParams(
        String cd,
        String joint,
        double stimulusSpinalEntry,
        double stimulusEndPlate,
        boolean active,
        int index,
        double start,
        double stop,
        double peak,
        double frequency,
        String signal,
        double tIni,
        double tFin,
        double amp,
        double freq,
        double width,
        double delay) {
}
