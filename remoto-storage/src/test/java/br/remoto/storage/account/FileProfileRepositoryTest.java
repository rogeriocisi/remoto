package br.remoto.storage.account;

import br.remoto.storage.DataLayout;
import br.remoto.storage.JsonCodec;
import br.remoto.storage.UserId;
import br.remoto.storage.UserLocks;
import br.remoto.storage.scenario.FileScenarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static br.remoto.storage.StorageTestSupport.scenario;
import static br.remoto.storage.StorageTestSupport.user;
import static org.assertj.core.api.Assertions.assertThat;

class FileProfileRepositoryTest {

    @TempDir
    Path data;

    @Test
    void exportaEApagaTodosOsDadosDoUsuario() throws Exception {
        DataLayout layout = new DataLayout(data, data.resolve("public"));
        JsonCodec json = new JsonCodec();
        UserLocks locks = new UserLocks();
        FileProfileRepository profiles = new FileProfileRepository(layout, json, locks);
        FileScenarioRepository scenarios = new FileScenarioRepository(layout, json, locks);
        UserId ana = user("ana");

        profiles.getOrCreate(ana, () -> new Profile("Ana", "USP", "https://issuer.test", Instant.now()));
        scenarios.create(ana, scenario("meu cenário"));

        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        profiles.exportZip(ana, zip);
        assertThat(entries(zip.toByteArray())).contains("profile.json").anyMatch(e -> e.startsWith("scenarios/"));
        assertThat(profiles.diskUsageBytes(ana)).isPositive();

        profiles.deleteAccount(ana);

        assertThat(Files.exists(layout.userDir(ana))).isFalse();
        assertThat(scenarios.list(ana)).isEmpty();
    }

    private static List<String> entries(byte[] zip) throws Exception {
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                names.add(e.getName());
            }
        }
        return names;
    }
}
