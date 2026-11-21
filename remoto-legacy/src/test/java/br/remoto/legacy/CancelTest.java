package br.remoto.legacy;

import br.remoto.core.engine.SimulationCancelledException;
import br.remoto.core.scenario.Scenario;
import br.remoto.storage.JsonCodec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cancelar uma execução do motor original para a simulação em pouco tempo, com ou sem threads. */
class CancelTest {

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
    @ValueSource(ints = {1, 4})
    void cancelarParaASimulacao(int threads) {
        // 5 s simulados levariam minutos; o pedido de parada chega com a simulação em curso
        Scenario scenario = json.fromBytes(json.toBytes(db.exportScenario(17)), Scenario.class).withDuration(5000);
        Recorder context = new Recorder() {
            private final long start = System.nanoTime();

            @Override
            public boolean isCancelled() {
                return System.nanoTime() - start > 3_000_000_000L;
            }
        };

        long start = System.nanoTime();
        assertThatThrownBy(() -> new LegacyEngine(threads).run(scenario, context))
                .isInstanceOf(SimulationCancelledException.class);
        double seconds = (System.nanoTime() - start) / 1e9;

        assertThat(seconds).as("tempo até parar").isLessThan(20);
        assertThat(context.outputs).as("execução cancelada não grava resultados").isEmpty();
    }
}
