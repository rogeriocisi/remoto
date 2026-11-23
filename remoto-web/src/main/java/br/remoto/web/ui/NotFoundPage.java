package br.remoto.web.ui;

import br.remoto.web.i18n.LangLinks;
import br.remoto.web.i18n.Messages;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;

/**
 * Endereço que não existe: para quem navega, a página de erro do site; para a API, o JSON de
 * erro (RFC 9457), como nos outros erros dela.
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE) // antes do tratador de "problem details" do Spring, que responde JSON
class NotFoundPage {

    @ExceptionHandler(NoResourceFoundException.class)
    ModelAndView notFound(NoResourceFoundException e, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (request.getRequestURI().startsWith("/api/")) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            response.setContentType("application/problem+json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"title\":\"Not Found\",\"status\":404,\"detail\":\""
                    + Messages.msg("route.notFound") + "\"}");
            return new ModelAndView();
        }
        ModelAndView mv = new ModelAndView("error");
        mv.setStatus(HttpStatus.NOT_FOUND);
        mv.addObject("status", 404);
        mv.addObject("path", request.getRequestURI());
        mv.addAllObjects(LangLinks.attributes(request)); // o seletor de idioma da página de erro
        return mv;
    }
}
