package br.remoto.storage.area;

import br.remoto.storage.DataLayout;
import br.remoto.storage.StorageFiles;
import br.remoto.storage.UserId;
import br.remoto.storage.UserLocks;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** A área de um navegador é a pasta dela; o último uso fica na data de um arquivo oculto. */
public final class FileAreaRepository implements AreaRepository {

    static final String LAST_USE = ".ultimo-uso";
    private static final Duration TOUCH_EVERY = Duration.ofHours(1);

    private final DataLayout layout;
    private final UserLocks locks;

    public FileAreaRepository(DataLayout layout, UserLocks locks) {
        this.layout = layout;
        this.locks = locks;
    }

    @Override
    public void exportZip(UserId user, OutputStream out) {
        Path root = layout.userDir(user);
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            if (!Files.isDirectory(root)) {
                return;
            }
            List<Path> files;
            try (Stream<Path> walk = Files.walk(root)) {
                files = walk.filter(Files::isRegularFile)
                        .filter(p -> !p.getFileName().toString().startsWith("."))
                        .sorted()
                        .toList();
            }
            for (Path file : files) {
                zip.putNextEntry(new ZipEntry(root.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, zip);
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(UserId user) {
        locks.withLock(user, () -> {
            StorageFiles.deleteRecursively(layout.userDir(user));
            return null;
        });
        locks.forget(user);
    }

    @Override
    public long diskUsageBytes(UserId user) {
        return StorageFiles.sizeOf(layout.userDir(user));
    }

    @Override
    public void touch(UserId user) {
        Path dir = layout.userDir(user);
        if (!Files.isDirectory(dir)) {
            return;
        }
        Path marker = dir.resolve(LAST_USE);
        try {
            if (!Files.exists(marker)) {
                Files.createFile(marker);
            } else if (Files.getLastModifiedTime(marker).toInstant().isBefore(Instant.now().minus(TOUCH_EVERY))) {
                Files.setLastModifiedTime(marker, FileTime.from(Instant.now()));
            }
        } catch (IOException e) {
            // uso não registrado desta vez: no máximo adia a limpeza
        }
    }

    @Override
    public int deleteUnusedSince(Instant limit) {
        int deleted = 0;
        for (Path dir : StorageFiles.subdirectories(layout.sessionsDir())) {
            UserId user;
            try {
                user = new UserId(dir.getFileName().toString());
            } catch (IllegalArgumentException e) {
                continue; // não é uma área
            }
            if (lastUse(dir).isBefore(limit)) {
                delete(user);
                deleted++;
            }
        }
        return deleted;
    }

    /** O mais recente entre o marcador de uso e a pasta (áreas sem marcador: a data da pasta). */
    private static Instant lastUse(Path dir) {
        try {
            Instant last = Files.getLastModifiedTime(dir).toInstant();
            Path marker = dir.resolve(LAST_USE);
            if (Files.exists(marker)) {
                Instant used = Files.getLastModifiedTime(marker).toInstant();
                if (used.isAfter(last)) {
                    last = used;
                }
            }
            return last;
        } catch (IOException e) {
            return Instant.now(); // na dúvida, não apaga
        }
    }
}
