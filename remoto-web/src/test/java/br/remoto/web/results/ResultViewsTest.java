package br.remoto.web.results;

import br.remoto.core.engine.ResultSink;
import br.remoto.core.engine.RunContext;
import br.remoto.core.engine.legacy.LegacyDatabase;
import br.remoto.core.engine.legacy.LegacyEngine;
import br.remoto.core.scenario.Scenario;
import br.remoto.storage.JsonCodec;
import br.remoto.storage.run.CsvGzResultSink;
import br.remoto.storage.run.ResultFiles;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A tela de resultados sobre execuções de verdade do motor original: todo gráfico que o menu
 * oferece tem de sair sem erro, e os cálculos conferem com o que a tela antiga fazia.
 */
class ResultViewsTest {

    private static final double DURATION_MS = 100;
    private static final long SEED = 17;

    @TempDir
    static Path tmp;
    private static Path soleus;
    private static Path renshaw;

    @BeforeAll
    static void run() throws Exception {
        Path repo = Path.of(System.getProperty("remoto.legacy.db")).getParent().getParent();
        JsonMapper mapper = JsonMapper.builder().build();
        @SuppressWarnings("unchecked")
        Map<String, Object> m = mapper.readValue(Files.readAllBytes(
                repo.resolve("scenarios/remoto-17-default-soleus-contraction.json")), LinkedHashMap.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> legacy = (Map<String, Object>) m.get("legacy");
        legacy.put("settings", Map.of("cdMuscleModel", "hill", "cdJointModel", "pendulum"));
        injectSineCurrent(legacy);
        JsonCodec json = new JsonCodec();
        Scenario scenario = json.fromBytes(mapper.writeValueAsBytes(m), Scenario.class).withDuration(DURATION_MS)
                .withSeed(SEED);
        soleus = Files.createDirectories(tmp.resolve("soleus"));
        new LegacyEngine(2).run(scenario, context(new CsvGzResultSink(soleus)));

        try (LegacyDatabase db = LegacyDatabase.open(Path.of(System.getProperty("remoto.legacy.db")))) {
            Scenario rc = json.fromBytes(json.toBytes(db.exportScenario(44)), Scenario.class).withDuration(DURATION_MS);
            renshaw = Files.createDirectories(tmp.resolve("renshaw"));
            new LegacyEngine(2).run(rc, context(new CsvGzResultSink(renshaw)));
        }
    }

    /** Corrente senoidal nos motoneurônios S do sóleo: 8 nA + 4 nA a 10 Hz. */
    @SuppressWarnings("unchecked")
    private static void injectSineCurrent(Map<String, Object> legacy) {
        Map<String, Object> table = (Map<String, Object>) ((Map<String, Object>) legacy.get("tables")).get("INJECTED_CURRENT");
        List<String> c = (List<String>) table.get("columns");
        for (Object o : (List<Object>) table.get("rows")) {
            List<Object> r = (List<Object>) o;
            if ("SOL".equals(r.get(c.indexOf("CD_NUCLEUS"))) && "S".equals(r.get(c.indexOf("CD_NEURON_TYPE")))) {
                r.set(c.indexOf("ACTIVE"), 1);
                r.set(c.indexOf("IMAX"), 8);
                r.set(c.indexOf("CD_SIGNAL"), "sine");
                r.set(c.indexOf("TFIN"), DURATION_MS);
                r.set(c.indexOf("AMP"), 4);
                r.set(c.indexOf("FREQ"), 10);
            }
        }
    }

    private static RunContext context(ResultSink sink) {
        return new RunContext() {
            public void progress(double fraction) {
            }

            public boolean isCancelled() {
                return false;
            }

            public ResultSink results() {
                return sink;
            }
        };
    }

    private static ResultCatalog catalog(Path dir) throws Exception {
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".csv.gz"))
                    .forEach(n -> names.add(n.substring(0, n.length() - ".csv.gz".length())));
        }
        return ResultCatalog.read(dir, names);
    }

    private static ResultViews.Options options(String type, String element, String nucleus, String neuron,
                                               String graphic, String spec) {
        return new ResultViews.Options(type, element, nucleus, neuron, graphic, spec, null,
                ResultViews.DEFAULT_FC_LOW, ResultViews.DEFAULT_FC_HIGH, true, true, 1, null, 0, false,
                null, null, null, null, null, null, null, null, 0);
    }

    /** Percorre o menu inteiro e pede cada gráfico, com cada neurônio e especificação. */
    private static List<String> everything(Path dir) throws Exception {
        ResultCatalog catalog = catalog(dir);
        ResultMenu.Menu menu = ResultMenu.build(catalog, dir);
        ResultViews views = new ResultViews(dir, catalog, SEED);
        List<String> problems = new ArrayList<>();
        int count = 0;
        for (ResultMenu.Option m : menu.muscles()) {
            for (ResultMenu.GraphicOption g : menu.biomechanical().get(m.value())) {
                for (ResultMenu.Option u : menu.motorUnits().get(m.value())) {
                    count += check(views, options("biomechanical", "", m.value(), u.value(), g.value(), ""), problems);
                }
            }
        }
        for (ResultMenu.Element e : menu.neural()) {
            for (ResultMenu.Option n : e.nuclei()) {
                for (ResultMenu.GraphicOption g : e.graphics().get(n.value())) {
                    for (ResultMenu.Option neuron : e.neurons().get(n.value())) {
                        if (g.stored() != neuron.value().contains(":")) {
                            continue;
                        }
                        List<ResultMenu.Option> specs = g.specs().isEmpty() ? List.of(new ResultMenu.Option("", ""))
                                : g.specs();
                        for (ResultMenu.Option spec : specs) {
                            count += check(views, options("neural", e.value(), n.value(), neuron.value(), g.value(),
                                    spec.value()), problems);
                        }
                    }
                }
            }
        }
        for (ResultMenu.Option p : menu.properties()) {
            String[] parts = p.value().split(":");
            count += check(views, options("properties", parts[0], parts[1], "", "", ""), problems);
        }
        count += check(views, options("summary", "", "", "", "", ""), problems);
        assertThat(count).isGreaterThan(50);
        return problems;
    }

    private static int check(ResultViews views, ResultViews.Options o, List<String> problems) {
        try {
            ResultViews.View v = views.view(o);
            if (v.title() == null || (v.series().isEmpty() && v.table() == null && v.message() == null)) {
                problems.add(o + ": vista vazia sem explicação");
            }
        } catch (RuntimeException e) {
            problems.add(o.type() + " " + o.element() + " " + o.nucleus() + " " + o.neuron() + " " + o.graphic() + " "
                    + o.spec() + ": " + e);
        }
        return 1;
    }

    @Test
    void todoGraficoDoMenuSaiSemErro() throws Exception {
        assertThat(everything(soleus)).isEmpty();
        assertThat(everything(renshaw)).isEmpty();
    }

    @Test
    void oMenuTemOsElementosDaExecucao() throws Exception {
        ResultMenu.Menu soleusMenu = ResultMenu.build(catalog(soleus), soleus);
        assertThat(soleusMenu.complete()).isTrue();
        assertThat(soleusMenu.neural()).extracting(ResultMenu.Element::value).contains("mns", "afs", "dts");
        assertThat(soleusMenu.biomechanical().get("SOL")).extracting(ResultMenu.GraphicOption::value)
                .contains("muscleLength", "tendonForce", "jointAngle", "jointAngleVsJointTorque", "EMG");
        assertThat(soleusMenu.types()).extracting(ResultMenu.Option::value)
                .contains("biomechanical", "neural", "properties", "summary", "preset:generalForSoleus");

        ResultMenu.Menu rcMenu = ResultMenu.build(catalog(renshaw), renshaw);
        ResultMenu.Element ins = rcMenu.neural().stream().filter(e -> e.value().equals("ins")).findFirst().orElseThrow();
        assertThat(ins.neurons().get("IN_ext")).extracting(ResultMenu.Option::value).contains("RC", "RC:1");
    }

    /** O EMG com as opções padrão é o {@code emg-SOL} do motor; refeito a partir do bruto, sai igual. */
    @Test
    void emgPadraoEOMesmoDoMotor() throws Exception {
        ResultCatalog catalog = catalog(soleus);
        ResultViews views = new ResultViews(soleus, catalog, SEED);
        ResultViews.View fromEngine = views.view(options("biomechanical", "", "SOL", "todos", "EMG", ""));
        ResultViews.View manual = new ResultViews(soleus, catalog, SEED).recomputingDefaultEmg()
                .view(options("biomechanical", "", "SOL", "todos", "EMG", ""));
        double[] a = fromEngine.series().get(0).y();
        double[] b = manual.series().get(0).y();
        assertThat(b).hasSameSizeAs(a);
        assertThat(b).containsExactly(a);
    }

    @Test
    void correnteInjetadaEOValorQueAMembranaUsou() throws Exception {
        ResultViews views = new ResultViews(soleus, catalog(soleus), SEED);
        ResultViews.View v = views.view(options("neural", "mns", "SOL", "S:1", "injectedCurrent", ""));
        double[] t = v.series().get(0).x();
        double[] i = v.series().get(0).y();
        assertThat(t).hasSize((int) (DURATION_MS / 0.05));
        for (int k = 0; k < t.length; k += 97) {
            assertThat(i[k]).isCloseTo(8 + 4 * Math.sin(2 * Math.PI * 10 * t[k] / 1000), org.assertj.core.data.Offset.offset(1e-9));
        }
    }

    @Test
    void frequenciaIntervalosEHistogramaSaemDosMesmosDisparos() throws Exception {
        ResultViews views = new ResultViews(soleus, catalog(soleus), SEED);
        ResultViews.View isi = views.view(options("neural", "mns", "SOL", "todos", "ISI", ""));
        ResultViews.View ifr = views.view(options("neural", "mns", "SOL", "todos", "firingRate", ""));
        ResultViews.View hist = views.view(options("neural", "mns", "SOL", "todos", "histogram", ""));
        double[] intervals = isi.series().get(0).y();
        assertThat(intervals).isNotEmpty();
        double total = 0;
        for (double c : hist.series().get(0).y()) {
            total += c;
        }
        assertThat(total).isEqualTo(intervals.length);
        assertThat(ifr.series().get(0).y()).hasSize(intervals.length);
        // número de intervalos = disparos - unidades que dispararam
        ResultFiles.Columns spikes = ResultFiles.read(soleus, "disparos-SOL", Integer.MAX_VALUE);
        long units = java.util.Arrays.stream(spikes.y()).distinct().count();
        assertThat(intervals.length).isEqualTo(spikes.y().length - (int) units);
    }

    @Test
    void estatisticasEFft() throws Exception {
        ResultViews views = new ResultViews(soleus, catalog(soleus), SEED);
        ResultViews.Options stats = new ResultViews.Options("biomechanical", "", "SOL", "todos", "muscleForce", "",
                null, 50, 1000, true, true, 1, "parameters", 0, false, 20.0, 80.0, null, null, null, null, null, null, 0);
        ResultViews.View v = views.view(stats);
        double[] y = v.series().get(0).y();
        double mean = java.util.Arrays.stream(y).average().orElseThrow();
        assertThat(v.stats().points()).isEqualTo(y.length);
        assertThat(v.stats().mean()).isCloseTo(mean, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(v.series()).extracting(ResultViews.Series::label).contains("Mean", "Mean - SD", "Mean + SD");

        ResultViews.Options fft = new ResultViews.Options("biomechanical", "", "SOL", "todos", "muscleForce", "",
                null, 50, 1000, true, true, 1, "fft", 1000, false, null, null, null, null, null, null, null, null, 0);
        ResultViews.View f = views.view(fft);
        assertThat(f.series().get(0).x()).hasSize(1024 / 2 + 1);
        assertThat(f.series().get(0).x()[1]).isCloseTo(20000.0 / 1024, org.assertj.core.data.Offset.offset(1e-9));
    }
}
