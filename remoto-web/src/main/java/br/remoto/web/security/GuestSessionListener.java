package br.remoto.web.security;

import br.remoto.storage.UserId;
import br.remoto.storage.account.ProfileRepository;
import br.remoto.web.sim.SimulationService;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.stereotype.Component;

/** Quando a sessão de um convidado termina, cancela suas simulações e apaga sua área temporária. */
@Component
class GuestSessionListener implements HttpSessionListener {

    private final SimulationService simulations;
    private final ProfileRepository profiles;

    GuestSessionListener(SimulationService simulations, ProfileRepository profiles) {
        this.simulations = simulations;
        this.profiles = profiles;
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        if (event.getSession().getAttribute(CallerResolver.GUEST_ATTRIBUTE) instanceof String id) {
            UserId guest = new UserId(id);
            simulations.cancelAll(guest);
            profiles.deleteAccount(guest);
        }
    }
}
