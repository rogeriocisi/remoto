package br.remoto.web;

import br.remoto.core.scenario.Scenario;
import br.remoto.storage.JsonCodec;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sem login, abrir outra sessão é fácil: o limite por endereço de origem soma as sessões. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class QuotaIntegrationTest {

    @TempDir
    static Path data;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("remoto.data-dir", () -> data.toString());
        registry.add("remoto.public-scenarios-dir", () -> Path.of("..", "scenarios").toAbsolutePath().toString());
        registry.add("remoto.simulation.max-per-origin", () -> "1");
    }

    @Autowired
    MockMvc mvc;

    @Test
    void limitePorOrigemSomaAsSessoes() throws Exception {
        JsonCodec codec = new JsonCodec();
        Scenario scenario = codec.read(Path.of("..", "scenarios", "remoto-17-default-soleus-contraction.json"),
                Scenario.class).withDuration(5000);
        String body = "{\"scenario\":" + new String(codec.toBytes(scenario), StandardCharsets.UTF_8) + "}";
        MockHttpSession ana = new MockHttpSession();
        MockHttpSession outraAbaDaAna = new MockHttpSession();
        MockHttpSession bruno = new MockHttpSession();

        String first = submit(ana, body, from("10.0.0.1")).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        submit(outraAbaDaAna, body, from("10.0.0.1")).andExpect(status().isTooManyRequests());
        String other = submit(bruno, body, from("10.0.0.2")).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        mvc.perform(delete("/api/runs/" + JsonPath.read(first, "$.runId")).session(ana).with(csrf()));
        mvc.perform(delete("/api/runs/" + JsonPath.read(other, "$.runId")).session(bruno).with(csrf()));
    }

    private org.springframework.test.web.servlet.ResultActions submit(MockHttpSession session, String body,
                                                                       RequestPostProcessor origin) throws Exception {
        return mvc.perform(post("/api/runs").session(session).with(csrf()).with(origin)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
