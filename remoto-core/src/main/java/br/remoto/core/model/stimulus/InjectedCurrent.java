package br.remoto.core.model.stimulus;

import br.remoto.core.model.signal.Modulation;

/**
 * Corrente injetada num compartimento de um neurônio: uma amplitude fixa mais um sinal modulante.
 *
 * @param imax amplitude fixa (nA)
 * @param signal o sinal somado à amplitude; null = nenhum
 */
public record InjectedCurrent(double imax, Modulation signal) {

    /** Corrente (nA) em {@code t} (ms). */
    public double value(double t) {
        double i = imax;

        if (signal != null) {
            i += signal.value(t);
        }

        return i;
    }
}
