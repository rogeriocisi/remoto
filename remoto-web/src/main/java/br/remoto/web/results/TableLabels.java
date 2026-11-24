package br.remoto.web.results;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Os cabeçalhos das tabelas de resultados, em inglês, para a tela e para o CSV baixado dela.
 *
 * <p>Nos arquivos de cada simulação as colunas têm nomes em português ({@code unidade},
 * {@code pico_abalo[N]}): são identificadores do formato dos dados, que o código, a linha de
 * comando e as simulações já gravadas usam, e por isso não mudam. A tela de resultados, porém, é
 * toda em inglês, como a do ReMoto original, e ali os nomes aparecem traduzidos. A unidade entre
 * colchetes é mantida, e um nome que não esteja na lista passa como veio.
 */
final class TableLabels {

    private static final Map<String, String> ENGLISH = Map.ofEntries(
            Map.entry("unidade", "unit"),
            Map.entry("tipo", "type"),
            Map.entry("cd", "code"),
            Map.entry("categoria", "category"),
            Map.entry("nucleo", "nucleus"),
            Map.entry("disparos", "spikes"),
            Map.entry("disparos_recebidos_exc", "spikes_received_exc"),
            Map.entry("disparos_recebidos_inib", "spikes_received_inh"),
            Map.entry("freq_primeiro_intervalo", "first_interval_rate"),
            Map.entry("freq_regime", "steady_state_rate"),
            Map.entry("gmax_exc_dendrito", "gmax_exc_dendrite"),
            Map.entry("gmax_inib_dendrito", "gmax_inh_dendrite"),
            Map.entry("guardado", "stored"),
            Map.entry("guardada", "stored"),
            Map.entry("limiar", "threshold"),
            Map.entry("limiar_axonio", "axon_threshold"),
            Map.entry("limiar_recrutamento", "recruitment_threshold"),
            Map.entry("velocidade_axonio", "axon_velocity"),
            Map.entry("resistencia_entrada", "input_resistance"),
            Map.entry("reobase", "rheobase"),
            Map.entry("posicao", "position"),
            Map.entry("posicao_y", "position_y"),
            Map.entry("posicao_z", "position_z"),
            Map.entry("pico_abalo", "twitch_peak"),
            Map.entry("tempo_contracao", "contraction_time"),
            Map.entry("meia_relaxacao", "half_relaxation_time"),
            Map.entry("amplitude_muap", "muap_amplitude"),
            Map.entry("alargamento", "widening"),
            Map.entry("atenuacao", "attenuation"),
            Map.entry("parametro", "parameter"),
            Map.entry("valor", "value"));

    /** {@code freq_intervalo_3[Hz]}: uma coluna por intervalo, com o número no nome. */
    private static final Pattern INTERVAL = Pattern.compile("freq_intervalo_(\\d+)");

    private TableLabels() {
    }

    static List<String> english(List<String> columns) {
        return columns.stream().map(TableLabels::english).toList();
    }

    static String english(String column) {
        int bracket = column.indexOf('[');
        String name = bracket < 0 ? column : column.substring(0, bracket);
        String unit = bracket < 0 ? "" : column.substring(bracket);
        Matcher interval = INTERVAL.matcher(name);
        if (interval.matches()) {
            return "interval_" + interval.group(1) + "_rate" + unit;
        }
        return ENGLISH.getOrDefault(name, name) + unit;
    }
}
