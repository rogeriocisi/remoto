package br.remoto.web.security;

import br.remoto.storage.UserId;
import br.remoto.storage.area.AreaRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Duration;
import java.util.Optional;

/**
 * Dá a cada navegador uma área própria. Não há login: a área é identificada por um id aleatório
 * num cookie ({@value #COOKIE}) que dura {@link #COOKIE_AGE} e se renova a cada visita, de modo
 * que cenários e simulações sobrevivem a reinícios do servidor e a fechar o navegador. A área só é
 * apagada depois de um tempo sem uso (limpeza diária) ou quando a pessoa pede.
 *
 * <p>O id também fica na sessão, para quem bloqueia cookies continuar com a mesma área enquanto a
 * sessão durar. A origem é o endereço remoto da requisição; atrás de um proxy reverso, ative
 * {@code server.forward-headers-strategy: native} para que seja o do navegador.
 */
@Component
public class CallerResolver implements HandlerMethodArgumentResolver {

    public static final String COOKIE = "remoto-area";
    public static final String AREA_ATTRIBUTE = "remoto.areaId";
    public static final Duration COOKIE_AGE = Duration.ofDays(30);

    private final AreaRepository areas;

    CallerResolver(AreaRepository areas) {
        this.areas = areas;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == Caller.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return resolve(webRequest.getNativeRequest(HttpServletRequest.class),
                webRequest.getNativeResponse(HttpServletResponse.class));
    }

    public Caller resolve(HttpServletRequest request, HttpServletResponse response) {
        UserId id = fromCookie(request).or(() -> fromSession(request)).orElseGet(UserId::newSession);
        HttpSession session = request.getSession(true);
        if (!id.value().equals(session.getAttribute(AREA_ATTRIBUTE))) {
            session.setAttribute(AREA_ATTRIBUTE, id.value());
        }
        if (response != null && !response.isCommitted()) {
            response.addHeader(HttpHeaders.SET_COOKIE, cookie(id.value(), COOKIE_AGE, request.isSecure()).toString());
        }
        areas.touch(id);
        return new Caller(id, request.getRemoteAddr());
    }

    /** Esquece a área neste navegador (depois de apagá-la). */
    public static void clear(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO, request.isSecure()).toString());
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    private static ResponseCookie cookie(String value, Duration maxAge, boolean secure) {
        return ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private static Optional<UserId> fromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (Cookie c : request.getCookies()) {
            if (COOKIE.equals(c.getName())) {
                Optional<UserId> id = parse(c.getValue());
                if (id.isPresent()) {
                    return id;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<UserId> fromSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute(AREA_ATTRIBUTE) instanceof String s ? parse(s) : Optional.empty();
    }

    /** Um valor adulterado não vira caminho: o {@code UserId} só aceita o formato que ele mesmo gera. */
    private static Optional<UserId> parse(String value) {
        try {
            return Optional.of(new UserId(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
