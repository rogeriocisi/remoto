// ReMoto — o e-mail de contato das páginas do site.
//
// O endereço não está escrito no repositório: sai do domínio onde a página está servida, como
// remoto@<domínio>, de modo que o mesmo arquivo serve a qualquer domínio em que o site seja
// publicado (remoto.exemplo.com e www.exemplo.com dão remoto@exemplo.com).
//
// Sem domínio utilizável — localhost, endereço IP, ou o espelho em github.io, que não tem caixa
// postal — nada é preenchido e o bloco de contato fica só com as issues do GitHub, que é também o
// que aparece com o JavaScript desligado. No HTML: <span class="mail" hidden> traz o e-mail e o
// conectivo da frase, e <span class="no-mail"> a abertura alternativa, quando a frase precisa de
// uma para ler bem sem o e-mail.
(function () {
  'use strict';

  var BOX = 'remoto';
  // Domínios de segundo nível (com.br, co.uk, gov.br...): nesses, o domínio do site tem 3 rótulos.
  var SECOND_LEVEL = /^(com|org|net|gov|edu|ac|co|mil|int)$/;

  /** O domínio do site a partir do host, ou null quando não há um em que faça sentido ter e-mail. */
  function siteDomain(host) {
    host = String(host || '').toLowerCase().replace(/\.$/, '');
    if (!host || host.indexOf(':') >= 0) {
      return null; // endereço IPv6
    }
    if (/^\d+(\.\d+)*$/.test(host)) {
      return null; // endereço IPv4
    }
    if (/(^|\.)localhost$/.test(host) || /(^|\.)github\.io$/.test(host)) {
      return null; // desenvolvimento e o espelho do GitHub Pages
    }
    var parts = host.split('.');
    if (parts.length < 2) {
      return null; // nome sem domínio (uma máquina da rede local)
    }
    var take = parts.length > 2 && SECOND_LEVEL.test(parts[parts.length - 2]) ? 3 : 2;
    return parts.slice(-take).join('.');
  }

  // Exportado para o teste da tabela de hosts (scripts/site-domain-test.mjs).
  window.ReMotoSiteDomain = siteDomain;

  function fill(address) {
    if (!address) {
      var domain = siteDomain(window.location.hostname);
      if (!domain) {
        return;
      }
      address = BOX + '@' + domain;
    }
    var links = document.querySelectorAll('a[data-mail]');
    for (var i = 0; i < links.length; i++) {
      links[i].href = 'mailto:' + address;
      links[i].textContent = address;
    }
    var mail = document.querySelectorAll('.mail');
    for (var j = 0; j < mail.length; j++) {
      mail[j].hidden = false;
    }
    var alternative = document.querySelectorAll('.no-mail');
    for (var k = 0; k < alternative.length; k++) {
      alternative[k].hidden = true;
    }
  }

  /**
   * O endereço que a instalação configurou (REMOTO_CONTACT_EMAIL), se houver.
   *
   * O simulador o entrega em api/site; o endereço dele sai do link "Simulador" da própria página,
   * porque o caminho do simulador dentro do domínio é configurável. Sem resposta — o espelho do
   * GitHub Pages, que não tem aplicação atrás, ou uma instalação só do site — fica o que o site
   * deduz do domínio, que é como sempre foi.
   */
  function configured(then) {
    // o link da API, e não o do simulador: este aponta para a porta do idioma (/simulator/pt/),
    // e o endereço do endpoint é a raiz do simulador, que é onde a Swagger UI também está
    // document.querySelector e fetch podem não existir: o arquivo também é lido fora do
    // navegador, pelo scripts/site-domain-test.mjs, que monta só o mínimo de que precisa
    var api = document.querySelector && document.querySelector('nav a.api');
    if (!api || !window.fetch || typeof URL !== 'function') {
      then(null);
      return;
    }
    var url = new URL('api/site', api.href).toString();
    window.fetch(url, { headers: { Accept: 'application/json' }, cache: 'no-store' })
      .then(function (r) { return r.ok ? r.json() : null; })
      .then(function (info) { then(info && info.contactEmail ? info.contactEmail : null); })
      .catch(function () { then(null); });
  }

  function start() {
    // o deduzido primeiro, para a página não ficar esperando a rede; o configurado corrige depois
    fill(null);
    configured(function (address) {
      if (address) {
        fill(address);
      }
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start);
  } else {
    start();
  }
})();
