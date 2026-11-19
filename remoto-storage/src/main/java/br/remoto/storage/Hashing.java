package br.remoto.storage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 em hexadecimal (ETags, links de compartilhamento, ids de usuário). */
public final class Hashing {

    private Hashing() {
    }

    public static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    public static String sha256Hex(String content) {
        return sha256Hex(content.getBytes(StandardCharsets.UTF_8));
    }
}
