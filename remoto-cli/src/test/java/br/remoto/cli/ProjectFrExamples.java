package br.remoto.cli;

import br.remoto.core.scenario.Scenario;
import br.remoto.storage.JsonCodec;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Os exemplos do projectFR (a versão em Fortran do ReMoto, de R. N. Watanabe,
 * github.com/rnwatanabe/projectFR, pasta Examples) convertidos em cenários. Cada cenário tem o
 * desenho do exemplo (o que é simulado, por quanto tempo, com que comando, estímulo, mecânica e
 * fusimotor) sobre a configuração 24 do banco original, que tem os quatro músculos do tornozelo e
 * todas as tabelas; os parâmetros dos modelos são os do ReMoto em Java (os do projectFR estão na
 * mesma estrutura, em µS em vez de nS). Quando o exemplo muda uma conectividade, uma condutância
 * ou a dinâmica de uma sinapse, o cenário muda também. O que não tem equivalente está na
 * descrição de cada um.
 *
 * <p>Os arquivos ficam em scenarios/projectfr-*.json; {@code ProjectFrExamplesTest} confere que
 * eles são o que este gerador produz, e os grava com {@code -Dremoto.gerar.cenarios=<pasta>}.
 */
final class ProjectFrExamples {

    private static final String SOURCE = "Convertido do exemplo %s do projectFR, a versão em Fortran do ReMoto"
            + " (R. N. Watanabe, github.com/rnwatanabe/projectFR): o desenho do experimento é o do exemplo, e os"
            + " parâmetros dos modelos são os do ReMoto em Java, sobre a configuração 24 do banco original.";

    private ProjectFrExamples() {
    }

    /** O cenário do exemplo, com a fonte somada à descrição. */
    private static ScenarioEdit.Example example(ScenarioEdit c, String file, String folder, String name,
            double durationMs, double stepMs, String text) {
        return c.example(file, name, durationMs, stepMs, text + " " + String.format(SOURCE, folder));
    }

    static List<ScenarioEdit.Example> build(Path scenariosDir) {
        Scenario base = new JsonCodec().read(scenariosDir.resolve("remoto-24-isb-2013-stretch-reflex.json"),
                Scenario.class);
        List<ScenarioEdit.Example> examples = new ArrayList<>();
        examples.add(antidromic(base));
        examples.add(forceVariability(base));
        examples.add(hReflexMWave(base));
        examples.add(ankleImpedance(base));
        examples.add(renshawInjectedCurrent(base));
        examples.add(isometricClosedLoop(base));
        examples.add(mnPoolRenshaw(base));
        examples.add(motorUnitPoolEmg(base));
        examples.add(descendingCommand(base));
        examples.add(nodeAxons(base));
        examples.add(hillMuscle(base));
        examples.add(muscleSpindle(base));
        examples.add(neuralTract(base));
        examples.add(neuralTractUnit(base));
        examples.add(posturalControl(base));
        examples.add(reciprocalInhibition(base));
        return examples;
    }

    // ------------------------------------------------------------------ os exemplos

    private static ScenarioEdit.Example antidromic(Scenario base) {
        ScenarioEdit c = new ScenarioEdit(base);
        c.active("SOL:S=5", "IN_ext:RC=2");
        c.synapse("IN_ext", "SOL", "IN RC-MN S", "CONNECTIVITY", 100);
        c.synapse("SOL", "IN_ext", "MN S-IN RC", "CONNECTIVITY", 100);
        c.dynamics("SOL", "IN_ext", "MN S-IN RC", "D", 200, 50);
        c.nerve("PTN", 100, 300, 20, 60);
        c.nerveSignal("PTN", "constant", 200, 300, 60);
        return example(c, "projectfr-antidromic-stimulation", "AntidromicSimulationofMNandRC",
                "projectFR - Antidromic stimulation of MN and RC", 600, 0.05,
                "Estimulação antidrômica de motoneurônios e células de Renshaw: pulsos de 20 mA no nervo tibial"
                        + " posterior, a 60 Hz de 100 a 300 ms, com a frequência somada de 60 Hz de 200 a 300 ms,"
                        + " em 5 motoneurônios S do sóleo ligados a 2 células de Renshaw, com conectividade de 100% nos"
                        + " dois sentidos e a sinapse dos motoneurônios nas células de Renshaw com depressão. Os"
                        + " disparos antidrômicos ativam as células de Renshaw, que inibem os motoneurônios. Sem"
                        + " comando descendente nem aferentes; as células de Renshaw têm o disparo espontâneo de 7 Hz.");
    }

    private static ScenarioEdit.Example forceVariability(Scenario base) {
        ScenarioEdit c = new ScenarioEdit(base);
        c.active("SOL:S=800", "SOL:FR=50", "SOL:FF=50", "MG:S=300", "MG:FR=150", "MG:FF=150",
                "LG:S=130", "LG:FR=65", "LG:FF=65", "DT:CM_ext=400");
        c.rateTract("CM_ext", 140, 4, 16000);
        return example(c, "projectfr-force-variability", "ForceVariability",
                "projectFR - Force variability", 16000, 0.05,
                "Variabilidade da força isométrica do tríceps sural: os motoneurônios do sóleo, do gastrocnêmio"
                        + " medial e do lateral recebem 400 axônios descendentes com disparos num processo gama de"
                        + " ordem 4, a 140 disparos/s, e o tornozelo fica isométrico. É uma das combinações do exemplo,"
                        + " que roda em lote 9 taxas (de 95 a 250/s, com ordens de 7 a 1) e 5 conjuntos de velocidades"
                        + " de condução, e grava a força e o torque de cada uma. Para varrer a taxa aqui: remoto sweep"
                        + " --protocol force --tract CM_ext --values 10.5,9.1,8,7.1,6.5,5.9,5.4,5,4 (o intervalo médio"
                        + " em ms; a ordem gama fica fixa). As velocidades de condução não foram convertidas.");
    }

    private static ScenarioEdit.Example hReflexMWave(Scenario base) {
        ScenarioEdit c = new ScenarioEdit(base);
        c.active("SOL:S=800", "SOL:FR=50", "SOL:FF=50", "SOL:Ia=400", "LG:Ia=160");
        c.nerve("PTN", 5, 50, 12, 5);
        return example(c, "projectfr-h-reflex-m-wave", "H-ReflexM-WaveCurve",
                "projectFR - H-reflex and M-wave curve", 50, 0.05,
                "Reflexo H e onda M do sóleo: um pulso de 1 ms no nervo tibial posterior em 5 ms excita os"
                        + " axônios motores (onda M) e os aferentes Ia do sóleo e do gastrocnêmio lateral, que excitam"
                        + " os motoneurônios (reflexo H, cerca de 30 ms depois). Com 12 mA, o pulso passa do limiar de"
                        + " parte dos aferentes e de quase nenhum axônio motor: há reflexo H e quase nenhuma onda M."
                        + " O exemplo repete a simulação para 35 intensidades, de 9 a 25 mA, e desenha a curva de"
                        + " recrutamento; aqui: remoto sweep --protocol hm --range 9:25:0.5.");
    }

    private static ScenarioEdit.Example ankleImpedance(Scenario base) {
        ScenarioEdit c = new ScenarioEdit(base);
        c.active("SOL:S=800", "SOL:FR=50", "SOL:FF=50", "MG:S=300", "MG:FR=150", "MG:FF=150",
                "LG:S=130", "LG:FR=65", "LG:FF=65", "TA:S=250", "TA:FR=50", "TA:FF=50");
        c.settings.put("cdJointModel", "imposed");
        c.settings.put("cdMuscleModel", "hill");
        c.settings.put("jointAngleSignal", series(multisine(), 0, 2000));
        return example(c, "projectfr-ankle-impedance", "ImpedanceAnkle",
                "projectFR - Ankle impedance", 2000, 0.05,
                "Impedância do tornozelo: o ângulo é imposto como uma soma de 200 senoides de 0,25 a 50 Hz,"
                        + " cada uma com 0,004 rad de amplitude e fase sorteada (aqui, com a semente 1), e os quatro"
                        + " músculos do tornozelo (sóleo, gastrocnêmios e tibial anterior), com o modelo de Hill,"
                        + " respondem; o torque contra o ângulo dá a impedância. Como no exemplo, em que o comando"
                        + " descendente e os aferentes estão desligados no programa, os músculos ficam passivos:"
                        + " para a impedância com contração, ligue o trato CM_ext em Descending Commands.");
    }

    private static ScenarioEdit.Example renshawInjectedCurrent(Scenario base) {
        ScenarioEdit c = new ScenarioEdit(base);
        c.active("IN_ext:RC=350");
        c.injectedCurrent("IN_ext", "RC", "S", 5);
        return example(c, "projectfr-renshaw-injected-current", "InjectedCurrentRenshawCellPool",
                "projectFR - Renshaw cells with injected current", 100, 0.05,
                "Um núcleo de 350 células de Renshaw sem motoneurônios, com uma corrente constante de 5 nA"
                        + " injetada no soma de cada uma, além do disparo espontâneo de 7 Hz.");
    }

    private static ScenarioEdit.Example isometricClosedLoop(Scenario base) {
        ScenarioEdit c = new ScenarioEdit(base);
        c.active("SOL:S=800", "SOL:FR=50", "SOL:FF=50", "SOL:Ia=400", "DT:CM_ext=400");
        c.gammaTract("CM_ext", 12, 10);
        c.settings.put("cdJointModel", "imposed");
        c.settings.put("cdMuscleModel", "hill");
        c.settings.put("jointAngleSignal", signal("sine", 0, 5000, Math.toDegrees(0.1), 1, 0));
        c.settings.put("gammaDynamic", 32.0);
        c.settings.put("gammaStatic", 32.0);
        return example(c, "projectfr-isometric-closed-loop", "IsometricClosedLoop",
                "projectFR - Closed loop with Ia feedback", 5000, 0.05,
                "O sóleo em malha fechada: 400 axônios descendentes (processo gama de ordem 10, intervalo médio"
                        + " de 12 ms) e os aferentes Ia do sóleo, movidos pelo fuso, excitam os motoneurônios, com o"
                        + " músculo de Hill. Apesar do nome do exemplo, o tornozelo segue uma senoide de 0,1 rad"
                        + " (5,7°) a 1 Hz, imposta, e o fusimotor dinâmico e o estático ficam em 32 disparos/s.");
    }

    private static ScenarioEdit.Example mnPoolRenshaw(Scenario base) {
        ScenarioEdit c = new ScenarioEdit(base);
        c.active("SOL:S=800", "IN_ext:RC=350", "DT:CM_ext=400");
        c.gammaTract("CM_ext", 12, 10);
        c.synapse("IN_ext", "SOL", "IN RC-MN S", "CONNECTIVITY", 4);
        c.synapse("SOL", "IN_ext", "MN S-IN RC", "CONNECTIVITY", 6);
        c.dynamics("SOL", "IN_ext", "MN S-IN RC", "D", 200, 5);
        return example(c, "projectfr-mn-pool-renshaw", "MNPoolWithRenshawCells",
                "projectFR - MN pool with Renshaw cells", 5000, 0.05,
                "Inibição recorrente: 800 motoneurônios S do sóleo, movidos por 400 axônios descendentes"
                        + " (processo gama de ordem 10, intervalo médio de 12 ms), excitam 350 células de Renshaw"
                        + " (conectividade de 6%, sinapse com depressão de 5%), que os inibem de volta (4%); as duas"
                        + " vias enfraquecem com a distância na coluna (fatores 0,01 e 0,22 da tela Miscellaneous).");
    }

    private static ScenarioEdit.Example motorUnitPoolEmg(Scenario base) {
        ScenarioEdit c = new ScenarioEdit(base);
        c.active("SOL:S=800");
        c.injectedCurrent("SOL", "S", "D", 20);
        return example(c, "projectfr-motor-unit-pool-emg", "MotorUnitPoolEMG",
                "projectFR - Motor-unit pool EMG", 5000, 0.05,
                "O EMG de um núcleo de 800 unidades motoras S do sóleo sem entrada sináptica: cada motoneurônio"
                        + " recebe uma corrente constante de 20 nA no dendrito, e os disparos dão a força e o EMG"
                        + " (modelo de Hermite-Rodriguez).");
    }

    private static ScenarioEdit.Example descendingCommand(Scenario base) {
        ScenarioEdit c = new ScenarioEdit(base);
        c.active("SOL:S=800", "SOL:FR=50", "SOL:FF=50", "DT:CM_ext=400");
        c.rateTract("CM_ext", 60, 10, 1000);
        return example(c, "projectfr-descending-command", "MotorUnitWithDescendingCommand",
                "projectFR - Motor-unit pool with descending command", 1000, 0.05,
                "Os 900 motoneurônios do sóleo movidos por 400 axônios descendentes a 60 disparos/s (processo gama"
                        + " de ordem 10): o EMG do núcleo, a força e os disparos. O exemplo desenha também o EMG de"
                        + " cada uma das 20 primeiras unidades.");
    }

    private static ScenarioEdit.Example nodeAxons(Scenario base) {
        ScenarioEdit c = new ScenarioEdit(base);
        c.active("SOL:S=3", "DT:CM_ext=400");
        c.rateTract("CM_ext", 185, 2, 100);
        c.nerve("PTN", 0, 100, 20, 10);
        c.settings.put("nerveStimulus", Map.of("PTN", Map.of("thresholdModel", "node")));
        return example(c, "projectfr-node-axons", "MotorUnitWithNodeAxonsAndDescendingCommand",
                "projectFR - Motor units with node-of-Ranvier axons", 100, 0.005,
                "Três motoneurônios S do sóleo, movidos por 400 axônios descendentes a 185 disparos/s (processo"
                        + " gama de ordem 2), com pulsos de 1 ms a 10 Hz no nervo tibial posterior, com passo de"
                        + " 0,005 ms. No exemplo, o axônio é uma cadeia de 50 nós de Ranvier; aqui, cada axônio tem um"
                        + " nó de Ranvier no ponto do estímulo (o limiar Node of Ranvier, com o modelo de Howells et"
                        + " al., 2012), e a condução até a medula e o músculo continua um atraso. O pulso é de 20 mA,"
                        + " acima dos limiares dos três axônios (de 12,4 a 18 mA); o exemplo usa 10 numa escala de"
                        + " limiares diferente.");
    }

    private static ScenarioEdit.Example hillMuscle(Scenario base) {
        ScenarioEdit c = new ScenarioEdit(base);
        c.active("SOL:S=800", "DT:CM_ext=400");
        c.gammaTract("CM_ext", 12, 10);
        c.settings.put("cdJointModel", "imposed");
        c.settings.put("cdMuscleModel", "hill");
        c.settings.put("jointAngleSignal", signal("sine", 0, 1000, Math.toDegrees(0.1), 1, 0));
        return example(c, "projectfr-hill-muscle", "MuscleHillExample",
                "projectFR - Hill muscle with imposed angle", 1000, 0.05,
                "O músculo de Hill do sóleo, ativado por 800 motoneurônios S movidos por 400 axônios descendentes"
                        + " (processo gama de ordem 10, intervalo médio de 12 ms), com o tornozelo seguindo uma"
                        + " senoide de 0,1 rad (5,7°) a 1 Hz: a força, o comprimento e a velocidade do músculo. Sem"
                        + " aferentes.");
    }

    private static ScenarioEdit.Example muscleSpindle(Scenario base) {
        ScenarioEdit c = new ScenarioEdit(base);
        // o músculo de Hill precisa de unidades motoras; sem comando, elas não disparam
        c.active("TA:S=250", "TA:FR=50", "TA:FF=50", "TA:Ia=280");
        c.settings.put("cdJointModel", "imposed");
        c.settings.put("cdMuscleModel", "hill");
        // cos(ωt) = sin(ωt + π/2): o atraso de −1/4 de período põe o tibial anterior no mais curto em t = 0
        c.settings.put("jointAngle", SPINDLE_INITIAL_DEG);
        c.settings.put("jointAngleSignal", signal("sine", 0, 20000, SPINDLE_ANGLE_DEG, 0.25, -1000));
        c.settings.put("gammaDynamic", 10.0);
        c.settings.put("gammaStatic", 10.0);
        return example(c, "projectfr-muscle-spindle", "MuscleSpindleExample",
                "projectFR - Muscle spindle", 20000, 0.05,
                "O fuso muscular do tibial anterior num estiramento senoidal lento, a 0,25 Hz, com o fusimotor"
                        + " dinâmico e o estático em 10 disparos/s: a taxa da aferência Ia contra o tempo, o"
                        + " comprimento e a velocidade. No exemplo, o fuso está sozinho e recebe o comprimento do"
                        + " fascículo (de 0,99 a 1,0 do ótimo); aqui, o comprimento vem do tornozelo: o ângulo inicial"
                        + " de 3,6° e uma senoide imposta de 0,54° dão ao fascículo do tibial anterior, com o músculo de"
                        + " Hill sem contração, de 0,99 a 1,0 do ótimo, e os 280 aferentes Ia disparam pela taxa do"
                        + " fuso.");
    }

    private static ScenarioEdit.Example neuralTract(Scenario base) {
        ScenarioEdit c = new ScenarioEdit(base);
        c.active("DT:CM_ext=400");
        c.gammaTract("CM_ext", 12, 1);
        return example(c, "projectfr-neural-tract", "NeuralTractExample",
                "projectFR - Neural tract", 10000, 0.05,
                "Um trato descendente sozinho: 400 axônios com disparos num processo gama de ordem 1 (intervalos"
                        + " exponenciais) a 83,3 disparos/s, sem motoneurônios. Os disparos e os intervalos estão nos"
                        + " resultados do trato.");
    }

    private static ScenarioEdit.Example neuralTractUnit(Scenario base) {
        ScenarioEdit c = new ScenarioEdit(base);
        c.active("DT:CM_ext=1");
        c.gammaTract("CM_ext", 4, 1);
        return example(c, "projectfr-neural-tract-unit", "NeuralTractUnitExample",
                "projectFR - Single neural tract unit", 10000, 0.05,
                "Um único axônio descendente com intervalo médio de 4 ms (250 disparos/s) num processo gama de"
                        + " ordem 1: o exemplo calcula a média, o desvio-padrão e o coeficiente de variação dos"
                        + " intervalos, que estão no histograma de intervalos dos resultados do trato.");
    }

    private static ScenarioEdit.Example posturalControl(Scenario base) {
        ScenarioEdit c = new ScenarioEdit(base);
        c.active("SOL:S=800", "SOL:FR=50", "SOL:FF=50", "MG:S=300", "MG:FR=150", "MG:FF=150",
                "LG:S=130", "LG:FR=65", "LG:FF=65", "TA:S=250", "TA:FR=50", "TA:FF=50",
                "SOL:Ia=400", "MG:Ia=160", "LG:Ia=160", "TA:Ia=280",
                "SOL:II=500", "MG:II=200", "LG:II=200", "TA:II=350",
                "SOL:Ib=300", "MG:Ib=120", "LG:Ib=120", "TA:Ib=140",
                "IN_ext:gII=350", "IN_ext:IaIn=350", "IN_ext:IbIn=350", "DT:CM_ext=400");
        c.gammaTract("CM_ext", 20, 25);
        // as sinapses do exemplo (confPosturalControl.rmto), em nS
        for (String m : new String[] {"SOL", "MG", "LG"}) {
            for (String t : new String[] {"S", "FR", "FF"}) {
                c.synapse("IN_ext", m, "IN IbIn-MN " + t, "CONNECTIVITY", 10);
                c.synapse("IN_ext", m, "IN IbIn-MN " + t, "GMAX", m.equals("SOL") && t.equals("S") ? 390 : 400);
                c.synapse("IN_ext", m, "IN gII-MN " + t, "CONNECTIVITY", 20);
                c.synapse("IN_ext", m, "IN gII-MN " + t, "GMAX", 300);
                c.synapse("IN_ext", m, "IN IaIn-MN " + t, "CONNECTIVITY", 15);
                c.synapse("IN_ext", m, "IN IaIn-MN " + t, "GMAX", 300);
                for (String pre : new String[] {"SOL", "MG", "LG"}) {
                    int con = pre.equals(m) ? 80 : pre.equals("MG") && m.equals("SOL") ? 0 : 15;
                    c.synapse(pre, m, "AF Ia-MN " + t, "CONNECTIVITY", con);
                }
            }
            c.synapse(m, "IN_ext", "AF II-IN gII", "CONNECTIVITY", 30);
            c.synapse(m, "IN_ext", "AF II-IN gII", "GMAX", 450);
            c.synapse(m, "IN_ext", "AF Ib-IN IbIn", "CONNECTIVITY", 30);
            c.synapse(m, "IN_ext", "AF Ib-IN IbIn", "GMAX", 300);
        }
        for (String t : new String[] {"S", "FR", "FF"}) {
            c.synapse("TA", "TA", "AF Ia-MN " + t, "CONNECTIVITY", 80);
        }
        c.synapse("TA", "IN_ext", "AF Ia-IN IaIn", "CONNECTIVITY", 0);
        c.settings.put("cdJointModel", "pendulum");
        c.settings.put("cdMuscleModel", "hill");
        c.settings.put("gammaDynamic", 37.1);
        c.settings.put("gammaStatic", 42.9);
        return example(c, "projectfr-postural-control", "PosturalControl",
                "projectFR - Postural control", 12000, 0.05,
                "Controle da postura ereta: o corpo é um pêndulo invertido sobre o tornozelo, controlado pelo"
                        + " sóleo, pelos gastrocnêmios e pelo tibial anterior, com músculos de Hill, aferentes Ia, II"
                        + " e Ib e interneurônios do grupo II, Ia recíprocos e Ib dos extensores, e 400 axônios"
                        + " descendentes a 50 disparos/s (processo gama de ordem 25), com as conectividades e as"
                        + " condutâncias das sinapses dos aferentes e dos interneurônios do exemplo. O fusimotor"
                        + " dinâmico fica em"
                        + " 37,1 e o estático em 42,9 disparos/s, o meio das faixas que o exemplo sorteia a cada"
                        + " tentativa (de 36 a 38,2 e de 42,4 a 43,4) para o tríceps sural; aqui, o tibial anterior"
                        + " recebe os mesmos valores (no exemplo, zero). O exemplo repete até 100 tentativas de 12 s,"
                        + " parando cada uma numa queda (ângulo acima de 12° ou abaixo de −5°), e grava o tempo até"
                        + " cair; aqui, uma tentativa. Com estes valores, o corpo fica parado no primeiro segundo e"
                        + " cai para trás (o ângulo passa de −5° em cerca de 1,3 s). Para várias: remoto sweep"
                        + " --param settings.gammaDynamic --values ... --measure \"max(articulacao-angulo)\".");
    }

    private static ScenarioEdit.Example reciprocalInhibition(Scenario base) {
        ScenarioEdit c = new ScenarioEdit(base);
        c.active("SOL:S=800", "SOL:FR=50", "SOL:FF=50", "SOL:Ia=400", "LG:Ia=160", "TA:Ia=280", "IN_ext:IaIn=350");
        c.synapse("TA", "IN_ext", "AF Ia-IN IaIn", "CONNECTIVITY", 100);
        c.synapse("TA", "IN_ext", "AF Ia-IN IaIn", "GMAX", 600);
        for (String type : new String[] {"S", "FR", "FF"}) {
            c.synapse("IN_ext", "SOL", "IN IaIn-MN " + type, "CONNECTIVITY", 20);
            c.synapse("IN_ext", "SOL", "IN IaIn-MN " + type, "GMAX", 500);
        }
        c.nerve("PTN", 2, 1100, 13, 1);
        c.nerve("CPN", 1001, 1100, 19, 1);
        return example(c, "projectfr-reciprocal-inhibition", "reciprocalInhibition",
                "projectFR - Reciprocal inhibition", 1100, 0.05,
                "Inibição recíproca: um pulso de 13 mA no nervo tibial posterior em 2 ms e outro de 19 mA no"
                        + " fibular comum em 1001 ms. Os aferentes Ia do sóleo e do gastrocnêmio lateral excitam os"
                        + " motoneurônios do sóleo; os do tibial anterior excitam 350 interneurônios Ia recíprocos,"
                        + " que inibem os motoneurônios do sóleo. O EMG do sóleo, os disparos dos interneurônios e o"
                        + " potencial de um motoneurônio mostram as duas respostas.");
    }

    // ------------------------------------------------------------------ sinais

    /** Amplitude (graus) do ângulo imposto no exemplo do fuso, a que estica o tibial anterior como no exemplo. */
    static final double SPINDLE_ANGLE_DEG = 0.54;
    /** Ângulo inicial (graus) que põe o fascículo do tibial anterior em 0,995 do ótimo, no meio da faixa do exemplo. */
    static final double SPINDLE_INITIAL_DEG = 3.6;

    /**
     * A perturbação do exemplo ImpedanceAnkle, em graus: 0,004 rad vezes a soma de 200 senoides de
     * 0,25·j Hz (j de 1 a 200) com fases sorteadas, de 0 a 2000 ms, um ponto a cada 0,5 ms.
     */
    static double[] multisine() {
        Random random = new Random(1);
        double[] phases = new double[200];
        for (int j = 0; j < phases.length; j++) {
            phases[j] = random.nextDouble();
        }
        int n = 4001;
        double[] points = new double[2 * n];
        for (int i = 0; i < n; i++) {
            double t = i * 0.5;
            double sum = 0;
            for (int j = 1; j <= 200; j++) {
                sum += Math.sin(2 * Math.PI * (50.0 / 200) * j * t / 1000 + 2 * Math.PI * phases[j - 1]);
            }
            points[2 * i] = t;
            points[2 * i + 1] = Math.round(Math.toDegrees(0.004 * sum) * 1e6) / 1e6;
        }
        return points;
    }

    private static Map<String, Object> signal(String shape, double tini, double tfin, double amp, double freq,
            double delay) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("shape", shape);
        s.put("tini", tini);
        s.put("tfin", tfin);
        s.put("amp", amp);
        s.put("freq", freq);
        s.put("width", 0.0);
        s.put("delay", delay);
        s.put("modFreq", 0.0);
        s.put("modFactor", 0.0);
        return s;
    }

    private static Map<String, Object> series(double[] flat, double tini, double tfin) {
        Map<String, Object> s = signal("series", tini, tfin, 0, 0, 0);
        List<List<Double>> points = new ArrayList<>();
        for (int i = 0; i < flat.length; i += 2) {
            points.add(List.of(flat[i], flat[i + 1]));
        }
        s.put("points", points);
        return s;
    }
}
