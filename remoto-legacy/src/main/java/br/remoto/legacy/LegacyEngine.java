package br.remoto.legacy;

import br.remoto.core.engine.RunContext;
import br.remoto.core.engine.SimulationCancelledException;
import br.remoto.core.engine.SimulationEngine;
import br.remoto.core.scenario.Scenario;
import br.remoto.model.Configuration;
import br.remoto.model.ReMoto;
import br.remoto.model.Simulation;
import br.remoto.util.Rng;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Roda os modelos originais do ReMoto (motoneurônios, interneurônios, sinapses, músculo, fuso,
 * OTG, articulação) dentro da nova plataforma.
 *
 * <p>O código numérico é o mesmo do site antigo; a única diferença é a aleatoriedade, agora
 * derivada da seed do cenário ({@link Rng}), o que torna cada execução reprodutível.
 */
public final class LegacyEngine implements SimulationEngine {

    /** Código original "remoto2b5" + fonte de aleatoriedade com seed. */
    public static final String VERSION = "remoto-legacy-2b5-rng1";

    private static final long PROGRESS_INTERVAL_MS = 250;

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
        conf.setStoreResults(false);   // o código original gravaria um .sim em ReMoto.path
        conf.setStoreSignals(true);    // necessário para guardar força, torque e disparos
        conf.setChangedConfiguration(true);
        conf.setKeepProperties(false);

        String cdSimulation = "remoto-" + UUID.randomUUID();
        Simulation sim = new Simulation(conf, cdSimulation);
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
            ReMoto.simulations.remove(cdSimulation);
        }
        if (context.isCancelled()) {
            throw new SimulationCancelledException();
        }
        LegacyOutputs.write(sim, context.results());
        context.progress(1.0);
    }
}
