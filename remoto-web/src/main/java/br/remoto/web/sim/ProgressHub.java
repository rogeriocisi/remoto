package br.remoto.web.sim;

import br.remoto.storage.run.RunStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Progresso ao vivo das execuções, enviado ao navegador por Server-Sent Events (substitui o
 * polling do antigo ProgressBarServlet). Fica só em memória; o estado durável está no manifesto.
 */
@Component
public class ProgressHub {

    public record Progress(String runId, RunStatus status, double fraction) {
    }

    private static final long TIMEOUT_MS = Duration.ofHours(2).toMillis();

    private final Map<String, Progress> current = new ConcurrentHashMap<>();
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public void publish(String runId, RunStatus status, double fraction) {
        Progress progress = new Progress(runId, status, fraction);
        List<SseEmitter> listeners;
        if (status.isTerminal()) {
            current.remove(runId);
            listeners = emitters.remove(runId);
        } else {
            current.put(runId, progress);
            listeners = emitters.get(runId);
        }
        if (listeners != null) {
            for (SseEmitter emitter : listeners) {
                send(emitter, progress);
            }
        }
    }

    public Optional<Progress> current(String runId) {
        return Optional.ofNullable(current.get(runId));
    }

    /**
     * @param persistedStatus status gravado no manifesto, usado quando a execução já não está
     *                        em memória (terminou antes da inscrição)
     */
    public SseEmitter subscribe(String runId, Supplier<RunStatus> persistedStatus) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        List<SseEmitter> list = emitters.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        emitter.onCompletion(() -> list.remove(emitter));
        emitter.onTimeout(() -> list.remove(emitter));
        emitter.onError(e -> list.remove(emitter));

        Progress now = current.get(runId);
        if (now == null) {
            // Terminou antes (ou durante) a inscrição: responde com o estado gravado e encerra.
            list.remove(emitter);
            RunStatus status = persistedStatus.get();
            send(emitter, new Progress(runId, status, status == RunStatus.DONE ? 1 : 0));
            if (!status.isTerminal()) {
                emitter.complete();
            }
        } else {
            send(emitter, now);
        }
        return emitter;
    }

    private static void send(SseEmitter emitter, Progress progress) {
        try {
            emitter.send(SseEmitter.event().name("progresso").data(progress, MediaType.APPLICATION_JSON));
            if (progress.status().isTerminal()) {
                emitter.complete();
            }
        } catch (IOException | IllegalStateException e) {
            // navegador fechou a conexão; o emitter é descartado pelos callbacks
        }
    }
}
