package br.remoto.web.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Deixa o navegador guardar os arquivos estáticos do simulador (CSS, JavaScript, imagens).
 *
 * <p>Sem isto, o Spring Security marca toda resposta com {@code no-store}, e cada visita baixa
 * tudo de novo. Com {@code no-cache} o navegador guarda o arquivo e, a cada uso, só pergunta se
 * mudou: quase sempre a resposta é um 304 sem corpo, e depois de um deploy ele recebe o arquivo
 * novo na hora — não há prazo de validade para esperar. O Spring Security não escreve os
 * cabeçalhos de cache quando a resposta já traz um.
 *
 * <p>O {@code /js/i18n.js} fica de fora: é montado a cada pedido, no idioma de quem pede.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class StaticCacheFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isStatic(request)) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        }
        chain.doFilter(request, response);
    }

    private static boolean isStatic(HttpServletRequest request) {
        if (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/js/i18n.js")) {
            return false;
        }
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/img/")
                || path.equals("/favicon.ico");
    }
}
