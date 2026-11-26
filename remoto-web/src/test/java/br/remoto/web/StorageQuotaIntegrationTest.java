package br.remoto.web;

import br.remoto.core.scenario.Scenario;
import br.remoto.storage.JsonCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O teto por área, com um valor pequeno de propósito: o primeiro cenário cabe, e a partir daí a
 * área não cresce mais. O que já está gravado continua a ser lido e pode ser apagado — é assim
 * que quem bateu no teto sai dele.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class StorageQuotaIntegrationTest {

    @TempDir
    static Path data;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("remoto.data-dir", () -> data.toString());
        registry.add("remoto.public-scenarios-dir", () -> Path.of("..", "scenarios").toAbsolutePath().toString());
        registry.add("remoto.storage.max-area-bytes", () -> "16KB");
    }

    @Autowired
    MockMvc mvc;

    @Test
    void aAreaParaDeCrescerNoTeto() throws Exception {
        MockHttpSession ana = new MockHttpSession();
        String body = scenario();

        // o primeiro cabe: a área está vazia
        mvc.perform(post("/api/scenarios").session(ana).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());

        // o cenário gravado já passa de 16 KB, então o seguinte não entra
        mvc.perform(post("/api/scenarios").session(ana).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("16 KB")));

        // e nem uma simulação, que gravaria bem mais
        mvc.perform(post("/api/runs").session(ana).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"scenario\":" + body + "}"))
                .andExpect(status().isTooManyRequests());

        // ler continua valendo: é como se vê o que apagar
        mvc.perform(get("/api/scenarios").session(ana)).andExpect(status().isOk());
        mvc.perform(get("/api/me").session(ana)).andExpect(status().isOk());
    }

    @Test
    void outraAreaNaoHerdaOTetoDaPrimeira() throws Exception {
        MockHttpSession bruno = new MockHttpSession();
        mvc.perform(post("/api/scenarios").session(bruno).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(scenario())).andExpect(status().isCreated());
    }

    private static String scenario() throws Exception {
        JsonCodec codec = new JsonCodec();
        Scenario scenario = codec.read(Path.of("..", "scenarios", "remoto-17-default-soleus-contraction.json"),
                Scenario.class).withDuration(5000);
        return new String(codec.toBytes(scenario), StandardCharsets.UTF_8);
    }
}
