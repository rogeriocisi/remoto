package br.remoto.legacy;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compara dois grafos de objetos campo a campo, por reflexão: listas na ordem, mapas por chave,
 * {@code double} bit a bit. Devolve as diferenças encontradas, com o caminho até cada uma.
 */
final class DeepCompare {

    private final List<String> differences = new ArrayList<>();
    private final Map<Object, Object> visited = new IdentityHashMap<>();

    private DeepCompare() {
    }

    static List<String> differences(Object a, Object b) {
        DeepCompare c = new DeepCompare();
        c.compare("conf", a, b);
        return c.differences;
    }

    private void compare(String path, Object a, Object b) {
        if (differences.size() > 50) {
            return;
        }
        if (a == b) {
            return;
        }
        if (a == null || b == null) {
            differences.add(path + ": " + a + " ≠ " + b);
            return;
        }
        if (a.getClass() != b.getClass()) {
            differences.add(path + ": classe " + a.getClass().getName() + " ≠ " + b.getClass().getName());
            return;
        }
        if (a instanceof Double x) {
            if (Double.doubleToLongBits(x) != Double.doubleToLongBits((Double) b)) {
                differences.add(path + ": " + a + " ≠ " + b);
            }
            return;
        }
        if (a instanceof Float x) {
            if (Float.floatToIntBits(x) != Float.floatToIntBits((Float) b)) {
                differences.add(path + ": " + a + " ≠ " + b);
            }
            return;
        }
        if (a instanceof String || a instanceof Number || a instanceof Boolean || a instanceof Character
                || a instanceof Enum<?> || a.getClass().getName().startsWith("java.") && !(a instanceof Collection)
                && !(a instanceof Map) && !a.getClass().isArray()) {
            if (!a.equals(b)) {
                differences.add(path + ": " + a + " ≠ " + b);
            }
            return;
        }
        if (visited.get(a) == b) {
            return;
        }
        visited.put(a, b);

        if (a.getClass().isArray()) {
            int n = Array.getLength(a);
            if (n != Array.getLength(b)) {
                differences.add(path + ": tamanho " + n + " ≠ " + Array.getLength(b));
                return;
            }
            for (int i = 0; i < n; i++) {
                compare(path + "[" + i + "]", Array.get(a, i), Array.get(b, i));
            }
            return;
        }
        if (a instanceof List<?> x) {
            List<?> y = (List<?>) b;
            if (x.size() != y.size()) {
                differences.add(path + ": tamanho " + x.size() + " ≠ " + y.size());
                return;
            }
            for (int i = 0; i < x.size(); i++) {
                compare(path + "[" + i + "]", x.get(i), y.get(i));
            }
            return;
        }
        if (a instanceof Map<?, ?> x) {
            Map<?, ?> y = (Map<?, ?>) b;
            if (!x.keySet().equals(y.keySet())) {
                differences.add(path + ": chaves " + x.keySet() + " ≠ " + y.keySet());
                return;
            }
            for (Object key : x.keySet()) {
                compare(path + "{" + key + "}", x.get(key), y.get(key));
            }
            return;
        }
        if (a instanceof Set<?> x) {
            if (!x.equals(b)) {
                differences.add(path + ": " + a + " ≠ " + b);
            }
            return;
        }
        if (a instanceof Collection<?> x) {
            Iterator<?> i = x.iterator();
            Iterator<?> j = ((Collection<?>) b).iterator();
            int k = 0;
            while (i.hasNext() && j.hasNext()) {
                compare(path + "[" + k++ + "]", i.next(), j.next());
            }
            if (i.hasNext() || j.hasNext()) {
                differences.add(path + ": tamanhos diferentes");
            }
            return;
        }

        for (Class<?> c = a.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                f.setAccessible(true);
                try {
                    compare(path + "." + f.getName(), f.get(a), f.get(b));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }
}
