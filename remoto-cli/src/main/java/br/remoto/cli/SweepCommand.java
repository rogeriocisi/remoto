package br.remoto.cli;

import br.remoto.core.engine.ResultSink;
import br.remoto.core.engine.SimulationEngine;
import br.remoto.core.protocol.Measure;
import br.remoto.core.protocol.Protocols;
import br.remoto.core.protocol.ScenarioParameter;
import br.remoto.core.protocol.Sweep;
import br.remoto.core.scenario.Scenario;
import br.remoto.core.scenario.ScenarioValidator;
import br.remoto.storage.JsonCodec;
import br.remoto.storage.StorageFiles;
import br.remoto.storage.run.CsvGzResultSink;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.IntFunction;

/**
 * {@code remoto sweep cenario.json ...}: um protocolo em lote, o mesmo cenário rodado para cada
 * valor de um parâmetro, com uma tabela das medidas de cada simulação (sweep.csv).
 */
@Command(name = "sweep",
        description = {"Roda o cenário uma vez para cada valor de um parâmetro e grava as medidas de cada"
                + " simulação em sweep.csv.",
                "Protocolos prontos: --protocol hm (curva de recrutamento do reflexo H e da onda M, variando"
                        + " a intensidade do estímulo do nervo) e --protocol force (variabilidade da força,"
                        + " variando o intervalo médio de um trato descendente).",
                "Ou um lote qualquer: --param e --measure (ver abaixo)."},
        sortOptions = false)
final class SweepCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "arquivo JSON do cenário")
    Path scenarioFile;

    @Option(names = "--protocol", description = "hm ou force")
    String protocol;

    @Option(names = "--param", description = "o parâmetro que varia: TABELA:CHAVE=VALOR[,CHAVE=VALOR]:COLUNA"
            + " (ex.: NERVE:CD_NERVE=PTN:PEAK), settings.caminho (ex.: settings.nerveStimulus.PTN.pulseWidth),"
            + " seed ou durationMs")
    String param;

    @Option(names = "--measure", description = "uma medida de cada simulação: [nome=]função(saída[,de,até]),"
            + " com a janela em ms; funções p2p, max, min, mean, sd, cv (séries) e count, units (disparos)."
            + " Ex.: M=p2p(emg-SOL,7,25). Pode repetir.")
    List<String> measures = new ArrayList<>();

    @Option(names = "--values", split = ",", description = "os valores do parâmetro, separados por vírgula")
    List<Double> values;

    @Option(names = "--range", description = "os valores de DE a ATÉ de PASSO em PASSO: DE:ATÉ:PASSO (ex.: 5:40:1)")
    String range;

    @Option(names = "--nerve", defaultValue = "PTN", description = "hm: o nervo estimulado (padrão: ${DEFAULT-VALUE})")
    String nerve;

    @Option(names = "--muscle", defaultValue = "SOL", description = "hm e force: o músculo medido (padrão: ${DEFAULT-VALUE})")
    String muscle;

    @Option(names = "--m-window", description = "hm: janela da onda M, em ms depois do início do estímulo: DE:ATÉ"
            + " (padrão: 2:20)")
    String mWindow;

    @Option(names = "--h-window", description = "hm: janela do reflexo H: DE:ATÉ (padrão: 20:50)")
    String hWindow;

    @Option(names = "--tract", defaultValue = "CM_ext", description = "force: o trato descendente (padrão: ${DEFAULT-VALUE})")
    String tract;

    @Option(names = "--from", description = "force: início da janela das medidas (ms; padrão: metade da simulação)")
    Double from;

    @Option(names = "--to", description = "force: fim da janela (ms; padrão: fim da simulação)")
    Double to;

    @Option(names = {"-o", "--out"}, description = "diretório de saída (padrão: ./varredura)")
    Path out = Path.of("varredura");

    @Option(names = "--keep-runs", description = "grava também os resultados de cada simulação (run-1/, run-2/...)")
    boolean keepRuns;

    @Option(names = "--seed", description = "sobrescreve a seed do cenário")
    Long seed;

    @Option(names = "--duration-ms", description = "sobrescreve a duração simulada (ms)")
    Double durationMs;

    @Option(names = "--threads", description = "threads do motor original (padrão: núcleos da CPU)")
    Integer threads;

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

        Scenario base;
        Sweep sweep;
        try {
            List<Double> list = values();
            if ("hm".equals(protocol)) {
                Protocols.Prepared p = Protocols.hmCurve(scenario, nerve, muscle, list, window(mWindow), window(hWindow));
                base = durationMs != null ? p.scenario().withDuration(durationMs) : p.scenario();
                sweep = p.sweep();
            } else if ("force".equals(protocol)) {
                Protocols.Prepared p = Protocols.forceVariability(scenario, tract, muscle, list, from, to);
                base = p.scenario();
                sweep = p.sweep();
            } else if (protocol != null) {
                throw new IllegalArgumentException("protocolo desconhecido: " + protocol + " (há hm e force)");
            } else {
                if (param == null || measures.isEmpty()) {
                    throw new IllegalArgumentException("sem --protocol, dê --param e pelo menos uma --measure");
                }
                base = scenario;
                sweep = new Sweep(ScenarioParameter.parse(param), list, measures.stream().map(Measure::parse).toList());
            }
            // confere o parâmetro antes de rodar
            sweep.parameter().apply(base, list.get(0));
        } catch (IllegalArgumentException e) {
            System.err.println("erro: " + e.getMessage());
            return 2;
        }

        Files.createDirectories(out);
        SimulationEngine engine = RemotoCli.engines(threads).forScenario(base);
        IntFunction<ResultSink> extra = null;
        if (keepRuns) {
            Scenario b = base;
            Sweep s = sweep;
            extra = i -> {
                Path dir = out.resolve("run-" + (i + 1));
                try {
                    Files.createDirectories(dir.resolve("results"));
                    StorageFiles.writeAtomically(dir.resolve("config.json"),
                            json.toBytes(s.parameter().apply(b, s.values().get(i))));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return new CsvGzResultSink(dir.resolve("results"));
            };
        }

        int total = sweep.values().size();
        List<Sweep.Row> rows = sweep.run(base, engine, extra, new Sweep.Listener() {
            @Override
            public void started(int index, double value) {
                System.err.printf("[%d/%d] %s = %s%n", index + 1, total, sweep.parameter().spec(), value);
            }
        });

        StringBuilder csv = new StringBuilder();
        sweep.writeCsv(rows, csv);
        StorageFiles.writeAtomically(out.resolve("sweep.csv"), csv.toString().getBytes(StandardCharsets.UTF_8));
        System.out.print(csv);
        System.err.println(out.resolve("sweep.csv").toAbsolutePath());
        return 0;
    }

    private List<Double> values() {
        if (values != null && range != null) {
            throw new IllegalArgumentException("use --values ou --range, não os dois");
        }
        if (values != null && !values.isEmpty()) {
            return values;
        }
        if (range != null) {
            String[] f = range.split(":");
            if (f.length != 3) {
                throw new IllegalArgumentException("--range é DE:ATÉ:PASSO, por exemplo 5:40:1");
            }
            return Sweep.range(Double.parseDouble(f[0]), Double.parseDouble(f[1]), Double.parseDouble(f[2]));
        }
        throw new IllegalArgumentException("dê os valores do parâmetro com --values ou --range");
    }

    private static double[] window(String text) {
        if (text == null) {
            return null;
        }
        String[] f = text.split(":");
        if (f.length != 2) {
            throw new IllegalArgumentException("janela inválida: " + text + " (use DE:ATÉ, em ms)");
        }
        return new double[] {Double.parseDouble(f[0]), Double.parseDouble(f[1])};
    }
}
