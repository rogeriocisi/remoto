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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sem login, trocar de cookie é trocar de área — mas não de endereço. O limite de escritas da
 * API é por endereço, e por isso soma as áreas; ler não entra na conta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class WriteRateIntegrationTest {

    @TempDir
    static Path data;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("remoto.data-dir", () -> data.toString());
        registry.add("remoto.public-scenarios-dir", () -> Path.of("..", "scenarios").toAbsolutePath().toString());
        registry.add("remoto.limits.api-writes-per-minute", () -> "2");
    }

    @Autowired
    MockMvc mvc;

    @Test
    void oLimiteSomaAsAreasDoMesmoEndereco() throws Exception {
        String body = scenario();
        MockHttpSession ana = new MockHttpSession();
        MockHttpSession outraAba = new MockHttpSession();

        create(ana, body, "10.1.0.1").andExpect(status().isCreated());
        create(outraAba, body, "10.1.0.1").andExpect(status().isCreated());

        // a terceira escrita do mesmo endereço não passa, mesmo numa área nova
        create(new MockHttpSession(), body, "10.1.0.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "1"));

        // ler não gasta ficha
        mvc.perform(get("/api/scenarios").session(ana).with(from("10.1.0.1"))).andExpect(status().isOk());

        // e o limite é de quem escreveu, não de todo mundo
        create(new MockHttpSession(), body, "10.1.0.2").andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions create(MockHttpSession session, String body,
                                                                      String address) throws Exception {
        return mvc.perform(post("/api/scenarios").session(session).with(csrf()).with(from(address))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private static String scenario() throws Exception {
        JsonCodec codec = new JsonCodec();
        Scenario scenario = codec.read(Path.of("..", "scenarios", "remoto-17-default-soleus-contraction.json"),
                Scenario.class).withDuration(5000);
        return new String(codec.toBytes(scenario), StandardCharsets.UTF_8);
    }
}
