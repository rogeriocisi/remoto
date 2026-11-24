package br.remoto.web.config;

import br.remoto.web.i18n.LangLinks;
import br.remoto.web.i18n.RemotoLocaleResolver;
import br.remoto.web.security.CallerResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.util.List;

/**
 * Permite que controllers recebam {@code Caller caller} como parâmetro, e escolhe o idioma da
 * página, entre os declarados em {@code remoto.languages} (ver {@link RemotoLocaleResolver}).
 */
@Configuration(proxyBeanMethods = false)
class WebConfig implements WebMvcConfigurer {

    private final CallerResolver callerResolver;

    WebConfig(CallerResolver callerResolver) {
        this.callerResolver = callerResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(callerResolver);
    }

    /** O nome do bean é o que o DispatcherServlet procura. */
    @Bean
    RemotoLocaleResolver localeResolver(RemotoProperties properties) {
        return new RemotoLocaleResolver(properties.languages());
    }

    /** {@code ?lang=pt} / {@code ?lang=en}: o seletor de idioma das páginas. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        LocaleChangeInterceptor lang = new LocaleChangeInterceptor();
        lang.setParamName(LangLinks.PARAM);
        lang.setIgnoreInvalidLocale(true);
        registry.addInterceptor(lang);
    }
}
