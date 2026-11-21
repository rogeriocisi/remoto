package br.remoto.core.model.synapse;

/**
 * Depressão ou facilitação de uma sinapse (Capek e Esplin, 1977; Kohn et al., 1995): a cada
 * disparo a condutância máxima cai (ou sobe) {@code variation} por cento e volta ao valor de
 * repouso com constante de tempo {@code tau} (ms).
 */
public record SynapticDynamics(Kind kind, double tau, double variation) {

    public enum Kind { NONE, DEPRESSING, FACILITATING }

    /** Sem dinâmica: todo disparo tem a mesma condutância máxima. */
    public static final SynapticDynamics NONE = new SynapticDynamics(Kind.NONE, 0, 0);
}
