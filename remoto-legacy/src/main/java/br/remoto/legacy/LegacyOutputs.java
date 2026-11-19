package br.remoto.legacy;

import br.remoto.core.engine.ResultSink;
import br.remoto.core.engine.Seeds;
import br.remoto.model.Configuration;
import br.remoto.model.ReMoto;
import br.remoto.model.Simulation;
import br.remoto.model.Joint.Joint;
import br.remoto.model.Musculotendon.Muscle.ExtrafusalMuscle.ExtrafusalMuscleSuperClass;
import br.remoto.model.Neuron.Neuron;
import br.remoto.util.ButterworthImpulseInvariance;
import br.remoto.util.Signal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Extrai os resultados de uma {@code Simulation} original para o formato da nova plataforma.
 *
 * <ul>
 *   <li>{@code forca-<núcleo>}: força muscular (N), como em "muscleForce / All MUs" do site antigo;</li>
 *   <li>{@code disparos-<núcleo>}: disparos dos motoneurônios (instante no terminal × índice);</li>
 *   <li>{@code torque}: torque na articulação (N·m);</li>
 *   <li>{@code emg-<núcleo>}: EMG como a tela de resultados do site antigo mostrava por padrão
 *       (soma dos MUAPs com atenuação, ruído e passa-banda de 1ª/2ª ordem de 50 a 1000 Hz), em mV;</li>
 *   <li>{@code emg-bruto-<núcleo>}: a soma dos MUAPs, sem ruído nem filtro, em mV.</li>
 * </ul>
 */
final class LegacyOutputs {

    static final List<String> MOTOR_NUCLEI = List.of(ReMoto.SOL, ReMoto.MG, ReMoto.LG, ReMoto.TA);

    /** Padrões de ResultVO no site antigo (tela de resultados). */
    static final double EMG_FC_LOW_HZ = 50;
    static final double EMG_FC_HIGH_HZ = 1000;
    static final boolean EMG_ATTENUATION = true;

    private LegacyOutputs() {
    }

    static void write(Simulation sim, Configuration conf, long seed, ResultSink sink) {
        for (String nucleus : MOTOR_NUCLEI) {
            ExtrafusalMuscleSuperClass muscle = quietly(() -> sim.getMuscle(nucleus));
            if (muscle != null) {
                series(sink, "forca-" + nucleus, "N", muscle.getForceStore());
            }
            Neuron[] neurons = quietly(() -> sim.getNeuronsByNucleus(nucleus));
            if (neurons != null) {
                motoneuronSpikes(sink, "disparos-" + nucleus, neurons);
            }
        }
        Joint joint = quietly(sim::getJoint);
        if (joint != null) {
            series(sink, "torque", "N.m", joint.getJointTorqueStore());
        }
        // EMG por último: muscle.reset(...) reinicia o estado de leitura dos MUAPs das unidades motoras
        for (String nucleus : MOTOR_NUCLEI) {
            ExtrafusalMuscleSuperClass muscle = quietly(() -> sim.getMuscle(nucleus));
            if (muscle != null) {
                emg(sink, nucleus, muscle, conf, seed);
            }
        }
    }

    /**
     * Mesmo cálculo do ramo "EMG" de {@code ResultDisplay} (site antigo), com os padrões da tela de
     * resultados: atenuação ligada, ruído = média de |EMG| × noiseEMG, filtro "1st2nd"
     * ({@code ButterworthImpulseInvariance}) de 50 a 1000 Hz. A única diferença é o ruído, que vem de
     * um gerador com a seed do cenário em vez de {@code Math.random()}.
     */
    private static void emg(ResultSink sink, String nucleus, ExtrafusalMuscleSuperClass muscle,
                            Configuration conf, long seed) {
        double step = conf.getStep();
        int totSteps = (int) (conf.getTFin() / step);
        if (totSteps <= 0) {
            return;
        }
        muscle.reset(step, EMG_ATTENUATION);
        double[] t = new double[totSteps];
        double[] raw = new double[totSteps];
        double meanNoise = 0;
        for (int i = 0; i < totSteps; i++) {
            t[i] = step * i;
            raw[i] = muscle.instantMuscleEMG(t[i]);
            meanNoise += Math.abs(raw[i]);
        }
        meanNoise = meanNoise / totSteps * conf.getMiscellaneous(ReMoto.noiseEMG);

        ButterworthImpulseInvariance filter =
                new ButterworthImpulseInvariance(raw, EMG_FC_LOW_HZ, EMG_FC_HIGH_HZ, step * 1e-3);
        RandomGenerator noise = Seeds.stream(seed, "emg-ruido/" + nucleus);
        double[] emg = new double[totSteps];
        for (int i = 0; i < totSteps; i++) {
            emg[i] = filter.bandPass(raw[i] + meanNoise * noise.nextDouble());
        }
        sink.series("emg-" + nucleus, "mV", t, emg);
        sink.series("emg-bruto-" + nucleus, "mV", t, raw);
    }

    private static void series(ResultSink sink, String name, String unit, List<?> store) {
        if (store == null || store.isEmpty()) {
            return;
        }
        double[] t = new double[store.size()];
        double[] v = new double[store.size()];
        int n = 0;
        for (Object item : store) {
            if (item instanceof Signal s) {
                t[n] = s.getTime();
                v[n] = s.getValue();
                n++;
            }
        }
        if (n > 0) {
            sink.series(name, unit, Arrays.copyOf(t, n), Arrays.copyOf(v, n));
        }
    }

    private static void motoneuronSpikes(ResultSink sink, String name, Neuron[] neurons) {
        record Spike(double time, int unit) {
        }
        List<Spike> spikes = new ArrayList<>();
        for (Neuron neuron : neurons) {
            if (neuron == null || !ReMoto.MN.equals(neuron.getCategory())) {
                continue;
            }
            List<?> train = neuron.getTerminalSpikeTrain();
            if (train == null) {
                continue;
            }
            for (Object time : train) {
                if (time instanceof Number number) {
                    spikes.add(new Spike(number.doubleValue(), neuron.getIndex()));
                }
            }
        }
        if (spikes.isEmpty()) {
            return;
        }
        spikes.sort(Comparator.comparingDouble(Spike::time).thenComparingInt(Spike::unit));
        int[] units = new int[spikes.size()];
        double[] times = new double[spikes.size()];
        for (int i = 0; i < spikes.size(); i++) {
            units[i] = spikes.get(i).unit();
            times[i] = spikes.get(i).time();
        }
        sink.spikes(name, units, times);
    }

    /** O código original lança NullPointerException para núcleos ausentes na configuração. */
    private static <T> T quietly(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
