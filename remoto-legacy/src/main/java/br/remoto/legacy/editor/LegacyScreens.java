package br.remoto.legacy.editor;

import br.remoto.legacy.editor.ConfAdapters.PoolPosition;
import br.remoto.legacy.editor.ConfAdapters.SimulationSettings;
import br.remoto.legacy.editor.LegacyScreen.Part;
import br.remoto.legacy.editor.LegacyScreen.SelectorDef;
import br.remoto.legacy.editor.ScreenView.Layout;
import br.remoto.model.Configuration;
import br.remoto.model.Current;
import br.remoto.model.ModulatingSignal;
import br.remoto.model.ReMoto;
import br.remoto.model.vo.ConductanceVO;
import br.remoto.model.vo.DynamicVO;
import br.remoto.model.vo.MiscellaneousVO;
import br.remoto.model.vo.MotorUnitVO;
import br.remoto.model.vo.MuscleVO;
import br.remoto.model.vo.NerveVO;
import br.remoto.model.vo.NeuronVO;
import br.remoto.model.vo.Nucleus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * As telas do editor, na ordem e com os rótulos do menu do site antigo (WebContent/jsp). Cada
 * tela reproduz o par JSP + Action correspondente: mesmos objetos, mesmas propriedades, mesmos
 * limites ao salvar.
 */
public final class LegacyScreens {

    /** Item do menu lateral; {@code screen == null} = tela ainda não portada. */
    public record MenuItem(String id, String title, boolean available) {
    }

    public record MenuGroup(String title, List<MenuItem> items) {
    }

    private static final Map<String, LegacyScreen> SCREENS = new LinkedHashMap<>();

    static {
        register(simulation());
        register(network());
        register(descendingCommands());
        register(synapses());
        register(motoneurons());
        register(interneurons());
        register(sensoryFibers());
        register(motorUnits());
        register(miscellaneous());
        register(nerveStimulation());
        register(injectedCurrent());
        register(conductances());
        register(kineticProperties());
        register(synapticDynamics());
        register(muscleSpindle());
        register(golgiTendonOrgan());
        register(biomechanicalElements());
        register(joint());
        register(musculotendon());
        register(electromyogram());
    }

    private LegacyScreens() {
    }

    public static Optional<LegacyScreen> find(String id) {
        return Optional.ofNullable(SCREENS.get(id));
    }

    /** Menu lateral do site antigo (template.dwt), com as telas já disponíveis marcadas. */
    public static List<MenuGroup> menu() {
        return List.of(
                group("Structure", "input", "Descending Commands", "network", "Neural Elements"),
                group("Neural Parameters", "synapse", "Synapses", "markov", "Kinetic Properties",
                        "dynamics", "Synaptic Dynamics", "conductance", "Conductances",
                        "motoneuron", "Motoneurons", "interneuron", "Interneurons", "sensory", "Sensory Fibers"),
                group("Proprioceptors", "muscleSpindle", "Muscle Spindle", "golgiTendonOrgan", "Golgi Tendon Organ"),
                group("Biomechanical Elements", "biomechanicalElements", "Biomechanical Elements"),
                group("Biomechanical Parameters", "joint", "Joint", "motorunit", "Muscle Units",
                        "musculotendon", "Musculotendon", "emg", "Electromyogram", "miscellaneous", "Miscellaneous"),
                group("Stimulation", "stimulation", "Nerve Stimulation", "injectedCurrent", "Injected Current"),
                group("Run", "start", "Simulation Start"));
    }

    private static MenuGroup group(String title, String... idsAndTitles) {
        List<MenuItem> items = new ArrayList<>();
        for (int i = 0; i < idsAndTitles.length; i += 2) {
            items.add(new MenuItem(idsAndTitles[i], idsAndTitles[i + 1], SCREENS.containsKey(idsAndTitles[i])));
        }
        return new MenuGroup(title, items);
    }

    private static void register(LegacyScreen screen) {
        SCREENS.put(screen.id(), screen);
    }

    // ------------------------------------------------------------------ Run

    /** start.jsp / StartAction. */
    private static LegacyScreen simulation() {
        return new LegacyScreen("start", "Simulation Start", List.of(),
                List.of(new Part(null, Layout.LIST,
                        (conf, sel) -> List.of(new SimulationSettings(conf)),
                        Object::toString,
                        List.of(Field.number("Time step", "ms", "step"), Field.number("Duration", "ms", "duration")))),
                (conf, sel, items) -> {
                    if (conf.getStep() < ReMoto.minStep) {
                        conf.setStep(ReMoto.minStep);
                    }
                },
                List.of());
    }

    // ------------------------------------------------------------------ Structure

    /** network.jsp / NetworkAction. */
    private static LegacyScreen network() {
        Part neurons = new Part(null, Layout.LIST,
                (conf, sel) -> {
                    List<Object> list = new ArrayList<>();
                    for (Nucleus nucleus : pools(conf, true, true)) {
                        String cd = nucleus.getCd();
                        if (cd.equals(ReMoto.IN_ext) || cd.equals(ReMoto.IN_flex)) {
                            list.addAll(conf.getNeuronTypes(cd, ReMoto.IN));
                        } else {
                            list.addAll(conf.getNeuronTypes(cd, ReMoto.MN));
                            list.addAll(conf.getNeuronTypes(cd, ReMoto.AF));
                        }
                    }
                    return list;
                },
                item -> {
                    NeuronVO n = (NeuronVO) item;
                    return n.getCdNucleus() + " — " + n.getType() + " (" + category(n.getCategory()) + ")";
                },
                List.of(Field.bool("Active", "active"), Field.integer("Quantity", null, "quantity")));
        Part positions = new Part("Anatomical position of spinal neurons", Layout.LIST,
                (conf, sel) -> {
                    List<Object> list = new ArrayList<>();
                    for (String[] pool : new String[][]{{ReMoto.SOL, "SOL MN pool"}, {ReMoto.MG, "MG MN pool"},
                            {ReMoto.LG, "LG MN pool"}, {ReMoto.TA, "TA MN pool"},
                            {ReMoto.IN_ext, "Extensors IN pool"}, {ReMoto.IN_flex, "Flexors (TA) IN pool"}}) {
                        if (PoolPosition.exists(conf, pool[0])) {
                            list.add(new PoolPosition(conf, pool[0], pool[1]));
                        }
                    }
                    return list;
                },
                Object::toString,
                List.of(Field.number("Initial", null, "initial"), Field.number("Final", null, "final")));
        return new LegacyScreen("network", "Neural Elements", List.of(), List.of(neurons, positions),
                (conf, sel, items) -> {
                    conf.setChangedConfiguration(true);
                    List<?> list = items.get(0);
                    for (Object o : list) {
                        NeuronVO n = (NeuronVO) o;
                        n.setQuantity(Math.max(0, Math.min(ReMoto.maxNeuronsPerType, n.getQuantity())));
                        n.setIndexStoreVm1(-1);
                    }
                    conf.setListNeuronTypes(list);
                },
                List.of("Quantity: at most " + ReMoto.maxNeuronsPerType + " neurons per type."));
    }

    /** input.jsp / InputAction. */
    private static LegacyScreen descendingCommands() {
        String[] signals = {"none", "none", "constant", "constant", "ramp", "ramp", "random", "random",
                "sine", "sine", "square", "square", "trapezoid", "trapezoid", "triangle", "triangle", "AM", "AM"};
        Part inputs = new Part(null, Layout.LIST,
                (conf, sel) -> {
                    List list = conf.getNeuronTypes(ReMoto.DT, ReMoto.TR);
                    Collections.sort(list);
                    return list;
                },
                item -> {
                    NeuronVO n = (NeuronVO) item;
                    return n.getTractName() == null || n.getTractName().isBlank() ? n.getType() : n.getTractName();
                },
                List.of(Field.bool("Active", "active"),
                        Field.integer("Axons", null, "quantity"),
                        Field.choice("Distribution", "distribution", "Gm", "Gamma", "P", "Poisson", "G", "Gaussian"),
                        Field.number("ISI mean", "ms", "mean"),
                        Field.number("Std", null, "std"),
                        Field.choice("Modulation", "cdSignal", signals),
                        Field.choice("Target", "modType", "ISI", "ISI", "rate", "rate"),
                        Field.number("Start", "ms", "ini"),
                        Field.number("Stop", "ms", "fin"),
                        Field.number("Peak", null, "amp"),
                        Field.number("Frequency", "Hz", "freq"),
                        Field.number("Delay", "ms", "delay"),
                        Field.number("Width", null, "width"),
                        Field.number("Modulating frequency", "Hz", "modFreq"),
                        Field.number("Modulating factor", null, "modFactor")));
        Part noises = new Part("Synaptic noise", Layout.LIST,
                (conf, sel) -> conf.getNeuronTypes(ReMoto.DT, ReMoto.Noise),
                item -> ((NeuronVO) item).getType(),
                List.of(Field.bool("Active", "active"), Field.number("ISI mean", "ms", "mean")));
        return new LegacyScreen("input", "Descending Commands", List.of(), List.of(inputs, noises),
                (conf, sel, items) -> {
                    conf.setChangedConfiguration(true);
                    List<Object> modulations = new ArrayList<>();
                    for (Object o : items.get(0)) {
                        NeuronVO neu = (NeuronVO) o;
                        if (neu.isActive()) {
                            if (neu.getMean() < ReMoto.minISImean) {
                                neu.setMean(ReMoto.minISImean);
                            }
                            if (neu.getQuantity() > ReMoto.maxNeuronsPerType) {
                                neu.setQuantity(ReMoto.maxNeuronsPerType);
                            }
                            ModulatingSignal signal = new ModulatingSignal();
                            signal.setAmp(neu.getAmp());
                            signal.setCdSignal(neu.getCdSignal());
                            signal.setFreq(neu.getFreq());
                            signal.setTini(neu.getIni());
                            signal.setTfin(neu.getFin());
                            signal.setWidth(neu.getWidth());
                            signal.setCdNucleus(neu.getCdNucleus());
                            signal.setCdNeuronType(neu.getType());
                            signal.setModType(neu.getModType());
                            signal.setDelay(neu.getDelay());
                            signal.setModFreq(neu.getModFreq());
                            signal.setModFactor(neu.getModFactor());
                            modulations.add(signal);
                        }
                    }
                    conf.setDescendingCommands(modulations);
                    for (Object o : items.get(1)) {
                        NeuronVO neu = (NeuronVO) o;
                        if (neu.getMean() < ReMoto.minISImean / 10) {
                            neu.setMean(ReMoto.minISImean / 10);
                        }
                    }
                    conf.setListNeuronTypes(items.get(0));
                    conf.setListNeuronTypes(items.get(1));
                },
                List.of("Only active tracts keep their modulating signal (as in the original InputAction)."));
    }

    // ------------------------------------------------------------------ Neural Parameters

    /** synapse.jsp / SynapseAction (filtros de célula de origem/alvo: "active" ou "all"). */
    private static LegacyScreen synapses() {
        List<SelectorDef> selectors = List.of(
                new SelectorDef("pool", "Pool", conf -> options(pools(conf, true, true)), ReMoto.SOL),
                new SelectorDef("typePre", "Incoming cell", conf -> List.of(
                        new Field.Option(ReMoto.ACTIVE, "active"), new Field.Option(ReMoto.ALL, "all")), ReMoto.ACTIVE),
                new SelectorDef("typePos", "Target cell", conf -> List.of(
                        new Field.Option(ReMoto.ACTIVE, "active"), new Field.Option(ReMoto.ALL, "all")), ReMoto.ACTIVE));
        Part part = new Part(null, Layout.LIST,
                (conf, sel) -> conf.getSynapticConductances(sel.get("pool"), sel.get("typePre"), sel.get("typePos")),
                item -> {
                    ConductanceVO c = (ConductanceVO) item;
                    return c.getPre() + " → " + c.getPos() + " (" + c.getPolarity() + ")";
                },
                List.of(Field.bool("Active", "active"),
                        Field.choice("Compartment", "compartment", "S", "soma", "D", "dendrite"),
                        Field.number("Maximum conductance", "nS", "gmax"),
                        Field.number("Connectivity", "%", "connectivity"),
                        Field.number("Transmission delay", "ms", "delay")));
        return new LegacyScreen("synapse", "Synaptic Parameters", selectors, List.of(part),
                (conf, sel, items) -> {
                    conf.setChangedConfiguration(true);
                    conf.setSynapseTypes(new ArrayList<>(items.get(0)));
                },
                List.of());
    }

    /** motoneuron.jsp / NeuronAction (task=MN). */
    private static LegacyScreen motoneurons() {
        Part electrotonic = new Part("General electrotonic parameters", Layout.MATRIX,
                (conf, sel) -> conf.getNeuronTypes(sel.get("pool"), ReMoto.MN),
                item -> ((NeuronVO) item).getType(),
                List.of(Field.number("Dendrite specific resistance", "Ωcm²", "rmDend1", "rmDend2"),
                        Field.number("Dendrite Ca⁺⁺ channel threshold", "mV", "thresholdCa1", "thresholdCa2"),
                        Field.number("Soma threshold", "mV", "threshold1", "threshold2"),
                        Field.number("Soma specific resistance", "Ωcm²", "rmSoma1", "rmSoma2"),
                        Field.number("Axon threshold", "mA", "axonThreshold1", "axonThreshold2"),
                        Field.number("Axon conduction velocity", "m/s", "axonVelocity1", "axonVelocity2"),
                        Field.number("Membrane specific capacitance", "µF/cm²", "cm"),
                        Field.number("Cytoplasm resistivity", "Ωcm", "ri")));
        Part geometry = new Part("Geometric parameters", Layout.MATRIX,
                (conf, sel) -> conf.getNeuronTypes(sel.get("pool"), ReMoto.MN),
                item -> ((NeuronVO) item).getType(),
                List.of(Field.number("Soma diameter", "µm", "dsoma1", "dsoma2"),
                        Field.number("Soma length", "µm", "lsoma1", "lsoma2"),
                        Field.number("Dendrite diameter", "µm", "ddend1", "ddend2"),
                        Field.number("Dendrite length", "µm", "ldend1", "ldend2")));
        return new LegacyScreen("motoneuron", "Parameters of the alpha motoneurons",
                List.of(new SelectorDef("pool", "Pool", conf -> options(pools(conf, true, false)), ReMoto.SOL)),
                List.of(electrotonic, geometry),
                (conf, sel, items) -> {
                    conf.setChangedConfiguration(true);
                    conf.setListNeuronTypes(items.get(0));
                    conf.setMerge(true);
                },
                List.of("Columns \"from\"/\"to\": values for the first and the last motoneuron of each type."));
    }

    /** interneuron.jsp / NeuronAction (task=IN). */
    private static LegacyScreen interneurons() {
        Part part = new Part("General parameters", Layout.MATRIX,
                (conf, sel) -> conf.getNeuronTypes(sel.get("pool"), ReMoto.IN),
                item -> ((NeuronVO) item).getType(),
                List.of(Field.number("Threshold", "mV", "threshold1", "threshold2"),
                        Field.number("Membrane specific capacitance", "μF/cm²", "cm"),
                        Field.number("Specific resistance", "Ωcm²", "rmSoma1"),
                        Field.number("Total area", "μm²", "totalArea")));
        return new LegacyScreen("interneuron", "Parameters of the interneurons",
                List.of(new SelectorDef("pool", "Pool", conf -> options(pools(conf, false, true)), ReMoto.IN_ext)),
                List.of(part),
                (conf, sel, items) -> {
                    conf.setChangedConfiguration(true);
                    conf.setListNeuronTypes(items.get(0));
                },
                List.of());
    }

    /** sensory.jsp / NeuronAction (task=AF). */
    private static LegacyScreen sensoryFibers() {
        Part part = new Part(null, Layout.MATRIX,
                (conf, sel) -> conf.getNeuronTypes(sel.get("pool"), ReMoto.AF),
                item -> ((NeuronVO) item).getType(),
                List.of(Field.number("Axon threshold", "mA", "axonThreshold1", "axonThreshold2"),
                        Field.number("Axon conduction velocity", "m/s", "axonVelocity1", "axonVelocity2")));
        return new LegacyScreen("sensory", "Parameters of the sensory fibers",
                List.of(new SelectorDef("pool", "Pool", conf -> options(pools(conf, true, false)), ReMoto.SOL)),
                List.of(part),
                (conf, sel, items) -> {
                    conf.setChangedConfiguration(true);
                    conf.setListNeuronTypes(items.get(0));
                },
                List.of());
    }

    // ------------------------------------------------------------------ Biomechanical Parameters

    /** motorunit.jsp / MotorUnitAction. */
    private static LegacyScreen motorUnits() {
        Part muscle = new Part("Muscle model parameters", Layout.MATRIX,
                (conf, sel) -> conf.getMotorUnitTypes(sel.get("pool")),
                item -> ((MotorUnitVO) item).getType(),
                List.of(Field.number("Twitch peak", "N", "twitchTension1", "twitchTension2"),
                        Field.number("Tetanic force", "N", "maxTension1", "maxTension2"),
                        Field.number("Twitch time-to-peak", "ms", "contractionTime1", "contractionTime2")));
        Part emg = new Part("EMG model parameters", Layout.MATRIX,
                (conf, sel) -> conf.getMotorUnitTypes(sel.get("pool")),
                item -> ((MotorUnitVO) item).getType(),
                List.of(Field.number("MUAP scale factor", "mV", "ampEMG1", "ampEMG2"),
                        Field.number("MUAP duration factor", "ms", "lambdaEMG1", "lambdaEMG2")));
        Part model = confPart(null, List.of(muscleModel(), muDistribution()));
        return new LegacyScreen("motorunit", "Parameters of the muscle units",
                List.of(new SelectorDef("pool", "Pool", conf -> options(pools(conf, true, false)), ReMoto.SOL)),
                List.of(model, muscle, emg),
                (conf, sel, items) -> conf.setListMotorUnitTypes(items.get(1)),
                List.of(SETTINGS_NOTE));
    }

    // ------------------------------------------------------------------ campos ligados à Configuration

    /** Escolhas de biomechanicalElements.jsp / motorunit.jsp (valores lidos pela JointFactory). */
    private static Field muscleModel() {
        return Field.choice("Muscle model", "cdMuscleModel",
                ReMoto.SOCDS, "Second-order critically-damped system", ReMoto.Raikova, "Raikova-Aladjov Model",
                ReMoto.Hill, "Hill-type Model", ReMoto.DistributionMoments, "Distributed-Moments Model");
    }

    private static Field jointModel() {
        return Field.choice("Joint model", "cdJointModel",
                "isometric", "Isometric", "pendulum", "Inverted Pendulum", "position", "Position Task");
    }

    private static Field muDistribution() {
        return Field.choice("Motor unit distribution in a muscle section",
                "muDistribution", "1", "preset", "2", "random", "3", "linear");
    }

    private static final String SETTINGS_NOTE = "Model choices, gains and angles were not stored by the original"
            + " ConfigurationDAO (they lasted only for the session); here they are saved with the scenario.";

    /** Uma "linha" só: a própria Configuration, como os formulários que usavam conf.get/set direto. */
    private static Part confPart(String title, List<Field> fields) {
        return new Part(title, Layout.MATRIX, (conf, sel) -> List.of(conf), item -> "Value", fields);
    }

    /** miscellaneous.jsp / MiscellaneousAction. */
    private static LegacyScreen miscellaneous() {
        Part part = new Part(null, Layout.LIST,
                (conf, sel) -> conf.getMiscellaneous(),
                item -> {
                    MiscellaneousVO m = (MiscellaneousVO) item;
                    return m.getDescription() == null || m.getDescription().isBlank() ? m.getProperty() : m.getDescription();
                },
                List.of(Field.number("Value", null, "value")));
        return new LegacyScreen("miscellaneous", "Miscellaneous", List.of(), List.of(part),
                (conf, sel, items) -> {
                    conf.setChangedConfiguration(true);
                    conf.setMiscellaneous(new ArrayList<>(items.get(0)));
                    for (String p : new String[]{ReMoto.afAxonRefractoryPeriod, ReMoto.mnAxonRefractoryPeriod,
                            ReMoto.mnSomaRefractoryPeriod, ReMoto.inRcSomaRefractoryPeriod, ReMoto.inIaSomaRefractoryPeriod,
                            ReMoto.inIbSomaRefractoryPeriod, ReMoto.gIISomaRefractoryPeriod}) {
                        if (conf.getMiscellaneous(p) < ReMoto.minRefractoryPeriod) {
                            conf.setMiscellaneous(p, ReMoto.minRefractoryPeriod);
                        }
                    }
                    if (conf.getMiscellaneous(ReMoto.cellsPerSlice) < ReMoto.minCellsPerSlice) {
                        conf.setMiscellaneous(ReMoto.cellsPerSlice, ReMoto.minCellsPerSlice);
                    }
                    if (conf.getMiscellaneous(ReMoto.gammaCa) < 0) {
                        conf.setMiscellaneous(ReMoto.gammaCa, 0);
                    } else if (conf.getMiscellaneous(ReMoto.gammaCa) > 1) {
                        conf.setMiscellaneous(ReMoto.gammaCa, 1);
                    }
                },
                List.of("Refractory periods are at least " + ReMoto.minRefractoryPeriod + " ms; gammaCa stays in [0, 1]."));
    }

    // ------------------------------------------------------------------ Stimulation

    /** stimulation.jsp / StimulationAction. */
    private static LegacyScreen nerveStimulation() {
        Part part = new Part(null, Layout.LIST,
                (conf, sel) -> conf.getNerves(),
                item -> ((NerveVO) item).getCdNerve(),
                List.of(Field.bool("Active", "active"),
                        Field.number("Distance to spinal entry", "m", "stimulusSpinalEntry"),
                        Field.number("Distance to end plate", "m", "stimulusEndPlate"),
                        Field.number("Start", "ms", "tini"),
                        Field.number("Stop", "ms", "tfin"),
                        Field.number("Intensity", "mA", "amp"),
                        Field.number("Frequency", "Hz", "freq"),
                        Field.choice("Modulation", "cdSignal", "none", "none", "constant", "constant", "ramp", "ramp",
                                "sine", "sine", "random", "random", "trapezoid", "trapezoid", "triangle", "triangle",
                                "stochastic", "stochastic"),
                        Field.number("Width", null, "width")));
        Part order = confPart(null, List.of(Field.choice("Motor-unit recruitment order", "recruitmentOrderFES",
                "linear", "Linear")));
        return new LegacyScreen("stimulation", "Nerve Stimulation", List.of(), List.of(order, part),
                (conf, sel, items) -> {
                    for (Object o : items.get(1)) {
                        NerveVO nerve = (NerveVO) o;
                        if (nerve.getTfin() > conf.getTFin()) {
                            conf.setTFin(nerve.getTfin());
                        }
                    }
                    conf.setNerves(new ArrayList<>(items.get(1)));
                },
                List.of("Pulse width: 1 ms. A stop time after the end of the simulation extends its duration"
                        + " (as in the original StimulationAction)."));
    }

    /** injectedCurrent.jsp / InjectedCurrentAction. */
    private static LegacyScreen injectedCurrent() {
        Part part = new Part(null, Layout.LIST,
                (conf, sel) -> conf.getInjectedCurrents(sel.get("pool")),
                item -> ((Current) item).getCdNeuronType(),
                List.of(Field.bool("Active", "active"),
                        Field.choice("Neuron compartment", "compartment", "S", "soma", "D", "dendrite"),
                        Field.number("Amplitude", "nA", "imax"),
                        Field.choice("Modulating signal", "cdSignal", "none", "none", "constant", "constant",
                                "ramp", "ramp", "random", "random", "sine", "sine", "square", "square",
                                "trapezoid", "trapezoid", "triangle", "triangle"),
                        Field.number("Start", "ms", "ini"),
                        Field.number("Stop", "ms", "fin"),
                        Field.number("Peak", "nA", "amp"),
                        Field.number("Frequency", "Hz", "freq"),
                        Field.number("Delay", "ms", "delay"),
                        Field.number("Width", null, "width")));
        return new LegacyScreen("injectedCurrent", "Injected Current",
                List.of(new SelectorDef("pool", "Pool", conf -> options(pools(conf, true, true)), ReMoto.SOL)),
                List.of(part),
                (conf, sel, items) -> {
                    for (Object o : items.get(0)) {
                        Current inj = (Current) o;
                        if (inj.getAmp() > ReMoto.maxAmpCurrent) {
                            inj.setAmp(ReMoto.maxAmpCurrent);
                        }
                        if (inj.getImax() > ReMoto.maxAmpCurrent) {
                            inj.setImax(ReMoto.maxAmpCurrent);
                        }
                    }
                    conf.setListInjectedCurrents(new ArrayList<>(items.get(0)));
                },
                List.of("Amplitudes are limited to " + ReMoto.maxAmpCurrent + " nA."));
    }

    // ------------------------------------------------------------------ Neural Parameters (cont.)

    /** conductance.jsp / ConductanceAction: as cinco condutâncias dependentes de voltagem de um tipo. */
    private static LegacyScreen conductances() {
        List<SelectorDef> selectors = List.of(
                new SelectorDef("pool", "Pool", conf -> options(pools(conf, true, true)), ReMoto.SOL),
                new SelectorDef("type", "Neuron", (conf, sel) -> {
                    List<Field.Option> types = new ArrayList<>();
                    for (String category : new String[]{ReMoto.MN, ReMoto.IN}) {
                        for (Object o : conf.getNeuronTypes(sel.get("pool"), category)) {
                            String type = ((NeuronVO) o).getType();
                            types.add(new Field.Option(type, type));
                        }
                    }
                    return types;
                }, ReMoto.S));
        Part part = new Part(null, Layout.LIST,
                (conf, sel) -> {
                    List<Object> list = new ArrayList<>();
                    String[][] specs = {{ReMoto.gNaM, ReMoto.excitatory}, {ReMoto.gNaH, ReMoto.excitatory},
                            {ReMoto.gKN, ReMoto.inhibitory}, {ReMoto.gKQ, ReMoto.inhibitory},
                            {ReMoto.gCaP, ReMoto.excitatory}};
                    for (String[] spec : specs) {
                        ConductanceVO vo = conf.getMarcovType(sel.get("pool"), spec[0] + "-" + sel.get("type"), spec[1]);
                        if (vo != null) {
                            list.add(vo);
                        }
                    }
                    return list;
                },
                item -> ((ConductanceVO) item).getCdConductanceType(),
                List.of(Field.number("Maximum conductance", "mS/cm²", "gmax"),
                        Field.number("Equilibrium potential*", "mV", "e"),
                        Field.number("Time-to-peak", "ms", "tpeak"),
                        Field.number("Peak value of alpha", "1/ms", "alpha"),
                        Field.number("Peak value of beta", "1/ms", "beta")));
        return new LegacyScreen("conductance", "Parameters of the voltage-dependent conductances", selectors,
                List.of(part),
                (conf, sel, items) -> {
                    conf.setChangedConfiguration(true);
                    conf.setNeuronIonicConductances(new ArrayList<>(items.get(0)));
                },
                List.of("* with respect to resting potential"));
    }

    /** markov.jsp / MarkovAction ("Kinetic Properties" das sinapses de um pool). */
    private static LegacyScreen kineticProperties() {
        Part part = new Part(null, Layout.LIST,
                (conf, sel) -> conf.getSynapticDynamics(sel.get("pool")),
                item -> {
                    ConductanceVO c = (ConductanceVO) item;
                    return c.getCdConductanceType() + (c.getCdNucleusPre() == null ? "" : " (from " + c.getCdNucleusPre() + ")");
                },
                List.of(Field.number("Transmitter", "mM", "tmax"),
                        Field.number("Time-to-peak", "ms", "tpeak"),
                        Field.number("Forward rate", "1/ms", "alpha"),
                        Field.number("Backward rate", "1/ms", "beta"),
                        Field.number("Reversal potential", "mV", "e")));
        return new LegacyScreen("markov", "Kinetic Properties",
                List.of(new SelectorDef("pool", "Pool", conf -> options(pools(conf, true, true)), ReMoto.SOL)),
                List.of(part),
                (conf, sel, items) -> {
                    conf.setChangedConfiguration(true);
                    conf.setListMarkovTypes(new ArrayList<>(items.get(0)));
                },
                List.of());
    }

    /** dynamics.jsp / DynamicsAction: depressão ou facilitação de cada sinapse do pool. */
    private static LegacyScreen synapticDynamics() {
        Part part = new Part(null, Layout.LIST,
                (conf, sel) -> {
                    List<Object> list = new ArrayList<>();
                    for (Object o : conf.getDynamicTypes()) {
                        if (sel.get("pool").equals(((DynamicVO) o).getCdNucleus())) {
                            list.add(o);
                        }
                    }
                    return list;
                },
                item -> {
                    DynamicVO d = (DynamicVO) item;
                    return d.getCdNucleusPre() + " → " + d.getCdNucleus() + " (" + d.getCdConductanceType() + ")";
                },
                List.of(Field.choice("Type", "dynamicType", "N", "none", "D", "Depressing", "F", "Facilitating"),
                        Field.number("Variation", "%", "variation"),
                        Field.number("Time constant", "ms", "tau")));
        return new LegacyScreen("dynamics", "Synaptic dynamics",
                List.of(new SelectorDef("pool", "Pool", conf -> options(pools(conf, true, true)), ReMoto.SOL)),
                List.of(part),
                (conf, sel, items) -> {
                    conf.setChangedConfiguration(true);
                    for (Object o : items.get(0)) {
                        conf.setDynamicType((DynamicVO) o);
                    }
                },
                List.of());
    }

    // ------------------------------------------------------------------ Proprioceptors

    /** muscleSpindle.jsp / MuscleSpindleAction. */
    private static LegacyScreen muscleSpindle() {
        Part part = confPart(null, List.of(
                Field.choice("Model", "cdSpindleModel",
                        ReMoto.spindleModelMileusnic, "Mileusnic", ReMoto.spindleModelProchazka, "Prochazka"),
                Field.number("Gamma dynamic", "spikes/s", "gammaDynamic"),
                Field.number("Gamma static", "spikes/s", "gammaStatic"),
                Field.number("Gain of bag1 fiber primary afferent activity", null, "primaryBag1Gain"),
                Field.number("Gain of bag 2 and chain fibers primary afferent activity", null, "primaryBag2AndChainGain"),
                Field.number("Gain of bag 2 and chain fibers secondary afferent activity", null, "secondaryBag2AndChainGain")));
        return new LegacyScreen("muscleSpindle", "Muscle Spindle", List.of(), List.of(part),
                (conf, sel, items) -> { }, List.of(SETTINGS_NOTE));
    }

    /** golgiTendonOrgan.jsp: só a escolha do modelo (Crago). */
    private static LegacyScreen golgiTendonOrgan() {
        Part part = confPart(null, List.of(
                Field.choice("Model", "cdGtoModel", ReMoto.lumpedGtoModelCrago, "Crago")));
        return new LegacyScreen("golgiTendonOrgan", "Golgi Tendon Organ", List.of(), List.of(part),
                (conf, sel, items) -> { }, List.of());
    }

    // ------------------------------------------------------------------ Biomechanical Elements / Parameters

    /** biomechanicalElements.jsp / BiomechanicalElementsAction. */
    private static LegacyScreen biomechanicalElements() {
        Part part = confPart(null, List.of(jointModel(), muscleModel()));
        return new LegacyScreen("biomechanicalElements", "Biomechanical Elements", List.of(), List.of(part),
                (conf, sel, items) -> { },
                List.of("Joint: Ankle. The converted scenarios only have data for the ankle nuclei"
                                + " (SOL, MG, LG, TA); the metacarpophalangeal and wrist joints are not available.",
                        "Per-muscle activation is set in Structure → Neural Elements (the original action did"
                                + " not save the muscle checkboxes of this screen).",
                        SETTINGS_NOTE));
    }

    /** joint.jsp / JointAction. */
    private static LegacyScreen joint() {
        Part part = confPart(null, List.of(
                Field.number("Ankle initial angle", "deg", "jointAngle"),
                Field.number("Knee angle", "deg", "kneeAngle")));
        return new LegacyScreen("joint", "Joint Parameters", List.of(), List.of(part),
                (conf, sel, items) -> { },
                List.of("The joint model is chosen in Biomechanical Elements.", SETTINGS_NOTE));
    }

    /** musculotendon.jsp / MusculotendonAction. */
    private static LegacyScreen musculotendon() {
        Part socds = new Part("Twitch parameters (second-order critically-damped system)", Layout.MATRIX,
                (conf, sel) -> conf.getMotorUnitTypes(sel.get("pool")),
                item -> ((MotorUnitVO) item).getType(),
                List.of(Field.number("Peak", "N", "twitchTension1", "twitchTension2"),
                        Field.number("Time-to-peak", "ms", "contractionTime1", "contractionTime2")));
        Part raikova = new Part("Twitch parameters (Raikova-Aladjov model)", Layout.MATRIX,
                (conf, sel) -> conf.getMotorUnitTypes(sel.get("pool")),
                item -> ((MotorUnitVO) item).getType(),
                List.of(Field.number("Peak", "N", "twitchTension1Raikova", "twitchTension2Raikova"),
                        Field.number("Time-to-peak", "ms", "contractionTime1Raikova", "contractionTime2Raikova"),
                        Field.number("Half relaxation time", "ms", "halfRelaxationTimeRaikova1", "halfRelaxationTimeRaikova2")));
        Part hill = new Part("Mechanical parameters (Hill-type model)", Layout.MATRIX,
                (conf, sel) -> {
                    MuscleVO vo = conf.getMuscle(sel.get("pool"), ReMoto.Hill);
                    return vo == null ? List.of() : List.of(vo);
                },
                item -> "Value",
                List.of(Field.number("Optimal Length", "m", "optimalLength"),
                        Field.number("Maximum Muscle Force", "N", "maximumMuscleForce"),
                        Field.number("Mass", "Kg", "muscleMass"),
                        Field.number("Viscosity Coeficient", "F0.s/L0M", "viscosityCoeficient"),
                        Field.number("Elastic Coeficient (Parallel Element)", "F0/L0M", "elasticCoeficientOfParallelElement"),
                        Field.number("Pennation Angle", "deg", "pennationAngle"),
                        Field.number("Slack Tendon Length", "m", "slackTendonLength"),
                        Field.number("Tendon Stiffness", "F0/L0T", "k_T"),
                        Field.number("Toe curvature", "-", "c_T"),
                        Field.number("Length at linear region onset", "L0T", "LR_T")));
        return new LegacyScreen("musculotendon", "Musculotendon Parameters",
                List.of(new SelectorDef("pool", "Muscle", conf -> options(pools(conf, true, false)), ReMoto.SOL)),
                List.of(socds, raikova, hill),
                (conf, sel, items) -> {
                    conf.setTwitchParameters(items.get(0));
                    if (!items.get(2).isEmpty()) {
                        conf.setMuscle(sel.get("pool"), ReMoto.Hill, (MuscleVO) items.get(2).get(0));
                    }
                },
                List.of("The parameters used depend on the muscle model chosen in Biomechanical Elements."));
    }

    /** emg.jsp / EMGAction. */
    private static LegacyScreen electromyogram() {
        Part model = confPart(null, List.of(
                Field.choice("MUAP model", "cdEMGModel", ReMoto.emgModelHermiteRodriguez, "Hermite-Rodriguez"),
                muDistribution()));
        Part units = new Part("Model parameters", Layout.MATRIX,
                (conf, sel) -> conf.getMotorUnitTypes(sel.get("pool")),
                item -> ((MotorUnitVO) item).getType(),
                List.of(Field.number("MUAP scale factor (A MUAP)", "mV", "ampEMG1", "ampEMG2"),
                        Field.number("MUAP duration factor (λ MUAP)", "ms", "lambdaEMG1", "lambdaEMG2")));
        return new LegacyScreen("emg", "Electromyogram",
                List.of(new SelectorDef("pool", "Muscle", conf -> options(pools(conf, true, false)), ReMoto.SOL)),
                List.of(model, units),
                (conf, sel, items) -> conf.setEMGParameters(items.get(1)),
                List.of("The new platform does not export the EMG signal yet; these parameters are kept for"
                        + " when it does.", SETTINGS_NOTE));
    }

    // ------------------------------------------------------------------ apoio

    /** Núcleos motores e/ou de interneurônios, sem o DT (como os Actions originais). */
    static List<Nucleus> pools(Configuration conf, boolean motor, boolean interneurons) {
        List<Nucleus> list = new ArrayList<>();
        if (motor) {
            for (Object o : conf.getNuclei(ReMoto.MNs)) {
                Nucleus n = (Nucleus) o;
                if (!n.getCd().equals(ReMoto.DT) && !n.getCd().equals(ReMoto.IN_ext) && !n.getCd().equals(ReMoto.IN_flex)) {
                    list.add(n);
                }
            }
        }
        if (interneurons) {
            for (Object o : conf.getNuclei(ReMoto.INs)) {
                Nucleus n = (Nucleus) o;
                if (!n.getCd().equals(ReMoto.DT)) {
                    list.add(n);
                }
            }
        }
        return list;
    }

    private static List<Field.Option> options(List<Nucleus> nuclei) {
        return nuclei.stream().map(n -> new Field.Option(n.getCd(), n.getName())).toList();
    }

    private static String category(String cd) {
        return switch (cd) {
            case ReMoto.MN -> "motoneurons";
            case ReMoto.IN -> "interneurons";
            case ReMoto.AF -> "sensory afferents";
            default -> cd;
        };
    }
}
