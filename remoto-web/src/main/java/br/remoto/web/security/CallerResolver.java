package br.remoto.web.security;

import br.remoto.storage.UserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Converte a autenticação do Spring Security em {@link Caller}.
 *
 * <ul>
 *   <li>OIDC/OAuth2 (ORCID, GitHub...): id = hash(emissor | sub). Nenhuma senha passa pelo ReMoto.</li>
 *   <li>Usuário local (perfil dev): id = hash("local" | username).</li>
 *   <li>Sem login: convidado com id aleatório guardado na sessão.</li>
 * </ul>
 */
@Component
public class CallerResolver implements HandlerMethodArgumentResolver {

    public static final String GUEST_ATTRIBUTE = "remoto.guestId";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == Caller.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        return resolve(SecurityContextHolder.getContext().getAuthentication(), request);
    }

    public static Caller resolve(Authentication auth, HttpServletRequest request) {
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return guest(request.getSession(true));
        }
        if (auth instanceof OAuth2AuthenticationToken token) {
            OAuth2User principal = token.getPrincipal();
            String issuer = principal instanceof OidcUser oidc && oidc.getIssuer() != null
                    ? oidc.getIssuer().toString()
                    : token.getAuthorizedClientRegistrationId();
            String name = firstNonBlank(principal.<String>getAttribute("name"),
                    principal.<String>getAttribute("login"), principal.getName());
            return new Caller(UserId.registered(issuer, principal.getName()), name, false, issuer);
        }
        return new Caller(UserId.registered("local", auth.getName()), auth.getName(), false, "local");
    }

    private static Caller guest(HttpSession session) {
        UserId id;
        if (session.getAttribute(GUEST_ATTRIBUTE) instanceof String stored) {
            id = new UserId(stored);
        } else {
            id = UserId.newGuest();
            session.setAttribute(GUEST_ATTRIBUTE, id.value());
        }
        return new Caller(id, "Convidado", true, null);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "usuário";
    }
}
