package br.remoto.web.i18n;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Põe em toda página o que o seletor de idioma usa ({@link LangLinks#attributes}) e a versão do
 * ReMoto, que o cabeçalho mostra como o site antigo mostrava ("Current Version: 2.1").
 *
 * <p>As páginas de erro não passam por aqui, porque o tratamento de exceções monta o próprio
 * modelo: {@code PageExceptionHandler} e {@code NotFoundPage} chamam o {@code LangLinks} direto, e
 * não mostram a versão.
 */
@ControllerAdvice(basePackages = "br.remoto.web.ui")
class LocaleModelAdvice {

    private final LangLinks langLinks;
    private final String version;

    /** A versão vem do pom pelo filtro de recursos do Spring Boot; só maior.menor chega à página. */
    LocaleModelAdvice(LangLinks langLinks, @Value("${remoto.version:}") String version) {
        this.langLinks = langLinks;
        this.version = shortVersion(version);
    }

    @ModelAttribute
    void page(Model model, HttpServletRequest request) {
        model.addAllAttributes(langLinks.attributes(request));
        if (!version.isEmpty()) {
            model.addAttribute("appVersion", version);
        }
    }

    /** "4.0.0-SNAPSHOT" → "4.0"; o que não tem esse formato vai como está. */
    static String shortVersion(String version) {
        String[] parts = version.split("\\.");
        return parts.length >= 2 ? parts[0] + "." + parts[1] : version;
    }
}
