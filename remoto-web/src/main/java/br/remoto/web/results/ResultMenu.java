package br.remoto.web.results;

import br.remoto.storage.run.ResultFiles;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Os menus da tela de resultados para uma execução, como o {@code ResultsAction.setFormProperties}
 * do site antigo montava: o tipo (elementos biomecânicos, neurais, propriedades, resumo e os
 * layouts prontos), o músculo, o elemento neural, o neurônio, o gráfico e a especificação. Só
 * aparece o que a execução tem.
 */
public final class ResultMenu {

    public record Option(String value, String label) {
    }

    /** @param stored se o gráfico é de um neurônio guardado (e não de um grupo) */
    public record GraphicOption(String value, String label, boolean stored, List<Option> specs) {
    }

    /** Um elemento neural: os núcleos e, para cada um, os neurônios e os gráficos. */
    public record Element(String value, String label, List<Option> nuclei, Map<String, List<Option>> neurons,
                          Map<String, List<GraphicOption>> graphics) {
    }

    /** Um subgráfico de um layout pronto: os mesmos campos de uma {@code ResultViews.Options}. */
    public record Subplot(String type, String element, String nucleus, String neuron, String graphic, String spec,
                          String label) {
    }

    public record Preset(String value, String label, List<Subplot> subplots) {
    }

    /**
     * @param complete false nas execuções antigas, que só têm as saídas básicas
     */
    public record Menu(List<Option> types, List<Option> muscles, Map<String, List<GraphicOption>> biomechanical,
                       Map<String, List<Option>> motorUnits, List<Element> neural, List<Option> properties,
                       List<Preset> presets, boolean complete) {
    }

    static final List<String> MUSCLES = List.of("SOL", "MG", "LG", "TA");
    private static final Map<String, String> MUSCLE_NAMES = Map.of("SOL", "Soleus", "MG", "Medial Gastrocnemius",
            "LG", "Lateral Gastrocnemius", "TA", "Tibial Anterior");
    private static final Map<String, String> TRACT_NAMES = Map.of("CM_ext", "CM_ext", "CM_flex", "CM_flex",
            "RBE_ext", "ExcINs_ext", "RBE_flex", "ExcINs_flex", "RBI_ext", "InhINs_ext", "RBI_flex", "InhINs_flex",
            "excitatory_MN", "Noise excitatory (MNs)", "inhibitory_MN", "Noise inhibitory (MNs)",
            "excitatory_RC", "Noise excitatory (RCs)", "inhibitory_RC", "Noise inhibitory (RCs)");
    private static final Map<String, String> IN_NAMES = Map.of("RC", "renshaw", "IaIn", "IaIN", "IbIn", "IbIN",
            "gII", "gII");
    private static final List<Option> MN_SPIKE_POINTS = List.of(new Option("terminal", "at Terminal"),
            new Option("soma", "at Soma"));
    private static final List<Option> AF_SPIKE_POINTS = List.of(new Option("terminal", "at Terminal"),
            new Option("estimulo", "at Stimulus Point"));
    private static final List<String> STORED_GRAPHICS = List.of("somaPotential", "dendritePotential", "conductance",
            "injectedCurrent", "VsxVdot");
    private static final List<String> PROPERTY_GRAPHICS = List.of("recruitmentThresholds", "axonConductionVelocity",
            "somaticInputResistance", "rheobase", "axonThresholds", "neuronPositions", "thresholdVsInputConductance");

    private ResultMenu() {
    }

    /** Sem menus: execuções de outros motores ficam com os gráficos simples. */
    public static Menu none() {
        return new Menu(List.of(), List.of(), Map.of(), Map.of(), List.of(), List.of(), List.of(), true);
    }

    public static Menu build(ResultCatalog catalog, Path resultsDir) {
        Set<String> names = new LinkedHashSet<>();
        catalog.entries().forEach(e -> names.add(e.name()));

        // ---- biomecânicos
        List<Option> muscles = new ArrayList<>();
        Map<String, List<GraphicOption>> biomechanical = new LinkedHashMap<>();
        Map<String, List<Option>> motorUnits = new LinkedHashMap<>();
        for (String m : MUSCLES) {
            List<GraphicOption> graphics = new ArrayList<>();
            for (Graphics.Graphic g : Graphics.BIOMECHANICAL) {
                if (available(ResultViews.biomechanicalFiles(g.code()), m, names)) {
                    graphics.add(new GraphicOption(g.code(), g.label(), false, List.of()));
                }
            }
            boolean muscle = graphics.stream().anyMatch(g -> !g.value().startsWith("joint"));
            if (!muscle) {
                continue;
            }
            muscles.add(new Option(m, MUSCLE_NAMES.get(m)));
            biomechanical.put(m, graphics);
            List<Option> units = new ArrayList<>(List.of(new Option("todos", "All MUs")));
            for (String type : types(resultsDir, catalog, "tabela-unidades-motoras-" + m)) {
                units.add(new Option(type, "All MUs " + type));
            }
            for (ResultCatalog.Entry e : catalog.entries()) {
                if (e.name().startsWith("forca-um-" + m + "-")) {
                    units.add(new Option(e.subtype() + ":" + e.index(), "MU " + e.subtype() + " " + e.index()));
                }
            }
            motorUnits.put(m, units);
        }

        // ---- neurais
        List<Element> neural = new ArrayList<>();
        Element mns = motoneurons(catalog, resultsDir, names);
        if (!mns.nuclei().isEmpty()) {
            neural.add(mns);
        }
        Element afs = afferents(catalog, resultsDir, names);
        if (!afs.nuclei().isEmpty()) {
            neural.add(afs);
        }
        Element dts = tracts(catalog, names);
        if (!dts.nuclei().isEmpty()) {
            neural.add(dts);
        }
        Element ins = interneurons(catalog, resultsDir, names);
        if (!ins.nuclei().isEmpty()) {
            neural.add(ins);
        }

        // ---- propriedades
        List<Option> properties = new ArrayList<>();
        for (ResultCatalog.Entry e : catalog.entries()) {
            String n = e.name();
            if (n.startsWith("tabela-motoneuronios-")) {
                properties.add(new Option("mns:" + e.nucleus(), "Motoneurons - " + e.nucleus()));
            } else if (n.startsWith("tabela-unidades-motoras-")) {
                properties.add(new Option("mus:" + e.nucleus(), "Motor units - " + e.nucleus()));
            } else if (n.startsWith("tabela-aferentes-")) {
                properties.add(new Option("afs:" + e.nucleus(), "Afferent fibers - " + e.nucleus()));
            } else if (n.startsWith("tabela-interneuronios-")) {
                properties.add(new Option("ins:" + e.nucleus(), "Interneurons - " + e.nucleus()));
            }
        }

        List<Preset> presets = presets(names);
        List<Option> types = new ArrayList<>();
        if (!muscles.isEmpty()) {
            types.add(new Option("biomechanical", "Biomechanical Elements"));
        }
        if (!neural.isEmpty()) {
            types.add(new Option("neural", "Neural Elements"));
        }
        if (!properties.isEmpty()) {
            types.add(new Option("properties", "Properties"));
        }
        if (names.contains("tabela-resumo")) {
            types.add(new Option("summary", "Summary"));
        }
        for (Preset p : presets) {
            types.add(new Option("preset:" + p.value(), p.label()));
        }
        return new Menu(types, muscles, biomechanical, motorUnits, neural, properties, presets, catalog.complete());
    }

    private static Element motoneurons(ResultCatalog catalog, Path dir, Set<String> names) {
        List<Option> nuclei = new ArrayList<>();
        Map<String, List<Option>> neurons = new LinkedHashMap<>();
        Map<String, List<GraphicOption>> graphics = new LinkedHashMap<>();
        for (String m : MUSCLES) {
            // sem nenhum disparo, as saídas básicas não gravam disparos-<m>; a tabela diz que há motoneurônios
            if (!names.contains("disparos-" + m) && !names.contains("tabela-motoneuronios-" + m)) {
                continue;
            }
            nuclei.add(new Option(m, MUSCLE_NAMES.get(m)));
            List<Option> list = new ArrayList<>(List.of(new Option("todos", "All MNs")));
            for (String type : types(dir, catalog, "tabela-motoneuronios-" + m)) {
                list.add(new Option(type, "All MNs " + type));
            }
            boolean stored = addStored(catalog, "motoneuronios", m, "MN", list);
            neurons.put(m, list);
            boolean properties = names.contains("tabela-motoneuronios-" + m);
            boolean units = names.contains("tabela-unidades-motoras-" + m);
            List<GraphicOption> g = new ArrayList<>();
            for (Graphics.Graphic graphic : Graphics.NEURAL.get("mns")) {
                String code = graphic.code();
                boolean ok = STORED_GRAPHICS.contains(code) ? stored
                        : PROPERTY_GRAPHICS.contains(code) ? properties
                        : code.startsWith("stimulusRate") ? units && (code.equals("stimulusRate") || names.contains("forca-" + m))
                        : true;
                if (ok) {
                    List<Option> specs = code.equals("spikeTimes") && names.contains("disparos-soma-" + m) ? MN_SPIKE_POINTS
                            : code.equals("conductance") ? conductances(true) : List.of();
                    g.add(new GraphicOption(code, graphic.label(), STORED_GRAPHICS.contains(code), specs));
                }
            }
            graphics.put(m, g);
        }
        return new Element("mns", "Motoneurons", nuclei, neurons, graphics);
    }

    private static Element afferents(ResultCatalog catalog, Path dir, Set<String> names) {
        List<Option> nuclei = new ArrayList<>();
        Map<String, List<Option>> neurons = new LinkedHashMap<>();
        Map<String, List<GraphicOption>> graphics = new LinkedHashMap<>();
        for (String m : MUSCLES) {
            List<Option> list = new ArrayList<>();
            for (String type : List.of("Ia", "II", "Ib")) {
                if (names.contains("disparos-" + m + "-" + type)) {
                    list.add(new Option(type, "All " + type + " afferents"));
                }
            }
            if (list.isEmpty()) {
                continue;
            }
            nuclei.add(new Option(m, MUSCLE_NAMES.get(m)));
            neurons.put(m, list);
            List<GraphicOption> g = new ArrayList<>();
            for (Graphics.Graphic graphic : Graphics.NEURAL.get("afs")) {
                String code = graphic.code();
                boolean ok = code.equals("meanFiringRate")
                        ? names.contains("fuso-ia-" + m) || names.contains("fuso-ii-" + m) || names.contains("otg-ib-" + m)
                        : !PROPERTY_GRAPHICS.contains(code) || names.contains("tabela-aferentes-" + m);
                if (ok) {
                    g.add(new GraphicOption(code, graphic.label(), false,
                            code.equals("spikeTimes") ? AF_SPIKE_POINTS : List.of()));
                }
            }
            graphics.put(m, g);
        }
        return new Element("afs", "Afferent Fibers", nuclei, neurons, graphics);
    }

    private static Element tracts(ResultCatalog catalog, Set<String> names) {
        List<Option> list = new ArrayList<>();
        boolean means = false;
        for (ResultCatalog.Entry e : catalog.entries()) {
            if (e.name().startsWith("disparos-DT-")) {
                list.add(new Option(e.subtype(), TRACT_NAMES.getOrDefault(e.subtype(), e.subtype())));
            }
            means |= e.name().startsWith("trato-isi-medio-");
        }
        if (list.isEmpty()) {
            return new Element("dts", "Descending Tracts", List.of(), Map.of(), Map.of());
        }
        List<GraphicOption> g = new ArrayList<>();
        for (Graphics.Graphic graphic : Graphics.NEURAL.get("dts")) {
            if (!graphic.code().equals("meanISI") || means) {
                g.add(new GraphicOption(graphic.code(), graphic.label(), false, List.of()));
            }
        }
        return new Element("dts", "Descending Tracts", List.of(new Option("DT", "Descending tracts")),
                Map.of("DT", list), Map.of("DT", g));
    }

    private static Element interneurons(ResultCatalog catalog, Path dir, Set<String> names) {
        List<Option> nuclei = new ArrayList<>();
        Map<String, List<Option>> neurons = new LinkedHashMap<>();
        Map<String, List<GraphicOption>> graphics = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ResultCatalog.Entry e : catalog.entries()) {
            if ("interneuronios".equals(e.element()) && "spikeTimes".equals(e.what())) {
                seen.add(e.nucleus());
            }
        }
        for (String n : seen) {
            List<Option> list = new ArrayList<>();
            for (ResultCatalog.Entry e : catalog.entries()) {
                if (e.name().equals("disparos-" + n + "-" + e.subtype()) && "interneuronios".equals(e.element())) {
                    list.add(new Option(e.subtype(), IN_NAMES.getOrDefault(e.subtype(), e.subtype())));
                }
            }
            boolean stored = addStored(catalog, "interneuronios", n, "IN", list);
            nuclei.add(new Option(n, n));
            neurons.put(n, list);
            boolean properties = names.contains("tabela-interneuronios-" + n);
            List<GraphicOption> g = new ArrayList<>();
            for (Graphics.Graphic graphic : Graphics.NEURAL.get("ins")) {
                String code = graphic.code();
                boolean ok = STORED_GRAPHICS.contains(code) ? stored : !PROPERTY_GRAPHICS.contains(code) || properties;
                if (ok) {
                    g.add(new GraphicOption(code, graphic.label(), STORED_GRAPHICS.contains(code),
                            code.equals("conductance") ? conductances(false) : List.of()));
                }
            }
            graphics.put(n, g);
        }
        return new Element("ins", "Interneurons", nuclei, neurons, graphics);
    }

    /** Neurônios guardados do núcleo (as tabelas {@code sinais-}), no fim da lista. */
    private static boolean addStored(ResultCatalog catalog, String element, String nucleus, String category,
                                     List<Option> list) {
        boolean any = false;
        for (ResultCatalog.Entry e : catalog.entries()) {
            if (e.name().startsWith("sinais-") && element.equals(e.element()) && nucleus.equals(e.nucleus())) {
                list.add(new Option(e.subtype() + ":" + e.index(), category + " " + e.subtype() + " " + e.index()));
                any = true;
            }
        }
        return any;
    }

    private static List<Option> conductances(boolean motoneuron) {
        List<Option> list = new ArrayList<>();
        for (String g : Graphics.CONDUCTANCES) {
            if (motoneuron || !Graphics.MOTONEURON_ONLY.contains(g)) {
                list.add(new Option(g, g));
            }
        }
        return list;
    }

    /** Os tipos (S, FR, FF...) de uma tabela de propriedades, na ordem em que aparecem. */
    private static List<String> types(Path dir, ResultCatalog catalog, String table) {
        if (!catalog.has(table)) {
            return List.of();
        }
        Set<String> types = new LinkedHashSet<>();
        for (String t : ResultFiles.readTable(dir, table).texts("tipo")) {
            types.add(t);
        }
        return List.copyOf(types);
    }

    private static boolean available(List<String> files, String m, Set<String> names) {
        if (files.isEmpty()) {
            return false;
        }
        for (String f : files) {
            boolean any = false;
            for (String alternative : f.split("\\|")) {
                any |= names.contains(alternative.replace("{m}", m));
            }
            if (!any) {
                return false;
            }
        }
        return true;
    }

    /** Os layouts prontos da tela antiga ("Spikes MNs", "Forces", "General"...), com os subgráficos que existem. */
    private static List<Preset> presets(Set<String> names) {
        List<Preset> presets = new ArrayList<>();
        List<String> withMns = MUSCLES.stream().filter(m -> names.contains("disparos-" + m)).toList();
        add(presets, "spikesMNs", "Spikes MNs", withMns.stream().filter(m -> !m.equals("TA"))
                .map(m -> new Subplot("neural", "mns", m, "todos", "spikeTimes", "terminal", "MNs " + m)).toList());
        List<Subplot> ias = new ArrayList<>();
        for (String m : List.of("SOL", "MG", "LG")) {
            if (names.contains("disparos-" + m + "-Ia")) {
                ias.add(new Subplot("neural", "afs", m, "Ia", "spikeTimes", "terminal", "Ia " + m));
            }
            if (names.contains("fuso-ia-" + m)) {
                ias.add(new Subplot("neural", "afs", m, "Ia", "meanFiringRate", "", "Ia " + m));
            }
        }
        add(presets, "spikesIas", "Spikes Ias", ias);
        add(presets, "forces", "Forces", MUSCLES.stream().filter(m -> names.contains("forca-" + m))
                .map(m -> new Subplot("biomechanical", "", m, "todos", "muscleForce", "", "Force " + m)).toList());
        List<Subplot> lengths = new ArrayList<>();
        for (String m : MUSCLES) {
            if (names.contains("musculo-comprimento-normalizado-" + m)) {
                lengths.add(new Subplot("biomechanical", "", m, "todos", "muscleLengthNorm", "", "Muscle Length " + m));
            }
            if (names.contains("tendao-comprimento-" + m)) {
                lengths.add(new Subplot("biomechanical", "", m, "todos", "tendonLength", "", "Tendon Length " + m));
            }
        }
        add(presets, "lengths", "Lengths", lengths);
        List<Subplot> general = new ArrayList<>();
        for (String m : List.of("SOL", "MG", "LG")) {
            if (names.contains("emg-" + m) || names.contains("emg-bruto-" + m)) {
                general.add(new Subplot("biomechanical", "", m, "todos", "EMG", "", "EMG " + m));
            }
        }
        String first = MUSCLES.stream().filter(m -> names.contains("forca-" + m)).findFirst().orElse("SOL");
        if (names.contains("torque")) {
            general.add(new Subplot("biomechanical", "", first, "todos", "jointTorque", "", "Torque"));
        }
        if (names.contains("articulacao-angulo")) {
            general.add(new Subplot("biomechanical", "", first, "todos", "jointAngle", "", "Angle"));
        }
        add(presets, "general", "General", general);
        // o valor é o do site antigo; o rótulo, o nome que ele mostrava no menu
        Map<String, String> values = Map.of("SOL", "generalForSoleus", "MG", "generalForMG", "LG", "generalForLG",
                "TA", "generalForTA");
        Map<String, String> labels = Map.of("SOL", "General for Soleus", "MG", "General for MG",
                "LG", "General for LG", "TA", "General for TA");
        for (String m : MUSCLES) {
            List<Subplot> s = new ArrayList<>();
            if (names.contains("emg-" + m) || names.contains("emg-bruto-" + m)) {
                s.add(new Subplot("biomechanical", "", m, "todos", "EMG", "", "EMG " + m));
            }
            if (names.contains("disparos-" + m)) {
                s.add(new Subplot("neural", "mns", m, "todos", "spikeTimes", "terminal", "MNs " + m));
            }
            if (names.contains("disparos-" + m + "-Ia")) {
                s.add(new Subplot("neural", "afs", m, "Ia", "spikeTimes", "terminal", "Ia " + m));
            }
            if (names.contains("fuso-ia-" + m)) {
                s.add(new Subplot("neural", "afs", m, "Ia", "meanFiringRate", "", "Ia " + m));
            }
            if (names.contains("musculo-comprimento-normalizado-" + m)) {
                s.add(new Subplot("biomechanical", "", m, "todos", "muscleLengthNorm", "", "Muscle Length " + m));
            }
            if (names.contains("forca-" + m)) {
                s.add(new Subplot("biomechanical", "", m, "todos", "muscleForce", "", "Force " + m));
            }
            if (names.contains("torque")) {
                s.add(new Subplot("biomechanical", "", m, "todos", "jointTorque", "", "Torque"));
            }
            if (names.contains("articulacao-angulo")) {
                s.add(new Subplot("biomechanical", "", m, "todos", "jointAngle", "", "Angle"));
            }
            if (names.contains("forca-" + m)) {
                add(presets, values.get(m), labels.get(m), s);
            }
        }
        return presets;
    }

    private static void add(List<Preset> presets, String value, String label, List<Subplot> subplots) {
        if (!subplots.isEmpty()) {
            presets.add(new Preset(value, label, List.copyOf(subplots)));
        }
    }
}
