package br.remoto.core.scenario;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Regras de validade de um {@link Scenario}; devolve mensagens em vez de lançar exceção. */
public final class ScenarioValidator {

    /** Limite de passos por simulação (protege memória e CPU do servidor). */
    public static final long MAX_STEPS = 2_000_000L;
    public static final int MAX_MOTONEURONS = 2_000;

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
        if (Scenario.ENGINE_LEGACY.equals(s.engineOrDefault())) {
            if (s.legacy() == null || s.legacy().isEmpty()) {
                errors.add("legacy: obrigatório para o motor legacy");
            }
            return List.copyOf(errors);
        }
        if (s.nuclei().isEmpty()) {
            errors.add("nuclei: informe ao menos um núcleo");
        }
        Set<Nucleus> seen = EnumSet.noneOf(Nucleus.class);
        for (int i = 0; i < s.nuclei().size(); i++) {
            NucleusSpec n = s.nuclei().get(i);
            String path = "nuclei[" + i + "]";
            if (n == null || n.nucleus() == null) {
                errors.add(path + ".nucleus: obrigatório");
                continue;
            }
            if (!seen.add(n.nucleus())) {
                errors.add(path + ".nucleus: " + n.nucleus() + " repetido");
            }
            if (n.motoneurons() < 1 || n.motoneurons() > MAX_MOTONEURONS) {
                errors.add(path + ".motoneurons: entre 1 e " + MAX_MOTONEURONS);
            }
            if (!Double.isFinite(n.driveNa())) {
                errors.add(path + ".driveNa: valor inválido");
            }
            if (!(n.noiseNa() >= 0) || !Double.isFinite(n.noiseNa())) {
                errors.add(path + ".noiseNa: deve ser maior ou igual a zero");
            }
        }
        return List.copyOf(errors);
    }
}
