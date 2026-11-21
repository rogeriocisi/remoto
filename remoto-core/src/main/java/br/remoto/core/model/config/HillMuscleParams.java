package br.remoto.core.model.config;

/** Linha de HILL_TYPE_MUSCULOTENDON_PARAMETERS: os parâmetros de um músculo tipo Hill. */
public record HillMuscleParams(
        String muscle,
        double optimalLength,
        double viscosityCoefficient,
        double elasticCoefParallel,
        double cT,
        double kT,
        double lrT,
        double pennationAngle,
        double slackTendonLength,
        double maximumMuscleForce,
        double muscleMass,
        int index) {
}
