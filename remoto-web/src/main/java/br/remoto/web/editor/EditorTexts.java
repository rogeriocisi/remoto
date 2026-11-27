package br.remoto.web.editor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Os rótulos do editor nos outros idiomas.
 *
 * <p>As telas nascem uma vez, num bloco estático, com os rótulos do site antigo, em inglês; o
 * idioma, esse é de cada requisição. A tradução acontece então ao desenhar a tela, e a chave é o
 * próprio texto em inglês: acrescentar um campo não obriga a inventar uma chave, e um texto sem
 * tradução sai em inglês, que é o do site original, o do guia e o da literatura — nunca um
 * {@code editor.campo.42} cru na tela.
 *
 * <p>O dicionário de cada idioma é um {@code editor-<idioma>.properties}, em que a chave é o
 * texto em inglês reduzido a um apelido estável ({@link #key}) e o valor é a tradução. O
 * {@code EditorTextsTest} confere que toda tela está traduzida e que não sobrou entrada de texto
 * que não existe mais.
 */
public final class EditorTexts {

    private static final Logger log = LoggerFactory.getLogger(EditorTexts.class);

    /** Idiomas com dicionário; o inglês é o próprio texto do código. */
    private static final Map<String, Map<String, String>> DICTIONARIES = new ConcurrentHashMap<>();

    private EditorTexts() {
    }

    /**
     * O texto no idioma da requisição, ou ele mesmo quando não há tradução.
     *
     * <p>Fora de uma requisição não há idioma escolhido, e o rótulo fica no original, em inglês:
     * é o do site antigo, e é por ele que o código e os testes chamam os campos. O idioma da
     * máquina não entra nisso — um teste não deve passar ou falhar conforme onde roda.
     */
    public static String t(String english) {
        if (english == null || english.isBlank() || LocaleContextHolder.getLocaleContext() == null) {
            return english;
        }
        Map<String, String> dictionary = of(LocaleContextHolder.getLocale().getLanguage());
        String translated = dictionary.get(key(english));
        return translated == null ? english : translated;
    }

    /**
     * Como {@link #t(String)}, para um rótulo que pode terminar com a unidade entre colchetes: a
     * unidade é símbolo e não se traduz, e no dicionário a entrada é só do texto.
     */
    public static String label(String english) {
        if (english == null) {
            return null;
        }
        int unit = english.lastIndexOf(" [");
        if (unit < 0 || !english.endsWith("]")) {
            return t(english);
        }
        return t(english.substring(0, unit)) + english.substring(unit);
    }

    /** O dicionário de um idioma, vazio quando não há arquivo (o inglês, entre eles). */
    public static Map<String, String> of(String language) {
        return DICTIONARIES.computeIfAbsent(language, EditorTexts::load);
    }

    private static Map<String, String> load(String language) {
        String file = "editor-" + language + ".properties";
        try (InputStream in = EditorTexts.class.getClassLoader().getResourceAsStream(file)) {
            if (in == null) {
                return Map.of();
            }
            Properties p = new Properties();
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return Map.copyOf((Map) p);
        } catch (IOException e) {
            log.warn("não foi possível ler {}: {}", file, e.getMessage());
            return Map.of();
        }
    }

    /**
     * O apelido de um texto em inglês: as primeiras palavras, sem acento nem pontuação, e um
     * resumo do texto inteiro. As palavras deixam o arquivo legível; o resumo distingue dois
     * textos que começam igual, e muda quando o inglês muda, de modo que uma tradução que ficou
     * para trás aparece como entrada órfã, em vez de passar por boa.
     */
    public static String key(String english) {
        String flat = Normalizer.normalize(english, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", ".")
                .replaceAll("(^\\.|\\.$)", "");
        String start = flat.length() <= 44 ? flat : flat.substring(0, flat.lastIndexOf('.', 44) + 1)
                .replaceAll("\\.$", "");
        return start + "." + digest(english);
    }

    private static String digest(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
