package br.remoto.web.api;

import br.remoto.web.i18n.Messages;
import jakarta.servlet.http.HttpServletRequest;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * A marca do ReMoto no alto do Swagger UI, no lugar da barra preta do Swagger: o logotipo e o nome
 * levam de volta ao simulador, e o título "ReMoto" que o Swagger desenha a partir do {@code info}
 * vira link para o mesmo lugar.
 *
 * <p>A página do Swagger UI vem pronta dentro do webjar, e por isso o jeito de mexer nela é este:
 * o springdoc já passa o index.html por um {@link SwaggerIndexTransformer} (é assim que ele injeta
 * a configuração e o CSRF), e aqui o que ele devolve ganha um cabeçalho, um estilo e um script
 * curto. Nada é copiado do webjar para o repositório, então atualizar o springdoc não deixa uma
 * cópia velha para trás.
 */
@Configuration(proxyBeanMethods = false)
class SwaggerUiBranding {

    @Bean
    SwaggerIndexTransformer remotoSwaggerIndexTransformer(SwaggerUiConfigProperties swaggerUiConfig,
                                                          SwaggerUiOAuthProperties swaggerUiOAuthProperties,
                                                          SwaggerWelcomeCommon swaggerWelcomeCommon,
                                                          ObjectMapperProvider objectMapperProvider) {
        return new BrandedIndexTransformer(swaggerUiConfig, swaggerUiOAuthProperties, swaggerWelcomeCommon,
                objectMapperProvider);
    }

    /**
     * O transformador padrão do springdoc mexe no {@code swagger-initializer.js} e deixa o
     * {@code index.html} passar; este cuida do index e delega o resto.
     */
    private static final class BrandedIndexTransformer extends SwaggerIndexPageTransformer {

        private BrandedIndexTransformer(SwaggerUiConfigProperties swaggerUiConfig,
                                        SwaggerUiOAuthProperties swaggerUiOAuthProperties,
                                        SwaggerWelcomeCommon swaggerWelcomeCommon,
                                        ObjectMapperProvider objectMapperProvider) {
            super(swaggerUiConfig, swaggerUiOAuthProperties, swaggerWelcomeCommon, objectMapperProvider);
        }

        @Override
        public Resource transform(HttpServletRequest request, Resource resource, ResourceTransformerChain chain)
                throws IOException {
            Resource transformed = super.transform(request, resource, chain);
            if (!"index.html".equals(transformed.getFilename())) {
                return transformed;
            }
            String html = new String(transformed.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String branded = brand(html, request.getContextPath());
            return new TransformedResource(transformed, branded.getBytes(StandardCharsets.UTF_8));
        }

        /** O simulador e os ícones ficam na raiz do context-path, ao lado de /swagger-ui/. */
        private static String brand(String html, String contextPath) {
            String simulator = contextPath + "/";
            return html
                    .replace("<title>Swagger UI</title>", "<title>ReMoto &mdash; API</title>")
                    .replace("</head>", head(contextPath) + "  </head>")
                    .replace("<div id=\"swagger-ui\"></div>", header(simulator) + "    <div id=\"swagger-ui\"></div>")
                    .replace("</body>", script(simulator) + "  </body>");
        }

        private static String head(String contextPath) {
            return """
                        <link rel="icon" type="image/svg+xml" href="%1$s/img/remoto-icon.svg" />
                        <style>
                          /* a barra preta do Swagger sai: quem identifica a página é a marca do ReMoto */
                          .swagger-ui .topbar { display: none; }
                          /* as mesmas medidas e cores do cabeçalho do simulador (app.css) */
                          .remoto-topo {
                            display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
                            min-height: 56px; padding: 8px 20px;
                            border-bottom: 1px solid #dde2e8; background: #ffffff;
                            font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
                          }
                          .remoto-topo .remoto-marca {
                            display: inline-flex; align-items: center; gap: 8px;
                            color: #1b1f24; text-decoration: none;
                          }
                          .remoto-topo .remoto-marca:hover strong { text-decoration: underline; }
                          .remoto-topo strong { font-size: 1.25rem; letter-spacing: -.01em; }
                          .remoto-topo .remoto-sub { color: #5b6470; font-size: .9rem; }
                          /* a barra de lugares, com as medidas dos botões do simulador */
                          .remoto-topo .remoto-lugares {
                            display: flex; align-items: center; gap: 6px; flex-wrap: wrap; margin-left: auto;
                          }
                          .remoto-topo .remoto-lugares a {
                            color: #1b1f24; text-decoration: none; font-size: .9rem;
                            padding: 4px 10px; border: 1px solid #dde2e8; border-radius: 4px;
                            background: #ffffff;
                          }
                          .remoto-topo .remoto-lugares a:hover { border-color: #1f5f99; color: #1f5f99; }
                          .remoto-topo .remoto-lugares a.atual {
                            background: #eaf2fa; border-color: #c7dcef; font-weight: 600; cursor: default;
                          }
                          /* o título que o Swagger monta a partir do info, agora clicável */
                          .swagger-ui .info .title a.remoto-link { color: inherit; text-decoration: none; }
                          .swagger-ui .info .title a.remoto-link:hover { text-decoration: underline; }
                        </style>
                    """.formatted(contextPath);
        }

        /**
         * O cabeçalho: a marca e a mesma barra de lugares do site, do guia e do simulador, para
         * que a página da API não seja o único canto do ReMoto de onde não se sai. Os endereços
         * do site e do guia saem das chaves {@code nav.*}, que têm uma versão por idioma, e o
         * idioma é o da requisição.
         */
        private static String header(String simulator) {
            return """
                        <header class="remoto-topo">
                          <a class="remoto-marca" href="%11$s" title="%2$s">
                            <img src="%1$simg/remoto-icon.svg" alt="" width="30" height="30">
                            <strong>ReMoto</strong>
                          </a>
                          <nav class="remoto-lugares" aria-label="ReMoto">
                            <a href="%3$s">%4$s</a>
                            <a href="%5$s">%6$s</a>
                            <a href="%1$s">%7$s</a>
                            <a class="atual" aria-current="page">%8$s</a>
                            <a href="%9$s" target="_blank" rel="noopener">%10$s</a>
                          </nav>
                        </header>
                    """.formatted(simulator, Messages.msg("nav.site"),
                    Messages.msg("nav.site.url"), Messages.msg("nav.site"),
                    Messages.msg("nav.guide.url"), Messages.msg("nav.guide"),
                    Messages.msg("nav.simulator"), Messages.msg("nav.api"),
                    Messages.msg("nav.repository.url"), Messages.msg("nav.repository"),
                    Messages.msg("nav.site.url"));
        }

        /**
         * O título vem do React do Swagger UI, e por isso é ligado depois que ele desenha; o
         * observador cobre o redesenho (trocar de documento, por exemplo) e não repete o link.
         */
        private static String script(String simulator) {
            return """
                        <script>
                          (function () {
                            var simulator = "%1$s";
                            function linkTitle() {
                              var title = document.querySelector(".swagger-ui .info .title");
                              if (!title) return;
                              var first = title.firstChild;
                              if (!first || first.nodeType !== Node.TEXT_NODE || !first.textContent.trim()) return;
                              var link = document.createElement("a");
                              link.className = "remoto-link";
                              link.href = simulator;
                              link.title = "Back to the simulator";
                              link.textContent = first.textContent;
                              title.replaceChild(link, first);
                            }
                            new MutationObserver(linkTitle).observe(document.body, { childList: true, subtree: true });
                            linkTitle();
                          })();
                        </script>
                    """.formatted(simulator);
        }
    }
}
