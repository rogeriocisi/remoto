package br.remoto.web;

import br.remoto.core.scenario.Scenario;
import br.remoto.core.engine.legacy.LegacyConfig;
import br.remoto.core.engine.legacy.LegacyJson;
import br.remoto.storage.JsonCodec;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Editor de cenários (telas do site antigo) de ponta a ponta. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class EditorIntegrationTest {

    @TempDir
    static Path data;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("remoto.data-dir", () -> data.toString());
        registry.add("remoto.public-scenarios-dir", () -> Path.of("..", "scenarios").toAbsolutePath().toString());
    }

    @Autowired
    MockMvc mvc;

    private final JsonCodec json = new JsonCodec();

    @Test
    void editaAQuantidadeDeMotoneuroniosComoNoSiteAntigo() throws Exception {
        String id = copyAs("ana");
        String etag = etag("ana", id);

        mvc.perform(get("/editor/" + id).param("screen", "network").session(session("ana")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Neural Elements")))
                .andExpect(content().string(containsString("SOL — S (motoneurons)")));

        mvc.perform(post("/editor/" + id).session(session("ana")).with(csrf())
                        .param("screen", "network").param("etag", etag)
                        .param("p0.i0.active", "true").param("p0.i0.quantity", "100"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/editor/" + id + "?*saved=1*"));

        assertThat(quantity(scenario("ana", id), "SOL", "S")).isEqualTo(100);
    }

    @Test
    void edicaoComEtagAntigoNaoSobrescreve() throws Exception {
        String id = copyAs("ana");
        String etag = etag("ana", id);
        mvc.perform(post("/editor/" + id).session(session("ana")).with(csrf())
                        .param("screen", "general").param("etag", etag).param("name", "primeira").param("seed", "1"))
                .andExpect(status().is3xxRedirection());

        mvc.perform(post("/editor/" + id).session(session("ana")).with(csrf())
                        .header("Accept-Language", "pt-BR")
                        .param("screen", "general").param("etag", etag).param("name", "segunda").param("seed", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("alterado em outra aba")));

        assertThat(scenario("ana", id).name()).isEqualTo("primeira");
    }

    @Test
    void paginaDePublicacoesEIconeDoSite() throws Exception {
        mvc.perform(get("/publicacoes").header("Accept-Language", "pt-BR"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Publicações")))
                .andExpect(content().string(containsString("https://doi.org/10.1007/s10827-008-0092-8")));
        mvc.perform(get("/favicon.ico")).andExpect(status().isOk());
        mvc.perform(get("/img/remoto-icon.svg")).andExpect(status().isOk());
    }

    @Test
    void enderecoInexistenteMostraAPaginaDeErroEAApiDevolveJson() throws Exception {
        mvc.perform(get("/nao-existe").session(session("ana")).header("Accept-Language", "pt-BR"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Não encontrado")));
        mvc.perform(get("/api/nao-existe").session(session("ana")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void outroUsuarioNaoAbreOEditor() throws Exception {
        String id = copyAs("ana");

        // o idioma vem do navegador (aqui, português); ver LanguageIntegrationTest
        mvc.perform(get("/editor/" + id).session(session("bruno")).header("Accept-Language", "pt-BR"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Não encontrado")))
                .andExpect(content().string(containsString("Voltar à página inicial")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Whitelabel"))));
        // pedido do htmx: só a mensagem, que a página mostra num aviso
        mvc.perform(get("/editor/" + id).session(session("bruno")).header("HX-Request", "true")
                        .header("Accept-Language", "pt-BR"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("cenário não encontrado")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("<html"))));
    }

    @Test
    void numeroInvalidoMostraOErroSemGravar() throws Exception {
        String id = copyAs("ana");
        mvc.perform(post("/editor/" + id).session(session("ana")).with(csrf())
                        .param("screen", "motoneuron").param("sel.pool", "SOL").param("etag", etag("ana", id))
                        .param("p0.i0.threshold1", "abc"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Soma threshold")));
    }

    @Test
    void seletoresDaTelaVemDaUrl() throws Exception {
        String id = copyAs("ana");

        mvc.perform(get("/editor/" + id).param("screen", "conductance").param("pool", "SOL").param("type", "FR")
                        .session(session("ana")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("gNaM-FR")));
    }

    @Test
    void editarUmCenarioPublicoAbreOEditorNumaCopia() throws Exception {
        // o Editar dos públicos é um formulário comum, que leva o token CSRF
        mvc.perform(get("/ui/scenarios").session(session("bruno")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/ui/scenarios/edit-copy\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));

        MvcResult r = mvc.perform(post("/ui/scenarios/edit-copy").session(session("bruno")).with(csrf())
                        .param("publicScenarioId", "remoto-17-default-soleus-contraction"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/editor/*"))
                .andReturn();
        String id = r.getResponse().getRedirectedUrl().substring("/editor/".length());

        mvc.perform(get("/editor/" + id).session(session("bruno")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Default - Soleus contraction")));
        assertThat(scenario("bruno", id).name()).isEqualTo("Default - Soleus contraction");

        // cenário público inexistente volta para a página inicial, sem criar nada
        mvc.perform(post("/ui/scenarios/edit-copy").session(session("bruno")).with(csrf())
                        .param("publicScenarioId", "nao-existe"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    private String copyAs(String username) throws Exception {
        MvcResult r = mvc.perform(post("/api/scenarios/public/remoto-17-default-soleus-contraction/copy")
                        .session(session(username)).with(csrf()))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(), "$.id");
    }

    private String etag(String username, String id) throws Exception {
        String header = mvc.perform(get("/api/scenarios/" + id).session(session(username)))
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        return header == null ? null : header.replace("\"", "");
    }

    private Scenario scenario(String username, String id) throws Exception {
        byte[] body = mvc.perform(get("/api/scenarios/" + id).session(session(username)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
        return json.fromBytes(body, Scenario.class);
    }

    private static int quantity(Scenario s, String nucleus, String type) {
        LegacyConfig.Table t = LegacyJson.toConfig(s.legacy()).tables().get("NEURON_TYPE");
        int n = t.columns().indexOf("CD_NUCLEUS");
        int ty = t.columns().indexOf("CD_NEURON_TYPE");
        int q = t.columns().indexOf("QUANTITY");
        return t.rows().stream().filter(r -> nucleus.equals(r.get(n)) && type.equals(r.get(ty)))
                .map(r -> ((Number) r.get(q)).intValue()).findFirst().orElseThrow();
    }

    /** Uma sessão do navegador por nome, como se cada pessoa usasse o seu (não há login). */
    private final java.util.Map<String, MockHttpSession> sessions = new java.util.HashMap<>();

    private MockHttpSession session(String who) {
        return sessions.computeIfAbsent(who, k -> new MockHttpSession());
    }
}
