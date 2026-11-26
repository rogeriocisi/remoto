package br.remoto.storage.scenario;

import br.remoto.core.scenario.Scenario;
import br.remoto.storage.DataLayout;
import br.remoto.storage.Hashing;
import br.remoto.storage.Ids;
import br.remoto.storage.JsonCodec;
import br.remoto.storage.StaleWriteException;
import br.remoto.storage.StorageFiles;
import br.remoto.storage.StorageGuard;
import br.remoto.storage.UserId;
import br.remoto.storage.UserLocks;
import br.remoto.storage.Versioned;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/** Um arquivo JSON por cenário em {@code users/<id>/scenarios/}. */
public final class FileScenarioRepository implements ScenarioRepository {

    private final DataLayout layout;
    private final JsonCodec json;
    private final UserLocks locks;
    private final StorageGuard guard;

    public FileScenarioRepository(DataLayout layout, JsonCodec json, UserLocks locks) {
        this(layout, json, locks, StorageGuard.NONE);
    }

    public FileScenarioRepository(DataLayout layout, JsonCodec json, UserLocks locks, StorageGuard guard) {
        this.layout = layout;
        this.json = json;
        this.locks = locks;
        this.guard = guard;
    }

    @Override
    public List<Scenario> list(UserId owner) {
        return StorageFiles.filesEndingWith(layout.scenariosDir(owner), ".json").stream()
                .map(p -> json.read(p, Scenario.class))
                .sorted(Comparator.comparing(Scenario::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public Optional<Versioned<Scenario>> find(UserId owner, String id) {
        if (!Ids.isSafe(id)) {
            return Optional.empty();
        }
        Path file = file(owner, id);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        byte[] bytes = StorageFiles.readAllBytes(file);
        return Optional.of(new Versioned<>(json.fromBytes(bytes, Scenario.class), Hashing.sha256Hex(bytes)));
    }

    @Override
    public Versioned<Scenario> create(UserId owner, Scenario scenario) {
        guard.requireRoom(owner);
        Scenario stored = scenario.withId(Ids.newId());
        return locks.withLock(owner, () -> write(owner, stored));
    }

    @Override
    public Versioned<Scenario> update(UserId owner, Scenario scenario, String expectedEtag) {
        guard.requireRoom(owner);
        Ids.requireSafe(scenario.id());
        return locks.withLock(owner, () -> {
            Versioned<Scenario> current = find(owner, scenario.id())
                    .orElseThrow(() -> new NoSuchElementException("cenário não encontrado"));
            if (expectedEtag != null && !expectedEtag.equals(current.etag())) {
                throw new StaleWriteException();
            }
            return write(owner, scenario);
        });
    }

    @Override
    public boolean delete(UserId owner, String id) {
        if (!Ids.isSafe(id)) {
            return false;
        }
        return locks.withLock(owner, () -> {
            try {
                return Files.deleteIfExists(file(owner, id));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private Versioned<Scenario> write(UserId owner, Scenario scenario) {
        byte[] bytes = json.toBytes(scenario);
        StorageFiles.writeAtomically(file(owner, scenario.id()), bytes);
        return new Versioned<>(scenario, Hashing.sha256Hex(bytes));
    }

    private Path file(UserId owner, String id) {
        return layout.scenariosDir(owner).resolve(Ids.requireSafe(id) + ".json");
    }
}
