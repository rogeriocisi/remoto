package br.remoto.web.security;

import br.remoto.storage.UserId;

/**
 * Quem está fazendo a requisição: usuário autenticado (OIDC ou local em dev) ou convidado com
 * área temporária. Todo acesso a dados passa por {@link #id()}, o que isola os usuários.
 */
public record Caller(UserId id, String displayName, boolean guest, String issuer) {

    public void requireLogin() {
        if (guest) {
            throw new LoginRequiredException();
        }
    }
}
