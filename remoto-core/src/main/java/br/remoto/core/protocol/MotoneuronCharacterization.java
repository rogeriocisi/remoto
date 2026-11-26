package br.remoto.core.protocol;

import br.remoto.core.model.neuron.MotoneuronMembraneModel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Supplier;

/**
 * As medidas eletrofisiológicas de um motoneurônio isolado, sem rede em volta: resistência de
 * entrada, constante de tempo de membrana, reobase, curva f–I e a AHP. São as grandezas com que
 * um modelo de motoneurônio é caracterizado na literatura, e é por elas que duas implementações
 * do mesmo modelo se comparam.
 *
 * <p>Funciona com qualquer membrana de motoneurônio, de pulso ou de cinética contínua, porque
 * fala pela interface: a mesma régua serve para as duas, que é o ponto.
 *
 * <p><b>Unidades.</b> Correntes em nA, potenciais em mV a partir do repouso, tempos em ms. Daí a
 * resistência sair em MΩ e as frequências em disparos por segundo.
 *
 * <p><b>O protocolo não está fixado aqui.</b> Cada medida recebe os seus parâmetros, e os valores
 * padrão são só um ponto de partida razoável. A comparação entre implementações depende de os
 * dois lados usarem os mesmos, e é isso que o protocolo acordado define.
 */
public final class MotoneuronCharacterization {

    /** Corrente de teste subliminar para a resistência de entrada e a constante de tempo (nA). */
    public static final double PROBE_CURRENT = 0.5;
    /** Tempo de acomodação antes de ler um estado estacionário (ms). */
    public static final double SETTLE_MS = 500;
    /** Duração padrão de um degrau de corrente na curva f–I (ms). */
    public static final double STEP_MS = 1000;
    /** Trecho inicial do degrau descartado antes de contar disparos (ms). */
    public static final double TRANSIENT_MS = 200;

    private final Supplier<MotoneuronMembraneModel> membranes;
    private final double stepMs;

    /**
     * @param membranes fábrica de membranas já configuradas: cada medida começa com uma nova,
     *                  porque medir muda o estado
     * @param stepMs    o passo de integração, o mesmo que a simulação usa
     */
    public MotoneuronCharacterization(Supplier<MotoneuronMembraneModel> membranes, double stepMs) {
        this.membranes = membranes;
        this.stepMs = stepMs;
    }

    /** Um ponto da curva f–I. */
    public record Point(double currentNa, double rateHz) {
    }

    /** O conjunto das medidas de um motoneurônio. */
    public record Result(double inputResistanceMOhm, double timeConstantMs, double rheobaseNa,
                         double ahpAmplitudeMv, double ahpDurationMs, List<Point> fiCurve) {
    }

    /**
     * Resistência de entrada: a razão entre o deslocamento do potencial do soma no estado
     * estacionário e a corrente que o produziu, com a corrente abaixo do limiar.
     */
    public double inputResistance() {
        double v = steadyVoltage(PROBE_CURRENT);
        return v / PROBE_CURRENT;
    }

    /**
     * Constante de tempo da membrana: o tempo que o soma leva para percorrer 63,2% do caminho
     * até o estado estacionário, sob um degrau de corrente subliminar.
     */
    public double timeConstant() {
        double target = steadyVoltage(PROBE_CURRENT) * (1 - 1 / Math.E);
        MotoneuronMembraneModel m = fresh();
        m.setInjection(t -> PROBE_CURRENT, null);
        for (int i = 0; i * stepMs <= SETTLE_MS; i++) {
            double t = i * stepMs;
            m.stepDendrite(t);
            m.stepSoma(t);
            if (m.vs() >= target) {
                return t;
            }
        }
        return Double.NaN;
    }

    /**
     * Reobase: a menor corrente sustentada que ainda produz disparo, por bisseção. {@code maxNa}
     * limita a busca; sem disparo até lá, devolve NaN.
     */
    public double rheobase(double maxNa, double toleranceNa) {
        if (countSpikes(maxNa, STEP_MS) == 0) {
            return Double.NaN;
        }
        double low = 0;
        double high = maxNa;
        while (high - low > toleranceNa) {
            double mid = (low + high) / 2;
            if (countSpikes(mid, STEP_MS) > 0) {
                high = mid;
            } else {
                low = mid;
            }
        }
        return high;
    }

    /**
     * Frequência de disparo estacionária sob um degrau de corrente, descartando o transiente
     * inicial. Zero quando não há dois disparos no trecho medido.
     */
    public double firingRate(double currentNa) {
        List<Double> spikes = spikeTimes(currentNa, STEP_MS);
        List<Double> steady = spikes.stream().filter(t -> t >= TRANSIENT_MS).toList();
        if (steady.size() < 2) {
            return 0;
        }
        double span = steady.get(steady.size() - 1) - steady.get(0);
        return span <= 0 ? 0 : 1000.0 * (steady.size() - 1) / span;
    }

    /** A curva f–I: a frequência estacionária em cada corrente da lista. */
    public List<Point> fiCurve(double fromNa, double toNa, double byNa) {
        List<Point> points = new ArrayList<>();
        for (double i = fromNa; i <= toNa + 1e-9; i += byNa) {
            points.add(new Point(i, firingRate(i)));
        }
        return points;
    }

    /**
     * A AHP de um disparo isolado, provocado por um pulso curto de corrente: a amplitude é o
     * quanto o soma passa abaixo do repouso depois do disparo, e a duração é o tempo do disparo
     * até o potencial voltar a {@code 10%} dessa amplitude.
     *
     * @return {@code {amplitude (mV), duração (ms)}}, ou NaN quando o pulso não provoca disparo
     */
    public double[] afterHyperpolarization(double pulseNa, double pulseMs, double windowMs) {
        MotoneuronMembraneModel m = fresh();
        m.setInjection(t -> t <= pulseMs ? pulseNa : 0, null);

        double spike = Double.NaN;
        double minV = 0;
        double back = Double.NaN;
        for (int i = 0; i * stepMs <= windowMs; i++) {
            double t = i * stepMs;
            m.stepDendrite(t);
            if (m.stepSoma(t)) {
                m.fireSoma(t);
                if (Double.isNaN(spike)) {
                    spike = t;
                }
            }
            if (!Double.isNaN(spike) && t > spike) {
                if (m.vs() < minV) {
                    minV = m.vs();
                    back = Double.NaN;
                } else if (Double.isNaN(back) && minV < 0 && m.vs() >= 0.1 * minV) {
                    back = t;
                }
            }
        }
        if (Double.isNaN(spike)) {
            return new double[] {Double.NaN, Double.NaN};
        }
        return new double[] {-minV, Double.isNaN(back) ? Double.NaN : back - spike};
    }

    /** Todas as medidas de uma vez, com os parâmetros padrão para a curva f–I. */
    public Result all(double maxRheobaseNa, double fiFromNa, double fiToNa, double fiByNa) {
        double[] ahp = afterHyperpolarization(20, 1, 200);
        return new Result(inputResistance(), timeConstant(), rheobase(maxRheobaseNa, 0.01),
                ahp[0], ahp[1], fiCurve(fiFromNa, fiToNa, fiByNa));
    }

    // --- acionamento da membrana ----------------------------------------------------------

    /** Potencial do soma depois de acomodar sob uma corrente constante. */
    private double steadyVoltage(double currentNa) {
        MotoneuronMembraneModel m = drive(fresh(), t -> currentNa, SETTLE_MS, null);
        return m.vs();
    }

    private int countSpikes(double currentNa, double durationMs) {
        return spikeTimes(currentNa, durationMs).size();
    }

    private List<Double> spikeTimes(double currentNa, double durationMs) {
        List<Double> spikes = new ArrayList<>();
        drive(fresh(), t -> currentNa, durationMs, spikes);
        return spikes;
    }

    /**
     * Integra a membrana, como a simulação faz: o dendrito primeiro, depois o soma, e o disparo
     * registrado por quem aciona.
     */
    private MotoneuronMembraneModel drive(MotoneuronMembraneModel m, DoubleUnaryOperator injection,
                                          double durationMs, List<Double> spikes) {
        m.setInjection(injection, null);
        for (int i = 0; i * stepMs <= durationMs; i++) {
            double t = i * stepMs;
            m.stepDendrite(t);
            if (m.stepSoma(t)) {
                m.fireSoma(t);
                if (spikes != null) {
                    spikes.add(t);
                }
            }
            m.checkPersistentInwardCurrent(t);
        }
        return m;
    }

    private MotoneuronMembraneModel fresh() {
        MotoneuronMembraneModel m = membranes.get();
        m.reset();
        return m;
    }
}
