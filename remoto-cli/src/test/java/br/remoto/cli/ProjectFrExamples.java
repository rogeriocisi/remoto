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

    private static final String SOURCE = "Converted from the %s example of projectFR, the Fortran version of ReMoto"
            + " (R. N. Watanabe, github.com/rnwatanabe/projectFR): the design of the experiment is that of the"
            + " example, and the model parameters are those of ReMoto in Java, on top of configuration 24 of the"
            + " original database.";

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
                "Antidromic stimulation of motoneurons and Renshaw cells: 20 mA pulses on the posterior tibial"
                        + " nerve, at 60 Hz from 100 to 300 ms, with 60 Hz added to the frequency from 200 to 300 ms,"
                        + " on 5 S motoneurons of the soleus connected to 2 Renshaw cells, with 100% connectivity both"
                        + " ways and the synapse of the motoneurons on the Renshaw cells with depression. The"
                        + " antidromic spikes activate the Renshaw cells, which inhibit the motoneurons. No descending"
                        + " command and no afferents; the Renshaw cells have the spontaneous firing of 7 Hz.");
    }

    private static ScenarioEdit.Example forceVariability(Scenario base) {
        ScenarioEdit c = new ScenarioEdit(base);
        c.active("SOL:S=800", "SOL:FR=50", "SOL:FF=50", "MG:S=300", "MG:FR=150", "MG:FF=150",
                "LG:S=130", "LG:FR=65", "LG:FF=65", "DT:CM_ext=400");
        c.rateTract("CM_ext", 140, 4, 16000);
        return example(c, "projectfr-force-variability", "ForceVariability",
                "projectFR - Force variability", 16000, 0.05,
                "Variability of the isometric force of the triceps surae: the motoneurons of the soleus, of the"
                        + " medial gastrocnemius and of the lateral one receive 400 descending axons firing in a gamma"
                        + " process of order 4, at 140 spikes/s, and the ankle stays isometric. It is one of the"
                        + " combinations of the example, which runs 9 rates in a batch (from 95 to 250/s, with orders"
                        + " from 7 to 1) and 5 sets of conduction velocities, and records the force and the torque of"
                        + " each. To sweep the rate here: remoto sweep --protocol force --tract CM_ext --values"
                        + " 10.5,9.1,8,7.1,6.5,5.9,5.4,5,4 (the mean interval in ms; the gamma order stays fixed). The"
                        + " conduction velocities were not converted.");
    }

    private static ScenarioEdit.Example hReflexMWave(Scenario base) {
        ScenarioEdit c = new ScenarioEdit(base);
        c.active("SOL:S=800", "SOL:FR=50", "SOL:FF=50", "SOL:Ia=400", "LG:Ia=160");
        c.nerve("PTN", 5, 50, 12, 5);
        return example(c, "projectfr-h-reflex-m-wave", "H-ReflexM-WaveCurve",
                "projectFR - H-reflex and M-wave curve", 50, 0.05,
                "H reflex and M wave of the soleus: a 1 ms pulse on the posterior tibial nerve at 5 ms excites the"
                        + " motor axons (M wave) and the Ia afferents of the soleus and of the lateral gastrocnemius,"
                        + " which excite the motoneurons (H reflex, about 30 ms later). At 12 mA, the pulse passes the"
                        + " threshold of part of the afferents and of almost no motor axon: there is an H reflex and"
                        + " almost no M wave. The example repeats the simulation for 35 intensities, from 9 to 25 mA,"
                        + " and draws the recruitment curve; here: remoto sweep --protocol hm --range 9:25:0.5.");
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
                "Ankle impedance: the angle is imposed as a sum of 200 sinusoids from 0.25 to 50 Hz, each with an"
                        + " amplitude of 0.004 rad and a drawn phase (here, with seed 1), and the four ankle muscles"
                        + " (soleus, gastrocnemii and tibialis anterior), with the Hill model, respond; the torque"
                        + " against the angle gives the impedance. As in the example, where the descending command and"
                        + " the afferents are turned off in the program, the muscles stay passive: for the impedance"
                        + " with contraction, turn the CM_ext tract on in Descending Commands.");
    }

    private static ScenarioEdit.Example renshawInjectedCurrent(Scenario base) {
        ScenarioEdit c = new ScenarioEdit(base);
        c.active("IN_ext:RC=350");
        c.injectedCurrent("IN_ext", "RC", "S", 5);
        return example(c, "projectfr-renshaw-injected-current", "InjectedCurrentRenshawCellPool",
                "projectFR - Renshaw cells with injected current", 100, 0.05,
                "A pool of 350 Renshaw cells without motoneurons, with a constant current of 5 nA injected into the"
                        + " soma of each one, besides the spontaneous firing of 7 Hz.");
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
                "The soleus in closed loop: 400 descending axons (gamma process of order 10, mean interval of"
                        + " 12 ms) and the Ia afferents of the soleus, driven by the spindle, excite the motoneurons,"
                        + " with the Hill muscle. Despite the name of the example, the ankle follows an imposed"
                        + " sinusoid of 0.1 rad (5.7°) at 1 Hz, and the dynamic and static fusimotor drives stay at"
                        + " 32 spikes/s.");
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
                "Recurrent inhibition: 800 S motoneurons of the soleus, driven by 400 descending axons (gamma"
                        + " process of order 10, mean interval of 12 ms), excite 350 Renshaw cells (6% connectivity,"
                        + " synapse with 5% depression), which inhibit them back (4%); both pathways weaken with the"
                        + " distance along the column (factors 0.01 and 0.22 of the Miscellaneous screen).");
    }

    private static ScenarioEdit.Example motorUnitPoolEmg(Scenario base) {
        ScenarioEdit c = new ScenarioEdit(base);
        c.active("SOL:S=800");
        c.injectedCurrent("SOL", "S", "D", 20);
        return example(c, "projectfr-motor-unit-pool-emg", "MotorUnitPoolEMG",
                "projectFR - Motor-unit pool EMG", 5000, 0.05,
                "The EMG of a pool of 800 S motor units of the soleus with no synaptic input: each motoneuron"
                        + " receives a constant current of 20 nA in the dendrite, and the spikes give the force and"
                        + " the EMG (Hermite-Rodriguez model).");
    }

    private static ScenarioEdit.Example descendingCommand(Scenario base) {
        ScenarioEdit c = new ScenarioEdit(base);
        c.active("SOL:S=800", "SOL:FR=50", "SOL:FF=50", "DT:CM_ext=400");
        c.rateTract("CM_ext", 60, 10, 1000);
        return example(c, "projectfr-descending-command", "MotorUnitWithDescendingCommand",
                "projectFR - Motor-unit pool with descending command", 1000, 0.05,
                "The 900 motoneurons of the soleus driven by 400 descending axons at 60 spikes/s (gamma process of"
                        + " order 10): the EMG of the pool, the force and the spikes. The example also draws the EMG"
                        + " of each of the first 20 units.");
    }

    private static ScenarioEdit.Example nodeAxons(Scenario base) {
        ScenarioEdit c = new ScenarioEdit(base);
        c.active("SOL:S=3", "DT:CM_ext=400");
        c.rateTract("CM_ext", 185, 2, 100);
        c.nerve("PTN", 0, 100, 20, 10);
        c.settings.put("nerveStimulus", Map.of("PTN", Map.of("thresholdModel", "node")));
        return example(c, "projectfr-node-axons", "MotorUnitWithNodeAxonsAndDescendingCommand",
                "projectFR - Motor units with node-of-Ranvier axons", 100, 0.005,
                "Three S motoneurons of the soleus, driven by 400 descending axons at 185 spikes/s (gamma process"
                        + " of order 2), with 1 ms pulses at 10 Hz on the posterior tibial nerve, with a step of"
                        + " 0.005 ms. In the example, the axon is a chain of 50 nodes of Ranvier; here, each axon has"
                        + " one node of Ranvier at the stimulus point (the Node of Ranvier threshold, with the model"
                        + " of Howells et al., 2012), and conduction to the spinal cord and to the muscle is still a"
                        + " delay. The pulse is 20 mA, above the thresholds of the three axons (from 12.4 to 18 mA);"
                        + " the example uses 10 on a different threshold scale.");
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
                "The Hill muscle of the soleus, activated by 800 S motoneurons driven by 400 descending axons"
                        + " (gamma process of order 10, mean interval of 12 ms), with the ankle following a sinusoid"
                        + " of 0.1 rad (5.7°) at 1 Hz: the force, the length and the velocity of the muscle. No"
                        + " afferents.");
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
                "The muscle spindle of the tibialis anterior on a slow sinusoidal stretch, at 0.25 Hz, with the"
                        + " dynamic and static fusimotor drives at 10 spikes/s: the rate of the Ia afference against"
                        + " time, the length and the velocity. In the example, the spindle is on its own and receives"
                        + " the length of the fascicle (from 0.99 to 1.0 of the optimum); here, the length comes from"
                        + " the ankle: the initial angle of 3.6° and an imposed sinusoid of 0.54° give the fascicle of"
                        + " the tibialis anterior, with the Hill muscle and no contraction, from 0.99 to 1.0 of the"
                        + " optimum, and the 280 Ia afferents fire at the rate of the spindle.");
    }

    private static ScenarioEdit.Example neuralTract(Scenario base) {
        ScenarioEdit c = new ScenarioEdit(base);
        c.active("DT:CM_ext=400");
        c.gammaTract("CM_ext", 12, 1);
        return example(c, "projectfr-neural-tract", "NeuralTractExample",
                "projectFR - Neural tract", 10000, 0.05,
                "A descending tract on its own: 400 axons firing in a gamma process of order 1 (exponential"
                        + " intervals) at 83.3 spikes/s, with no motoneurons. The spikes and the intervals are in the"
                        + " results of the tract.");
    }

    private static ScenarioEdit.Example neuralTractUnit(Scenario base) {
        ScenarioEdit c = new ScenarioEdit(base);
        c.active("DT:CM_ext=1");
        c.gammaTract("CM_ext", 4, 1);
        return example(c, "projectfr-neural-tract-unit", "NeuralTractUnitExample",
                "projectFR - Single neural tract unit", 10000, 0.05,
                "A single descending axon with a mean interval of 4 ms (250 spikes/s) in a gamma process of"
                        + " order 1: the example computes the mean, the standard deviation and the coefficient of"
                        + " variation of the intervals, which are in the interval histogram of the tract's results.");
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
                "Control of upright stance: the body is an inverted pendulum over the ankle, controlled by the"
                        + " soleus, the gastrocnemii and the tibialis anterior, with Hill muscles, Ia, II and Ib"
                        + " afferents and group II, reciprocal Ia and Ib interneurons of the extensors, and 400"
                        + " descending axons at 50 spikes/s (gamma process of order 25), with the connectivities and"
                        + " the conductances of the synapses of the afferents and of the interneurons of the example."
                        + " The dynamic fusimotor drive stays at 37.1 and the static one at 42.9 spikes/s, the middle"
                        + " of the ranges the example draws from at every trial (from 36 to 38.2 and from 42.4 to"
                        + " 43.4) for the triceps surae; here, the tibialis anterior gets the same values (in the"
                        + " example, zero). The example repeats up to 100 trials of 12 s, stopping each one at a fall"
                        + " (angle above 12° or below −5°), and records the time to fall; here, one trial. With these"
                        + " values, the body stays still in the first second and falls backwards (the angle passes"
                        + " −5° at about 1.3 s). For several: remoto sweep --param settings.gammaDynamic --values ..."
                        + " --measure \"max(articulacao-angulo)\".");
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
                "Reciprocal inhibition: a 13 mA pulse on the posterior tibial nerve at 2 ms and a 19 mA one on the"
                        + " common peroneal nerve at 1001 ms. The Ia afferents of the soleus and of the lateral"
                        + " gastrocnemius excite the motoneurons of the soleus; those of the tibialis anterior excite"
                        + " 350 reciprocal Ia interneurons, which inhibit the motoneurons of the soleus. The EMG of"
                        + " the soleus, the spikes of the interneurons and the potential of one motoneuron show the"
                        + " two responses.");
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
