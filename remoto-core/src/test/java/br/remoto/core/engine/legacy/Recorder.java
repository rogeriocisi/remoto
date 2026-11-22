package br.remoto.core.engine.legacy;

import br.remoto.core.engine.ResultSink;
import br.remoto.core.engine.RunContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/** Contexto em memória: guarda os resultados como texto CSV para comparar e calcular hash. */
class Recorder implements RunContext, ResultSink {

    final Map<String, String> outputs = new TreeMap<>();
    double lastProgress;

    @Override
    public void progress(double fraction) {
        lastProgress = fraction;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public ResultSink results() {
        return this;
    }

    @Override
    public void series(String name, String unit, double[] timeMs, double[] values) {
        StringBuilder sb = new StringBuilder("t_ms,valor[").append(unit).append("]\n");
        for (int i = 0; i < timeMs.length; i++) {
            sb.append(timeMs[i]).append(',').append(values[i]).append('\n');
        }
        outputs.put(name, sb.toString());
    }

    @Override
    public void spikes(String name, int[] units, double[] timeMs) {
        StringBuilder sb = new StringBuilder("t_ms,unidade\n");
        for (int i = 0; i < timeMs.length; i++) {
            sb.append(timeMs[i]).append(',').append(units[i]).append('\n');
        }
        outputs.put(name, sb.toString());
    }

    /** SHA-256 de todas as saídas, em ordem de nome. */
    String sha256() throws Exception {
        return sha256(name -> true);
    }

    /** SHA-256 das saídas cujo nome passa no filtro (ex.: só EMG, ou tudo menos EMG). */
    String sha256(java.util.function.Predicate<String> names) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (Map.Entry<String, String> e : outputs.entrySet()) {
            if (!names.test(e.getKey())) {
                continue;
            }
            md.update(e.getKey().getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(e.getValue().getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(md.digest());
    }

    /** Resumo legível: linhas por saída. */
    String summary() {
        StringBuilder sb = new StringBuilder();
        outputs.forEach((name, csv) -> sb.append(name).append(": ")
                .append(csv.split("\n").length - 1).append(" linhas\n"));
        return sb.toString();
    }
}
