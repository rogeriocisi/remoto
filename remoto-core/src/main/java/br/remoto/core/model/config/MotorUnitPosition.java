package br.remoto.core.model.config;

/** Linha de MU_POSITION: a posição predefinida de uma unidade motora no músculo (tabela global). */
public record MotorUnitPosition(
        String nucleus,
        String mu,
        double y,
        double z) {
}
