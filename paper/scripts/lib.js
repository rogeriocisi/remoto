const fs = require('fs'), zlib = require('zlib'), path = require('path');
function readCsv(dir, name) {
  const txt = zlib.gunzipSync(fs.readFileSync(path.join(dir, 'results', name + '.csv.gz'))).toString('utf8');
  const lines = txt.split('\n').filter(l => l.length);
  const header = lines[0].split(',');
  const cols = header.map(() => []);
  for (let i = 1; i < lines.length; i++) {
    const p = lines[i].split(',');
    for (let c = 0; c < header.length; c++) cols[c].push(p[c]);
  }
  return { header, cols, n: lines.length - 1 };
}
function series(dir, name) { const c = readCsv(dir, name); return { t: c.cols[0].map(Number), v: c.cols[1].map(Number), unit: c.header[1] }; }
function mean(a) { let s = 0; for (const x of a) s += x; return s / a.length; }
function sd(a) { const m = mean(a); let s = 0; for (const x of a) s += (x - m) * (x - m); return Math.sqrt(s / (a.length - 1)); }
module.exports = { readCsv, series, mean, sd };
