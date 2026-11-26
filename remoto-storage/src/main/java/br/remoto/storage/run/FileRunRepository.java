package br.remoto.storage.run;

import br.remoto.core.scenario.Scenario;
import br.remoto.storage.DataLayout;
import br.remoto.storage.Hashing;
import br.remoto.storage.Ids;
import br.remoto.storage.JsonCodec;
import br.remoto.storage.StorageFiles;
import br.remoto.storage.StorageGuard;
import br.remoto.storage.UserId;
import br.remoto.storage.UserLocks;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Um diretório por execução: {@code runs/<runId>/{manifest.json, config.json, results/}}. */
public final class FileRunRepository implements RunRepository {

    private static final String MANIFEST = "manifest.json";
    private static final String CONFIG = "config.json";
    private static final String RESULTS = "results";
    private static final String RESULT_SUFFIX = ".csv.gz";
    /** Nome de um resultado: o que o motor grava (letras, números, hífen, sublinhado e ponto). */
    private static final java.util.regex.Pattern RESULT_NAME =
            java.util.regex.Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$");
    /** Limites da importação: arquivos dentro do .zip e bytes descompactados no total. */
    static final int MAX_IMPORT_ENTRIES = 5_000;
    static final long MAX_IMPORT_BYTES = 2L * 1024 * 1024 * 1024;
    private static final int MAX_JSON_BYTES = 16 * 1024 * 1024;

    private final DataLayout layout;
    private final JsonCodec json;
    private final UserLocks locks;
    private final StorageGuard guard;

    public FileRunRepository(DataLayout layout, JsonCodec json, UserLocks locks) {
        this(layout, json, locks, StorageGuard.NONE);
    }

    public FileRunRepository(DataLayout layout, JsonCodec json, UserLocks locks, StorageGuard guard) {
        this.layout = layout;
        this.json = json;
        this.locks = locks;
        this.guard = guard;
    }

    @Override
    public RunManifest create(UserId owner, Scenario scenario, String engineVersion) {
        guard.requireRoom(owner);
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
        return StorageFiles.subdirectories(layout.sessionsDir()).stream()
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

    /** Vazio se a execução não existe, ou deixou de existir enquanto era lida (apagada). */
    private Optional<RunManifest> readManifest(Path runDir) {
        Path file = runDir.resolve(MANIFEST);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(json.read(file, RunManifest.class));
        } catch (UncheckedIOException e) {
            if (e.getCause() instanceof java.nio.file.NoSuchFileException) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public void exportZip(UserId owner, String runId, OutputStream out) {
        if (!Ids.isSafe(runId)) {
            return;
        }
        Path dir = layout.runDir(owner, runId);
        if (!Files.isRegularFile(dir.resolve(MANIFEST))) {
            return;
        }
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String name : List.of(MANIFEST, CONFIG)) {
                Path file = dir.resolve(name);
                if (Files.isRegularFile(file)) {
                    zip.putNextEntry(new ZipEntry(name));
                    Files.copy(file, zip);
                    zip.closeEntry();
                }
            }
            for (Path file : StorageFiles.filesEndingWith(dir.resolve(RESULTS), RESULT_SUFFIX)) {
                String name = file.getFileName().toString();
                zip.putNextEntry(new ZipEntry(RESULTS + "/" + name.substring(0, name.length() - ".gz".length())));
                try (InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
                    in.transferTo(zip);
                }
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public RunManifest importZip(UserId owner, InputStream zip) {
        guard.requireRoom(owner);
        String runId = Ids.newId();
        Path dir = layout.runDir(owner, runId);
        byte[] manifestBytes = null;
        byte[] config = null;
        int results = 0;
        try {
            Files.createDirectories(dir.resolve(RESULTS));
            long total = 0;
            int entries = 0;
            try (ZipInputStream in = new ZipInputStream(zip)) {
                for (ZipEntry entry; (entry = in.getNextEntry()) != null; ) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    if (++entries > MAX_IMPORT_ENTRIES) {
                        throw new IllegalArgumentException("arquivos demais no .zip (máximo " + MAX_IMPORT_ENTRIES + ")");
                    }
                    String name = entry.getName();
                    if (name.equals(MANIFEST)) {
                        manifestBytes = readLimited(in, MAX_JSON_BYTES);
                        total += manifestBytes.length;
                    } else if (name.equals(CONFIG)) {
                        config = readLimited(in, MAX_JSON_BYTES);
                        total += config.length;
                    } else if (name.startsWith(RESULTS + "/") && name.endsWith(".csv")) {
                        String result = name.substring(RESULTS.length() + 1, name.length() - ".csv".length());
                        if (!RESULT_NAME.matcher(result).matches() || result.contains("..")) {
                            throw new IllegalArgumentException("nome de resultado inválido: " + name);
                        }
                        try (OutputStream out = new GZIPOutputStream(
                                Files.newOutputStream(dir.resolve(RESULTS).resolve(result + RESULT_SUFFIX)))) {
                            total += copyLimited(in, out, MAX_IMPORT_BYTES - total);
                        }
                        results++;
                    }
                    if (total > MAX_IMPORT_BYTES) {
                        throw new IllegalArgumentException("o .zip descompactado passa de 2 GB");
                    }
                }
            }
            if (manifestBytes == null || config == null) {
                throw new IllegalArgumentException("não é uma simulação exportada: faltam manifest.json ou config.json");
            }
            RunManifest original;
            try {
                original = json.fromBytes(manifestBytes, RunManifest.class);
                json.fromBytes(config, Scenario.class);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("manifest.json ou config.json ilegível");
            }
            if (original.status() == null || !original.status().isTerminal()) {
                throw new IllegalArgumentException("só simulações terminadas podem ser importadas");
            }
            if (!Hashing.sha256Hex(config).equals(original.scenarioSha256())) {
                throw new IllegalArgumentException("config.json não confere com o manifesto");
            }
            if (results == 0 && original.status() == RunStatus.DONE) {
                throw new IllegalArgumentException("a simulação não tem resultados");
            }
            RunManifest imported = new RunManifest(runId, owner.value(), original.scenarioId(),
                    original.scenarioName(), original.scenarioSha256(), original.seed(), original.engineVersion(),
                    original.status(), original.createdAt(), original.startedAt(), original.finishedAt(),
                    original.error(), false);
            byte[] configBytes = config;
            return locks.withLock(owner, () -> {
                StorageFiles.writeAtomically(dir.resolve(CONFIG), configBytes);
                json.writeAtomically(dir.resolve(MANIFEST), imported); // por último: sem ele, a execução não aparece
                return imported;
            });
        } catch (IOException e) {
            StorageFiles.deleteRecursively(dir);
            throw new IllegalArgumentException("não foi possível ler o .zip: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            StorageFiles.deleteRecursively(dir);
            throw e;
        }
    }

    private static byte[] readLimited(InputStream in, int max) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        copyLimited(in, out, max);
        return out.toByteArray();
    }

    private static long copyLimited(InputStream in, OutputStream out, long max) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long copied = 0;
        for (int n; (n = in.read(buffer)) > 0; ) {
            copied += n;
            if (copied > max) {
                throw new IllegalArgumentException("arquivo grande demais dentro do .zip");
            }
            out.write(buffer, 0, n);
        }
        return copied;
    }
}
