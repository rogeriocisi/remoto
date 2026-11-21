package br.remoto.core.model.config;

/** Linha de MISCELLANEOUS: um parâmetro avulso, propriedade e valor. */
public record MiscellaneousParam(
        String property,
        String description,
        double value,
        int index,
        boolean division) {
}
