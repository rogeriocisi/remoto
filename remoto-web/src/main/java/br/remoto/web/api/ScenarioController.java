package br.remoto.web.api;

import br.remoto.core.scenario.Scenario;
import br.remoto.core.scenario.ScenarioValidator;
import br.remoto.storage.Versioned;
import br.remoto.storage.scenario.PublicScenarioCatalog;
import br.remoto.storage.scenario.ScenarioRepository;
import br.remoto.storage.scenario.ShareRepository;
import br.remoto.web.security.Caller;
import br.remoto.web.sim.InvalidScenarioException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** Cenários públicos (só leitura) e cenários do usuário (CRUD com ETag/If-Match). */
@RestController
@RequestMapping("/api/scenarios")
class ScenarioController {

    private final ScenarioRepository scenarios;
    private final PublicScenarioCatalog catalog;
    private final ShareRepository shares;

    ScenarioController(ScenarioRepository scenarios, PublicScenarioCatalog catalog, ShareRepository shares) {
        this.scenarios = scenarios;
        this.catalog = catalog;
        this.shares = shares;
    }

    @GetMapping("/public")
    List<Scenario> publicScenarios() {
        return catalog.list();
    }

    @GetMapping("/public/{id}")
    Scenario publicScenario(@PathVariable String id) {
        return catalog.find(id).orElseThrow(() -> new NoSuchElementException("cenário público não encontrado"));
    }

    /** Copia um cenário público para a área do usuário, onde ele pode editar. */
    @PostMapping("/public/{id}/copy")
    ResponseEntity<Scenario> copyPublic(Caller caller, @PathVariable String id) {
        return created(scenarios.create(caller.id(), publicScenario(id)));
    }

    @GetMapping
    List<Scenario> mine(Caller caller) {
        return scenarios.list(caller.id());
    }

    @GetMapping("/{id}")
    ResponseEntity<Scenario> get(Caller caller, @PathVariable String id) {
        Versioned<Scenario> v = find(caller, id);
        return ResponseEntity.ok().eTag(v.etag()).body(v.value());
    }

    @PostMapping
    ResponseEntity<Scenario> create(Caller caller, @RequestBody Scenario scenario) {
        validate(scenario);
        return created(scenarios.create(caller.id(), scenario));
    }

    @PutMapping("/{id}")
    ResponseEntity<Scenario> update(Caller caller, @PathVariable String id,
                                    @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                    @RequestBody Scenario scenario) {
        Scenario withId = scenario.withId(id);
        validate(withId);
        Versioned<Scenario> v = scenarios.update(caller.id(), withId, Etags.parse(ifMatch));
        return ResponseEntity.ok().eTag(v.etag()).body(v.value());
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(Caller caller, @PathVariable String id) {
        return scenarios.delete(caller.id(), id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /** Gera um link imutável para o cenário (exige login). */
    @PostMapping("/{id}/share")
    Map<String, String> share(Caller caller, @PathVariable String id) {
        caller.requireLogin();
        String hash = shares.share(find(caller, id).value());
        return Map.of("hash", hash, "url", "/api/shares/" + hash);
    }

    private Versioned<Scenario> find(Caller caller, String id) {
        return scenarios.find(caller.id(), id).orElseThrow(() -> new NoSuchElementException("cenário não encontrado"));
    }

    private static void validate(Scenario scenario) {
        List<String> errors = ScenarioValidator.validate(scenario);
        if (!errors.isEmpty()) {
            throw new InvalidScenarioException(errors);
        }
    }

    static ResponseEntity<Scenario> created(Versioned<Scenario> v) {
        return ResponseEntity.created(URI.create("/api/scenarios/" + v.value().id())).eTag(v.etag()).body(v.value());
    }
}
