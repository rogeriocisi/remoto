package br.remoto.storage;

/** O arquivo mudou desde a leitura (outra aba ou outro dispositivo do mesmo usuário gravou antes). */
public class StaleWriteException extends RuntimeException {

    public StaleWriteException() {
        super("o cenário foi alterado em outro lugar; recarregue antes de salvar");
    }
}
