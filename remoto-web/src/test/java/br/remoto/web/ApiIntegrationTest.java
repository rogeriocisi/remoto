package br.remoto.web;

import br.remoto.core.engine.legacy.LegacyEngine;
import br.remoto.core.scenario.Scenario;
import br.remoto.storage.JsonCodec;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fluxos multiusuário de ponta a ponta, com o armazenamento em arquivos num diretório temporário. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ApiIntegrationTest {

    @TempDir
    static Path data;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("remoto.data-dir", () -> data.toString());
        registry.add("remoto.public-scenarios-dir", () -> Path.of("..", "scenarios").toAbsolutePath().toString());
    }

    private static final JsonCodec CODEC = new JsonCodec();
    /** Um cenário público, encurtado para 20 ms. */
    private static final Scenario BASE = CODEC.read(Path.of("..", "scenarios", "remoto-17-default-soleus-contraction.json"),
            Scenario.class).withDuration(20).withSeed(7);
    private static final String SCENARIO = json("rápido", BASE.durationMs(), BASE.engine());

    @Autowired
    MockMvc mvc;

    @Test
    void convidadoVeOsCenariosPublicos() throws Exception {
        mvc.perform(get("/api/scenarios/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem("remoto-17-default-soleus-contraction")));
    }

    @Test
    void cenarioDeUmUsuarioNaoApareceParaOutro() throws Exception {
        String id = createScenarioAs("ana");

        mvc.perform(get("/api/scenarios/" + id).with(user("ana"))).andExpect(status().isOk());
        mvc.perform(get("/api/scenarios/" + id).with(user("bruno"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/scenarios").with(user("bruno"))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void edicaoComEtagDesatualizadoEhRecusada() throws Exception {
        var created = mvc.perform(post("/api/scenarios").with(user("ana")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(SCENARIO))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn().getResponse();
        String id = JsonPath.read(created.getContentAsString(), "$.id");
        String etag = created.getHeader(HttpHeaders.ETAG);

        mvc.perform(put("/api/scenarios/" + id).with(user("ana")).with(csrf()).header(HttpHeaders.IF_MATCH, etag)
                        .contentType(MediaType.APPLICATION_JSON).content(json("editado", BASE.durationMs(), BASE.engine())))
                .andExpect(status().isOk());
        mvc.perform(put("/api/scenarios/" + id).with(user("ana")).with(csrf()).header(HttpHeaders.IF_MATCH, etag)
                        .contentType(MediaType.APPLICATION_JSON).content(SCENARIO))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void cenarioInvalidoDevolveAsMensagens() throws Exception {
        mvc.perform(post("/api/scenarios").with(user("ana")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("rápido", -1, BASE.engine())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros", hasItem("durationMs: deve ser maior que zero")));
    }

    @Test
    void gravacaoSemTokenCsrfEhRecusada() throws Exception {
        mvc.perform(post("/api/scenarios").with(user("ana")).contentType(MediaType.APPLICATION_JSON).content(SCENARIO))
                .andExpect(status().isForbidden());
    }

    @Test
    void convidadoRodaSimulacaoEBaixaResultadosSoNaPropriaSessao() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String body = mvc.perform(post("/api/runs").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"scenario\":" + SCENARIO + "}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.engineVersion").value(LegacyEngine.VERSION))
                .andReturn().getResponse().getContentAsString();
        String runId = JsonPath.read(body, "$.runId");

        assertThat(waitUntilFinished(session, runId)).isEqualTo("DONE");
        mvc.perform(get("/api/runs/" + runId).session(session))
                .andExpect(jsonPath("$.results", hasItem("forca-SOL")))
                .andExpect(jsonPath("$.results", hasItem("torque")));
        mvc.perform(get("/api/runs/" + runId + "/results/forca-SOL.csv").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(startsWith("t_ms,valor")));
        mvc.perform(get("/api/runs/" + runId + "/results/forca-SOL.png").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
        mvc.perform(get("/api/runs/" + runId + "/results/forca-SOL/preview?max=100").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPoints").value(400))
                .andExpect(jsonPath("$.x", hasSize(100)));
        mvc.perform(get("/api/runs/" + runId + "/results/..%2F..%2Fmanifest.csv").session(session))
                .andExpect(status().is4xxClientError());
        mvc.perform(get("/api/runs/" + runId + "/menu").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.types[*].value", hasItem("neural")));
        mvc.perform(get("/api/runs/" + runId + "/view").session(session)
                        .param("type", "neural").param("element", "mns").param("nucleus", "SOL")
                        .param("neuron", "todos").param("graphic", "spikeTimes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("pontos"));

        // outro convidado (outra sessão) e um usuário autenticado não enxergam a execução
        mvc.perform(get("/api/runs/" + runId).session(new MockHttpSession())).andExpect(status().isNotFound());
        mvc.perform(get("/api/runs/" + runId).with(user("ana"))).andExpect(status().isNotFound());
    }

    @Test
    void motorDesconhecidoEhRecusado() throws Exception {
        mvc.perform(post("/api/runs").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":" + json("rápido", BASE.durationMs(), "outro") + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void convidadoNaoPodeExportarNemCompartilhar() throws Exception {
        mvc.perform(get("/api/me/export")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me")).andExpect(jsonPath("$.guest").value(true));
    }

    private static String json(String name, double durationMs, String engine) {
        Scenario s = new Scenario(null, name, "", Scenario.CURRENT_SCHEMA_VERSION, BASE.seed(), durationMs,
                BASE.stepMs(), engine, BASE.legacy());
        return new String(CODEC.toBytes(s), StandardCharsets.UTF_8);
    }

    private String createScenarioAs(String username) throws Exception {
        String body = mvc.perform(post("/api/scenarios").with(user(username)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(SCENARIO))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String waitUntilFinished(MockHttpSession session, String runId) throws Exception {
        String status = "";
        for (int i = 0; i < 600; i++) {
            String body = mvc.perform(get("/api/runs/" + runId).session(session)).andReturn().getResponse().getContentAsString();
            status = JsonPath.read(body, "$.run.status");
            if (status.equals("DONE") || status.equals("FAILED") || status.equals("CANCELED")) {
                return status;
            }
            Thread.sleep(50);
        }
        return status;
    }
}
