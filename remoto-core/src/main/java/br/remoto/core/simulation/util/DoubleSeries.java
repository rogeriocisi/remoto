package br.remoto.core.simulation.util;

import java.util.AbstractList;

/**
 * Série amostrada de um único observável, guardada como dois vetores primitivos.
 *
 * <p>Substitui a lista de objetos {@link Signal} que o amostrador usava: cada amostra custava
 * um objeto na heap (cabeçalho, referência para o rótulo e dois {@code double}), cerca de 48
 * bytes, contra os 16 bytes de dois {@code double} em vetores paralelos. Numa simulação de 30 s
 * com passo de 0,05 ms são 600 mil amostras por série, e o cenário
 * {@code remoto-25-position-task} chegava a 9,8 GB de memória viva por causa disso.
 *
 * <p>Continua sendo uma {@code List<Signal>} para que os leitores — {@code LegacyResults} e
 * {@code LegacyOutputs}, que percorrem a lista e testam {@code instanceof Signal} — não mudem.
 * O {@link Signal} é criado na leitura e morre logo em seguida, o que é barato: vive na geração
 * jovem e some na primeira coleta. Os valores devolvidos são exatamente os que foram guardados,
 * então o resultado da simulação não muda em um bit.
 *
 * <p>Não é sincronizada: cada série pertence a um elemento do modelo e é escrita pela thread que
 * o atualiza, como a lista que ela substitui.
 */
public final class DoubleSeries extends AbstractList<Signal> {

    private static final int CAPACIDADE_INICIAL = 16;

    /** Rótulo da série, repassado a cada {@link Signal} materializado na leitura. */
    private final String type;

    private double[] times = new double[CAPACIDADE_INICIAL];
    private double[] values = new double[CAPACIDADE_INICIAL];
    private int n;

    public DoubleSeries() {
        this("");
    }

    public DoubleSeries(String type) {
        this.type = type == null ? "" : type;
    }

    /** Acrescenta uma amostra, sem criar objeto. */
    public void add(double time, double value) {
        if (n == times.length) {
            int maior = times.length + (times.length >> 1) + 1;
            times = java.util.Arrays.copyOf(times, maior);
            values = java.util.Arrays.copyOf(values, maior);
        }
        times[n] = time;
        values[n] = value;
        n++;
    }

    /** Os instantes guardados, numa cópia do tamanho exato. */
    public double[] times() {
        return java.util.Arrays.copyOf(times, n);
    }

    /** Os valores guardados, numa cópia do tamanho exato. */
    public double[] values() {
        return java.util.Arrays.copyOf(values, n);
    }

    public double time(int i) {
        if (i >= n) {
            throw new IndexOutOfBoundsException(i);
        }
        return times[i];
    }

    public double value(int i) {
        if (i >= n) {
            throw new IndexOutOfBoundsException(i);
        }
        return values[i];
    }

    @Override
    public Signal get(int i) {
        if (i >= n) {
            throw new IndexOutOfBoundsException(i);
        }
        return new Signal(type, values[i], times[i]);
    }

    @Override
    public int size() {
        return n;
    }

    @Override
    public void clear() {
        n = 0;
    }
}
