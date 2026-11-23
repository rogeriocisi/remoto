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
        Scenario active = ScenarioParameter.parse("NERVE:CD_NERVE=" + nerve + ":ACTIVE").apply(scenario, 1);
        Object start = ScenarioParameter.parse("NERVE:CD_NERVE=" + nerve + ":START").read(active);
        double t0 = start instanceof Number n ? n.doubleValue() : 0;
        // sem janela de estímulo (STOP antes do START), um pulso só, no START
        ScenarioParameter stop = ScenarioParameter.parse("NERVE:CD_NERVE=" + nerve + ":STOP");
        if (!(stop.read(active) instanceof Number s && s.doubleValue() > t0)) {
            active = stop.apply(active, t0 + 5);
        }
        if (active.durationMs() > t0 + h[1] + 10) {
            active = active.withDuration(t0 + h[1] + 10);
        }
        String emg = "emg-" + muscle;
        String spikes = "disparos-" + muscle;
        List<Measure> measures = List.of(
                new Measure("M_p2p[mV]", Measure.Function.P2P, emg, t0 + m[0], t0 + m[1]),
                new Measure("H_p2p[mV]", Measure.Function.P2P, emg, t0 + h[0], t0 + h[1]),
                new Measure("M_motoneuronios", Measure.Function.UNITS, spikes, t0 + m[0], t0 + m[1]),
                new Measure("H_motoneuronios", Measure.Function.UNITS, spikes, t0 + h[0], t0 + h[1]));
        Sweep sweep = new Sweep(ScenarioParameter.parse("NERVE:CD_NERVE=" + nerve + ":PEAK"), intensities, measures);
        return new Prepared(active, sweep);
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
