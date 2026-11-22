package br.remoto.storage.area;

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
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static br.remoto.storage.StorageTestSupport.scenario;
import static br.remoto.storage.StorageTestSupport.user;
import static org.assertj.core.api.Assertions.assertThat;

class FileAreaRepositoryTest {

    @TempDir
    Path data;

    @Test
    void exportaEApagaTodaAArea() throws Exception {
        DataLayout layout = new DataLayout(data, data.resolve("public"));
        JsonCodec json = new JsonCodec();
        UserLocks locks = new UserLocks();
        FileAreaRepository areas = new FileAreaRepository(layout, locks);
        FileScenarioRepository scenarios = new FileScenarioRepository(layout, json, locks);
        UserId ana = user("ana");

        scenarios.create(ana, scenario("meu cenário"));

        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        areas.exportZip(ana, zip);
        assertThat(entries(zip.toByteArray())).anyMatch(e -> e.startsWith("scenarios/"));
        assertThat(areas.diskUsageBytes(ana)).isPositive();

        areas.delete(ana);

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
