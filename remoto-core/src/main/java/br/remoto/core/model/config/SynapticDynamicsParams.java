package br.remoto.core.model.config;

/** Linha de SYNAPTIC_DYNAMICS: a depressão ou facilitação de uma sinapse. */
public record SynapticDynamicsParams(
        String nucleus,
        String nucleusPre,
        String conductanceType,
        String dynamicType,
        double tau,
        double variation) {
}
