package br.remoto.core.engine.placeholder;

import br.remoto.core.engine.RunContext;
import br.remoto.core.engine.Seeds;
import br.remoto.core.engine.SimulationEngine;
import br.remoto.core.scenario.NucleusSpec;
import br.remoto.core.scenario.Scenario;

import java.util.Arrays;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Motor PROVISÓRIO: um pool de motoneurônios integra-e-dispara por núcleo, com recrutamento pelo
 * princípio do tamanho e força por somação de abalos exponenciais.
 *
 * <p>Existe só para exercitar a cadeia inteira (fila, progresso, resultados, multiusuário)
 * enquanto os modelos originais do ReMoto (condutâncias, Markov, Hill/Raikova, fuso, OTG...) são
 * portados para o {@code remoto-core} na Fase 1 da proposta. <strong>Não use para resultados
 * científicos.</strong>
 */
public final class PlaceholderPoolEngine implements SimulationEngine {

    public static final String VERSION = "placeholder-pool-0.1";

    private static final double TAU_MEMBRANE_MS = 10.0;
    private static final double REFRACTORY_MS = 5.0;
    private static final double TAU_TWITCH_MS = 40.0;
    private static final double SAMPLE_EVERY_MS = 1.0;
    /** Razão entre o limiar da maior e o da menor unidade. */
    private static final double THRESHOLD_RANGE = 10.0;
    /** Razão entre o abalo da maior e o da menor unidade. */
    private static final double TWITCH_RANGE = 100.0;

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public void run(Scenario scenario, RunContext ctx) {
        long steps = scenario.totalSteps();
        double dt = scenario.stepMs();
        int sampleEvery = (int) Math.max(1, Math.round(SAMPLE_EVERY_MS / dt));
        int samples = (int) ((steps + sampleEvery - 1) / sampleEvery);
        long progressEvery = Math.max(1, steps / 100);

        List<Pool> pools = scenario.nuclei().stream()
                .map(spec -> new Pool(spec, scenario.seed(), dt, samples))
                .toList();
        double[] time = new double[samples];

        int k = 0;
        for (long i = 0; i < steps; i++) {
            double t = i * dt;
            for (Pool pool : pools) {
                pool.step(t);
            }
            if (i % sampleEvery == 0) {
                time[k] = t;
                for (Pool pool : pools) {
                    pool.force[k] = pool.totalForce();
                }
                k++;
            }
            if (i % progressEvery == 0) {
                ctx.checkCancelled();
                ctx.progress((double) i / steps);
            }
        }

        for (Pool pool : pools) {
            String code = pool.spec.nucleus().name();
            ctx.results().series("forca-" + code, "u.a.", Arrays.copyOf(time, k), Arrays.copyOf(pool.force, k));
            ctx.results().spikes("disparos-" + code, pool.spikeUnits.toArray(), pool.spikeTimes.toArray());
        }
        ctx.progress(1.0);
    }

    private static final class Pool {
        final NucleusSpec spec;
        final double[] v;
        final double[] threshold;
        final double[] twitchAmplitude;
        final double[] twitch;
        final double[] refractoryUntil;
        final double[] force;
        final double dt;
        final double membraneGain;
        final double twitchDecay;
        final RandomGenerator noise;
        final IntArray spikeUnits = new IntArray();
        final DoubleArray spikeTimes = new DoubleArray();

        Pool(NucleusSpec spec, long seed, double dt, int samples) {
            int n = spec.motoneurons();
            this.spec = spec;
            this.dt = dt;
            this.v = new double[n];
            this.threshold = new double[n];
            this.twitchAmplitude = new double[n];
            this.twitch = new double[n];
            this.refractoryUntil = new double[n];
            this.force = new double[samples];
            this.membraneGain = dt / TAU_MEMBRANE_MS;
            this.twitchDecay = Math.exp(-dt / TAU_TWITCH_MS);
            this.noise = Seeds.stream(seed, spec.nucleus().name() + "/ruido");
            for (int i = 0; i < n; i++) {
                double f = n == 1 ? 0 : (double) i / (n - 1);
                threshold[i] = Math.pow(THRESHOLD_RANGE, f);
                twitchAmplitude[i] = Math.pow(TWITCH_RANGE, f);
                refractoryUntil[i] = -1;
            }
        }

        void step(double t) {
            for (int i = 0; i < v.length; i++) {
                twitch[i] *= twitchDecay;
                if (t < refractoryUntil[i]) {
                    continue;
                }
                double input = spec.driveNa() + spec.noiseNa() * noise.nextGaussian();
                v[i] += membraneGain * (input - v[i]);
                if (v[i] >= threshold[i]) {
                    v[i] = 0;
                    refractoryUntil[i] = t + REFRACTORY_MS;
                    twitch[i] += twitchAmplitude[i];
                    spikeUnits.add(i);
                    spikeTimes.add(t);
                }
            }
        }

        double totalForce() {
            double sum = 0;
            for (double f : twitch) {
                sum += f;
            }
            return sum;
        }
    }

    private static final class DoubleArray {
        private double[] values = new double[1024];
        private int size;

        void add(double x) {
            if (size == values.length) {
                values = Arrays.copyOf(values, size * 2);
            }
            values[size++] = x;
        }

        double[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }

    private static final class IntArray {
        private int[] values = new int[1024];
        private int size;

        void add(int x) {
            if (size == values.length) {
                values = Arrays.copyOf(values, size * 2);
            }
            values[size++] = x;
        }

        int[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }
}
