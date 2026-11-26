package br.remoto.web.api;

import br.remoto.web.config.RemotoProperties;
import br.remoto.web.i18n.Messages;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quantas escritas por minuto a API aceita de um mesmo endereço.
 *
 * <p>É o limite que as cotas por área não dão: sem login, trocar de cookie é trocar de área, mas
 * o endereço continua o mesmo. Ele vale só para a API ({@code /api/**}) e só para os métodos que
 * escrevem — é o lado roteirizável, e é por onde o abuso passaria. As telas não entram: elas
 * mandam fragmentos htmx, que não trocam conteúdo numa resposta 4xx, e já esbarram na cota de
 * disco e nos limites da fila, que elas sabem mostrar.
 *
 * <p>Cada endereço tem um balde de fichas: ele enche até
 * {@code remoto.limits.api-writes-per-minute} e se recompõe ao longo do minuto, de modo que uma
 * rajada curta passa e um fluxo contínuo é cortado. Zero, o padrão, desliga o limite. Os baldes
 * ficam em memória, nesta instância, como o resto dos limites.
 */
@Component
class ApiWriteRateFilter extends OncePerRequestFilter {

    /** Métodos que mudam alguma coisa; o resto passa direto. */
    private static final Set<String> WRITES = Set.of("POST", "PUT", "PATCH", "DELETE");

    /** Acima disto, os baldes cheios são varridos: um balde por endereço não cresce sem fim. */
    private static final int MANY_BUCKETS = 2_000;

    private static final Logger log = LoggerFactory.getLogger(ApiWriteRateFilter.class);

    private final int perMinute;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    ApiWriteRateFilter(RemotoProperties props) {
        this.perMinute = props.limits().apiWritesPerMinute();
        log.info(perMinute > 0
                ? "Escritas da API: até " + perMinute + " por minuto do mesmo endereço"
                : "Escritas da API: sem limite por endereço");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (perMinute <= 0 || !WRITES.contains(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (allow(request.getRemoteAddr())) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.RETRY_AFTER, "1");
        // o formato do ApiExceptionHandler (RFC 9457), escrito à mão: o filtro responde antes de
        // o Spring MVC entrar, e aqui são três campos
        response.getWriter().write("{\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\""
                + quoted(Messages.msg("quota.writesPerMinute", perMinute)) + "\",\"instance\":\""
                + quoted(request.getRequestURI()) + "\"}");
    }

    /** O texto como corpo de uma string JSON (as mensagens não têm aspas, mas podem ganhar). */
    private static String quoted(String text) {
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c < 0x20) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Tira uma ficha do balde do endereço, se houver. */
    private boolean allow(String address) {
        if (buckets.size() > MANY_BUCKETS) {
            sweep();
        }
        return buckets.computeIfAbsent(address, a -> new Bucket(perMinute)).take(perMinute);
    }

    /** Esquece quem está com o balde cheio: é como se nunca tivesse escrito. */
    private void sweep() {
        for (Iterator<Map.Entry<String, Bucket>> it = buckets.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue().isFull(perMinute)) {
                it.remove();
            }
        }
    }

    /**
     * Fichas que voltam com o tempo: ao fim de um minuto sem escrever, o balde está cheio de
     * novo. A conta é feita na hora de usar, e não por um relógio à parte.
     */
    private static final class Bucket {

        private double tokens;
        private Instant since = Instant.now();

        private Bucket(int perMinute) {
            this.tokens = perMinute;
        }

        private synchronized boolean take(int perMinute) {
            refill(perMinute);
            if (tokens < 1) {
                return false;
            }
            tokens -= 1;
            return true;
        }

        private synchronized boolean isFull(int perMinute) {
            refill(perMinute);
            return tokens >= perMinute;
        }

        private void refill(int perMinute) {
            Instant now = Instant.now();
            double seconds = (now.toEpochMilli() - since.toEpochMilli()) / 1000.0;
            if (seconds > 0) {
                tokens = Math.min(perMinute, tokens + seconds * perMinute / 60.0);
                since = now;
            }
        }
    }
}
