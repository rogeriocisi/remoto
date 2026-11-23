package br.remoto.web.editor;

import br.remoto.core.engine.legacy.LegacyConfig;
import br.remoto.core.engine.legacy.LegacyDatabase;
import br.remoto.core.simulation.ReMoto;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @Test
    void todasAsTelasDoMenuEstaoPortadas() {
        assertThat(LegacyScreens.menu()).flatMap(LegacyScreens.MenuGroup::items)
                .allMatch(LegacyScreens.MenuItem::available);
    }

    /** Campos que o DAO original não gravava (fuso, modelos...) ficam no campo settings e voltam ao reabrir. */
    @Test
    void ganhosDoFusoEModelosSobrevivemAGravacao() {
        LegacyEditor editor = LegacyEditor.open(config17, 5000, 0.05);
        ScreenView spindle = editor.view("muscleSpindle", Map.of());
        String gammaDynamic = cellOf(spindle, "Gamma dynamic [spikes/s]", 0);
        ScreenView bio = editor.view("biomechanicalElements", Map.of());
        String jointModel = cellOf(bio, "Joint model", 0);

        assertThat(editor.apply("muscleSpindle", Map.of(), Map.of(gammaDynamic, "77"))).isEmpty();
        assertThat(editor.apply("biomechanicalElements", Map.of(), Map.of(jointModel, "pendulum"))).isEmpty();
        LegacyConfig saved = editor.save().config();

        assertThat(saved.settings()).containsEntry("gammaDynamic", 77.0).containsEntry("cdJointModel", "pendulum");
        LegacyEditor reopened = LegacyEditor.open(saved, 5000, 0.05);
        assertThat(value(reopened.view("muscleSpindle", Map.of()), "Gamma dynamic [spikes/s]")).isEqualTo("77");
        assertThat(value(reopened.view("biomechanicalElements", Map.of()), "Joint model")).isEqualTo("pendulum");
    }

    @Test
    void condutanciaDinamicaEMusculoSaoGravadosNasTabelasCertas() {
        LegacyEditor editor = LegacyEditor.open(config17, 5000, 0.05);
        ScreenView cond = editor.view("conductance", Map.of("pool", "SOL", "type", "S"));
        ScreenView dyn = editor.view("dynamics", Map.of("pool", "SOL"));
        ScreenView hill = editor.view("musculotendon", Map.of("pool", "SOL"));
        assertThat(cond.sections().get(0).rows()).hasSize(5);
        assertThat(dyn.sections().get(0).rows()).isNotEmpty();

        editor.apply("conductance", Map.of("pool", "SOL", "type", "S"),
                Map.of(cond.sections().get(0).rows().get(0).cells().get(0).name(), "12.5"));
        editor.apply("dynamics", Map.of("pool", "SOL"),
                Map.of(dyn.sections().get(0).rows().get(0).cells().get(2).name(), "123"));
        editor.apply("musculotendon", Map.of("pool", "SOL"),
                Map.of(cellOf(hill, "Maximum Muscle Force [N]", 2), "4321"));
        LegacyConfig saved = editor.save().config();

        assertThat(column(saved, "MARKOV_TYPE", "GMAX")).contains(12.5);
        assertThat(column(saved, "SYNAPTIC_DYNAMICS", "TAU")).contains(123.0);
        assertThat(column(saved, "HILL_TYPE_MUSCULOTENDON_PARAMETERS", "MAXIMUM_MUSCLE_FORCE")).contains(4321.0);
    }

    /**
     * Os neurônios que guardam sinais (seção "Additional configuration" das telas de motoneurônios e
     * interneurônios) ficam no settings e decidem quais neurônios aparecem com Vm nos resultados.
     */
    @Test
    void neuroniosGuardadosEscolhidosNoEditorChegamAosResultados() {
        LegacyEditor editor = LegacyEditor.open(config17, 5000, 0.05);
        ScreenView mn = editor.view("motoneuron", Map.of("pool", "SOL"));
        ScreenView.Section stored = mn.sections().get(2);
        assertThat(stored.title()).isEqualTo("Additional configuration");
        assertThat(stored.headers()).contains("S");
        int column = stored.headers().indexOf("S");
        String first = stored.rows().get(0).cells().get(column).name();
        assertThat(stored.rows().get(0).cells().get(column).value()).isEqualTo("1");

        assertThat(editor.apply("motoneuron", Map.of("pool", "SOL"), Map.of(first, "5"))).isEmpty();
        LegacyConfig saved = editor.save().config();

        assertThat(saved.settings()).containsKey("storedNeurons");
        assertThat(((Map<?, ?>) saved.settings().get("storedNeurons")).get("SOL:S")).isEqualTo(List.of(5, -1));
        ScreenView reopened = LegacyEditor.open(saved, 5000, 0.05).view("motoneuron", Map.of("pool", "SOL"));
        assertThat(reopened.sections().get(2).rows().get(0).cells().get(column).value()).isEqualTo("5");

        br.remoto.core.scenario.Scenario scenario = new br.remoto.core.scenario.Scenario(null, "guardados", "", 1, 3,
                2, 0.05, br.remoto.core.scenario.Scenario.ENGINE_LEGACY,
                br.remoto.core.engine.legacy.LegacyJson.toMap(saved));
        List<String> names = new java.util.ArrayList<>();
        new br.remoto.core.engine.legacy.LegacyEngine(1).run(scenario, new br.remoto.core.engine.RunContext() {
            @Override public void progress(double fraction) { }
            @Override public boolean isCancelled() { return false; }
            @Override public br.remoto.core.engine.ResultSink results() {
                return new br.remoto.core.engine.ResultSink() {
                    @Override public void series(String name, String unit, double[] t, double[] v) { names.add(name); }
                    @Override public void spikes(String name, int[] u, double[] t) { names.add(name); }
                    @Override public void table(String name, List<Column> columns) { names.add(name); }
                };
            }
        });
        // o 5º S guarda no lugar do 1º; o do meio (400) continua
        assertThat(names).contains("sinais-SOL-S-5", "sinais-SOL-S-400").doesNotContain("sinais-SOL-S-1");
    }

    /**
     * O Std dos tratos (a ordem do processo gama) não ia para as tabelas no DAO original e se
     * perdia ao gravar; agora fica no settings, volta ao reabrir e faz o trato gama disparar.
     */
    @Test
    void stdDoTratoGamaFicaGravadoEFazOTratoDisparar() {
        LegacyEditor editor = LegacyEditor.open(config17, 5000, 0.05);
        ScreenView input = editor.view("input", Map.of());
        List<String> headers = input.sections().get(0).headers();
        int distributionColumn = headers.indexOf("Distribution");
        int column = headers.indexOf("Std");
        String distribution = row(input, 0, "Direct corticomotor (CM_ext)").cells().get(distributionColumn).name();
        String std = row(input, 0, "Direct corticomotor (CM_ext)").cells().get(column).name();

        // o formulário inteiro, como o navegador manda (checkbox ausente = desmarcado), com os dois campos mudados
        Map<String, String> form = new HashMap<>();
        for (ScreenView.Section section : input.sections()) {
            for (ScreenView.Row r : section.rows()) {
                for (ScreenView.Cell c : r.cells()) {
                    if (c.kind() != Field.Kind.BOOLEAN || "true".equals(c.value())) {
                        form.put(c.name(), c.value());
                    }
                }
            }
        }
        form.put(distribution, "Gm");
        form.put(std, "4");
        assertThat(editor.apply("input", Map.of(), form)).isEmpty();
        LegacyConfig saved = editor.save().config();

        assertThat(((Map<?, ?>) saved.settings().get("tractStd")).get("DT:CM_ext")).isEqualTo(4.0);
        assertThat(column(saved, "NEURON_TYPE", "STD")).containsOnly(0.0);
        ScreenView reopened = LegacyEditor.open(saved, 5000, 0.05).view("input", Map.of());
        assertThat(row(reopened, 0, "Direct corticomotor (CM_ext)").cells().get(column).value()).isEqualTo("4");

        br.remoto.core.scenario.Scenario scenario = new br.remoto.core.scenario.Scenario(null, "gama", "", 1, 3,
                40, 0.05, br.remoto.core.scenario.Scenario.ENGINE_LEGACY,
                br.remoto.core.engine.legacy.LegacyJson.toMap(saved));
        Map<String, Integer> spikes = new HashMap<>();
        new br.remoto.core.engine.legacy.LegacyEngine(1).run(scenario, new br.remoto.core.engine.RunContext() {
            @Override public void progress(double fraction) { }
            @Override public boolean isCancelled() { return false; }
            @Override public br.remoto.core.engine.ResultSink results() {
                return new br.remoto.core.engine.ResultSink() {
                    @Override public void series(String name, String unit, double[] t, double[] v) { }
                    @Override public void spikes(String name, int[] u, double[] t) { spikes.put(name, t.length); }
                };
            }
        });
        // 100 axônios com intervalo médio de 3 ms em 40 ms: muito mais que o disparo de cada um em t = 0
        assertThat(spikes.get("disparos-DT-CM_ext")).isGreaterThan(500);
    }

    /**
     * Os sinais no tempo (ângulo imposto ao tornozelo e fusimotor), que o site antigo não tinha:
     * editados nas telas Joint e Muscle Spindle, ficam no settings e voltam ao reabrir; os que
     * ficam em "none" não são gravados.
     */
    @Test
    void sinaisNoTempoFicamGravadosEVoltamAoReabrir() {
        LegacyEditor editor = LegacyEditor.open(config17, 5000, 0.05);
        ScreenView bio = editor.view("biomechanicalElements", Map.of());
        assertThat(editor.apply("biomechanicalElements", Map.of(),
                Map.of(cellOf(bio, "Joint model", 0), ReMoto.imposedAngle))).isEmpty();

        ScreenView joint = editor.view("joint", Map.of());
        Map<String, String> form = fullForm(joint);
        form.put(signalCell(joint, "Ankle angle [deg]", "Modulation"), "ramp");
        form.put(signalCell(joint, "Ankle angle [deg]", "Start"), "100");
        form.put(signalCell(joint, "Ankle angle [deg]", "Stop"), "1000");
        form.put(signalCell(joint, "Ankle angle [deg]", "Peak"), "10");
        form.put(signalCell(joint, "Ankle angle [deg]", "Width"), "50");
        assertThat(editor.apply("joint", Map.of(), form)).isEmpty();

        ScreenView spindle = editor.view("muscleSpindle", Map.of());
        form = fullForm(spindle);
        form.put(signalCell(spindle, "Gamma static [spikes/s]", "Modulation"), "constant");
        form.put(signalCell(spindle, "Gamma static [spikes/s]", "Start"), "200");
        form.put(signalCell(spindle, "Gamma static [spikes/s]", "Stop"), "800");
        form.put(signalCell(spindle, "Gamma static [spikes/s]", "Peak"), "40");
        assertThat(editor.apply("muscleSpindle", Map.of(), form)).isEmpty();
        LegacyConfig saved = editor.save().config();

        assertThat(saved.settings()).containsEntry("cdJointModel", ReMoto.imposedAngle)
                .containsKeys("jointAngleSignal", "gammaStaticSignal").doesNotContainKey("gammaDynamicSignal");
        Map<?, ?> angle = (Map<?, ?>) saved.settings().get("jointAngleSignal");
        assertThat(angle.get("shape")).isEqualTo("ramp");
        assertThat(angle.get("amp")).isEqualTo(10.0);
        assertThat(angle.get("width")).isEqualTo(50.0);
        assertThat(((Map<?, ?>) saved.settings().get("gammaStaticSignal")).get("tini")).isEqualTo(200.0);

        LegacyEditor reopened = LegacyEditor.open(saved, 5000, 0.05);
        ScreenView joint2 = reopened.view("joint", Map.of());
        assertThat(signalValue(joint2, "Ankle angle [deg]", "Modulation")).isEqualTo("ramp");
        assertThat(signalValue(joint2, "Ankle angle [deg]", "Peak")).isEqualTo("10");
        ScreenView spindle2 = reopened.view("muscleSpindle", Map.of());
        assertThat(signalValue(spindle2, "Gamma static [spikes/s]", "Modulation")).isEqualTo("constant");
        assertThat(signalValue(spindle2, "Gamma dynamic [spikes/s]", "Modulation")).isEqualTo("none");
        assertThat(value(reopened.view("biomechanicalElements", Map.of()), "Joint model")).isEqualTo(ReMoto.imposedAngle);
    }

    /**
     * O pulso e o limiar do estímulo do nervo (tela Nerve Stimulation) e a ordem gama dos aferentes
     * (tela das fibras sensoriais), que o site antigo não tinha: ficam no settings e voltam ao reabrir;
     * um valor recusado vira erro do campo e não muda nada.
     */
    @Test
    void pulsoLimiarEOrdemGamaFicamGravados() {
        LegacyEditor editor = LegacyEditor.open(config17, 5000, 0.05);
        ScreenView stimulation = editor.view("stimulation", Map.of());
        Map<String, String> form = fullForm(stimulation);
        form.put(listCell(stimulation, 2, "PTN", "Pulse width").name(), "0.5");
        form.put(listCell(stimulation, 2, "PTN", "Axon threshold").name(), "charge");
        form.put(listCell(stimulation, 2, "PTN", "Time constant, sensory axons").name(), "0.7");
        assertThat(editor.apply("stimulation", Map.of(), form)).isEmpty();

        ScreenView sensory = editor.view("sensory", Map.of("pool", "SOL"));
        ScreenView.Section orders = sensory.sections().get(1);
        String iaOrder = row(sensory, 1, "Gamma order").cells().get(orders.headers().indexOf("Ia")).name();
        assertThat(editor.apply("sensory", Map.of("pool", "SOL"), Map.of(iaOrder, "4"))).isEmpty();
        LegacyConfig saved = editor.save().config();

        assertThat(saved.settings().get("afferentGammaOrder")).isEqualTo(Map.of("SOL:Ia", 4));
        Map<?, ?> ptn = (Map<?, ?>) ((Map<?, ?>) saved.settings().get("nerveStimulus")).get("PTN");
        assertThat(ptn.get("pulseWidth")).isEqualTo(0.5);
        assertThat(ptn.get("thresholdModel")).isEqualTo("charge");
        assertThat(ptn.get("tauSensory")).isEqualTo(0.7);
        assertThat(((Map<?, ?>) saved.settings().get("nerveStimulus")).containsKey("CPN")).isFalse();

        LegacyEditor reopened = LegacyEditor.open(saved, 5000, 0.05);
        ScreenView stimulation2 = reopened.view("stimulation", Map.of());
        assertThat(listCell(stimulation2, 2, "PTN", "Pulse width").value()).isEqualTo("0.5");
        assertThat(listCell(stimulation2, 2, "PTN", "Axon threshold").value()).isEqualTo("charge");
        assertThat(listCell(stimulation2, 2, "CPN", "Axon threshold").value()).isEqualTo("current");
        ScreenView sensory2 = reopened.view("sensory", Map.of("pool", "SOL"));
        assertThat(row(sensory2, 1, "Gamma order").cells().get(orders.headers().indexOf("Ia")).value()).isEqualTo("4");
        assertThat(row(sensory2, 1, "Gamma order").cells().get(orders.headers().indexOf("II")).value()).isEqualTo("0");

        // valores recusados pelo modelo: erro do campo, e nada muda
        List<String> errors = reopened.apply("sensory", Map.of("pool", "SOL"), Map.of(iaOrder, "-3"));
        assertThat(errors).singleElement().asString().contains("Gamma order");
        errors = reopened.apply("stimulation", Map.of(),
                Map.of(listCell(stimulation2, 2, "PTN", "Pulse width").name(), "0"));
        assertThat(errors).anyMatch(e -> e.contains("Pulse width"));
        assertThat(row(reopened.view("sensory", Map.of("pool", "SOL")), 1, "Gamma order").cells()
                .get(orders.headers().indexOf("Ia")).value()).isEqualTo("4");
    }

    /** A célula de uma parte em lista: a linha do item e a coluna do campo ("Start" acha "Start [ms]"). */
    private static ScreenView.Cell listCell(ScreenView view, int section, String rowLabel, String header) {
        List<String> headers = view.sections().get(section).headers();
        int column = -1;
        for (int i = 0; i < headers.size() && column < 0; i++) {
            if (headers.get(i).equals(header) || headers.get(i).startsWith(header + " [")) {
                column = i;
            }
        }
        assertThat(column).as(header + " em " + headers).isNotNegative();
        return row(view, section, rowLabel).cells().get(column);
    }

    /** O formulário inteiro, como o navegador manda (checkbox ausente = desmarcado). */
    private static Map<String, String> fullForm(ScreenView view) {
        Map<String, String> form = new HashMap<>();
        for (ScreenView.Section section : view.sections()) {
            for (ScreenView.Row r : section.rows()) {
                for (ScreenView.Cell c : r.cells()) {
                    if (c.kind() != Field.Kind.BOOLEAN || "true".equals(c.value())) {
                        form.put(c.name(), c.value());
                    }
                }
            }
        }
        return form;
    }

    /** A célula de um sinal (a segunda parte das telas Joint e Muscle Spindle). */
    private static ScreenView.Cell signal(ScreenView view, String rowLabel, String header) {
        return listCell(view, 1, rowLabel, header);
    }

    private static String signalCell(ScreenView view, String rowLabel, String header) {
        return signal(view, rowLabel, header).name();
    }

    private static String signalValue(ScreenView view, String rowLabel, String header) {
        return signal(view, rowLabel, header).value();
    }

    @Test
    void cenarioComOutroModeloDeArticulacaoRoda() {
        LegacyEditor editor = LegacyEditor.open(config17, 5000, 0.05);
        ScreenView bio = editor.view("biomechanicalElements", Map.of());
        editor.apply("biomechanicalElements", Map.of(), Map.of(cellOf(bio, "Joint model", 0), "pendulum"));
        LegacyConfig saved = editor.save().config();
        br.remoto.core.scenario.Scenario scenario = new br.remoto.core.scenario.Scenario(null, "pendulum", "", 1, 3,
                10, 0.05, br.remoto.core.scenario.Scenario.ENGINE_LEGACY, br.remoto.core.engine.legacy.LegacyJson.toMap(saved));
        Map<String, Integer> sizes = new HashMap<>();

        new br.remoto.core.engine.legacy.LegacyEngine().run(scenario, new br.remoto.core.engine.RunContext() {
            @Override public void progress(double fraction) { }
            @Override public boolean isCancelled() { return false; }
            @Override public br.remoto.core.engine.ResultSink results() {
                return new br.remoto.core.engine.ResultSink() {
                    @Override public void series(String name, String unit, double[] t, double[] v) { sizes.put(name, t.length); }
                    @Override public void spikes(String name, int[] u, double[] t) { sizes.put(name, t.length); }
                };
            }
        });

        assertThat(sizes).containsKey("torque");
    }

    /**
     * Todas as telas editadas de uma vez e gravadas: as tabelas têm de sair iguais às que o
     * {@code ConfigurationDAO.saveConfiguration} original gravava num HSQLDB (referências geradas
     * por ele, antes de o editor passar a gravar sem banco).
     */
    @ParameterizedTest
    @ValueSource(ints = {17, 24, 42})
    void editarTodasAsTelasGravaComoODaoOriginal(int id) throws Exception {
        br.remoto.core.scenario.Scenario scenario = db.exportScenario(id);
        String hash = EditEverything.hash(br.remoto.core.engine.legacy.LegacyJson.toConfig(scenario.legacy()),
                scenario.durationMs(), scenario.stepMs());

        java.util.Properties golden = new java.util.Properties();
        try (java.io.InputStream in = LegacyEditorTest.class.getResourceAsStream("golden-master.properties")) {
            golden.load(in);
        }
        assertThat(hash).as("golden master editor.%d", id).isEqualTo(golden.getProperty("editor." + id));
    }

    private static String cellOf(ScreenView view, String rowLabel, int section) {
        return row(view, section, rowLabel).cells().get(0).name();
    }

    private static String value(ScreenView view, String rowLabel) {
        return row(view, 0, rowLabel).cells().get(0).value();
    }

    private static List<Double> column(LegacyConfig config, String table, String column) {
        LegacyConfig.Table t = config.tables().get(table);
        int c = t.columns().indexOf(column);
        return t.rows().stream().map(r -> r.get(c)).filter(Objects::nonNull)
                .map(v -> ((Number) v).doubleValue()).toList();
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
