package br.remoto.core.model.config;

/** Linha de CONDUCTANCE_TYPE: um tipo de sinapse entre dois núcleos. */
public record SynapseParams(
        String conductanceType,
        String nucleus,
        String nucleusPre,
        double gmax,
        double e,
        double connectivity,
        double delay,
        String compartment,
        boolean active,
        int index) {
}
