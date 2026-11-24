#!/usr/bin/env node
// Confere a regra do e-mail de contato do site (docs/assets/site.js) numa tabela de hosts:
// o endereço é remoto@<domínio do site>, e não existe onde não faria sentido (desenvolvimento,
// endereço IP, o espelho do GitHub Pages).
//
//   node scripts/site-domain-test.mjs
//
// Os domínios aqui são de exemplo de propósito: o domínio real do deploy não entra no
// repositório — é dele que a função tira o endereço, em tempo de execução.

import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const source = readFileSync(join(root, 'docs', 'assets', 'site.js'), 'utf8');

// O arquivo é escrito para o navegador; aqui roda com o mínimo de que precisa.
const window = { location: { hostname: '' } };
const document = { readyState: 'complete', querySelectorAll: () => [], addEventListener: () => {} };
new Function('window', 'document', source)(window, document);
const siteDomain = window.ReMotoSiteDomain;

const cases = [
  ['exemplo.com', 'exemplo.com'],
  ['www.exemplo.com', 'exemplo.com'],
  ['remoto.exemplo.com', 'exemplo.com'],
  ['app.remoto.exemplo.com', 'exemplo.com'],
  ['REMOTO.EXEMPLO.COM', 'exemplo.com'],
  ['remoto.exemplo.com.', 'exemplo.com'], // com o ponto final da raiz do DNS
  ['remoto.exemplo.com.br', 'exemplo.com.br'],
  ['www.exemplo.co.uk', 'exemplo.co.uk'],
  ['remoto.leb.usp.br', 'usp.br'],
  ['localhost', null],
  ['remoto.localhost', null],
  ['127.0.0.1', null],
  ['192.168.0.10', null],
  ['::1', null],
  ['maquina', null],
  ['', null],
  ['exemplo.github.io', null],
];

let failures = 0;
for (const [host, expected] of cases) {
  const got = siteDomain(host);
  const ok = got === expected;
  if (!ok) {
    failures++;
    console.error(`FALHOU  ${JSON.stringify(host)}: esperado ${JSON.stringify(expected)}, veio ${JSON.stringify(got)}`);
  }
}

if (failures > 0) {
  console.error(`\n${failures} de ${cases.length} casos falharam.`);
  process.exit(1);
}
console.log(`site.js: ${cases.length} casos do domínio do e-mail conferidos.`);
