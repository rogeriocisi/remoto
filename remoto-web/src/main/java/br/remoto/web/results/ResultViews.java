package br.remoto.web.results;

import br.remoto.core.engine.Seeds;
import br.remoto.core.model.emg.BandPassFilter;
import br.remoto.core.model.emg.ThirdOrderBandPass;
import br.remoto.storage.run.ResultFiles;

import br.remoto.web.i18n.Messages;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Os gráficos da tela de resultados do site antigo, calculados a partir dos resultados gravados,
 * como o {@code ResultDisplay.processGraphic} de lá calculava a partir da simulação em memória:
 * raster de disparos, frequência instantânea, intervalos entre disparos e o histograma deles,
 * sinais dos neurônios guardados, propriedades de neurônios e unidades motoras, séries do músculo e
 * da articulação, gráficos de uma grandeza contra outra, EMG com os filtros, o ruído e a atenuação
 * da tela, estatísticas e FFT.
 *
 * <p>Onde o código antigo tinha erros evidentes (o histograma e a frequência dos tratos não
 * achavam os neurônios, a condutância "gInhi" não existia), vale o que a tela anunciava.
 */
public final class ResultViews {

    /** O EMG que a tela antiga mostrava por padrão, o mesmo gravado em {@code emg-<músculo>}. */
    public static final String DEFAULT_FILTER = "1st2nd";
    public static final double DEFAULT_FC_LOW = 50;
    public static final double DEFAULT_FC_HIGH = 1000;
    /** Bins do histograma de intervalos ({@code ReMoto.numberOfBins}). */
    public static final int HISTOGRAM_BINS = 50;

    /**
     * O que se pede, com os nomes dos campos da tela antiga.
     *
     * @param type     "biomechanical", "neural", "properties" ou "summary"
     * @param element  elemento neural: "mns", "afs", "dts" ou "ins"
     * @param nucleus  músculo (SOL, MG...), núcleo de interneurônios (IN_ext, IN_flex) ou DT
     * @param neuron   "todos", um tipo (S, FR, FF, Ia, RC, CM_ext...) ou um neurônio guardado, "tipo:índice"
     * @param graphic  código do gráfico ({@link Graphics})
     * @param spec     especificação: "soma", "terminal" ou "estimulo" nos disparos; a condutância
     * @param emgDecimation 1 para todas as amostras do EMG; k para uma em cada k
     * @param analysis "", "parameters" ou "fft"
     * @param fftPoints pontos usados na FFT (0 = todos), arredondados para a potência de 2 seguinte
     * @param maxPoints pontos por série enviados para o gráfico (o cálculo usa todos)
     */
    public record Options(String type, String element, String nucleus, String neuron, String graphic, String spec,
                          String emgFilter, double fcLow, double fcHigh, boolean emgNoise, boolean emgAttenuation,
                          int emgDecimation, String analysis, int fftPoints, boolean periodic,
                          Double xmin, Double xmax, Double ymin, Double ymax,
                          Double fftXmin, Double fftXmax, Double fftYmin, Double fftYmax, int maxPoints) {

        public Options {
            type = blank(type, "neural");
            element = blank(element, "mns");
            nucleus = blank(nucleus, "");
            neuron = blank(neuron, "todos");
            graphic = blank(graphic, "spikeTimes");
            spec = blank(spec, "");
            emgFilter = blank(emgFilter, DEFAULT_FILTER);
            analysis = blank(analysis, "");
            emgDecimation = Math.max(1, emgDecimation);
            maxPoints = maxPoints <= 0 ? 20_000 : Math.min(maxPoints, 200_000);
        }

        private static String blank(String s, String fallback) {
            return s == null || s.isBlank() ? fallback : s.trim();
        }

        boolean defaultEmg() {
            return DEFAULT_FILTER.equals(emgFilter) && fcLow == DEFAULT_FC_LOW && fcHigh == DEFAULT_FC_HIGH
                    && emgNoise && emgAttenuation && "todos".equals(neuron);
        }
    }

    public record Series(String label, double[] x, double[] y) {
    }

    public record Stats(int points, double mean, double variance, double std, double cv, double min, double max) {
    }

    public record Table(List<String> columns, List<List<String>> rows) {
    }

    /**
     * @param kind        "linha", "pontos", "histograma" ou "tabela"
     * @param totalPoints pontos calculados (antes da redução para o gráfico)
     * @param message     aviso para quem vê (por exemplo, quando não há dados)
     */
    public record View(String title, String xLabel, String yLabel, String kind, List<Series> series, Stats stats,
                       Table table, int totalPoints, String message) {
    }

    private final Path dir;
    private final ResultCatalog catalog;
    private final long seed;
    private final Map<String, ResultFiles.Table> tables = new HashMap<>();
    // com as opções padrão, devolve o emg-<músculo> gravado; os testes desligam para conferir o cálculo
    private boolean storedDefaultEmg = true;

    public ResultViews(Path resultsDir, ResultCatalog catalog, long seed) {
        this.dir = resultsDir;
        this.catalog = catalog;
        this.seed = seed;
    }

    ResultViews recomputingDefaultEmg() {
        storedDefaultEmg = false;
        return this;
    }

    public View view(Options o) {
        return switch (o.type()) {
            case "properties" -> propertiesTable(o);
            case "summary" -> summaryTable(o);
            case "biomechanical" -> biomechanical(o);
            default -> neural(o);
        };
    }

    // ---- elementos neurais ---------------------------------------------------------------------

    private View neural(Options o) {
        Graphics.Graphic g = Graphics.neural(o.element(), o.graphic())
                .orElseThrow(() -> new NoSuchElementException(Messages.msg("results.unknownGraphic", o.graphic())));
        String who = neuronLabel(o);
        return switch (g.code()) {
            case "spikeTimes" -> spikeTimes(o, g, who);
            case "firingRate", "ISI", "histogram", "stimulusRate", "stimulusRateVsForce" -> fromIntervals(o, g, who);
            case "somaPotential", "dendritePotential", "conductance", "injectedCurrent", "VsxVdot" -> stored(o, g, who);
            case "meanFiringRate" -> meanFiringRate(o, g, who);
            case "meanISI" -> line(o, g, who, "trato-isi-medio-" + type(o));
            default -> neuronProperty(o, g, who);
        };
    }

    private View spikeTimes(Options o, Graphics.Graphic g, String who) {
        String name = spikesFile(o);
        double[][] s = spikes(name);
        Set<Integer> units = selectedUnits(o);
        List<double[]> points = new ArrayList<>();
        for (int i = 0; i < s[0].length; i++) {
            if (units == null || units.contains((int) s[1][i])) {
                points.add(new double[] {s[0][i], s[1][i]});
            }
        }
        String point = switch (spikePoint(o)) {
            case "soma" -> "at Soma";
            case "estimulo" -> "at Stimulus Point";
            default -> "at Terminal";
        };
        return finish(o, g.title() + " - " + who + " - " + point, g, "pontos", List.of(series(who + " " + point, points)));
    }

    /** Frequência instantânea, intervalos, histograma e taxa de estímulo: todos saem dos intervalos de cada unidade. */
    private View fromIntervals(Options o, Graphics.Graphic g, String who) {
        String name = spikesFile(o);
        double[][] s = spikes(name);
        Set<Integer> units = selectedUnits(o);
        Map<Integer, List<Double>> trains = new LinkedHashMap<>();
        for (int i = 0; i < s[0].length; i++) {
            int u = (int) s[1][i];
            if (units == null || units.contains(u)) {
                trains.computeIfAbsent(u, k -> new ArrayList<>()).add(s[0][i]);
            }
        }
        Map<Integer, Double> contraction = g.code().startsWith("stimulusRate") ? contractionTimes(o) : Map.of();
        List<double[]> points = new ArrayList<>();
        List<Double> intervals = new ArrayList<>();
        for (Map.Entry<Integer, List<Double>> e : trains.entrySet()) {
            List<Double> train = e.getValue();
            Double ct = contraction.get(e.getKey());
            for (int i = 1; i < train.size(); i++) {
                double t = train.get(i);
                double isi = t - train.get(i - 1);
                switch (g.code()) {
                    case "firingRate" -> {
                        double ifr = 1000.0 / isi;
                        if (ifr < 10000) {
                            points.add(new double[] {t, ifr});
                        }
                    }
                    case "ISI" -> points.add(new double[] {t, isi});
                    case "histogram" -> intervals.add(isi);
                    default -> {
                        if (ct != null) {
                            points.add(new double[] {t, ct / isi});
                        }
                    }
                }
            }
        }
        points.sort((a, b) -> Double.compare(a[0], b[0]));
        if (g.code().equals("histogram")) {
            return histogram(o, g, who, intervals);
        }
        if (g.code().equals("stimulusRateVsForce")) {
            return stimulusRateVsForce(o, g, who, points);
        }
        String title = g.title() + " - " + who;
        return finish(o, title, g, "pontos", List.of(series(who, points)));
    }

    /** Cada taxa de estímulo com a primeira amostra da força do músculo depois dela, como a tela antiga. */
    private View stimulusRateVsForce(Options o, Graphics.Graphic g, String who, List<double[]> rates) {
        ResultFiles.Columns force = column("forca-" + o.nucleus());
        List<double[]> points = new ArrayList<>();
        int k = 0;
        for (double[] rate : rates) {
            while (k < force.x().length && force.x()[k] <= rate[0]) {
                k++;
            }
            if (k < force.x().length) {
                points.add(new double[] {rate[1], force.y()[k]});
            }
        }
        return finish(o, g.title() + " - " + who, g, "pontos", List.of(series(who, points)));
    }

    private View histogram(Options o, Graphics.Graphic g, String who, List<Double> intervals) {
        List<Double> values = new ArrayList<>();
        for (double v : intervals) {
            if (inside(v, o.xmin(), o.xmax())) {
                values.add(v);
            }
        }
        if (values.isEmpty()) {
            return empty(o, g.title() + " - " + who, g, "histograma", "Sem intervalos entre disparos.");
        }
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        int bins = HISTOGRAM_BINS;
        double width = max > min ? (max - min) / bins : 1;
        double[] counts = new double[bins];
        for (double v : values) {
            int b = max > min ? (int) ((v - min) / width) : 0;
            counts[Math.min(b, bins - 1)]++;
        }
        double[] centers = new double[bins];
        for (int b = 0; b < bins; b++) {
            centers[b] = min + (b + 0.5) * width;
        }
        Series s = new Series(who, centers, counts);
        return new View(g.title() + " - " + who, g.xLabel(), g.yLabel(), "histograma", List.of(s),
                stats(o, values.stream().mapToDouble(Double::doubleValue).toArray()), null, values.size(),
                values.size() + " intervalos em " + bins + " bins de " + String.format(java.util.Locale.ROOT, "%.4g", width)
                        + " ms");
    }

    /** Sinais de um neurônio guardado: potenciais, condutâncias, corrente injetada e o plano de fase do soma. */
    private View stored(Options o, Graphics.Graphic g, String who) {
        String[] id = storedId(o);
        if (id == null) {
            return empty(o, g.title() + " - " + who, g, "linha",
                    Messages.msg("results.storedNeuron"));
        }
        String name = "sinais-" + o.nucleus() + "-" + id[0] + "-" + id[1];
        ResultFiles.Table t = table(name);
        double[] time = t.numbers("t_ms");
        String title = g.title() + " - " + who;
        switch (g.code()) {
            case "somaPotential" -> {
                return finish(o, title, g, "linha", List.of(new Series(who, time, t.numbers("Vs[mV]"))));
            }
            case "dendritePotential" -> {
                return finish(o, title, g, "linha", List.of(new Series(who, time, t.numbers("Vd[mV]"))));
            }
            case "conductance" -> {
                String gName = o.spec().isEmpty() ? "gNa" : o.spec();
                String column = gName + "[nS]";
                if (t.column(column) < 0) {
                    return empty(o, g.title() + " - " + gName, g, "linha", Messages.msg("results.noConductance", gName));
                }
                return finish(o, g.title() + " - " + gName, g, "linha", List.of(new Series(who, time, t.numbers(column))));
            }
            case "injectedCurrent" -> {
                if (t.column("inj[nA]") < 0) {
                    return empty(o, title, g, "linha", Messages.msg("results.noInjectedCurrent"));
                }
                return finish(o, title, g, "linha", List.of(new Series(who, time, t.numbers("inj[nA]"))));
            }
            default -> {
                // plano de fase: Vs contra a derivada dele (mV/s), por diferença dividida
                double[] vs = t.numbers("Vs[mV]");
                int n = Math.max(0, vs.length - 1);
                double[] x = new double[n];
                double[] y = new double[n];
                for (int i = 0; i < n; i++) {
                    x[i] = vs[i];
                    y[i] = (vs[i + 1] - vs[i]) / (time[i + 1] - time[i]) * 1000;
                }
                return finish(o, title, g, "pontos", List.of(new Series(who, x, y)));
            }
        }
    }

    private View meanFiringRate(Options o, Graphics.Graphic g, String who) {
        String type = type(o);
        String name = switch (type) {
            case "II" -> "fuso-ii-";
            case "Ib" -> "otg-ib-";
            default -> "fuso-ia-";
        } + o.nucleus();
        View v = line(o, new Graphics.Graphic(g.code(), g.label(), g.title(), g.xLabel(), type + " [Hz]", g.kind()), who,
                name);
        return v;
    }

    /** Propriedades de cada neurônio contra o índice (ou o limiar contra a condutância de entrada). */
    private View neuronProperty(Options o, Graphics.Graphic g, String who) {
        String tableName = switch (o.element()) {
            case "afs" -> "tabela-aferentes-" + o.nucleus();
            case "ins" -> "tabela-interneuronios-" + o.nucleus();
            default -> "tabela-motoneuronios-" + o.nucleus();
        };
        ResultFiles.Table t = table(tableName);
        String column = switch (g.code()) {
            case "recruitmentThresholds" -> "afs".equals(o.element()) ? "limiar_recrutamento" : "limiar[mV]";
            case "axonConductionVelocity" -> "velocidade_axonio[m/s]";
            case "somaticInputResistance" -> "resistencia_entrada[MOhm]";
            case "rheobase" -> "reobase[nA]";
            case "axonThresholds" -> "limiar_axonio[mA]";
            case "neuronPositions" -> "posicao";
            default -> "limiar[mV]";
        };
        int[] rows = selectedRows(t, o);
        double[] index = t.numbers("unidade");
        double[] value = t.numbers(column);
        double[] rn = g.code().equals("thresholdVsInputConductance") ? t.numbers("resistencia_entrada[MOhm]") : null;
        List<double[]> points = new ArrayList<>();
        for (int r : rows) {
            points.add(rn == null ? new double[] {index[r], value[r]} : new double[] {1 / rn[r], value[r]});
        }
        Graphics.Graphic shown = g;
        if (g.code().equals("recruitmentThresholds") && !"todos".equals(o.neuron()) && !"mns".equals(o.element())) {
            shown = new Graphics.Graphic(g.code(), g.label(), g.title(), g.xLabel(), "Threshold", g.kind());
        }
        return finish(o, g.title() + " - " + who, shown, "pontos", List.of(series(who, points)));
    }

    // ---- elementos biomecânicos ----------------------------------------------------------------

    private View biomechanical(Options o) {
        Graphics.Graphic g = Graphics.biomechanical(o.graphic())
                .orElseThrow(() -> new NoSuchElementException(Messages.msg("results.unknownGraphic", o.graphic())));
        String m = o.nucleus();
        String who = muscleLabel(o);
        return switch (g.code()) {
            case "EMG" -> emg(o, g, who);
            case "muscleForce" -> {
                String[] id = storedId(o);
                yield line(o, g, who, id == null ? "forca-" + m : "forca-um-" + m + "-" + id[1]);
            }
            case "twitchPeak", "contractionTime", "halfRelaxation", "amplitudeMUAP", "MUsPosition" -> motorUnitProperty(o, g, who);
            case "jointAngleVsJointTorque" -> xy(o, g, who, "articulacao-angulo", "torque");
            case "jointAngleVsVelocity" -> xy(o, g, who, "articulacao-angulo", "articulacao-velocidade");
            case "MTLengthVSTendonForce" -> xy(o, g, who, "mt-comprimento-" + m, "tendao-forca-" + m);
            case "tendonLengthVSTendonForce" -> xy(o, g, who, "tendao-comprimento-" + m, "tendao-forca-" + m);
            case "muscleLengthVSTendonForce" -> xy(o, g, who, "musculo-comprimento-" + m, "tendao-forca-" + m);
            default -> line(o, g, who, BIOMECHANICAL_FILES.get(g.code()).replace("{m}", m));
        };
    }

    /** Arquivo de cada série biomecânica; {m} é o músculo. */
    static final Map<String, String> BIOMECHANICAL_FILES = Map.ofEntries(
            Map.entry("muscleLength", "musculo-comprimento-{m}"),
            Map.entry("muscleVelocity", "musculo-velocidade-{m}"),
            Map.entry("muscleAcceleration", "musculo-aceleracao-{m}"),
            Map.entry("activationNormSType", "musculo-ativacao-s-{m}"),
            Map.entry("activationNormFType", "musculo-ativacao-f-{m}"),
            Map.entry("activationNorm", "musculo-ativacao-{m}"),
            Map.entry("muscleLengthNorm", "musculo-comprimento-normalizado-{m}"),
            Map.entry("forceParallelElement", "musculo-forca-paralela-{m}"),
            Map.entry("forceViscousElement", "musculo-forca-viscosa-{m}"),
            Map.entry("forceActiveSType", "musculo-forca-ativa-s-{m}"),
            Map.entry("forceActiveFType", "musculo-forca-ativa-f-{m}"),
            Map.entry("muscleForce", "forca-{m}"),
            Map.entry("tendonLength", "tendao-comprimento-{m}"),
            Map.entry("tendonForce", "tendao-forca-{m}"),
            Map.entry("normalizedForce", "musculo-forca-normalizada-{m}"),
            Map.entry("jointAngle", "articulacao-angulo"),
            Map.entry("jointVelocity", "articulacao-velocidade"),
            Map.entry("jointCenterMass", "articulacao-centro-massa"),
            Map.entry("jointCenterPressure", "articulacao-centro-pressao"),
            Map.entry("jointTorque", "torque"),
            Map.entry("jointMuscleTorque", "articulacao-torque-muscular"),
            Map.entry("jointGravTorque", "articulacao-torque-gravitacional"),
            Map.entry("jointPassiveTorque", "articulacao-torque-passivo"),
            Map.entry("jointDisturbance", "articulacao-disturbio"),
            Map.entry("musculotendonLength", "mt-comprimento-{m}"),
            Map.entry("musculotendonMomentArm", "mt-braco-momento-{m}"),
            Map.entry("pennationAngle", "musculo-angulo-penacao-{m}"));

    /** Os arquivos de que um gráfico biomecânico precisa ({m} é o músculo), para os menus. */
    static List<String> biomechanicalFiles(String code) {
        return switch (code) {
            case "EMG" -> List.of("emg-bruto-{m}|emg-{m}");
            case "twitchPeak", "contractionTime", "halfRelaxation", "amplitudeMUAP", "MUsPosition" ->
                    List.of("tabela-unidades-motoras-{m}");
            case "jointAngleVsJointTorque" -> List.of("articulacao-angulo", "torque");
            case "jointAngleVsVelocity" -> List.of("articulacao-angulo", "articulacao-velocidade");
            case "MTLengthVSTendonForce" -> List.of("mt-comprimento-{m}", "tendao-forca-{m}");
            case "tendonLengthVSTendonForce" -> List.of("tendao-comprimento-{m}", "tendao-forca-{m}");
            case "muscleLengthVSTendonForce" -> List.of("musculo-comprimento-{m}", "tendao-forca-{m}");
            default -> BIOMECHANICAL_FILES.containsKey(code) ? List.of(BIOMECHANICAL_FILES.get(code)) : List.of();
        };
    }

    private View motorUnitProperty(Options o, Graphics.Graphic g, String who) {
        ResultFiles.Table t = table("tabela-unidades-motoras-" + o.nucleus());
        int[] rows = selectedRows(t, o);
        double[] index = t.numbers("unidade");
        List<double[]> points = new ArrayList<>();
        if (g.code().equals("MUsPosition")) {
            double[] z = t.numbers("posicao_z[mm]");
            double[] y = t.numbers("posicao_y[mm]");
            for (int r : rows) {
                points.add(new double[] {z[r], y[r]});
            }
        } else {
            String column = switch (g.code()) {
                case "twitchPeak" -> "pico_abalo[N]";
                case "contractionTime" -> "tempo_contracao[ms]";
                case "halfRelaxation" -> "meia_relaxacao[ms]";
                default -> "amplitude_muap[mV]";
            };
            double[] v = t.numbers(column);
            for (int r : rows) {
                points.add(new double[] {index[r], v[r]});
            }
        }
        return finish(o, g.title() + " - " + who, g, "pontos", List.of(series(who, points)));
    }

    /** Uma grandeza contra outra, amostra a amostra (as duas séries têm a mesma amostragem). */
    private View xy(Options o, Graphics.Graphic g, String who, String xName, String yName) {
        ResultFiles.Columns x = column(xName);
        ResultFiles.Columns y = column(yName);
        int n = Math.min(x.y().length, y.y().length);
        return finish(o, g.title(), g, "pontos",
                List.of(new Series(who, Arrays.copyOf(x.y(), n), Arrays.copyOf(y.y(), n))));
    }

    /**
     * O EMG como o ramo "EMG" do {@code ResultDisplay}: a soma dos MUAPs (com ou sem atenuação, do
     * músculo ou de uma unidade), mais ruído uniforme de média |EMG| × noiseEMG, e o filtro
     * escolhido. O ruído vem de um gerador com a seed da execução, o mesmo das saídas: com as opções
     * padrão, o resultado é o {@code emg-<músculo>} gravado pelo motor.
     */
    private View emg(Options o, Graphics.Graphic g, String who) {
        String m = o.nucleus();
        String[] id = storedId(o);
        String title = g.title() + " - " + who;
        if (storedDefaultEmg && o.defaultEmg() && catalog.has("emg-" + m) && o.emgDecimation() == 1) {
            return line(o, g, who, "emg-" + m);
        }
        String rawName = id == null
                ? (o.emgAttenuation() ? "emg-bruto-" : "emg-bruto-sem-atenuacao-") + m
                : (o.emgAttenuation() ? "emg-bruto-um-" : "emg-bruto-um-sem-atenuacao-") + m + "-" + id[1];
        if (!catalog.has(rawName)) {
            return empty(o, title, g, "linha", Messages.msg("results.noEmg", rawName));
        }
        ResultFiles.Columns raw = column(rawName);
        double[] t = raw.x();
        double[] x = raw.y();
        double step = t.length > 1 ? t[1] - t[0] : parameter("passo", 0.05);
        double meanNoise = 0;
        if (o.emgNoise()) {
            for (double v : x) {
                meanNoise += Math.abs(v);
            }
            meanNoise = x.length == 0 ? 0 : meanNoise / x.length * parameter("ruido_emg", 0);
        }
        RandomGenerator noise = Seeds.stream(seed, "emg-ruido/" + m + (id == null ? "" : "/" + id[1]));
        double[] y = new double[x.length];
        switch (o.emgFilter()) {
            case "3rd3rd" -> {
                double[] filtered = ThirdOrderBandPass.bandPass(x, o.fcLow(), o.fcHigh(), step * 1e-3);
                for (int i = 0; i < x.length; i++) {
                    y[i] = filtered[i] + meanNoise * noise.nextDouble();
                }
            }
            case "noFiltering" -> {
                for (int i = 0; i < x.length; i++) {
                    y[i] = x[i] + meanNoise * noise.nextDouble();
                }
            }
            default -> {
                BandPassFilter filter = new BandPassFilter(o.fcLow(), o.fcHigh(), step * 1e-3);
                for (int i = 0; i < x.length; i++) {
                    y[i] = filter.next(x[i] + meanNoise * noise.nextDouble());
                }
            }
        }
        int k = o.emgDecimation();
        if (k > 1) {
            int n = (x.length + k - 1) / k;
            double[] td = new double[n];
            double[] yd = new double[n];
            for (int i = 0, j = 0; i < x.length; i += k, j++) {
                td[j] = t[i];
                yd[j] = y[i];
            }
            t = td;
            y = yd;
        }
        return finish(o, title, g, "linha", List.of(new Series(who, t, y)));
    }

    // ---- tabelas -------------------------------------------------------------------------------

    /** As propriedades (a opção "Properties" da tela antiga), como tabela. */
    private View propertiesTable(Options o) {
        String name = switch (o.element()) {
            case "afs" -> "tabela-aferentes-";
            case "ins" -> "tabela-interneuronios-";
            case "mus" -> "tabela-unidades-motoras-";
            default -> "tabela-motoneuronios-";
        } + o.nucleus();
        return tableView(name, "Properties - " + o.nucleus(), o);
    }

    private View summaryTable(Options o) {
        return tableView("tabela-resumo", "Summary", o);
    }

    private View tableView(String name, String title, Options o) {
        if (!catalog.has(name)) {
            return new View(title, "", "", "tabela", List.of(), null, new Table(List.of(), List.of()), 0,
                    Messages.msg("results.noTable", name));
        }
        ResultFiles.Table t = table(name);
        List<List<String>> rows = t.rows();
        if (!o.nucleus().isEmpty() && t.column("nucleo") >= 0) {
            int c = t.column("nucleo");
            rows = rows.stream().filter(r -> c < r.size() && r.get(c).equals(o.nucleus())).toList();
        }
        return new View(title, "", "", "tabela", List.of(), null, new Table(t.columns(), rows), rows.size(), null);
    }

    // ---- seleção de neurônios ------------------------------------------------------------------

    private String spikesFile(Options o) {
        String type = type(o);
        String name = switch (o.element()) {
            case "afs" -> ("estimulo".equals(spikePoint(o)) ? "disparos-estimulo-" : "disparos-") + o.nucleus() + "-" + type;
            case "ins" -> "disparos-" + o.nucleus() + "-" + type;
            case "dts" -> "disparos-DT-" + type;
            default -> ("soma".equals(spikePoint(o)) ? "disparos-soma-" : "disparos-") + o.nucleus();
        };
        if (!catalog.has(name)) {
            // as saídas básicas não gravam disparos-<m> quando nenhum motoneurônio disparou
            if (name.equals("disparos-" + o.nucleus()) && catalog.has("tabela-motoneuronios-" + o.nucleus())) {
                return null;
            }
            throw new NoSuchElementException(Messages.msg("results.missing", name));
        }
        return name;
    }

    private static String spikePoint(Options o) {
        return switch (o.spec()) {
            case "soma", "atSoma" -> "soma";
            case "estimulo", "atStimulusPoint" -> "estimulo";
            default -> "terminal";
        };
    }

    /** O tipo escolhido (S, Ia, RC, CM_ext...), sem o índice de um neurônio guardado. */
    private static String type(Options o) {
        String n = o.neuron();
        int colon = n.indexOf(':');
        return colon >= 0 ? n.substring(0, colon) : n;
    }

    /** {tipo, índice} de um neurônio (ou unidade motora) guardado, ou null para um grupo. */
    private static String[] storedId(Options o) {
        String n = o.neuron();
        int colon = n.indexOf(':');
        return colon < 0 ? null : new String[] {n.substring(0, colon), n.substring(colon + 1)};
    }

    /**
     * As unidades do grupo escolhido, ou null para todas. Nos motoneurônios, os tipos S, FR e FF
     * vêm da tabela de motoneurônios; nos outros elementos, o arquivo já é de um tipo só.
     */
    private Set<Integer> selectedUnits(Options o) {
        String[] id = storedId(o);
        if (id != null) {
            return Set.of(Integer.parseInt(id[1]));
        }
        if (!"mns".equals(o.element()) || "todos".equals(o.neuron())) {
            return null;
        }
        ResultFiles.Table t = table("tabela-motoneuronios-" + o.nucleus());
        Set<Integer> units = new HashSet<>();
        double[] index = t.numbers("unidade");
        String[] types = t.texts("tipo");
        for (int i = 0; i < index.length; i++) {
            if (types[i].equals(o.neuron())) {
                units.add((int) index[i]);
            }
        }
        return units;
    }

    /** As linhas de uma tabela de propriedades que pertencem à seleção. */
    private static int[] selectedRows(ResultFiles.Table t, Options o) {
        String[] id = storedId(o);
        String[] types = t.texts("tipo");
        double[] index = t.numbers("unidade");
        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < types.length; i++) {
            boolean keep;
            if (id != null) {
                keep = types[i].equals(id[0]) && (int) index[i] == Integer.parseInt(id[1]);
            } else {
                keep = "todos".equals(o.neuron()) || types[i].equals(o.neuron());
            }
            if (keep) {
                rows.add(i);
            }
        }
        return rows.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Tempo de contração de cada unidade motora, pelo índice (o mesmo do motoneurônio). */
    private Map<Integer, Double> contractionTimes(Options o) {
        ResultFiles.Table t = table("tabela-unidades-motoras-" + o.nucleus());
        double[] index = t.numbers("unidade");
        double[] ct = t.numbers("tempo_contracao[ms]");
        Map<Integer, Double> map = new HashMap<>();
        for (int i = 0; i < index.length; i++) {
            map.put((int) index[i], ct[i]);
        }
        return map;
    }

    private String neuronLabel(Options o) {
        String[] id = storedId(o);
        String n = o.neuron();
        if (id != null) {
            String category = switch (o.element()) {
                case "ins" -> "IN";
                case "afs" -> "AF";
                default -> "MN";
            };
            return category + " " + id[0] + " " + id[1];
        }
        return switch (o.element()) {
            case "mns" -> "todos".equals(n) ? "All MNs" : "All MNs " + n;
            case "afs" -> "All " + n + " afferents";
            case "ins" -> switch (n) {
                case "RC" -> "renshaw";
                case "IaIn" -> "IaIN";
                case "IbIn" -> "IbIN";
                default -> n;
            };
            default -> n;
        } + (o.nucleus().isEmpty() || "dts".equals(o.element()) ? "" : " (" + o.nucleus() + ")");
    }

    private static String muscleLabel(Options o) {
        String[] id = storedId(o);
        if (id != null) {
            return "MU " + id[0] + " " + id[1] + " (" + o.nucleus() + ")";
        }
        return ("todos".equals(o.neuron()) ? "All MUs" : "All MUs " + o.neuron()) + " (" + o.nucleus() + ")";
    }

    // ---- leitura e acabamento ------------------------------------------------------------------

    private View line(Options o, Graphics.Graphic g, String who, String name) {
        if (!catalog.has(name)) {
            return empty(o, g.title() + " - " + who, g, "linha", Messages.msg("results.noResult", name));
        }
        ResultFiles.Columns c = column(name);
        return finish(o, g.title() + " - " + who, g, "linha", List.of(new Series(who, c.x(), c.y())));
    }

    private ResultFiles.Columns column(String name) {
        if (!catalog.has(name)) {
            throw new NoSuchElementException(Messages.msg("results.missing", name));
        }
        return ResultFiles.read(dir, name, Integer.MAX_VALUE);
    }

    private double[][] spikes(String name) {
        if (name == null) {
            return new double[][] {new double[0], new double[0]};
        }
        ResultFiles.Columns c = column(name);
        return new double[][] {c.x(), c.y()};
    }

    private ResultFiles.Table table(String name) {
        if (!catalog.has(name)) {
            throw new NoSuchElementException(Messages.msg("results.missing", name));
        }
        return tables.computeIfAbsent(name, n -> ResultFiles.readTable(dir, n));
    }

    private double parameter(String key, double fallback) {
        if (!catalog.has("tabela-parametros")) {
            return fallback;
        }
        ResultFiles.Table t = table("tabela-parametros");
        String[] keys = t.texts("parametro");
        double[] values = t.numbers("valor");
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equals(key)) {
                return values[i];
            }
        }
        return fallback;
    }

    private static Series series(String label, List<double[]> points) {
        double[] x = new double[points.size()];
        double[] y = new double[points.size()];
        for (int i = 0; i < x.length; i++) {
            x[i] = points.get(i)[0];
            y[i] = points.get(i)[1];
        }
        return new Series(label, x, y);
    }

    private static View empty(Options o, String title, Graphics.Graphic g, String kind, String message) {
        return new View(title, g.xLabel(), g.yLabel(), kind, List.of(), null, null, 0, message);
    }

    /** Recorta pelos limites dos eixos, calcula a análise pedida e reduz os pontos para o gráfico. */
    private View finish(Options o, String title, Graphics.Graphic g, String kind, List<Series> series) {
        List<Series> clipped = new ArrayList<>();
        int total = 0;
        for (Series s : series) {
            Series c = clip(s, o.xmin(), o.xmax(), o.ymin(), o.ymax());
            clipped.add(c);
            total += c.x().length;
        }
        if (total == 0) {
            return new View(title, g.xLabel(), g.yLabel(), kind, clipped, null, null, 0,
                    Messages.msg("results.empty"));
        }
        Series main = clipped.get(0);
        if ("fft".equals(o.analysis())) {
            Series spectrum = clip(fft(main, o), o.fftXmin(), o.fftXmax(), o.fftYmin(), o.fftYmax());
            return new View("FFT - " + title, "Frequency [Hz]", "Amplitude", "linha", List.of(reduce(spectrum, o)),
                    null, null, spectrum.x().length, null);
        }
        Stats stats = null;
        List<Series> shown = new ArrayList<>();
        for (Series s : clipped) {
            shown.add(reduce(s, o));
        }
        if ("parameters".equals(o.analysis())) {
            stats = stats(o, main.y());
            double x0 = main.x()[0];
            double x1 = main.x()[main.x().length - 1];
            shown.add(new Series("Mean", new double[] {x0, x1}, new double[] {stats.mean(), stats.mean()}));
            shown.add(new Series("Mean - SD", new double[] {x0, x1},
                    new double[] {stats.mean() - stats.std(), stats.mean() - stats.std()}));
            shown.add(new Series("Mean + SD", new double[] {x0, x1},
                    new double[] {stats.mean() + stats.std(), stats.mean() + stats.std()}));
        }
        String message = total > o.maxPoints() ? Messages.msg("results.preview", o.maxPoints(), total) : null;
        return new View(title, g.xLabel(), g.yLabel(), kind, shown, stats, null, total, message);
    }

    private static Series clip(Series s, Double xmin, Double xmax, Double ymin, Double ymax) {
        if (xmin == null && xmax == null && ymin == null && ymax == null) {
            return s;
        }
        double[] x = new double[s.x().length];
        double[] y = new double[s.x().length];
        int n = 0;
        for (int i = 0; i < s.x().length; i++) {
            if (inside(s.x()[i], xmin, xmax) && inside(s.y()[i], ymin, ymax)) {
                x[n] = s.x()[i];
                y[n] = s.y()[i];
                n++;
            }
        }
        return new Series(s.label(), Arrays.copyOf(x, n), Arrays.copyOf(y, n));
    }

    private static boolean inside(double v, Double min, Double max) {
        return (min == null || v >= min) && (max == null || v <= max);
    }

    private static Series reduce(Series s, Options o) {
        int n = s.x().length;
        if (n <= o.maxPoints()) {
            return s;
        }
        int stride = (n + o.maxPoints() - 1) / o.maxPoints();
        int m = (n + stride - 1) / stride;
        double[] x = new double[m];
        double[] y = new double[m];
        for (int i = 0, j = 0; i < n; i += stride, j++) {
            x[j] = s.x()[i];
            y[j] = s.y()[i];
        }
        return new Series(s.label(), x, y);
    }

    /** "Basic statistical parameters" da tela antiga; variância populacional, CV = DP / média. */
    static Stats stats(Options o, double[] y) {
        int n = y.length;
        if (n == 0) {
            return new Stats(0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }
        double sum = 0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double v : y) {
            sum += v;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        double mean = sum / n;
        double squares = 0;
        for (double v : y) {
            squares += (v - mean) * (v - mean);
        }
        double variance = squares / n;
        double std = Math.sqrt(variance);
        return new Stats(n, mean, variance, std, std / mean, min, max);
    }

    /**
     * A FFT da tela antiga: os primeiros N pontos (N = a potência de 2 seguinte ao pedido, com zeros
     * no fim), amostrados a 1/Δt; amplitude |X| ou, para sinal periódico, 2|X|/n.
     */
    static Series fft(Series s, Options o) {
        int count = s.y().length;
        int requested = o.fftPoints() > 0 ? o.fftPoints() : count;
        int n = Integer.highestOneBit(Math.max(1, requested - 1)) << 1;
        if (n < 2) {
            n = 2;
        }
        double[] re = new double[n];
        double[] im = new double[n];
        for (int i = 0; i < Math.min(n, count); i++) {
            re[i] = s.y()[i];
        }
        Fft.transform(re, im);
        double dt = count > 1 ? (s.x()[1] - s.x()[0]) * 1e-3 : 1;
        double fs = 1 / dt;
        int half = n / 2 + 1;
        double[] f = new double[half];
        double[] amp = new double[half];
        for (int i = 0; i < half; i++) {
            f[i] = i * fs / n;
            double abs = Math.hypot(re[i], im[i]);
            amp[i] = o.periodic() ? 2 * abs / count : abs;
        }
        return new Series(s.label(), f, amp);
    }
}
