package br.remoto.web.i18n;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * O que o seletor de idioma precisa em toda página: o idioma atual, os outros idiomas declarados e
 * o endereço desta mesma página em cada um deles. Vem pelo modelo porque o Thymeleaf 3.1 não
 * alcança mais a requisição.
 *
 * <p>O seletor lista todos os idiomas declarados, com o atual marcado (como o "EN · PT" do site),
 * e são sempre as mesmas páginas em todos eles, então o endereço de cada um é:
 *
 * <ul>
 *   <li>nas <b>portas de entrada</b> (a raiz da aplicação e {@code /<código>}), a porta do outro
 *       idioma — um endereço linkável, que é o que o site do projeto usa;</li>
 *   <li>nas <b>telas internas</b> (editor, resultados), a mesma tela com {@code ?lang=}, mantendo
 *       os outros parâmetros, para que trocar de idioma não tire ninguém de onde está.</li>
 * </ul>
 */
@Component
public class LangLinks {

    public static final String PARAM = "lang";

    private final RemotoLocaleResolver locales;

    LangLinks(RemotoLocaleResolver locales) {
        this.locales = locales;
    }

    /**
     * Um idioma no seletor: etiqueta, rótulo, título, o endereço desta página nele e se é o atual.
     * O seletor mostra todos, com o atual marcado, como o "EN · PT" do site.
     */
    public record Choice(String tag, String label, String title, String url, boolean current) {
    }

    /** A porta de entrada de um idioma, para o {@code canonical} e os {@code hreflang}. */
    public record Entry(String tag, String url) {
    }

    /** Os atributos que toda página recebe. */
    public Map<String, Object> attributes(HttpServletRequest request) {
        Locale current = LocaleContextHolder.getLocale();
        boolean entryPage = isEntryPage(request, current);

        List<Choice> choices = new ArrayList<>();
        List<Entry> entries = new ArrayList<>();
        for (Locale locale : locales.supported()) {
            entries.add(new Entry(locale.toLanguageTag(), entryUrl(request, locale)));
            boolean isCurrent = locale.getLanguage().equals(current.getLanguage());
            choices.add(new Choice(locale.toLanguageTag(),
                    Messages.msg("lang.name." + locale.getLanguage()),
                    isCurrent ? Messages.msg("lang.current") : Messages.msg("lang.switch." + locale.getLanguage()),
                    chosenUrl(request, locale, entryPage),
                    isCurrent));
        }

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("lang", current.getLanguage());
        model.put("langChoices", choices);
        model.put("langEntries", entries);
        model.put("langEntry", entryUrl(request, current));
        model.put("langDefaultEntry", entryUrl(request, locales.defaultLocale()));
        return model;
    }

    /**
     * O endereço que o seletor usa para um idioma.
     *
     * <p>Numa porta de entrada, a porta do idioma pedido — mas a do idioma padrão é a raiz da
     * aplicação, que não diz idioma nenhum: ali o resolver voltaria a decidir pelo cookie, e quem
     * já tivesse escolhido outro idioma clicaria sem sair do lugar. Por isso a do padrão leva o
     * {@code ?lang=}, que é o que registra a escolha. Fora das portas, a mesma tela com
     * {@code ?lang=}.
     */
    private String chosenUrl(HttpServletRequest request, Locale locale, boolean entryPage) {
        if (!entryPage) {
            return switchUrl(request, locale);
        }
        String entry = entryUrl(request, locale);
        return isDefault(locale) ? entry + "?" + PARAM + "=" + locale.getLanguage() : entry;
    }

    private boolean isDefault(Locale locale) {
        return locale.getLanguage().equals(locales.defaultLocale().getLanguage());
    }

    /**
     * A porta de entrada de um idioma: a raiz da aplicação, no idioma padrão, e {@code /<código>/}
     * nos outros.
     */
    public String entryUrl(HttpServletRequest request, Locale locale) {
        String base = request.getContextPath() + "/";
        return isDefault(locale) ? base : base + locale.getLanguage() + "/";
    }

    /**
     * Se o endereço atual é uma porta de entrada (com ou sem a barra final): a raiz da aplicação
     * ou o código de um idioma declarado. Vale a de qualquer idioma, e não só a do atual, porque
     * {@code /simulator/en} serve o idioma padrão, que também mora na raiz.
     */
    private boolean isEntryPage(HttpServletRequest request, Locale current) {
        String uri = withoutTrailingSlash(request.getRequestURI());
        if (uri.equals(withoutTrailingSlash(entryUrl(request, current)))) {
            return true;
        }
        String base = request.getContextPath() + "/";
        return locales.supported().stream()
                .anyMatch(locale -> uri.equals(withoutTrailingSlash(base + locale.getLanguage())));
    }

    private static String withoutTrailingSlash(String path) {
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    /**
     * A mesma página, com {@code ?lang=} do idioma pedido. Mantém os outros parâmetros (a tela e a
     * seleção do editor, por exemplo).
     */
    public static String switchUrl(HttpServletRequest request, Locale target) {
        String query = request.getQueryString();
        String kept = query == null || query.isBlank() ? "" : Arrays.stream(query.split("&"))
                .filter(p -> !p.equals(PARAM) && !p.startsWith(PARAM + "="))
                .collect(Collectors.joining("&"));
        String base = request.getRequestURI() + "?" + (kept.isEmpty() ? "" : kept + "&");
        return base + PARAM + "=" + target.getLanguage();
    }
}
