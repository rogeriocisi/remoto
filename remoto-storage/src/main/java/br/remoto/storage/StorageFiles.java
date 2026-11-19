package br.remoto.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Operações de arquivo que substituem as garantias que um banco daria (atomicidade). */
public final class StorageFiles {

    private StorageFiles() {
    }

    /**
     * Grava num arquivo temporário e renomeia por cima do destino. Quem lê nunca vê um arquivo
     * pela metade, mesmo se o processo morrer no meio da gravação.
     */
    public static void writeAtomically(Path file, byte[] content) {
        try {
            Path dir = file.toAbsolutePath().getParent();
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, "." + file.getFileName(), ".tmp");
            try {
                Files.write(tmp, content);
                moveAtomically(tmp, file);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static byte[] readAllBytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path p : paths) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static long sizeOf(Path dir) {
        if (!Files.exists(dir)) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile).mapToLong(p -> p.toFile().length()).sum();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Subdiretórios imediatos (lista vazia se o diretório não existir). */
    public static List<Path> subdirectories(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> list = Files.list(dir)) {
            return list.filter(Files::isDirectory).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Arquivos imediatos com a extensão dada, ignorando temporários ocultos. */
    public static List<Path> filesEndingWith(Path dir, String suffix) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> list = Files.list(dir)) {
            return list.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(suffix) && !name.startsWith(".");
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
