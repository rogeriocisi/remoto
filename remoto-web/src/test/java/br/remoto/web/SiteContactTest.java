package br.remoto.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O endereço de contato que a instalação configura, e a barra de lugares na página da API.
 * Sem {@code remoto.contact-email}, api/site devolve vazio e o site segue deduzindo o endereço
 * do domínio em que está servido, como sempre fez.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SiteContactTest {

    @TempDir
    static Path data;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("remoto.data-dir", () -> data.toString());
        registry.add("remoto.contact-email", () -> "contato@exemplo.br");
    }

    @Autowired
    MockMvc mvc;

    @Test
    void aInstalacaoEntregaOEnderecoConfigurado() throws Exception {
        mvc.perform(get("/api/site"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactEmail").value("contato@exemplo.br"));
    }

    @Test
    void aPaginaDaApiLevaAosOutrosLugaresDoReMoto() throws Exception {
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("remoto-lugares")))
                .andExpect(content().string(containsString("github.com/rogeriocisi/remoto")))
                .andExpect(content().string(containsString("aria-current=\"page\"")));
    }
}
