package br.remoto.storage.run;

import java.time.Instant;

/**
 * Metadados de uma execução, gravados em {@code runs/<runId>/manifest.json}. Com a cópia da
 * configuração ({@code config.json}), a seed e a versão do motor, a execução é reprodutível.
 */
public record RunManifest(
        String runId,
        String ownerId,
        String scenarioId,
        String scenarioName,
        String scenarioSha256,
        long seed,
        String engineVersion,
        RunStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        String error,
        boolean pinned) {

    public RunManifest running(Instant now) {
        return new RunManifest(runId, ownerId, scenarioId, scenarioName, scenarioSha256, seed, engineVersion,
                RunStatus.RUNNING, createdAt, now, null, null, pinned);
    }

    public RunManifest finished(RunStatus newStatus, Instant now, String newError) {
        if (!newStatus.isTerminal()) {
            throw new IllegalArgumentException("status final esperado: " + newStatus);
        }
        return new RunManifest(runId, ownerId, scenarioId, scenarioName, scenarioSha256, seed, engineVersion,
                newStatus, createdAt, startedAt, now, newError, pinned);
    }

    public RunManifest withPinned(boolean newPinned) {
        return new RunManifest(runId, ownerId, scenarioId, scenarioName, scenarioSha256, seed, engineVersion,
                status, createdAt, startedAt, finishedAt, error, newPinned);
    }
}
