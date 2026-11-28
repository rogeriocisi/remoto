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

    /**
     * {@code Simulation.run} faz {@code totSteps = (int)(tFin/step)}, que trunca. Enquanto
     * {@code totalSteps()} arredondava, os dois discordavam em uma unidade sempre que a duração
     * não é múltipla exata do passo, e o validador contava um passo que o motor nunca roda.
     */
    @Test
    void aContagemDePassosEhADoMotor() {
        assertThat(passos(8.6, 0.05)).as("8,6 / 0,05 = 171,999…: trunca para 171").isEqualTo(171);
        assertThat(passos(4.3, 0.05)).as("4,3 / 0,05 = 85,999…").isEqualTo(85);
        assertThat(passos(9.1, 0.05)).as("9,1 / 0,05 = 181,999…").isEqualTo(181);
        assertThat(passos(16.2, 0.05)).as("16,2 / 0,05 = 323,999…").isEqualTo(323);
        assertThat(passos(100, 0.05)).as("múltiplo exato continua exato").isEqualTo(2000);
        assertThat(passos(5000, 0.05)).as("a duração do cenário padrão").isEqualTo(100_000);
    }

    private static long passos(double durationMs, double stepMs) {
        return new Scenario(null, "p", "", 1, 1, durationMs, stepMs, Scenario.ENGINE_LEGACY, CONFIG).totalSteps();
    }
}
