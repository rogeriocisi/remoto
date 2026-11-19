package br.remoto.core.scenario;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuração completa e imutável de uma simulação.
 *
 * <p>É o mesmo documento JSON usado pelo site, pela CLI e pelos cenários públicos versionados
 * no Git. Junto com a {@link #seed()} e a versão do motor, define o resultado de forma
 * reprodutível.
 *
 * <p>O campo {@link #engine()} escolhe o motor:
 * <ul>
 *   <li>{@value #ENGINE_PLACEHOLDER} (padrão): motor provisório, parametrizado por {@link #nuclei()};</li>
 *   <li>{@value #ENGINE_LEGACY}: modelos originais do ReMoto, parametrizados pela configuração
 *       legada em {@link #legacy()} (exportada do banco antigo, tabela por tabela).</li>
 * </ul>
 */
public record Scenario(
        String id,
        String name,
        String description,
        int schemaVersion,
        long seed,
        double durationMs,
        double stepMs,
        List<NucleusSpec> nuclei,
        String engine,
        Map<String, Object> legacy) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String ENGINE_PLACEHOLDER = "placeholder";
    public static final String ENGINE_LEGACY = "legacy";

    public Scenario {
        nuclei = nuclei == null ? List.of() : List.copyOf(nuclei);
        legacy = legacy == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(legacy));
    }

    /** Cenário do motor provisório. */
    public Scenario(String id, String name, String description, int schemaVersion, long seed,
                    double durationMs, double stepMs, List<NucleusSpec> nuclei) {
        this(id, name, description, schemaVersion, seed, durationMs, stepMs, nuclei, null, null);
    }

    public String engineOrDefault() {
        return engine == null || engine.isBlank() ? ENGINE_PLACEHOLDER : engine;
    }

    public Scenario withId(String newId) {
        return new Scenario(newId, name, description, schemaVersion, seed, durationMs, stepMs, nuclei, engine, legacy);
    }

    public Scenario withSeed(long newSeed) {
        return new Scenario(id, name, description, schemaVersion, newSeed, durationMs, stepMs, nuclei, engine, legacy);
    }

    public Scenario withDuration(double newDurationMs) {
        return new Scenario(id, name, description, schemaVersion, seed, newDurationMs, stepMs, nuclei, engine, legacy);
    }

    /** Número de passos de integração. */
    public long totalSteps() {
        return Math.round(durationMs / stepMs);
    }
}
