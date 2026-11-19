package br.remoto.web.api;

import br.remoto.core.scenario.Scenario;
import br.remoto.storage.scenario.ScenarioRepository;
import br.remoto.storage.scenario.ShareRepository;
import br.remoto.web.security.Caller;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

/** Cenários compartilhados por link: qualquer pessoa lê; qualquer pessoa pode copiar para si. */
@RestController
@RequestMapping("/api/shares")
class ShareController {

    private final ShareRepository shares;
    private final ScenarioRepository scenarios;

    ShareController(ShareRepository shares, ScenarioRepository scenarios) {
        this.shares = shares;
        this.scenarios = scenarios;
    }

    @GetMapping("/{hash}")
    Scenario get(@PathVariable String hash) {
        return shares.find(hash).orElseThrow(() -> new NoSuchElementException("compartilhamento não encontrado"));
    }

    @PostMapping("/{hash}/fork")
    ResponseEntity<Scenario> fork(Caller caller, @PathVariable String hash) {
        return ScenarioController.created(scenarios.create(caller.id(), get(hash)));
    }
}
