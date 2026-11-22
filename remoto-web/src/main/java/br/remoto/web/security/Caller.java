package br.remoto.web.security;

import br.remoto.storage.UserId;

/**
 * Quem está fazendo a requisição: a sessão do navegador, com sua área ({@link #id()}), e o
 * endereço de origem ({@link #origin()}), usado nos limites da fila. Todo acesso a dados passa
 * por {@link #id()}, o que isola as sessões.
 */
public record Caller(UserId id, String origin) {
}
