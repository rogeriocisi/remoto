package br.remoto.web.results;

import br.remoto.storage.run.ResultFiles;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * O que uma execução do motor original produziu, descrito pela tabela {@code catalogo} que o motor
 * grava: uma entrada por resultado, com o elemento (músculo, motoneurônios, aferentes...), o núcleo,
 * o subtipo (S, Ia, RC, CM_ext...), a grandeza (o código do gráfico na tela antiga) e o ponto
 * (soma, terminal, estímulo).
 *
 * <p>Execuções anteriores ao catálogo só têm as saídas básicas; para elas, as entradas são
 * deduzidas dos nomes.
 */
public final class ResultCatalog {

    public static final String TABLE = "catalogo";

    public record Entry(String name, String kind, String group, String element, String nucleus, String subtype,
                        String what, String point, String unit, String label, String index) {
    }

    private static final Pattern BASIC = Pattern.compile("^(forca|disparos|emg-bruto|emg)-(SOL|MG|LG|TA)$");

    private final List<Entry> entries;

    private ResultCatalog(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    public static ResultCatalog read(Path resultsDir, Collection<String> names) {
        if (names.contains(TABLE)) {
            ResultFiles.Table t = ResultFiles.readTable(resultsDir, TABLE);
            List<Entry> list = new ArrayList<>();
            for (List<String> row : t.rows()) {
                list.add(new Entry(cell(t, row, "nome"), cell(t, row, "tipo"), cell(t, row, "grupo"),
                        cell(t, row, "elemento"), cell(t, row, "nucleo"), cell(t, row, "subtipo"),
                        cell(t, row, "grandeza"), cell(t, row, "ponto"), cell(t, row, "unidade"),
                        cell(t, row, "rotulo"), cell(t, row, "indice")));
            }
            return new ResultCatalog(list);
        }
        return derived(names);
    }

    /** Execuções sem catálogo: só as saídas básicas, reconhecidas pelo nome. */
    static ResultCatalog derived(Collection<String> names) {
        List<Entry> list = new ArrayList<>();
        for (String name : names) {
            Matcher m = BASIC.matcher(name);
            if (name.equals("torque")) {
                list.add(new Entry(name, "serie", "biomecanico", "articulacao", "", "", "jointTorque", "", "N.m",
                        "Joint torque", ""));
            } else if (m.matches()) {
                String nucleus = m.group(2);
                list.add(switch (m.group(1)) {
                    case "forca" -> new Entry(name, "serie", "biomecanico", "musculo", nucleus, "", "muscleForce", "",
                            "N", "Muscle force", "");
                    case "disparos" -> new Entry(name, "disparos", "neural", "motoneuronios", nucleus, "",
                            "spikeTimes", "terminal", "", "MNs at terminal", "");
                    case "emg" -> new Entry(name, "serie", "biomecanico", "emg", nucleus, "", "EMG", "filtrado", "mV",
                            "EMG (noise, 50-1000 Hz)", "");
                    default -> new Entry(name, "serie", "biomecanico", "emg", nucleus, "", "EMG", "bruto", "mV",
                            "Raw EMG", "");
                });
            }
        }
        return new ResultCatalog(list);
    }

    private static String cell(ResultFiles.Table t, List<String> row, String column) {
        int c = t.column(column);
        return c >= 0 && c < row.size() ? row.get(c) : "";
    }

    public List<Entry> entries() {
        return entries;
    }

    public boolean has(String name) {
        return entries.stream().anyMatch(e -> e.name().equals(name));
    }

    public Optional<Entry> entry(String name) {
        return entries.stream().filter(e -> e.name().equals(name)).findFirst();
    }

    /** Se é um catálogo gravado pelo motor (e não deduzido dos nomes). */
    public boolean complete() {
        return has(TABLE) || entries.stream().anyMatch(e -> e.name().startsWith("tabela-"));
    }
}
