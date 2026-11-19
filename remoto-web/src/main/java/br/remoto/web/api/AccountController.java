package br.remoto.web.api;

import br.remoto.storage.account.Profile;
import br.remoto.storage.account.ProfileRepository;
import br.remoto.web.security.Caller;
import br.remoto.web.sim.SimulationService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.Instant;

/** Conta do usuário: perfil, exportação de todos os dados e exclusão definitiva (LGPD). */
@RestController
@RequestMapping("/api/me")
class AccountController {

    record Me(String id, String name, boolean guest, long diskUsageBytes) {
    }

    private final ProfileRepository profiles;
    private final SimulationService simulations;

    AccountController(ProfileRepository profiles, SimulationService simulations) {
        this.profiles = profiles;
        this.simulations = simulations;
    }

    @GetMapping
    Me me(Caller caller) {
        if (!caller.guest()) {
            profiles.getOrCreate(caller.id(), () -> new Profile(caller.displayName(), null, caller.issuer(), Instant.now()));
        }
        return new Me(caller.id().value(), caller.displayName(), caller.guest(), profiles.diskUsageBytes(caller.id()));
    }

    @GetMapping(path = "/export", produces = "application/zip")
    ResponseEntity<StreamingResponseBody> export(Caller caller) {
        caller.requireLogin();
        StreamingResponseBody body = out -> profiles.exportZip(caller.id(), out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"remoto-meus-dados.zip\"")
                .body(body);
    }

    @DeleteMapping
    ResponseEntity<Void> delete(Caller caller, HttpServletRequest request) throws ServletException {
        caller.requireLogin();
        simulations.cancelAll(caller.id());
        profiles.deleteAccount(caller.id());
        request.logout();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }
}
