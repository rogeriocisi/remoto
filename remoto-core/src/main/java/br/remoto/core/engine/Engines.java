package br.remoto.core.engine;

import br.remoto.core.scenario.Scenario;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Motores disponíveis, escolhidos pelo campo {@code engine} de cada cenário. */
public final class Engines {

    private final Map<String, SimulationEngine> byId = new LinkedHashMap<>();

    public Engines(List<SimulationEngine> engines) {
        for (SimulationEngine engine : engines) {
            if (byId.putIfAbsent(engine.id(), engine) != null) {
                throw new IllegalArgumentException("motor repetido: " + engine.id());
            }
        }
    }

    /** @throws IllegalArgumentException se o cenário pede um motor que não existe aqui */
    public SimulationEngine forScenario(Scenario scenario) {
        SimulationEngine engine = byId.get(scenario.engineOrDefault());
        if (engine == null) {
            throw new IllegalArgumentException("motor desconhecido: " + scenario.engineOrDefault()
                    + " (disponíveis: " + String.join(", ", byId.keySet()) + ")");
        }
        return engine;
    }

    public Collection<SimulationEngine> all() {
        return byId.values();
    }
}
