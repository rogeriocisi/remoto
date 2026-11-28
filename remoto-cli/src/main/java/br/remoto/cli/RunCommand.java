package br.remoto.cli;

import br.remoto.core.engine.ResultSink;
import br.remoto.core.engine.RunContext;
import br.remoto.core.engine.SimulationCancelledException;
import br.remoto.core.engine.SimulationEngine;
import br.remoto.core.scenario.Scenario;
import br.remoto.core.scenario.ScenarioValidator;
import br.remoto.storage.Hashing;
import br.remoto.storage.Ids;
import br.remoto.storage.JsonCodec;
import br.remoto.storage.StorageFiles;
import br.remoto.storage.run.CsvGzResultSink;
import br.remoto.storage.run.RunManifest;
import br.remoto.storage.run.RunStatus;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;

/** {@code remoto run cenario.json -o saida/} grava manifest.json, config.json e results/*.csv.gz. */
@Command(name = "run", description = "Roda um cenário e grava os resultados (CSV.gz) com manifesto.")
final class RunCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "arquivo JSON do cenário")
    Path scenarioFile;

    @Option(names = {"-o", "--out"}, description = "diretório de saída (padrão: ./resultados/<id>)")
    Path out;

    @Option(names = "--seed", description = "sobrescreve a seed do cenário")
    Long seed;

    @Option(names = "--duration-ms", description = "sobrescreve a duração simulada (ms)")
    Double durationMs;

    @Option(names = "--threads",
            description = "threads do motor original (padrão: núcleos da CPU; 1 = laço sequencial). "
                    + "O resultado é o mesmo com qualquer valor.")
    Integer threads;

    /**
     * O cancelamento desta execução. Fica visível para o teste poder entregar um já cancelado;
     * em uso normal é criado em {@link #call()} e ligado ao Ctrl+C.
     */
    Cancellation cancellation;

    @Override
    public Integer call() throws Exception {
        JsonCodec json = new JsonCodec();
        Scenario scenario = json.read(scenarioFile, Scenario.class);
        if (seed != null) {
            scenario = scenario.withSeed(seed);
        }
        if (durationMs != null) {
            scenario = scenario.withDuration(durationMs);
        }
        List<String> errors = ScenarioValidator.validate(scenario);
        if (!errors.isEmpty()) {
            errors.forEach(e -> System.err.println("erro: " + e));
            return 2;
        }

        SimulationEngine engine = RemotoCli.engines(threads).forScenario(scenario);
        String runId = Ids.newId();
        Path dir = out != null ? out : Path.of("resultados", runId);
        Path results = dir.resolve("results");
        Files.createDirectories(results);

        byte[] config = json.toBytes(scenario);
        StorageFiles.writeAtomically(dir.resolve("config.json"), config);
        RunManifest manifest = new RunManifest(runId, "cli", scenario.id(), scenario.name(), Hashing.sha256Hex(config),
                scenario.seed(), engine.version(), RunStatus.RUNNING, Instant.now(), Instant.now(), null, null, false);

        ResultSink sink = new CsvGzResultSink(results);
        int[] lastDecile = {-1};
        Cancellation cancel = cancellation != null ? cancellation : Cancellation.onShutdown();
        try {
            engine.run(scenario, new RunContext() {
                @Override
                public void progress(double fraction) {
                    int decile = (int) (fraction * 10);
                    if (decile != lastDecile[0]) {
                        lastDecile[0] = decile;
                        System.err.printf("  %3d%%%n", decile * 10);
                    }
                }

                @Override
                public boolean isCancelled() {
                    return cancel.cancelled();
                }

                @Override
                public ResultSink results() {
                    return sink;
                }
            });
        } catch (SimulationCancelledException cancelled) {
            json.writeAtomically(dir.resolve("manifest.json"),
                    manifest.finished(RunStatus.CANCELED, Instant.now(), "cancelada"));
            System.err.println("simulação cancelada");
            System.out.println(dir.toAbsolutePath());
            return 130;
        } finally {
            cancel.close();
        }

        json.writeAtomically(dir.resolve("manifest.json"), manifest.finished(RunStatus.DONE, Instant.now(), null));
        System.out.println(dir.toAbsolutePath());
        return 0;
    }
}
