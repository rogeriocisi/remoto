package br.remoto.web.api;

import br.remoto.web.security.Caller;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Set;

/**
 * Identificação da API na descrição OpenAPI ({@code /v3/api-docs}, mostrada pelo Swagger UI em
 * {@code /swagger-ui.html}). O que entra na descrição, e como ela é servida, está em
 * {@code springdoc.*}, no application.yml.
 *
 * <p>Os textos aqui estão em inglês, como as páginas de quem não escolheu idioma: a descrição é
 * uma só, e quem a lê é quem chama a API de fora.
 *
 * <p>Não há esquema de autenticação a declarar: a API não tem login nem contas — cada navegador
 * tem a sua área, identificada por um cookie (ver {@code CallerResolver}).
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    /** Os esquemas que o springdoc gera para os tipos de stream do Spring MVC. */
    private static final Set<String> STREAM_SCHEMAS = Set.of("SseEmitter", "StreamingResponseBody");

    /*
     * O Caller não é um parâmetro de quem chama: o CallerResolver o monta a partir do cookie da
     * área e do endereço da requisição (ver WebConfig). Sem isto, ele apareceria como parâmetro
     * obrigatório em quase toda operação — e, nas de multipart, no lugar do arquivo. Não há
     * propriedade de configuração equivalente; só esta chamada.
     */
    static {
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(Caller.class);
    }

    @Bean
    OpenAPI remotoOpenApi(@Value("${remoto.version:}") String version) {
        return new OpenAPI().info(new Info()
                .title("ReMoto")
                .version(version)
                .description("""
                        API of the spinal cord motor nuclei simulator: submit simulations and \
                        follow them, edit and share scenarios, and download the results as CSV, \
                        PNG or .zip.

                        There is no login: a cookie identifies the caller's area, and each area \
                        only sees its own scenarios and simulations. Errors come back as \
                        `application/problem+json` (RFC 9457).""")
                .license(new License().name("MIT License").url("https://opensource.org/licenses/MIT")));
    }

    /**
     * O que sai de um stream é uma sequência de bytes, não um objeto. Sem isto, as respostas do
     * {@code SseEmitter} (o acompanhamento das simulações) e do {@code StreamingResponseBody}
     * (os .zip de exportação) apareceriam como esquemas desses dois tipos do Spring — um deles
     * vazio, o outro com o campo {@code timeout} —, e não como o conteúdo que o endereço devolve.
     */
    @Bean
    OpenApiCustomizer streamsAreBytes() {
        return openApi -> {
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                if (operation.getResponses() == null) {
                    return;
                }
                operation.getResponses().values().stream()
                        .filter(response -> response.getContent() != null)
                        .flatMap(response -> response.getContent().entrySet().stream())
                        .forEach(OpenApiConfig::unwrapStream);
            }));
            openApi.getComponents().getSchemas().keySet().removeAll(STREAM_SCHEMAS);
        };
    }

    /**
     * Troca o esquema do tipo de stream por uma cadeia de bytes. O {@code text/event-stream} fica
     * como texto, sem {@code format}: o que chega ali são linhas {@code data:}, não um arquivo.
     */
    private static void unwrapStream(Map.Entry<String, MediaType> content) {
        Schema<?> schema = content.getValue().getSchema();
        if (schema == null || schema.get$ref() == null) {
            return;
        }
        String ref = schema.get$ref();
        if (!STREAM_SCHEMAS.contains(ref.substring(ref.lastIndexOf('/') + 1))) {
            return;
        }
        StringSchema bytes = new StringSchema();
        if (!content.getKey().startsWith("text/event-stream")) {
            bytes.format("binary");
        }
        content.getValue().setSchema(bytes);
    }
}
