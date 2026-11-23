#!/usr/bin/env node
// Gera a figura de abertura da GitHub Pages (docs/index.html) a partir de uma execução do cenário
// "Default - Soleus contraction". Só usa o Node (18 ou mais novo), sem dependências.
//
//   java -jar remoto-cli/target/remoto-cli-3.0.0-SNAPSHOT.jar run \
//        scenarios/remoto-17-default-soleus-contraction.json --threads 4 -o resultados/soleus
//   node scripts/pages-figure.mjs resultados/soleus
//
// Grava docs/assets/raster-soleus.png e troca, em docs/index.html, o trecho entre os comentários
// "recording:begin" e "recording:end". A mesma seed e a mesma versão do motor dão a mesma figura.

import { readFileSync, writeFileSync } from 'node:fs';
import { deflateSync, gunzipSync } from 'node:zlib';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const runDir = process.argv[2];
if (!runDir) {
  console.error('uso: node scripts/pages-figure.mjs <pasta da execução>');
  process.exit(2);
}
const docs = resolve(dirname(fileURLToPath(import.meta.url)), '..', 'docs');
const NUCLEUS = 'SOL';
const STORED = 'S-1'; // motoneurônio com sinais guardados (o primeiro do tipo S)

// ---- leitura ---------------------------------------------------------------------------------
function rows(name) {
  const lines = gunzipSync(readFileSync(join(runDir, 'results', name + '.csv.gz'))).toString('utf8').trim().split(/\r?\n/);
  const header = lines.shift().split(',');
  return { header, lines };
}
function series(name, column = 1) {
  const { lines } = rows(name);
  const t = new Float64Array(lines.length), v = new Float64Array(lines.length);
  lines.forEach((line, i) => { const c = line.split(','); t[i] = +c[0]; v[i] = +c[column]; });
  return { t, v };
}
const manifest = JSON.parse(readFileSync(join(runDir, 'manifest.json'), 'utf8'));
const params = Object.fromEntries(rows('tabela-parametros').lines.map(l => l.split(',')));
const duration = +params.duracao; // ms
const force = series('forca-' + NUCLEUS);
const emg = series('emg-' + NUCLEUS);
const stored = rows('sinais-' + NUCLEUS + '-' + STORED);
const vs = series('sinais-' + NUCLEUS + '-' + STORED, stored.header.indexOf('Vs[mV]'));
const spikes = rows('disparos-' + NUCLEUS).lines.map(l => l.split(',').map(Number)); // [t_ms, unidade]
const poolSize = rows('tabela-motoneuronios-' + NUCLEUS).lines.length;

// ---- geometria -------------------------------------------------------------------------------
const W = 1000, ML = 64, MR = 16, PW = W - ML - MR;
const P = {
  vs:     { top: 30,  h: 66,  min: -6,   max: 90,  ticks: [0, 40, 80] },
  raster: { top: 132, h: 190 },
  emg:    { top: 358, h: 76,  min: -0.2, max: 0.2, ticks: [-0.1, 0, 0.1] },
  force:  { top: 470, h: 104, min: 0,    max: 350, ticks: [0, 100, 200, 300] },
};
const H = 614;
const n1 = x => (Math.round(x * 10) / 10).toString();
const xOf = t => ML + t / duration * PW;
const yOf = (p, v) => p.top + p.h - (v - p.min) / (p.max - p.min) * p.h;
const esc = s => String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

// faixa (mínimo e máximo) de um sinal a cada 2 unidades do gráfico: preserva os picos
function envelope(s, p) {
  const cols = PW / 2;
  const lo = new Float64Array(cols).fill(Infinity), hi = new Float64Array(cols).fill(-Infinity);
  for (let i = 0; i < s.t.length; i++) {
    const c = Math.min(cols - 1, Math.floor(s.t[i] / duration * cols));
    if (s.v[i] < lo[c]) lo[c] = s.v[i];
    if (s.v[i] > hi[c]) hi[c] = s.v[i];
  }
  let d = '';
  for (let c = 0; c < cols; c++) d += (c ? 'L' : 'M') + (ML + c * 2 + 1) + ' ' + n1(yOf(p, hi[c]));
  for (let c = cols - 1; c >= 0; c--) d += 'L' + (ML + c * 2 + 1) + ' ' + n1(yOf(p, lo[c]));
  return d + 'Z';
}

// média do sinal a cada 2 unidades do gráfico
function line(s, p) {
  const cols = PW / 2, sum = new Float64Array(cols), cnt = new Uint32Array(cols);
  for (let i = 0; i < s.t.length; i++) {
    const c = Math.min(cols - 1, Math.floor(s.t[i] / duration * cols));
    sum[c] += s.v[i]; cnt[c]++;
  }
  const pts = [];
  for (let c = 0; c < cols; c++) if (cnt[c]) pts.push([ML + (c + 0.5) * 2, yOf(p, sum[c] / cnt[c])]);
  return pts;
}

// ---- raster em PNG (1 bit, fundo transparente) --------------------------------------------------
const maxUnit = spikes.reduce((m, s) => Math.max(m, s[1]), 0);
const topUnit = Math.max(100, Math.ceil(maxUnit / 100) * 100);
function rasterPng() {
  const SC = 3, w = PW * SC, h = P.raster.h * SC, stride = Math.ceil(w / 8) + 1;
  const raw = Buffer.alloc(stride * h); // byte 0 de cada linha: filtro 0
  const set = (x, y) => { if (x >= 0 && x < w && y >= 0 && y < h) raw[y * stride + 1 + (x >> 3)] |= 0x80 >> (x & 7); };
  for (const [t, u] of spikes) {
    const x = Math.round(t / duration * w), y = Math.round(h - u / topUnit * h);
    for (let dx = -1; dx <= 2; dx++) for (let dy = -2; dy <= 2; dy++) set(x + dx, y + dy);
  }
  const crcTable = new Int32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c; });
  const crc = buf => { let c = -1; for (const b of buf) c = crcTable[(c ^ b) & 0xff] ^ (c >>> 8); return (c ^ -1) >>> 0; };
  const chunk = (type, data) => {
    const body = Buffer.concat([Buffer.from(type, 'latin1'), data]);
    const len = Buffer.alloc(4), sum = Buffer.alloc(4);
    len.writeUInt32BE(data.length); sum.writeUInt32BE(crc(body));
    return Buffer.concat([len, body, sum]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 1; ihdr[9] = 3; // 1 bit por pixel, cor indexada
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('PLTE', Buffer.from([0xff, 0xff, 0xff, 0x1f, 0x5f, 0x99])),
    chunk('tRNS', Buffer.from([0x00, 0xff])),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}
writeFileSync(join(docs, 'assets', 'raster-soleus.png'), rasterPng());

// ---- números da legenda, da leitura e da tabela ---------------------------------------------------
const STEP = 10, WINDOW = 200; // ms
const sampleAt = (s, t) => s.v[Math.min(s.v.length - 1, Math.max(0, Math.round(t / (duration / s.v.length)) - 1))];
const byTime = spikes.slice().sort((a, b) => a[0] - b[0]);
function firingUnits(t) { // unidades com disparo em (t - WINDOW, t]
  const seen = new Set();
  for (const [ts, u] of byTime) { if (ts > t) break; if (ts > t - WINDOW) seen.add(u); }
  return seen.size;
}
function recruitedBy(t) {
  const seen = new Set();
  for (const [ts, u] of byTime) { if (ts > t) break; seen.add(u); }
  return seen.size;
}
const hover = { x0: ML, x1: ML + PW, t1: duration, dt: STEP, force: [], units: [] };
for (let t = STEP; t <= duration + 1e-9; t += STEP) {
  hover.force.push(Math.round(sampleAt(force, t) * 10) / 10);
  hover.units.push(firingUnits(t));
}
const recruited = new Set(spikes.map(s => s[1]));
const plateauFrom = duration * 0.4;
let sum = 0, count = 0;
for (let i = 0; i < force.t.length; i++) if (force.t[i] >= plateauFrom) { sum += force.v[i]; count++; }
const plateau = sum / count;
const tableRows = [];
for (let t = 500; t <= duration + 1e-9; t += 500) {
  tableRows.push(`<tr><td class="num">${(t / 1000).toFixed(1)}</td><td class="num">${sampleAt(force, t).toFixed(1)}</td>` +
      `<td class="num">${firingUnits(t)}</td><td class="num">${recruitedBy(t)}</td></tr>`);
}

// ---- SVG -----------------------------------------------------------------------------------------
const seconds = Math.round(duration / 1000);
const marks = [], axes = [];
function frame(key, title, unit) {
  const p = P[key];
  axes.push(`<text class="ch-title" x="${ML}" y="${p.top - 9}">${title}</text>`);
  axes.push(`<text class="ch-unit" x="${W - MR}" y="${p.top - 9}" text-anchor="end">${unit}</text>`);
  for (let s = 0; s <= seconds; s++) {
    axes.push(`<line class="${s === 0 ? 'ch-axis' : 'ch-grid'}" x1="${n1(xOf(s * 1000))}" x2="${n1(xOf(s * 1000))}" y1="${p.top}" y2="${p.top + p.h}"/>`);
  }
}
function yTicks(key, label = v => String(v)) {
  const p = P[key];
  for (const v of p.ticks) {
    const y = n1(yOf(p, v));
    axes.push(`<line class="ch-grid" x1="${ML}" x2="${ML + PW}" y1="${y}" y2="${y}"/>`);
    axes.push(`<text class="ch-tick" x="${ML - 8}" y="${n1(+y + 3.5)}" text-anchor="end">${label(v)}</text>`);
  }
}

frame('vs', 'SOMA POTENTIAL OF ONE MOTONEURON (S 1)', 'mV above rest');
yTicks('vs');
marks.push(`<path class="ch-env" d="${envelope(vs, P.vs)}"/>`);

frame('raster', 'SPIKES OF THE MOTONEURON POOL', 'motoneuron #');
P.raster.min = 0; P.raster.max = topUnit; P.raster.ticks = [];
for (let u = 0; u <= topUnit; u += 200) P.raster.ticks.push(u);
yTicks('raster', v => (v === 0 ? '1' : String(v)));
marks.push(`<image href="assets/raster-soleus.png" x="${ML}" y="${P.raster.top}" width="${PW}" height="${P.raster.h}" preserveAspectRatio="none"/>`);

frame('emg', 'SURFACE EMG (WITH NOISE, 50&#8211;1000 HZ)', 'mV');
yTicks('emg', v => (v === 0 ? '0' : v.toFixed(1).replace('-', '&#8722;')));
marks.push(`<path class="ch-env" d="${envelope(emg, P.emg)}"/>`);

frame('force', 'MUSCLE FORCE', 'N');
yTicks('force');
const pts = line(force, P.force);
const path = pts.map((q, i) => (i ? 'L' : 'M') + n1(q[0]) + ' ' + n1(q[1])).join('');
const base = P.force.top + P.force.h;
marks.push(`<path class="ch-wash" d="${path}L${n1(pts[pts.length - 1][0])} ${base}L${n1(pts[0][0])} ${base}Z"/>`);
marks.push(`<path class="ch-line" d="${path}"/>`);
axes.push(`<line class="ch-axis" x1="${ML}" x2="${ML + PW}" y1="${base}" y2="${base}"/>`);
for (let s = 0; s <= seconds; s++) {
  axes.push(`<text class="ch-tick" x="${n1(xOf(s * 1000))}" y="${base + 16}" text-anchor="middle">${s}</text>`);
}
axes.push(`<text class="ch-unit" x="${W - MR}" y="${base + 33}" text-anchor="end">time (s)</text>`);

const name = esc(manifest.scenarioName);
const types = [...new Set([...recruited].map(u => rowType(u)))].sort();
function rowType(unit) {
  const { header, lines } = rowType.table ??= rows('tabela-motoneuronios-' + NUCLEUS);
  const iu = header.indexOf('unidade'), it = header.indexOf('tipo');
  const map = rowType.map ??= new Map(lines.map(l => { const c = l.split(','); return [+c[iu], c[it]]; }));
  return map.get(unit);
}
const typeText = types.length === 1 ? `all of type ${esc(types[0])}` : `of types ${esc(types.join(', '))}`;

const html = `      <div class="strip-head"><span><b>Recording</b> &middot; ${name}</span><span>seed ${esc(manifest.seed)} &middot; engine ${esc(manifest.engineVersion)}</span></div>
      <div class="scroll-x">
        <svg viewBox="0 0 ${W} ${H}" role="img" tabindex="0" aria-labelledby="rec-title rec-desc">
          <title id="rec-title">Simulated recording of the scenario ${name}</title>
          <desc id="rec-desc">Four stacked panels over ${seconds} seconds: the soma potential of one motoneuron, the spikes of the motoneuron pool, the surface EMG and the muscle force, which rises in the first second and settles near ${Math.round(plateau)} newtons. The values are in the table below the figure.</desc>
          ${axes.join('\n          ')}
          <g class="reveal">
          ${marks.join('\n          ')}
          </g>
          <line class="xh" x1="${ML}" x2="${ML}" y1="${P.vs.top}" y2="${base}" style="display:none"/>
        </svg>
      </div>
      <p class="readout">Move the pointer over the recording, or focus it and press the arrow keys, to read the values.</p>
      <figcaption><b>Fig. 1</b>Output of the public scenario <em>${name}</em>: 100 descending axons firing as Poisson
        processes drive the ${poolSize} motoneurons of the soleus. ${recruited.size} of them are recruited, ${typeText}, and the
        force settles near ${Math.round(plateau)}&nbsp;N. Nothing here is drawn by hand: the panels are the simulator&rsquo;s output for
        seed ${esc(manifest.seed)}, and the same seed and engine version give the same figure again.</figcaption>
      <details class="data">
        <summary>Values behind the figure</summary>
        <div class="tbl-wrap" style="max-width:34rem">
          <table>
            <thead><tr><th class="num">Time (s)</th><th class="num">Force (N)</th><th class="num">Units firing in the last ${WINDOW} ms</th><th class="num">Units recruited so far</th></tr></thead>
            <tbody>
              ${tableRows.join('\n              ')}
            </tbody>
          </table>
        </div>
      </details>
      <script type="application/json" id="rec-data">${JSON.stringify(hover)}</script>`;

const index = join(docs, 'index.html');
const page = readFileSync(index, 'utf8');
const block = /(<!-- recording:begin[^>]*-->)[\s\S]*?(<!-- recording:end -->)/;
if (!block.test(page)) {
  console.error('docs/index.html não tem os comentários recording:begin e recording:end');
  process.exit(1);
}
writeFileSync(index, page.replace(block, (_, a, b) => a + '\n' + html + '\n' + b));
console.log(`figura gravada: ${recruited.size} de ${poolSize} motoneurônios recrutados, platô de ${plateau.toFixed(1)} N, ${spikes.length} disparos`);
