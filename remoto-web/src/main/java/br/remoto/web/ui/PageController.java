package br.remoto.web.ui;

import br.remoto.storage.run.RunManifest;
import br.remoto.storage.run.RunRepository;
import br.remoto.storage.run.RunStatus;
import br.remoto.storage.scenario.PublicScenarioCatalog;
import br.remoto.storage.scenario.ScenarioRepository;
import br.remoto.web.api.ScenarioSources;
import br.remoto.web.security.Caller;
import br.remoto.web.sim.InvalidScenarioException;
import br.remoto.web.sim.QuotaExceededException;
import br.remoto.web.sim.SimulationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Páginas Thymeleaf. As listas são fragmentos atualizados pelo htmx; o gráfico da execução
 * selecionada é desenhado com uPlot a partir da API (static/js/app.js).
 */
@Controller
class PageController {

    record RunRow(String runId, String scenarioName, RunStatus status, Integer percent, String created, boolean active) {
    }

    private static final DateTimeFormatter CREATED = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault());

    private final PublicScenarioCatalog catalog;
    private final ScenarioRepository scenarios;
    private final RunRepository runs;
    private final SimulationService simulations;
    private final ScenarioSources sources;

    PageController(PublicScenarioCatalog catalog, ScenarioRepository scenarios, RunRepository runs,
                   SimulationService simulations, ScenarioSources sources) {
        this.catalog = catalog;
        this.scenarios = scenarios;
        this.runs = runs;
        this.simulations = simulations;
        this.sources = sources;
    }

    @GetMapping("/")
    String index(Caller caller, Model model) {
        model.addAttribute("caller", caller);
        return "index";
    }

    @GetMapping("/ui/scenarios")
    String scenarios(Caller caller, Model model) {
        model.addAttribute("publicScenarios", catalog.list());
        model.addAttribute("mine", scenarios.list(caller.id()));
        return "fragments :: scenarios";
    }

    @PostMapping("/ui/scenarios/copy")
    String copy(Caller caller, @RequestParam String publicScenarioId, Model model) {
        catalog.find(publicScenarioId).ifPresent(s -> scenarios.create(caller.id(), s));
        return scenarios(caller, model);
    }

    @PostMapping("/ui/scenarios/{id}/delete")
    String deleteScenario(Caller caller, @PathVariable String id, Model model) {
        scenarios.delete(caller.id(), id);
        return scenarios(caller, model);
    }

    @GetMapping("/ui/runs")
    String runs(Caller caller, Model model) {
        List<RunRow> rows = runs.list(caller.id()).stream().map(this::row).toList();
        model.addAttribute("runs", rows);
        return "fragments :: runs";
    }

    @PostMapping("/ui/runs")
    String submit(Caller caller,
                  @RequestParam(required = false) String publicScenarioId,
                  @RequestParam(required = false) String scenarioId,
                  Model model) {
        try {
            simulations.submit(caller.id(), sources.resolve(caller,
                    new ScenarioSources.RunRequest(null, scenarioId, publicScenarioId, null)));
        } catch (QuotaExceededException | InvalidScenarioException | NoSuchElementException | IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
        }
        return runs(caller, model);
    }

    @PostMapping("/ui/runs/{id}/delete")
    String deleteRun(Caller caller, @PathVariable String id, Model model) {
        if (!simulations.cancel(caller.id(), id)) {
            runs.delete(caller.id(), id);
        }
        return runs(caller, model);
    }

    private RunRow row(RunManifest m) {
        Integer percent = simulations.liveProgress(m.runId()).map(f -> (int) Math.round(f * 100)).orElse(null);
        return new RunRow(m.runId(), m.scenarioName(), m.status(), percent, CREATED.format(m.createdAt()),
                simulations.isActive(m.runId()));
    }
}
