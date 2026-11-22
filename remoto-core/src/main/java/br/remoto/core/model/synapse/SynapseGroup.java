package br.remoto.core.model.synapse;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * As sinapses de um tipo (excitatórias ou inibitórias) num compartimento de um neurônio, somadas
 * pela agregação de Lytton (1996) sobre a cinética de Destexhe et al. (1994).
 *
 * <p>Cada disparo pré-sináptico chega depois do atraso da sinapse e libera transmissor por um
 * pulso de duração fixa. Durante o pulso, a fração ligada sobe para r∞ com constante τon; depois
 * cai com τoff. Em vez de integrar uma equação por sinapse, o grupo guarda duas somas, a das
 * sinapses em pulso (Ron) e a das demais (Roff), cada uma com solução fechada desde o último evento.
 *
 * <p>A aritmética é a do {@code MultSynapticConductance} do ReMoto original, operação por operação,
 * para que o resultado seja o mesmo bit a bit.
 */
public final class SynapseGroup {

    /** Folga para comparar instantes de eventos com o tempo da simulação (ms). */
    private static final double T_TOLERANCE = 0.001;

    private final double e;
    private final double pulseDuration;
    private final double rInf;
    private final double tauOn;
    private final double tauOff;
    private final double expFinish;

    private boolean active;
    private final Map<String, Synapse> synapses = new HashMap<>();
    private final ArrayDeque<Synapse> pulsing = new ArrayDeque<>();
    private final ArrayDeque<Received> received = new ArrayDeque<>();
    private int spikesReceived;
    private double gMaxTot;

    private double non;
    private double sumOn;
    private double sumOff;
    private double ron;
    private double roff;
    private double t0;

    // valor no último instante pedido, guardado só quando aquela chamada não começou nem terminou
    // nenhum pulso: os estágios k2 e k3 do Runge-Kutta pedem o mesmo instante duas vezes seguidas
    private double cachedT = Double.NaN;
    private double cachedValue;

    private record Received(double time, Synapse synapse) {
    }

    /**
     * @param e             potencial de reversão (mV)
     * @param pulseDuration duração do pulso de transmissor (ms)
     * @param tmax          concentração de transmissor no pulso (mM)
     * @param alpha         taxa de ligação (1/(mM·ms))
     * @param beta          taxa de desligamento (1/ms)
     * @param active        se o grupo já começa ativo; senão, ativa no primeiro disparo recebido
     */
    public SynapseGroup(double e, double pulseDuration, double tmax, double alpha, double beta, boolean active) {
        this.e = e;
        this.pulseDuration = pulseDuration;
        this.rInf = (alpha * tmax) / (alpha * tmax + beta);
        this.tauOn = 1.0 / (alpha * tmax + beta);
        this.tauOff = 1.0 / beta;
        this.expFinish = Math.exp(-pulseDuration / tauOn);
        this.active = active;
        reset();
    }

    /**
     * Uma sinapse do neurônio pré-sináptico identificado por {@code key}. Se a chave já existe, a
     * sinapse nova a substitui, mas a condutância das duas continua somada no total, como no
     * original.
     *
     * @param gmax condutância máxima (µS)
     */
    public Synapse add(String key, double gmax, double delay, SynapticDynamics dynamics) {
        gMaxTot += gmax;
        Synapse s = new Synapse(gmax, delay, dynamics);
        synapses.put(key, s);
        return s;
    }

    /** A sinapse que os disparos do neurônio pré-sináptico {@code key} ligam, ou null. */
    public Synapse synapse(String key) {
        return synapses.get(key);
    }

    /** Um disparo pré-sináptico no instante {@code t}; chega depois do atraso da sinapse. */
    public void receiveSpike(double t, Synapse synapse) {
        active = true;
        spikesReceived++;
        received.addLast(new Received(t + synapse.delay, synapse));
        cachedT = Double.NaN;
    }

    /** Corrente sináptica (nA) com o potencial {@code v} (mV) no instante {@code t} (ms). */
    public double current(double t, double v) {
        if (gMaxTot <= 0) {
            return 0;
        }
        return value(t) * (e - v);
    }

    /** Condutância (µS) no instante {@code t}, depois de começar e terminar os pulsos devidos. */
    public double value(double t) {
        if (!active) {
            return 0;
        }
        if (t == cachedT) {
            return cachedValue;
        }

        boolean changed = false;

        // solução fechada desde o último evento (Lytton, 1996)
        sumOn = non * rInf + (ron - non * rInf) * Math.exp(-(t - t0) / tauOn);
        sumOff = roff * Math.exp(-(t - t0) / tauOff);

        while (!received.isEmpty()) {
            Received spike = received.peekFirst();
            if (t > spike.time() - T_TOLERANCE) {
                start(t, spike.synapse());
                changed = true;
                received.pollFirst();
            } else {
                break;
            }
        }

        while (!pulsing.isEmpty()) {
            Synapse g = pulsing.peekFirst();
            if (t > g.endOfPulse - T_TOLERANCE) {
                stop(t, g);
                changed = true;
                pulsing.pollFirst();
                g.on = false;
            } else {
                break;
            }
        }

        double value = gMaxTot * (sumOn + sumOff);

        if (changed) {
            cachedT = Double.NaN;
        } else {
            cachedT = t;
            cachedValue = value;
        }
        return value;
    }

    /** A última condutância calculada (µS). */
    public double lastValue() {
        if (gMaxTot <= 0) {
            return 0;
        }
        return gMaxTot * (sumOn + sumOff);
    }

    /** A soma das condutâncias máximas das sinapses do grupo (µS). */
    public double gMaxTotal() {
        return gMaxTot;
    }

    public int spikesReceived() {
        return spikesReceived;
    }

    public void reset() {
        non = 0;
        sumOn = 0.0;
        sumOff = 0.0;
        ron = 0.0;
        roff = 0.0;
        t0 = 0.0;
        cachedT = Double.NaN;
        pulsing.clear();
        received.clear();
        spikesReceived = 0;
        for (Synapse s : synapses.values()) {
            s.reset();
        }
    }

    /** Começa o pulso de uma sinapse; com dinâmica, a condutância máxima depende do último disparo. */
    private void start(double t, Synapse g) {
        double synContrib;

        if (g.dynamics.kind() == SynapticDynamics.Kind.NONE) {
            synContrib = g.gmax / gMaxTot;
        } else {
            double lastSpike = g.lastSpike;
            double gMax = g.gmax;

            if (lastSpike > 0) {
                double tauDynamics = g.dynamics.tau();
                double variation = g.dynamics.variation();
                double lastGmax = g.lastGmax;

                if (g.dynamics.kind() == SynapticDynamics.Kind.DEPRESSING) {
                    // variation/100 = 1 - p em Capek e Esplin (1977) e Kohn et al. (1995)
                    lastGmax = lastGmax * (1 - variation / 100);
                    gMax = lastGmax + (gMax - lastGmax) * (1 - Math.exp(-(t - lastSpike) / tauDynamics));
                } else {
                    lastGmax = lastGmax * (1 + variation / 100);
                    gMax = lastGmax + (gMax - lastGmax) * (1 - Math.exp(-(t - lastSpike) / tauDynamics));
                }
            }
            synContrib = gMax / gMaxTot;

            g.lastSpike = t;
            g.lastGmax = gMax;
        }

        // um disparo durante o pulso só prolonga o pulso
        if (!g.on) {
            double ri = g.ri * Math.exp(-(t - g.t - pulseDuration) / tauOff);

            g.ri = ri;
            g.t = t;
            ron = sumOn + ri * synContrib;
            roff = sumOff - ri * synContrib;
            non += synContrib;

            t0 = t;

            pulsing.addLast(g);
            g.on = true;
        }

        g.endOfPulse = t + pulseDuration;
    }

    /** Termina o pulso de uma sinapse: a fração dela passa de Ron para Roff. */
    private void stop(double t, Synapse g) {
        double synContrib;

        if (g.dynamics.kind() == SynapticDynamics.Kind.NONE) {
            synContrib = g.gmax / gMaxTot;
        } else {
            double gMax = g.gmax;
            if (g.lastSpike > 0) {
                gMax = g.lastGmax;
            }
            synContrib = gMax / gMaxTot;
        }

        double ri = rInf + (g.ri - rInf) * expFinish;

        g.ri = ri;

        t0 = t;
        ron = sumOn - ri * synContrib;
        roff = sumOff + ri * synContrib;
        non -= synContrib;
    }
}
