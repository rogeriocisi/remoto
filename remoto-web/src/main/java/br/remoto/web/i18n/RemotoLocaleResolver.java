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
 * Em que idioma a página sai: português ou inglês.
 *
 * <ol>
 *   <li>a escolha da pessoa, guardada no cookie {@value #COOKIE} (o seletor de idioma manda
 *       {@code ?lang=pt} ou {@code ?lang=en}, que o {@code LocaleChangeInterceptor} grava aqui);</li>
 *   <li>o {@code Accept-Language} do navegador, que traz o idioma da região de onde a pessoa
 *       acessa: qualquer variante de português (pt, pt-BR, pt-PT) abre em português;</li>
 *   <li>inglês, para todos os outros idiomas e quando o navegador não diz nada.</li>
 * </ol>
 *
 * <p>É o idioma do navegador, não o país do endereço de origem: não há aqui nenhuma base de
 * geolocalização, e o cabeçalho acerta mais (quem está fora do Brasil com o navegador em português
 * continua lendo em português).
 */
public final class RemotoLocaleResolver implements LocaleResolver {

    public static final String COOKIE = "remoto-lang";
    public static final Locale PT = Locale.forLanguageTag("pt");
    public static final Locale EN = Locale.ENGLISH;
    private static final List<Locale> SUPPORTED = List.of(PT, EN);
    private static final Duration COOKIE_AGE = Duration.ofDays(365);

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        Locale chosen = fromCookie(request);
        return chosen != null ? chosen : fromHeader(request);
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        Locale supported = locale == null ? null : supported(locale.getLanguage());
        if (supported == null || response == null) {
            return; // ?lang= com algo que não é pt nem en: fica como estava
        }
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, supported.getLanguage())
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(COOKIE_AGE)
                .build()
                .toString());
        request.setAttribute(RemotoLocaleResolver.class.getName(), supported);
    }

    /** Na requisição que troca o idioma, vale já o novo (o cookie só chega na próxima). */
    private Locale fromCookie(HttpServletRequest request) {
        if (request.getAttribute(RemotoLocaleResolver.class.getName()) instanceof Locale justChosen) {
            return justChosen;
        }
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie c : request.getCookies()) {
            if (COOKIE.equals(c.getName())) {
                Locale locale = supported(c.getValue());
                if (locale != null) {
                    return locale;
                }
            }
        }
        return null;
    }

    private static Locale fromHeader(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
        if (header == null || header.isBlank()) {
            return EN;
        }
        try {
            Locale best = Locale.lookup(Locale.LanguageRange.parse(header), SUPPORTED);
            return best != null ? best : EN;
        } catch (IllegalArgumentException e) {
            return EN; // cabeçalho malformado
        }
    }

    private static Locale supported(String language) {
        if (language == null) {
            return null;
        }
        String tag = language.trim().toLowerCase(Locale.ROOT);
        if (tag.startsWith("pt")) {
            return PT;
        }
        return tag.startsWith("en") ? EN : null;
    }
}
