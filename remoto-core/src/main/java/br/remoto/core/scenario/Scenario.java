package br.remoto.core.scenario;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuração completa e imutável de uma simulação.
 *
 * <p>É o mesmo documento JSON usado pelo site, pela CLI e pelos cenários públicos versionados
 * no Git. Junto com a {@link #seed()} e a versão do motor, define o resultado de forma
 * reprodutível.
 *
 * <p>O campo {@link #engine()} escolhe o motor. Hoje há um, {@value #ENGINE_LEGACY} (o padrão):
 * os modelos do ReMoto, parametrizados pela configuração em {@link #legacy()} (as tabelas do
 * banco original). O campo fica para motores futuros que leiam o mesmo cenário.
 */
public record Scenario(
        String id,
        String name,
        String description,
        int schemaVersion,
        long seed,
        double durationMs,
        double stepMs,
        String engine,
        Map<String, Object> legacy) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String ENGINE_LEGACY = "legacy";

    public Scenario {
        legacy = legacy == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(legacy));
    }

    public String engineOrDefault() {
        return engine == null || engine.isBlank() ? ENGINE_LEGACY : engine;
    }

    public Scenario withId(String newId) {
        return new Scenario(newId, name, description, schemaVersion, seed, durationMs, stepMs, engine, legacy);
    }

    public Scenario withSeed(long newSeed) {
        return new Scenario(id, name, description, schemaVersion, newSeed, durationMs, stepMs, engine, legacy);
    }

    public Scenario withDuration(double newDurationMs) {
        return new Scenario(id, name, description, schemaVersion, seed, newDurationMs, stepMs, engine, legacy);
    }

    /** Número de passos de integração. */
    public long totalSteps() {
        return Math.round(durationMs / stepMs);
    }
}
