package br.remoto.web.i18n;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.TreeMap;

/**
 * Os textos que o JavaScript da página usa, no idioma da requisição: {@code /js/i18n.js} define
 * {@code window.ReMotoI18n} com as chaves {@code js.*} do pacote de mensagens (sem o prefixo) e a
 * função {@code window.T(chave, ...)}, que troca {@code {0}}, {@code {1}}... pelos argumentos.
 *
 * <p>As chaves são lidas do pacote, e não listadas aqui, para que traduzir um texto novo seja só
 * acrescentar a linha nos dois arquivos .properties.
 */
@Controller
class I18nScriptController {

    private static final String PREFIX = "js.";

    @GetMapping(path = "/js/i18n.js", produces = "text/javascript;charset=UTF-8")
    ResponseEntity<String> script() {
        Locale locale = LocaleContextHolder.getLocale();
        return ResponseEntity.ok()
                // depende do idioma, que vem do cookie e do Accept-Language; é pequeno e sai pronto
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE + ", " + HttpHeaders.ACCEPT_LANGUAGE)
                .body(body(locale));
    }

    private static String body(Locale locale) {
        ResourceBundle bundle = ResourceBundle.getBundle("messages", locale,
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
        TreeMap<String, String> texts = new TreeMap<>();
        for (String key : bundle.keySet()) {
            if (key.startsWith(PREFIX)) {
                texts.put(key.substring(PREFIX.length()), bundle.getString(key));
            }
        }
        StringBuilder js = new StringBuilder("// gerado por I18nScriptController a partir de messages*.properties\n");
        js.append("window.ReMotoLang = ").append(quote(locale.getLanguage())).append(";\n");
        js.append("window.ReMotoI18n = {");
        String separator = "\n  ";
        for (var entry : texts.entrySet()) {
            js.append(separator).append(quote(entry.getKey())).append(": ").append(quote(entry.getValue()));
            separator = ",\n  ";
        }
        js.append("\n};\n");
        js.append("""
                window.T = (key, ...args) => {
                  const text = window.ReMotoI18n[key];
                  if (text === undefined) return key;
                  return text.replace(/\\{(\\d+)\\}/g, (whole, i) => (args[i] === undefined ? whole : args[i]));
                };
                """);
        return js.toString();
    }

    /** Texto como literal JavaScript: o conteúdo vem dos .properties, mas escapar é barato. */
    private static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '<' -> out.append("\\u003c"); // nunca fecha um <script> por acidente
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
