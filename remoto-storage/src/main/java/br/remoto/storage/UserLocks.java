package br.remoto.storage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Uma trava por usuário: gravações do mesmo usuário são serializadas, usuários diferentes
 * gravam em paralelo. Vale para uma única instância do servidor (ver docs/nova-versao.md).
 */
public final class UserLocks {

    private final ConcurrentHashMap<UserId, ReentrantLock> locks = new ConcurrentHashMap<>();

    public <T> T withLock(UserId user, Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(user, k -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    public void forget(UserId user) {
        locks.remove(user);
    }
}
