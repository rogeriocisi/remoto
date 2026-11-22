package br.remoto.storage;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Identificador da área de uma sessão do navegador, usado como nome de diretório.
 *
 * <p>Não há contas: cada sessão ganha um id aleatório, que nunca vem de algo que o usuário
 * digita. Por isso é sempre seguro em caminhos.
 */
public record UserId(String value) {

    private static final Pattern VALID = Pattern.compile("^g-[0-9a-f]{32}$");

    public UserId {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("UserId inválido");
        }
    }

    /** Área nova, para uma sessão que acabou de começar. */
    public static UserId newSession() {
        return new UserId("g-" + UUID.randomUUID().toString().replace("-", ""));
    }

    @Override
    public String toString() {
        return value;
    }
}
