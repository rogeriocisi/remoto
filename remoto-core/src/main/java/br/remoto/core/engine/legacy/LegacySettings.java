package br.remoto.core.engine.legacy;

import br.remoto.core.simulation.ModulatingSignal;
import br.remoto.core.simulation.ReMoto;
import br.remoto.core.simulation.setup.ConductanceVO;
import br.remoto.core.simulation.setup.Configuration;
import br.remoto.core.simulation.setup.NerveVO;
import br.remoto.core.simulation.setup.NeuronVO;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Propriedades da {@code Configuration} que as telas do site antigo editavam, mas que o
 * {@code ConfigurationDAO} original não gravava: valiam só durante a sessão e voltavam ao padrão
 * ao recarregar. Na nova plataforma elas ficam no campo {@code settings} do cenário e são aplicadas
 * depois do carregamento pelo DAO, por cima dos padrões dele.
 */
public final class LegacySettings {

    /** Na ordem das telas: Biomechanical Elements, Muscle Units/EMG, Stimulation, Joint, Muscle Spindle. */
    public static final List<String> PROPERTIES = List.of(
            "cdJointModel", "cdMuscleModel", "cdSpindleModel", "cdGtoModel", "cdEMGModel",
            "muDistribution", "recruitmentOrderFES", "jointAngle", "kneeAngle",
            "gammaDynamic", "gammaStatic", "primaryBag1Gain", "primaryBag2AndChainGain",
            "secondaryBag2AndChainGain");

    /**
     * Os neurônios que guardam sinais, escolhidos nas telas de motoneurônios e interneurônios
     * ("Index of MNs that will have signals stored"): "núcleo:tipo" → os dois índices; -1 é o padrão
     * (o primeiro e o do meio do tipo).
     */
    public static final String STORED_NEURONS = "storedNeurons";

    /**
     * O Std dos tratos descendentes (tela Descending Commands): no processo gama, a ordem, que deve
     * ser inteira; no gaussiano, o desvio-padrão do intervalo (ms). O {@code ConfigurationDAO}
     * original gravava na coluna STD outro campo ({@code order}, que nada lê) e o Std se perdia,
     * deixando os tratos gama mudos depois de t = 0. Aqui fica no settings: "DT:tipo" → valor;
     * sem valor, vale o do DAO (0).
     */
    public static final String TRACT_STD = "tractStd";

    /**
     * Sinais no tempo, que o site antigo não tinha: o ângulo imposto ao tornozelo (graus, somado ao
     * inicial, no modelo de articulação "imposed") e os disparos fusimotores dinâmico e estático
     * (Hz, somados aos constantes). Cada um é a forma e os parâmetros de um sinal modulante; só é
     * gravado quando tem forma ("none" é o padrão: o comportamento do original).
     */
    public static final List<String> SIGNALS = List.of("jointAngleSignal", "gammaDynamicSignal", "gammaStaticSignal");

    /**
     * O pulso e o limiar do estímulo de cada nervo, que o site antigo não deixava mudar: nervo →
     * {@code pulseWidth} (ms), {@code thresholdModel} ("current", o do original, ou "charge", por
     * carga com vazamento) e as constantes de tempo {@code tauMotor} e {@code tauSensory} (ms). Só é
     * gravado o nervo que sai do padrão (pulso de 1 ms, limiar por corrente).
     */
    public static final String NERVE_STIMULUS = "nerveStimulus";

    /**
     * A ordem do processo gama do disparo das fibras aferentes pelo receptor, que no original era
     * fixa no código (Ia 25, Ib 30, II 400): "músculo:tipo" → ordem inteira. Sem valor, a do original.
     */
    public static final String AFFERENT_GAMMA_ORDER = "afferentGammaOrder";

    /**
     * O decaimento com a distância de cada tipo de sinapse, que o original só tinha entre células
     * de Renshaw e motoneurônios (na tela Miscellaneous): "núcleoPré>núcleo:tipo" → dec (mm²), com o
     * peso {@code dec / (dec + d²)}. Sem valor, peso 1.
     */
    public static final String SYNAPSE_DISTANCE_DECAY = "synapseDistanceDecay";

    /**
     * Os pontos da forma "series" dos tratos descendentes, que o original não tinha: "DT:tipo" →
     * lista de pares [tempo (ms), valor]. Com o alvo "rate", o valor soma à taxa do intervalo
     * médio (Hz); com o intervalo médio 0, é a própria taxa.
     */
    public static final String TRACT_SERIES = "tractSeries";

    private static final List<String> SIGNAL_FIELDS =
            List.of("tini", "tfin", "amp", "freq", "width", "delay", "modFreq", "modFactor");

    private LegacySettings() {
    }

    static void apply(Configuration conf, Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return;
        }
        for (String property : PROPERTIES) {
            if (!settings.containsKey(property) || settings.get(property) == null) {
                continue;
            }
            PropertyDescriptor pd = descriptor(property);
            Object raw = settings.get(property);
            Class<?> type = pd.getPropertyType();
            Object value;
            if (type == int.class) {
                value = ((Number) number(raw)).intValue();
            } else if (type == double.class) {
                value = ((Number) number(raw)).doubleValue();
            } else {
                value = raw.toString();
            }
            invoke(pd.getWriteMethod(), conf, value);
        }
        for (String key : SIGNALS) {
            if (settings.get(key) instanceof Map<?, ?> values) {
                ModulatingSignal signal = signal(conf, key);
                if (values.get("shape") != null) {
                    signal.setCdSignal(values.get("shape").toString());
                }
                for (String field : SIGNAL_FIELDS) {
                    if (values.get(field) != null) {
                        invoke(signalDescriptor(field).getWriteMethod(), signal,
                                ((Number) number(values.get(field))).doubleValue());
                    }
                }
            }
        }
        if (settings.get(TRACT_STD) instanceof Map<?, ?> std) {
            for (Object o : conf.getNeuronTypes()) {
                if (o instanceof NeuronVO neu && ReMoto.TR.equals(neu.getCategory())
                        && std.get(neu.getCdNucleus() + ":" + neu.getType()) != null) {
                    neu.setStd(((Number) number(std.get(neu.getCdNucleus() + ":" + neu.getType()))).doubleValue());
                }
            }
        }
        if (settings.get(AFFERENT_GAMMA_ORDER) instanceof Map<?, ?> orders) {
            for (Object o : conf.getNeuronTypes()) {
                if (o instanceof NeuronVO neu && ReMoto.AF.equals(neu.getCategory())
                        && orders.get(neu.getCdNucleus() + ":" + neu.getType()) != null) {
                    neu.setGammaOrder(((Number) number(orders.get(neu.getCdNucleus() + ":" + neu.getType()))).intValue());
                }
            }
        }
        if (settings.get(TRACT_SERIES) instanceof Map<?, ?> series) {
            for (Object o : conf.getNeuronTypes()) {
                if (o instanceof NeuronVO neu && ReMoto.TR.equals(neu.getCategory())
                        && series.get(neu.getCdNucleus() + ":" + neu.getType()) instanceof List<?> points) {
                    double[] flat = new double[points.size() * 2];
                    for (int i = 0; i < points.size(); i++) {
                        if (!(points.get(i) instanceof List<?> pair) || pair.size() != 2) {
                            throw new IllegalArgumentException("tractSeries: cada ponto é [tempo, valor]");
                        }
                        flat[2 * i] = ((Number) number(pair.get(0))).doubleValue();
                        flat[2 * i + 1] = ((Number) number(pair.get(1))).doubleValue();
                    }
                    neu.setRateSeriesPoints(flat);
                }
            }
        }
        if (settings.get(SYNAPSE_DISTANCE_DECAY) instanceof Map<?, ?> decays && conf.getSynapseTypes() != null) {
            for (Object o : conf.getSynapseTypes().values()) {
                if (o instanceof ConductanceVO g && decays.get(g.settingsKey()) != null) {
                    g.setDistanceDecay(((Number) number(decays.get(g.settingsKey()))).doubleValue());
                }
            }
        }
        if (settings.get(NERVE_STIMULUS) instanceof Map<?, ?> nerves) {
            for (Object o : nerves(conf)) {
                if (o instanceof NerveVO nerve && nerves.get(nerve.getCdNerve()) instanceof Map<?, ?> values) {
                    if (values.get("pulseWidth") != null) {
                        nerve.setPulseWidth(((Number) number(values.get("pulseWidth"))).doubleValue());
                    }
                    if (values.get("thresholdModel") != null) {
                        nerve.setThresholdModel(values.get("thresholdModel").toString());
                    }
                    if (values.get("tauMotor") != null) {
                        nerve.setTauMotor(((Number) number(values.get("tauMotor"))).doubleValue());
                    }
                    if (values.get("tauSensory") != null) {
                        nerve.setTauSensory(((Number) number(values.get("tauSensory"))).doubleValue());
                    }
                }
            }
        }
        if (settings.get(STORED_NEURONS) instanceof Map<?, ?> stored) {
            for (Object o : conf.getNeuronTypes()) {
                if (o instanceof NeuronVO neu && stored.get(neu.getCdNucleus() + ":" + neu.getType()) instanceof List<?> idx) {
                    if (!idx.isEmpty()) {
                        neu.setIndexStoreVm1(((Number) number(idx.get(0))).intValue());
                    }
                    if (idx.size() > 1) {
                        neu.setIndexStoreVm2(((Number) number(idx.get(1))).intValue());
                    }
                }
            }
        }
    }

    static Map<String, Object> capture(Configuration conf) {
        Map<String, Object> settings = new LinkedHashMap<>();
        for (String property : PROPERTIES) {
            Object value = invoke(descriptor(property).getReadMethod(), conf);
            if (value != null) {
                settings.put(property, value);
            }
        }
        Map<String, Object> stored = new LinkedHashMap<>();
        for (Object o : conf.getNeuronTypes()) {
            if (o instanceof NeuronVO neu && (ReMoto.MN.equals(neu.getCategory()) || ReMoto.IN.equals(neu.getCategory()))) {
                int[] idx = neu.storedIndexes();
                if (idx[0] != -1 || idx[1] != -1) {
                    stored.put(neu.getCdNucleus() + ":" + neu.getType(), List.of(idx[0], idx[1]));
                }
            }
        }
        if (!stored.isEmpty()) {
            settings.put(STORED_NEURONS, stored);
        }
        Map<String, Object> std = new LinkedHashMap<>();
        for (Object o : conf.getNeuronTypes()) {
            if (o instanceof NeuronVO neu && ReMoto.TR.equals(neu.getCategory()) && neu.getStd() != 0) {
                std.put(neu.getCdNucleus() + ":" + neu.getType(), neu.getStd());
            }
        }
        if (!std.isEmpty()) {
            settings.put(TRACT_STD, std);
        }
        Map<String, Object> orders = new LinkedHashMap<>();
        for (Object o : conf.getNeuronTypes()) {
            if (o instanceof NeuronVO neu && ReMoto.AF.equals(neu.getCategory()) && neu.getGammaOrder() > 0) {
                orders.put(neu.getCdNucleus() + ":" + neu.getType(), neu.getGammaOrder());
            }
        }
        if (!orders.isEmpty()) {
            settings.put(AFFERENT_GAMMA_ORDER, orders);
        }
        Map<String, Object> series = new LinkedHashMap<>();
        for (Object o : conf.getNeuronTypes()) {
            if (o instanceof NeuronVO neu && ReMoto.TR.equals(neu.getCategory()) && neu.getRateSeriesPoints() != null) {
                double[] flat = neu.getRateSeriesPoints();
                List<List<Double>> points = new java.util.ArrayList<>();
                for (int i = 0; i < flat.length; i += 2) {
                    points.add(List.of(flat[i], flat[i + 1]));
                }
                series.put(neu.getCdNucleus() + ":" + neu.getType(), points);
            }
        }
        if (!series.isEmpty()) {
            settings.put(TRACT_SERIES, series);
        }
        Map<String, Object> decays = new java.util.TreeMap<>();
        if (conf.getSynapseTypes() != null) {
            for (Object o : conf.getSynapseTypes().values()) {
                if (o instanceof ConductanceVO g && g.getDistanceDecay() > 0) {
                    decays.put(g.settingsKey(), g.getDistanceDecay());
                }
            }
        }
        if (!decays.isEmpty()) {
            settings.put(SYNAPSE_DISTANCE_DECAY, decays);
        }
        Map<String, Object> nerves = new LinkedHashMap<>();
        for (Object o : nerves(conf)) {
            if (o instanceof NerveVO nerve && !nerve.hasOriginalStimulus()) {
                Map<String, Object> values = new LinkedHashMap<>();
                values.put("pulseWidth", nerve.getPulseWidth());
                values.put("thresholdModel", nerve.getThresholdModel());
                values.put("tauMotor", nerve.getTauMotor());
                values.put("tauSensory", nerve.getTauSensory());
                nerves.put(nerve.getCdNerve(), values);
            }
        }
        if (!nerves.isEmpty()) {
            settings.put(NERVE_STIMULUS, nerves);
        }
        for (String key : SIGNALS) {
            ModulatingSignal signal = signal(conf, key);
            if (signal != null && signal.getCdSignal() != null && !ReMoto.none.equals(signal.getCdSignal())) {
                Map<String, Object> values = new LinkedHashMap<>();
                values.put("shape", signal.getCdSignal());
                for (String field : SIGNAL_FIELDS) {
                    values.put(field, invoke(signalDescriptor(field).getReadMethod(), signal));
                }
                settings.put(key, values);
            }
        }
        return settings;
    }

    private static List<?> nerves(Configuration conf) {
        return conf.getAllNerves() == null ? List.of() : conf.getAllNerves();
    }

    private static ModulatingSignal signal(Configuration conf, String key) {
        return switch (key) {
            case "jointAngleSignal" -> conf.getJointAngleSignal();
            case "gammaDynamicSignal" -> conf.getGammaDynamicSignal();
            case "gammaStaticSignal" -> conf.getGammaStaticSignal();
            default -> throw new IllegalArgumentException(key);
        };
    }

    private static PropertyDescriptor signalDescriptor(String property) {
        try {
            for (PropertyDescriptor pd : Introspector.getBeanInfo(ModulatingSignal.class).getPropertyDescriptors()) {
                if (pd.getName().equals(property) && pd.getReadMethod() != null && pd.getWriteMethod() != null) {
                    return pd;
                }
            }
        } catch (IntrospectionException e) {
            throw new IllegalStateException(e);
        }
        throw new IllegalStateException("ModulatingSignal não tem a propriedade " + property);
    }

    private static Object number(Object raw) {
        return raw instanceof Number n ? n : Double.valueOf(raw.toString());
    }

    private static PropertyDescriptor descriptor(String property) {
        try {
            for (PropertyDescriptor pd : Introspector.getBeanInfo(Configuration.class).getPropertyDescriptors()) {
                if (pd.getName().equals(property) && pd.getReadMethod() != null && pd.getWriteMethod() != null) {
                    return pd;
                }
            }
        } catch (IntrospectionException e) {
            throw new IllegalStateException(e);
        }
        throw new IllegalStateException("Configuration não tem a propriedade " + property);
    }

    private static Object invoke(java.lang.reflect.Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("falha em " + method.getName(), e);
        }
    }
}
