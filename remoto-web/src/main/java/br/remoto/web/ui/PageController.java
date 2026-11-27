package br.remoto.web.ui;

import br.remoto.core.scenario.Scenario;
import br.remoto.storage.run.RunManifest;
import br.remoto.storage.run.RunRepository;
import br.remoto.storage.run.RunStatus;
import br.remoto.storage.scenario.PublicScenarioCatalog;
import br.remoto.storage.scenario.ScenarioRepository;
import br.remoto.web.api.ScenarioFiles;
import br.remoto.web.api.ScenarioSources;
import br.remoto.web.i18n.Messages;
import br.remoto.web.i18n.RemotoLocaleResolver;
import br.remoto.web.security.Caller;
import br.remoto.web.sim.InvalidScenarioException;
import br.remoto.web.sim.QuotaExceededException;
import br.remoto.web.sim.SimulationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Páginas Thymeleaf. As listas são fragmentos atualizados pelo htmx; o gráfico da execução
 * selecionada é desenhado com uPlot a partir da API (static/js/app.js).
 */
@Controller
class PageController {

    record RunRow(String runId, String scenarioName, RunStatus status, String statusLabel, Integer percent,
                  String created, String createdIso, String elapsed, boolean active) {
    }

    /**
     * A rota da porta de entrada por idioma: um código de idioma e nada mais ({@code pt},
     * {@code pt-br}). O grupo é não capturante porque o Spring não aceita captura na restrição.
     */
    private static final String LANGUAGE_ROUTE = "/{lang:[a-z][a-z](?:-[a-z][a-z][a-z]?[a-z]?)?}";

    /** O formato da data vem do idioma (dia/mês em português, mês/dia em inglês). */
    private static DateTimeFormatter created() {
        return DateTimeFormatter.ofPattern(Messages.msg("runs.created.pattern")).withZone(ZoneId.systemDefault());
    }

    private final PublicScenarioCatalog catalog;
    private final ScenarioRepository scenarios;
    private final RunRepository runs;
    private final SimulationService simulations;
    private final ScenarioSources sources;
    private final ScenarioFiles files;
    private final RemotoLocaleResolver locales;
    private final int areaDays;

    PageController(PublicScenarioCatalog catalog, ScenarioRepository scenarios, RunRepository runs,
                   SimulationService simulations, ScenarioSources sources, ScenarioFiles files,
                   RemotoLocaleResolver locales,
                   @Value("${remoto.retention.area-days:30}") int areaDays) {
        this.files = files;
        this.locales = locales;
        this.areaDays = areaDays;
        this.catalog = catalog;
        this.scenarios = scenarios;
        this.runs = runs;
        this.simulations = simulations;
        this.sources = sources;
    }

    @GetMapping("/")
    String index(Caller caller, Model model) {
        model.addAttribute("areaDays", areaDays);
        return "index";
    }

    /**
     * A porta de entrada de um idioma: {@code /pt} (com o context-path, {@code /simulator/pt})
     * serve esta mesma página inicial com os textos daquele idioma e guarda a escolha no cookie,
     * de modo que o endereço seja linkável — é por ele que o site do projeto manda quem clica em
     * "Simulador" na página em português. Não há página por idioma: o que muda são os textos, que
     * saem de {@code messages_<código>.properties}.
     *
     * <p>O idioma tem de estar declarado em {@code remoto.languages}; fora dela, 404, como
     * qualquer endereço que não existe. O padrão da rota casa somente com código de idioma
     * ({@code pt}, {@code pt-br}), para não engolir o que está na raiz da aplicação (o
     * {@code favicon.ico}, as pastas estáticas): não convém criar pasta estática de duas letras.
     */
    @GetMapping({LANGUAGE_ROUTE, LANGUAGE_ROUTE + "/"})
    String indexInLanguage(@PathVariable String lang, Caller caller, Model model,
                           HttpServletRequest request, HttpServletResponse response) {
        Locale locale = locales.match(lang);
        if (locale == null) {
            // NoSuchElementException, e não ResponseStatusException: o tratador de "problem
            // details" do Spring responderia a segunda em JSON, e aqui quem navega espera a
            // página de erro do site, como em qualquer outro endereço que não existe
            throw new NoSuchElementException(Messages.msg("route.notFound"));
        }
        // a página já sai neste idioma (o resolver o lê do caminho); o cookie é para que as telas
        // internas, que não têm porta por idioma, continuem nele
        locales.setLocale(request, response, locale);
        return index(caller, model);
    }

    @GetMapping("/ui/scenarios")
    String scenarios(Caller caller, Model model) {
        List<Scenario> publicScenarios = catalog.list();
        model.addAttribute("publicScenarios", publicScenarios);
        // a lista pública sai reunida por tema; a contagem da aba continua sendo o total
        model.addAttribute("publicGroups", ScenarioGroups.of(publicScenarios));
        model.addAttribute("mine", scenarios.list(caller.id()));
        return "fragments :: scenarios";
    }

    /** Copiar não pergunta nada, mas a segunda cópia leva a data e a hora, para não repetir o nome. */
    @PostMapping("/ui/scenarios/copy")
    String copy(Caller caller, @RequestParam String publicScenarioId, Model model) {
        catalog.find(publicScenarioId).ifPresent(s -> scenarios.create(caller.id(),
                copiesOf(caller, s).isEmpty() ? s : withName(s, copyName(s.name(), LocalDateTime.now()))));
        model.addAttribute("aba", "meus"); // mostra a cópia
        return scenarios(caller, model);
    }

    /**
     * Editar um cenário público: cenários públicos são de todos e só leitura, então o editor abre
     * numa cópia em "Meus cenários", criada aqui. O público continua intacto.
     *
     * <p>Quem já tem cópias daquele cenário costuma querer voltar para a que estava editando, e
     * não começar outra igual: nesse caso a página pergunta, em vez de criar mais uma. Começar
     * outra continua valendo — é só escolher, e aí ela sai com a data e a hora no nome, para as
     * duas não ficarem com o mesmo.
     */
    @PostMapping("/ui/scenarios/edit-copy")
    String editCopy(Caller caller, @RequestParam String publicScenarioId,
                    @RequestParam(defaultValue = "false") boolean novo, Model model,
                    HttpServletRequest request, HttpServletResponse response) {
        Optional<Scenario> original = catalog.find(publicScenarioId);
        if (original.isEmpty()) {
            return "redirect:/";
        }
        List<Scenario> copies = copiesOf(caller, original.get());
        if (!novo && !copies.isEmpty()) {
            model.addAttribute("askScenario", original.get());
            model.addAttribute("askGroup", ScenarioGroups.themeOf(publicScenarioId));
            model.addAttribute("askCopies", copies);
            model.addAttribute("aba", "publicos");
            return scenarios(caller, model); // a lista volta com a pergunta no cartão do cenário
        }
        Scenario copy = copies.isEmpty() ? original.get()
                : withName(original.get(), copyName(original.get().name(), LocalDateTime.now()));
        String id = scenarios.create(caller.id(), copy).value().id();
        // a lista é trocada no lugar (htmx), então quem leva ao editor é o cabeçalho; sem htmx,
        // o formulário é comum e o redirecionamento de sempre resolve
        if (request.getHeader("HX-Request") != null) {
            response.setHeader("HX-Redirect", request.getContextPath() + "/editor/" + id);
            return "fragments :: vazio";
        }
        return "redirect:/editor/" + id;
    }

    /**
     * As cópias que o usuário já tem de um cenário público: as que ainda têm o nome dele, como
     * saem daqui e do "Copiar" — o mesmo nome, ou ele seguido da data e da hora de uma cópia
     * posterior. Renomeou, deixa de ser reconhecida, e a página oferece começar outra, que é o
     * que acontecia sempre antes: o cenário guardado não registra de onde veio, e o nome é o que
     * há. O sufixo é conferido pelo formato, e não por qualquer texto depois do nome, para
     * "Position Task - Ib" não passar por cópia de "Position Task".
     */
    private List<Scenario> copiesOf(Caller caller, Scenario original) {
        return scenarios.list(caller.id()).stream()
                .filter(s -> s.name() != null && isCopyName(original.name(), s.name()))
                .toList();
    }

    static boolean isCopyName(String original, String name) {
        return name.equals(original)
                || name.startsWith(original + " ")
                && name.substring(original.length() + 1).matches("\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}");
    }

    /** O nome de mais uma cópia: o do cenário com a data e a hora, para não repetir o anterior. */
    static String copyName(String original, LocalDateTime when) {
        return original + " " + when.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm"));
    }

    private static Scenario withName(Scenario s, String name) {
        return new Scenario(s.id(), name, s.description(), s.schemaVersion(), s.seed(), s.durationMs(),
                s.stepMs(), s.engine(), s.legacy());
    }

    /** Importa arquivos .json e .zip para "Meus cenários" e mostra o que entrou e o que não. */
    @PostMapping(path = "/ui/scenarios/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    String importFiles(Caller caller, @RequestParam(name = "files", required = false) List<MultipartFile> uploads,
                       Model model) {
        ScenarioFiles.Imported imported = files.importFiles(caller.id(), uploads);
        model.addAttribute("imported", imported.created().size());
        model.addAttribute("importErrors", imported.errors());
        model.addAttribute("aba", "meus");
        return scenarios(caller, model);
    }

    @PostMapping("/ui/scenarios/{id}/delete")
    String deleteScenario(Caller caller, @PathVariable String id, Model model) {
        scenarios.delete(caller.id(), id);
        model.addAttribute("aba", "meus");
        return scenarios(caller, model);
    }

    @GetMapping("/ui/runs")
    String runs(Caller caller, Model model) {
        List<RunRow> rows = runs.list(caller.id()).stream().map(this::row).toList();
        model.addAttribute("runs", rows);
        return "fragments :: runs";
    }

    @PostMapping("/ui/runs")
    String submit(Caller caller,
                  @RequestParam(required = false) String publicScenarioId,
                  @RequestParam(required = false) String scenarioId,
                  Model model, HttpServletResponse response) {
        try {
            RunManifest m = simulations.submit(caller.id(), caller.origin(), sources.resolve(caller,
                    new ScenarioSources.RunRequest(null, scenarioId, publicScenarioId)));
            // a página abre a simulação nova na área de resultados
            response.setHeader("HX-Trigger", "{\"abrir-execucao\": \"" + m.runId() + "\"}");
        } catch (QuotaExceededException | InvalidScenarioException | NoSuchElementException | IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
        }
        return runs(caller, model);
    }

    /**
     * Importa uma simulação exportada (.zip). Devolve só a mensagem; o evento "execucoes" faz a
     * lista, que se atualiza a cada poucos segundos, se atualizar na hora.
     */
    @PostMapping(path = "/ui/runs/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    String importRun(Caller caller, @RequestParam(name = "file", required = false) MultipartFile file, Model model,
                     HttpServletResponse response) {
        if (file == null || file.isEmpty()) {
            model.addAttribute("importError", Messages.msg("import.noFile"));
        } else {
            try (var in = file.getInputStream()) {
                RunManifest m = runs.importZip(caller.id(), in);
                model.addAttribute("importedRun", m.scenarioName());
                response.setHeader("HX-Trigger", "execucoes");
            } catch (IllegalArgumentException | java.io.IOException e) {
                model.addAttribute("importError", file.getOriginalFilename() + ": " + e.getMessage());
            }
        }
        return "fragments :: runImport";
    }

    @PostMapping("/ui/runs/{id}/delete")
    String deleteRun(Caller caller, @PathVariable String id, Model model) {
        if (!simulations.cancel(caller.id(), id)) {
            runs.delete(caller.id(), id);
        }
        return runs(caller, model);
    }

    private RunRow row(RunManifest m) {
        Integer percent = simulations.liveProgress(m.runId()).map(f -> (int) Math.round(f * 100)).orElse(null);
        return new RunRow(m.runId(), m.scenarioName(), m.status(), label(m.status()), percent,
                created().format(m.createdAt()), m.createdAt().toString(), elapsed(m),
                simulations.isActive(m.runId()));
    }

    static String label(RunStatus status) {
        return Messages.msg("status." + status.name());
    }

    /** Do início ao fim da simulação (ou até agora, se ainda roda); "—" se não chegou a começar. */
    static String elapsed(RunManifest m) {
        if (m.startedAt() == null) {
            return "—";
        }
        Instant end = m.finishedAt() != null ? m.finishedAt() : Instant.now();
        long s = Math.max(0, Duration.between(m.startedAt(), end).toSeconds());
        if (s < 60) {
            return s + " s";
        }
        if (s < 3600) {
            return String.format("%d min %02d s", s / 60, s % 60);
        }
        return String.format("%d h %02d min", s / 3600, (s % 3600) / 60);
    }
}
