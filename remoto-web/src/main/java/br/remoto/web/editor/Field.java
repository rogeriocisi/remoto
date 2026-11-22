package br.remoto.web.editor;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Um campo de uma tela do editor: rótulo e unidade iguais aos do JSP original, e uma ou duas
 * propriedades do objeto editado (por exemplo {@code threshold1}/{@code threshold2}, as colunas
 * "from"/"to" do site antigo).
 */
public record Field(String label, String unit, Kind kind, List<Option> options, List<Accessor> accessors) {

    public enum Kind { NUMBER, INTEGER, BOOLEAN, CHOICE, TEXT }

    public record Option(String value, String label) {
    }

    /** Lê e grava um valor de um item (objeto do modelo original ou adaptador). */
    public interface Accessor {
        String name();

        Object get(Object item);

        /** @param raw texto vindo do formulário; {@code null} para checkbox desmarcado */
        void set(Object item, String raw, Kind kind);
    }

    public boolean paired() {
        return accessors.size() == 2;
    }

    public String labelWithUnit() {
        return unit == null || unit.isBlank() ? label : label + " [" + unit + "]";
    }

    public static Field number(String label, String unit, String... properties) {
        return new Field(label, unit, Kind.NUMBER, List.of(), beans(properties));
    }

    public static Field integer(String label, String unit, String... properties) {
        return new Field(label, unit, Kind.INTEGER, List.of(), beans(properties));
    }

    public static Field bool(String label, String property) {
        return new Field(label, null, Kind.BOOLEAN, List.of(), beans(property));
    }

    public static Field text(String label, String property) {
        return new Field(label, null, Kind.TEXT, List.of(), beans(property));
    }

    /** @param options pares valor, rótulo, valor, rótulo... (os mesmos do html:select original) */
    public static Field choice(String label, String property, String... options) {
        Option[] list = new Option[options.length / 2];
        for (int i = 0; i < list.length; i++) {
            list[i] = new Option(options[2 * i], options[2 * i + 1]);
        }
        return new Field(label, null, Kind.CHOICE, List.of(list), beans(property));
    }

    private static List<Accessor> beans(String... properties) {
        return Arrays.stream(properties).map(p -> (Accessor) new BeanAccessor(p)).toList();
    }

    /** Propriedade JavaBean, como o Struts fazia ao preencher os VOs a partir do formulário. */
    record BeanAccessor(String name) implements Accessor {

        @Override
        public Object get(Object item) {
            try {
                return descriptor(item).getReadMethod().invoke(item);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("não foi possível ler " + name, e);
            }
        }

        @Override
        public void set(Object item, String raw, Kind kind) {
            PropertyDescriptor pd = descriptor(item);
            if (pd.getWriteMethod() == null) {
                throw new IllegalStateException("propriedade somente leitura: " + name);
            }
            Class<?> type = pd.getPropertyType();
            Object value = convert(raw, type, kind);
            try {
                pd.getWriteMethod().invoke(item, value);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("não foi possível gravar " + name, e);
            }
        }

        private PropertyDescriptor descriptor(Object item) {
            try {
                for (PropertyDescriptor pd : Introspector.getBeanInfo(item.getClass()).getPropertyDescriptors()) {
                    if (pd.getName().equals(name) && pd.getReadMethod() != null) {
                        return pd;
                    }
                }
            } catch (IntrospectionException e) {
                throw new IllegalStateException(e);
            }
            throw new IllegalStateException(item.getClass().getSimpleName() + " não tem a propriedade " + name);
        }
    }

    static Object convert(String raw, Class<?> type, Kind kind) {
        if (type == boolean.class || type == Boolean.class) {
            return raw != null && !raw.isBlank() && !"false".equalsIgnoreCase(raw);
        }
        String text = raw == null ? "" : raw.trim();
        if (type == String.class) {
            return text;
        }
        if (text.isEmpty()) {
            throw new IllegalArgumentException("valor obrigatório");
        }
        String normalized = text.replace(',', '.');
        try {
            if (type == int.class || type == Integer.class) {
                return (int) Math.round(Double.parseDouble(normalized));
            }
            if (type == long.class || type == Long.class) {
                return Math.round(Double.parseDouble(normalized));
            }
            if (type == double.class || type == Double.class) {
                double d = Double.parseDouble(normalized);
                if (!Double.isFinite(d)) {
                    throw new NumberFormatException();
                }
                return d;
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("número inválido: " + text);
        }
        throw new IllegalStateException("tipo não suportado: " + type);
    }

    /** Valor como texto para o formulário (números sem notação científica desnecessária). */
    static String format(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Double d) {
            if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                return String.valueOf(d.longValue());
            }
            String s = String.format(Locale.ROOT, "%.10g", d).replaceAll("\\.?0+(e|$)", "$1");
            return s.contains("e") ? String.valueOf(d) : s;
        }
        return value.toString();
    }
}
