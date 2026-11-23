package br.remoto.web.ui;

import br.remoto.storage.run.RunManifest;
import br.remoto.storage.run.RunRepository;
import br.remoto.storage.run.RunStatus;
import br.remoto.storage.scenario.PublicScenarioCatalog;
import br.remoto.storage.scenario.ScenarioRepository;
import br.remoto.web.api.ScenarioFiles;
import br.remoto.web.api.ScenarioSources;
import br.remoto.web.security.Caller;
import br.remoto.web.sim.InvalidScenarioException;
import br.remoto.web.sim.QuotaExceededException;
import br.remoto.web.sim.SimulationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
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

    record RunRow(String runId, String scenarioName, RunStatus status, String statusLabel, Integer percent,
                  String created, String elapsed, boolean active) {
    }

    private static final DateTimeFormatter CREATED = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault());

    private final PublicScenarioCatalog catalog;
    private final ScenarioRepository scenarios;
    private final RunRepository runs;
    private final SimulationService simulations;
    private final ScenarioSources sources;
    private final ScenarioFiles files;
    private final int areaDays;

    PageController(PublicScenarioCatalog catalog, ScenarioRepository scenarios, RunRepository runs,
                   SimulationService simulations, ScenarioSources sources, ScenarioFiles files,
                   @Value("${remoto.retention.area-days:30}") int areaDays) {
        this.files = files;
        this.areaDays = areaDays;
        this.catalog = catalog;
        this.scenarios = scenarios;
        this.runs = runs;
        this.simulations = simulations;
        this.sources = sources;
    }

    @GetMapping("/")
    String index(Caller caller, Model model) {
        model.addAttribute("areaDays", areaDays);
        return "index";
    }

    /** As principais publicações sobre o ReMoto, e como citá-las. */
    @GetMapping("/publicacoes")
    String publications() {
        return "publicacoes";
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
        model.addAttribute("aba", "meus"); // mostra a cópia
        return scenarios(caller, model);
    }

    /**
     * Editar um cenário público: cenários públicos são de todos e só leitura, então o editor abre
     * numa cópia em "Meus cenários", criada aqui. O público continua intacto.
     */
    @PostMapping("/ui/scenarios/edit-copy")
    String editCopy(Caller caller, @RequestParam String publicScenarioId) {
        return catalog.find(publicScenarioId)
                .map(s -> "redirect:/editor/" + scenarios.create(caller.id(), s).value().id())
                .orElse("redirect:/");
    }

    /** Importa arquivos .json e .zip para "Meus cenários" e mostra o que entrou e o que não. */
    @PostMapping(path = "/ui/scenarios/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    String importFiles(Caller caller, @RequestParam(name = "files", required = false) List<MultipartFile> uploads,
                       Model model) {
        ScenarioFiles.Imported imported = files.importFiles(caller.id(), uploads);
        model.addAttribute("imported", imported.created().size());
        model.addAttribute("importErrors", imported.errors());
        model.addAttribute("aba", "meus");
        return scenarios(caller, model);
    }

    @PostMapping("/ui/scenarios/{id}/delete")
    String deleteScenario(Caller caller, @PathVariable String id, Model model) {
        scenarios.delete(caller.id(), id);
        model.addAttribute("aba", "meus");
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
                  Model model, jakarta.servlet.http.HttpServletResponse response) {
        try {
            RunManifest m = simulations.submit(caller.id(), caller.origin(), sources.resolve(caller,
                    new ScenarioSources.RunRequest(null, scenarioId, publicScenarioId)));
            // a página abre a simulação nova na área de resultados
            response.setHeader("HX-Trigger", "{\"abrir-execucao\": \"" + m.runId() + "\"}");
        } catch (QuotaExceededException | InvalidScenarioException | NoSuchElementException | IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
        }
        return runs(caller, model);
    }

    /**
     * Importa uma simulação exportada (.zip). Devolve só a mensagem; o evento "execucoes" faz a
     * lista, que se atualiza a cada poucos segundos, se atualizar na hora.
     */
    @PostMapping(path = "/ui/runs/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    String importRun(Caller caller, @RequestParam(name = "file", required = false) MultipartFile file, Model model,
                     jakarta.servlet.http.HttpServletResponse response) {
        if (file == null || file.isEmpty()) {
            model.addAttribute("importError", "nenhum arquivo enviado");
        } else {
            try (var in = file.getInputStream()) {
                RunManifest m = runs.importZip(caller.id(), in);
                model.addAttribute("importedRun", m.scenarioName());
                response.setHeader("HX-Trigger", "execucoes");
            } catch (IllegalArgumentException | java.io.IOException e) {
                model.addAttribute("importError", file.getOriginalFilename() + ": " + e.getMessage());
            }
        }
        return "fragments :: runImport";
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
        return new RunRow(m.runId(), m.scenarioName(), m.status(), label(m.status()), percent,
                CREATED.format(m.createdAt()), elapsed(m), simulations.isActive(m.runId()));
    }

    static String label(RunStatus status) {
        return switch (status) {
            case QUEUED -> "na fila";
            case RUNNING -> "rodando";
            case DONE -> "concluída";
            case FAILED -> "falhou";
            case CANCELED -> "cancelada";
        };
    }

    /** Do início ao fim da simulação (ou até agora, se ainda roda); "—" se não chegou a começar. */
    static String elapsed(RunManifest m) {
        if (m.startedAt() == null) {
            return "—";
        }
        Instant end = m.finishedAt() != null ? m.finishedAt() : Instant.now();
        long s = Math.max(0, Duration.between(m.startedAt(), end).toSeconds());
        if (s < 60) {
            return s + " s";
        }
        if (s < 3600) {
            return String.format("%d min %02d s", s / 60, s % 60);
        }
        return String.format("%d h %02d min", s / 3600, (s % 3600) / 60);
    }
}
