package br.remoto.legacy.editor;

import br.remoto.legacy.LegacyConfig;
import br.remoto.legacy.LegacyDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyEditorTest {

    private static LegacyDatabase db;
    private static LegacyConfig config17;

    @BeforeAll
    static void open() {
        db = LegacyDatabase.open(Path.of(System.getProperty("remoto.legacy.db")));
        config17 = db.export(17);
    }

    @AfterAll
    static void close() {
        db.close();
    }

    @Test
    void todasAsTelasDisponiveisAbremComOsValoresDaConfiguracao() {
        LegacyEditor editor = LegacyEditor.open(config17, 5000, 0.05);
        for (LegacyScreens.MenuGroup group : LegacyScreens.menu()) {
            for (LegacyScreens.MenuItem item : group.items()) {
                if (item.available()) {
                    ScreenView view = editor.view(item.id(), Map.of());
                    assertThat(view.sections()).as(item.id()).isNotEmpty();
                    assertThat(view.sections().stream().mapToInt(s -> s.rows().size()).sum()).as(item.id()).isPositive();
                }
            }
        }
        ScreenView mn = editor.view("motoneuron", Map.of("pool", "SOL"));
        assertThat(mn.sections().get(0).headers()).contains("S");
        assertThat(mn.sections().get(0).paired()).isTrue();
    }

    @Test
    void editarQuantidadeDeMotoneuroniosGravaSoEssaMudanca() {
        LegacyEditor editor = LegacyEditor.open(config17, 5000, 0.05);
        ScreenView view = editor.view("network", Map.of());
        ScreenView.Row solS = row(view, 0, "SOL — S (motoneurons)");
        String quantityField = solS.cells().get(1).name();

        List<String> errors = editor.apply("network", Map.of(), Map.of(quantityField, "42",
                solS.cells().get(0).name(), "true"));
        LegacyEditor.Saved saved = editor.save();

        assertThat(errors).isEmpty();
        assertThat(neuronQuantity(saved.config(), "SOL", "S")).isEqualTo(42);
        assertThat(neuronQuantity(config17, "SOL", "S")).isNotEqualTo(42);
        assertThat(neuronQuantity(saved.config(), "SOL", "FR")).isEqualTo(neuronQuantity(config17, "SOL", "FR"));
        // as outras tabelas não mudam
        assertThat(saved.config().tables().get("CONDUCTANCE_TYPE").rows())
                .containsExactlyInAnyOrderElementsOf(config17.tables().get("CONDUCTANCE_TYPE").rows());
    }

    @Test
    void aplicaOsLimitesDoActionOriginal() {
        LegacyEditor editor = LegacyEditor.open(config17, 5000, 0.05);
        ScreenView view = editor.view("network", Map.of());
        ScreenView.Row solS = row(view, 0, "SOL — S (motoneurons)");

        editor.apply("network", Map.of(), Map.of(solS.cells().get(1).name(), "5000", solS.cells().get(0).name(), "true"));

        assertThat(neuronQuantity(editor.save().config(), "SOL", "S")).isEqualTo(1000);
    }

    @Test
    void numeroInvalidoNaoAlteraNada() {
        LegacyEditor editor = LegacyEditor.open(config17, 5000, 0.05);
        ScreenView view = editor.view("motoneuron", Map.of("pool", "SOL"));
        String threshold = view.sections().get(0).rows().get(2).cells().get(0).name();

        List<String> errors = editor.apply("motoneuron", Map.of("pool", "SOL"), Map.of(threshold, "abc"));

        assertThat(errors).singleElement().asString().contains("Soma threshold").contains("abc");
    }

    @Test
    void duracaoEPassoVemDaTelaSimulationStart() {
        LegacyEditor editor = LegacyEditor.open(config17, 5000, 0.05);
        ScreenView view = editor.view("start", Map.of());
        Map<String, String> form = new HashMap<>();
        form.put(view.sections().get(0).rows().get(0).cells().get(0).name(), "0.025");
        form.put(view.sections().get(0).rows().get(0).cells().get(1).name(), "250");

        editor.apply("start", Map.of(), form);
        LegacyEditor.Saved saved = editor.save();

        assertThat(saved.durationMs()).isEqualTo(250);
        assertThat(saved.stepMs()).isEqualTo(0.025);
    }

    private static ScreenView.Row row(ScreenView view, int section, String label) {
        return view.sections().get(section).rows().stream()
                .filter(r -> r.label().equals(label))
                .findFirst()
                .orElseThrow(() -> new AssertionError("linha não encontrada: " + label));
    }

    private static int neuronQuantity(LegacyConfig config, String nucleus, String type) {
        LegacyConfig.Table t = config.tables().get("NEURON_TYPE");
        int cNucleus = t.columns().indexOf("CD_NUCLEUS");
        int cType = t.columns().indexOf("CD_NEURON_TYPE");
        int cQuantity = t.columns().indexOf("QUANTITY");
        return t.rows().stream()
                .filter(r -> nucleus.equals(r.get(cNucleus)) && type.equals(r.get(cType)))
                .map(r -> ((Number) Objects.requireNonNull(r.get(cQuantity))).intValue())
                .findFirst()
                .orElseThrow();
    }
}
