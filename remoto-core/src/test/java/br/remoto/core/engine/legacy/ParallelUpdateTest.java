package br.remoto.core.engine.legacy;

import br.remoto.core.model.random.Rng;

import br.remoto.core.scenario.Scenario;
import br.remoto.core.simulation.setup.Configuration;
import br.remoto.core.simulation.Simulation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A atualização paralela dos neurônios tem de dar exatamente o resultado do laço sequencial,
 * bit a bit, e não apenas um resultado estatisticamente parecido.
 */
class ParallelUpdateTest {

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

    @ParameterizedTest
    @CsvSource({"17, 100", "24, 40", "42, 40"})
    void quatroThreadsDaoAsMesmasSaidasQueUma(int id, double durationMs) throws Exception {
        Scenario scenario = viaJsonFile(db.exportScenario(id)).withDuration(durationMs);

        Recorder sequential = run(new LegacyEngine(1), scenario);
        Recorder parallel = run(new LegacyEngine(4), scenario);

        assertThat(sequential.outputs).isNotEmpty();
        assertThat(parallel.outputs).isEqualTo(sequential.outputs);
        assertThat(parallel.sha256()).isEqualTo(sequential.sha256());
    }

    @Test
    void oCenarioPadraoRealmenteRodaEmParalelo() {
        Scenario scenario = viaJsonFile(db.exportScenario(17));
        Configuration conf = LegacyConfigurations.load(LegacyJson.toConfig(scenario.legacy()));
        conf.setTFin(5);
        conf.setStep(scenario.stepMs());
        conf.setStoreResults(false);
        conf.setStoreSignals(true);
        conf.setChangedConfiguration(true);
        conf.setKeepProperties(false);

        Simulation sim = new Simulation(conf, "teste-paralelo");
        sim.setThreads(4);
        Rng.seed(scenario.seed());
        try {
            sim.run();
        } finally {
            Rng.reset();
        }

        // os motoneurônios vão para as threads; tratos e aferentes ficam na thread principal
        assertThat(sim.getParallelNeurons()).isGreaterThan(100);
    }

    @Test
    void threadMarcadaNaoSorteia() throws Exception {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            Rng.forbid();
            try {
                Rng.random();
            } catch (Throwable e) {
                thrown.set(e);
            }
        });
        worker.start();
        worker.join();

        // um Error, para não ser engolido pelos catch (Exception) do código original
        assertThat(thrown.get()).isInstanceOf(Rng.ForbiddenDraw.class).isInstanceOf(Error.class);
    }

    private static Recorder run(LegacyEngine engine, Scenario scenario) {
        Recorder r = new Recorder();
        engine.run(scenario, r);
        return r;
    }

    /** Mesmo caminho do site: o cenário vira arquivo JSON e é lido de volta. */
    private Scenario viaJsonFile(Scenario scenario) {
        return json.fromBytes(json.toBytes(scenario), Scenario.class);
    }
}
