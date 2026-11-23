package br.remoto.core.protocol;

import br.remoto.core.engine.ResultSink;
import br.remoto.core.engine.RunContext;
import br.remoto.core.engine.SimulationEngine;
import br.remoto.core.scenario.Scenario;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * Um protocolo em lote: o mesmo cenário rodado uma vez para cada valor de um parâmetro, com
 * medidas de cada simulação. É o que o ReMoto em Fortran fazia em scripts, como a curva de
 * recrutamento do reflexo H e da onda M (a intensidade do estímulo) e a variabilidade da força
 * (o comando descendente); os protocolos prontos estão em {@link Protocols}.
 *
 * <p>As simulações rodam uma depois da outra, cada uma com a seed do cenário (para repetições com
 * sorteios diferentes, o parâmetro é a própria {@code seed}).
 */
public record Sweep(ScenarioParameter parameter, List<Double> values, List<Measure> measures) {

    /** O resultado de uma simulação: o valor do parâmetro e as medidas, na ordem de {@link #measures}. */
    public record Row(double value, double[] measures) {
    }

    /** Acompanha o lote. */
    public interface Listener {

        default void started(int index, double value) {
        }

        /** Fração da simulação {@code index} já rodada. */
        default void progress(int index, double fraction) {
        }

        default void finished(int index, Row row) {
        }
    }

    public Sweep {
        if (parameter == null || values == null || values.isEmpty() || measures == null || measures.isEmpty()) {
            throw new IllegalArgumentException("o lote precisa de um parâmetro, valores e medidas");
        }
        values = List.copyOf(values);
        measures = List.copyOf(measures);
    }

    /** Os valores de {@code from} a {@code to} (inclusive), de {@code step} em {@code step}. */
    public static List<Double> range(double from, double to, double step) {
        if (!(step > 0) || to < from) {
            throw new IllegalArgumentException("intervalo inválido: de " + from + " a " + to + " com passo " + step);
        }
        List<Double> values = new ArrayList<>();
        int n = (int) Math.floor((to - from) / step + 1e-9);
        if (n > 10_000) {
            throw new IllegalArgumentException("valores demais: " + (n + 1));
        }
        for (int i = 0; i <= n; i++) {
            values.add(Math.round((from + i * step) * 1e9) / 1e9);
        }
        return values;
    }

    /**
     * Roda o lote.
     *
     * @param base o cenário de partida
     * @param extra para cada simulação, um destino a mais para todos os resultados (para guardá-los);
     *     null para só medir
     */
    public List<Row> run(Scenario base, SimulationEngine engine, IntFunction<ResultSink> extra, Listener listener) {
        Listener l = listener != null ? listener : new Listener() {
        };
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            double value = values.get(i);
            l.started(i, value);
            Scenario scenario = parameter.apply(base, value);
            Collector collector = new Collector(extra == null ? null : extra.apply(i));
            int index = i;
            engine.run(scenario, new RunContext() {
                @Override
                public void progress(double fraction) {
                    l.progress(index, fraction);
                }

                @Override
                public boolean isCancelled() {
                    return Thread.currentThread().isInterrupted();
                }

                @Override
                public ResultSink results() {
                    return collector;
                }
            });
            Row row = new Row(value, collector.measure());
            rows.add(row);
            l.finished(i, row);
        }
        return rows;
    }

    /** As linhas em CSV: o parâmetro e as medidas. */
    public void writeCsv(List<Row> rows, Appendable out) throws IOException {
        out.append(csv(parameter.spec()));
        for (Measure m : measures) {
            out.append(',').append(csv(m.name()));
        }
        out.append('\n');
        for (Row row : rows) {
            out.append(number(row.value()));
            for (double v : row.measures()) {
                out.append(',').append(number(v));
            }
            out.append('\n');
        }
    }

    private static String number(double v) {
        if (Double.isNaN(v)) {
            return "";
        }
        return v == Math.rint(v) && Math.abs(v) < 1e15 ? Long.toString((long) v) : String.format(Locale.ROOT, "%.6g", v);
    }

    private static String csv(String text) {
        return text.contains(",") || text.contains("\"") ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
    }

    /** Guarda as saídas que as medidas usam e repassa tudo ao destino extra, se houver. */
    private final class Collector implements ResultSink {

        private final ResultSink extra;
        private final Set<String> wanted = new HashSet<>();
        private final Map<String, double[][]> series = new HashMap<>();
        private final Map<String, Object[]> spikes = new HashMap<>();

        Collector(ResultSink extra) {
            this.extra = extra;
            measures.forEach(m -> wanted.add(m.output()));
        }

        @Override
        public void series(String name, String unit, double[] timeMs, double[] values) {
            if (wanted.contains(name)) {
                series.put(name, new double[][] {timeMs, values});
            }
            if (extra != null) {
                extra.series(name, unit, timeMs, values);
            }
        }

        @Override
        public void spikes(String name, int[] units, double[] timeMs) {
            if (wanted.contains(name)) {
                spikes.put(name, new Object[] {units, timeMs});
            }
            if (extra != null) {
                extra.spikes(name, units, timeMs);
            }
        }

        @Override
        public void table(String name, List<Column> columns) {
            if (extra != null) {
                extra.table(name, columns);
            }
        }

        double[] measure() {
            double[] result = new double[measures.size()];
            for (int k = 0; k < result.length; k++) {
                Measure m = measures.get(k);
                if (m.function().spikes()) {
                    Object[] s = spikes.get(m.output());
                    // trem que não veio: nenhum disparo (o motor só grava trens com disparos)
                    result[k] = s == null ? m.ofSpikes(new int[0], new double[0]) : m.ofSpikes((int[]) s[0], (double[]) s[1]);
                } else {
                    double[][] s = series.get(m.output());
                    if (s == null) {
                        throw new IllegalArgumentException("a simulação não tem a série " + m.output()
                                + " (medida " + m.name() + ")");
                    }
                    result[k] = m.ofSeries(s[0], s[1]);
                }
            }
            return result;
        }
    }
}
