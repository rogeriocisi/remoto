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
}
