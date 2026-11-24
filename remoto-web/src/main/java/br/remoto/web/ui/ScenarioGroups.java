package br.remoto.web.ui;

import br.remoto.core.scenario.Scenario;
import br.remoto.web.i18n.Messages;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Os cenários públicos reunidos por tema, para a lista da página inicial. Quarenta e três
 * cenários numa lista corrida são difíceis de percorrer, e quem procura um fenômeno quer ver
 * juntos os que tratam dele, mesmo vindos de origens diferentes: o reflexo H do banco antigo
 * fica ao lado do convertido do projectFR.
 *
 * <p>O tema é só apresentação, e por isso mora aqui, e não no cenário, que é o que se exporta,
 * importa e roda na linha de comando. Cenário sem tema cai no último grupo, e o
 * {@code ScenarioGroupsTest} falha se algum cenário público ficar lá: acrescentar um cenário é
 * também classificá-lo.
 */
final class ScenarioGroups {

    /** O grupo de quem não foi classificado; é o último da lista. */
    static final String UNCLASSIFIED = "other";

    /** A ordem em que os grupos aparecem na tela. */
    private static final List<String> ORDER = List.of("contraction", "reflexes", "recurrent",
            "proprioception", "command", "posture", "demyelination", "complete", UNCLASSIFIED);

    private static final Map<String, String> THEME = new LinkedHashMap<>();

    static {
        // o núcleo motor movido por um comando descendente, com o tornozelo parado
        group("contraction",
                "remoto-17-default-soleus-contraction",
                "remoto-14-isometric-contraction",
                "remoto-16-recruitment",
                "remoto-32-variability-gamma",
                "remoto-39-isometrictaskam",
                "projectfr-descending-command",
                "projectfr-motor-unit-pool-emg",
                "projectfr-force-variability");

        // o que um estímulo no nervo, ou um estiramento, provoca de volta
        group("reflexes",
                "remoto-18-neuroscience-meeting-2012",
                "remoto-19-biceps-brachii-stim-ia",
                "remoto-24-isb-2013-stretch-reflex",
                "estiramento-tornozelo",
                "projectfr-h-reflex-m-wave",
                "projectfr-reciprocal-inhibition",
                "projectfr-node-axons");

        // motoneurônios e células de Renshaw
        group("recurrent",
                "remoto-44-identsolrenshaw",
                "projectfr-mn-pool-renshaw",
                "projectfr-renshaw-injected-current",
                "projectfr-antidromic-stimulation");

        // fuso, OTG e a realimentação aferente, com o pé livre para girar
        group("proprioception",
                "remoto-25-position-task",
                "remoto-26-position-task-iia",
                "remoto-27-position-task-iia-no-ia",
                "remoto-28-position-task-ib",
                "projectfr-muscle-spindle",
                "projectfr-isometric-closed-loop");

        // a estatística do comando e a resposta do núcleo a ele
        group("command",
                "remoto-34-identification-sol-mg",
                "remoto-36-identification-sol-mg",
                "remoto-35-identification-lg",
                "remoto-38-identsol",
                "remoto-41-ammodrenshaw",
                "remoto-42-jn-watanabe-and-kohn-2015",
                "projectfr-neural-tract",
                "projectfr-neural-tract-unit");

        // o corpo e a articulação, com o músculo de Hill
        group("posture",
                "projectfr-postural-control",
                "projectfr-ankle-impedance",
                "projectfr-hill-muscle");

        // as tarefas do ReMoto_AIDP, com os três sujeitos
        group("demyelination",
                "aidp-force-task-s0",
                "aidp-force-task-s1",
                "aidp-force-task-s2",
                "aidp-position-task-s0",
                "aidp-position-task-s1",
                "aidp-position-task-s2");

        group("complete", "modelo-completo");
    }

    private ScenarioGroups() {
    }

    private static void group(String key, String... ids) {
        for (String id : ids) {
            THEME.put(id, key);
        }
    }

    /** Um grupo com o seu rótulo, no idioma da página, e os cenários dele. */
    record Group(String key, String label, List<Scenario> scenarios) {
    }

    /** O tema de um cenário, ou {@value #UNCLASSIFIED} se ele não foi classificado. */
    static String themeOf(String id) {
        return THEME.getOrDefault(id, UNCLASSIFIED);
    }

    /** Os ids classificados aqui, para o teste conferir que todos existem em scenarios/. */
    static Set<String> classified() {
        return Set.copyOf(THEME.keySet());
    }

    /** Os cenários na ordem dos grupos; grupo sem cenário não aparece. */
    static List<Group> of(List<Scenario> scenarios) {
        Map<String, List<Scenario>> byKey = new LinkedHashMap<>();
        for (String key : ORDER) {
            byKey.put(key, new ArrayList<>());
        }
        for (Scenario s : scenarios) {
            byKey.get(themeOf(s.id())).add(s);
        }
        List<Group> groups = new ArrayList<>();
        byKey.forEach((key, list) -> {
            if (!list.isEmpty()) {
                groups.add(new Group(key, Messages.msg("scenarios.group." + key), list));
            }
        });
        return groups;
    }
}
