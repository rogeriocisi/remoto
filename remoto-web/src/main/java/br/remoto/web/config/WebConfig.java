package br.remoto.web.config;

import br.remoto.web.security.CallerResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/** Permite que controllers recebam {@code Caller caller} como parâmetro. */
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
}
