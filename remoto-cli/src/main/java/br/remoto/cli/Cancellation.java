package br.remoto.cli;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cancelamento de uma execução da linha de comando, por Ctrl+C.
 *
 * <p>Existe porque o motor consulta {@code RunContext.isCancelled()} de dentro da thread virtual
 * que acompanha o progresso, e não da thread que chamou {@code run}. Enquanto a CLI respondia
 * {@code Thread.currentThread().isInterrupted()}, ela lia a flag da própria thread do vigia: o
 * cancelamento era código morto e não havia como parar uma simulação a não ser matando a JVM, o
 * que deixava o manifesto preso em {@code RUNNING}. A resposta tem de vir de um estado
 * compartilhado, que é o que esta classe guarda.
 *
 * <p>O gancho de desligamento espera a thread principal terminar, com um teto de tempo: sem essa
 * espera a JVM encerraria antes de o manifesto ser gravado, e a execução cancelada ficaria
 * indistinguível de uma que ainda está rodando.
 */
final class Cancellation {

    /** Quanto o desligamento espera a thread principal gravar o manifesto. */
    private static final long CLEANUP_MS = 10_000;

    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final Thread hook;

    private Cancellation() {
        Thread main = Thread.currentThread();
        this.hook = new Thread(() -> {
            cancelled.set(true);
            try {
                main.join(CLEANUP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "cancelar-simulacao");
    }

    /** Liga o Ctrl+C ao cancelamento. Chame de dentro da thread que vai rodar a simulação. */
    static Cancellation onShutdown() {
        Cancellation c = new Cancellation();
        Runtime.getRuntime().addShutdownHook(c.hook);
        return c;
    }

    boolean cancelled() {
        return cancelled.get();
    }

    /** Cancela sem passar pelo Ctrl+C (usado nos testes). */
    void cancel() {
        cancelled.set(true);
    }

    /** Desfaz o gancho quando a execução termina por conta própria. */
    void close() {
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException alreadyShuttingDown) {
            // a JVM já está encerrando: o gancho está rodando e não há o que desfazer
        }
    }
}
