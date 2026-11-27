package br.remoto.web.api;

import br.remoto.web.config.RemotoProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * O que as páginas do site descritivo, que são estáticas, precisam saber da instalação.
 *
 * <p>Hoje é só o endereço de contato. O site monta {@code remoto@<domínio>} a partir do domínio em
 * que está servido ({@code docs/assets/site.js}), o que serve a maior parte dos casos e mantém o
 * endereço fora do repositório; quem instala em um domínio cuja caixa não é essa, ou prefere não
 * publicar endereço nenhum, preenche {@code REMOTO_CONTACT_EMAIL} e as páginas passam a usar o
 * que estiver ali.
 *
 * <p>As páginas pedem isto uma vez e seguem sem ele se não vier resposta: no espelho do GitHub
 * Pages não há aplicação nenhuma atrás, e lá vale o que o site deduz sozinho.
 */
@Tag(name = "Site")
@RestController
class SiteInfoController {

    /** @param contactEmail vazio = o site decide pelo domínio, como sempre fez */
    record SiteInfo(String contactEmail) {
    }

    private final RemotoProperties props;

    SiteInfoController(RemotoProperties props) {
        this.props = props;
    }

    @Operation(summary = "What the static site pages need to know about this installation",
            description = "The contact e-mail configured for this installation, if any. Empty means the pages"
                    + " keep building it from the domain they are served from.")
    // Sem cache: é um pedido minúsculo, e quem muda a configuração e reinicia espera ver a
    // mudança na próxima visita, não dali a uma hora.
    @GetMapping("/api/site")
    SiteInfo site() {
        String email = props.contactEmail() == null ? "" : props.contactEmail().trim();
        return new SiteInfo(email);
    }
}
