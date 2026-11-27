package br.remoto.web.editor;

import br.remoto.core.engine.legacy.LegacyConfig;
import br.remoto.core.engine.legacy.LegacyDatabase;
import br.remoto.core.simulation.ReMoto;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O editor em português. Os rótulos nascem em inglês, que é como o site antigo os tinha, e são
 * traduzidos ao desenhar a tela ({@link EditorTexts}); aqui se confere que não ficou nada para
 * trás, e que o dicionário não guarda tradução de texto que não existe mais.
 */
class EditorTextsTest {

    /**
     * O que sai igual nos dois idiomas: siglas, nomes próprios dos modelos e as palavras que o
     * português escreve como o inglês. Está aqui para a lista ser conferida, e não suposta.
     */
    private static final Set<String> SAME = Set.of("S", "FR", "FF", "SOL", "MG", "LG", "TA",
            "IN_ext", "IN_flex", "RC", "IaIn", "IbIn", "gII", "Ia", "II", "Ib", "—", "EMG", "ReMoto",
            "ISI", "AM", "Poisson", "Mileusnic", "Prochazka", "Crago", "Hermite-Rodriguez",
            "Tibial anterior", "soma", "linear", "Linear", "Final", "Final [mm]", "Amplitude",
            "Amplitude [nA]", "Musculotendon");

    private static LegacyDatabase db;
    private static LegacyConfig config;

    @BeforeAll
    static void open() {
        db = LegacyDatabase.open(Path.of(System.getProperty("remoto.legacy.db")));
        config = db.export(24);
    }

    @AfterAll
    static void close() {
        db.close();
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void todaTelaEstaEmPortugues() {
        Map<String, String> emIngles = textos(Locale.ENGLISH);
        Map<String, String> emPortugues = textos(Locale.forLanguageTag("pt"));

        List<String> faltando = new ArrayList<>();
        emIngles.forEach((onde, texto) -> {
            String pt = emPortugues.get(onde);
            if (pt != null && pt.equals(texto) && !SAME.contains(texto.trim()) && temLetras(texto)) {
                faltando.add(onde + ": " + texto);
            }
        });
        assertThat(faltando).as("textos do editor sem tradução (%d)", faltando.size()).isEmpty();
    }

    @Test
    void oDicionarioNaoGuardaTextoQueNaoExisteMais() {
        Set<String> usadas = new LinkedHashSet<>();
        for (String texto : textos(Locale.ENGLISH).values()) {
            usadas.add(EditorTexts.key(texto));
            usadas.add(EditorTexts.key(texto.replaceAll(" \\[.*\\]$", "")));       // rótulo sem a unidade
            usadas.add(EditorTexts.key(texto.replaceAll("^Group \\d+: ", "")));    // grupo sem o número
        }
        // textos que vêm do dado e não de um rótulo da tela
        usadas.add(EditorTexts.key("Group"));
        usadas.add(EditorTexts.key(ReMoto.excitatory));
        usadas.add(EditorTexts.key(ReMoto.inhibitory));

        List<String> orfas = EditorTexts.of("pt").keySet().stream()
                .filter(k -> !usadas.contains(k)).sorted().toList();
        assertThat(orfas).as("entradas de editor-pt.properties sem texto correspondente").isEmpty();
    }

    /** Todos os textos que o editor mostra, por onde aparecem, num idioma. */
    private static Map<String, String> textos(Locale locale) {
        LocaleContextHolder.setLocale(locale);
        Map<String, String> todos = new LinkedHashMap<>();
        LegacyEditor editor = LegacyEditor.open(config, 5000, 0.05);
        for (LegacyScreens.MenuGroup group : LegacyScreens.menu()) {
            todos.put("menu/" + group.items().get(0).id(), group.title());
            for (LegacyScreens.MenuItem item : group.items()) {
                todos.put("menu/item/" + item.id(), item.title());
                if (!item.available()) {
                    continue;
                }
                for (Map<String, String> selecao : selecoes(editor, item.id())) {
                    recolhe(todos, item.id(), editor.view(item.id(), selecao));
                }
            }
        }
        todos.values().removeIf(v -> v == null || v.isBlank());
        return todos;
    }

    /**
     * A seleção padrão não mostra tudo: um núcleo de interneurônio troca os grupos da tela de
     * sinapses, e os filtros em "all" trazem linhas que "active" esconde.
     */
    private static List<Map<String, String>> selecoes(LegacyEditor editor, String tela) {
        List<Map<String, String>> selecoes = new ArrayList<>();
        selecoes.add(Map.of());
        ScreenView primeira = editor.view(tela, Map.of());
        for (ScreenView.Selector s : primeira.selectors()) {
            Map<String, String> todasAsOpcoes = new LinkedHashMap<>();
            primeira.selectors().forEach(o -> todasAsOpcoes.put(o.name(), ReMoto.ALL));
            for (Field.Option o : s.options()) {
                Map<String, String> com = new LinkedHashMap<>(todasAsOpcoes);
                com.put(s.name(), o.value());
                selecoes.add(com);
            }
        }
        return selecoes;
    }

    private static void recolhe(Map<String, String> todos, String tela, ScreenView view) {
        todos.put(tela + "/title", view.title());
        todos.put(tela + "/intro", view.intro());
        for (int i = 0; i < view.notes().size(); i++) {
            todos.put(tela + "/note/" + i, view.notes().get(i));
        }
        for (ScreenView.Selector s : view.selectors()) {
            todos.put(tela + "/sel/" + s.name(), s.label());
            s.options().forEach(o -> todos.put(tela + "/sel/" + s.name() + "/" + o.value(), o.label()));
        }
        for (ScreenView.Section section : view.sections()) {
            if (section.title() != null) {
                todos.put(tela + "/part/" + section.title().hashCode(), section.title());
            }
            if (section.rowHeader() != null) {
                todos.put(tela + "/rowHeader/" + section.rowHeader().hashCode(), section.rowHeader());
            }
            section.headers().forEach(h -> todos.put(tela + "/col/" + h.hashCode(), h));
            for (ScreenView.Row row : section.rows()) {
                if (row.header()) {
                    todos.put(tela + "/group/" + row.label().hashCode(), row.label());
                }
                if (section.layout() == ScreenView.Layout.MATRIX && !row.header()) {
                    todos.put(tela + "/param/" + row.label().hashCode(), row.label());
                }
                row.cells().stream().filter(c -> c.kind() == Field.Kind.CHOICE)
                        .forEach(c -> c.options().forEach(o -> todos.put(tela + "/opt/" + o.value(), o.label())));
            }
        }
    }

    private static boolean temLetras(String texto) {
        return texto.chars().anyMatch(Character::isLetter) && texto.replaceAll("[^A-Za-z]", "").length() > 2;
    }
}
