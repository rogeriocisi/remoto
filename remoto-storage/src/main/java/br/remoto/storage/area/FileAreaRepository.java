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
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** A área de uma sessão é a pasta dela. */
public final class FileAreaRepository implements AreaRepository {

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
}
