package br.remoto.web.api;

import br.remoto.storage.area.AreaRepository;
import br.remoto.web.security.Caller;
import br.remoto.web.sim.SimulationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import br.remoto.web.security.CallerResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** A área do navegador: quanto ocupa, todos os arquivos num .zip e apagar tudo agora. */
@Tag(name = "Area", description = "The caller's own area: what is in it, exporting it, erasing it")
@RestController
@RequestMapping("/api/me")
class AreaController {

    record Area(String id, long diskUsageBytes) {
    }

    private final AreaRepository areas;
    private final SimulationService simulations;

    AreaController(AreaRepository areas, SimulationService simulations) {
        this.areas = areas;
        this.simulations = simulations;
    }

    @GetMapping
    Area area(Caller caller) {
        return new Area(caller.id().value(), areas.diskUsageBytes(caller.id()));
    }

    /** Cenários e execuções da sessão, como estão gravados. */
    @GetMapping(path = "/export", produces = "application/zip")
    ResponseEntity<StreamingResponseBody> export(Caller caller) {
        StreamingResponseBody body = out -> areas.exportZip(caller.id(), out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ScenarioController.attachment("remoto-sessao.zip"))
                .body(body);
    }

    /** Cancela as simulações, apaga a área e a esquece neste navegador, sem esperar a limpeza. */
    @DeleteMapping
    ResponseEntity<Void> delete(Caller caller, HttpServletRequest request, HttpServletResponse response) {
        simulations.cancelAll(caller.id());
        areas.delete(caller.id());
        CallerResolver.clear(request, response);
        return ResponseEntity.noContent().build();
    }
}
