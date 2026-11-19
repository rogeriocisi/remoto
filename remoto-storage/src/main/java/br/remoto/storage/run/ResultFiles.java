package br.remoto.storage.run;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

/** Leitura dos resultados gravados por {@link CsvGzResultSink}. */
public final class ResultFiles {

    private ResultFiles() {
    }

    public static Path file(Path resultsDir, String name) {
        return resultsDir.resolve(name + ".csv.gz");
    }

    /** CSV descomprimido, para download. */
    public static InputStream openCsv(Path resultsDir, String name) throws IOException {
        return new GZIPInputStream(Files.newInputStream(file(resultsDir, name)));
    }

    /** As duas colunas numéricas, opcionalmente reduzidas a no máximo {@code maxPoints} pontos. */
    public static Columns read(Path resultsDir, String name, int maxPoints) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(openCsv(resultsDir, name), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            double[] x = new double[1024];
            double[] y = new double[1024];
            int n = 0;
            for (String line; (line = reader.readLine()) != null; ) {
                int comma = line.indexOf(',');
                if (comma < 0) {
                    continue;
                }
                if (n == x.length) {
                    x = Arrays.copyOf(x, n * 2);
                    y = Arrays.copyOf(y, n * 2);
                }
                x[n] = Double.parseDouble(line.substring(0, comma));
                y[n] = Double.parseDouble(line.substring(comma + 1));
                n++;
            }
            int stride = Math.max(1, (n + maxPoints - 1) / Math.max(1, maxPoints));
            int m = (n + stride - 1) / stride;
            double[] xs = new double[m];
            double[] ys = new double[m];
            for (int i = 0, j = 0; i < n; i += stride, j++) {
                xs[j] = x[i];
                ys[j] = y[i];
            }
            return new Columns(header, xs, ys);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public record Columns(String header, double[] x, double[] y) {
    }
}
