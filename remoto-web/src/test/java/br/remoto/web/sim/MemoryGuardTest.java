package br.remoto.web.sim;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O vigia de memória com um heap de mentira: cada teste diz quanto está em uso depois de cada
 * coleta, e o vigia decide se a simulação continua.
 */
class MemoryGuardTest {

    private static final long MB = 1024L * 1024;

    /** Um heap de 1000 MB em que o teste escolhe as medidas. */
    private static final class FakeHeap implements MemoryGuard.Heap {
        long collections;
        long afterLast;
        long live;
        int forced;

        /** Uma coleta parcial: sobra {@code used}, do qual {@code live} está de fato vivo. */
        void collected(long used, long live) {
            collections++;
            afterLast = used;
            this.live = live;
        }

        @Override
        public long max() {
            return 1000 * MB;
        }

        @Override
        public long collections() {
            return collections;
        }

        @Override
        public long usedAfterLastCollection() {
            return afterLast;
        }

        @Override
        public long collect() {
            forced++;
            collections++;
            afterLast = live;
            return live;
        }
    }

    @Test
    void simulacaoQueCabeChegaAoFim() {
        FakeHeap heap = new FakeHeap();
        MemoryGuard.Watch watch = new MemoryGuard(heap, 0.8).watch();

        // 100 MB de rede e mais 4 MB a cada 1%: 500 MB no fim, abaixo do limite de 800 MB
        for (int percent = 1; percent <= 99; percent++) {
            long used = (100 + 4L * percent) * MB;
            heap.collected(used, used);
            assertThat(watch.check(percent / 100.0, true)).as("aos %d%%", percent).isNull();
        }
        assertThat(heap.forced).as("coletas forçadas").isZero();
    }

    @Test
    void simulacaoQueNaoVaiCaberParaLogoNoComeco() {
        FakeHeap heap = new FakeHeap();
        MemoryGuard.Watch watch = new MemoryGuard(heap, 0.8).watch();

        // 100 MB de rede e 86 MB a cada 1%: 8,7 GB no fim, como a Position Task de 30 s
        MemoryGuard.Verdict verdict = null;
        int percent = 0;
        while (verdict == null && percent < 99) {
            percent++;
            long used = (100 + 86L * percent) * MB;
            heap.collected(used, used);
            verdict = watch.check(percent / 100.0, true);
        }

        assertThat(verdict).as("o vigia interrompe").isNotNull();
        assertThat(percent).as("ainda no começo, com duas medidas").isLessThanOrEqualTo(3);
        assertThat(verdict.neededBytes() / MB).as("o que a simulação pediria").isBetween(8000L, 9500L);
        assertThat(verdict.limitBytes()).isEqualTo(800 * MB);
        assertThat(verdict.message()).startsWith(MemoryGuard.PREFIX).contains("800 MB");
    }

    @Test
    void lixoAntigoNaoInterrompeNinguem() {
        FakeHeap heap = new FakeHeap();
        MemoryGuard.Watch watch = new MemoryGuard(heap, 0.8).watch();

        // a medida depois da coleta parcial cresce depressa, mas quase tudo é lixo de antes: a
        // coleta completa mostra 150 MB vivos, e a simulação segue
        heap.collected(300 * MB, 110 * MB);
        assertThat(watch.check(0.01, true)).isNull();
        heap.collected(700 * MB, 150 * MB);
        assertThat(watch.check(0.02, true)).isNull();
        heap.collected(900 * MB, 150 * MB);
        assertThat(watch.check(0.03, true)).as("acima do teto, mas era lixo").isNull();
        assertThat(heap.forced).as("a coleta completa confirmou antes de decidir").isPositive();
    }

    @Test
    void memoriaVivaAcimaDoTetoInterrompeMesmoComOutrasRodando() {
        FakeHeap heap = new FakeHeap();
        MemoryGuard.Watch watch = new MemoryGuard(heap, 0.8).watch();

        heap.collected(400 * MB, 400 * MB);
        assertThat(watch.check(0.10, false)).isNull();
        // com várias simulações a projeção não vale: o crescimento não é de uma só
        heap.collected(700 * MB, 700 * MB);
        assertThat(watch.check(0.11, false)).as("sem projeção com outras rodando").isNull();
        heap.collected(900 * MB, 900 * MB);
        MemoryGuard.Verdict verdict = watch.check(0.12, false);

        assertThat(verdict).as("900 MB vivos de 1000").isNotNull();
        assertThat(verdict.neededBytes()).isEqualTo(900 * MB);
    }

    @Test
    void semColetaNovaNaoHaMedidaNova() {
        FakeHeap heap = new FakeHeap();
        MemoryGuard.Watch watch = new MemoryGuard(heap, 0.8).watch();

        heap.collected(100 * MB, 100 * MB);
        assertThat(watch.check(0.01, true)).isNull();
        heap.afterLast = 990 * MB; // sem contar coleta: o vigia nem olha
        assertThat(watch.check(0.50, true)).isNull();
    }

    @Test
    void fracaoZeroDesligaOVigia() {
        FakeHeap heap = new FakeHeap();
        MemoryGuard guard = new MemoryGuard(heap, 0);

        heap.collected(999 * MB, 999 * MB);
        assertThat(guard.enabled()).isFalse();
        assertThat(guard.watch().check(0.5, true)).isNull();
    }

    @Test
    void oHeapDestaJvmInformaAsMedidas() {
        MemoryGuard.Heap heap = MemoryGuard.JvmHeap.create();

        assertThat(heap).as("a JVM dos testes informa o uso depois das coletas").isNotNull();
        long live = heap.collect();
        assertThat(live).isPositive().isLessThan(heap.max());
        assertThat(heap.collections()).isPositive();
        assertThat(heap.usedAfterLastCollection()).isPositive().isLessThan(heap.max());
    }
}
