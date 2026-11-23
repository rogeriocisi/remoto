package br.remoto.core.engine.legacy;

import br.remoto.core.scenario.Scenario;
import br.remoto.core.scenario.ScenarioValidator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Garante que o motor original, rodando na nova plataforma, é (1) fiel ao banco antigo,
 * (2) reprodutível e (3) estável em relação às saídas de referência.
 */
class LegacyEngineTest {

    private static final double SHORT_RUN_MS = 40;
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static LegacyDatabase db;
    private final LegacyEngine engine = new LegacyEngine();
    private final JsonCodec json = new JsonCodec();

    @BeforeAll
    static void open() {
        db = LegacyDatabase.open(Path.of(System.getProperty("remoto.legacy.db")));
    }

    @AfterAll
    static void close() {
        db.close();
    }

    @Test
    void exportaAsConfiguracoesPublicasSemDadosPessoais() {
        assertThat(db.configurations())
                .filteredOn(c -> "guest".equals(c.owner()))
                .extracting(LegacyDatabase.ConfigurationInfo::id)
                .containsExactly(17, 24, 42);

        Scenario scenario = db.exportScenario(17);
        String text = new String(json.toBytes(scenario), StandardCharsets.UTF_8);

        assertThat(ScenarioValidator.validate(scenario)).isEmpty();
        assertThat(scenario.engine()).isEqualTo(Scenario.ENGINE_LEGACY);
        assertThat(scenario.durationMs()).isEqualTo(5000);
        assertThat(text).doesNotContain("\"USER\"").doesNotContain("PASSWORD").doesNotContain("EMAIL");
    }

    /**
     * Configurações publicadas com os tratos gama como Poisson: a ordem do processo gama não foi
     * gravada no banco, e o trato só disparava em t = 0 (ver docs/nova-versao.md).
     */
    private static final Set<Integer> GAMMA_AS_POISSON = Set.of(24, 25, 26, 27, 28, 32, 34, 35, 36, 38, 39, 41, 42, 44);

    /**
     * Configurações publicadas com um settings que o banco não tinha: as "Position Task", com o
     * tornozelo em tarefa de posição (o DAO original não gravava o modelo de articulação).
     */
    private static final Map<Integer, Map<String, Object>> SETTINGS_ADDED = Map.of(
            25, Map.of("cdJointModel", "position"), 26, Map.of("cdJointModel", "position"),
            27, Map.of("cdJointModel", "position"), 28, Map.of("cdJointModel", "position"));

    /**
     * O export lê o arquivo do banco sem o HSQLDB; os cenários públicos "remoto-&lt;id&gt;-..." foram
     * exportados por ele. Depois, só a descrição pode ter sido escrita à mão; nos
     * {@link #GAMMA_AS_POISSON}, os tratos gama passaram a Poisson; e nos {@link #SETTINGS_ADDED}
     * entrou um settings.
     */
    @ParameterizedTest
    @MethodSource("publishedConfigurations")
    void exportDoBancoReproduzOsCenariosPublicos(String fileName) throws Exception {
        int id = Integer.parseInt(fileName.split("-")[1]);
        Map<?, ?> published = MAPPER.readValue(Files.readAllBytes(scenariosDir().resolve(fileName)), Map.class);
        Map<?, ?> exported = MAPPER.readValue(json.toBytes(db.exportScenario(id)), Map.class);
        published.remove("description");
        exported.remove("description");
        if (GAMMA_AS_POISSON.contains(id)) {
            assertThat(gammaTractsAsPoisson(exported)).isNotEmpty();
        }
        if (SETTINGS_ADDED.containsKey(id)) {
            Map<?, ?> legacy = (Map<?, ?>) published.get("legacy");
            assertThat(legacy.get("settings")).isEqualTo(SETTINGS_ADDED.get(id));
            legacy.remove("settings");
        }

        assertThat(exported).usingRecursiveComparison().isEqualTo(published);
    }

    static Stream<String> publishedConfigurations() throws IOException {
        try (var files = Files.list(scenariosDir())) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.matches("remoto-\\d+-.*\\.json"))
                    .sorted()
                    .toList()
                    .stream();
        }
    }

    /** Troca a distribuição dos tratos gama por Poisson; devolve os tipos trocados. */
    @SuppressWarnings("unchecked")
    private static List<Object> gammaTractsAsPoisson(Map<?, ?> scenario) {
        Map<String, Object> tables = (Map<String, Object>) ((Map<String, Object>) scenario.get("legacy")).get("tables");
        Map<String, Object> table = (Map<String, Object>) tables.get("NEURON_TYPE");
        List<String> columns = (List<String>) table.get("columns");
        int category = columns.indexOf("CD_CATEGORY");
        int distribution = columns.indexOf("DISTRIBUTION");
        int type = columns.indexOf("CD_NEURON_TYPE");
        List<Object> changed = new java.util.ArrayList<>();
        for (List<Object> row : (List<List<Object>>) table.get("rows")) {
            if ("TR".equals(row.get(category)) && "Gm".equals(row.get(distribution))) {
                row.set(distribution, "P");
                changed.add(row.get(type));
            }
        }
        return changed;
    }

    private static Path scenariosDir() {
        return Path.of(System.getProperty("remoto.legacy.db")).getParent().getParent().resolve("scenarios");
    }

    @Test
    void mesmaSeedReproduzESeedDiferenteMuda() throws Exception {
        // O 17 tem disparos já nos primeiros 40 ms. O 42 não serve: o comando dele só começa em
        // 3 s, e sem disparos as saídas não dependem da seed.
        Scenario scenario = viaJsonFile(db.exportScenario(17)).withDuration(SHORT_RUN_MS);

        Recorder a = run(scenario.withSeed(1));
        Recorder b = run(scenario.withSeed(1));
        Recorder c = run(scenario.withSeed(2));

        assertThat(a.sha256()).isEqualTo(b.sha256());
        assertThat(c.sha256()).isNotEqualTo(a.sha256());
        assertThat(a.lastProgress).isEqualTo(1.0);
    }

    @ParameterizedTest
    @ValueSource(ints = {17, 24, 42})
    void saidasBatemComAReferencia(int id) throws Exception {
        Scenario scenario = viaJsonFile(db.exportScenario(id)).withDuration(SHORT_RUN_MS);
        String key = id + "." + scenario.seed() + "." + (int) SHORT_RUN_MS;

        Recorder r = run(scenario);
        String emgHash = r.sha256(Recorder::basicEmg);

        assertThat(r.outputs).containsKey("torque");
        // força, disparos e torque: as mesmas referências de antes do EMG existir
        assertThat(r.sha256(name -> Recorder.basic(name) && !Recorder.basicEmg(name)))
                .as("golden master %s%n%s", key, r.summary())
                .isEqualTo(golden().getProperty(key));
        assertThat(emgHash).as("golden master %s.emg (valor atual: %s)", key, emgHash)
                .isEqualTo(golden().getProperty(key + ".emg"));
        // todas as saídas, as da tela de resultados incluídas (sinais guardados, tabelas, catálogo)
        String complete = r.sha256();
        assertThat(complete).as("golden master %s.completo (valor atual: %s)", key, complete)
                .isEqualTo(golden().getProperty(key + ".completo"));
    }

    /**
     * As 30 configurações do banco original, com a seed igual ao ID. As referências foram geradas
     * pelo motor quando ele ainda montava a configuração com o {@code ConfigurationDAO} original,
     * lendo do HSQLDB.
     */
    @ParameterizedTest
    @MethodSource("databaseConfigurations")
    void todasAsConfiguracoesDoBancoBatemComAReferencia(int id) throws Exception {
        Scenario scenario = viaJsonFile(db.exportScenario(id)).withDuration(SHORT_RUN_MS);
        String key = "db." + id + "." + scenario.seed() + "." + (int) SHORT_RUN_MS;

        Recorder r = run(scenario);

        assertThat(r.sha256(Recorder::basic)).as("golden master %s%n%s", key, r.summary())
                .isEqualTo(golden().getProperty(key));
    }

    static IntStream databaseConfigurations() {
        try (LegacyDatabase database = LegacyDatabase.open(Path.of(System.getProperty("remoto.legacy.db")))) {
            int[] ids = database.configurations().stream().mapToInt(LegacyDatabase.ConfigurationInfo::id).toArray();
            assertThat(ids).hasSizeGreaterThanOrEqualTo(30);
            return IntStream.of(ids);
        }
    }

    @Test
    void emgTemUmPontoPorPassoEDuasVersoesPorMusculo() throws Exception {
        Recorder r = run(viaJsonFile(db.exportScenario(17)).withDuration(SHORT_RUN_MS));

        assertThat(r.outputs).containsKeys("emg-SOL", "emg-bruto-SOL");
        String[] raw = r.outputs.get("emg-bruto-SOL").split("\n");
        assertThat(raw[0]).isEqualTo("t_ms,valor[mV]");
        assertThat(raw).hasSize(1 + (int) (SHORT_RUN_MS / 0.05));
        assertThat(java.util.Arrays.stream(raw).skip(1).mapToDouble(l -> Double.parseDouble(l.split(",")[1])))
                .as("há disparos nos 40 ms, então o EMG bruto não pode ser todo zero")
                .anyMatch(v -> v != 0);
    }

    /**
     * Nenhuma série sai com NaN ou infinito. A taxa do órgão tendinoso é dividida pelas fibras Ib
     * ativas do músculo, e a força normalizada, pela força máxima das unidades motoras: sem fibras Ib
     * (o 17) ou sem unidades motoras (o tibial anterior do 18, que só tem aferentes), as duas séries
     * seriam divisões por zero, e não saem.
     */
    @Test
    void nenhumaSerieTemNaNNemInfinito() {
        for (int id : new int[] {17, 18}) {
            Recorder r = run(viaJsonFile(db.exportScenario(id)).withDuration(SHORT_RUN_MS));
            r.outputs.forEach((name, csv) -> {
                if (!name.startsWith("tabela") && !name.equals("catalogo")) {
                    assertThat(csv).as("%s em %d", name, id).doesNotContain("NaN").doesNotContain("Infinity");
                }
            });
            assertThat(r.outputs).doesNotContainKey("otg-ib-SOL").containsKey("musculo-forca-normalizada-SOL");
            if (id == 18) {
                assertThat(r.outputs).containsKey("mt-comprimento-TA").doesNotContainKey("musculo-forca-normalizada-TA");
            }
        }
    }

    /** O "Modelo completo" de scenarios/ liga todos os elementos: cada um aparece nas saídas. */
    @Test
    void modeloCompletoLigaTodosOsElementos() throws Exception {
        Scenario scenario = json.fromBytes(Files.readAllBytes(scenariosDir().resolve("modelo-completo.json")),
                Scenario.class);
        assertThat(ScenarioValidator.validate(scenario)).isEmpty();

        Recorder r = run(scenario.withDuration(SHORT_RUN_MS));

        for (String m : new String[] {"SOL", "MG", "LG", "TA"}) {
            assertThat(r.outputs).containsKeys("forca-" + m, "musculo-forca-paralela-" + m, "fuso-ia-" + m,
                    "fuso-ii-" + m, "otg-ib-" + m, "tabela-aferentes-" + m);
        }
        assertThat(r.outputs).containsKeys("articulacao-angulo", "articulacao-centro-massa",
                "tabela-interneuronios-IN_ext", "tabela-interneuronios-IN_flex");
        for (String tract : new String[] {"CM_ext", "CM_flex", "RBE_ext", "RBE_flex", "RBI_ext", "RBI_flex"}) {
            assertThat(r.outputs).containsKey("trato-isi-medio-" + tract);
        }
    }

    /**
     * O "Reflexo de estiramento no tornozelo" de scenarios/ impõe um trapézio ao ângulo. Para caber
     * nos 40 ms do teste, o trapézio vai de 5 a 35 ms, com rampas de 10 ms: o ângulo sobe os 5°, fica
     * e volta, e os fusos do tríceps sural respondem.
     */
    @Test
    @SuppressWarnings("unchecked")
    void estiramentoDoTornozeloImpoeOTrapezioAoAngulo() throws Exception {
        Scenario scenario = json.fromBytes(Files.readAllBytes(scenariosDir().resolve("estiramento-tornozelo.json")),
                Scenario.class);
        assertThat(ScenarioValidator.validate(scenario)).isEmpty();
        LegacyConfig config = LegacyJson.toConfig(scenario.legacy());
        assertThat(config.settings()).containsEntry("cdJointModel", "imposed");
        Map<String, Object> signal = new java.util.LinkedHashMap<>((Map<String, Object>) config.settings().get("jointAngleSignal"));
        assertThat(signal.get("shape")).isEqualTo("trapezoid");
        assertThat(((Number) signal.get("amp")).doubleValue()).isEqualTo(5.0);

        signal.put("tini", 5.0);
        signal.put("tfin", 35.0);
        signal.put("width", 10.0);
        Map<String, Object> settings = new java.util.LinkedHashMap<>(config.settings());
        settings.put("jointAngleSignal", signal);
        LegacyConfig early = new LegacyConfig(config.format(), config.idConfiguration(), config.tables(), settings);
        Recorder r = run(new Scenario(null, scenario.name(), "", scenario.schemaVersion(), scenario.seed(),
                SHORT_RUN_MS, scenario.stepMs(), scenario.engine(), LegacyJson.toMap(early)));

        double[] angle = r.outputs.get("articulacao-angulo").lines().skip(1)
                .mapToDouble(l -> Double.parseDouble(l.split(",")[1])).toArray();
        double initial = angle[0];
        assertThat(java.util.Arrays.stream(angle).max().orElseThrow() - initial).isCloseTo(5, within(1e-6));
        assertThat(angle[angle.length - 1]).isCloseTo(initial, within(1e-6));
        assertThat(r.outputs).containsKeys("fuso-ia-SOL", "fuso-ia-MG", "fuso-ia-LG");
    }

    private Recorder run(Scenario scenario) {
        Recorder r = new Recorder();
        engine.run(scenario, r);
        return r;
    }

    /** Mesmo caminho do site: o cenário vira arquivo JSON e é lido de volta. */
    private Scenario viaJsonFile(Scenario scenario) {
        return json.fromBytes(json.toBytes(scenario), Scenario.class);
    }

    private static Properties golden() throws Exception {
        Properties p = new Properties();
        try (InputStream in = LegacyEngineTest.class.getResourceAsStream("golden-master.properties")) {
            p.load(in);
        }
        return p;
    }
}
