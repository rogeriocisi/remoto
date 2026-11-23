package br.remoto.web.i18n;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Os dois idiomas têm as mesmas chaves: um texto novo sem tradução apareceria como a chave crua na
 * página, e é fácil esquecer um dos arquivos.
 */
class MessagesTest {

    @Test
    void osDoisIdiomasTemAsMesmasChaves() throws IOException {
        Properties en = load("/messages.properties");
        Properties pt = load("/messages_pt.properties");

        assertThat(new TreeSet<>(pt.stringPropertyNames()))
                .as("chaves de messages_pt.properties")
                .isEqualTo(new TreeSet<>(en.stringPropertyNames()));
        assertThat(en.stringPropertyNames()).isNotEmpty();
    }

    /** Nenhum texto fica vazio nem sobra como cópia literal da chave. */
    @Test
    void nenhumTextoVazio() throws IOException {
        for (String file : new String[] {"/messages.properties", "/messages_pt.properties"}) {
            Properties p = load(file);
            for (String key : p.stringPropertyNames()) {
                assertThat(p.getProperty(key).trim()).as("%s → %s", file, key).isNotEmpty();
            }
        }
    }

    private static Properties load(String resource) throws IOException {
        Properties p = new Properties();
        try (InputStream in = MessagesTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("arquivo %s", resource).isNotNull();
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }
}
