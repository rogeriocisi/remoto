package br.remoto.web;

import jakarta.servlet.http.Cookie;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O site sai em português ou em inglês: pelo idioma do navegador e, se a pessoa escolher no
 * seletor, pela escolha dela (cookie {@code remoto-lang}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class LanguageIntegrationTest {

    @TempDir
    static Path data;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("remoto.data-dir", () -> data.toString());
        registry.add("remoto.public-scenarios-dir", () -> Path.of("..", "scenarios").toAbsolutePath().toString());
    }

    @Autowired
    MockMvc mvc;

    @Test
    void oIdiomaDoNavegadorEscolheAPagina() throws Exception {
        mvc.perform(get("/").header(HttpHeaders.ACCEPT_LANGUAGE, "pt-BR,pt;q=0.9,en;q=0.8"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("lang=\"pt-BR\"")))
                .andExpect(content().string(containsString("Nenhuma simulação aberta")))
                .andExpect(content().string(containsString("Publicações")));

        mvc.perform(get("/").header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9"))
                .andExpect(content().string(containsString("lang=\"en\"")))
                .andExpect(content().string(containsString("No simulation open")))
                .andExpect(content().string(not(containsString("Nenhuma simulação"))));

        // português de Portugal também é português
        mvc.perform(get("/").header(HttpHeaders.ACCEPT_LANGUAGE, "pt-PT"))
                .andExpect(content().string(containsString("Nenhuma simulação aberta")));

        // qualquer outro idioma, e um navegador que não diz nada: inglês
        mvc.perform(get("/").header(HttpHeaders.ACCEPT_LANGUAGE, "fr-FR,de;q=0.8"))
                .andExpect(content().string(containsString("No simulation open")));
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("No simulation open")));
        mvc.perform(get("/").header(HttpHeaders.ACCEPT_LANGUAGE, "???"))
                .andExpect(content().string(containsString("No simulation open")));
    }

    @Test
    void oSeletorGuardaAEscolhaEManteveOndeAPessoaEsta() throws Exception {
        // ?lang=pt vale já nesta resposta e grava o cookie de um ano
        Cookie chosen = mvc.perform(get("/").param("lang", "pt").header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(content().string(containsString("Nenhuma simulação aberta")))
                .andExpect(cookie().value("remoto-lang", "pt"))
                .andExpect(cookie().maxAge("remoto-lang", 365 * 24 * 60 * 60))
                .andExpect(cookie().httpOnly("remoto-lang", true))
                // há mais de um Set-Cookie na resposta (o da área, o do CSRF)
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE,
                        hasItem(containsString("remoto-lang=pt; Path=/; Max-Age=31536000"))))
                .andReturn().getResponse().getCookie("remoto-lang");

        // a escolha vence o idioma do navegador nas visitas seguintes
        mvc.perform(get("/").cookie(chosen).header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(content().string(containsString("Nenhuma simulação aberta")));
        // e o seletor leva de volta ao inglês, na mesma página, mantendo os outros parâmetros
        // (a query vai na própria URL: com .param() o MockMvc não preenche a query string)
        mvc.perform(get("/publicacoes?x=1").cookie(chosen))
                .andExpect(content().string(containsString("/publicacoes?x=1&amp;lang=en")));

        // um cookie com qualquer outra coisa não quebra nada: vale o navegador
        mvc.perform(get("/").cookie(new Cookie("remoto-lang", "../etc")).header(HttpHeaders.ACCEPT_LANGUAGE, "pt-BR"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nenhuma simulação aberta")));
        mvc.perform(get("/").param("lang", "xx").header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("remoto-lang"))
                .andExpect(content().string(containsString("No simulation open")));
    }

    @Test
    void osTextosDoNavegadorVaoNoIdiomaDaPagina() throws Exception {
        mvc.perform(get("/js/i18n.js").header(HttpHeaders.ACCEPT_LANGUAGE, "pt-BR"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/javascript"))
                .andExpect(content().string(containsString("window.ReMotoLang = \"pt\"")))
                .andExpect(content().string(containsString("\"status.DONE\": \"concluída\"")))
                .andExpect(content().string(containsString("window.T =")));

        mvc.perform(get("/js/i18n.js").header(HttpHeaders.ACCEPT_LANGUAGE, "en-GB"))
                .andExpect(content().string(containsString("\"status.DONE\": \"done\"")))
                .andExpect(content().string(not(containsString("concluída"))));
    }

    /** A versão vem do pom (filtro de recursos do Maven) e o rótulo dela, do idioma. */
    @Test
    void oCabecalhoMostraAVersao() throws Exception {
        mvc.perform(get("/").header(HttpHeaders.ACCEPT_LANGUAGE, "pt-BR"))
                .andExpect(content().string(containsString("Versão 4.0")))
                .andExpect(content().string(not(containsString("project.version"))));
        mvc.perform(get("/publicacoes").header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(content().string(containsString("Version 4.0")));
    }

    /** A página de erro também sai traduzida, com o seletor de idioma. */
    @Test
    void aPaginaDeErroSegueOIdioma() throws Exception {
        mvc.perform(get("/nao-existe").header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Not found")))
                .andExpect(content().string(containsString("lang=\"en\"")))
                .andExpect(content().string(containsString("lang=pt"))); // o seletor, para o outro idioma

        mvc.perform(get("/nao-existe").header(HttpHeaders.ACCEPT_LANGUAGE, "pt-BR"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Não encontrado")));

        // a API responde JSON, não a página
        mvc.perform(get("/api/nao-existe").header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("route not found")));
    }
}
