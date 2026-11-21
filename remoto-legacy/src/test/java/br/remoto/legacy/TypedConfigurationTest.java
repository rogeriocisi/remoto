package br.remoto.legacy;

import br.remoto.core.scenario.Scenario;
import br.remoto.model.Configuration;
import br.remoto.storage.JsonCodec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A configuração montada a partir dos parâmetros tipados (sem banco) tem de ser a mesma, campo a
 * campo, que o {@code ConfigurationDAO} original montava lendo do HSQLDB.
 */
class TypedConfigurationTest {

    private static LegacyDatabase db;
    private final JsonCodec json = new JsonCodec();

    @BeforeAll
    static void open() {
        db = LegacyDatabase.open(Path.of(System.getProperty("remoto.legacy.db")));
    }

    @AfterAll
    static void close() {
        db.close();
    }

    @Test
    void todasAsConfiguracoesDoBancoOriginalSaoIguaisPelosDoisCaminhos() {
        List<String> failures = new ArrayList<>();
        int compared = 0;
        for (LegacyDatabase.ConfigurationInfo info : db.configurations()) {
            LegacyConfig config = LegacyJson.toConfig(viaJsonFile(db.exportScenario(info.id())).legacy());

            Configuration throughHsqldb = LegacyConfigurations.loadThroughHsqldb(config);
            Configuration typed = LegacyConfigurations.load(config);

            List<String> differences = DeepCompare.differences(throughHsqldb, typed);
            if (!differences.isEmpty()) {
                failures.add("configuração " + info.id() + ":\n  " + String.join("\n  ", differences));
            }
            compared++;
        }
        assertThat(compared).isEqualTo(db.configurations().size()).isGreaterThanOrEqualTo(30);
        assertThat(failures).isEmpty();
    }

    @Test
    void osCenariosPublicosSaoIguaisPelosDoisCaminhos() throws Exception {
        List<Path> files;
        try (Stream<Path> s = Files.list(Path.of(System.getProperty("remoto.legacy.db")).getParent().getParent()
                .resolve("scenarios"))) {
            files = s.filter(p -> p.getFileName().toString().startsWith("remoto-")).sorted().toList();
        }
        assertThat(files).hasSize(3);
        for (Path file : files) {
            Scenario scenario = json.read(file, Scenario.class);
            LegacyConfig config = LegacyJson.toConfig(scenario.legacy());

            assertThat(DeepCompare.differences(LegacyConfigurations.loadThroughHsqldb(config),
                    LegacyConfigurations.load(config)))
                    .as(file.getFileName().toString())
                    .isEmpty();
        }
    }

    /** Mesmo caminho do site: o cenário vira arquivo JSON e é lido de volta. */
    private Scenario viaJsonFile(Scenario scenario) {
        return json.fromBytes(json.toBytes(scenario), Scenario.class);
    }
}
