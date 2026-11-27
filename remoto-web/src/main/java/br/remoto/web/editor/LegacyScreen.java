package br.remoto.web.editor;

import br.remoto.core.simulation.setup.Configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Uma tela do editor, equivalente a um par JSP + Action do site antigo.
 *
 * <p>Os itens editados são os mesmos objetos que o Action original colocava no formulário
 * ({@code NeuronVO}, {@code ConductanceVO}, {@code NerveVO}...), obtidos pelos mesmos métodos da
 * {@code Configuration}; ao salvar, o {@link Saver} repete o {@code setConfProperties} do Action
 * (limites, listas devolvidas à configuração).
 */
public final class LegacyScreen {

    /** Seletores do topo da tela (ex.: "Pool"). */
    public record SelectorDef(String name, String label,
                              BiFunction<Configuration, Map<String, String>, List<Field.Option>> options,
                              String defaultValue) {

        /** Seletor cujas opções não dependem dos seletores anteriores. */
        public SelectorDef(String name, String label, Function<Configuration, List<Field.Option>> options,
                           String defaultValue) {
            this(name, label, (conf, selection) -> options.apply(conf), defaultValue);
        }
    }

    /**
     * Grupos de uma lista: a lista entra na ordem que a {@code Configuration} devolve, e quando
     * essa ordem é a de um campo editável (o compartimento, nas sinapses) o grupo mostra por que a
     * linha muda de lugar depois de gravar.
     *
     * @param property a propriedade que decide o grupo
     * @param label    o título do grupo de um item
     */
    public record Grouping(String property, Function<Object, String> label) {
    }

    /**
     * Um bloco da tela: uma lista de itens e os campos de cada item.
     *
     * @param grouping    LIST: os grupos da lista, ou {@code null} para uma lista corrida
     * @param firstColumn LIST: o rótulo do campo que aparece na primeira coluna, ou {@code null}
     *                    para a ordem dos campos. Só muda a tela: {@code fields} continua na
     *                    ordem do Action original, que é a do formulário.
     */
    public record Part(String title, ScreenView.Layout layout,
                       BiFunction<Configuration, Map<String, String>, List<?>> items,
                       Function<Object, String> itemLabel, List<Field> fields,
                       Grouping grouping, String firstColumn) {

        public Part(String title, ScreenView.Layout layout,
                    BiFunction<Configuration, Map<String, String>, List<?>> items,
                    Function<Object, String> itemLabel, List<Field> fields) {
            this(title, layout, items, itemLabel, fields, null, null);
        }
    }

    @FunctionalInterface
    public interface Saver {
        /** @param items para cada parte, os itens já com os valores do formulário */
        void save(Configuration conf, Map<String, String> selection, List<List<?>> items);
    }

    private final String id;
    private final String title;
    private final List<SelectorDef> selectors;
    private final List<Part> parts;
    private final Saver saver;
    private final List<String> notes;

    public LegacyScreen(String id, String title, List<SelectorDef> selectors, List<Part> parts, Saver saver,
                        List<String> notes) {
        this.id = id;
        this.title = title;
        this.selectors = selectors;
        this.parts = parts;
        this.saver = saver;
        this.notes = notes;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    /** Seleção efetiva: o que veio do pedido, se for uma opção válida, ou o padrão do Action original. */
    public Map<String, String> selection(Configuration conf, Map<String, String> requested) {
        Map<String, String> selection = new LinkedHashMap<>();
        for (SelectorDef s : selectors) {
            List<Field.Option> options = s.options().apply(conf, selection);
            String wanted = requested.get(s.name());
            boolean valid = options.stream().anyMatch(o -> o.value().equals(wanted));
            String value = valid ? wanted
                    : options.stream().anyMatch(o -> o.value().equals(s.defaultValue())) ? s.defaultValue()
                    : options.isEmpty() ? "" : options.get(0).value();
            selection.put(s.name(), value);
        }
        return selection;
    }

    public ScreenView view(Configuration conf, Map<String, String> requested) {
        Map<String, String> selection = selection(conf, requested);
        List<ScreenView.Selector> selectorViews = new ArrayList<>();
        for (SelectorDef s : selectors) {
            selectorViews.add(new ScreenView.Selector(s.name(), s.label(), s.options().apply(conf, selection),
                    selection.get(s.name())));
        }
        List<ScreenView.Section> sections = new ArrayList<>();
        for (int p = 0; p < parts.size(); p++) {
            Part part = parts.get(p);
            List<?> items = part.items().apply(conf, selection);
            sections.add(part.layout() == ScreenView.Layout.MATRIX ? matrix(p, part, items) : list(p, part, items));
        }
        return new ScreenView(id, title, selectorViews, sections, notes);
    }

    /**
     * Aplica o formulário: grava cada campo no item correspondente e chama o Saver.
     *
     * @return erros de validação ("rótulo: mensagem"); vazio se tudo foi aplicado
     */
    public List<String> apply(Configuration conf, Map<String, String> requested, Map<String, String> form) {
        Map<String, String> selection = selection(conf, requested);
        List<String> errors = new ArrayList<>();
        List<List<?>> allItems = new ArrayList<>();
        for (int p = 0; p < parts.size(); p++) {
            Part part = parts.get(p);
            List<?> items = part.items().apply(conf, selection);
            for (int i = 0; i < items.size(); i++) {
                Object item = items.get(i);
                if (isDivision(item)) {
                    continue;
                }
                for (Field field : part.fields()) {
                    for (Field.Accessor accessor : field.accessors()) {
                        String name = name(p, i, accessor);
                        String raw = form.get(name);
                        if (raw == null && field.kind() != Field.Kind.BOOLEAN) {
                            continue; // campo não enviado: mantém o valor
                        }
                        if (field.kind() == Field.Kind.CHOICE && Field.KEEP.equals(raw)) {
                            continue; // escolha sem valor atual, não mexida: mantém o valor
                        }
                        try {
                            accessor.set(item, raw, field.kind());
                        } catch (IllegalArgumentException e) {
                            errors.add(part.itemLabel().apply(item) + " — " + field.label() + ": " + e.getMessage());
                        }
                    }
                }
            }
            allItems.add(items);
        }
        if (errors.isEmpty()) {
            saver.save(conf, selection, allItems);
        }
        return errors;
    }

    private ScreenView.Section matrix(int p, Part part, List<?> items) {
        boolean paired = part.fields().stream().anyMatch(Field::paired);
        List<String> headers = items.stream().map(part.itemLabel()).toList();
        List<ScreenView.Row> rows = new ArrayList<>();
        for (Field field : part.fields()) {
            List<ScreenView.Cell> cells = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                for (Field.Accessor accessor : field.accessors()) {
                    cells.add(cell(p, i, items.get(i), field, accessor, paired && !field.paired() ? 2 : 1));
                }
            }
            rows.add(new ScreenView.Row(field.labelWithUnit(), false, cells));
        }
        return new ScreenView.Section(part.title(), ScreenView.Layout.MATRIX, headers, paired, rows,
                List.of(), null, null);
    }

    private ScreenView.Section list(int p, Part part, List<?> items) {
        List<String> headers = part.fields().stream().map(Field::labelWithUnit).toList();
        List<ScreenView.Row> rows = new ArrayList<>();
        List<Integer> groupRows = new ArrayList<>();
        String group = null;
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (isDivision(item)) {
                rows.add(new ScreenView.Row(part.itemLabel().apply(item), true, List.of()));
                continue;
            }
            if (part.grouping() != null) {
                String g = part.grouping().label().apply(item);
                if (!g.equals(group)) {
                    group = g;
                    groupRows.add(rows.size());
                    rows.add(new ScreenView.Row(g, true, List.of()));
                }
            }
            List<ScreenView.Cell> cells = new ArrayList<>();
            for (Field field : part.fields()) {
                for (Field.Accessor accessor : field.accessors()) {
                    cells.add(cell(p, i, item, field, accessor, 1));
                }
            }
            rows.add(new ScreenView.Row(part.itemLabel().apply(item), false, cells));
        }
        List<Integer> order = columnOrder(part, headers.size());
        String groupProperty = part.grouping() == null ? null : part.grouping().property();
        String rowHeader = null;

        // Um grupo só, e nenhum item com valor no campo que agrupa: o campo não tem o que
        // escolher (o compartimento de uma sinapse sobre um interneurônio, que tem um só). A
        // coluna sairia com "—" em todas as linhas, então ela sai inteira e o título do grupo
        // passa a ser o da primeira coluna. O campo não vai no formulário e fica como está.
        int grouping = groupRows.size() != 1 ? -1 : columnOf(part, groupProperty);
        if (grouping >= 0 && semValorEmTodas(rows, groupProperty)) {
            rowHeader = rows.get(groupRows.get(0)).label();
            rows.remove(groupRows.get(0).intValue());
            order = new ArrayList<>(order);
            order.remove(Integer.valueOf(grouping));
            groupProperty = null;
        } else if (groupRows.size() > 1) {
            // Os grupos são numerados na ordem em que aparecem; com um grupo só o número não
            // diria nada.
            for (int n = 0; n < groupRows.size(); n++) {
                ScreenView.Row r = rows.get(groupRows.get(n));
                rows.set(groupRows.get(n), new ScreenView.Row("Group " + (n + 1) + ": " + r.label(), true, r.cells()));
            }
        }
        return new ScreenView.Section(part.title(), ScreenView.Layout.LIST, headers, false, rows, order,
                groupProperty, rowHeader);
    }

    /** O índice da coluna de uma propriedade numa lista, ou -1. */
    private static int columnOf(Part part, String property) {
        if (property == null || part.fields().stream().anyMatch(Field::paired)) {
            return -1;
        }
        for (int i = 0; i < part.fields().size(); i++) {
            List<Field.Accessor> accessors = part.fields().get(i).accessors();
            if (accessors.size() == 1 && accessors.get(0).name().equals(property)) {
                return i;
            }
        }
        return -1;
    }

    /** Nenhuma linha tem valor guardado nesta propriedade (a escolha mostra "—"). */
    private static boolean semValorEmTodas(List<ScreenView.Row> rows, String property) {
        return rows.stream().filter(r -> !r.header())
                .flatMap(r -> r.cells().stream())
                .filter(c -> c.name().endsWith("." + property))
                .allMatch(c -> Field.KEEP.equals(c.selected()));
    }

    /**
     * A ordem das colunas na tela: o campo de {@link Part#firstColumn()} na frente, os outros
     * atrás, na ordem em que foram declarados. Numa lista cada campo é uma coluna (nenhuma tem
     * parâmetros em par, que é coisa do MATRIX), então o índice serve para o cabeçalho e para a
     * célula.
     */
    private static List<Integer> columnOrder(Part part, int columns) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < columns; i++) {
            order.add(i);
        }
        if (part.firstColumn() == null || part.fields().size() != columns
                || part.fields().stream().anyMatch(Field::paired)) {
            return order;
        }
        int first = -1;
        for (int i = 0; i < part.fields().size(); i++) {
            if (part.fields().get(i).label().equals(part.firstColumn())) {
                first = i;
            }
        }
        if (first < 0) {
            throw new IllegalStateException("primeira coluna desconhecida: " + part.firstColumn());
        }
        order.add(0, order.remove(first));
        return order;
    }

    private static ScreenView.Cell cell(int p, int i, Object item, Field field, Field.Accessor accessor, int span) {
        String value = Field.format(accessor.get(item));
        List<Field.Option> options = field.options();
        String selected = value;
        if (field.kind() == Field.Kind.CHOICE && !isOption(options, value)) {
            // Nenhuma opção é o valor guardado. Se a ausência significa uma delas, o combo mostra
            // essa; se não significa nada (o compartimento de uma sinapse sobre um interneurônio),
            // mostra "—", em vez de fingir que o valor é a primeira opção e gravá-la ao salvar.
            selected = isOption(options, field.missing()) ? field.missing() : Field.KEEP;
            if (Field.KEEP.equals(selected)) {
                options = new ArrayList<>(options);
                options.add(0, new Field.Option(Field.KEEP, Field.KEEP_LABEL));
            }
        }
        return new ScreenView.Cell(name(p, i, accessor), value, selected, field.kind(), options, span);
    }

    private static boolean isOption(List<Field.Option> options, String value) {
        return value != null && options.stream().anyMatch(o -> o.value().equals(value));
    }

    static String name(int part, int item, Field.Accessor accessor) {
        return "p" + part + ".i" + item + "." + accessor.name();
    }

    /** Linhas de título da tela "Miscellaneous" (MiscellaneousVO com division = true). */
    private static boolean isDivision(Object item) {
        return item instanceof br.remoto.core.simulation.setup.MiscellaneousVO m && m.isDivision();
    }
}
