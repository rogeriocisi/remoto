package br.remoto.web.ui;

import br.remoto.core.scenario.Scenario;
import br.remoto.core.scenario.ScenarioValidator;
import br.remoto.legacy.LegacyJson;
import br.remoto.legacy.editor.LegacyEditor;
import br.remoto.legacy.editor.LegacyScreens;
import br.remoto.legacy.editor.ScreenView;
import br.remoto.storage.StaleWriteException;
import br.remoto.storage.Versioned;
import br.remoto.storage.scenario.ScenarioRepository;
import br.remoto.web.security.Caller;
import br.remoto.web.sim.InvalidScenarioException;
import br.remoto.web.sim.QuotaExceededException;
import br.remoto.web.sim.SimulationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Editor de cenários no modelo do site antigo: menu lateral com as mesmas telas (Structure,
 * Neural Parameters, Stimulation...) e os mesmos campos. Só edita cenários do próprio usuário;
 * cenários públicos são copiados antes ("Copiar").
 */
@Controller
@RequestMapping("/editor/{id}")
class EditorController {

    static final String GENERAL = "general";
    private static final String SELECTION_PREFIX = "sel.";
    /** Parâmetros da URL que não são seletores de tela. */
    private static final java.util.Set<String> NOT_SELECTORS = java.util.Set.of(
            "screen", "saved", "erro", "etag", "name", "description", "seed", "durationMs", "stepMs", "_csrf");

    private final ScenarioRepository scenarios;
    private final SimulationService simulations;

    EditorController(ScenarioRepository scenarios, SimulationService simulations) {
        this.scenarios = scenarios;
        this.simulations = simulations;
    }

    @GetMapping
    String edit(Caller caller, @PathVariable String id,
                @RequestParam(defaultValue = GENERAL) String screen,
                @RequestParam Map<String, String> params, Model model) {
        Versioned<Scenario> current = find(caller, id);
        fill(model, current, screen, selection(params), List.of());
        model.addAttribute("saved", params.containsKey("saved"));
        return "editor";
    }

    @PostMapping
    String save(Caller caller, @PathVariable String id, @RequestParam Map<String, String> form,
                Model model, RedirectAttributes redirect) {
        Versioned<Scenario> current = find(caller, id);
        String screen = form.getOrDefault("screen", GENERAL);
        Map<String, String> selection = selection(form);
        String etag = form.get("etag");
        Scenario scenario = current.value();
        List<String> errors = new ArrayList<>();
        Scenario updated;

        if (GENERAL.equals(screen)) {
            updated = general(scenario, form, errors);
        } else {
            requireLegacy(scenario);
            LegacyEditor editor = LegacyEditor.open(LegacyJson.toConfig(scenario.legacy()),
                    scenario.durationMs(), scenario.stepMs());
            errors.addAll(editor.apply(screen, selection, form));
            updated = null;
            if (errors.isEmpty()) {
                LegacyEditor.Saved saved = editor.save();
                updated = new Scenario(scenario.id(), scenario.name(), scenario.description(),
                        scenario.schemaVersion(), scenario.seed(), saved.durationMs(), saved.stepMs(),
                        scenario.nuclei(), scenario.engine(), LegacyJson.toMap(saved.config()));
            }
        }
        if (updated != null) {
            errors.addAll(ScenarioValidator.validate(updated));
        }
        if (!errors.isEmpty()) {
            fill(model, current, screen, selection, errors);
            return "editor";
        }
        try {
            scenarios.update(caller.id(), updated, etag);
        } catch (StaleWriteException e) {
            fill(model, find(caller, id), screen, selection, List.of(
                    "O cenário foi alterado em outra aba ou dispositivo. Os valores abaixo são os atuais; refaça a edição."));
            return "editor";
        }
        redirect.addAttribute("screen", screen);
        selection.forEach(redirect::addAttribute);
        redirect.addAttribute("saved", "1");
        return "redirect:/editor/" + id;
    }

    @PostMapping("/run")
    String run(Caller caller, @PathVariable String id, RedirectAttributes redirect) {
        Scenario scenario = find(caller, id).value();
        try {
            simulations.submit(caller.id(), scenario);
            return "redirect:/";
        } catch (QuotaExceededException | InvalidScenarioException | IllegalArgumentException e) {
            redirect.addAttribute("erro", e.getMessage());
            return "redirect:/editor/" + id;
        }
    }

    /** Cenário de outro usuário ou inexistente: 404, como na API. */
    @org.springframework.web.bind.annotation.ExceptionHandler(NoSuchElementException.class)
    org.springframework.http.ResponseEntity<String> notFound(NoSuchElementException e) {
        return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                .body(e.getMessage());
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    org.springframework.http.ResponseEntity<String> badRequest(IllegalArgumentException e) {
        return org.springframework.http.ResponseEntity.badRequest()
                .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                .body(e.getMessage());
    }

    private void fill(Model model, Versioned<Scenario> current, String screen, Map<String, String> selection,
                      List<String> errors) {
        Scenario scenario = current.value();
        boolean legacy = Scenario.ENGINE_LEGACY.equals(scenario.engineOrDefault());
        if (!GENERAL.equals(screen) && (!legacy || LegacyScreens.find(screen).isEmpty())) {
            screen = GENERAL;
        }
        model.addAttribute("scenario", scenario);
        model.addAttribute("etag", current.etag());
        model.addAttribute("legacy", legacy);
        model.addAttribute("menu", legacy ? LegacyScreens.menu() : List.of());
        model.addAttribute("screen", screen);
        model.addAttribute("errors", errors);
        if (!GENERAL.equals(screen)) {
            ScreenView view = LegacyEditor.open(LegacyJson.toConfig(scenario.legacy()), scenario.durationMs(),
                    scenario.stepMs()).view(screen, selection);
            model.addAttribute("view", view);
        }
    }

    /** general.jsp ("Scenarios"): nome e descrição; aqui também a seed. */
    private static Scenario general(Scenario s, Map<String, String> form, List<String> errors) {
        String name = form.getOrDefault("name", s.name()).trim();
        String description = form.getOrDefault("description", s.description());
        long seed = s.seed();
        try {
            seed = Long.parseLong(form.getOrDefault("seed", Long.toString(s.seed())).trim());
        } catch (NumberFormatException e) {
            errors.add("Seed: número inteiro inválido");
        }
        double duration = s.durationMs();
        double step = s.stepMs();
        if (!Scenario.ENGINE_LEGACY.equals(s.engineOrDefault())) {
            duration = number(form, "durationMs", s.durationMs(), "Duração", errors);
            step = number(form, "stepMs", s.stepMs(), "Passo", errors);
        }
        return new Scenario(s.id(), name, description, s.schemaVersion(), seed, duration, step,
                s.nuclei(), s.engine(), s.legacy());
    }

    private static double number(Map<String, String> form, String key, double fallback, String label, List<String> errors) {
        String raw = form.get(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            errors.add(label + ": número inválido");
            return fallback;
        }
    }

    private Versioned<Scenario> find(Caller caller, String id) {
        return scenarios.find(caller.id(), id).orElseThrow(() -> new NoSuchElementException("cenário não encontrado"));
    }

    private static void requireLegacy(Scenario scenario) {
        if (!Scenario.ENGINE_LEGACY.equals(scenario.engineOrDefault())) {
            throw new IllegalArgumentException("este cenário não usa os modelos originais");
        }
    }

    /** Parâmetros "sel.pool" etc. da requisição, ou "pool" direto na URL. */
    private static Map<String, String> selection(Map<String, String> params) {
        Map<String, String> selection = new LinkedHashMap<>();
        params.forEach((k, v) -> {
            if (k.startsWith(SELECTION_PREFIX)) {
                selection.put(k.substring(SELECTION_PREFIX.length()), v);
            } else if (k.matches("[A-Za-z]+") && !NOT_SELECTORS.contains(k)) {
                // seletores da tela na URL (pool, type, typePre...); a tela ignora os que não conhece
                selection.putIfAbsent(k, v);
            }
        });
        return selection;
    }
}
