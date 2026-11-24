package br.remoto.web.results;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Os cabeçalhos das tabelas da tela de resultados, que é em inglês. */
class TableLabelsTest {

    @Test
    void traduzONomeEMantemAUnidade() {
        assertThat(TableLabels.english(List.of("unidade", "tipo", "cd", "pico_abalo[N]", "tempo_contracao[ms]",
                "meia_relaxacao[ms]", "amplitude_muap[mV]", "posicao_y[mm]")))
                .containsExactly("unit", "type", "code", "twitch_peak[N]", "contraction_time[ms]",
                        "half_relaxation_time[ms]", "muap_amplitude[mV]", "position_y[mm]");
    }

    @Test
    void asColunasDeIntervaloLevamONumero() {
        assertThat(TableLabels.english("freq_intervalo_3[Hz]")).isEqualTo("interval_3_rate[Hz]");
        assertThat(TableLabels.english("freq_primeiro_intervalo[Hz]")).isEqualTo("first_interval_rate[Hz]");
    }

    @Test
    void nomeDesconhecidoPassaComoVeio() {
        assertThat(TableLabels.english("gNa[nS]")).isEqualTo("gNa[nS]");
        assertThat(TableLabels.english("t_ms")).isEqualTo("t_ms");
    }
}
