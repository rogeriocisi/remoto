package br.remoto.web.i18n;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

/**
 * Os textos da interface no idioma da requisição ({@code messages.properties} em inglês e
 * {@code messages_pt.properties} em português).
 *
 * <p>O acesso é estático porque boa parte dos textos nasce em objetos comuns, que não são beans do
 * Spring e são criados a cada pedido: os campos do editor ({@code Field}) e as vistas da tela de
 * resultados ({@code ResultViews}). Passar um {@code MessageSource} até lá acrescentaria um
 * parâmetro a dezenas de métodos. O idioma vem do {@link LocaleContextHolder}, que o Spring MVC
 * preenche por requisição a partir do {@link RemotoLocaleResolver}.
 *
 * <p>Fora do Spring (num teste de unidade), o pacote é lido direto dos arquivos, de modo que o
 * texto é o mesmo. Uma chave sem tradução não derruba o pedido: fica no log e aparece como ela
 * mesma.
 */
@Component
public final class Messages {

    private static final Logger log = LoggerFactory.getLogger(Messages.class);

    private static volatile MessageSource source;

    /** O Spring injeta o {@code MessageSource} da aplicação ao subir. */
    Messages(MessageSource messageSource) {
        source = messageSource;
    }

    public static String msg(String key, Object... args) {
        try {
            return current().getMessage(key, args, LocaleContextHolder.getLocale());
        } catch (NoSuchMessageException e) {
            log.warn("Texto sem tradução: {}", key);
            return key;
        }
    }

    private static MessageSource current() {
        MessageSource s = source;
        if (s == null) {
            s = standalone();
            source = s;
        }
        return s;
    }

    private static MessageSource standalone() {
        ResourceBundleMessageSource s = new ResourceBundleMessageSource();
        s.setBasename("messages");
        s.setDefaultEncoding("UTF-8");
        s.setFallbackToSystemLocale(false);
        return s;
    }
}
