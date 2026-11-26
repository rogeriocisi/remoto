package br.remoto.web.config;

import org.apache.catalina.Host;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.ErrorPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.tomcat.TomcatContextCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * O site descritivo (as páginas de {@code docs/}) na raiz do domínio, servido pelo mesmo Tomcat
 * que serve o simulador.
 *
 * <p>Os dois são um sistema só num domínio só, e os links entre eles são relativos a ele
 * ({@code /pt/} no menu do simulador, {@code simulator/} nas páginas do site). Quem serve o quê é
 * decidido pelo caminho: o simulador tem context-path {@code /simulator} e fica com tudo que
 * comece por aí; o resto é o site. Em desenvolvimento isso faz os dois lados funcionarem em
 * localhost como funcionam no ar, sem servidor nenhum na frente; em produção, o reverse proxy
 * cuida só do TLS e repassa o domínio inteiro.
 *
 * <p>O Tomcat hospeda mais de uma aplicação, e é disso que se trata aqui: ao lado da aplicação do
 * Spring entra um segundo contexto, na raiz, que é só o DefaultServlet servindo uma pasta. Assim o
 * simulador continua exatamente onde está — nada nele muda de endereço, e o site não passa pelos
 * filtros nem pelas páginas de erro dele.
 *
 * <p>A pasta é {@code remoto.site-dir} (REMOTO_SITE_DIR), {@code ./docs} por padrão, que é onde
 * ela está no repositório. Se não existir, o simulador sobe sozinho e a raiz fica sem nada: é o
 * caso de quem roda só a aplicação.
 */
@Configuration(proxyBeanMethods = false)
class SiteConfig {

    private static final Logger log = LoggerFactory.getLogger(SiteConfig.class);

    @Bean
    TomcatContextCustomizer siteOnTheRoot(RemotoProperties props) {
        return context -> {
            Path dir = Path.of(props.siteDir()).toAbsolutePath().normalize();
            if (!Files.isDirectory(dir)) {
                log.info("Site descritivo: {} não existe, a raiz do domínio fica sem nada", dir);
                return;
            }
            if (!(context.getParent() instanceof Host host)) {
                return;
            }
            host.addChild(siteContext(dir));
            log.info("Site descritivo em {} | servido na raiz, ao lado de {}", dir, context.getPath() + "/");
        };
    }

    private static StandardContext siteContext(Path dir) {
        StandardContext site = new StandardContext();
        site.setName("site");
        site.setPath("");                    // a raiz: o que não for do simulador cai aqui
        site.setDocBase(dir.toString());
        site.addLifecycleListener(new Tomcat.FixContextListener());
        Tomcat.addDefaultMimeTypeMappings(site);

        Wrapper files = Tomcat.addServlet(site, "default", new DefaultServlet());
        files.setLoadOnStartup(1);
        files.addMapping("/");
        site.addWelcomeFile("index.html");

        // a mesma página de erro que o GitHub Pages serve no espelho do site
        ErrorPage notFound = new ErrorPage();
        notFound.setErrorCode(404);
        notFound.setLocation("/404.html");
        site.addErrorPage(notFound);
        return site;
    }
}
