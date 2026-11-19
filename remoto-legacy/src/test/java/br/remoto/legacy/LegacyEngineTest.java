package br.remoto.legacy;

import br.remoto.core.scenario.Scenario;
import br.remoto.core.scenario.ScenarioValidator;
import br.remoto.storage.JsonCodec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que o motor original, rodando na nova plataforma, é (1) fiel ao banco antigo,
 * (2) reprodutível e (3) estável em relação às saídas de referência.
 */
class LegacyEngineTest {

    private static final double SHORT_RUN_MS = 40;
    private static LegacyDatabase db;
    private final LegacyEngine engine = new LegacyEngine();
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
    void exportaAsConfiguracoesPublicasSemDadosPessoais() {
        assertThat(db.configurations())
                .filteredOn(c -> "guest".equals(c.owner()))
                .extracting(LegacyDatabase.ConfigurationInfo::id)
                .containsExactly(17, 24, 42);

        Scenario scenario = db.exportScenario(17);
        String text = new String(json.toBytes(scenario), StandardCharsets.UTF_8);

        assertThat(ScenarioValidator.validate(scenario)).isEmpty();
        assertThat(scenario.engine()).isEqualTo(Scenario.ENGINE_LEGACY);
        assertThat(scenario.durationMs()).isEqualTo(5000);
        assertThat(text).doesNotContain("\"USER\"").doesNotContain("PASSWORD").doesNotContain("EMAIL");
    }

    @Test
    void cenarioJsonReproduzExatamenteOBancoOriginal() throws Exception {
        Recorder fromDatabase = new Recorder();
        engine.run(LegacyConfigurations.loadFromJdbc(db.jdbcUrl(), 17), 7, SHORT_RUN_MS, 0.05, fromDatabase);

        Recorder fromJson = new Recorder();
        engine.run(viaJsonFile(db.exportScenario(17)).withSeed(7).withDuration(SHORT_RUN_MS), fromJson);

        assertThat(fromJson.outputs).isNotEmpty().isEqualTo(fromDatabase.outputs);
    }

    @Test
    void mesmaSeedReproduzESeedDiferenteMuda() throws Exception {
        Scenario scenario = viaJsonFile(db.exportScenario(42)).withDuration(SHORT_RUN_MS);

        Recorder a = run(scenario.withSeed(1));
        Recorder b = run(scenario.withSeed(1));
        Recorder c = run(scenario.withSeed(2));

        assertThat(a.sha256()).isEqualTo(b.sha256());
        assertThat(c.sha256()).isNotEqualTo(a.sha256());
        assertThat(a.lastProgress).isEqualTo(1.0);
    }

    @ParameterizedTest
    @ValueSource(ints = {17, 24, 42})
    void saidasBatemComAReferencia(int id) throws Exception {
        Scenario scenario = viaJsonFile(db.exportScenario(id)).withDuration(SHORT_RUN_MS);
        String key = id + "." + scenario.seed() + "." + (int) SHORT_RUN_MS;

        Recorder r = run(scenario);
        String emgHash = r.sha256(name -> name.startsWith("emg-"));

        assertThat(r.outputs).containsKey("torque");
        // força, disparos e torque: as mesmas referências de antes do EMG existir
        assertThat(r.sha256(name -> !name.startsWith("emg-"))).as("golden master %s%n%s", key, r.summary())
                .isEqualTo(golden().getProperty(key));
        assertThat(emgHash).as("golden master %s.emg (valor atual: %s)", key, emgHash)
                .isEqualTo(golden().getProperty(key + ".emg"));
    }

    @Test
    void emgTemUmPontoPorPassoEDuasVersoesPorMusculo() throws Exception {
        Recorder r = run(viaJsonFile(db.exportScenario(17)).withDuration(SHORT_RUN_MS));

        assertThat(r.outputs).containsKeys("emg-SOL", "emg-bruto-SOL");
        String[] raw = r.outputs.get("emg-bruto-SOL").split("\n");
        assertThat(raw[0]).isEqualTo("t_ms,valor[mV]");
        assertThat(raw).hasSize(1 + (int) (SHORT_RUN_MS / 0.05));
        assertThat(java.util.Arrays.stream(raw).skip(1).mapToDouble(l -> Double.parseDouble(l.split(",")[1])))
                .as("há disparos nos 40 ms, então o EMG bruto não pode ser todo zero")
                .anyMatch(v -> v != 0);
    }

    private Recorder run(Scenario scenario) {
        Recorder r = new Recorder();
        engine.run(scenario, r);
        return r;
    }

    /** Mesmo caminho do site: o cenário vira arquivo JSON e é lido de volta. */
    private Scenario viaJsonFile(Scenario scenario) {
        return json.fromBytes(json.toBytes(scenario), Scenario.class);
    }

    private static Properties golden() throws Exception {
        Properties p = new Properties();
        try (InputStream in = LegacyEngineTest.class.getResourceAsStream("golden-master.properties")) {
            p.load(in);
        }
        return p;
    }
}
