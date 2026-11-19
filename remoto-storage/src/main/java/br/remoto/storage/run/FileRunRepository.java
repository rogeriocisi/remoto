package br.remoto.storage.run;

import br.remoto.core.scenario.Scenario;
import br.remoto.storage.DataLayout;
import br.remoto.storage.Hashing;
import br.remoto.storage.Ids;
import br.remoto.storage.JsonCodec;
import br.remoto.storage.StorageFiles;
import br.remoto.storage.UserId;
import br.remoto.storage.UserLocks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/** Um diretório por execução: {@code runs/<runId>/{manifest.json, config.json, results/}}. */
public final class FileRunRepository implements RunRepository {

    private static final String MANIFEST = "manifest.json";
    private static final String CONFIG = "config.json";
    private static final String RESULTS = "results";
    private static final String RESULT_SUFFIX = ".csv.gz";

    private final DataLayout layout;
    private final JsonCodec json;
    private final UserLocks locks;

    public FileRunRepository(DataLayout layout, JsonCodec json, UserLocks locks) {
        this.layout = layout;
        this.json = json;
        this.locks = locks;
    }

    @Override
    public RunManifest create(UserId owner, Scenario scenario, String engineVersion) {
        String runId = Ids.newId();
        byte[] config = json.toBytes(scenario);
        RunManifest manifest = new RunManifest(runId, owner.value(), scenario.id(), scenario.name(),
                Hashing.sha256Hex(config), scenario.seed(), engineVersion, RunStatus.QUEUED,
                Instant.now(), null, null, null, false);
        return locks.withLock(owner, () -> {
            Path dir = layout.runDir(owner, runId);
            try {
                Files.createDirectories(dir.resolve(RESULTS));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            StorageFiles.writeAtomically(dir.resolve(CONFIG), config);
            json.writeAtomically(dir.resolve(MANIFEST), manifest);
            return manifest;
        });
    }

    @Override
    public void save(UserId owner, RunManifest manifest) {
        locks.withLock(owner, () -> {
            Path dir = layout.runDir(owner, manifest.runId());
            if (Files.isDirectory(dir)) {
                json.writeAtomically(dir.resolve(MANIFEST), manifest);
            }
            return null;
        });
    }

    @Override
    public Optional<RunManifest> find(UserId owner, String runId) {
        if (!Ids.isSafe(runId)) {
            return Optional.empty();
        }
        return readManifest(layout.runDir(owner, runId));
    }

    @Override
    public List<RunManifest> list(UserId owner) {
        return StorageFiles.subdirectories(layout.runsDir(owner)).stream()
                .map(this::readManifest)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(RunManifest::createdAt).reversed())
                .toList();
    }

    @Override
    public Optional<Scenario> config(UserId owner, String runId) {
        if (!Ids.isSafe(runId)) {
            return Optional.empty();
        }
        Path file = layout.runDir(owner, runId).resolve(CONFIG);
        return Files.isRegularFile(file) ? Optional.of(json.read(file, Scenario.class)) : Optional.empty();
    }

    @Override
    public Path resultsDir(UserId owner, String runId) {
        return layout.runDir(owner, runId).resolve(RESULTS);
    }

    @Override
    public List<String> resultNames(UserId owner, String runId) {
        if (!Ids.isSafe(runId)) {
            return List.of();
        }
        return StorageFiles.filesEndingWith(resultsDir(owner, runId), RESULT_SUFFIX).stream()
                .map(p -> p.getFileName().toString())
                .map(n -> n.substring(0, n.length() - RESULT_SUFFIX.length()))
                .toList();
    }

    @Override
    public List<RunManifest> findAllByStatus(Set<RunStatus> statuses) {
        return Stream.of(layout.usersDir(), layout.guestsDir())
                .flatMap(base -> StorageFiles.subdirectories(base).stream())
                .flatMap(userDir -> StorageFiles.subdirectories(userDir.resolve("runs")).stream())
                .map(this::readManifest)
                .flatMap(Optional::stream)
                .filter(m -> statuses.contains(m.status()))
                .toList();
    }

    @Override
    public boolean delete(UserId owner, String runId) {
        if (!Ids.isSafe(runId)) {
            return false;
        }
        return locks.withLock(owner, () -> {
            Path dir = layout.runDir(owner, runId);
            if (!Files.isDirectory(dir)) {
                return false;
            }
            StorageFiles.deleteRecursively(dir);
            return true;
        });
    }

    private Optional<RunManifest> readManifest(Path runDir) {
        Path file = runDir.resolve(MANIFEST);
        return Files.isRegularFile(file) ? Optional.of(json.read(file, RunManifest.class)) : Optional.empty();
    }
}
