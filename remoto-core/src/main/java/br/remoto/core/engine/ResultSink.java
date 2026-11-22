package br.remoto.core.engine;

import java.util.List;

/** Destino dos resultados de uma execução (arquivos, memória, testes...). */
public interface ResultSink {

    /** Série temporal amostrada, por exemplo força muscular ou EMG. */
    void series(String name, String unit, double[] timeMs, double[] values);

    /** Trem de disparos: o i-ésimo disparo ocorreu na unidade {@code units[i]} no instante {@code timeMs[i]}. */
    void spikes(String name, int[] units, double[] timeMs);

    /**
     * Tabela com colunas nomeadas, todas do mesmo tamanho: propriedades de neurônios e unidades
     * motoras, sinais de um neurônio ao longo do tempo, o catálogo dos resultados. Destinos que não
     * guardam tabelas podem ignorá-las.
     */
    default void table(String name, List<Column> columns) {
    }

    /**
     * Uma coluna de uma {@link #table tabela}: números, inteiros ou textos.
     *
     * @param name    nome da coluna, com a unidade entre colchetes quando houver, como nas séries
     * @param numbers valores numéricos; null numa coluna de texto
     * @param texts   valores de texto; null numa coluna numérica
     * @param integer se os números são inteiros (gravados sem casas decimais)
     */
    record Column(String name, double[] numbers, String[] texts, boolean integer) {

        public Column {
            if ((numbers == null) == (texts == null)) {
                throw new IllegalArgumentException("coluna " + name + ": números ou textos, um dos dois");
            }
        }

        public static Column numbers(String name, double[] values) {
            return new Column(name, values, null, false);
        }

        public static Column integers(String name, int[] values) {
            double[] numbers = new double[values.length];
            for (int i = 0; i < values.length; i++) {
                numbers[i] = values[i];
            }
            return new Column(name, numbers, null, true);
        }

        public static Column texts(String name, String[] values) {
            return new Column(name, null, values, false);
        }

        public int size() {
            return numbers != null ? numbers.length : texts.length;
        }

        /** O valor da linha {@code i} como texto de CSV. */
        public String cell(int i) {
            if (texts != null) {
                return texts[i] == null ? "" : texts[i];
            }
            double v = numbers[i];
            return integer && v == Math.rint(v) && Math.abs(v) < 1e15 ? Long.toString((long) v) : Double.toString(v);
        }
    }
}
