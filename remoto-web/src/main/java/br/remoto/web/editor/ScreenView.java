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
public record ScreenView(String id, String title, String intro, List<Selector> selectors,
                         List<Section> sections, List<String> notes) {

    public enum Layout { MATRIX, LIST }

    public record Selector(String name, String label, List<Field.Option> options, String selected) {
    }

    /**
     * @param headers       MATRIX: um por item; LIST: um por campo
     * @param paired        MATRIX com algum parâmetro em par ("from"/"to")
     * @param order         LIST: em que ordem as colunas aparecem, como índices de {@code headers}
     *                      e das células de cada linha. A ordem dos campos, que é a do Action
     *                      original, fica como está: é por ela que o formulário é percorrido.
     *                      Vazia (e ignorada) no MATRIX.
     * @param groupProperty LIST agrupada: a propriedade que decide o grupo da linha, para a tela
     *                      marcar a linha que vai mudar de grupo quando for salva; {@code null}
     *                      quando a lista não tem grupos
     * @param rowHeader     LIST: o título da coluna dos nomes das linhas, que normalmente não tem
     *                      nenhum. É o título do grupo quando a lista inteira é um grupo só e o
     *                      campo que agrupa não tem o que escolher: a coluna dele sai, e o que ela
     *                      diria fica aqui
     */
    public record Section(String title, Layout layout, List<String> headers, boolean paired, List<Row> rows,
                          List<Integer> order, String groupProperty, String rowHeader) {
    }

    /** @param header linha de título dentro da seção (sem campos) */
    public record Row(String label, boolean header, List<Cell> cells) {
    }

    /**
     * @param value    o valor guardado na configuração
     * @param selected CHOICE: a opção que o combo mostra. É {@code value} quando ele é uma das
     *                 opções; quando não é, é a opção declarada em {@link Field#missing()} ou,
     *                 na falta dela, {@link Field#KEEP} — que o combo mostra como "—" e que o
     *                 editor entende, ao salvar, como "não mexeram neste campo". Sem isso o
     *                 navegador mostraria a primeira opção como se fosse o valor atual.
     * @param span     quantas subcolunas ocupa (2 = parâmetro simples numa seção com pares)
     */
    public record Cell(String name, String value, String selected, Field.Kind kind, List<Field.Option> options,
                       int span) {

        public boolean checked() {
            return "true".equals(value);
        }
    }
}
