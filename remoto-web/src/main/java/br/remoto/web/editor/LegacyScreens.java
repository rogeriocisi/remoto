package br.remoto.web.editor;

import br.remoto.web.editor.ConfAdapters.PoolPosition;
import br.remoto.web.editor.ConfAdapters.SimulationSettings;
import br.remoto.web.editor.LegacyScreen.Part;
import br.remoto.web.editor.LegacyScreen.SelectorDef;
import br.remoto.web.editor.ScreenView.Layout;
import br.remoto.core.simulation.setup.Configuration;
import br.remoto.core.simulation.Current;
import br.remoto.core.simulation.ModulatingSignal;
import br.remoto.core.simulation.ReMoto;
import br.remoto.core.simulation.setup.ConductanceVO;
import br.remoto.core.simulation.setup.DynamicVO;
import br.remoto.core.simulation.setup.MiscellaneousVO;
import br.remoto.core.simulation.setup.MotorUnitVO;
import br.remoto.core.simulation.setup.MuscleVO;
import br.remoto.core.simulation.setup.NerveVO;
import br.remoto.core.simulation.setup.NeuronVO;
import br.remoto.core.simulation.setup.Nucleus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * As telas do editor, na ordem e com os rótulos do menu do site antigo (WebContent/jsp, hoje só no repositório pai). Cada
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

    /**
     * Menu lateral do site antigo (template.dwt), com as telas já disponíveis marcadas. A duração
     * e o passo vêm primeiro (no original ficavam no fim): são o que mais se muda, e a duração é o
     * que decide se uma simulação cabe na memória do servidor.
     */
    public static List<MenuGroup> menu() {
        return List.of(
                group("Simulation", "start", "Duration and Time Step"),
                group("Structure", "input", "Descending Commands", "network", "Neural Elements"),
                group("Neural Parameters", "synapse", "Synapses", "markov", "Kinetic Properties",
                        "dynamics", "Synaptic Dynamics", "conductance", "Conductances",
                        "motoneuron", "Motoneurons", "interneuron", "Interneurons", "sensory", "Sensory Fibers"),
                group("Proprioceptors", "muscleSpindle", "Muscle Spindle", "golgiTendonOrgan", "Golgi Tendon Organ"),
                group("Biomechanical Elements", "biomechanicalElements", "Biomechanical Elements"),
                group("Biomechanical Parameters", "joint", "Joint", "motorunit", "Muscle Units",
                        "musculotendon", "Musculotendon", "emg", "Electromyogram", "miscellaneous", "Miscellaneous"),
                group("Stimulation", "stimulation", "Nerve Stimulation", "injectedCurrent", "Injected Current"));
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

    /**
     * start.jsp / StartAction ("Run → Simulation Start" no site antigo). Aqui a tela só guarda a
     * duração e o passo; quem roda é o botão "Rodar este cenário".
     */
    private static LegacyScreen simulation() {
        return new LegacyScreen("start", "Duration and Time Step", List.of(),
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
                "sine", "sine", "square", "square", "trapezoid", "trapezoid", "triangle", "triangle", "AM", "AM",
                "series", "series"};
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
        Part series = new Part("Series modulation (not in the original site)", Layout.LIST,
                (conf, sel) -> {
                    List list = conf.getNeuronTypes(ReMoto.DT, ReMoto.TR);
                    Collections.sort(list);
                    return list;
                },
                item -> {
                    NeuronVO n = (NeuronVO) item;
                    return n.getTractName() == null || n.getTractName().isBlank() ? n.getType() : n.getTractName();
                },
                List.of(Field.text("Points: time [ms] and value", "rateSeries")));
        return new LegacyScreen("input", "Descending Commands", List.of(), List.of(inputs, noises, series),
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
                List.of("Only active tracts keep their modulating signal (as in the original InputAction).",
                        "Series modulation: with the modulation \"series\", the signal follows these points,"
                                + " linearly interpolated, from the first to the last; two points at the same time"
                                + " make a step. Write them as time and value separated by a space, the points"
                                + " separated by \";\" (decimal point: 0 20; 1000 40; 1000 10; 3000 10). With the"
                                + " target \"rate\", the value is added to the rate of the ISI mean, in spikes/s;"
                                + " with the ISI mean 0, it is the rate itself (any rate profile). Start, Stop and"
                                + " the other signal fields are not used."));
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
                        Field.number("Transmission delay", "ms", "delay"),
                        Field.number("Distance decay", "mm²", "distanceDecay")));
        return new LegacyScreen("synapse", "Synaptic Parameters", selectors, List.of(part),
                (conf, sel, items) -> {
                    conf.setChangedConfiguration(true);
                    conf.setSynapseTypes(new ArrayList<>(items.get(0)));
                },
                List.of("Distance decay (not in the original site): each synapse of the type gets the weight"
                        + " dec / (dec + d\u00B2), d being the distance (mm) between the two cells in the"
                        + " caudal-rostral column, and no synapse is made below 1%. 0 = no decay (weight 1)."
                        + " Motoneurons and interneurons are placed as in the original (Miscellaneous: initial"
                        + " and final positions of each pool); the afferents of a muscle spread over its"
                        + " motoneuron pool, and the descending axons over the whole column. Between Renshaw"
                        + " cells and motoneurons, 0 keeps the declining factors of the Miscellaneous screen."));
    }

    /**
     * A família de membrana dos motoneurônios. Não estava no site antigo: a de pulso é a do
     * original e é o padrão, de modo que um cenário sem a escolha dá os resultados publicados.
     * O ganho γ da corrente de cálcio continua na tela Miscellaneous, onde sempre esteve.
     */
    private static Field motoneuronModel() {
        return Field.choice("Membrane model", "cdMotoneuronModel",
                ReMoto.mnModelPulse, "Pulse channels (original)",
                ReMoto.mnModelContinuous, "Continuous kinetics (persistent Na and Ca)");
    }

    /** motoneuron.jsp / NeuronAction (task=MN). */
    private static LegacyScreen motoneurons() {
        Part membrane = confPart("Membrane (not in the original site)", List.of(motoneuronModel()));
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
        Part stored = new Part("Additional configuration", Layout.MATRIX,
                (conf, sel) -> conf.getNeuronTypes(sel.get("pool"), ReMoto.MN),
                item -> ((NeuronVO) item).getType(),
                List.of(Field.integer("Index of MNs that will have signals stored (1)", null, "indexStoreVm1"),
                        Field.integer("Index of MNs that will have signals stored (2)", null, "indexStoreVm2")));
        Part block = new Part("Conduction block (not in the original site)", Layout.MATRIX,
                (conf, sel) -> conf.getNeuronTypes(sel.get("pool"), ReMoto.MN),
                item -> ((NeuronVO) item).getType(),
                List.of(Field.number("Blocked fraction of the pool", null, "blockedAxons")));
        return new LegacyScreen("motoneuron", "Parameters of the alpha motoneurons",
                List.of(new SelectorDef("pool", "Pool", conf -> options(pools(conf, true, false)), ReMoto.SOL)),
                // membrane é uma parte da Configuration e vai por último: as matrizes de tipos de
                // neurônio precisam seguir nos índices que o handler abaixo usa
                List.of(electrotonic, geometry, stored, block, membrane),
                (conf, sel, items) -> {
                    conf.setChangedConfiguration(true);
                    conf.setListNeuronTypes(items.get(0));
                    conf.setMerge(true);
                },
                List.of("Columns \"from\"/\"to\": values for the first and the last motoneuron of each type.",
                        "Signals stored (membrane potentials, conductances, injected current) appear in the results;"
                                + " by default, the first and the middle motoneuron of each type.",
                        "Blocked fraction of the pool: a fraction from 0 (none, as in the original) to 1 of the"
                                + " motor axons of the pool, counted from the slowest one, whose conduction is"
                                + " blocked: the spike of the soma never reaches the end-plate and the motor unit"
                                + " makes no force. The value is the same for the three types, since it is a"
                                + " fraction of the whole pool. It is the conduction block of the Fortran version"
                                + " for demyelinating diseases (de Oliveira, Watanabe and Kohn, 2022)."));
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
        Part stored = new Part("Additional configuration", Layout.MATRIX,
                (conf, sel) -> conf.getNeuronTypes(sel.get("pool"), ReMoto.IN),
                item -> ((NeuronVO) item).getType(),
                List.of(Field.integer("Index of INs that will have signals stored", null, "indexStoreVm1")));
        return new LegacyScreen("interneuron", "Parameters of the interneurons",
                List.of(new SelectorDef("pool", "Pool", conf -> options(pools(conf, false, true)), ReMoto.IN_ext)),
                List.of(part, stored),
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
        Part order = new Part("Receptor-driven firing (not in the original site)", Layout.MATRIX,
                (conf, sel) -> conf.getNeuronTypes(sel.get("pool"), ReMoto.AF),
                item -> ((NeuronVO) item).getType(),
                List.of(Field.integer("Gamma order", null, "gammaOrder")));
        return new LegacyScreen("sensory", "Parameters of the sensory fibers",
                List.of(new SelectorDef("pool", "Pool", conf -> options(pools(conf, true, false)), ReMoto.SOL)),
                List.of(part, order),
                (conf, sel, items) -> {
                    conf.setChangedConfiguration(true);
                    conf.setListNeuronTypes(items.get(0));
                },
                List.of("Gamma order: the receptor (spindle or tendon organ) drives the fiber through a gamma"
                        + " process of this integer order; the higher the order, the more regular the firing"
                        + " (coefficient of variation 1/\u221Aorder at a constant rate). 0 keeps the orders of the"
                        + " original, fixed in its code: Ia 25, Ib 30 and II 400 (the II uses 25 after its first"
                        + " spike)."));
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
                "isometric", "Isometric", "pendulum", "Inverted Pendulum", "position", "Position Task",
                ReMoto.imposedAngle, "Imposed angle (perturbation)");
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
        Part pulse = new Part("Pulse and axon threshold (not in the original site)", Layout.LIST,
                (conf, sel) -> conf.getNerves(),
                item -> ((NerveVO) item).getCdNerve(),
                List.of(Field.number("Pulse width", "ms", "pulseWidth"),
                        Field.choice("Axon threshold", "thresholdModel",
                                NerveVO.CURRENT_THRESHOLD, "By current (original)",
                                NerveVO.CHARGE_THRESHOLD, "By charge, with leak",
                                NerveVO.NODE_THRESHOLD, "Node of Ranvier"),
                        Field.number("Time constant, motor axons", "ms", "tauMotor"),
                        Field.number("Time constant, sensory axons", "ms", "tauSensory")));
        return new LegacyScreen("stimulation", "Nerve Stimulation", List.of(), List.of(order, part, pulse),
                (conf, sel, items) -> {
                    for (Object o : items.get(1)) {
                        NerveVO nerve = (NerveVO) o;
                        if (nerve.getTfin() > conf.getTFin()) {
                            conf.setTFin(nerve.getTfin());
                        }
                    }
                    conf.setNerves(new ArrayList<>(items.get(1)));
                },
                List.of("A stop time after the end of the simulation extends its duration (as in the original"
                                + " StimulationAction).",
                        "Pulse width: 1 ms in the original. Axon threshold by current (the original): an axon fires"
                                + " when the intensity passes its threshold, whatever the pulse width. By charge, with"
                                + " leak: each axon integrates the stimulus with the time constant of its kind (motor"
                                + " or sensory) and fires when the charge passes the threshold, giving the"
                                + " strength-duration curve: short pulses need more current, long pulses favor the"
                                + " axons with the longer time constant (the sensory ones, hence the H reflex). The"
                                + " thresholds of the axons keep their meaning: the current of a 1 ms pulse."
                                + " Chronaxie = time constant \u00D7 ln 2.",
                        "Node of Ranvier: each axon has, at the stimulus site, a node and its internode with the"
                                + " channels of the human axon model of Bostock (transient and persistent Na\u207A,"
                                + " fast and slow K\u207A, h current, leak and Na\u207A/K\u207A pump), with the"
                                + " motor and sensory parameters of Howells et al. (2012, J Physiol 590:1625). The"
                                + " strength-duration curve, refractoriness, supernormality and accommodation come"
                                + " from the model; the time constants above are not used. The thresholds of the axons"
                                + " keep their meaning (the current of a 1 ms pulse), and the conduction from the"
                                + " stimulus site is still a delay. Spikes that come from the soma or the receptor"
                                + " do not pass through the node model."));
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

    /**
     * Sinais no tempo que o site antigo não tinha (o ângulo imposto, o fusimotor): uma linha por
     * sinal, com os mesmos campos do comando descendente; ficam no settings do cenário.
     */
    private static Part signalPart(String title, java.util.function.Function<Configuration, List<Object>> signals) {
        // as formas de sinal da tela Descending Commands (aqui, e não num campo estático, que as telas
        // são registradas antes de os campos estáticos serem iniciados)
        String[] shapes = {"none", "none", "constant", "constant", "ramp", "ramp", "random", "random",
                "sine", "sine", "square", "square", "trapezoid", "trapezoid", "triangle", "triangle", "AM", "AM",
                "series", "series"};
        return new Part(title, Layout.LIST,
                (conf, sel) -> signals.apply(conf),
                item -> switch (String.valueOf(((ModulatingSignal) item).getCdNeuronType())) {
                    case "jointAngle" -> "Ankle angle [deg]";
                    case "gammaDynamic" -> "Gamma dynamic [spikes/s]";
                    case "gammaStatic" -> "Gamma static [spikes/s]";
                    default -> "Signal";
                },
                List.of(Field.choice("Modulation", "cdSignal", shapes),
                        Field.number("Start", "ms", "tini"),
                        Field.number("Stop", "ms", "tfin"),
                        Field.number("Peak", null, "amp"),
                        Field.number("Frequency", "Hz", "freq"),
                        Field.number("Delay", "ms", "delay"),
                        Field.number("Width", null, "width"),
                        Field.number("Modulating frequency", "Hz", "modFreq"),
                        Field.number("Modulating factor", null, "modFactor"),
                        Field.text("Series points: time [ms] and value", "seriesText")));
    }

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
        Part fusimotor = signalPart("Fusimotor drive over time (added to the values above)",
                conf -> List.of(conf.getGammaDynamicSignal(), conf.getGammaStaticSignal()));
        return new LegacyScreen("muscleSpindle", "Muscle Spindle", List.of(), List.of(part, fusimotor),
                (conf, sel, items) -> { },
                List.of(SETTINGS_NOTE,
                        "Fusimotor drive over time: each signal is added to the constant rate above (a negative"
                                + " total counts as 0); with \"none\", the rate stays constant. Only the Mileusnic"
                                + " spindle uses it, and the spindle is only integrated with the Hill muscle model."));
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
                Field.number("Knee angle", "deg", "kneeAngle"),
                Field.number("Load of the position task (fraction of the MVC)", null, "percentageOfMVC")));
        Part imposed = signalPart("Imposed ankle angle (added to the initial angle)",
                conf -> List.of(conf.getJointAngleSignal()));
        return new LegacyScreen("joint", "Joint Parameters", List.of(), List.of(part, imposed),
                (conf, sel, items) -> { },
                List.of("The joint model is chosen in Biomechanical Elements.",
                        "Load of the position task: with the joint model \"Position Task\", the fraction of the"
                                + " maximum voluntary torque (79 N·m) that the pedal opposes to the foot; it does"
                                + " nothing in the other joint models.",
                        "Imposed ankle angle: with the joint model \"Imposed angle (perturbation)\", the ankle"
                                + " follows the initial angle plus this signal, in degrees (a ramp-and-hold stretch,"
                                + " a sine, steps...), and the muscle lengths follow it. The spindles only respond"
                                + " with the Hill muscle model, which computes the fascicle length. With the"
                                + " modulation \"series\", the angle follows the points (time and value separated"
                                + " by a space, the points by \";\"), linearly interpolated: any trajectory.",
                        SETTINGS_NOTE));
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
