package br.remoto.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RemotoCliTest {

    @TempDir
    Path tmp;

    @Test
    void rodaUmCenarioPublicoEGravaResultados() {
        Path scenario = Path.of("..", "scenarios", "remoto-17-default-soleus-contraction.json");
        Path out = tmp.resolve("saida");

        int exit = new CommandLine(new RemotoCli()).execute("run", scenario.toString(), "-o", out.toString(),
                "--duration-ms", "40");

        assertThat(exit).isZero();
        assertThat(out.resolve("manifest.json")).exists();
        assertThat(out.resolve("config.json")).exists();
        assertThat(out.resolve("results").resolve("forca-SOL.csv.gz")).exists();
        assertThat(out.resolve("results").resolve("disparos-SOL.csv.gz")).exists();
    }

    @Test
    void validaOsCenariosPublicosDoRepositorio() throws Exception {
        try (var files = Files.list(Path.of("..", "scenarios"))) {
            String[] args = files.map(Path::toString).filter(p -> p.endsWith(".json")).toArray(String[]::new);
            String[] cmd = new String[args.length + 1];
            cmd[0] = "validate";
            System.arraycopy(args, 0, cmd, 1, args.length);

            assertThat(new CommandLine(new RemotoCli()).execute(cmd)).isZero();
        }
    }

    @Test
    void cenarioInvalidoRetornaCodigoDeErro() throws Exception {
        Path bad = tmp.resolve("ruim.json");
        Files.writeString(bad, "{\"name\":\"\",\"schemaVersion\":1,\"durationMs\":-1,\"stepMs\":0.05}");

        assertThat(new CommandLine(new RemotoCli()).execute("validate", bad.toString())).isEqualTo(2);
    }
    /** A curva H/M em lote, com duas intensidades: uma linha por simulação em sweep.csv. */
    @Test
    void varreduraGravaUmaLinhaPorSimulacao() throws Exception {
        Path scenario = Path.of("..", "scenarios", "remoto-17-default-soleus-contraction.json");
        Path out = tmp.resolve("lote");

        int exit = new CommandLine(new RemotoCli()).execute("sweep", scenario.toString(), "--protocol", "hm",
                "--values", "0,40", "--keep-runs", "-o", out.toString());

        assertThat(exit).isZero();
        java.util.List<String> lines = Files.readAllLines(out.resolve("sweep.csv"));
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).isEqualTo("NERVE:CD_NERVE=PTN:PEAK,M_p2p[mV],H_p2p[mV],M_motoneuronios,H_motoneuronios");
        assertThat(lines.get(1)).startsWith("0,");
        assertThat(lines.get(2)).startsWith("40,").endsWith(",900,0");
        assertThat(out.resolve("run-2").resolve("results").resolve("emg-SOL.csv.gz")).exists();
        assertThat(out.resolve("run-2").resolve("config.json")).exists();
    }

    @Test
    void varreduraComParametroOuMedidaInvalidaRetornaCodigoDeErro() {
        Path scenario = Path.of("..", "scenarios", "remoto-17-default-soleus-contraction.json");
        CommandLine cli = new CommandLine(new RemotoCli());
        assertThat(cli.execute("sweep", scenario.toString(), "--param", "NERVE:CD_NERVE=XX:PEAK", "--values", "1",
                "--measure", "p2p(emg-SOL)", "-o", tmp.toString())).isEqualTo(2);
        assertThat(cli.execute("sweep", scenario.toString(), "--param", "NERVE:CD_NERVE=PTN:PEAK", "--values", "1",
                "--measure", "media(emg-SOL)", "-o", tmp.toString())).isEqualTo(2);
        assertThat(cli.execute("sweep", scenario.toString(), "--protocol", "hm", "-o", tmp.toString())).isEqualTo(2);
    }

    @Test
    void cancelarParaASimulacaoEMarcaOManifesto() throws Exception {
        // O motor consulta isCancelled() na thread do vigia de progresso, não nesta. Enquanto a CLI
        // respondia Thread.currentThread().isInterrupted(), lia a flag da thread errada e nunca
        // cancelava; e, sem capturar a exceção, o manifesto ficava preso em RUNNING.
        Path scenario = Path.of("..", "scenarios", "remoto-17-default-soleus-contraction.json");
        Path out = tmp.resolve("cancelada");

        RunCommand cmd = new RunCommand();
        cmd.scenarioFile = scenario;
        cmd.out = out;
        cmd.threads = 1;
        cmd.cancellation = Cancellation.onShutdown();
        cmd.cancellation.cancel();

        assertThat(cmd.call()).as("código de saída de execução cancelada").isEqualTo(130);
        assertThat(out.resolve("manifest.json")).exists();
        assertThat(Files.readString(out.resolve("manifest.json")))
                .as("manifesto de execução cancelada").contains("CANCELED");
        try (var arquivos = Files.list(out.resolve("results"))) {
            assertThat(arquivos).as("execução cancelada não grava resultados").isEmpty();
        }
    }
}
