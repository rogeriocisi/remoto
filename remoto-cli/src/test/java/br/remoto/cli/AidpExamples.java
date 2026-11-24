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

    private static final String SOURCE = "Convertido da tarefa de %s do ReMoto_AIDP, a versão em Fortran do ReMoto"
            + " para doenças desmielinizantes (M. C. de Oliveira, R. N. Watanabe e A. F. Kohn,"
            + " github.com/oliveiramarina/ReMoto_AIDP): o desenho do experimento é o do programa de lá e as"
            + " velocidades de condução são as do %s, e os parâmetros dos modelos são os do ReMoto em Java, sobre a"
            + " configuração 24 do banco original.";

    /** O que o {@code conf*_S<n>.rmto} de lá muda: as faixas de velocidade e o bloqueio. */
    private record Subject(String file, String name, String conf, double[] slow, double[] fast,
            double[] fastFatigable, double blocked, String text) {
    }

    /** Os três sujeitos, com as faixas de velocidade de condução (m/s) de cada tipo de unidade. */
    private static final List<Subject> SUBJECTS = List.of(
            new Subject("s0", "S0, healthy", "confForceTask_S0.rmto / confPositionTask_S0.rmto",
                    new double[] {44, 51}, new double[] {51, 52}, new double[] {52, 53}, 0,
                    "O sujeito saudável (S0): os axônios motores conduzem de 44 a 53 m/s, do mais lento (as"
                            + " unidades S) ao mais rápido (as FF)."),
            new Subject("s1", "S1, demyelinated", "confForceTask_S1.rmto / confPositionTask_S1.rmto",
                    new double[] {5, 51}, new double[] {51, 52}, new double[] {52, 40}, 0,
                    "A desmielinização moderada (S1): a faixa dos axônios motores desce para 5 a 51 m/s nas"
                            + " unidades S e cai de 52 para 40 m/s nas FF, de modo que os axônios mais grossos"
                            + " passam a ser os mais lentos. Os axônios sensoriais ficam como no S0."),
            new Subject("s2", "S2, demyelinated with conduction block",
                    "confForceTask_S2.rmto / confPositionTask_S2.rmto",
                    new double[] {5, 51}, new double[] {51, 52}, new double[] {52, 40}, 0.65,
                    "A desmielinização grave (S2): as faixas de velocidade são as do S1 e, além delas, 65% dos"
                            + " axônios de cada núcleo motor, a contar do mais lento, têm a condução bloqueada e não"
                            + " levam disparo nenhum à placa motora."));

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
        return example(c, "aidp-force-task-" + s.file(), "força", s,
                "ReMoto_AIDP - Force task (" + s.name() + ")",
                "Tarefa de força: o sóleo e os dois gastrocnêmios, com músculos de Hill e o tornozelo isométrico"
                        + " a 0°, são movidos por 400 axônios descendentes com 40 disparos/s nos primeiros 2 s e 250"
                        + " disparos/s depois, e os aferentes Ia dos três músculos respondem ao fuso, com o"
                        + " fusimotor dinâmico e o estático em 25 disparos/s. A força do tríceps sural e o torque do"
                        + " tornozelo nos 10 s de contração são o que o exemplo mede. " + s.text());
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
        return example(c, "aidp-position-task-" + s.file(), "posição", s,
                "ReMoto_AIDP - Position task (" + s.name() + ")",
                "Tarefa de posição: o pé, livre para girar em torno do tornozelo a partir de 5° e carregado com"
                        + " 10% do torque voluntário máximo, é controlado pelo sóleo, pelos gastrocnêmios e pelo"
                        + " tibial anterior, com músculos de Hill e os aferentes Ia dos quatro. Os 400 axônios"
                        + " descendentes têm 40 disparos/s nos primeiros 2 s e 72,5 depois, num processo gama de"
                        + " ordem 7, e o fusimotor dinâmico fica em 50 e o estático em 48 disparos/s. O ângulo do"
                        + " tornozelo e a sua variabilidade são o que o exemplo mede. " + s.text());
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
