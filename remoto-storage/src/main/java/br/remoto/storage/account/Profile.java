package br.remoto.storage.account;

import java.time.Instant;

/** Dados mínimos do usuário. Nada de senha: a autenticação fica com o provedor OIDC. */
public record Profile(String displayName, String institution, String issuer, Instant createdAt) {
}
