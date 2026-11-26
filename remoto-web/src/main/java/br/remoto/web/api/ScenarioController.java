package br.remoto.web.api;

import br.remoto.core.scenario.Scenario;
import br.remoto.core.scenario.ScenarioValidator;
import br.remoto.storage.Versioned;
import br.remoto.storage.scenario.PublicScenarioCatalog;
import br.remoto.storage.scenario.ScenarioRepository;
import br.remoto.web.i18n.Messages;
import br.remoto.web.security.Caller;
import br.remoto.web.sim.InvalidScenarioException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.net.URI;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;

/**
 * Cenários públicos (só leitura) e cenários da sessão (CRUD com ETag/If-Match), com exportação e
 * importação em arquivos (.json, ou vários num .zip).
 */
@Tag(name = "Scenarios", description = "Public scenarios, and the scenarios of the caller's own area")
@RestController
@RequestMapping("/api/scenarios")
class ScenarioController {

    private final ScenarioRepository scenarios;
    private final PublicScenarioCatalog catalog;
    private final ScenarioFiles files;

    ScenarioController(ScenarioRepository scenarios, PublicScenarioCatalog catalog, ScenarioFiles files) {
        this.scenarios = scenarios;
        this.catalog = catalog;
        this.files = files;
    }

    @Operation(summary = "List the public scenarios",
            description = "The whole catalogue, with every scenario in full: a few megabytes of JSON.")
    @GetMapping("/public")
    List<Scenario> publicScenarios() {
        return catalog.list();
    }

    @Operation(summary = "One public scenario")
    @GetMapping("/public/{id}")
    Scenario publicScenario(@PathVariable String id) {
        return catalog.find(id).orElseThrow(() -> new NoSuchElementException(Messages.msg("scenario.public.notFound")));
    }

    @Operation(summary = "Download a public scenario as a .json file")
    @GetMapping("/public/{id}/export")
    ResponseEntity<byte[]> exportPublic(@PathVariable String id) {
        return download(publicScenario(id));
    }

    /** Copia um cenário público para a área da sessão, onde ele pode ser editado. */
    @Operation(summary = "Copy a public scenario into the caller's area, where it can be edited")
    @PostMapping("/public/{id}/copy")
    ResponseEntity<Scenario> copyPublic(Caller caller, @PathVariable String id) {
        return created(scenarios.create(caller.id(), publicScenario(id)));
    }

    @Operation(summary = "List the scenarios of the area")
    @GetMapping
    List<Scenario> mine(Caller caller) {
        return scenarios.list(caller.id());
    }

    @Operation(summary = "One scenario of the area, with its ETag")
    @GetMapping("/{id}")
    ResponseEntity<Scenario> get(Caller caller, @PathVariable String id) {
        Versioned<Scenario> v = find(caller, id);
        return ResponseEntity.ok().eTag(v.etag()).body(v.value());
    }

    @Operation(summary = "Create a scenario in the area",
            description = "The scenario is validated first; 422 comes back with the list of what is wrong.")
    @PostMapping
    ResponseEntity<Scenario> create(Caller caller, @RequestBody Scenario scenario) {
        validate(scenario);
        return created(scenarios.create(caller.id(), scenario));
    }

    @Operation(summary = "Replace a scenario of the area",
            description = "Send in If-Match the ETag that came with the scenario; if it changed meanwhile, the answer is 412 and nothing is written.")
    @PutMapping("/{id}")
    ResponseEntity<Scenario> update(Caller caller, @PathVariable String id,
                                    @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                    @RequestBody Scenario scenario) {
        Scenario withId = scenario.withId(id);
        validate(withId);
        Versioned<Scenario> v = scenarios.update(caller.id(), withId, Etags.parse(ifMatch));
        return ResponseEntity.ok().eTag(v.etag()).body(v.value());
    }

    @Operation(summary = "Delete a scenario of the area")
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(Caller caller, @PathVariable String id) {
        return scenarios.delete(caller.id(), id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /** O cenário como arquivo .json, para guardar ou rodar na CLI. */
    @Operation(summary = "Download a scenario as a .json file, to keep or to run in the CLI")
    @GetMapping("/{id}/export")
    ResponseEntity<byte[]> export(Caller caller, @PathVariable String id) {
        return download(find(caller, id).value());
    }

    /** Todos os cenários da sessão num .zip, um .json cada. */
    @Operation(summary = "Download every scenario of the area as a .zip, one .json each")
    @GetMapping(path = "/export", produces = "application/zip")
    ResponseEntity<StreamingResponseBody> exportAll(Caller caller) {
        List<Scenario> mine = scenarios.list(caller.id());
        StreamingResponseBody body = out -> files.writeZip(mine, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment("remoto-cenarios.zip"))
                .body(body);
    }

    /** Importa arquivos .json e .zip; devolve os cenários criados e os arquivos recusados. */
    @Operation(summary = "Import scenarios from .json and .zip files",
            description = "Answers with the scenarios created and with the files it refused, one reason each.")
    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ScenarioFiles.Imported importFiles(Caller caller, @RequestParam("files") List<MultipartFile> uploads) {
        ScenarioFiles.Imported imported = files.importFiles(caller.id(), uploads);
        if (imported.created().isEmpty()) {
            throw new InvalidScenarioException(imported.errors());
        }
        return imported;
    }

    private Versioned<Scenario> find(Caller caller, String id) {
        return scenarios.find(caller.id(), id).orElseThrow(() -> new NoSuchElementException(Messages.msg("scenario.notFound")));
    }

    private static void validate(Scenario scenario) {
        List<String> errors = ScenarioValidator.validate(scenario);
        if (!errors.isEmpty()) {
            throw new InvalidScenarioException(errors);
        }
    }

    private ResponseEntity<byte[]> download(Scenario scenario) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(ScenarioFiles.fileName(scenario)))
                .body(files.toJson(scenario));
    }

    static String attachment(String fileName) {
        return ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString();
    }

    static ResponseEntity<Scenario> created(Versioned<Scenario> v) {
        return ResponseEntity.created(URI.create("/api/scenarios/" + v.value().id())).eTag(v.etag()).body(v.value());
    }
}
