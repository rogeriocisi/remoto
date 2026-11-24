package br.remoto.web.sim;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Interrompe uma simulação antes que ela esgote a memória do servidor.
 *
 * <p>O motor guarda todas as amostras de todas as séries até o fim, então a memória de uma
 * simulação cresce em linha reta com o tempo simulado: um cenário longo, com muitos neurônios de
 * sinais guardados, pede vários gigabytes. Sem este vigia, a JVM lança {@code OutOfMemoryError}
 * na thread que estiver alocando naquele instante, que pode ser a de outra pessoa abrindo uma
 * página, e a simulação fica presa em "rodando".
 *
 * <p>O que se mede é a memória em uso logo depois de cada coleta de lixo, que é o que está de fato
 * vivo. Duas regras, e nas duas uma coleta completa confirma a medida antes de interromper:
 * <ul>
 *   <li><b>projeção</b>: com duas medidas ao longo da simulação, a reta diz quanto ela vai pedir
 *       no fim; passando do limite, ela para já, nos primeiros por cento, em vez de rodar muito
 *       tempo para falhar depois. Só vale com uma simulação por vez, porque com várias o
 *       crescimento não é de uma só;</li>
 *   <li><b>teto</b>: memória viva acima do limite, com qualquer número de simulações.</li>
 * </ul>
 */
final class MemoryGuard {

    /** A memória da JVM, atrás de uma interface para os testes simularem um heap. */
    interface Heap {
        /** O tamanho máximo do heap, em bytes. */
        long max();

        /** Quantas coletas de lixo já aconteceram. */
        long collections();

        /** O heap em uso logo depois da coleta mais recente, em bytes. */
        long usedAfterLastCollection();

        /** Faz uma coleta completa e devolve o heap em uso depois dela. */
        long collect();
    }

    /**
     * Por que a simulação foi interrompida.
     *
     * @param neededBytes a memória que a simulação pediria até o fim (ou a que já ocupa, no teto)
     * @param limitBytes  a memória que o servidor deixa uma simulação ocupar
     */
    record Verdict(long neededBytes, long limitBytes) {

        /** O texto que vai para o manifesto da execução; a página o traduz pelo prefixo. */
        String message() {
            return PREFIX + " needs about " + megabytes(neededBytes) + " MB, this server allows "
                    + megabytes(limitBytes) + " MB";
        }

        private static long megabytes(long bytes) {
            return Math.round(bytes / (1024.0 * 1024.0));
        }
    }

    /** Começo do erro de falta de memória no manifesto (ver app.js). */
    static final String PREFIX = "memory:";

    /** Acima do limite da projeção, um pouco de folga antes de parar pelo teto. */
    private static final double CEILING_OVER_LIMIT = 1.06;
    /** A reta só vale com as duas medidas separadas por este tanto da simulação... */
    private static final double MIN_SPAN = 0.005;
    /** ...e por este tanto de memória; abaixo disso é ruído. */
    private static final long MIN_GROWTH = 32L * 1024 * 1024;
    /** Depois de uma projeção desmentida pela coleta completa, espera-se este tanto para outra. */
    private static final double RETRY_SPAN = 0.05;

    private final Heap heap;
    private final long limit;
    private final long ceiling;

    /**
     * @param fraction a fração do heap que uma simulação pode ocupar; 0 (ou menos) desliga o vigia
     */
    MemoryGuard(Heap heap, double fraction) {
        this.heap = heap;
        boolean on = heap != null && fraction > 0 && heap.max() > 0 && heap.max() != Long.MAX_VALUE;
        this.limit = on ? (long) (heap.max() * Math.min(fraction, 0.94)) : Long.MAX_VALUE;
        this.ceiling = on ? (long) (limit * CEILING_OVER_LIMIT) : Long.MAX_VALUE;
    }

    static MemoryGuard ofJvm(double fraction) {
        return new MemoryGuard(JvmHeap.create(), fraction);
    }

    boolean enabled() {
        return limit != Long.MAX_VALUE;
    }

    long limitBytes() {
        return limit;
    }

    /** O acompanhamento de uma simulação; cada uma tem o seu. */
    Watch watch() {
        return new Watch();
    }

    final class Watch {
        private long seen = -1;
        private double firstFraction = -1;
        private long firstUsed;
        private double nextProjection;
        private double confirmedFraction = -1;
        private long confirmedLive;

        /**
         * Chamado a cada aviso de progresso.
         *
         * @param fraction quanto da simulação já foi feito, de 0 a 1
         * @param alone    se esta é a única simulação rodando
         * @return por que interromper, ou null para continuar
         */
        Verdict check(double fraction, boolean alone) {
            if (!enabled()) {
                return null;
            }
            long collections = heap.collections();
            if (collections == seen) {
                return null; // sem coleta nova, não há medida nova
            }
            seen = collections;
            long used = heap.usedAfterLastCollection();

            if (used > ceiling) {
                long live = confirm();
                if (live > ceiling) {
                    return new Verdict(live, limit);
                }
                used = live;
            }
            if (!alone || fraction <= 0 || fraction >= 1) {
                return null;
            }
            if (firstFraction < 0) {
                firstFraction = fraction;
                firstUsed = used;
                return null;
            }
            if (fraction - firstFraction < MIN_SPAN || used - firstUsed < MIN_GROWTH || fraction < nextProjection) {
                return null;
            }
            double slope = (used - firstUsed) / (fraction - firstFraction);
            if (used + slope * (1 - fraction) <= limit) {
                return null;
            }
            // A medida depois de uma coleta parcial ainda conta lixo, e a reta sairia inclinada
            // demais. Na suspeita, a decisão é refeita só com coletas completas: a primeira marca
            // um ponto, a segunda, um pouco adiante, dá a inclinação de verdade.
            long live = confirm();
            if (confirmedFraction < 0) {
                confirmedFraction = fraction;
                confirmedLive = live;
                nextProjection = fraction + MIN_SPAN;
                return null;
            }
            double growth = live - confirmedLive;
            double projected = live + growth / (fraction - confirmedFraction) * (1 - fraction);
            if (growth >= MIN_GROWTH && projected > limit) {
                return new Verdict((long) projected, limit);
            }
            confirmedFraction = fraction;
            confirmedLive = live;
            nextProjection = fraction + RETRY_SPAN;
            return null;
        }

        private long confirm() {
            long live = heap.collect();
            seen = heap.collections();
            return live;
        }
    }

    /** O heap desta JVM, lido dos MXBeans; funciona com qualquer coletor. */
    static final class JvmHeap implements Heap {
        private final Set<String> heapPools;

        private JvmHeap(Set<String> heapPools) {
            this.heapPools = heapPools;
        }

        /** @return null se esta JVM não informa o uso depois das coletas (o vigia fica desligado) */
        static JvmHeap create() {
            try {
                Set<String> pools = ManagementFactory.getMemoryPoolMXBeans().stream()
                        .filter(p -> p.getType() == MemoryType.HEAP)
                        .map(MemoryPoolMXBean::getName)
                        .collect(Collectors.toUnmodifiableSet());
                boolean informs = ManagementFactory.getGarbageCollectorMXBeans().stream()
                        .anyMatch(b -> b instanceof com.sun.management.GarbageCollectorMXBean);
                return informs && !pools.isEmpty() ? new JvmHeap(pools) : null;
            } catch (RuntimeException | LinkageError e) {
                return null;
            }
        }

        @Override
        public long max() {
            return Runtime.getRuntime().maxMemory();
        }

        @Override
        public long collections() {
            long total = 0;
            for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                total += Math.max(0, bean.getCollectionCount());
            }
            return total;
        }

        @Override
        public long usedAfterLastCollection() {
            com.sun.management.GcInfo last = null;
            for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                if (bean instanceof com.sun.management.GarbageCollectorMXBean sun) {
                    com.sun.management.GcInfo info = sun.getLastGcInfo();
                    if (info != null && (last == null || info.getEndTime() > last.getEndTime())) {
                        last = info;
                    }
                }
            }
            if (last == null) {
                return 0;
            }
            long used = 0;
            for (Map.Entry<String, MemoryUsage> pool : last.getMemoryUsageAfterGc().entrySet()) {
                if (heapPools.contains(pool.getKey())) {
                    used += pool.getValue().getUsed();
                }
            }
            return used;
        }

        @Override
        public long collect() {
            System.gc();
            Runtime runtime = Runtime.getRuntime();
            return runtime.totalMemory() - runtime.freeMemory();
        }
    }
}
