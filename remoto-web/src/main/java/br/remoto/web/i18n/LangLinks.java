package br.remoto.web.i18n;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** O endereço da página atual no outro idioma, para o seletor de idioma. */
public final class LangLinks {

    public static final String PARAM = "lang";

    private LangLinks() {
    }

    /**
     * O que o seletor de idioma precisa em toda página: o idioma atual, o outro e o endereço desta
     * mesma página nele. Vem pelo modelo porque o Thymeleaf 3.1 não alcança mais a requisição.
     */
    public static Map<String, Object> attributes(HttpServletRequest request) {
        Locale current = LocaleContextHolder.getLocale();
        Locale other = other(current);
        return Map.of("lang", current.getLanguage(),
                "langOther", other.getLanguage(),
                "langSwitchUrl", switchUrl(request, other));
    }

    /** O outro dos dois idiomas. */
    public static Locale other(Locale current) {
        return RemotoLocaleResolver.PT.getLanguage().equals(current.getLanguage())
                ? RemotoLocaleResolver.EN
                : RemotoLocaleResolver.PT;
    }

    /**
     * A mesma página, com {@code ?lang=} do outro idioma. Mantém os outros parâmetros (a tela e a
     * seleção do editor, por exemplo), para que trocar de idioma não tire ninguém de onde está.
     */
    public static String switchUrl(HttpServletRequest request, Locale other) {
        String query = request.getQueryString();
        String kept = query == null || query.isBlank() ? "" : Arrays.stream(query.split("&"))
                .filter(p -> !p.equals(PARAM) && !p.startsWith(PARAM + "="))
                .collect(Collectors.joining("&"));
        String base = request.getRequestURI() + "?" + (kept.isEmpty() ? "" : kept + "&");
        return base + PARAM + "=" + other.getLanguage();
    }
}
