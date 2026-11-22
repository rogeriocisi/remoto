package br.remoto.core.scenario;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioValidatorTest {

    private static final Map<String, Object> CONFIG = Map.of("format", "remoto-legacy-db/1");

    @Test
    void cenarioValidoNaoTemErros() {
        Scenario s = new Scenario(null, "ok", "", 1, 1, 100, 0.05, Scenario.ENGINE_LEGACY, CONFIG);

        assertThat(ScenarioValidator.validate(s)).isEmpty();
    }

    @Test
    void apontaCadaCampoInvalido() {
        Scenario s = new Scenario(null, " ", "", 99, 1, -1, 0, null, null);

        assertThat(ScenarioValidator.validate(s)).containsExactlyInAnyOrder(
                "name: obrigatório",
                "schemaVersion: esperado 1",
                "durationMs: deve ser maior que zero",
                "stepMs: deve ser maior que zero",
                "legacy: obrigatório (a configuração do modelo)");
    }

    @Test
    void limitaONumeroDePassos() {
        Scenario s = new Scenario(null, "longo", "", 1, 1, 1_000_000, 0.01, Scenario.ENGINE_LEGACY, CONFIG);

        assertThat(ScenarioValidator.validate(s)).anyMatch(e -> e.startsWith("durationMs/stepMs"));
    }
}
