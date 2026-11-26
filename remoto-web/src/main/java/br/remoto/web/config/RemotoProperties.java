package br.remoto.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

import java.util.List;

/**
 * Configurações {@code remoto.*} (application.yml ou variáveis de ambiente REMOTO_*).
 *
 * @param languages os idiomas em que as páginas saem, na ordem: o primeiro é o padrão (para quem
 *                  não escolheu e não pede nenhum dos outros) e fica na raiz da aplicação; cada um
 *                  dos outros ganha uma porta de entrada com o seu código ({@code /pt}), servindo
 *                  a mesma página com os textos do seu {@code messages_<código>.properties}.
 *                  Acrescentar um idioma é acrescentar o código aqui e o arquivo de textos
 *                  completo — o {@code MessagesTest} falha enquanto faltar uma chave —, sem página
 *                  nem rota nova.
 */
@ConfigurationProperties("remoto")
public record RemotoProperties(
        @DefaultValue("./data") String dataDir,
        @DefaultValue("./scenarios") String publicScenariosDir,
        @DefaultValue("./docs") String siteDir,
        @DefaultValue({"en", "pt"}) List<String> languages,
        @DefaultValue Simulation simulation,
        @DefaultValue Retention retention,
        @DefaultValue Storage storage,
        @DefaultValue Limits limits) {

    /**
     * @param slots            simulações simultâneas no servidor; 0 = número de núcleos da CPU
     * @param maxActivePerUser quantas uma mesma sessão pode ter rodando ao mesmo tempo
     * @param maxQueuedPerUser quantas uma mesma sessão pode ter esperando na fila
     * @param maxPerOrigin     quantas um mesmo endereço de origem pode ter rodando ou na fila,
     *                         somando as sessões (sem login, abrir outra sessão é fácil); 0 = sem limite
     * @param maxQueuedTotal   quantas o servidor aceita esperando na fila, somando todos; 0 = sem limite
     * @param threadsPerRun    threads do motor original em cada simulação; 0 = núcleos ÷ slots,
     *                         para que as simulações simultâneas não disputem a CPU
     */
    public record Simulation(
            @DefaultValue("0") int slots,
            @DefaultValue("2") int maxActivePerUser,
            @DefaultValue("20") int maxQueuedPerUser,
            @DefaultValue("20") int maxPerOrigin,
            @DefaultValue("200") int maxQueuedTotal,
            @DefaultValue("0") int threadsPerRun,
            @DefaultValue("0.8") double memoryFraction) {

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
     * @param areaDays   áreas sem uso (cenários e simulações de um navegador) são apagadas depois
     *                   deste prazo; o cookie que identifica a área dura o mesmo
     */
    public record Retention(@DefaultValue("30") int runDays, @DefaultValue("30") int areaDays) {
    }

    /**
     * Quanto o disco pode crescer (ver {@code StorageQuota}). Zero, o padrão, é sem limite: num
     * computador pessoal não faz sentido limitar. Num servidor aberto faz, e aí os dois valem
     * juntos — o primeiro segura o uso exagerado de quem está de boa-fé, e o segundo protege a
     * máquina de quem não está, já que um cookie novo é uma área nova.
     *
     * @param maxAreaBytes  o que uma área (um navegador) pode ocupar; 0 = sem limite
     * @param maxTotalBytes o que todas as áreas somadas podem ocupar; 0 = sem limite
     */
    public record Storage(
            @DefaultValue("0") DataSize maxAreaBytes,
            @DefaultValue("0") DataSize maxTotalBytes) {
    }

    /**
     * Limites por endereço de quem chama, e não por área: sem login, trocar de cookie é trocar
     * de área, mas o endereço continua o mesmo (ver {@code ApiWriteRateFilter}).
     *
     * @param apiWritesPerMinute escritas da API aceitas por minuto do mesmo endereço; 0 = sem limite
     */
    public record Limits(@DefaultValue("0") int apiWritesPerMinute) {
    }
}
