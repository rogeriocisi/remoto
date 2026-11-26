package br.remoto.web.results;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * As condutâncias que a tela de resultados oferece. O sódio persistente entrou com a membrana de
 * cinética contínua e, como o cálcio, só faz sentido em motoneurônio.
 */
class ConductanceMenuTest {

    @Test
    void oSodioPersistenteEstaNaListaDeCondutancias() {
        assertThat(Graphics.CONDUCTANCES).contains("gNap");
    }

    @Test
    void asCondutanciasDeMotoneuronioSaoOCalcioEOSodioPersistente() {
        assertThat(Graphics.MOTONEURON_ONLY).containsExactlyInAnyOrder("gCa", "gNap");
        assertThat(Graphics.CONDUCTANCES).containsAll(Graphics.MOTONEURON_ONLY);
    }

    @Test
    void asCondutanciasDoOriginalContinuamTodasNaLista() {
        assertThat(Graphics.CONDUCTANCES).contains("gNa", "gKf", "gKs", "gCa",
                "gExc-dend", "gInib-dend", "gExc-soma", "gInib-soma");
    }
}
