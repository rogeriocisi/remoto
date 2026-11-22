package br.remoto.core.engine.legacy;

import br.remoto.core.model.random.Rng;

import br.remoto.core.engine.ResultSink;
import br.remoto.core.engine.RunContext;
import br.remoto.core.engine.SimulationCancelledException;
import br.remoto.core.engine.SimulationEngine;
import br.remoto.core.scenario.Scenario;
import br.remoto.core.simulation.setup.Configuration;
import br.remoto.core.simulation.Simulation;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Roda os modelos originais do ReMoto (motoneurônios, interneurônios, sinapses, músculo, fuso,
 * OTG, articulação) dentro da nova plataforma.
 *
 * <p>O código numérico é o mesmo do site antigo, com duas diferenças: a aleatoriedade, agora
 * derivada da seed do cenário ({@link Rng}), o que torna cada execução reprodutível; e a
 * correção do decaimento das condutâncias sinápticas (hoje {@code SynapseGroup}, no remoto-core), que
 * desde 2016 ficavam congeladas entre um evento e outro.
 *
 * <p>Os neurônios de cada passo podem ser atualizados por várias threads ({@code NeuronUpdater}
 * no modelo), com exatamente o mesmo resultado do laço sequencial.
 */
public final class LegacyEngine implements SimulationEngine {

    /**
     * Código original "remoto2b5" + fonte de aleatoriedade com seed + correção das sinapses.
     * Resultados de versões diferentes não são comparáveis entre si.
     */
    public static final String VERSION = "remoto-legacy-2b5-rng1-syn1";

    private static final long PROGRESS_INTERVAL_MS = 250;

    private final int threads;

    /** Uma thread por núcleo da CPU para atualizar os neurônios de cada execução. */
    public LegacyEngine() {
        this(Runtime.getRuntime().availableProcessors());
    }

    /**
     * @param threads threads para atualizar os neurônios de cada execução; 1 mantém o laço
     *                sequencial original. O resultado é o mesmo, bit a bit, com qualquer valor.
     */
    public LegacyEngine(int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("threads deve ser pelo menos 1: " + threads);
        }
        this.threads = threads;
    }

    public int threads() {
        return threads;
    }

    @Override
    public String id() {
        return Scenario.ENGINE_LEGACY;
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public void run(Scenario scenario, RunContext context) {
        Configuration conf = LegacyConfigurations.load(LegacyJson.toConfig(scenario.legacy()));
        run(conf, scenario.seed(), scenario.durationMs(), scenario.stepMs(), context);
    }

    /** Também usado nos testes para rodar uma Configuration lida direto do banco original. */
    void run(Configuration conf, long seed, double durationMs, double stepMs, RunContext context) {
        conf.setTFin(durationMs);
        conf.setStep(stepMs);
        conf.setStoreResults(false);
        conf.setStoreSignals(true);    // necessário para guardar força, torque e disparos
        conf.setChangedConfiguration(true);
        conf.setKeepProperties(false);

        String cdSimulation = "remoto-" + UUID.randomUUID();
        Simulation sim = new Simulation(conf, cdSimulation);
        sim.setThreads(threads);
        AtomicBoolean finished = new AtomicBoolean();
        Thread watcher = Thread.ofVirtual().name("progresso-" + cdSimulation).start(() -> {
            while (!finished.get()) {
                context.progress(Math.min(0.99, sim.getPercentageInt() / 100.0));
                if (context.isCancelled()) {
                    sim.finishSimulation();
                }
                try {
                    Thread.sleep(PROGRESS_INTERVAL_MS);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });

        Rng.seed(seed);
        try {
            sim.run();
        } finally {
            finished.set(true);
            watcher.interrupt();
            Rng.reset();
        }
        if (context.isCancelled()) {
            throw new SimulationCancelledException();
        }
        NamedSink sink = new NamedSink(context.results());
        LegacyOutputs.write(sim, conf, seed, sink);
        LegacyResults.write(sim, conf, sink, sink.names);
        context.progress(1.0);
    }

    /** Repassa os resultados e anota os nomes gravados, para o catálogo. */
    private static final class NamedSink implements ResultSink {
        private final ResultSink target;
        private final java.util.Set<String> names = new java.util.LinkedHashSet<>();

        NamedSink(ResultSink target) {
            this.target = target;
        }

        @Override
        public void series(String name, String unit, double[] timeMs, double[] values) {
            names.add(name);
            target.series(name, unit, timeMs, values);
        }

        @Override
        public void spikes(String name, int[] units, double[] timeMs) {
            names.add(name);
            target.spikes(name, units, timeMs);
        }

        @Override
        public void table(String name, java.util.List<Column> columns) {
            names.add(name);
            target.table(name, columns);
        }
    }
}
