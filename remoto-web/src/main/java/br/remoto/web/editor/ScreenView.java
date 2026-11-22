package br.remoto.web.editor;

import java.util.List;

/**
 * O que a camada web precisa para desenhar uma tela do editor, já com os valores atuais.
 *
 * <p>Duas disposições, como nos JSPs originais:
 * <ul>
 *   <li>{@link Layout#MATRIX}: parâmetros nas linhas, itens (tipos S, FR, FF...) nas colunas, com
 *       subcolunas "from"/"to" para os parâmetros em pares;</li>
 *   <li>{@link Layout#LIST}: um item por linha, parâmetros nas colunas.</li>
 * </ul>
 */
public record ScreenView(String id, String title, List<Selector> selectors, List<Section> sections,
                         List<String> notes) {

    public enum Layout { MATRIX, LIST }

    public record Selector(String name, String label, List<Field.Option> options, String selected) {
    }

    /**
     * @param headers MATRIX: um por item; LIST: um por campo
     * @param paired  MATRIX com algum parâmetro em par ("from"/"to")
     */
    public record Section(String title, Layout layout, List<String> headers, boolean paired, List<Row> rows) {
    }

    /** @param header linha de título dentro da seção (sem campos) */
    public record Row(String label, boolean header, List<Cell> cells) {
    }

    /** @param span quantas subcolunas ocupa (2 = parâmetro simples numa seção com pares) */
    public record Cell(String name, String value, Field.Kind kind, List<Field.Option> options, int span) {

        public boolean checked() {
            return "true".equals(value);
        }
    }
}
