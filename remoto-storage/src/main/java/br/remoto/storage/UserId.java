package br.remoto.storage;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Identificador da área de um navegador, usado como nome de diretório.
 *
 * <p>Não há contas: cada navegador ganha um id aleatório, que volta num cookie. Como o cookie
 * pode ser alterado, só vale o formato gerado aqui; por isso o id é sempre seguro em caminhos.
 */
public record UserId(String value) {

    private static final Pattern VALID = Pattern.compile("^g-[0-9a-f]{32}$");

    public UserId {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("UserId inválido");
        }
    }

    /** Área nova, para um navegador que chega sem área. */
    public static UserId newSession() {
        return new UserId("g-" + UUID.randomUUID().toString().replace("-", ""));
    }

    @Override
    public String toString() {
        return value;
    }
}
