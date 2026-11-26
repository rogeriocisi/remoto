package br.remoto.storage;

/**
 * Permissão para a área de um usuário crescer. O armazenamento não sabe de cotas — quem decide é
 * quem o monta (no ReMoto web, a cota de disco de {@code remoto.storage}) —, e por isso o guarda
 * chega de fora. O padrão é {@link #NONE}, que deixa gravar sempre: é o caso de quem usa este
 * módulo fora do servidor.
 *
 * <p>É consultado no começo das escritas que crescem muito — criar uma execução, importar um
 * arquivo, gravar um cenário — e não durante elas: uma simulação que já começou termina de
 * gravar os seus resultados.
 */
@FunctionalInterface
public interface StorageGuard {

    /** Não limita nada. */
    StorageGuard NONE = user -> {
    };

    /**
     * Lança se não houver espaço para a área deste usuário crescer. A exceção é de quem
     * implementa, e a mensagem dela chega a quem pediu a escrita.
     */
    void requireRoom(UserId user);
}
