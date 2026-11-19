package br.remoto.web.security;

/** Ação disponível só para usuários autenticados (compartilhar, exportar ou apagar conta). */
public class LoginRequiredException extends RuntimeException {

    public LoginRequiredException() {
        super("entre com sua conta para usar esta função");
    }
}
