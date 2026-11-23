package br.remoto.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
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

    /**
     * Renomeia por cima do destino. No Windows, a troca falha com "acesso negado" enquanto outra
     * thread está lendo o destino; nesse caso tenta de novo por até ~1 s. No Linux não acontece.
     */
    public static void moveAtomically(Path source, Path target) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                try {
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (AccessDeniedException e) {
                if (attempt >= MOVE_ATTEMPTS) {
                    throw e;
                }
                pause(attempt);
            }
        }
    }

    private static final int MOVE_ATTEMPTS = 20;

    /**
     * Lê o arquivo inteiro. No Windows (e mais ainda numa pasta sincronizada, como a do Google
     * Drive), quem lê não vê a troca de {@link #writeAtomically} de uma vez só: por um instante o
     * arquivo some ou fica bloqueado. Nesses casos tenta de novo por até ~1 s.
     *
     * @throws UncheckedIOException com a causa original, se o arquivo continua sem poder ser lido
     */
    public static byte[] readAllBytes(Path file) {
        for (int attempt = 1; ; attempt++) {
            try {
                return Files.readAllBytes(file);
            } catch (NoSuchFileException | AccessDeniedException e) {
                if (attempt >= MOVE_ATTEMPTS) {
                    throw new UncheckedIOException(e);
                }
                try {
                    pause(attempt);
                } catch (IOException interrupted) {
                    throw new UncheckedIOException(interrupted);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** Espera um pouco mais a cada tentativa (até 100 ms); interrompido, desiste. */
    private static void pause(int attempt) throws IOException {
        try {
            Thread.sleep(Math.min(100, 5L * attempt));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new java.io.InterruptedIOException("interrompido");
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
