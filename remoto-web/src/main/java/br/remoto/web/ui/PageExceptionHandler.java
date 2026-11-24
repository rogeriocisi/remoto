package br.remoto.web.ui;

import br.remoto.web.i18n.LangLinks;
import br.remoto.web.i18n.Messages;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

import java.util.NoSuchElementException;

/**
 * Erros das páginas (a principal e o editor). Numa navegação comum, a página de erro do site
 * ({@code error.html}); num pedido do htmx (as listas que se atualizam sozinhas, os botões),
 * só a mensagem, que a página mostra num aviso. Erros inesperados ficam no log com o stack trace.
 * A API tem o seu próprio tratamento, em JSON ({@code ApiExceptionHandler}).
 */
@ControllerAdvice(basePackageClasses = PageExceptionHandler.class)
class PageExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PageExceptionHandler.class);

    private final LangLinks langLinks;

    PageExceptionHandler(LangLinks langLinks) {
        this.langLinks = langLinks;
    }

    @ExceptionHandler(NoSuchElementException.class)
    ModelAndView notFound(NoSuchElementException e, HttpServletRequest request) {
        return page(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ModelAndView badRequest(IllegalArgumentException e, HttpServletRequest request) {
        return page(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ModelAndView tooLarge(MaxUploadSizeExceededException e, HttpServletRequest request) {
        return page(HttpStatus.CONTENT_TOO_LARGE, Messages.msg("upload.tooLarge"), request);
    }

    /** Erros do próprio Spring MVC (parâmetro faltando, método errado...) mantêm o status deles. */
    @ExceptionHandler(Exception.class)
    ModelAndView unexpected(Exception e, HttpServletRequest request) {
        if (e instanceof ErrorResponse response && !response.getStatusCode().is5xxServerError()) {
            return page(response.getStatusCode(), null, request);
        }
        log.error("Erro inesperado em {} {}", request.getMethod(), request.getRequestURI(), e);
        return page(HttpStatus.INTERNAL_SERVER_ERROR, null, request);
    }

    private ModelAndView page(HttpStatusCode status, String message, HttpServletRequest request) {
        boolean htmx = "true".equals(request.getHeader("HX-Request"));
        ModelAndView mv = new ModelAndView(htmx ? "fragments :: erroHtmx" : "error");
        mv.setStatus(status);
        mv.addObject("status", status.value());
        mv.addObject("message", message);
        mv.addObject("path", request.getRequestURI());
        mv.addAllObjects(langLinks.attributes(request)); // o seletor de idioma da página de erro
        return mv;
    }
}
