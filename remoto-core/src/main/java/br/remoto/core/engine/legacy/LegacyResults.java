package br.remoto.core.engine.legacy;

import br.remoto.core.engine.ResultSink;
import br.remoto.core.engine.ResultSink.Column;
import br.remoto.core.model.synapse.SynapseGroup;
import br.remoto.core.simulation.MotorUnit;
import br.remoto.core.simulation.ReMoto;
import br.remoto.core.simulation.Simulation;
import br.remoto.core.simulation.joint.Joint;
import br.remoto.core.simulation.musculotendon.ExtrafusalMuscleSuperClass;
import br.remoto.core.simulation.musculotendon.MusculotendonSuperClass;
import br.remoto.core.simulation.musculotendon.NonInnervatedTendon;
import br.remoto.core.simulation.neuron.Interneuron;
import br.remoto.core.simulation.neuron.Motoneuron;
import br.remoto.core.simulation.neuron.NeuralTract;
import br.remoto.core.simulation.neuron.Neuron;
import br.remoto.core.simulation.neuron.SensoryFiber;
import br.remoto.core.simulation.neuron.SpinalNeuron;
import br.remoto.core.simulation.proprioceptor.GolgiTendonOrgan;
import br.remoto.core.simulation.proprioceptor.MuscleSpindle;
import br.remoto.core.simulation.setup.Configuration;
import br.remoto.core.simulation.setup.Nucleus;
import br.remoto.core.simulation.util.ElectroCalculation;
import br.remoto.core.simulation.util.Signal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Os dados da tela de resultados do site antigo ({@code ResultsAction}/{@code ResultDisplay}),
 * além das saídas básicas de {@link LegacyOutputs}: as séries do músculo, do tendão, do
 * musculotendão, do fuso, do OTG e da articulação; os disparos de cada grupo de neurônios (no soma,
 * no terminal e no ponto de estímulo); as propriedades de cada neurônio e unidade motora; os sinais
 * dos neurônios guardados; o resumo dos disparos; e o {@code catalogo}, que descreve cada saída
 * para a página de resultados.
 *
 * <p>Tudo aqui já foi calculado pela simulação, que não muda: nada é sorteado e nenhum modelo é
 * avaliado de novo. As exceções são o EMG sem atenuação e o de cada unidade guardada, que leem os
 * MUAPs das unidades como as saídas básicas leem, e por isso vêm depois delas.
 */
final class LegacyResults {

    static final List<String> MOTOR_NUCLEI = LegacyOutputs.MOTOR_NUCLEI;
    static final List<String> AFFERENT_TYPES = List.of(ReMoto.Ia, ReMoto.II, ReMoto.Ib);
    static final String CATALOG = "catalogo";

    /** Nomes que a tela antiga dava aos tratos e ruídos. */
    private static final Map<String, String> TRACT_LABELS = Map.of(
            ReMoto.CM_ext, ReMoto.name_CM_ext, ReMoto.CM_flex, ReMoto.name_CM_flex,
            ReMoto.RBE_ext, ReMoto.name_RBE_ext, ReMoto.RBE_flex, ReMoto.name_RBE_flex,
            ReMoto.RBI_ext, ReMoto.name_RBI_ext, ReMoto.RBI_flex, ReMoto.name_RBI_flex);

    private final Simulation sim;
    private final Configuration conf;
    private final ResultSink sink;
    private final Set<String> written;
    private final Catalog catalog = new Catalog();

    private LegacyResults(Simulation sim, Configuration conf, ResultSink sink, Set<String> written) {
        this.sim = sim;
        this.conf = conf;
        this.sink = sink;
        this.written = written;
    }

    /** @param written nomes que as saídas básicas já gravaram (entram no catálogo) */
    static void write(Simulation sim, Configuration conf, ResultSink sink, Set<String> written) {
        new LegacyResults(sim, conf, sink, written).writeAll();
    }

    private void writeAll() {
        for (String m : MOTOR_NUCLEI) {
            Neuron[] neurons = quietly(() -> sim.getNeuronsByNucleus(m));
            ExtrafusalMuscleSuperClass muscle = quietly(() -> sim.getMuscle(m));
            basicOutputs(m);
            if (muscle != null) {
                muscle(m, muscle);
                motorUnits(m, muscle);
            }
            if (neurons != null) {
                motoneurons(m, neurons, muscle);
                afferents(m, neurons);
            }
        }
        for (String n : nuclei(ReMoto.INs)) {
            Neuron[] neurons = quietly(() -> sim.getNeuronsByNucleus(n));
            if (neurons != null) {
                interneurons(n, neurons);
            }
        }
        Neuron[] tracts = quietly(() -> sim.getNeuronsByNucleus(ReMoto.DT));
        if (tracts != null) {
            tracts(tracts);
        }
        joint();
        summary();
        parameters();
        sink.table(CATALOG, catalog.columns());
    }

    /** O passo, a duração e o ruído do EMG da configuração: a página de resultados refaz o EMG com eles. */
    private void parameters() {
        Double noise = quietly(() -> conf.getMiscellaneous(ReMoto.noiseEMG));
        sink.table("tabela-parametros", List.of(
                Column.texts("parametro", new String[] {"passo", "duracao", "ruido_emg"}),
                Column.numbers("valor", new double[] {conf.getStep(), conf.getTFin(), noise == null ? 0 : noise})));
        catalog.add("tabela-parametros", "tabela", "geral", "parametros", "", "", "parameters", "", "",
                "Step, duration and EMG noise of the run");
    }

    // ---- saídas básicas (LegacyOutputs): só entram no catálogo --------------------------------

    private void basicOutputs(String m) {
        if (written.contains("forca-" + m)) {
            catalog.add("forca-" + m, "serie", "biomecanico", "musculo", m, "", "muscleForce", "", "N", "Muscle force");
        }
        if (written.contains("disparos-" + m)) {
            catalog.add("disparos-" + m, "disparos", "neural", "motoneuronios", m, "", "spikeTimes", "terminal", "",
                    "MNs at terminal");
        }
        if (written.contains("emg-" + m)) {
            catalog.add("emg-" + m, "serie", "biomecanico", "emg", m, "", "EMG", "filtrado", "mV",
                    "EMG (noise, 50-1000 Hz)");
        }
        if (written.contains("emg-bruto-" + m)) {
            catalog.add("emg-bruto-" + m, "serie", "biomecanico", "emg", m, "", "EMG", "bruto", "mV", "Raw EMG");
        }
    }

    // ---- músculo, tendão, musculotendão, fuso e OTG --------------------------------------------

    private void muscle(String m, ExtrafusalMuscleSuperClass muscle) {
        muscleSeries(m, "musculo-comprimento-", "muscleLength", "m", "Muscle length", muscle::getLengthStore);
        muscleSeries(m, "musculo-velocidade-", "muscleVelocity", "m/s", "Muscle velocity", muscle::getVelocityStore);
        muscleSeries(m, "musculo-aceleracao-", "muscleAcceleration", "m/s^2", "Muscle acceleration",
                muscle::getAccelerationStore);
        muscleSeries(m, "musculo-ativacao-s-", "activationNormSType", "normalizada", "Muscle activation - S type",
                muscle::getSTypeMuscleActivationStore);
        muscleSeries(m, "musculo-ativacao-f-", "activationNormFType", "normalizada", "Muscle activation - F type",
                muscle::getFTypeMuscleActivationStore);
        muscleSeries(m, "musculo-ativacao-", "activationNorm", "normalizada", "Total muscle activation",
                muscle::getMuscleActivationStore);
        muscleSeries(m, "musculo-comprimento-normalizado-", "muscleLengthNorm", "normalizado",
                "Muscle normalized length", muscle::getLengthNormStore);
        muscleSeries(m, "musculo-forca-paralela-", "forceParallelElement", "normalizada",
                "Muscle parallel element force", muscle::getForceParallelElementStore);
        muscleSeries(m, "musculo-forca-viscosa-", "forceViscousElement", "normalizada",
                "Muscle viscous element force", muscle::getForceViscousElementStore);
        muscleSeries(m, "musculo-forca-ativa-s-", "forceActiveSType", "normalizada", "Muscle S type active force",
                muscle::getForceActiveSTypeStore);
        muscleSeries(m, "musculo-forca-ativa-f-", "forceActiveFType", "normalizada", "Muscle F type active force",
                muscle::getForceActiveFTypeStore);
        muscleSeries(m, "musculo-angulo-penacao-", "pennationAngle", "graus", "Pennation angle",
                muscle::getPennationAngleStore);

        MusculotendonSuperClass mt = quietly(muscle::getAssociatedMusculotendon);
        NonInnervatedTendon tendon = mt == null ? null : quietly(mt::getTendon);
        if (tendon != null) {
            series(m, "tendao-comprimento-" + m, "tendao", "tendonLength", "m", "Tendon length", tendon::getLengthStore);
            series(m, "tendao-forca-" + m, "tendao", "tendonForce", "N", "Tendon force", tendon::getForceStore);
        }
        if (mt != null) {
            series(m, "mt-comprimento-" + m, "musculotendao", "musculotendonLength", "m", "Musculotendon length",
                    mt::getLengthStore);
            series(m, "mt-braco-momento-" + m, "musculotendao", "musculotendonMomentArm", "m",
                    "Musculotendon moment arm", mt::getMomentArmStore);
        }
        normalizedForce(m, muscle, tendon);

        MuscleSpindle spindle = quietly(() -> sim.getMuscleSpindle(m));
        if (spindle != null) {
            series(m, "fuso-ia-" + m, "fuso", "IaFiringRate", "Hz", "Ia mean firing rate", spindle::getIaFiringRateStore);
            series(m, "fuso-ii-" + m, "fuso", "IIFiringRate", "Hz", "II mean firing rate", spindle::getIIFiringRateStore);
        }
        GolgiTendonOrgan gto = quietly(() -> sim.getGolgiTendonOrgan(m));
        // sem fibras Ib ativas, a taxa por fibra é uma divisão por zero (NaN e infinito)
        if (gto != null && gto.hasIbFibers()) {
            series(m, "otg-ib-" + m, "otg", "IbFiringRate", "Hz", "Ib mean firing rate", gto::getIbFiringRateStore);
        }
    }

    private void muscleSeries(String m, String prefix, String what, String unit, String label,
                              Supplier<List<?>> store) {
        series(m, prefix + m, "musculo", what, unit, label, store);
    }

    /**
     * "Normalized musculotendon force" da tela antiga: a força do tendão no Hill, a do músculo nos
     * outros modelos, em % da força máxima do músculo. Um músculo sem unidades motoras (que existe
     * por causa dos aferentes) tem força máxima 0, e a série não sai (seria 0/0).
     */
    private void normalizedForce(String m, ExtrafusalMuscleSuperClass muscle, NonInnervatedTendon tendon) {
        List<?> force = "hill".equals(quietly(muscle::getMuscleModel)) && tendon != null
                ? quietly(tendon::getForceStore) : quietly(muscle::getForceStore);
        Double max = quietly(muscle::getMaximumMuscleForce);
        double[][] tv = values(force);
        if (tv == null || max == null || !(max > 0)) {
            return;
        }
        double[] v = new double[tv[1].length];
        for (int i = 0; i < v.length; i++) {
            v[i] = tv[1][i] / max * 100;
        }
        String name = "musculo-forca-normalizada-" + m;
        sink.series(name, "% CVM", tv[0], v);
        catalog.add(name, "serie", "biomecanico", "musculo", m, "", "normalizedForce", "", "% CVM",
                "Normalized musculotendon force");
    }

    // ---- unidades motoras ----------------------------------------------------------------------

    private void motorUnits(String m, ExtrafusalMuscleSuperClass muscle) {
        MotorUnit[] units = quietly(muscle::getMotorunits);
        if (units == null || units.length == 0) {
            return;
        }
        int n = units.length;
        int[] index = new int[n];
        String[] type = new String[n];
        String[] cd = new String[n];
        double[] twitch = new double[n];
        double[] contraction = new double[n];
        double[] halfRelaxation = new double[n];
        double[] muap = new double[n];
        double[] y = new double[n];
        double[] z = new double[n];
        double[] attenuation = new double[n];
        double[] widening = new double[n];
        int[] stored = new int[n];
        for (int i = 0; i < n; i++) {
            MotorUnit mu = units[i];
            index[i] = mu.getIndex();
            type[i] = mu.getType();
            cd[i] = mu.getCd();
            twitch[i] = mu.getGpeak();
            contraction[i] = mu.getTpeak();
            halfRelaxation[i] = mu.getTHalfRaikova();
            muap[i] = mu.getAmpEMG();
            y[i] = mu.getPosY();
            z[i] = mu.getPosZ();
            attenuation[i] = (1 - mu.getAttenuationToSkin()) * 100;
            widening[i] = (mu.getTimeWidening() - 1) * 100;
            stored[i] = mu.isStoredSignals() ? 1 : 0;
        }
        String name = "tabela-unidades-motoras-" + m;
        sink.table(name, List.of(Column.integers("unidade", index), Column.texts("tipo", type),
                Column.texts("cd", cd), Column.numbers("pico_abalo[N]", twitch),
                Column.numbers("tempo_contracao[ms]", contraction), Column.numbers("meia_relaxacao[ms]", halfRelaxation),
                Column.numbers("amplitude_muap[mV]", muap), Column.numbers("posicao_y[mm]", y),
                Column.numbers("posicao_z[mm]", z), Column.numbers("atenuacao[%]", attenuation),
                Column.numbers("alargamento[%]", widening), Column.integers("guardada", stored)));
        catalog.add(name, "tabela", "biomecanico", "unidades-motoras", m, "", "motorUnits", "", "",
                "Motor units: twitch, contraction, MUAP, position");

        for (MotorUnit mu : units) {
            if (!mu.isStoredSignals()) {
                continue;
            }
            String suffix = m + "-" + mu.getIndex();
            series(m, "forca-um-" + suffix, "musculo", "muscleForce", "N", "Force of motor unit " + mu.getCd(),
                    () -> muscle.getMotorUnitForceStore(mu.getCd()), mu.getType(), Integer.toString(mu.getIndex()));
        }
        emgVariants(m, muscle, units);
    }

    /**
     * O EMG bruto sem atenuação e o de cada unidade guardada, com e sem atenuação: a tela antiga
     * deixava escolher a atenuação e mostrar o EMG de uma unidade. O ruído e o filtro são aplicados
     * na hora de mostrar, como lá.
     */
    private void emgVariants(String m, ExtrafusalMuscleSuperClass muscle, MotorUnit[] units) {
        double step = conf.getStep();
        int totSteps = (int) (conf.getTFin() / step);
        if (totSteps <= 0) {
            return;
        }
        double[] t = new double[totSteps];
        for (int i = 0; i < totSteps; i++) {
            t[i] = step * i;
        }
        for (boolean attenuation : new boolean[] {false, true}) {
            muscle.reset(step, attenuation);
            if (!attenuation) {
                double[] raw = new double[totSteps];
                for (int i = 0; i < totSteps; i++) {
                    raw[i] = muscle.instantMuscleEMG(t[i]);
                }
                raw[0] = 0;
                if (totSteps > 1) {
                    raw[1] = 0;
                }
                String name = "emg-bruto-sem-atenuacao-" + m;
                sink.series(name, "mV", t, raw);
                catalog.add(name, "serie", "biomecanico", "emg", m, "", "EMG", "bruto-sem-atenuacao", "mV",
                        "Raw EMG without attenuation");
            }
            for (MotorUnit mu : units) {
                if (!mu.isStoredSignals()) {
                    continue;
                }
                double[] raw = new double[totSteps];
                for (int i = 0; i < totSteps; i++) {
                    raw[i] = muscle.instantMotorUnitEMG(mu.getCd(), t[i]);
                }
                String name = (attenuation ? "emg-bruto-um-" : "emg-bruto-um-sem-atenuacao-") + m + "-" + mu.getIndex();
                sink.series(name, "mV", t, raw);
                catalog.add(name, "serie", "biomecanico", "emg", m, mu.getType(), "EMG",
                        attenuation ? "bruto" : "bruto-sem-atenuacao", "mV", "Raw EMG of motor unit " + mu.getCd(),
                        Integer.toString(mu.getIndex()));
            }
        }
    }

    // ---- motoneurônios -------------------------------------------------------------------------

    private void motoneurons(String m, Neuron[] neurons, ExtrafusalMuscleSuperClass muscle) {
        List<Motoneuron> mns = new ArrayList<>();
        for (Neuron n : neurons) {
            if (n instanceof Motoneuron mn && ReMoto.MN.equals(n.getCategory())) {
                mns.add(mn);
            }
        }
        if (mns.isEmpty()) {
            return;
        }
        spikes("disparos-soma-" + m, mns, n -> ((SpinalNeuron) n).getSomaSpikeTrain(), Neuron::getIndex);
        catalog.add("disparos-soma-" + m, "disparos", "neural", "motoneuronios", m, "", "spikeTimes", "soma", "",
                "MNs at soma");

        int n = mns.size();
        int[] index = new int[n];
        String[] type = new String[n];
        String[] cd = new String[n];
        double[] threshold = new double[n];
        double[] velocity = new double[n];
        double[] rn = new double[n];
        double[] rheobase = new double[n];
        double[] axonThreshold = new double[n];
        double[] position = new double[n];
        int[] stored = new int[n];
        ElectroCalculation el = new ElectroCalculation();
        for (int i = 0; i < n; i++) {
            Motoneuron mn = mns.get(i);
            index[i] = mn.getIndex();
            type[i] = mn.getType();
            cd[i] = mn.getCd();
            threshold[i] = mn.getThreshold();
            Double v = quietly(mn::getAxonConductionVelocity);
            velocity[i] = v == null ? Double.NaN : v;
            rn[i] = el.calcRn(mn.getGCoupling(), mn.getGLeakSoma(), mn.getGLeakDend());
            rheobase[i] = threshold[i] / rn[i];
            axonThreshold[i] = mn.getAxonThreshold();
            position[i] = mn.getXPosition();
            stored[i] = mn.isStoredSignals() ? 1 : 0;
        }
        String name = "tabela-motoneuronios-" + m;
        sink.table(name, List.of(Column.integers("unidade", index), Column.texts("tipo", type),
                Column.texts("cd", cd), Column.numbers("limiar[mV]", threshold),
                Column.numbers("velocidade_axonio[m/s]", velocity), Column.numbers("resistencia_entrada[MOhm]", rn),
                Column.numbers("reobase[nA]", rheobase), Column.numbers("limiar_axonio[mA]", axonThreshold),
                Column.numbers("posicao", position), Column.integers("guardado", stored)));
        catalog.add(name, "tabela", "neural", "motoneuronios", m, "", "properties", "", "",
                "MNs: threshold, conduction velocity, input resistance, rheobase, position");

        for (Motoneuron mn : mns) {
            if (mn.isStoredSignals()) {
                storedSignals(m, mn, "motoneuronios");
            }
        }
    }

    // ---- aferentes -----------------------------------------------------------------------------

    private void afferents(String m, Neuron[] neurons) {
        List<SensoryFiber> all = new ArrayList<>();
        for (Neuron n : neurons) {
            if (n instanceof SensoryFiber sf && ReMoto.AF.equals(n.getCategory())) {
                all.add(sf);
            }
        }
        if (all.isEmpty()) {
            return;
        }
        for (String type : AFFERENT_TYPES) {
            List<SensoryFiber> fibers = all.stream().filter(f -> type.equals(f.getType())).toList();
            if (fibers.isEmpty()) {
                continue;
            }
            String terminal = "disparos-" + m + "-" + type;
            spikes(terminal, fibers, Neuron::getTerminalSpikeTrain, Neuron::getIndex);
            catalog.add(terminal, "disparos", "neural", "aferentes", m, type, "spikeTimes", "terminal", "",
                    type + " afferents at terminal");
            String stimulus = "disparos-estimulo-" + m + "-" + type;
            spikeArrays(stimulus, fibers, f -> ((SensoryFiber) f).getAxonSpikeTimes(), Neuron::getIndex);
            catalog.add(stimulus, "disparos", "neural", "aferentes", m, type, "spikeTimes", "estimulo", "",
                    type + " afferents at stimulus point");
        }
        int n = all.size();
        int[] index = new int[n];
        String[] type = new String[n];
        String[] cd = new String[n];
        double[] recruitment = new double[n];
        double[] velocity = new double[n];
        double[] axonThreshold = new double[n];
        for (int i = 0; i < n; i++) {
            SensoryFiber f = all.get(i);
            index[i] = f.getIndex();
            type[i] = f.getType();
            cd[i] = f.getCd();
            recruitment[i] = f.getRecruitmentThreshold();
            velocity[i] = f.getAxonConductionVelocity();
            axonThreshold[i] = f.getAxonThreshold();
        }
        String name = "tabela-aferentes-" + m;
        sink.table(name, List.of(Column.integers("unidade", index), Column.texts("tipo", type),
                Column.texts("cd", cd), Column.numbers("limiar_recrutamento", recruitment),
                Column.numbers("velocidade_axonio[m/s]", velocity), Column.numbers("limiar_axonio[mA]", axonThreshold)));
        catalog.add(name, "tabela", "neural", "aferentes", m, "", "properties", "", "",
                "Afferents: recruitment threshold, conduction velocity, axon threshold");
    }

    // ---- interneurônios ------------------------------------------------------------------------

    private void interneurons(String nucleus, Neuron[] neurons) {
        List<Interneuron> ins = new ArrayList<>();
        for (Neuron n : neurons) {
            if (n instanceof Interneuron in && ReMoto.IN.equals(n.getCategory())) {
                ins.add(in);
            }
        }
        if (ins.isEmpty()) {
            return;
        }
        for (String type : distinctTypes(ins)) {
            List<Interneuron> group = ins.stream().filter(i -> type.equals(i.getType())).toList();
            String name = "disparos-" + nucleus + "-" + type;
            spikes(name, group, Neuron::getTerminalSpikeTrain, Neuron::getIndex);
            catalog.add(name, "disparos", "neural", "interneuronios", nucleus, type, "spikeTimes", "soma", "",
                    type + " interneurons");
        }
        int n = ins.size();
        int[] index = new int[n];
        String[] type = new String[n];
        String[] cd = new String[n];
        double[] threshold = new double[n];
        double[] rn = new double[n];
        double[] rheobase = new double[n];
        double[] position = new double[n];
        int[] stored = new int[n];
        for (int i = 0; i < n; i++) {
            Interneuron in = ins.get(i);
            index[i] = in.getIndex();
            type[i] = in.getType();
            cd[i] = in.getCd();
            threshold[i] = in.getThreshold();
            rn[i] = 1 / in.getGLeakSoma();
            rheobase[i] = threshold[i] / rn[i];
            position[i] = in.getXPosition();
            stored[i] = in.isStoredSignals() ? 1 : 0;
        }
        String name = "tabela-interneuronios-" + nucleus;
        sink.table(name, List.of(Column.integers("unidade", index), Column.texts("tipo", type),
                Column.texts("cd", cd), Column.numbers("limiar[mV]", threshold),
                Column.numbers("resistencia_entrada[MOhm]", rn), Column.numbers("reobase[nA]", rheobase),
                Column.numbers("posicao", position), Column.integers("guardado", stored)));
        catalog.add(name, "tabela", "neural", "interneuronios", nucleus, "", "properties", "", "",
                "Interneurons: threshold, input resistance, rheobase, position");

        for (Interneuron in : ins) {
            if (in.isStoredSignals()) {
                storedSignals(nucleus, in, "interneuronios");
            }
        }
    }

    // ---- tratos descendentes e ruídos ----------------------------------------------------------

    private void tracts(Neuron[] neurons) {
        List<Neuron> all = Arrays.stream(neurons)
                .filter(n -> ReMoto.TR.equals(n.getCategory()) || ReMoto.Noise.equals(n.getCategory())).toList();
        for (String type : distinctTypes(all)) {
            List<Neuron> group = all.stream().filter(n -> type.equals(n.getType())).toList();
            // ruídos de núcleos diferentes repetem o índice: no raster, a posição no grupo
            Map<Neuron, Integer> position = new LinkedHashMap<>();
            for (int i = 0; i < group.size(); i++) {
                position.put(group.get(i), i);
            }
            String name = "disparos-DT-" + type;
            spikes(name, group, Neuron::getTerminalSpikeTrain, position::get);
            String label = TRACT_LABELS.getOrDefault(type, type);
            catalog.add(name, "disparos", "neural", "tratos", ReMoto.DT, type, "spikeTimes", "terminal", "",
                    label + " axons");
            for (Neuron n : group) {
                if (n instanceof NeuralTract tract && tract.isRecordingMean()) {
                    double[][] tv = lastPerInstant(tract.getMeanTimes(), tract.getMeanValues());
                    if (tv[0].length > 0) {
                        String mean = "trato-isi-medio-" + type;
                        sink.series(mean, "ms", tv[0], tv[1]);
                        catalog.add(mean, "serie", "neural", "tratos", ReMoto.DT, type, "meanISI", "", "ms",
                                label + " ISI mean value");
                    }
                    break;
                }
            }
        }
    }

    // ---- articulação ---------------------------------------------------------------------------

    private void joint() {
        Joint joint = quietly(sim::getJoint);
        if (joint == null) {
            return;
        }
        if (written.contains("torque")) {
            catalog.add("torque", "serie", "biomecanico", "articulacao", "", "", "jointTorque", "", "N.m", "Joint torque");
        }
        jointSeries("articulacao-angulo", "jointAngle", "graus", "Joint angle", joint::getJointAngleStore);
        jointSeries("articulacao-velocidade", "jointVelocity", "graus/s", "Joint angular velocity",
                joint::getJointVelocityStore);
        jointSeries("articulacao-centro-massa", "jointCenterMass", "mm", "Center of mass", joint::getJointCenterMassStore);
        jointSeries("articulacao-centro-pressao", "jointCenterPressure", "mm", "Center of pressure",
                joint::getJointCenterPressureStore);
        jointSeries("articulacao-torque-muscular", "jointMuscleTorque", "N.m", "Joint muscle torque",
                joint::getJointMuscleTorqueStore);
        jointSeries("articulacao-torque-gravitacional", "jointGravTorque", "N.m", "Gravitational torque",
                joint::getJointGravTorqueStore);
        jointSeries("articulacao-torque-passivo", "jointPassiveTorque", "N.m", "Passive joint torque",
                joint::getJointPassiveTorqueStore);
        jointSeries("articulacao-disturbio", "jointDisturbance", "N.m", "Disturbance", joint::getJointDisturbanceStore);
    }

    private void jointSeries(String name, String what, String unit, String label, Supplier<List<?>> store) {
        series("", name, "articulacao", what, unit, label, store);
    }

    // ---- sinais dos neurônios guardados --------------------------------------------------------

    /**
     * Os sinais que o neurônio guardou a cada passo: potenciais do soma e do dendrito, condutâncias
     * (em nS, como a tela antiga mostrava: ×1000 os µS do modelo) e a corrente injetada.
     */
    private void storedSignals(String nucleus, SpinalNeuron neuron, String element) {
        List<?> store = neuron.getSignalStore();
        if (store == null || store.isEmpty()) {
            return;
        }
        Map<String, double[]> values = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        double[] times = new double[16];
        int steps = 0;
        int capacity = Math.max(16, store.size() / 8);
        for (Object o : store) {
            if (!(o instanceof Signal s)) {
                continue;
            }
            String type = s.getType();
            double[] v = values.computeIfAbsent(type, k -> new double[capacity]);
            int c = counts.getOrDefault(type, 0);
            if (c == v.length) {
                v = Arrays.copyOf(v, c * 2);
                values.put(type, v);
            }
            v[c] = s.getValue();
            counts.put(type, c + 1);
            if (ReMoto.Vs.equals(type)) {
                if (steps == times.length) {
                    times = Arrays.copyOf(times, steps * 2);
                }
                times[steps++] = s.getTime();
            }
        }
        if (steps == 0) {
            return;
        }
        List<Column> columns = new ArrayList<>();
        columns.add(Column.numbers("t_ms", Arrays.copyOf(times, steps)));
        boolean motoneuron = neuron instanceof Motoneuron;
        for (Map.Entry<String, double[]> e : values.entrySet()) {
            String type = e.getKey();
            int c = counts.get(type);
            if (c != steps || (!motoneuron && ReMoto.Vd.equals(type))) {
                continue; // a corrente só aparece com corrente injetada; o interneurônio não tem dendrito
            }
            double[] v = Arrays.copyOf(e.getValue(), steps);
            String column;
            if (ReMoto.Vs.equals(type) || ReMoto.Vd.equals(type)) {
                column = type + "[mV]";
            } else if (ReMoto.inj.equals(type)) {
                column = "inj[nA]";
            } else {
                for (int i = 0; i < v.length; i++) {
                    v[i] *= 1000;
                }
                column = type + "[nS]";
            }
            columns.add(Column.numbers(column, v));
        }
        String name = "sinais-" + nucleus + "-" + neuron.getType() + "-" + neuron.getIndex();
        sink.table(name, columns);
        catalog.add(name, "tabela", "neural", element, nucleus, neuron.getType(), "signals", "", "",
                "Stored signals of " + neuron.getCd(), Integer.toString(neuron.getIndex()));
    }

    // ---- resumo (a página "summary" do site antigo) ---------------------------------------------

    private void summary() {
        Neuron[][] all = sim.getNeurons();
        if (all == null) {
            return;
        }
        List<Neuron> list = new ArrayList<>();
        for (Neuron[] nucleus : all) {
            if (nucleus != null) {
                list.addAll(Arrays.asList(nucleus));
            }
        }
        if (list.isEmpty()) {
            return;
        }
        int n = list.size();
        int steady = 4;
        String[] nucleus = new String[n];
        String[] category = new String[n];
        String[] type = new String[n];
        String[] cd = new String[n];
        int[] index = new int[n];
        int[] count = new int[n];
        double[] first = new double[n];
        double[] steadyRate = new double[n];
        double[][] rates = new double[steady][n];
        double[] gExc = new double[n];
        double[] gInh = new double[n];
        int[] received = new int[n];
        int[] receivedInh = new int[n];
        double[] threshold = new double[n];
        double[] rn = new double[n];
        for (int k = 0; k < n; k++) {
            Neuron neuron = list.get(k);
            nucleus[k] = neuron.getCdNucleus();
            category[k] = neuron.getCategory();
            type[k] = neuron.getType();
            cd[k] = neuron.getCd();
            index[k] = neuron.getIndex();
            double[] train = times(neuron.getTerminalSpikeTrain());
            count[k] = train.length;
            // as frequências saem dos instantes distintos, em ordem: o reset dos tratos anota um
            // disparo em t = 0 que pode coincidir com o primeiro (intervalo 0, frequência infinita),
            // e o trem do terminal de um motoneurônio estimulado mistura os disparos do axônio
            double[] spikes = distinctSorted(train);
            int s = spikes.length;
            if (s > 1) {
                first[k] = 1000.0 / (spikes[1] - spikes[0]);
            }
            if (s > steady) {
                steadyRate[k] = 1000.0 / ((spikes[s - 1] - spikes[steady - 1]) / (s - steady));
            }
            for (int i = 0; i < steady; i++) {
                rates[i][k] = s > i + 1 ? 1000 / (spikes[i + 1] - spikes[i]) : 0;
            }
            if (neuron instanceof SpinalNeuron sn) {
                SynapseGroup exc = sn.getDendExcitSynapses();
                SynapseGroup inh = sn.getDendInhibSynapses();
                gExc[k] = exc == null ? 0 : exc.gMaxTotal();
                gInh[k] = inh == null ? 0 : inh.gMaxTotal();
                received[k] = exc == null ? 0 : exc.spikesReceived();
                receivedInh[k] = inh == null ? 0 : inh.spikesReceived();
                threshold[k] = sn.getThreshold();
                rn[k] = sn.getRn();
            }
        }
        List<Column> columns = new ArrayList<>(List.of(Column.texts("nucleo", nucleus),
                Column.texts("categoria", category), Column.texts("tipo", type), Column.texts("cd", cd),
                Column.integers("unidade", index), Column.integers("disparos", count),
                Column.numbers("freq_primeiro_intervalo[Hz]", first), Column.numbers("freq_regime[Hz]", steadyRate)));
        for (int i = 0; i < steady; i++) {
            columns.add(Column.numbers("freq_intervalo_" + (i + 1) + "[Hz]", rates[i]));
        }
        columns.addAll(List.of(Column.numbers("gmax_exc_dendrito[uS]", gExc),
                Column.numbers("gmax_inib_dendrito[uS]", gInh), Column.integers("disparos_recebidos_exc", received),
                Column.integers("disparos_recebidos_inib", receivedInh), Column.numbers("limiar[mV]", threshold),
                Column.numbers("resistencia_entrada[MOhm]", rn)));
        sink.table("tabela-resumo", columns);
        catalog.add("tabela-resumo", "tabela", "geral", "resumo", "", "", "summary", "", "",
                "Summary of spikes per neuron");
    }

    // ---- auxiliares ----------------------------------------------------------------------------

    private void series(String nucleus, String name, String element, String what, String unit, String label,
                        Supplier<List<?>> store) {
        series(nucleus, name, element, what, unit, label, store, "", "");
    }

    private void series(String nucleus, String name, String element, String what, String unit, String label,
                        Supplier<List<?>> store, String subtype, String index) {
        double[][] tv = values(quietly(store));
        if (tv == null) {
            return;
        }
        sink.series(name, unit, tv[0], tv[1]);
        catalog.add(name, "serie", "biomecanico", element, nucleus, subtype, what, "", unit, label, index);
    }

    /** Instantes e valores de uma lista de {@code Signal}; null se vazia. */
    private static double[][] values(List<?> store) {
        if (store == null || store.isEmpty()) {
            return null;
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
        boolean any = false;
        for (int i = 0; i < n && !any; i++) {
            any = !Double.isNaN(v[i]);
        }
        // um proprioceptor que não é integrado (SOCDS com tornozelo isométrico) guarda só NaN
        return n == 0 || !any ? null : new double[][] {Arrays.copyOf(t, n), Arrays.copyOf(v, n)};
    }

    /** Um valor por instante: quando o mesmo instante aparece seguido, fica o último. */
    private static double[][] lastPerInstant(double[] t, double[] v) {
        double[] ts = new double[t.length];
        double[] vs = new double[t.length];
        int n = 0;
        for (int i = 0; i < t.length; i++) {
            if (n > 0 && ts[n - 1] == t[i]) {
                vs[n - 1] = v[i];
            } else {
                ts[n] = t[i];
                vs[n] = v[i];
                n++;
            }
        }
        return new double[][] {Arrays.copyOf(ts, n), Arrays.copyOf(vs, n)};
    }

    private <N extends Neuron> void spikes(String name, List<N> neurons, java.util.function.Function<Neuron, List<?>> train,
                                           java.util.function.Function<Neuron, Integer> unit) {
        spikeArrays(name, neurons, n -> times(train.apply(n)), unit);
    }

    /** Disparos de um grupo, em ordem de tempo e de unidade, como os {@code disparos-} básicos. */
    private <N extends Neuron> void spikeArrays(String name, List<N> neurons,
                                                java.util.function.Function<Neuron, double[]> train,
                                                java.util.function.Function<Neuron, Integer> unit) {
        record Spike(double time, int unit) {
        }
        List<Spike> spikes = new ArrayList<>();
        for (Neuron n : neurons) {
            double[] t = train.apply(n);
            int u = unit.apply(n);
            for (double time : t) {
                spikes.add(new Spike(time, u));
            }
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

    /** Os instantes em ordem, sem repetir os iguais. */
    private static double[] distinctSorted(double[] times) {
        double[] t = times.clone();
        Arrays.sort(t);
        int n = 0;
        for (int i = 0; i < t.length; i++) {
            if (n == 0 || t[i] != t[n - 1]) {
                t[n++] = t[i];
            }
        }
        return Arrays.copyOf(t, n);
    }

    private static double[] times(List<?> train) {
        if (train == null) {
            return new double[0];
        }
        double[] t = new double[train.size()];
        int n = 0;
        for (Object o : train) {
            if (o instanceof Number number) {
                t[n++] = number.doubleValue();
            }
        }
        return Arrays.copyOf(t, n);
    }

    private static <N extends Neuron> List<String> distinctTypes(List<N> neurons) {
        return neurons.stream().map(Neuron::getType).distinct().toList();
    }

    private List<String> nuclei(String layer) {
        List<String> codes = new ArrayList<>();
        List<?> nuclei = quietly(() -> conf.getNuclei(layer));
        if (nuclei != null) {
            for (Object o : nuclei) {
                if (o instanceof Nucleus nucleus) {
                    codes.add(nucleus.getCd());
                }
            }
        }
        return codes;
    }

    /** O código original lança exceções para elementos ausentes na configuração. */
    private static <T> T quietly(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** O catálogo: uma linha por saída, com o que a página de resultados precisa para montar os menus. */
    private static final class Catalog {
        private final List<String[]> rows = new ArrayList<>();

        void add(String name, String kind, String group, String element, String nucleus, String subtype,
                 String what, String point, String unit, String label) {
            add(name, kind, group, element, nucleus, subtype, what, point, unit, label, "");
        }

        void add(String name, String kind, String group, String element, String nucleus, String subtype,
                 String what, String point, String unit, String label, String index) {
            rows.add(new String[] {name, kind, group, element, nucleus, subtype, what, point, unit, label, index});
        }

        List<Column> columns() {
            String[] header = {"nome", "tipo", "grupo", "elemento", "nucleo", "subtipo", "grandeza", "ponto",
                    "unidade", "rotulo", "indice"};
            List<Column> columns = new ArrayList<>();
            for (int c = 0; c < header.length; c++) {
                String[] values = new String[rows.size()];
                for (int r = 0; r < rows.size(); r++) {
                    values[r] = rows.get(r)[c];
                }
                columns.add(Column.texts(header[c], values));
            }
            return columns;
        }
    }
}
