package br.remoto.storage.run;

import br.remoto.core.engine.ResultSink;
import br.remoto.storage.StorageFiles;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/**
 * Grava cada resultado como {@code results/<nome>.csv.gz}. CSV comprimido abre direto em Python
 * (pandas), MATLAB e R. Não cria o diretório: se a execução foi apagada durante a simulação, a
 * gravação falha em vez de ressuscitar a pasta.
 */
public final class CsvGzResultSink implements ResultSink {

    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$");

    private final Path dir;

    public CsvGzResultSink(Path dir) {
        this.dir = dir;
    }

    @Override
    public void series(String name, String unit, double[] timeMs, double[] values) {
        if (timeMs.length != values.length) {
            throw new IllegalArgumentException("tempo e valores com tamanhos diferentes");
        }
        write(name, "t_ms,valor[" + unit + "]", w -> {
            for (int i = 0; i < timeMs.length; i++) {
                w.append(Double.toString(timeMs[i])).append(',').append(Double.toString(values[i])).append('\n');
            }
        });
    }

    @Override
    public void spikes(String name, int[] units, double[] timeMs) {
        if (timeMs.length != units.length) {
            throw new IllegalArgumentException("tempo e unidades com tamanhos diferentes");
        }
        write(name, "t_ms,unidade", w -> {
            for (int i = 0; i < timeMs.length; i++) {
                w.append(Double.toString(timeMs[i])).append(',').append(Integer.toString(units[i])).append('\n');
            }
        });
    }

    @Override
    public void table(String name, List<Column> columns) {
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("tabela sem colunas: " + name);
        }
        int size = columns.get(0).size();
        StringBuilder header = new StringBuilder();
        for (Column c : columns) {
            if (c.size() != size) {
                throw new IllegalArgumentException("colunas com tamanhos diferentes em " + name);
            }
            if (c.name().indexOf(',') >= 0 || c.name().indexOf('\n') >= 0) {
                throw new IllegalArgumentException("nome de coluna inválido: " + c.name());
            }
            header.append(header.isEmpty() ? "" : ",").append(c.name());
        }
        write(name, header.toString(), w -> {
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < columns.size(); j++) {
                    if (j > 0) {
                        w.append(',');
                    }
                    w.append(csv(columns.get(j).cell(i)));
                }
                w.append('\n');
            }
        });
    }

    /** Texto com vírgula, aspas ou quebra de linha vai entre aspas (RFC 4180). */
    private static String csv(String cell) {
        if (cell.indexOf(',') < 0 && cell.indexOf('"') < 0 && cell.indexOf('\n') < 0) {
            return cell;
        }
        return '"' + cell.replace("\"", "\"\"") + '"';
    }

    private void write(String name, String header, Rows rows) {
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("nome de resultado inválido: " + name);
        }
        try {
            Path tmp = Files.createTempFile(dir, "." + name, ".tmp");
            try {
                try (Writer w = new BufferedWriter(new OutputStreamWriter(
                        new GZIPOutputStream(Files.newOutputStream(tmp)), StandardCharsets.UTF_8))) {
                    w.append(header).append('\n');
                    rows.write(w);
                }
                StorageFiles.moveAtomically(tmp, dir.resolve(name + ".csv.gz"));
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @FunctionalInterface
    private interface Rows {
        void write(Writer w) throws IOException;
    }
}
