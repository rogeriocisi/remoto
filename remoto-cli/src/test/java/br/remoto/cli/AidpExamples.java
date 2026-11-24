package br.remoto.cli;

import br.remoto.core.scenario.Scenario;
import br.remoto.storage.JsonCodec;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * As tarefas de força e de posição do ReMoto_AIDP (a versão em Fortran do ReMoto adaptada para
 * doenças desmielinizantes, de M. C. de Oliveira, R. N. Watanabe e A. F. Kohn,
 * github.com/oliveiramarina/ReMoto_AIDP, pastas ForceTask e PositionTask) convertidas em cenários.
 * Cada tarefa tem os três cenários de lá: S0, o sujeito saudável, e S1 e S2, com a desmielinização
 * crescente, que nos arquivos {@code confForceTask_S*.rmto} e {@code confPositionTask_S*.rmto} é
 * feita pelas faixas de velocidade de condução dos axônios motores e, no S2, por um bloqueio de
 * condução. O desenho de cada experimento é o do programa de lá; os parâmetros dos modelos são os
 * do ReMoto em Java, sobre a configuração 24 do banco original.
 *
 * <p>Os arquivos ficam em scenarios/aidp-*.json; {@code AidpExamplesTest} confere que eles são o
 * que este gerador produz, e os grava com {@code -Dremoto.gerar.cenarios=<pasta>}.
 */
final class AidpExamples {

    private static final String SOURCE = "Converted from the %s task of ReMoto_AIDP, the Fortran version of ReMoto"
            + " for demyelinating diseases (M. C. de Oliveira, R. N. Watanabe and A. F. Kohn,"
            + " github.com/oliveiramarina/ReMoto_AIDP): the design of the experiment is that of the program there"
            + " and the conduction velocities are those of %s, and the model parameters are those of ReMoto in Java,"
            + " on top of configuration 24 of the original database.";

    /** O que o {@code conf*_S<n>.rmto} de lá muda: as faixas de velocidade e o bloqueio. */
    private record Subject(String file, String name, String conf, double[] slow, double[] fast,
            double[] fastFatigable, double blocked, String text) {
    }

    /** Os três sujeitos, com as faixas de velocidade de condução (m/s) de cada tipo de unidade. */
    private static final List<Subject> SUBJECTS = List.of(
            new Subject("s0", "S0, healthy", "confForceTask_S0.rmto / confPositionTask_S0.rmto",
                    new double[] {44, 51}, new double[] {51, 52}, new double[] {52, 53}, 0,
                    "The healthy subject (S0): the motor axons conduct from 44 to 53 m/s, from the slowest (the S"
                            + " units) to the fastest (the FF ones)."),
            new Subject("s1", "S1, demyelinated", "confForceTask_S1.rmto / confPositionTask_S1.rmto",
                    new double[] {5, 51}, new double[] {51, 52}, new double[] {52, 40}, 0,
                    "Moderate demyelination (S1): the range of the motor axons drops to 5 to 51 m/s in the S units"
                            + " and falls from 52 to 40 m/s in the FF ones, so that the thickest axons become the"
                            + " slowest. The sensory axons stay as in S0."),
            new Subject("s2", "S2, demyelinated with conduction block",
                    "confForceTask_S2.rmto / confPositionTask_S2.rmto",
                    new double[] {5, 51}, new double[] {51, 52}, new double[] {52, 40}, 0.65,
                    "Severe demyelination (S2): the velocity ranges are those of S1 and, on top of them, 65% of the"
                            + " axons of each motor nucleus, counting from the slowest, have their conduction"
                            + " blocked and carry no spike at all to the end-plate."));

    /** Os músculos de cada tarefa e os seus aferentes Ia, com as quantidades do exemplo. */
    private static final String[] TRICEPS = {
            "SOL:S=800", "SOL:FR=50", "SOL:FF=50", "MG:S=300", "MG:FR=150", "MG:FF=150",
            "LG:S=130", "LG:FR=65", "LG:FF=65", "SOL:Ia=400", "MG:Ia=160", "LG:Ia=160"};

    private static final double DURATION_MS = 12000;
    private static final double STEP_MS = 0.05;

    private AidpExamples() {
    }

    static List<ScenarioEdit.Example> build(Path scenariosDir) {
        Scenario base = new JsonCodec().read(scenariosDir.resolve("remoto-24-isb-2013-stretch-reflex.json"),
                Scenario.class);
        List<ScenarioEdit.Example> examples = new ArrayList<>();
        for (Subject s : SUBJECTS) {
            examples.add(forceTask(base, s));
        }
        for (Subject s : SUBJECTS) {
            examples.add(positionTask(base, s));
        }
        return examples;
    }

    // ------------------------------------------------------------------ as duas tarefas

    /**
     * A tarefa de força: o tríceps sural isométrico, com o comando descendente subindo de 40 para
     * 250 disparos/s em 2 s (ForceTask.f90).
     */
    private static ScenarioEdit.Example forceTask(Scenario base, Subject s) {
        ScenarioEdit c = new ScenarioEdit(base);
        List<String> active = new ArrayList<>(List.of(TRICEPS));
        active.add("DT:CM_ext=400");
        c.active(active.toArray(String[]::new));
        velocities(c, s, "SOL", "MG", "LG", "TA");
        c.seriesTract("CM_ext", 1, new double[] {0, 40, 2000, 40, 2000, 250, DURATION_MS, 250}, DURATION_MS);
        c.settings.put("cdMuscleModel", "hill");
        c.settings.put("cdJointModel", "isometric");
        c.settings.put("jointAngle", 0.0);
        c.settings.put("gammaDynamic", 25.0);
        c.settings.put("gammaStatic", 25.0);
        block(c, s, "SOL", "MG", "LG");
        return example(c, "aidp-force-task-" + s.file(), "force", s,
                "ReMoto_AIDP - Force task (" + s.name() + ")",
                "Force task: the soleus and the two gastrocnemii, with Hill muscles and the ankle isometric at 0°,"
                        + " are driven by 400 descending axons with 40 spikes/s in the first 2 s and 250 spikes/s"
                        + " afterwards, and the Ia afferents of the three muscles respond to the spindle, with the"
                        + " dynamic and static fusimotor drives at 25 spikes/s. The force of the triceps surae and"
                        + " the ankle torque over the 10 s of contraction are what the example measures. " + s.text());
    }

    /**
     * A tarefa de posição: o pé contra o pedal livre, com o comando descendente subindo de 40 para
     * 72,5 disparos/s em 2 s (PositionTask.f90).
     */
    private static ScenarioEdit.Example positionTask(Scenario base, Subject s) {
        ScenarioEdit c = new ScenarioEdit(base);
        List<String> active = new ArrayList<>(List.of(TRICEPS));
        active.addAll(List.of("TA:S=250", "TA:FR=50", "TA:FF=50", "TA:Ia=280", "DT:CM_ext=400"));
        c.active(active.toArray(String[]::new));
        velocities(c, s, "SOL", "MG", "LG", "TA");
        c.seriesTract("CM_ext", 7, new double[] {0, 40, 2000, 40, 2000, 1000 / 13.8, DURATION_MS, 1000 / 13.8},
                DURATION_MS);
        c.settings.put("cdMuscleModel", "hill");
        c.settings.put("cdJointModel", "position");
        c.settings.put("jointAngle", 5.0);
        c.settings.put("percentageOfMVC", 0.1);
        c.settings.put("gammaDynamic", 50.0);
        c.settings.put("gammaStatic", 48.0);
        block(c, s, "SOL", "MG", "LG", "TA");
        return example(c, "aidp-position-task-" + s.file(), "position", s,
                "ReMoto_AIDP - Position task (" + s.name() + ")",
                "Position task: the foot, free to rotate about the ankle from 5° on and loaded with 10% of the"
                        + " maximum voluntary torque, is controlled by the soleus, the gastrocnemii and the tibialis"
                        + " anterior, with Hill muscles and the Ia afferents of all four. The 400 descending axons"
                        + " have 40 spikes/s in the first 2 s and 72.5 afterwards, in a gamma process of order 7, and"
                        + " the dynamic fusimotor drive stays at 50 and the static one at 48 spikes/s. The ankle"
                        + " angle and its variability are what the example measures. " + s.text());
    }

    // ------------------------------------------------------------------ o que muda de um sujeito para outro

    /** As faixas de velocidade de condução dos axônios motores de cada núcleo. */
    private static void velocities(ScenarioEdit c, Subject s, String... nuclei) {
        for (String nucleus : nuclei) {
            c.axonVelocity(nucleus, "S", s.slow()[0], s.slow()[1]);
            c.axonVelocity(nucleus, "FR", s.fast()[0], s.fast()[1]);
            c.axonVelocity(nucleus, "FF", s.fastFatigable()[0], s.fastFatigable()[1]);
        }
    }

    /** O bloqueio de condução dos núcleos motores da tarefa, quando o sujeito tem bloqueio. */
    private static void block(ScenarioEdit c, Subject s, String... nuclei) {
        if (s.blocked() == 0) {
            return;
        }
        Map<String, Object> blocks = new LinkedHashMap<>();
        for (String nucleus : nuclei) {
            blocks.put(nucleus, s.blocked());
        }
        c.settings.put("axonConductionBlock", blocks);
    }

    /** O cenário da tarefa, com a fonte somada à descrição. */
    private static ScenarioEdit.Example example(ScenarioEdit c, String file, String task, Subject s, String name,
            String text) {
        return c.example(file, name, DURATION_MS, STEP_MS, text + " " + String.format(SOURCE, task, s.conf()));
    }
}
