package br.remoto.web.i18n;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.servlet.LocaleResolver;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Em que idioma a página sai, entre os declarados em {@code remoto.languages} (ver
 * {@code RemotoProperties}). São sempre as mesmas páginas: o que muda são os textos, que vêm de
 * {@code messages_<código>.properties}.
 *
 * <ol>
 *   <li>o {@code ?lang=pt} do seletor de idioma, nesta mesma requisição (o
 *       {@code LocaleChangeInterceptor} o grava aqui, e daí no cookie);</li>
 *   <li>a <b>porta de entrada por idioma</b>, {@code /pt}: o endereço diz o idioma, então ele
 *       vence o cookie — é o que faz o link do site do projeto abrir no idioma dele;</li>
 *   <li>a escolha anterior da pessoa, guardada no cookie {@value #COOKIE};</li>
 *   <li>o {@code Accept-Language} do navegador, que traz o idioma da região de onde a pessoa
 *       acessa: qualquer variante de um idioma declarado serve (pt, pt-BR e pt-PT caem em pt);</li>
 *   <li>o primeiro idioma declarado, para todos os outros e quando o navegador não diz nada.</li>
 * </ol>
 *
 * <p>A porta de entrada é resolvida aqui, e não no controller, porque o idioma tem de estar
 * decidido antes de a página começar a ser montada: o {@code LocaleModelAdvice}, que monta o
 * seletor e os {@code hreflang}, roda antes do método do controller.
 *
 * <p>É o idioma do navegador, não o país do endereço de origem: não há aqui nenhuma base de
 * geolocalização, e o cabeçalho acerta mais (quem está fora do Brasil com o navegador em português
 * continua lendo em português).
 */
public final class RemotoLocaleResolver implements LocaleResolver {

    public static final String COOKIE = "remoto-lang";
    private static final Duration COOKIE_AGE = Duration.ofDays(365);

    private final List<Locale> supported;

    public RemotoLocaleResolver(List<String> languages) {
        this.supported = languages.stream()
                .map(tag -> Locale.forLanguageTag(tag.trim()))
                .filter(locale -> !locale.getLanguage().isEmpty())
                .toList();
        if (supported.isEmpty()) {
            throw new IllegalArgumentException("remoto.languages não declara nenhum idioma válido: " + languages);
        }
    }

    /** Os idiomas declarados, na ordem em que foram declarados. */
    public List<Locale> supported() {
        return supported;
    }

    /** O idioma padrão: o primeiro declarado. É o que fica na raiz da aplicação. */
    public Locale defaultLocale() {
        return supported.get(0);
    }

    /**
     * O idioma declarado que corresponde a esta etiqueta ({@code pt}, {@code pt-BR}), ou
     * {@code null} se nenhum corresponde. É o que valida a porta de entrada {@code /<código>}.
     */
    public Locale match(String tag) {
        if (tag == null) {
            return null;
        }
        String language = Locale.forLanguageTag(tag.trim()).getLanguage();
        if (language.isEmpty()) {
            return null;
        }
        for (Locale locale : supported) {
            if (locale.getLanguage().equals(language)) {
                return locale;
            }
        }
        return null;
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        Locale chosen = justChosen(request);
        if (chosen == null) {
            chosen = fromPath(request);
        }
        if (chosen == null) {
            chosen = fromCookie(request);
        }
        return chosen != null ? chosen : fromHeader(request);
    }

    /**
     * A porta de entrada por idioma: o primeiro trecho do caminho, quando é um idioma declarado e
     * não há mais nada depois dele ({@code /pt} e {@code /pt/}, não {@code /pt/qualquer-coisa}).
     */
    private Locale fromPath(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.isEmpty() || path.charAt(0) != '/') {
            return null;
        }
        int slash = path.indexOf('/', 1);
        if (slash >= 0 && slash < path.length() - 1) {
            return null; // tem mais caminho depois do primeiro trecho
        }
        return match(slash < 0 ? path.substring(1) : path.substring(1, slash));
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        Locale chosen = locale == null ? null : match(locale.toLanguageTag());
        if (chosen == null || response == null) {
            return; // ?lang= com algo que não é um idioma declarado: fica como estava
        }
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, chosen.getLanguage())
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(COOKIE_AGE)
                .build()
                .toString());
        request.setAttribute(RemotoLocaleResolver.class.getName(), chosen);
    }

    /** Na requisição que troca o idioma, vale já o novo (o cookie só chega na próxima). */
    private static Locale justChosen(HttpServletRequest request) {
        return request.getAttribute(RemotoLocaleResolver.class.getName()) instanceof Locale chosen ? chosen : null;
    }

    private Locale fromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie c : request.getCookies()) {
            if (COOKIE.equals(c.getName())) {
                Locale locale = match(c.getValue());
                if (locale != null) {
                    return locale;
                }
            }
        }
        return null;
    }

    private Locale fromHeader(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
        if (header == null || header.isBlank()) {
            return defaultLocale();
        }
        try {
            Locale best = Locale.lookup(Locale.LanguageRange.parse(header), supported);
            return best != null ? best : defaultLocale();
        } catch (IllegalArgumentException e) {
            return defaultLocale(); // cabeçalho malformado
        }
    }
}
