package br.remoto.core.protocol;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Uma medida de cada simulação de um protocolo em lote, escrita como texto:
 * {@code [nome=]função(saída[, de, até])}, com a janela em ms (sem janela, a simulação toda).
 * <ul>
 *   <li>numa série (força, EMG, ângulo...): {@code p2p} (pico a pico), {@code max}, {@code min},
 *   {@code mean} (média), {@code sd} (desvio-padrão) e {@code cv} (desvio-padrão / média);</li>
 *   <li>num trem de disparos: {@code count} (disparos) e {@code units} (unidades que dispararam).</li>
 * </ul>
 * Por exemplo, {@code M=p2p(emg-SOL,7,25)} é a onda M de um pulso em 5 ms.
 */
public record Measure(String name, Function function, String output, double from, double to) {

    /** As funções, pelo nome no texto. */
    public enum Function {
        P2P(false), MAX(false), MIN(false), MEAN(false), SD(false), CV(false), COUNT(true), UNITS(true);

        private final boolean spikes;

        Function(boolean spikes) {
            this.spikes = spikes;
        }

        /** Se a função se aplica a um trem de disparos (e não a uma série). */
        public boolean spikes() {
            return spikes;
        }
    }

    private static final Pattern TEXT = Pattern.compile(
            "\\s*(?:([^=()\\s]+)\\s*=\\s*)?(\\w+)\\(\\s*([^,()\\s]+)\\s*(?:,\\s*([^,()\\s]+)\\s*,\\s*([^,()\\s]+)\\s*)?\\)\\s*");

    /** @throws IllegalArgumentException se o texto não for uma medida */
    public static Measure parse(String text) {
        Matcher m = TEXT.matcher(text == null ? "" : text);
        if (!m.matches()) {
            throw new IllegalArgumentException("medida inválida: " + text + " (use nome=função(saída,de,até),"
                    + " por exemplo M=p2p(emg-SOL,7,25))");
        }
        Function function;
        try {
            function = Function.valueOf(m.group(2).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("função desconhecida: " + m.group(2)
                    + " (há p2p, max, min, mean, sd, cv, count, units)");
        }
        double from = Double.NEGATIVE_INFINITY;
        double to = Double.POSITIVE_INFINITY;
        if (m.group(4) != null) {
            try {
                from = Double.parseDouble(m.group(4));
                to = Double.parseDouble(m.group(5));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("janela inválida em " + text);
            }
            if (to < from) {
                throw new IllegalArgumentException("janela ao contrário em " + text);
            }
        }
        String name = m.group(1) != null ? m.group(1) : text.trim();
        return new Measure(name, function, m.group(3), from, to);
    }

    /** O valor da medida numa série; NaN se a janela não tem pontos. */
    public double ofSeries(double[] timeMs, double[] values) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double sum = 0;
        double sum2 = 0;
        int n = 0;
        for (int i = 0; i < timeMs.length; i++) {
            if (timeMs[i] >= from && timeMs[i] <= to) {
                double v = values[i];
                min = Math.min(min, v);
                max = Math.max(max, v);
                sum += v;
                sum2 += v * v;
                n++;
            }
        }
        if (n == 0) {
            return Double.NaN;
        }
        double mean = sum / n;
        double sd = n > 1 ? Math.sqrt(Math.max(0, (sum2 - n * mean * mean) / (n - 1))) : 0;
        return switch (function) {
            case P2P -> max - min;
            case MAX -> max;
            case MIN -> min;
            case MEAN -> mean;
            case SD -> sd;
            case CV -> sd / mean;
            default -> throw new IllegalStateException(function + " é de disparos");
        };
    }

    /** O valor da medida num trem de disparos (os disparos em (de, até]). */
    public double ofSpikes(int[] units, double[] timeMs) {
        int count = 0;
        Set<Integer> fired = new HashSet<>();
        for (int i = 0; i < timeMs.length; i++) {
            if (timeMs[i] > from && timeMs[i] <= to) {
                count++;
                fired.add(units[i]);
            }
        }
        return switch (function) {
            case COUNT -> count;
            case UNITS -> fired.size();
            default -> throw new IllegalStateException(function + " é de séries");
        };
    }
}
