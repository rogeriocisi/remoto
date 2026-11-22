package br.remoto.core.scenario;

import java.util.ArrayList;
import java.util.List;

/** Regras de validade de um {@link Scenario}; devolve mensagens em vez de lançar exceção. */
public final class ScenarioValidator {

    /** Limite de passos por simulação (protege memória e CPU do servidor). */
    public static final long MAX_STEPS = 2_000_000L;

    private ScenarioValidator() {
    }

    public static List<String> validate(Scenario s) {
        if (s == null) {
            return List.of("cenário ausente");
        }
        List<String> errors = new ArrayList<>();
        if (s.name() == null || s.name().isBlank()) {
            errors.add("name: obrigatório");
        }
        if (s.schemaVersion() != Scenario.CURRENT_SCHEMA_VERSION) {
            errors.add("schemaVersion: esperado " + Scenario.CURRENT_SCHEMA_VERSION);
        }
        if (!(s.durationMs() > 0)) {
            errors.add("durationMs: deve ser maior que zero");
        }
        if (!(s.stepMs() > 0)) {
            errors.add("stepMs: deve ser maior que zero");
        } else if (s.durationMs() > 0 && s.totalSteps() > MAX_STEPS) {
            errors.add("durationMs/stepMs: no máximo " + MAX_STEPS + " passos por simulação");
        }
        if (Scenario.ENGINE_LEGACY.equals(s.engineOrDefault()) && (s.legacy() == null || s.legacy().isEmpty())) {
            errors.add("legacy: obrigatório (a configuração do modelo)");
        }
        return List.copyOf(errors);
    }
}
