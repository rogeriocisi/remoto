package br.remoto.web.i18n;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Todos os idiomas têm as mesmas chaves: um texto novo sem tradução apareceria como a chave crua
 * na página, e é fácil esquecer um dos arquivos. Vale para os idiomas de hoje e para o que vier —
 * um idioma novo é um {@code messages_<código>.properties} e o código em {@code remoto.languages},
 * e este teste falha enquanto faltar uma chave nele.
 *
 * <p>Os arquivos são lidos de {@code src/main/resources}, e não do classpath, para que o pacote de
 * mentira do {@code ThirdLanguageIntegrationTest} não entre na conferência.
 */
class MessagesTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final String BASE = "messages.properties";

    @Test
    void todosOsIdiomasTemAsMesmasChaves() throws IOException {
        Properties base = load(RESOURCES.resolve(BASE));
        Map<Path, Properties> translations = translations();

        assertThat(base.stringPropertyNames()).as("chaves de %s", BASE).isNotEmpty();
        assertThat(translations).as("arquivos messages_*.properties").isNotEmpty();
        for (var translation : translations.entrySet()) {
            assertThat(new TreeSet<>(translation.getValue().stringPropertyNames()))
                    .as("chaves de %s", translation.getKey().getFileName())
                    .isEqualTo(new TreeSet<>(base.stringPropertyNames()));
        }
    }

    /** Nenhum texto fica vazio nem sobra como cópia literal da chave. */
    @Test
    void nenhumTextoVazio() throws IOException {
        for (var file : allBundles().entrySet()) {
            Properties texts = file.getValue();
            for (String key : texts.stringPropertyNames()) {
                assertThat(texts.getProperty(key).trim())
                        .as("%s → %s", file.getKey().getFileName(), key)
                        .isNotEmpty();
            }
        }
    }

    /**
     * Cada pacote diz o seu próprio código de idioma, que vai no atributo {@code lang} do
     * {@code <html>} de todas as páginas (é dele que o idioma da página sai, e não de um
     * {@code if} no template).
     */
    @Test
    void cadaPacoteDizOSeuCodigoDeIdioma() throws IOException {
        for (var file : allBundles().entrySet()) {
            String name = file.getKey().getFileName().toString();
            String tag = file.getValue().getProperty("lang.htmlTag");
            assertThat(tag).as("lang.htmlTag de %s", name).isNotNull();
            // messages.properties é o inglês; messages_pt.properties, o pt-BR
            String expected = name.equals(BASE) ? "en" : name.replaceAll("messages_|\\.properties", "");
            assertThat(tag).as("lang.htmlTag de %s", name).startsWith(expected);
        }
    }

    private static Map<Path, Properties> allBundles() throws IOException {
        Map<Path, Properties> all = new LinkedHashMap<>();
        all.put(RESOURCES.resolve(BASE), load(RESOURCES.resolve(BASE)));
        all.putAll(translations());
        return all;
    }

    private static Map<Path, Properties> translations() throws IOException {
        Map<Path, Properties> found = new TreeMap<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(RESOURCES, "messages_*.properties")) {
            for (Path file : files) {
                found.put(file, load(file));
            }
        }
        return found;
    }

    private static Properties load(Path file) throws IOException {
        assertThat(file).as("arquivo de textos").exists();
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }
}
