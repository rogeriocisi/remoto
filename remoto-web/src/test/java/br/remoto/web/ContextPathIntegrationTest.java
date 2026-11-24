package br.remoto.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O simulador é servido em {@code <domínio>/simulator}, e não na raiz do domínio, onde fica o site
 * descritivo ({@code docs/}). Este é o único teste que exercita o {@code context-path} — o
 * {@code MockMvc} dos outros não o aplica —, e portanto o que pega endereço escrito a partir da
 * raiz dentro de uma página, do JavaScript ou de um tratador de erro.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class ContextPathIntegrationTest {

    @TempDir
    static Path data;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("remoto.data-dir", () -> data.toString());
        registry.add("remoto.public-scenarios-dir", () -> Path.of("..", "scenarios").toAbsolutePath().toString());
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path, String... headers) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        for (int i = 0; i < headers.length; i += 2) {
            request.header(headers[i], headers[i + 1]);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** A página sai com todos os seus endereços sob /simulator, inclusive os do htmx. */
    @Test
    void aPaginaTrazOCaminhoBaseEmTodosOsEnderecos() throws Exception {
        HttpResponse<String> response = get("/simulator/");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("href=\"/simulator/css/app.css\"")
                .contains("src=\"/simulator/js/app.js\"")
                .contains("hx-get=\"/simulator/ui/runs\"")
                .contains("hx-get=\"/simulator/ui/scenarios\"")
                .contains("hx-post=\"/simulator/ui/runs/import\"")
                .contains("<link rel=\"canonical\" href=\"/simulator/\">")
                // nenhum endereço da aplicação saindo da raiz do domínio, onde está o site
                .doesNotContain("hx-get=\"/ui/")
                .doesNotContain("hx-post=\"/ui/");
    }

    /** O JavaScript monta os endereços da API com o caminho base que o /js/i18n.js publica. */
    @Test
    void oJavaScriptRecebeOCaminhoBase() throws Exception {
        HttpResponse<String> response = get("/simulator/js/i18n.js");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("window.ReMotoBase = \"/simulator/\"");
    }

    /** A API responde sob o prefixo. */
    @Test
    void aApiRespondeSobOPrefixo() throws Exception {
        HttpResponse<String> response = get("/simulator/api/scenarios/public");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"id\"");
    }

    /**
     * Endereço inexistente da API responde o JSON de erro, e não a página de erro do site: a
     * escolha depende de reconhecer o /api/ depois do context-path.
     */
    @Test
    void oErroDaApiContinuaSendoJson() throws Exception {
        HttpResponse<String> response = get("/simulator/api/nao-existe");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .contains("application/problem+json");
        assertThat(response.body()).doesNotContain("<html");
    }

    /**
     * A porta de entrada em português é /simulator/pt (com ou sem a barra final): a mesma página,
     * com os textos do messages_pt.properties. É o endereço que o site do projeto usa.
     */
    @Test
    void aPortaEmPortuguesFicaEmSimulatorPt() throws Exception {
        for (String path : new String[] {"/simulator/pt", "/simulator/pt/"}) {
            HttpResponse<String> response = get(path, "Accept-Language", "en-US");

            assertThat(response.statusCode()).as(path).isEqualTo(200);
            assertThat(response.body()).as(path)
                    .contains("lang=\"pt-BR\"")
                    .contains("Minhas simulações")
                    .contains("<link rel=\"canonical\" href=\"/simulator/pt/\">")
                    // na porta de entrada, o seletor leva à porta do outro idioma, não a ?lang=
                    .contains("See this page in English");
            assertThat(response.headers().allValues("set-cookie").toString()).as(path)
                    .contains("remoto-lang=pt");
        }
    }

    /**
     * A porta do idioma padrão é /simulator/en, e vale como porta: o seletor dela leva à porta do
     * outro idioma, e não a ?lang= na mesma tela.
     */
    @Test
    void aPortaDoIdiomaPadraoTambemLevaAOutraPorta() throws Exception {
        HttpResponse<String> response = get("/simulator/en", "Accept-Language", "pt-BR");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("No simulation open") // inglês, apesar do Accept-Language
                .contains("<link rel=\"canonical\" href=\"/simulator/\">")
                .contains("hreflang=\"x-default\" href=\"/simulator/\"")
                .contains("href=\"/simulator/pt/\"")
                .doesNotContain("/simulator/en?lang=");
    }

    /**
     * Idioma que não está em remoto.languages não tem porta de entrada, e quem navega recebe a
     * página de erro do site, e não o JSON de erro, que é só da API.
     */
    @Test
    void idiomaNaoDeclaradoNaoTemPorta() throws Exception {
        HttpResponse<String> response = get("/simulator/zz");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("text/html");
        assertThat(response.body()).contains("<html");
    }

    /** A raiz do domínio não é a aplicação: ali o servidor da frente serve o site descritivo. */
    @Test
    void aRaizDoDominioNaoEhAAplicacao() throws Exception {
        assertThat(get("/").statusCode()).isEqualTo(404);
        assertThat(get("/ui/runs").statusCode()).isEqualTo(404);
    }
}
