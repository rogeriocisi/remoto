package br.remoto.web.ui;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Como a página reconhece as cópias que o usuário já tem de um cenário público, para perguntar
 * antes de criar mais uma igual. O cenário guardado não registra de onde veio: o que há é o nome,
 * que a cópia herda, com a data e a hora quando é mais de uma.
 */
class ScenarioCopyNameTest {

    @Test
    void oNomeDaCopiaEoDoCenarioComADataEaHora() {
        assertThat(PageController.copyName("Full model", LocalDateTime.of(2026, 11, 27, 9, 45)))
                .isEqualTo("Full model 2026-11-27-09-45");
    }

    @Test
    void aCopiaEoMesmoNomeOuEleComADataEaHora() {
        assertThat(PageController.isCopyName("Full model", "Full model")).isTrue();
        assertThat(PageController.isCopyName("Full model", "Full model 2026-11-27-09-45")).isTrue();
        assertThat(PageController.isCopyName("Full model",
                PageController.copyName("Full model", LocalDateTime.of(2026, 1, 2, 3, 4)))).isTrue();
    }

    /**
     * "Position Task" é o começo do nome de outros três cenários públicos. O sufixo é conferido
     * pelo formato da data, e não por qualquer texto depois do nome.
     */
    @Test
    void outroCenarioQueComecaComOMesmoNomeNaoEcopia() {
        assertThat(PageController.isCopyName("Position Task", "Position Task - Ib")).isFalse();
        assertThat(PageController.isCopyName("Position Task", "Position Task - IIa no Ia")).isFalse();
        assertThat(PageController.isCopyName("Position Task", "Position Task 2026-11")).isFalse();
    }

    @Test
    void cenarioRenomeadoDeixaDeSerReconhecido() {
        assertThat(PageController.isCopyName("Full model", "Meu teste")).isFalse();
        assertThat(PageController.isCopyName("Full model", "full model")).isFalse();
    }
}
