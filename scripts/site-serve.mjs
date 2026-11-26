#!/usr/bin/env node
// Serve o site e o simulador juntos, em desenvolvimento, como eles ficam em produção: as páginas
// estáticas de docs/ na raiz e o simulador em /simulator/. Só usa o Node (18 ou mais novo), sem
// dependências.
//
//   .\scripts\start.ps1                 (numa janela: o simulador, em 8081)
//   node scripts/site-serve.mjs         (noutra: o site, em 8090, com /simulator/ no proxy)
//
//   node scripts/site-serve.mjs [--port 8090] [--simulator http://localhost:8081]
//
// Sem ele, só o simulador sobe em localhost, e os links entre os dois lados ficam pela metade: no
// repositório os endereços são relativos ao domínio ("/pt/" no menu do simulador, "simulator/" nas
// páginas do site), porque o domínio do deploy não entra no código. Com este servidor na frente
// dos dois, os dois sentidos funcionam igual ao que está no ar.

import { createReadStream, existsSync, statSync } from 'node:fs';
import { createServer, request as httpRequest } from 'node:http';
import { dirname, extname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const docs = join(root, 'docs');

const args = process.argv.slice(2);
const option = (name, fallback) => {
  const i = args.indexOf(name);
  return i < 0 ? fallback : args[i + 1] || fallback;
};
const port = Number(option('--port', '8090'));
const simulator = new URL(option('--simulator', 'http://localhost:8081'));

// O caminho do simulador é o context-path da aplicação (server.servlet.context-path), e é também
// onde ele é servido em produção; o resto é o site.
const PREFIX = '/simulator';

const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
  '.zip': 'application/zip',
  '.md': 'text/markdown; charset=utf-8',
  '.txt': 'text/plain; charset=utf-8',
};

// ---- o simulador, por trás ---------------------------------------------------------------------
// O proxy é transparente de propósito: mesmo método, mesmos cabeçalhos e o corpo em fluxo, sem
// buffer, que é o que o /api/runs/{id}/events (SSE) precisa para chegar evento a evento.
function proxy(req, res) {
  const upstream = httpRequest(
    {
      protocol: simulator.protocol,
      hostname: simulator.hostname,
      port: simulator.port,
      method: req.method,
      path: req.url,
      headers: { ...req.headers, host: simulator.host },
    },
    (answer) => {
      const headers = { ...answer.headers };
      // Um redirecionamento do simulador aponta para a porta dele; aqui quem fala com o navegador
      // é esta porta, e o endereço precisa continuar valendo.
      const location = headers.location;
      if (location && location.startsWith(simulator.origin)) {
        headers.location = location.slice(simulator.origin.length);
      }
      res.writeHead(answer.statusCode || 502, headers);
      answer.pipe(res);
    },
  );
  upstream.on('error', (error) => {
    res.writeHead(502, { 'content-type': 'text/plain; charset=utf-8' });
    res.end(`O simulador não respondeu em ${simulator.origin} (${error.code || error.message}).\n`
      + 'Suba-o numa outra janela com scripts/start.ps1 (ou start.sh).\n');
  });
  req.pipe(upstream);
}

// ---- o site, em arquivos -----------------------------------------------------------------------
function fileFor(pathname) {
  let file = join(docs, decodeURIComponent(pathname).replaceAll('/', sep));
  // Nada fora de docs/, por mais voltas que o endereço dê.
  if (file !== docs && !file.startsWith(docs + sep)) {
    return null;
  }
  if (existsSync(file) && statSync(file).isDirectory()) {
    file = join(file, 'index.html');
  }
  return existsSync(file) && statSync(file).isFile() ? file : null;
}

function serve(res, file, status = 200) {
  res.writeHead(status, {
    'content-type': TYPES[extname(file).toLowerCase()] || 'application/octet-stream',
    'content-length': statSync(file).size,
    // Em desenvolvimento o arquivo que vale é sempre o do disco.
    'cache-control': 'no-store',
  });
  createReadStream(file).pipe(res);
}

createServer((req, res) => {
  const pathname = new URL(req.url, 'http://localhost').pathname;
  if (pathname === PREFIX || pathname.startsWith(PREFIX + '/')) {
    proxy(req, res);
    return;
  }
  const file = fileFor(pathname);
  if (file) {
    serve(res, file);
    return;
  }
  // A mesma página de erro que o GitHub Pages serve.
  const notFound = join(docs, '404.html');
  if (existsSync(notFound)) {
    serve(res, notFound, 404);
    return;
  }
  res.writeHead(404, { 'content-type': 'text/plain; charset=utf-8' });
  res.end('404\n');
}).listen(port, () => {
  console.log(`[ReMoto] site   http://localhost:${port}/  (e /pt/ em português)`);
  console.log(`[ReMoto] simulador http://localhost:${port}${PREFIX}/  → ${simulator.origin}${PREFIX}/`);
  console.log('[ReMoto] Ctrl+C para parar');
});
