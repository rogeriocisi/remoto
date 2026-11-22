package br.remoto.core.engine.legacy;

import br.remoto.core.simulation.setup.Configuration;

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
    }

    static Map<String, Object> capture(Configuration conf) {
        Map<String, Object> settings = new LinkedHashMap<>();
        for (String property : PROPERTIES) {
            Object value = invoke(descriptor(property).getReadMethod(), conf);
            if (value != null) {
                settings.put(property, value);
            }
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
