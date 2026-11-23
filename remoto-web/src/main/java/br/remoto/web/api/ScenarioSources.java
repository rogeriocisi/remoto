package br.remoto.web.api;

import br.remoto.core.scenario.Scenario;
import br.remoto.storage.Versioned;
import br.remoto.storage.scenario.PublicScenarioCatalog;
import br.remoto.storage.scenario.ScenarioRepository;
import br.remoto.web.i18n.Messages;
import br.remoto.web.security.Caller;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;

/** De onde vem o cenário de uma execução: enviado inline, salvo na sessão ou público. */
@Component
public class ScenarioSources {

    /** Exatamente um dos campos deve ser preenchido. */
    public record RunRequest(Scenario scenario, String scenarioId, String publicScenarioId) {
    }

    private final ScenarioRepository scenarios;
    private final PublicScenarioCatalog catalog;
    ScenarioSources(ScenarioRepository scenarios, PublicScenarioCatalog catalog) {
        this.scenarios = scenarios;
        this.catalog = catalog;
    }

    public Scenario resolve(Caller caller, RunRequest request) {
        if (request.scenario() != null) {
            return request.scenario();
        }
        if (request.scenarioId() != null) {
            return scenarios.find(caller.id(), request.scenarioId()).map(Versioned::value)
                    .orElseThrow(() -> new NoSuchElementException(Messages.msg("scenario.notFound")));
        }
        if (request.publicScenarioId() != null) {
            return catalog.find(request.publicScenarioId())
                    .orElseThrow(() -> new NoSuchElementException(Messages.msg("scenario.public.notFound")));
        }
        throw new IllegalArgumentException("informe scenario, scenarioId ou publicScenarioId");
    }
}
