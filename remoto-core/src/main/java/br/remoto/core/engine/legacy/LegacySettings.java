package br.remoto.core.engine.legacy;

import br.remoto.core.simulation.ReMoto;
import br.remoto.core.simulation.setup.Configuration;
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
        if (settings.get(TRACT_STD) instanceof Map<?, ?> std) {
            for (Object o : conf.getNeuronTypes()) {
                if (o instanceof NeuronVO neu && ReMoto.TR.equals(neu.getCategory())
                        && std.get(neu.getCdNucleus() + ":" + neu.getType()) != null) {
                    neu.setStd(((Number) number(std.get(neu.getCdNucleus() + ":" + neu.getType()))).doubleValue());
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
        return settings;
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
