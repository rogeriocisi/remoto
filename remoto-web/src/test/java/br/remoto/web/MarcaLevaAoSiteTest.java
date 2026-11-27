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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Clicar na marca leva sempre à página inicial do site, em qualquer tela: no simulador, no
 * editor e na página da API. Quem leva à raiz do simulador é o item "Simulador" da barra.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class MarcaLevaAoSiteTest {

    @TempDir
    static Path data;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("remoto.data-dir", () -> data.toString());
    }

    @Autowired
    MockMvc mvc;

    @Test
    void aMarcaDoSimuladorLevaAoSite() throws Exception {
        assertThat(marca(pagina("/", "en"))).isEqualTo("/");
        assertThat(marca(pagina("/", "pt-BR"))).isEqualTo("/pt/");
    }

    @Test
    void aMarcaDaPaginaDaApiLevaAoSite() throws Exception {
        assertThat(marca(pagina("/swagger-ui/index.html", "en"))).isEqualTo("/");
        assertThat(marca(pagina("/swagger-ui/index.html", "pt-BR"))).isEqualTo("/pt/");
    }

    private String pagina(String caminho, String idioma) throws Exception {
        return mvc.perform(get(caminho).header(HttpHeaders.ACCEPT_LANGUAGE, idioma))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** O href da primeira marca da página (a que tem o logotipo e o nome). */
    private static String marca(String html) {
        Matcher m = Pattern.compile("<a[^>]*class=\"(?:marca marca-link|remoto-marca)\"[^>]*href=\"([^\"]*)\"")
                .matcher(html);
        if (!m.find()) {
            m = Pattern.compile("<a[^>]*href=\"([^\"]*)\"[^>]*class=\"(?:marca marca-link|remoto-marca)\"")
                    .matcher(html);
            assertThat(m.find()).as("marca encontrada no HTML").isTrue();
        }
        return m.group(1);
    }
}
