package br.remoto.web.api;

import br.remoto.core.scenario.Scenario;
import br.remoto.storage.run.ResultFiles;
import br.remoto.storage.run.RunManifest;
import br.remoto.storage.run.RunRepository;
import br.remoto.storage.run.RunStatus;
import br.remoto.web.i18n.Messages;
import br.remoto.web.security.Caller;
import br.remoto.web.sim.ProgressHub;
import br.remoto.web.sim.SimulationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;

/** Execuções: enviar, acompanhar (SSE), cancelar, baixar resultados em CSV ou PNG. */
@Tag(name = "Runs", description = "Submitting simulations, following them, and downloading their results")
@RestController
@RequestMapping("/api/runs")
class RunController {

    record RunView(RunManifest run, Double progress, List<String> results) {
    }

    private final SimulationService simulations;
    private final RunRepository runs;
    private final ProgressHub progressHub;
    private final ScenarioSources sources;
    private final FigureRenderer figures;

    RunController(SimulationService simulations, RunRepository runs, ProgressHub progressHub,
                  ScenarioSources sources, FigureRenderer figures) {
        this.simulations = simulations;
        this.runs = runs;
        this.progressHub = progressHub;
        this.sources = sources;
        this.figures = figures;
    }

    @PostMapping
    ResponseEntity<RunManifest> submit(Caller caller, @RequestBody ScenarioSources.RunRequest request) {
        RunManifest manifest = simulations.submit(caller.id(), caller.origin(), sources.resolve(caller, request));
        return ResponseEntity.accepted().location(URI.create("/api/runs/" + manifest.runId())).body(manifest);
    }

    @GetMapping
    List<RunView> list(Caller caller) {
        return runs.list(caller.id()).stream()
                .map(m -> new RunView(m, simulations.liveProgress(m.runId()).orElse(null), null))
                .toList();
    }

    @GetMapping("/{id}")
    RunView get(Caller caller, @PathVariable String id) {
        RunManifest m = find(caller, id);
        return new RunView(m, simulations.liveProgress(id).orElse(null), runs.resultNames(caller.id(), id));
    }

    @GetMapping(path = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(Caller caller, @PathVariable String id) {
        find(caller, id);
        return progressHub.subscribe(id, () -> runs.find(caller.id(), id).map(RunManifest::status).orElse(RunStatus.FAILED));
    }

    @GetMapping("/{id}/config")
    Scenario config(Caller caller, @PathVariable String id) {
        return runs.config(caller.id(), id).orElseThrow(() -> new NoSuchElementException(Messages.msg("run.notFound")));
    }

    /** Ativa: cancela (202). Terminada: apaga (204). */
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(Caller caller, @PathVariable String id) {
        RunManifest m = find(caller, id);
        if (!m.status().isTerminal() && simulations.cancel(caller.id(), id)) {
            return ResponseEntity.accepted().build();
        }
        runs.delete(caller.id(), id);
        return ResponseEntity.noContent().build();
    }

    /** Execuções fixadas não são apagadas pela retenção automática. */
    @PutMapping("/{id}/pinned")
    RunManifest pin(Caller caller, @PathVariable String id, @RequestBody boolean pinned) {
        RunManifest m = find(caller, id).withPinned(pinned);
        runs.save(caller.id(), m);
        return m;
    }

    @GetMapping(path = "/{id}/results/{name}.csv", produces = "text/csv")
    ResponseEntity<Resource> csv(Caller caller, @PathVariable String id, @PathVariable String name) throws IOException {
        Path dir = resultsDir(caller, id, name);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(new InputStreamResource(ResultFiles.openCsv(dir, name)));
    }

    /** Figura para artigos e relatórios (JFreeChart, no servidor). */
    @GetMapping(path = "/{id}/results/{name}.png", produces = MediaType.IMAGE_PNG_VALUE)
    byte[] png(Caller caller, @PathVariable String id, @PathVariable String name) {
        Path dir = resultsDir(caller, id, name);
        return figures.png(name, ResultFiles.read(dir, name, 20_000), name.startsWith("disparos-"));
    }

    record Preview(String header, int totalPoints, double[] x, double[] y) {
    }

    /**
     * Prévia reduzida para gráficos no navegador: execuções do motor original podem ter
     * centenas de milhares de pontos por série; o CSV completo continua disponível.
     */
    @GetMapping("/{id}/results/{name}/preview")
    Preview preview(Caller caller, @PathVariable String id, @PathVariable String name,
                    @org.springframework.web.bind.annotation.RequestParam(defaultValue = "5000") int max) {
        Path dir = resultsDir(caller, id, name);
        ResultFiles.Columns reduced = ResultFiles.read(dir, name, Math.max(100, Math.min(max, 50_000)));
        return new Preview(reduced.header(), reduced.totalPoints(), reduced.x(), reduced.y());
    }

    /** Traz de volta uma execução exportada (.zip), com um id novo, para ver os resultados de novo. */
    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<RunManifest> importZip(Caller caller, @RequestParam("file") MultipartFile file) throws IOException {
        try (var in = file.getInputStream()) {
            RunManifest m = runs.importZip(caller.id(), in);
            return ResponseEntity.created(URI.create("/api/runs/" + m.runId())).body(m);
        }
    }

    /** A execução inteira num .zip: manifesto, configuração e todos os resultados em CSV. */
    @GetMapping(path = "/{id}/export.zip", produces = "application/zip")
    ResponseEntity<StreamingResponseBody> exportZip(Caller caller, @PathVariable String id) {
        RunManifest m = find(caller, id);
        StreamingResponseBody body = out -> runs.exportZip(caller.id(), m.runId(), out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ScenarioController.attachment("remoto-" + m.runId() + ".zip"))
                .body(body);
    }

    private RunManifest find(Caller caller, String id) {
        return runs.find(caller.id(), id).orElseThrow(() -> new NoSuchElementException(Messages.msg("run.notFound")));
    }

    /** Só aceita nomes que existem de fato na execução do próprio usuário. */
    private Path resultsDir(Caller caller, String id, String name) {
        find(caller, id);
        if (!runs.resultNames(caller.id(), id).contains(name)) {
            throw new NoSuchElementException(Messages.msg("result.notFound"));
        }
        return runs.resultsDir(caller.id(), id);
    }
}
