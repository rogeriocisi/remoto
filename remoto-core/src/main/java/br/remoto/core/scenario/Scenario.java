package br.remoto.core.scenario;

import java.util.List;

/**
 * Configuração completa e imutável de uma simulação.
 *
 * <p>É o mesmo documento JSON usado pelo site, pela CLI e pelos cenários públicos versionados
 * no Git. Junto com a {@link #seed()} e a versão do motor, define o resultado de forma
 * reprodutível.
 */
public record Scenario(
        String id,
        String name,
        String description,
        int schemaVersion,
        long seed,
        double durationMs,
        double stepMs,
        List<NucleusSpec> nuclei) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public Scenario {
        nuclei = nuclei == null ? List.of() : List.copyOf(nuclei);
    }

    public Scenario withId(String newId) {
        return new Scenario(newId, name, description, schemaVersion, seed, durationMs, stepMs, nuclei);
    }

    public Scenario withSeed(long newSeed) {
        return new Scenario(id, name, description, schemaVersion, newSeed, durationMs, stepMs, nuclei);
    }

    /** Número de passos de integração. */
    public long totalSteps() {
        return Math.round(durationMs / stepMs);
    }
}
