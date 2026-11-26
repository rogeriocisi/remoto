package br.remoto.cli;

import br.remoto.core.scenario.Scenario;
import br.remoto.storage.JsonCodec;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Os cenários do trabalho computacional da disciplina IA880 (Estudo Quantitativo do Sistema
 * Neuromuscular, FEEC-UNICAMP), que o roteiro monta sobre demonstrações do site antigo. Duas delas
 * não estão no banco original que ficou guardado — eram configurações do servidor que saiu do ar —
 * e por isso estão refeitas aqui a partir do que o roteiro descreve, com os parâmetros dos modelos
 * do ReMoto em Java, sobre a configuração 24 do banco (a que tem os quatro músculos do tornozelo e
 * todas as tabelas), como nos cenários convertidos do projectFR. Não são, portanto, cópias das
 * demonstrações antigas: são o mesmo experimento, remontado.
 *
 * <p>As outras duas tarefas do roteiro usam cenários que já vêm com o simulador: a segunda, o
 * "Default - Soleus contraction" (configuração 17 do banco), e a terceira, o
 * "projectFR - H-reflex and M-wave curve".
 *
 * <p>Os arquivos ficam em scenarios/; {@code Ia880ExamplesTest} confere que eles são o que este
 * gerador produz, e os grava com {@code -Dremoto.gerar.cenarios=<pasta>}.
 */
final class Ia880Examples {

    private static final String SOURCE = "Rebuilt for the computational assignment of IA880 (Quantitative Study of"
            + " the Neuromuscular System, FEEC-UNICAMP), which uses it in task %s: the experiment is the one the"
            + " assignment describes, and the model parameters are those of ReMoto in Java, on top of configuration"
            + " 24 of the original database. The demonstration of the retired site is not in the database that was"
            + " kept, so this is a reconstruction, not a copy of it.";

    private Ia880Examples() {
    }

    static List<ScenarioEdit.Example> build(Path scenariosDir) {
        Scenario base = new JsonCodec().read(scenariosDir.resolve("remoto-24-isb-2013-stretch-reflex.json"),
                Scenario.class);
        List<ScenarioEdit.Example> examples = new ArrayList<>();
        examples.add(rampMedialGastrocnemius(base));
        examples.add(conditionedHReflex(base));
        return examples;
    }

    /**
     * A primeira tarefa: a rampa de corrente injetada no soma dos motoneurônios do gastrocnêmio
     * medial, que o roteiro chama de "Demo 2 (Ramp activation of Medial Gastrocnemius)".
     */
    private static ScenarioEdit.Example rampMedialGastrocnemius(Scenario base) {
        ScenarioEdit c = new ScenarioEdit(base);
        c.active("MG:S=300", "MG:FR=150", "MG:FF=150");
        for (String type : new String[] {"S", "FR", "FF"}) {
            c.injectedRamp("MG", type, "S", 20, 0, 6000, 4000);
        }
        c.storeSignals();
        c.pool("MG");
        return c.example("demo-2-ramp-mg", "Demo 2 - Ramp activation of Medial Gastrocnemius", 6000, 0.05,
                "Ramp activation of the medial gastrocnemius: the same current is injected into the soma of every"
                        + " motoneuron of the MG (300 S, 150 FR and 150 FF), rising from 0 to a peak of 20 nA in the"
                        + " first 4 s and staying there until the end, with no descending command, no afferent"
                        + " feedback and no nerve stimulation. The motoneurons are recruited in the order of their"
                        + " thresholds, which follow the size principle: MN S 1 is the first to fire, its firing rate"
                        + " rises while the current rises and becomes constant when the ramp reaches the peak, and the"
                        + " force and the EMG of the muscle grow with the number of units recruited and with their"
                        + " rates. With the peak at 20 nA, 377 of the 600 motoneurons fire and the force settles near"
                        + " 980 N; lowering the peak (Stimulation > Injected Current, field Peak, one row per type)"
                        + " moves the whole response down: at 8 nA, 110 motoneurons and about 74 N. The scenario stores"
                        + " the signals of two motoneurons of each type, the first and the middle one, so the injected"
                        + " current, the membrane potentials and the conductances of MN S 1 are in the results; the"
                        + " fields \"Index of MNs that will have signals stored\", in Neural Parameters >"
                        + " Motoneurons, choose other ones. " + String.format(SOURCE, "1"));
    }

    /**
     * A quarta tarefa: o reflexo H do sóleo condicionado por um estímulo no nervo fibular comum,
     * que recruta as aferentes Ia do tibial anterior e, pelos interneurônios Ia, inibe os
     * motoneurônios do sóleo.
     */
    private static ScenarioEdit.Example conditionedHReflex(Scenario base) {
        ScenarioEdit c = new ScenarioEdit(base);
        c.active("SOL:S=800", "SOL:FR=50", "SOL:FF=50", "SOL:Ia=400", "LG:Ia=160", "TA:Ia=280", "IN_ext:IaIn=350");
        c.nerve("PTN", 20, 25, 13, 0);
        c.nerve("CPN", 20, 25, 15, 0);
        c.pool("SOL");
        return c.example("ia880-h-reflex-conditioning", "IA880 - Conditioned H reflex (reciprocal inhibition)",
                100, 0.05,
                "The H reflex of the soleus conditioned through the reciprocal Ia pathway. The test pulse, 13 mA on"
                        + " the posterior tibial nerve at 20 ms, sits on the ascending limb of the recruitment curve"
                        + " of this model: on its own it gives an H reflex of about 1.4 mV, 18% of the maximal M wave"
                        + " (7.6 mV at 20.5 mA), and an M wave of about 3.7 mV, so the reflex is far from saturated"
                        + " and a change in it is easy to see. The conditioning pulse, 15 mA on the common peroneal"
                        + " nerve, passes the threshold of 191 of the 280 Ia afferents of the tibialis anterior, which"
                        + " excite the 350 reciprocal Ia interneurons, which inhibit the 900 motoneurons of the soleus"
                        + " (the synapses are the ones of the database: 80% connectivity from the afferents to the"
                        + " interneurons and 60% from them to the motoneurons). The motoneurons of the tibialis"
                        + " anterior are off, so the conditioning pulse acts only through the afferents. As it comes,"
                        + " the two pulses are simultaneous (a conditioning-test interval of 0 ms) and the reflex is"
                        + " barely touched, at 1.17 mV: the inhibitory potential arrives too late to meet the"
                        + " excitatory one. Moving the conditioning pulse earlier (Stimulation > Nerve Stimulation,"
                        + " field Start of the CPN) brings the inhibition in, and the interval of the largest"
                        + " inhibition is what the fourth task asks for. On the command line the whole curve is one"
                        + " command: remoto sweep --protocol ct --range 0:20:1. " + String.format(SOURCE, "4"));
    }
}
