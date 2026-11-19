package br.remoto.core.scenario;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioValidatorTest {

    @Test
    void cenarioValidoNaoTemErros() {
        Scenario s = new Scenario(null, "ok", "", 1, 1, 100, 0.05,
                List.of(new NucleusSpec(Nucleus.SOL, 10, 5, 1)));

        assertThat(ScenarioValidator.validate(s)).isEmpty();
    }

    @Test
    void apontaCadaCampoInvalido() {
        Scenario s = new Scenario(null, " ", "", 99, 1, -1, 0, List.of(
                new NucleusSpec(Nucleus.SOL, 0, Double.NaN, -1),
                new NucleusSpec(Nucleus.SOL, 10, 5, 1)));

        assertThat(ScenarioValidator.validate(s)).containsExactlyInAnyOrder(
                "name: obrigatório",
                "schemaVersion: esperado 1",
                "durationMs: deve ser maior que zero",
                "stepMs: deve ser maior que zero",
                "nuclei[0].motoneurons: entre 1 e 2000",
                "nuclei[0].driveNa: valor inválido",
                "nuclei[0].noiseNa: deve ser maior ou igual a zero",
                "nuclei[1].nucleus: SOL repetido");
    }

    @Test
    void limitaONumeroDePassos() {
        Scenario s = new Scenario(null, "longo", "", 1, 1, 1_000_000, 0.01,
                List.of(new NucleusSpec(Nucleus.TA, 10, 5, 1)));

        assertThat(ScenarioValidator.validate(s)).anyMatch(e -> e.startsWith("durationMs/stepMs"));
    }
}
