package br.remoto.web.sim;

/** O usuário atingiu o limite de simulações na fila. */
public class QuotaExceededException extends RuntimeException {

    public QuotaExceededException(String message) {
        super(message);
    }
}
