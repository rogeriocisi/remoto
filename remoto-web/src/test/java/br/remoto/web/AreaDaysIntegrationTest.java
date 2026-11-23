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
import java.time.Duration;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O prazo das áreas ({@code remoto.retention.area-days}) vale para a limpeza, para o cookie e para
 * o aviso da página: se o cookie durasse menos, a área ficaria no disco sem ninguém conseguir
 * abri-la.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AreaDaysIntegrationTest {

    @TempDir
    static Path data;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("remoto.data-dir", () -> data.toString());
        registry.add("remoto.public-scenarios-dir", () -> Path.of("..", "scenarios").toAbsolutePath().toString());
        registry.add("remoto.retention.area-days", () -> "7");
    }

    @Autowired
    MockMvc mvc;

    @Test
    void cookieEPaginaSeguemOPrazoDaArea() throws Exception {
        // em português, para conferir a frase da página (o idioma vem do navegador)
        mvc.perform(get("/").header("Accept-Language", "pt-BR"))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("remoto-area", (int) Duration.ofDays(7).toSeconds()))
                .andExpect(content().string(containsString("depois de 7 dias sem uso")));
    }
}
