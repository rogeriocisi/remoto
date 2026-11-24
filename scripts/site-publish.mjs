// Prepara uma cópia do site (docs/) para publicar, com o que só se sabe na hora do deploy.
//
//   node scripts/site-publish.mjs <origem> <destino> [--site-url URL] [--simulator-url URL]
//
// O repositório não sabe em que domínio o site é servido: os endereços das páginas são
// relativos. Duas coisas, porém, só funcionam com endereço absoluto, e por isso entram aqui, a
// partir da variável SITE_URL do GitHub (README, seção "Deployment"):
//
//   --site-url       a prévia do link (og:url, og:image): Facebook, LinkedIn, WhatsApp e afins
//                    não aceitam endereço relativo. Sem ela, as páginas saem sem prévia, como antes.
//   --simulator-url  para o espelho do GitHub Pages, que só tem as páginas estáticas: os links do
//                    simulador ("simulator/", "../simulator/pt/") passam a apontar para o servidor.
//
// Sem nenhuma das duas, a cópia sai igual à origem.

import { copyFileSync, mkdirSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { join, relative, sep } from 'node:path';

const args = process.argv.slice(2);
const option = (name) => {
  const i = args.indexOf(name);
  if (i < 0) return '';
  const value = args[i + 1] || '';
  args.splice(i, 2);
  return value;
};
const withSlash = (url) => (url && !url.endsWith('/') ? url + '/' : url);
const siteUrl = withSlash(option('--site-url').trim());
const simulatorUrl = withSlash(option('--simulator-url').trim());
const [from, to] = args;
if (!from || !to) {
  console.error('uso: node scripts/site-publish.mjs <origem> <destino> [--site-url URL] [--simulator-url URL]');
  process.exit(2);
}
for (const url of [siteUrl, simulatorUrl]) {
  if (url && !/^https:\/\/[^/\s"<>]+\/[^\s"<>]*$/.test(url)) {
    console.error(`endereço inválido (precisa ser https://...): ${url}`);
    process.exit(2);
  }
}

// cópia arquivo por arquivo: o cpSync do Node 24 encerra o processo sem mensagem no Windows
// quando o caminho tem acento
function copy(src, dst) {
  mkdirSync(dst, { recursive: true });
  for (const name of readdirSync(src)) {
    const s = join(src, name);
    const d = join(dst, name);
    if (statSync(s).isDirectory()) copy(s, d);
    else copyFileSync(s, d);
  }
}
rmSync(to, { recursive: true, force: true });
copy(from, to);

const pages = [];
(function walk(dir) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) walk(path);
    else if (name === 'index.html' || name === '404.html') pages.push(path);
  }
})(to);

const escape = (s) => s.replace(/&/g, '&amp;').replace(/"/g, '&quot;');
let changed = 0;
for (const file of pages) {
  const before = readFileSync(file, 'utf8');
  let html = before;
  // a página que só redireciona para o endereço novo (pt/guia/) não precisa de prévia
  const isStub = /http-equiv="refresh"/i.test(html);

  if (siteUrl && !isStub && /<meta property="og:title"/.test(html)) {
    const dir = relative(to, join(file, '..')).split(sep).filter(Boolean).join('/');
    const pageUrl = siteUrl + (dir ? dir + '/' : '');
    const tags = [
      `<meta property="og:url" content="${escape(pageUrl)}">`,
      `<meta property="og:image" content="${escape(siteUrl + 'assets/og-image.png')}">`,
      '<meta property="og:image:width" content="1200">',
      '<meta property="og:image:height" content="630">',
      '<meta property="og:image:alt" content="ReMoto: spike raster of the soleus motoneuron pool">',
      '<meta name="twitter:card" content="summary_large_image">',
    ].map((t) => '  ' + t).join('\n');
    html = html.replace(/(\n\s*<meta property="og:type"[^>]*>)/, `$1\n${tags}`);
  }

  if (simulatorUrl) {
    html = html.replace(/href="((?:\.\.\/)*)simulator\/((?:[a-z]{2}(?:-[a-z]{2,4})?\/)?)"/g,
      (_, up, lang) => `href="${escape(simulatorUrl)}simulator/${lang}"`);
  }

  if (html !== before) {
    writeFileSync(file, html);
    changed++;
  }
}
console.log(`${to}: ${pages.length} páginas, ${changed} alteradas`
  + (siteUrl ? `; prévia em ${siteUrl}` : '; sem prévia (SITE_URL vazio)')
  + (simulatorUrl ? `; simulador em ${simulatorUrl}simulator/` : ''));
