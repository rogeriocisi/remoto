package br.remoto.web.api;

import br.remoto.core.scenario.Scenario;
import br.remoto.storage.run.RunManifest;
import br.remoto.storage.run.RunRepository;
import br.remoto.web.i18n.Messages;
import br.remoto.web.results.ResultCatalog;
import br.remoto.web.results.ResultMenu;
import br.remoto.web.results.ResultViews;
import br.remoto.web.security.Caller;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * A tela de resultados do motor original: os menus de uma execução e cada gráfico (ou tabela), em
 * JSON para a página e em CSV para baixar, como a opção "file" do site antigo.
 */
@RestController
@RequestMapping("/api/runs/{id}")
class ResultsController {

    private final RunRepository runs;

    ResultsController(RunRepository runs) {
        this.runs = runs;
    }

    /**
     * A tela de resultados é a do motor original. Execuções antigas do motor provisório (já
     * removido) gravaram saídas com os mesmos nomes, mas ficam com os gráficos simples.
     */
    @GetMapping("/menu")
    ResultMenu.Menu menu(Caller caller, @PathVariable String id) {
        Path dir = runs.resultsDir(caller.id(), find(caller, id).runId());
        boolean original = runs.config(caller.id(), id)
                .map(s -> Scenario.ENGINE_LEGACY.equals(s.engineOrDefault())).orElse(false);
        return original ? ResultMenu.build(catalog(caller, id, dir), dir) : ResultMenu.none();
    }

    @GetMapping("/view")
    ResultViews.View view(Caller caller, @PathVariable String id, ViewParams p) {
        return views(caller, id).view(p.options());
    }

    @GetMapping(path = "/view.csv", produces = "text/csv")
    ResponseEntity<byte[]> csv(Caller caller, @PathVariable String id, ViewParams p) {
        ResultViews.Options o = p.options();
        ResultViews.View v = views(caller, id).view(new ResultViews.Options(o.type(), o.element(), o.nucleus(),
                o.neuron(), o.graphic(), o.spec(), o.emgFilter(), o.fcLow(), o.fcHigh(), o.emgNoise(),
                o.emgAttenuation(), o.emgDecimation(), o.analysis(), o.fftPoints(), o.periodic(), o.xmin(), o.xmax(),
                o.ymin(), o.ymax(), o.fftXmin(), o.fftXmax(), o.fftYmin(), o.fftYmax(), 200_000));
        String file = fileName(o).replaceAll("[^A-Za-z0-9_-]", "_");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv(v).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Os parâmetros da tela (nomes em inglês, como os campos de lá); os ausentes ficam com os
     * padrões da tela antiga.
     */
    record ViewParams(String type, String element, String nucleus, String neuron, String graphic, String spec,
                      String emgFilter, Double fcLow, Double fcHigh, Boolean emgNoise, Boolean emgAttenuation,
                      Integer emgDecimation, String analysis, Integer fftPoints, Boolean periodic,
                      Double xmin, Double xmax, Double ymin, Double ymax,
                      Double fftXmin, Double fftXmax, Double fftYmin, Double fftYmax, Integer maxPoints) {

        ResultViews.Options options() {
            return new ResultViews.Options(type, element, nucleus, neuron, graphic, spec, emgFilter,
                    fcLow == null ? ResultViews.DEFAULT_FC_LOW : fcLow,
                    fcHigh == null ? ResultViews.DEFAULT_FC_HIGH : fcHigh,
                    emgNoise == null || emgNoise, emgAttenuation == null || emgAttenuation,
                    emgDecimation == null ? 1 : emgDecimation, analysis, fftPoints == null ? 0 : fftPoints,
                    periodic != null && periodic, xmin, xmax, ymin, ymax, fftXmin, fftXmax, fftYmin, fftYmax,
                    maxPoints == null ? 0 : maxPoints);
        }
    }

    /**
     * O nome do arquivo baixado: o da tabela, nas telas de tabela (que não têm gráfico escolhido —
     * o nome saía com o gráfico padrão, "spikeTimes"), e o do gráfico nas outras.
     */
    static String fileName(ResultViews.Options o) {
        String neuron = "todos".equals(o.neuron()) ? "all" : o.neuron();
        return switch (o.type()) {
            case "summary" -> "summary";
            case "properties" -> "properties-" + o.element() + "-" + o.nucleus();
            default -> o.graphic() + "-" + o.nucleus() + "-" + neuron;
        };
    }

    static String csv(ResultViews.View v) {
        StringBuilder sb = new StringBuilder();
        if (v.table() != null) {
            sb.append(String.join(",", v.table().columns())).append('\n');
            for (List<String> row : v.table().rows()) {
                sb.append(String.join(",", row.stream().map(ResultsController::cell).toList())).append('\n');
            }
            return sb.toString();
        }
        if (v.stats() != null) {
            ResultViews.Stats s = v.stats();
            sb.append("Number of points,").append(s.points()).append('\n')
                    .append("Mean,").append(number(s.mean())).append('\n')
                    .append("Variance,").append(number(s.variance())).append('\n')
                    .append("Standard deviation,").append(number(s.std())).append('\n')
                    .append("Coeficient of variation,").append(number(s.cv())).append('\n')
                    .append("Minimum value,").append(number(s.min())).append('\n')
                    .append("Maximum value,").append(number(s.max())).append('\n');
            return sb.toString();
        }
        for (ResultViews.Series s : v.series()) {
            sb.append(cell(v.xLabel())).append(',').append(cell(s.label() + " - " + v.yLabel())).append('\n');
            for (int i = 0; i < s.x().length; i++) {
                sb.append(number(s.x()[i])).append(',').append(number(s.y()[i])).append('\n');
            }
        }
        return sb.toString();
    }

    private static String number(double v) {
        return Double.toString(v);
    }

    private static String cell(String text) {
        if (text.indexOf(',') < 0 && text.indexOf('"') < 0) {
            return text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    private ResultViews views(Caller caller, String id) {
        RunManifest m = find(caller, id);
        Path dir = runs.resultsDir(caller.id(), id);
        return new ResultViews(dir, catalog(caller, id, dir), m.seed());
    }

    private ResultCatalog catalog(Caller caller, String id, Path dir) {
        return ResultCatalog.read(dir, runs.resultNames(caller.id(), id));
    }

    private RunManifest find(Caller caller, String id) {
        return runs.find(caller.id(), id).orElseThrow(() -> new NoSuchElementException(Messages.msg("run.notFound")));
    }
}
