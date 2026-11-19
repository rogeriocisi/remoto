package br.remoto.storage;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Identificador de usuário usado como nome de diretório.
 *
 * <p>Nunca vem de algo que o usuário digita: é um hash do emissor + sujeito do provedor de
 * identidade (OIDC), ou um id aleatório de convidado. Por isso é sempre seguro em caminhos.
 */
public record UserId(String value) {

    private static final Pattern VALID = Pattern.compile("^[ug]-[0-9a-f]{32}$");

    public UserId {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("UserId inválido");
        }
    }

    /** Usuário autenticado: hash de {@code issuer|subject}. */
    public static UserId registered(String issuer, String subject) {
        return new UserId("u-" + Hashing.sha256Hex(issuer + "|" + subject).substring(0, 32));
    }

    /** Convidado novo, com área temporária própria. */
    public static UserId newGuest() {
        return new UserId("g-" + UUID.randomUUID().toString().replace("-", ""));
    }

    public boolean isGuest() {
        return value.startsWith("g-");
    }

    @Override
    public String toString() {
        return value;
    }
}
