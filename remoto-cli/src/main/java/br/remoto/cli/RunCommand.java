package br.remoto.cli;

import br.remoto.core.engine.ResultSink;
import br.remoto.core.engine.RunContext;
import br.remoto.core.engine.SimulationEngine;
import br.remoto.core.engine.placeholder.PlaceholderPoolEngine;
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

    @Override
    public Integer call() throws Exception {
        JsonCodec json = new JsonCodec();
        Scenario scenario = json.read(scenarioFile, Scenario.class);
        if (seed != null) {
            scenario = scenario.withSeed(seed);
        }
        List<String> errors = ScenarioValidator.validate(scenario);
        if (!errors.isEmpty()) {
            errors.forEach(e -> System.err.println("erro: " + e));
            return 2;
        }

        SimulationEngine engine = new PlaceholderPoolEngine();
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
                return Thread.currentThread().isInterrupted();
            }

            @Override
            public ResultSink results() {
                return sink;
            }
        });

        json.writeAtomically(dir.resolve("manifest.json"), manifest.finished(RunStatus.DONE, Instant.now(), null));
        System.out.println(dir.toAbsolutePath());
        return 0;
    }
}
