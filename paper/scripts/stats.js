const { readCsv, series, mean, sd } = require('./lib.js');
const dir = process.argv[2];
const sp = readCsv(dir, 'disparos-SOL'); const t = sp.cols[0].map(Number), u = sp.cols[1].map(Number);
const mn = readCsv(dir, 'tabela-motoneuronios-SOL');
const types = {}; mn.cols[0].forEach((id, i) => types[+id] = mn.cols[1][i]);
const count = {}; for (const k of Object.values(types)) count[k] = (count[k] || 0) + 1;
console.log('MN pool by type', JSON.stringify(count), 'total', mn.n);
console.log('MN spikes total', sp.n, ' first 1 s:', t.filter(x => x < 1000).length);
const per = {}; u.forEach((id, i) => (per[id] = per[id] || []).push(t[i]));
const active = Object.keys(per).map(Number);
const act = {}; for (const id of active) act[types[id]] = (act[types[id]] || 0) + 1;
console.log('active MNs', active.length, JSON.stringify(act), 'max active id', Math.max(...active), 'min', Math.min(...active));
// mean rate over 1-5 s for units with >=2 spikes in that window
const rates = []; for (const id of active) { const s = per[id].filter(x => x >= 1000); if (s.length >= 2) rates.push((s.length - 1) / ((s[s.length - 1] - s[0]) / 1000)); }
console.log('units firing in 1-5 s', rates.length, 'mean rate Hz', mean(rates).toFixed(2), 'sd', sd(rates).toFixed(2), 'min', Math.min(...rates).toFixed(2), 'max', Math.max(...rates).toFixed(2));
const f = series(dir, 'forca-SOL'); const idx = f.t.map((x, i) => i).filter(i => f.t[i] >= 1000);
const fs1 = idx.map(i => f.v[i]);
console.log('force 1-5 s mean N', mean(fs1).toFixed(2), 'sd', sd(fs1).toFixed(3), 'CV %', (100 * sd(fs1) / mean(fs1)).toFixed(3), 'max overall', Math.max(...f.v).toFixed(2));
const f01 = f.v.filter((x, i) => f.t[i] < 1000); console.log('force 0-1 s mean N', mean(f01).toFixed(2));
const tq = series(dir, 'torque'); const tq1 = idx.map(i => tq.v[i]); console.log('torque 1-5 s mean', mean(tq1).toFixed(3), tq.unit);
const e = series(dir, 'emg-SOL'); const e1 = idx.map(i => e.v[i]); console.log('EMG 1-5 s rms mV', Math.sqrt(mean(e1.map(x => x * x))).toFixed(4));
const tr = readCsv(dir, 'disparos-DT-CM_ext'); const tu = new Set(tr.cols[1]); console.log('tract axons', tu.size, 'tract spikes', tr.n, 'mean rate/axon Hz', (tr.n / tu.size / 5).toFixed(1));
