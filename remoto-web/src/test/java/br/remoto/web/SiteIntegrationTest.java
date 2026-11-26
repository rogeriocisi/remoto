package br.remoto.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O mesmo Tomcat serve as duas coisas: o site descritivo na raiz do domínio e o simulador em
 * {@code /simulator} (ver {@code SiteConfig}). As páginas aqui são de mentira, de propósito: o que
 * se exercita é quem responde a cada endereço, e não o conteúdo de {@code docs/}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class SiteIntegrationTest {

    @TempDir
    static Path data;

    @TempDir
    static Path site;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // antes de o contexto subir: o Tomcat só serve a pasta do site se ela já existir
        write("index.html", "<h1>a raiz</h1>");
        write("404.html", "<h1>essa pagina nao existe</h1>");
        write("assets/site.css", "body { color: red }");
        write("pt/index.html", "<h1>a raiz em portugues</h1>");
        registry.add("remoto.data-dir", () -> data.toString());
        registry.add("remoto.public-scenarios-dir", () -> Path.of("..", "scenarios").toAbsolutePath().toString());
        registry.add("remoto.site-dir", () -> site.toString());
    }

    private static void write(String path, String content) {
        try {
            Path file = site.resolve(path);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void aRaizEOSite() throws Exception {
        HttpResponse<String> home = get("/");
        assertThat(home.statusCode()).isEqualTo(200);
        assertThat(home.body()).contains("a raiz");

        // a pasta de um idioma responde com o index dela
        HttpResponse<String> pt = get("/pt/");
        assertThat(pt.statusCode()).isEqualTo(200);
        assertThat(pt.body()).contains("a raiz em portugues");
    }

    @Test
    void osArquivosDoSiteSaemComOTipoCerto() throws Exception {
        HttpResponse<String> css = get("/assets/site.css");
        assertThat(css.statusCode()).isEqualTo(200);
        assertThat(css.headers().firstValue("content-type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("text/css"));
    }

    @Test
    void oQueNaoExisteNoSiteCaiNaPaginaDeErroDele() throws Exception {
        HttpResponse<String> missing = get("/nao-existe");
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(missing.body()).contains("essa pagina nao existe");
    }

    @Test
    void oSimuladorContinuaNoLugar() throws Exception {
        HttpResponse<String> app = get("/simulator/");
        assertThat(app.statusCode()).isEqualTo(200);
        assertThat(app.body()).contains("ReMoto");

        // e a API dele também, que é o que separa um contexto do outro
        assertThat(get("/simulator/api/me").statusCode()).isEqualTo(200);
    }
}
