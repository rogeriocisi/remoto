package br.remoto.cli;

import br.remoto.core.engine.ResultSink;
import br.remoto.core.engine.RunContext;
import br.remoto.core.engine.legacy.LegacyEngine;
import br.remoto.core.scenario.Scenario;
import br.remoto.core.scenario.ScenarioValidator;
import br.remoto.storage.JsonCodec;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * As tarefas do ReMoto_AIDP convertidas em cenários ({@link AidpExamples}): os arquivos de
 * scenarios/ são o que o gerador produz, e cada cenário é válido e roda. Para gravar os arquivos
 * depois de mudar o gerador: {@code -Dremoto.gerar.cenarios=<pasta scenarios>}.
 */
class AidpExamplesTest {

    private static final Path SCENARIOS = Path.of("..", "scenarios");

    @Test
    void osArquivosSaoOQueOGeradorProduz() throws Exception {
        String out = System.getProperty("remoto.gerar.cenarios");
        JsonCodec json = new JsonCodec();
        for (ScenarioEdit.Example e : AidpExamples.build(SCENARIOS)) {
            String generated = new String(json.toBytes(e.scenario()), StandardCharsets.UTF_8);
            if (out != null) {
                Files.writeString(Path.of(out).resolve(e.file()), generated);
                continue;
            }
            Path file = SCENARIOS.resolve(e.file());
            assertThat(file).as(e.file()).exists();
            // o Git pode ter trocado as quebras de linha no Windows
            assertThat(Files.readString(file).replace("\r\n", "\n")).as(e.file())
                    .isEqualTo(generated.replace("\r\n", "\n"));
        }
    }

    /** Cada cenário é válido e roda os primeiros 5 ms; os nomes dos arquivos não se repetem. */
    @Test
    void todosValidamERodam() {
        Set<String> files = new HashSet<>();
        for (ScenarioEdit.Example e : AidpExamples.build(SCENARIOS)) {
            assertThat(files.add(e.file())).as(e.file()).isTrue();
            assertThat(ScenarioValidator.validate(e.scenario())).as(e.file()).isEmpty();
            Scenario shortRun = e.scenario().withDuration(5);
            new LegacyEngine(1).run(shortRun, new RunContext() {
                @Override
                public void progress(double fraction) {
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }

                @Override
                public ResultSink results() {
                    return new ResultSink() {
                        @Override
                        public void series(String name, String unit, double[] timeMs, double[] values) {
                        }

                        @Override
                        public void spikes(String name, int[] units, double[] timeMs) {
                        }
                    };
                }
            });
        }
        assertThat(files).hasSize(6);
    }
}
