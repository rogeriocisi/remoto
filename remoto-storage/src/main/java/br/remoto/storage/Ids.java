package br.remoto.storage;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/** Ids de cenários e execuções: ULIDs (ordenáveis por tempo) e validação contra path traversal. */
public final class Ids {

    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9-]{0,63}$");
    private static final char[] CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {
    }

    public static String newId() {
        return ulid(System.currentTimeMillis(), RANDOM);
    }

    public static boolean isSafe(String id) {
        return id != null && SAFE.matcher(id).matches();
    }

    /** Garante que o id pode virar nome de arquivo (sem "/", "..", etc.). */
    public static String requireSafe(String id) {
        if (!isSafe(id)) {
            throw new IllegalArgumentException("identificador inválido");
        }
        return id;
    }

    /** 10 caracteres de tempo (ms) + 16 aleatórios, alfabeto Crockford base32. */
    static String ulid(long timeMs, RandomGenerator random) {
        char[] out = new char[26];
        long t = timeMs;
        for (int i = 9; i >= 0; i--) {
            out[i] = CROCKFORD[(int) (t & 31)];
            t >>>= 5;
        }
        for (int i = 10; i < 26; i++) {
            out[i] = CROCKFORD[random.nextInt(32)];
        }
        return new String(out);
    }
}
