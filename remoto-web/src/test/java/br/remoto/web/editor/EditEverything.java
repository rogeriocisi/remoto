package br.remoto.web.editor;

import br.remoto.core.engine.legacy.LegacyConfig;
import br.remoto.core.engine.legacy.LegacyJson;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Edita todas as telas do editor de uma vez, com valores determinísticos, e resume a gravação num
 * hash. Os valores de referência foram gerados com o {@code ConfigurationDAO} original gravando
 * num HSQLDB, antes de o editor passar a gravar sem banco.
 */
final class EditEverything {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private EditEverything() {
    }

    /**
     * Em cada tela, na seleção padrão e em cada opção do primeiro seletor, muda todos os campos:
     * números por um fator, inteiros por um incremento, uma em cada cinco caixas e uma em cada
     * quatro escolhas.
     *
     * @return os erros de validação
     */
    static List<String> edit(LegacyEditor editor) {
        List<String> errors = new ArrayList<>();
        int k = 0;
        for (LegacyScreens.MenuGroup group : LegacyScreens.menu()) {
            for (LegacyScreens.MenuItem item : group.items()) {
                ScreenView first = editor.view(item.id(), Map.of());
                List<Map<String, String>> selections = new ArrayList<>();
                selections.add(Map.of());
                if (!first.selectors().isEmpty()) {
                    ScreenView.Selector s0 = first.selectors().get(0);
                    for (Field.Option o : s0.options()) {
                        selections.add(Map.of(s0.name(), o.value()));
                    }
                }
                for (Map<String, String> selection : selections) {
                    ScreenView view = editor.view(item.id(), selection);
                    Map<String, String> sel = new LinkedHashMap<>();
                    view.selectors().forEach(s -> sel.put(s.name(), s.selected()));
                    Map<String, String> form = new LinkedHashMap<>();
                    for (ScreenView.Section section : view.sections()) {
                        for (ScreenView.Row row : section.rows()) {
                            for (ScreenView.Cell cell : row.cells()) {
                                if (cell.name().endsWith(".indexStoreVm1") || cell.name().endsWith(".indexStoreVm2")
                                        || cell.name().endsWith(".distanceDecay")) {
                                    continue; // campo novo, que o DAO original não gravava: a referência é anterior a ele
                                }
                                if ((item.id().equals("joint") || item.id().equals("muscleSpindle")
                                        || item.id().equals("sensory")) && cell.name().startsWith("p1.")
                                        || item.id().equals("stimulation") && cell.name().startsWith("p2.")) {
                                    // partes novas, posteriores à referência: sinais no tempo (ângulo imposto,
                                    // fusimotor), ordem gama dos aferentes, pulso e limiar do estímulo do nervo
                                    continue;
                                }
                                if (cell.name().endsWith(".std")) {
                                    // o Std dos tratos existia na referência, mas o DAO original o perdia ao
                                    // gravar; hoje fica no settings. Conta na numeração e fica como estava.
                                    k++;
                                    continue;
                                }
                                k++;
                                form.put(cell.name(), changed(cell, k));
                            }
                        }
                    }
                    errors.addAll(editor.apply(item.id(), sel, form).stream()
                            .map(e -> item.id() + sel + ": " + e).toList());
                }
            }
        }
        return errors;
    }

    private static String changed(ScreenView.Cell cell, int k) {
        String v = cell.value();
        return switch (cell.kind()) {
            case NUMBER -> v == null || v.isBlank() ? v
                    : String.valueOf(Double.parseDouble(v) * (1 + 0.01 * (k % 7)) + 0.001 * (k % 3));
            case INTEGER -> v == null || v.isBlank() ? v : String.valueOf(Long.parseLong(v) + (k % 3));
            case BOOLEAN -> {
                boolean b = "true".equals(v);
                yield (k % 5 == 0) ? (b ? null : "true") : (b ? "true" : null);
            }
            case CHOICE -> {
                // a opção "imposed" do modelo de articulação é posterior à referência
                List<Field.Option> options = cell.options().stream()
                        .filter(o -> !br.remoto.core.simulation.ReMoto.imposedAngle.equals(o.value())).toList();
                yield options.isEmpty() || k % 4 != 0 ? v : options.get(k % options.size()).value();
            }
            default -> v == null ? "" : v + (k % 2 == 0 ? " (editado)" : "");
        };
    }

    /** SHA-256 das tabelas (em ordem de nome, sem a data da gravação), dos settings, da duração e do passo. */
    @SuppressWarnings("unchecked")
    static String hash(LegacyEditor.Saved saved) throws Exception {
        Map<String, Object> map = LegacyJson.toMap(saved.config());
        Map<String, Object> tables = new TreeMap<>((Map<String, Object>) map.get("tables"));
        Map<String, Object> configuration = MAPPER.convertValue(tables.get("CONFIGURATION"), LinkedHashMap.class);
        int date = ((List<String>) configuration.get("columns")).indexOf("DT_CONFIGURATION");
        for (Object row : (List<Object>) configuration.get("rows")) {
            ((List<Object>) row).set(date, "*");
        }
        tables.put("CONFIGURATION", configuration);
        String text = MAPPER.writeValueAsString(tables) + "\n" + MAPPER.writeValueAsString(map.get("settings"))
                + "\n" + saved.durationMs() + " " + saved.stepMs();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    static String hash(LegacyConfig config, double durationMs, double stepMs) throws Exception {
        LegacyEditor editor = LegacyEditor.open(config, durationMs, stepMs);
        List<String> errors = edit(editor);
        if (!errors.isEmpty()) {
            throw new AssertionError("erros de validação: " + errors);
        }
        return hash(editor.save());
    }
}
