package br.remoto.web.sim;

import br.remoto.core.engine.ResultSink;
import br.remoto.core.engine.RunContext;
import br.remoto.core.engine.SimulationCancelledException;
import br.remoto.core.engine.Engines;
import br.remoto.core.engine.SimulationEngine;
import br.remoto.core.scenario.Scenario;
import br.remoto.core.scenario.ScenarioValidator;
import br.remoto.storage.UserId;
import br.remoto.storage.run.CsvGzResultSink;
import br.remoto.storage.run.RunManifest;
import br.remoto.storage.run.RunRepository;
import br.remoto.storage.run.RunStatus;
import br.remoto.web.config.RemotoProperties;
import br.remoto.web.i18n.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executa simulações de vários usuários ao mesmo tempo.
 *
 * <ul>
 *   <li>Até {@code slots} simulações em paralelo no servidor (padrão: núcleos da CPU), cada uma
 *       numa thread de plataforma, porque o trabalho é de CPU.</li>
 *   <li>Fila justa por sessão ({@link FairQueue}) com limite de ativas e de enfileiradas.</li>
 *   <li>Sem login, abrir outra sessão é fácil: há também um limite por endereço de origem,
 *       somando as sessões, e um teto para a fila inteira.</li>
 *   <li>Estado durável no manifest.json de cada execução; ao reiniciar, execuções que estavam
 *       na fila ou rodando são marcadas como interrompidas.</li>
 * </ul>
 */
@Service
public class SimulationService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    private final RunRepository runs;
    private final Engines engines;
    private final ProgressHub progressHub;
    private final int slots;
    private final int maxActivePerUser;
    private final int maxQueuedPerUser;
    private final int maxPerOrigin;
    private final int maxQueuedTotal;
    private final ExecutorService workers;
    private final MemoryGuard memory;

    private final Object lock = new Object();
    private final FairQueue<Job> queue = new FairQueue<>();
    private final Map<UserId, Integer> active = new HashMap<>();
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private int running;

    record Job(UserId owner, String origin, String runId, Scenario scenario, SimulationEngine engine, AtomicBoolean cancelled) {
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SimulationService(RunRepository runs, Engines engines, ProgressHub progressHub,
                             RemotoProperties props) {
        this(runs, engines, progressHub, props, MemoryGuard.ofJvm(props.simulation().memoryFraction()));
    }

    SimulationService(RunRepository runs, Engines engines, ProgressHub progressHub,
                      RemotoProperties props, MemoryGuard memory) {
        this.memory = memory;
        this.runs = runs;
        this.engines = engines;
        this.progressHub = progressHub;
        this.slots = props.simulation().effectiveSlots();
        this.maxActivePerUser = Math.max(1, props.simulation().maxActivePerUser());
        this.maxQueuedPerUser = Math.max(1, props.simulation().maxQueuedPerUser());
        this.maxPerOrigin = props.simulation().maxPerOrigin();
        this.maxQueuedTotal = props.simulation().maxQueuedTotal();
        this.workers = Executors.newFixedThreadPool(slots, Thread.ofPlatform().name("simulacao-", 0).factory());
        recoverInterruptedRuns();
        log.info("Fila de simulações: {} em paralelo; por sessão, até {} ativas e {} na fila; "
                + "por origem, até {} (0 = sem limite); no total, até {} na fila (0 = sem limite)",
                slots, maxActivePerUser, maxQueuedPerUser, maxPerOrigin, maxQueuedTotal);
        if (memory.enabled()) {
            log.info("Vigia de memória: uma simulação é interrompida se for passar de {} MB de heap",
                    memory.limitBytes() / (1024 * 1024));
        } else {
            log.info("Vigia de memória desligado");
        }
    }

    /**
     * @param origin endereço de quem pediu, para o limite por origem; null não conta
     */
    public RunManifest submit(UserId owner, String origin, Scenario scenario) {
        List<String> errors = ScenarioValidator.validate(scenario);
        if (!errors.isEmpty()) {
            throw new InvalidScenarioException(errors);
        }
        SimulationEngine engine = engines.forScenario(scenario);
        synchronized (lock) {
            if (queue.queued(owner) >= maxQueuedPerUser) {
                throw new QuotaExceededException(Messages.msg("quota.queuedPerUser", maxQueuedPerUser));
            }
            if (maxPerOrigin > 0 && origin != null
                    && jobs.values().stream().filter(j -> origin.equals(j.origin())).count() >= maxPerOrigin) {
                throw new QuotaExceededException(Messages.msg("quota.perOrigin", maxPerOrigin));
            }
            if (maxQueuedTotal > 0 && jobs.size() - running >= maxQueuedTotal) {
                throw new QuotaExceededException(Messages.msg("quota.queueFull"));
            }
            RunManifest manifest = runs.create(owner, scenario, engine.version());
            Job job = new Job(owner, origin, manifest.runId(), scenario, engine, new AtomicBoolean());
            jobs.put(job.runId(), job);
            queue.offer(owner, job);
            progressHub.publish(job.runId(), RunStatus.QUEUED, 0);
            dispatch();
            return manifest;
        }
    }

    /** @return false se a execução não está ativa (já terminou) ou não é do usuário */
    public boolean cancel(UserId owner, String runId) {
        Job job = jobs.get(runId);
        if (job == null || !job.owner().equals(owner)) {
            return false;
        }
        job.cancelled().set(true);
        boolean wasQueued;
        synchronized (lock) {
            wasQueued = queue.remove(owner, j -> j.runId().equals(runId));
        }
        if (wasQueued) {
            jobs.remove(runId);
            finish(job, RunStatus.CANCELED, null);
        }
        return true;
    }

    public void cancelAll(UserId owner) {
        jobs.values().stream()
                .filter(j -> j.owner().equals(owner))
                .map(Job::runId)
                .toList()
                .forEach(runId -> cancel(owner, runId));
    }

    public boolean isActive(String runId) {
        return jobs.containsKey(runId);
    }

    public Optional<Double> liveProgress(String runId) {
        return progressHub.current(runId).map(ProgressHub.Progress::fraction);
    }

    /** Chamado com {@code lock}: ocupa as vagas livres com o próximo job de cada usuário. */
    private void dispatch() {
        while (running < slots) {
            Optional<FairQueue.Entry<Job>> next = queue.poll(u -> active.getOrDefault(u, 0), maxActivePerUser);
            if (next.isEmpty()) {
                return;
            }
            Job job = next.get().job();
            running++;
            active.merge(job.owner(), 1, Integer::sum);
            workers.execute(() -> execute(job));
        }
    }

    private void execute(Job job) {
        try {
            runJob(job);
        } catch (RuntimeException e) {
            log.error("Erro inesperado na execução {}", job.runId(), e);
            fail(job, "erro interno: " + e.getMessage());
        } catch (OutOfMemoryError e) {
            // O vigia de memória para a simulação antes disto; se mesmo assim faltar memória (na
            // montagem da rede, por exemplo, antes do primeiro aviso de progresso), a execução
            // termina como falha e a thread continua servindo a fila. O que a simulação ocupava
            // já não tem referência aqui, e a próxima coleta devolve.
            log.error("Faltou memória na execução {}", job.runId());
            fail(job, MemoryGuard.PREFIX + " the server ran out of memory");
        } finally {
            jobs.remove(job.runId());
            synchronized (lock) {
                running--;
                active.computeIfPresent(job.owner(), (u, n) -> n > 1 ? n - 1 : null);
                dispatch();
            }
        }
    }

    /** Não deixa a execução presa em QUEUED/RUNNING. */
    private void fail(Job job, String error) {
        try {
            finish(job, RunStatus.FAILED, error);
        } catch (RuntimeException | OutOfMemoryError again) {
            log.error("Não foi possível marcar a execução {} como FAILED", job.runId(), again);
        }
    }

    private void runJob(Job job) {
        if (job.cancelled().get()) {
            finish(job, RunStatus.CANCELED, null);
            return;
        }
        Optional<RunManifest> current = runs.find(job.owner(), job.runId());
        if (current.isEmpty()) {
            return; // apagada antes de começar
        }
        runs.save(job.owner(), current.get().running(Instant.now()));
        progressHub.publish(job.runId(), RunStatus.RUNNING, 0);

        ResultSink sink = new CsvGzResultSink(runs.resultsDir(job.owner(), job.runId()));
        MemoryGuard.Watch watch = memory.watch();
        java.util.concurrent.atomic.AtomicReference<MemoryGuard.Verdict> outOfMemory =
                new java.util.concurrent.atomic.AtomicReference<>();
        RunContext context = new RunContext() {
            @Override
            public void progress(double fraction) {
                progressHub.publish(job.runId(), RunStatus.RUNNING, fraction);
                if (outOfMemory.get() == null) {
                    boolean alone;
                    synchronized (lock) {
                        alone = running <= 1;
                    }
                    MemoryGuard.Verdict verdict = watch.check(fraction, alone);
                    if (verdict != null) {
                        outOfMemory.set(verdict);
                    }
                }
            }

            @Override
            public boolean isCancelled() {
                // sem memória para continuar, o motor para pelo mesmo caminho do cancelamento
                return job.cancelled().get() || outOfMemory.get() != null;
            }

            @Override
            public ResultSink results() {
                return sink;
            }
        };

        long start = System.nanoTime();
        try {
            job.engine().run(job.scenario(), context);
            finish(job, RunStatus.DONE, null);
            log.info("Execução {} concluída em {} ms", job.runId(), (System.nanoTime() - start) / 1_000_000);
        } catch (SimulationCancelledException e) {
            MemoryGuard.Verdict verdict = outOfMemory.get();
            if (verdict != null && !job.cancelled().get()) {
                log.warn("Execução {} interrompida por memória: {}", job.runId(), verdict.message());
                finish(job, RunStatus.FAILED, verdict.message());
            } else {
                finish(job, RunStatus.CANCELED, null);
            }
        } catch (RuntimeException e) {
            log.warn("Execução {} falhou: {}", job.runId(), e.toString());
            finish(job, RunStatus.FAILED, e.getMessage());
        }
    }

    private void finish(Job job, RunStatus status, String error) {
        // relê o manifesto para não perder alterações feitas durante a execução (ex.: fixar)
        runs.find(job.owner(), job.runId())
                .ifPresent(m -> runs.save(job.owner(), m.finished(status, Instant.now(), error)));
        progressHub.publish(job.runId(), status, status == RunStatus.DONE ? 1 : 0);
    }

    private void recoverInterruptedRuns() {
        List<RunManifest> interrupted = runs.findAllByStatus(EnumSet.of(RunStatus.QUEUED, RunStatus.RUNNING));
        for (RunManifest m : interrupted) {
            runs.save(new UserId(m.ownerId()),
                    m.finished(RunStatus.FAILED, Instant.now(), "interrompida: o servidor foi reiniciado"));
        }
        if (!interrupted.isEmpty()) {
            log.warn("{} execuções interrompidas pelo reinício foram marcadas como FAILED", interrupted.size());
        }
    }

    @Override
    public void destroy() throws InterruptedException {
        jobs.values().forEach(j -> j.cancelled().set(true));
        workers.shutdown();
        if (!workers.awaitTermination(10, TimeUnit.SECONDS)) {
            workers.shutdownNow();
        }
    }
}
