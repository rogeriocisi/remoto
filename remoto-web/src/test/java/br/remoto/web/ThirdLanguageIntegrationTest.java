package br.remoto.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Um idioma novo é um {@code messages_<código>.properties} e o código em
 * {@code remoto.languages}: nenhuma página nova, nenhuma rota nova, nenhum {@code if} de idioma no
 * código. Aqui o terceiro idioma é o "xx" de
 * {@code src/test/resources/messages_xx.properties}, que traduz umas poucas chaves — as outras
 * caem no pacote padrão, em inglês.
 */
@SpringBootTest(properties = "remoto.languages=en,pt,xx")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ThirdLanguageIntegrationTest {

    private static final String IN_XX = "Simulations in the test language";

    @TempDir
    static Path data;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("remoto.data-dir", () -> data.toString());
        registry.add("remoto.public-scenarios-dir", () -> Path.of("..", "scenarios").toAbsolutePath().toString());
    }

    @Autowired
    MockMvc mvc;

    /** A porta de entrada do idioma novo existe, com o atributo lang e os textos dele. */
    @Test
    void oIdiomaNovoGanhaAPortaDeEntrada() throws Exception {
        mvc.perform(get("/xx").header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("lang=\"xx\"")))
                .andExpect(content().string(containsString(IN_XX)))
                .andExpect(cookie().value("remoto-lang", "xx"));

        // e as portas que já existiam continuam
        mvc.perform(get("/pt")).andExpect(status().isOk())
                .andExpect(content().string(containsString("lang=\"pt-BR\"")));
        mvc.perform(get("/")).andExpect(status().isOk());
    }

    /** O idioma do navegador também alcança o idioma novo, sem ninguém escolher nada. */
    @Test
    void oNavegadorNoIdiomaNovoJaAbreNele() throws Exception {
        mvc.perform(get("/").header(HttpHeaders.ACCEPT_LANGUAGE, "xx-XX,xx;q=0.9"))
                .andExpect(content().string(containsString(IN_XX)));
    }

    /**
     * Com três idiomas, o seletor mostra os três, com o atual marcado — ele é uma lista, não "o
     * outro dos dois". As chaves que o pacote novo não traduz saem do padrão, em inglês.
     */
    @Test
    void oSeletorMostraOsTresIdiomas() throws Exception {
        String page = mvc.perform(get("/xx")).andReturn().getResponse().getContentAsString();

        int links = page.split("class=\"botao secundario pequeno idioma\"", -1).length - 1;
        assertThat(links).as("links do seletor de idioma").isEqualTo(3);
        assertThat(page)
                .contains("hreflang=\"en\"")
                .contains("hreflang=\"pt\"")
                .contains("hreflang=\"xx\"")
                .contains("aria-current=\"page\"") // o idioma atual
                // o rótulo de cada idioma e, nas chaves sem tradução, o texto do pacote padrão
                .contains(">EN<")
                .contains(">PT<")
                .contains("The model");
    }
}
