package br.remoto.web.api;

import br.remoto.core.scenario.Scenario;
import br.remoto.storage.Versioned;
import br.remoto.storage.scenario.PublicScenarioCatalog;
import br.remoto.storage.scenario.ScenarioRepository;
import br.remoto.storage.scenario.ShareRepository;
import br.remoto.web.security.Caller;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;

/** De onde vem o cenário de uma execução: enviado inline, salvo pelo usuário, público ou compartilhado. */
@Component
public class ScenarioSources {

    /** Exatamente um dos campos deve ser preenchido. */
    public record RunRequest(Scenario scenario, String scenarioId, String publicScenarioId, String shareHash) {
    }

    private final ScenarioRepository scenarios;
    private final PublicScenarioCatalog catalog;
    private final ShareRepository shares;

    ScenarioSources(ScenarioRepository scenarios, PublicScenarioCatalog catalog, ShareRepository shares) {
        this.scenarios = scenarios;
        this.catalog = catalog;
        this.shares = shares;
    }

    public Scenario resolve(Caller caller, RunRequest request) {
        if (request.scenario() != null) {
            return request.scenario();
        }
        if (request.scenarioId() != null) {
            return scenarios.find(caller.id(), request.scenarioId()).map(Versioned::value)
                    .orElseThrow(() -> new NoSuchElementException("cenário não encontrado"));
        }
        if (request.publicScenarioId() != null) {
            return catalog.find(request.publicScenarioId())
                    .orElseThrow(() -> new NoSuchElementException("cenário público não encontrado"));
        }
        if (request.shareHash() != null) {
            return shares.find(request.shareHash())
                    .orElseThrow(() -> new NoSuchElementException("compartilhamento não encontrado"));
        }
        throw new IllegalArgumentException("informe scenario, scenarioId, publicScenarioId ou shareHash");
    }
}
