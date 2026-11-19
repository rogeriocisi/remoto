package br.remoto.legacy.editor;

import br.remoto.model.Configuration;

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
    public record SelectorDef(String name, String label, Function<Configuration, List<Field.Option>> options,
                              String defaultValue) {
    }

    /** Um bloco da tela: uma lista de itens e os campos de cada item. */
    public record Part(String title, ScreenView.Layout layout,
                       BiFunction<Configuration, Map<String, String>, List<?>> items,
                       Function<Object, String> itemLabel, List<Field> fields) {
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
            List<Field.Option> options = s.options().apply(conf);
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
            selectorViews.add(new ScreenView.Selector(s.name(), s.label(), s.options().apply(conf), selection.get(s.name())));
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
        return new ScreenView.Section(part.title(), ScreenView.Layout.MATRIX, headers, paired, rows);
    }

    private ScreenView.Section list(int p, Part part, List<?> items) {
        List<String> headers = part.fields().stream().map(Field::labelWithUnit).toList();
        List<ScreenView.Row> rows = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (isDivision(item)) {
                rows.add(new ScreenView.Row(part.itemLabel().apply(item), true, List.of()));
                continue;
            }
            List<ScreenView.Cell> cells = new ArrayList<>();
            for (Field field : part.fields()) {
                for (Field.Accessor accessor : field.accessors()) {
                    cells.add(cell(p, i, item, field, accessor, 1));
                }
            }
            rows.add(new ScreenView.Row(part.itemLabel().apply(item), false, cells));
        }
        return new ScreenView.Section(part.title(), ScreenView.Layout.LIST, headers, false, rows);
    }

    private static ScreenView.Cell cell(int p, int i, Object item, Field field, Field.Accessor accessor, int span) {
        return new ScreenView.Cell(name(p, i, accessor), Field.format(accessor.get(item)), field.kind(), field.options(), span);
    }

    static String name(int part, int item, Field.Accessor accessor) {
        return "p" + part + ".i" + item + "." + accessor.name();
    }

    /** Linhas de título da tela "Miscellaneous" (MiscellaneousVO com division = true). */
    private static boolean isDivision(Object item) {
        return item instanceof br.remoto.model.vo.MiscellaneousVO m && m.isDivision();
    }
}
