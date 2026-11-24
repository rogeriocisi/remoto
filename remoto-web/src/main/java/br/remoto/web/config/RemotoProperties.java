package br.remoto.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

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
        @DefaultValue({"en", "pt"}) List<String> languages,
        @DefaultValue Simulation simulation,
        @DefaultValue Retention retention) {

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
     * @param areaDays   áreas sem uso (cenários e simulações de um navegador) são apagadas depois
     *                   deste prazo; o cookie que identifica a área dura o mesmo
     */
    public record Retention(@DefaultValue("30") int runDays, @DefaultValue("30") int areaDays) {
    }
}
