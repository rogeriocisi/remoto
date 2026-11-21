package br.remoto.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/** Configurações {@code remoto.*} (application.yml ou variáveis de ambiente REMOTO_*). */
@ConfigurationProperties("remoto")
public record RemotoProperties(
        @DefaultValue("./data") String dataDir,
        @DefaultValue("./scenarios") String publicScenariosDir,
        @DefaultValue Simulation simulation,
        @DefaultValue Retention retention,
        @DefaultValue Dev dev) {

    /**
     * @param slots            simulações simultâneas no servidor; 0 = número de núcleos da CPU
     * @param maxActivePerUser quantas um mesmo usuário pode ter rodando ao mesmo tempo
     * @param maxQueuedPerUser quantas um mesmo usuário pode ter esperando na fila
     * @param threadsPerRun    threads do motor original em cada simulação; 0 = núcleos ÷ slots,
     *                         para que as simulações simultâneas não disputem a CPU
     */
    public record Simulation(
            @DefaultValue("0") int slots,
            @DefaultValue("2") int maxActivePerUser,
            @DefaultValue("20") int maxQueuedPerUser,
            @DefaultValue("0") int threadsPerRun) {

        public int effectiveSlots() {
            return slots > 0 ? slots : Runtime.getRuntime().availableProcessors();
        }

        public int effectiveThreadsPerRun() {
            if (threadsPerRun > 0) {
                return threadsPerRun;
            }
            return Math.max(1, Runtime.getRuntime().availableProcessors() / effectiveSlots());
        }
    }

    /**
     * @param runDays    execuções terminadas (e não fixadas) são apagadas depois deste prazo
     * @param guestHours áreas de convidado abandonadas são apagadas depois deste prazo
     */
    public record Retention(@DefaultValue("30") int runDays, @DefaultValue("24") int guestHours) {
    }

    /** Usuários locais, só para desenvolvimento (perfil "dev"). */
    public record Dev(List<DevUser> users) {
        public Dev {
            users = users == null ? List.of() : List.copyOf(users);
        }
    }

    public record DevUser(String username, String password) {
    }
}
