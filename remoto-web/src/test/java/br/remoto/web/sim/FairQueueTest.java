package br.remoto.web.sim;

import br.remoto.storage.UserId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FairQueueTest {

    private final UserId a = new UserId("g-" + "a".repeat(32));
    private final UserId b = new UserId("g-" + "b".repeat(32));
    private final UserId c = new UserId("g-" + "c".repeat(32));

    @Test
    void loteGrandeDeUmUsuarioNaoBloqueiaOsOutros() {
        FairQueue<String> queue = new FairQueue<>();
        for (int i = 0; i < 50; i++) {
            queue.offer(a, "A" + i);
        }
        queue.offer(b, "B0");
        queue.offer(c, "C0");
        queue.offer(c, "C1");

        // sem limite por usuário: rodízio A, B, C, A, C, A, A...
        List<String> order = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            order.add(queue.poll(u -> 0, Integer.MAX_VALUE).orElseThrow().job());
        }
        assertThat(order).containsExactly("A0", "B0", "C0", "A1", "C1", "A2", "A3");
    }

    @Test
    void respeitaOLimiteDeAtivasPorUsuario() {
        FairQueue<String> queue = new FairQueue<>();
        queue.offer(a, "A0");
        queue.offer(a, "A1");
        queue.offer(b, "B0");
        Map<UserId, Integer> active = new HashMap<>();

        Optional<FairQueue.Entry<String>> first = queue.poll(u -> active.getOrDefault(u, 0), 1);
        active.merge(first.orElseThrow().user(), 1, Integer::sum);
        Optional<FairQueue.Entry<String>> second = queue.poll(u -> active.getOrDefault(u, 0), 1);
        active.merge(second.orElseThrow().user(), 1, Integer::sum);

        assertThat(first.get().job()).isEqualTo("A0");
        assertThat(second.get().job()).isEqualTo("B0");
        // A já tem 1 ativa e B não tem mais nada: ninguém pode ser despachado
        assertThat(queue.poll(u -> active.getOrDefault(u, 0), 1)).isEmpty();
        assertThat(queue.queued(a)).isEqualTo(1);
    }

    @Test
    void removerJobDaFila() {
        FairQueue<String> queue = new FairQueue<>();
        queue.offer(a, "A0");

        assertThat(queue.remove(a, "A0"::equals)).isTrue();
        assertThat(queue.isEmpty()).isTrue();
        assertThat(queue.poll(u -> 0, 1)).isEmpty();
    }
}
