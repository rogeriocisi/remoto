package br.remoto.web.security;

import br.remoto.storage.UserId;
import br.remoto.storage.area.AreaRepository;
import br.remoto.web.sim.SimulationService;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.stereotype.Component;

/** Quando uma sessão termina, cancela suas simulações e apaga sua área. */
@Component
class SessionListener implements HttpSessionListener {

    private final SimulationService simulations;
    private final AreaRepository areas;

    SessionListener(SimulationService simulations, AreaRepository areas) {
        this.simulations = simulations;
        this.areas = areas;
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        if (event.getSession().getAttribute(CallerResolver.AREA_ATTRIBUTE) instanceof String id) {
            UserId area = new UserId(id);
            simulations.cancelAll(area);
            areas.delete(area);
        }
    }
}
