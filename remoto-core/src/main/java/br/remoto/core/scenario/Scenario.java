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

    /**
     * Número de passos de integração que a simulação vai de fato rodar.
     *
     * <p>Trunca, e não arredonda, porque é o que {@code Simulation.run} faz
     * ({@code totSteps = (int)(tFin/step)}). Com {@code Math.round} os dois discordavam quando a
     * duração não é múltipla exata do passo — 8,6 ms com passo de 0,05 ms dá 171,999… , que
     * arredonda para 172 e trunca para 171 —, e o validador contava um passo que o motor nunca
     * roda. Nenhum dos cenários públicos cai nesse caso, mas {@code --duration-ms} aceita
     * qualquer valor.
     *
     * <p>A consequência de truncar, que continua valendo, é que uma duração não múltipla do passo
     * simula um pedaço a menos do que foi pedido: 8,6 ms viram 8,55 ms.
     */
    public long totalSteps() {
        return (long) (durationMs / stepMs);
    }
}
