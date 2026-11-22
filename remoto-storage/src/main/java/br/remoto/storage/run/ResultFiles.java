package br.remoto.storage.run;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
            return new Columns(header, xs, ys, n);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Uma tabela inteira, como texto: o cabeçalho e as células de cada linha. */
    public static Table readTable(Path resultsDir, String name) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(openCsv(resultsDir, name), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            List<String> columns = header == null ? List.of() : cells(header);
            List<List<String>> rows = new ArrayList<>();
            for (String line; (line = reader.readLine()) != null; ) {
                if (!line.isEmpty()) {
                    rows.add(cells(line));
                }
            }
            return new Table(columns, rows);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** As células de uma linha de CSV, com aspas como na RFC 4180. */
    static List<String> cells(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }

    /** Tabela lida de um resultado: {@code rows[i][j]} é a célula da coluna {@code columns[j]}. */
    public record Table(List<String> columns, List<List<String>> rows) {

        public int column(String name) {
            return columns.indexOf(name);
        }

        /** Os valores numéricos de uma coluna; células vazias viram NaN. */
        public double[] numbers(String name) {
            int c = column(name);
            if (c < 0) {
                throw new IllegalArgumentException("coluna ausente: " + name);
            }
            double[] v = new double[rows.size()];
            for (int i = 0; i < v.length; i++) {
                String cell = c < rows.get(i).size() ? rows.get(i).get(c) : "";
                v[i] = cell.isEmpty() ? Double.NaN : Double.parseDouble(cell);
            }
            return v;
        }

        public String[] texts(String name) {
            int c = column(name);
            if (c < 0) {
                throw new IllegalArgumentException("coluna ausente: " + name);
            }
            String[] v = new String[rows.size()];
            for (int i = 0; i < v.length; i++) {
                v[i] = c < rows.get(i).size() ? rows.get(i).get(c) : "";
            }
            return v;
        }
    }

    /** @param totalPoints pontos no arquivo antes da redução */
    public record Columns(String header, double[] x, double[] y, int totalPoints) {
    }
}
