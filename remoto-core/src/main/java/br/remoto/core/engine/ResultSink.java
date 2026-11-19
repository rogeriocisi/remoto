package br.remoto.core.engine;

/** Destino dos resultados de uma execução (arquivos, memória, testes...). */
public interface ResultSink {

    /** Série temporal amostrada, por exemplo força muscular ou EMG. */
    void series(String name, String unit, double[] timeMs, double[] values);

    /** Trem de disparos: o i-ésimo disparo ocorreu na unidade {@code units[i]} no instante {@code timeMs[i]}. */
    void spikes(String name, int[] units, double[] timeMs);
}
