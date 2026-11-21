package br.remoto.core.model.config;

/** Linha de NUCLEUS: um núcleo (tabela global). */
public record NucleusParams(
        String cd,
        String name,
        String layer,
        int index,
        String joint) {
}
