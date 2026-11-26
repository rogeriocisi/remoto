package br.remoto.core.protocol;

import br.remoto.core.scenario.Scenario;

import java.util.List;

/**
 * Os protocolos em lote prontos, sobre um cenário que já tem o que eles variam.
 */
public final class Protocols {

    /** Um protocolo pronto para rodar: o cenário ajustado e o lote. */
    public record Prepared(Scenario scenario, Sweep sweep) {
    }

    /** Janelas padrão (ms depois do início do estímulo) da onda M e do reflexo H. */
    public static final double[] M_WINDOW = {2, 20};
    public static final double[] H_WINDOW = {20, 50};

    private Protocols() {
    }

    /**
     * A curva de recrutamento do reflexo H e da onda M: a intensidade (mA) do estímulo de um nervo
     * varia, e cada simulação mede o pico a pico do EMG do músculo nas janelas da onda M e do
     * reflexo H, e quantos motoneurônios dispararam em cada uma. O nervo é ativado, e o primeiro
     * pulso é o do início do estímulo (o START da tabela NERVE); se o nervo não tem janela de
     * estímulo (STOP até o START), ela passa a ter 5 ms: um pulso só, na frequência 0. A simulação
     * vai até o fim da janela do H, mais 10 ms, se o cenário for mais longo.
     *
     * @param mWindow janela da onda M (ms depois do início do estímulo); null para {@link #M_WINDOW}
     * @param hWindow janela do reflexo H; null para {@link #H_WINDOW}
     */
    public static Prepared hmCurve(Scenario scenario, String nerve, String muscle, List<Double> intensities,
            double[] mWindow, double[] hWindow) {
        double[] m = mWindow != null ? mWindow : M_WINDOW;
        double[] h = hWindow != null ? hWindow : H_WINDOW;
        Scenario active = singlePulse(scenario, nerve);
        double t0 = start(active, nerve);
        if (active.durationMs() > t0 + h[1] + 10) {
            active = active.withDuration(t0 + h[1] + 10);
        }
        Sweep sweep = new Sweep(nerve(nerve, "PEAK"), intensities, responseMeasures(muscle, t0, m, h));
        return new Prepared(active, sweep);
    }

    /**
     * O condicionamento do reflexo H: o pulso teste fica onde está, num nervo, e o pulso
     * condicionante, no outro, muda de lugar — o que varia é o intervalo condicionamento-teste
     * (CT, em ms, contado para trás a partir do pulso teste, como na literatura: 3 ms é o
     * condicionante 3 ms antes do teste). Cada simulação mede o pico a pico do EMG do músculo nas
     * janelas da onda M e do reflexo H do pulso teste, e quantos motoneurônios dispararam em cada
     * uma; a tabela do lote é a curva da inibição (ou da facilitação) do reflexo H pelo intervalo,
     * e a coluna do parâmetro é o próprio CT.
     *
     * <p>É o protocolo da inibição recíproca Ia: o teste no nervo tibial posterior e o
     * condicionante no fibular comum, que recruta as aferentes Ia do tibial anterior e, por meio
     * dos interneurônios Ia, inibe os motoneurônios do sóleo. Os dois nervos ficam ativos, cada
     * um com um pulso só (frequência 0).
     *
     * @param intervals os intervalos CT (ms); 0 é o condicionante junto com o teste, e um valor
     *     negativo, depois dele
     * @param condPeak a intensidade (mA) do pulso condicionante; null mantém a do cenário
     * @param mWindow janela da onda M (ms depois do pulso teste); null para {@link #M_WINDOW}
     * @param hWindow janela do reflexo H; null para {@link #H_WINDOW}
     */
    public static Prepared conditioningTest(Scenario scenario, String testNerve, String condNerve, String muscle,
            Double condPeak, List<Double> intervals, double[] mWindow, double[] hWindow) {
        if (testNerve.equals(condNerve)) {
            throw new IllegalArgumentException("o nervo do teste e o do condicionamento são o mesmo: " + testNerve);
        }
        double[] m = mWindow != null ? mWindow : M_WINDOW;
        double[] h = hWindow != null ? hWindow : H_WINDOW;
        Scenario active = singlePulse(scenario, testNerve);
        double t0 = start(active, testNerve);
        double longest = intervals.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double shortest = intervals.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        if (t0 - longest < 0) {
            throw new IllegalArgumentException("com o intervalo de " + longest + " ms o pulso condicionante cairia"
                    + " antes do início da simulação: adie o pulso teste do nervo " + testNerve + ", que está em "
                    + t0 + " ms");
        }
        // o condicionante: ativo, um pulso só no START, que é o que varia, e a janela de estímulo
        // acaba depois do último pulso possível, para o intervalo poder ser negativo (condicionante
        // depois do teste)
        active = nerve(condNerve, "ACTIVE").apply(active, 1);
        active = nerve(condNerve, "FREQUENCY").apply(active, 0);
        active = nerve(condNerve, "STOP").apply(active, t0 + Math.max(5, 1 - shortest));
        if (condPeak != null) {
            active = nerve(condNerve, "PEAK").apply(active, condPeak);
        }
        if (active.durationMs() > t0 + h[1] + 10) {
            active = active.withDuration(t0 + h[1] + 10);
        }
        ScenarioParameter ct = ScenarioParameter.mapped("CT[ms]", nerve(condNerve, "START"), interval -> t0 - interval);
        Sweep sweep = new Sweep(ct, intervals, responseMeasures(muscle, t0, m, h));
        return new Prepared(active, sweep);
    }

    /** As medidas da resposta a um pulso em {@code t0}: a onda M e o reflexo H. */
    private static List<Measure> responseMeasures(String muscle, double t0, double[] m, double[] h) {
        String emg = "emg-" + muscle;
        String spikes = "disparos-" + muscle;
        return List.of(
                new Measure("M_p2p[mV]", Measure.Function.P2P, emg, t0 + m[0], t0 + m[1]),
                new Measure("H_p2p[mV]", Measure.Function.P2P, emg, t0 + h[0], t0 + h[1]),
                new Measure("M_motoneuronios", Measure.Function.UNITS, spikes, t0 + m[0], t0 + m[1]),
                new Measure("H_motoneuronios", Measure.Function.UNITS, spikes, t0 + h[0], t0 + h[1]));
    }

    /** Uma coluna da linha de um nervo na tabela NERVE. */
    private static ScenarioParameter nerve(String nerve, String column) {
        return ScenarioParameter.parse("NERVE:CD_NERVE=" + nerve + ":" + column);
    }

    /** O nervo ativo e com janela de estímulo: sem ela (STOP até o START), passa a ter 5 ms. */
    private static Scenario singlePulse(Scenario scenario, String nerve) {
        Scenario active = nerve(nerve, "ACTIVE").apply(scenario, 1);
        double t0 = start(active, nerve);
        ScenarioParameter stop = nerve(nerve, "STOP");
        if (!(stop.read(active) instanceof Number s && s.doubleValue() > t0)) {
            active = stop.apply(active, t0 + 5);
        }
        return active;
    }

    /** O instante do primeiro pulso do nervo (ms). */
    private static double start(Scenario scenario, String nerve) {
        return nerve(nerve, "START").read(scenario) instanceof Number n ? n.doubleValue() : 0;
    }

    /**
     * A variabilidade da força: o intervalo médio (ms) dos axônios de um trato descendente varia,
     * e cada simulação mede a média, o desvio-padrão e o coeficiente de variação da força do
     * músculo numa janela, e quantos motoneurônios dispararam nela.
     *
     * @param from início da janela (ms); null para a metade da simulação (depois do transitório)
     * @param to fim da janela; null para o fim da simulação
     */
    public static Prepared forceVariability(Scenario scenario, String tract, String muscle, List<Double> isiMeans,
            Double from, Double to) {
        double a = from != null ? from : scenario.durationMs() / 2;
        double b = to != null ? to : scenario.durationMs();
        String force = "forca-" + muscle;
        List<Measure> measures = List.of(
                new Measure("forca_media", Measure.Function.MEAN, force, a, b),
                new Measure("forca_dp", Measure.Function.SD, force, a, b),
                new Measure("forca_cv", Measure.Function.CV, force, a, b),
                new Measure("motoneuronios", Measure.Function.UNITS, "disparos-" + muscle, a, b));
        Sweep sweep = new Sweep(ScenarioParameter.parse("NEURON_TYPE:CD_NUCLEUS=DT,CD_NEURON_TYPE=" + tract + ":MEAN"),
                isiMeans, measures);
        return new Prepared(scenario, sweep);
    }
}
