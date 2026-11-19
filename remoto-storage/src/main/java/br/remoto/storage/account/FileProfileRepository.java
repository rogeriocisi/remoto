package br.remoto.storage.account;

import br.remoto.storage.DataLayout;
import br.remoto.storage.JsonCodec;
import br.remoto.storage.StorageFiles;
import br.remoto.storage.UserId;
import br.remoto.storage.UserLocks;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Perfil em {@code users/<id>/profile.json}; a conta é a própria pasta do usuário. */
public final class FileProfileRepository implements ProfileRepository {

    private final DataLayout layout;
    private final JsonCodec json;
    private final UserLocks locks;

    public FileProfileRepository(DataLayout layout, JsonCodec json, UserLocks locks) {
        this.layout = layout;
        this.json = json;
        this.locks = locks;
    }

    @Override
    public Profile getOrCreate(UserId user, Supplier<Profile> initial) {
        return locks.withLock(user, () -> {
            Path file = layout.profileFile(user);
            if (Files.isRegularFile(file)) {
                return json.read(file, Profile.class);
            }
            Profile profile = initial.get();
            json.writeAtomically(file, profile);
            return profile;
        });
    }

    @Override
    public Profile save(UserId user, Profile profile) {
        return locks.withLock(user, () -> {
            json.writeAtomically(layout.profileFile(user), profile);
            return profile;
        });
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
    public void deleteAccount(UserId user) {
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
