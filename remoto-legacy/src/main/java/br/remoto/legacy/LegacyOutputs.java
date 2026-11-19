package br.remoto.legacy;

import br.remoto.core.engine.ResultSink;
import br.remoto.model.ReMoto;
import br.remoto.model.Simulation;
import br.remoto.model.Joint.Joint;
import br.remoto.model.Musculotendon.Muscle.ExtrafusalMuscle.ExtrafusalMuscleSuperClass;
import br.remoto.model.Neuron.Neuron;
import br.remoto.util.Signal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Extrai os resultados de uma {@code Simulation} original para o formato da nova plataforma.
 *
 * <ul>
 *   <li>{@code forca-<núcleo>}: força muscular (N), como em "muscleForce / All MUs" do site antigo;</li>
 *   <li>{@code disparos-<núcleo>}: disparos dos motoneurônios (instante no terminal × índice);</li>
 *   <li>{@code torque}: torque na articulação (N·m).</li>
 * </ul>
 *
 * O EMG ainda não é exportado: no código original ele é calculado na camada de gráficos
 * (ResultDisplay), que depende do JFreeChart antigo.
 */
final class LegacyOutputs {

    static final List<String> MOTOR_NUCLEI = List.of(ReMoto.SOL, ReMoto.MG, ReMoto.LG, ReMoto.TA);

    private LegacyOutputs() {
    }

    static void write(Simulation sim, ResultSink sink) {
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
