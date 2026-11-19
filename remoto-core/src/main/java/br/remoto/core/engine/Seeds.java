package br.remoto.core.engine;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Geradores aleatórios determinísticos derivados da seed do cenário.
 *
 * <p>Cada fluxo nomeado (por exemplo {@code "SOL/ruido"}) recebe um gerador independente, de modo
 * que acrescentar um núcleo ao cenário não altera a sequência aleatória dos outros.
 */
public final class Seeds {

    private static final RandomGeneratorFactory<RandomGenerator> FACTORY =
            RandomGeneratorFactory.of("L64X128MixRandom");

    private Seeds() {
    }

    public static RandomGenerator stream(long seed, String name) {
        return FACTORY.create(mix(seed ^ mix(name.hashCode())));
    }

    /** Finalizador do SplitMix64: espalha bem seeds parecidas. */
    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
