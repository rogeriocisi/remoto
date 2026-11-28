package br.remoto.core.model.random;

import java.util.Random;

/**
 * A fonte de aleatoriedade das simulações: um gerador por thread.
 *
 * <p>Quem roda uma simulação fixa a semente com {@link #seed(long)} e obtém resultados
 * reprodutíveis; sem semente, o comportamento é o dos antigos {@code Math.random()} e
 * {@code new Random()}. Os sorteios têm de acontecer sempre na mesma ordem: a atualização
 * paralela dos neurônios marca as threads de trabalho com {@link #forbid()}.
 */
public final class Rng {

    private static final ThreadLocal<Random> CURRENT = ThreadLocal.withInitial(Random::new);

    /** Para threads que não podem sortear. */
    private static final Random FORBIDDEN = new Random(0) {
        private static final long serialVersionUID = 1L;

        @Override
        protected int next(int bits) {
            throw new ForbiddenDraw();
        }
    };

    /**
     * Uma thread marcada com {@link #forbid()} tentou sortear. É um Error para que os
     * {@code catch (Exception)} do código original não o engulam.
     */
    public static final class ForbiddenDraw extends Error {
        private static final long serialVersionUID = 1L;

        ForbiddenDraw() {
            super("random draw outside the simulation thread");
        }
    }

    private Rng() {
    }

    /** Torna reprodutível a sequência da thread atual. */
    public static void seed(long seed) {
        CURRENT.set(new Random(seed));
    }

    /** Volta a um gerador sem semente. */
    public static void reset() {
        CURRENT.remove();
    }

    /** Faz qualquer sorteio na thread atual falhar ({@link ForbiddenDraw}). */
    public static void forbid() {
        CURRENT.set(FORBIDDEN);
    }

    /**
     * Roda {@code body} com os sorteios proibidos nesta thread e devolve, no fim, o gerador que
     * ela tinha.
     *
     * <p>Existe porque a thread que coordena a atualização paralela também processa blocos de
     * neurônios, depois de rodar o laço sequencial — onde sortear é legítimo e necessário. Sem
     * isto, um sorteio indevido num neurônio paralelo só era detectado quando o bloco calhava de
     * cair numa thread de trabalho, de modo que a proteção valia por sorteio e por execução, e
     * não sempre.
     */
    public static void forbidDuring(Runnable body) {
        Random before = CURRENT.get();
        CURRENT.set(FORBIDDEN);
        try {
            body.run();
        } finally {
            CURRENT.set(before);
        }
    }

    /** Um número uniforme em [0, 1), no lugar de {@code Math.random()}. */
    public static double random() {
        return CURRENT.get().nextDouble();
    }

    /** Um gerador derivado da sequência atual, no lugar de {@code new Random()}. */
    public static Random newRandom() {
        return new Random(CURRENT.get().nextLong());
    }
}
