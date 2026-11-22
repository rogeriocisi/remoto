package br.remoto.web.security;

import br.remoto.storage.UserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Dá a cada sessão do navegador uma área própria, com um id aleatório guardado na sessão. Não há
 * login: a área vale enquanto a sessão durar, e o usuário guarda o que quiser exportando.
 *
 * <p>A origem é o endereço remoto da requisição. Atrás de um proxy reverso, ative
 * {@code server.forward-headers-strategy: native} para que seja o do navegador, e não o do proxy.
 */
@Component
public class CallerResolver implements HandlerMethodArgumentResolver {

    public static final String AREA_ATTRIBUTE = "remoto.areaId";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == Caller.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return resolve(webRequest.getNativeRequest(HttpServletRequest.class));
    }

    public static Caller resolve(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        UserId id;
        if (session.getAttribute(AREA_ATTRIBUTE) instanceof String stored) {
            id = new UserId(stored);
        } else {
            id = UserId.newSession();
            session.setAttribute(AREA_ATTRIBUTE, id.value());
        }
        return new Caller(id, request.getRemoteAddr());
    }
}
