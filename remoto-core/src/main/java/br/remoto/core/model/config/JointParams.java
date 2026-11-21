package br.remoto.core.model.config;

/** Linha de JOINTS: uma articulação (tabela global). */
public record JointParams(
        String cd,
        String name,
        int numNuclei,
        int index) {
}
