package br.remoto.core.model.synapse;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * A synapse of the original model, driven the way the neuron drives it: four calls per
 * integration step (the Runge-Kutta stages at t, t + h/2, t + h/2 and t + h).
 *
 * <p>The kinetic scheme is Destexhe et al. (1994) with the aggregation of Lytton (1996): during
 * the transmitter pulse the bound fraction rises towards r∞ with time constant τon, and after it
 * decays with τoff. Both have closed forms, so the integrated conductance can be compared
 * against them point by point.
 */
class SynapseGroupTest {

    private static final double STEP = 0.05;      // ms
    private static final double ALPHA = 1.1;      // 1/(mM ms)
    private static final double BETA = 0.19;      // 1/ms
    private static final double TMAX = 1.0;       // mM
    private static final double PULSE = 1.0;      // ms (tpeak: duration of the transmitter pulse)
    private static final double GMAX_NS = 10.0;   // nS
    private static final double SPIKE_AT = 5.0;   // ms

    @Test
    void a_single_spike_rises_during_the_pulse_and_decays_after_it() {
        double[] g = simulate(40.0);

        double gMax = GMAX_NS / 1000.0;           // uS, as the model stores it
        double rInf = ALPHA * TMAX / (ALPHA * TMAX + BETA);
        double tauOn = 1.0 / (ALPHA * TMAX + BETA);
        double tauOff = 1.0 / BETA;
        double rEnd = rInf * (1 - Math.exp(-PULSE / tauOn));

        for (int n = 0; n < g.length; n++) {
            double t = n * STEP;
            double expected;
            if (t <= SPIKE_AT) {
                expected = 0;
            } else if (t <= SPIKE_AT + PULSE) {
                expected = gMax * rInf * (1 - Math.exp(-(t - SPIKE_AT) / tauOn));
            } else {
                expected = gMax * rEnd * Math.exp(-(t - SPIKE_AT - PULSE) / tauOff);
            }
            // one step of slack: the model starts and stops the pulse on the step grid
            double slack = gMax * STEP / tauOn;
            assertThat(g[n]).as("g(t = %.2f ms)", t).isCloseTo(expected, within(slack));
        }
    }

    @Test
    void the_conductance_does_not_stay_frozen_between_spikes() {
        double[] g = simulate(40.0);
        double peak = 0;
        for (double v : g) {
            peak = Math.max(peak, v);
        }
        double afterFiveTau = g[(int) Math.round((SPIKE_AT + PULSE + 5 / BETA) / STEP)];
        assertThat(peak).isPositive();
        assertThat(afterFiveTau).isLessThan(0.01 * peak);
    }

    @Test
    void comDepressaoOSegundoDisparoProximoAbreMenos() {
        SynapticDynamics depressing = new SynapticDynamics(SynapticDynamics.Kind.DEPRESSING, 100, 50);
        double[] g = simulate(60.0, depressing, SPIKE_AT, SPIKE_AT + 30);
        double first = max(g, SPIKE_AT, SPIKE_AT + 10);
        double second = max(g, SPIKE_AT + 30, SPIKE_AT + 40);

        // o primeiro disparo não tem anterior: a condutância é a máxima
        assertThat(first).isCloseTo(max(simulate(40.0), SPIKE_AT, SPIKE_AT + 10), within(1e-15));
        assertThat(second).isLessThan(0.8 * first);
    }

    private static double max(double[] g, double from, double to) {
        double m = 0;
        for (int n = (int) Math.round(from / STEP); n < Math.round(to / STEP); n++) {
            m = Math.max(m, g[n]);
        }
        return m;
    }

    /** Conductance (uS) at the start of each step, one presynaptic spike at {@link #SPIKE_AT}. */
    static double[] simulate(double durationMs) {
        return simulate(durationMs, SynapticDynamics.NONE, SPIKE_AT);
    }

    static double[] simulate(double durationMs, SynapticDynamics dynamics, double... spikes) {
        SynapseGroup group = new SynapseGroup(70, PULSE, TMAX, ALPHA, BETA, true);
        Synapse pre = group.add("pre", GMAX_NS / 1000.0, 0, dynamics);
        for (double spike : spikes) {
            group.receiveSpike(spike, pre);
        }

        int steps = (int) Math.round(durationMs / STEP);
        double[] g = new double[steps];
        for (int n = 0; n < steps; n++) {
            double t = n * STEP;
            g[n] = group.value(t);
            group.value(t + STEP / 2);
            group.value(t + STEP / 2);
            group.value(t + STEP);
        }
        return g;
    }
}
