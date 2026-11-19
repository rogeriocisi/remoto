package br.remoto.web.sim;

import br.remoto.storage.UserId;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Fila justa: uma fila por usuário, despachada em rodízio, respeitando um limite de jobs ativos
 * por usuário. Assim uma varredura de 100 simulações de um usuário não bloqueia os demais.
 *
 * <p>Não é thread-safe; o {@link SimulationService} sincroniza o acesso.
 */
final class FairQueue<T> {

    private final Map<UserId, ArrayDeque<T>> queues = new HashMap<>();
    /** Usuários com fila não vazia, na ordem em que serão atendidos. */
    private final ArrayDeque<UserId> rotation = new ArrayDeque<>();

    void offer(UserId user, T job) {
        queues.computeIfAbsent(user, u -> {
            rotation.addLast(u);
            return new ArrayDeque<>();
        }).addLast(job);
    }

    int queued(UserId user) {
        ArrayDeque<T> q = queues.get(user);
        return q == null ? 0 : q.size();
    }

    boolean isEmpty() {
        return queues.isEmpty();
    }

    boolean remove(UserId user, Predicate<T> match) {
        ArrayDeque<T> q = queues.get(user);
        if (q == null) {
            return false;
        }
        boolean removed = false;
        for (Iterator<T> it = q.iterator(); it.hasNext(); ) {
            if (match.test(it.next())) {
                it.remove();
                removed = true;
                break;
            }
        }
        if (q.isEmpty()) {
            queues.remove(user);
            rotation.remove(user);
        }
        return removed;
    }

    /**
     * Próximo job em rodízio. Usuários que já atingiram {@code maxActivePerUser} são pulados
     * (mantêm o lugar na fila) e quem acabou de ser atendido vai para o fim.
     */
    Optional<Entry<T>> poll(ToIntFunction<UserId> activeOf, int maxActivePerUser) {
        int n = rotation.size();
        for (int i = 0; i < n; i++) {
            UserId user = rotation.pollFirst();
            if (activeOf.applyAsInt(user) < maxActivePerUser) {
                ArrayDeque<T> q = queues.get(user);
                T job = q.pollFirst();
                if (q.isEmpty()) {
                    queues.remove(user);
                } else {
                    rotation.addLast(user);
                }
                return Optional.of(new Entry<>(user, job));
            }
            rotation.addLast(user);
        }
        return Optional.empty();
    }

    record Entry<T>(UserId user, T job) {
    }
}
