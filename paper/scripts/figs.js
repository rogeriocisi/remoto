// Generates the manuscript figures as SVG + HTML wrappers (printed to PDF by a headless browser).
const fs = require('fs'), path = require('path');
const { readCsv, series } = require('./lib.js');
const S = require('./svg.js');
const { Panel, text, legend, box, boxText, arrow, elbow, svg, html, BLACK, BLUE, VERM, GREY } = S;

const RUNS = process.argv[2], OUT = process.argv[3];
fs.mkdirSync(OUT, { recursive: true });
function emit(name, w, h, body) {
  const s = svg(w, h, body);
  fs.writeFileSync(path.join(OUT, name + '.svg'), s);
  fs.writeFileSync(path.join(OUT, name + '.html'), html(w, h, s));
  console.log(name, w + 'x' + h + ' mm', (s.length / 1024).toFixed(0) + ' kB');
}
const sec = a => a.map(x => x / 1000);

// ---------------------------------------------------------------- Fig. 1: architecture
(function fig1() {
  const W = 174, H = 104, o = [];
  // scenario sources (left)
  o.push(box(2, 8, 40, 20, { fill: '#F2F2F2' }), boxText(2, 8, 40, 20, ['Original database', 'remoto.script (text)', '30 configurations'], { boldFirst: true }));
  o.push(box(2, 40, 40, 30), boxText(2, 40, 40, 30, ['Scenario (JSON)', 'seed, duration, step,', 'engine id, model tables', 'and settings', '20 public scenarios'], { boldFirst: true }));
  o.push(arrow(22, 28, 22, 40), text(24, 35.5, 'export-legacy'));
  // core (centre)
  o.push(box(52, 4, 70, 72, { fill: '#F7F7F7' }), text(87, 9.5, 'remoto-core (plain Java, no dependencies)', { anchor: 'middle', bold: true }));
  const cb = (y, h, lines) => box(55, y, 64, h) + boxText(55, y, 64, h, lines, { boldFirst: true });
  o.push(cb(12.5, 10.5, ['Scenario format and validation', 'typed model configuration']));
  o.push(cb(25, 17.5, ['Models', 'channels, synapses, membranes, tracts,', 'afferents, motor units, muscles, EMG,', 'spindle, tendon organ, joint']));
  o.push(cb(44.5, 14, ['Engine', 'fixed-step loop, parallel neuron update,', 'seeded random source']));
  o.push(cb(60.5, 12.5, ['Outputs', 'time series, spike trains, tables,', 'catalogue of results']));
  o.push(arrow(42, 55, 52, 55));
  // front ends (right)
  o.push(box(132, 4, 40, 22), boxText(132, 4, 40, 22, ['remoto-cli', 'run, validate,', 'export-legacy', '(scripts, clusters)'], { boldFirst: true }));
  o.push(box(132, 32, 40, 44), boxText(132, 32, 40, 44, ['remoto-web', 'single executable JAR', 'session areas, no login', 'fair queue and limits', 'REST API, live progress', 'scenario editor', 'results views', 'CSV, PNG, ZIP export'], { boldFirst: true }));
  o.push(arrow(132, 15, 122, 15), arrow(132, 54, 122, 54));
  // storage (bottom)
  o.push(box(52, 84, 120, 17, { fill: '#F2F2F2' }), boxText(52, 84, 120, 17, ['remoto-storage (files, no database)', 'scenarios/<id>.json;  runs/<id>/manifest.json (seed, engine version, scenario hash);', 'runs/<id>/config.json (frozen scenario);  runs/<id>/results/*.csv.gz'], { boldFirst: true }));
  o.push(arrow(152, 76, 152, 84), arrow(87, 76, 87, 84));
  o.push(elbow([[22, 70], [22, 92.5], [52, 92.5]], { dash: '1 0.8' }), text(24, 90.5, 'import, export'));
  emit('Fig1', W, H, o.join('\n'));
})();

// ---------------------------------------------------------------- Fig. 2: default scenario
(function fig2() {
  const dir = path.join(RUNS, 's17-full');
  const W = 174, H = 115, o = [];
  const sp = readCsv(dir, 'disparos-SOL'); const st = sec(sp.cols[0].map(Number)), su = sp.cols[1].map(Number);
  const a = new Panel({ x: 15, y: 6, w: 68, h: 99 }, [0, 5], [0, 900], { id: 'a', tag: 'a', xlabel: 'Time (s)', ylabel: 'Motoneuron index', yticks: [0, 200, 400, 600, 800], yoff: 10.5 });
  a.raster(st, su).hline(800).hline(850);
  a.label(5.08, 390, 'S').label(5.08, 815, 'FR').label(5.08, 865, 'FF');
  o.push(a.render());
  const fo = series(dir, 'forca-SOL');
  const b = new Panel({ x: 104, y: 6, w: 67, h: 22 }, [0, 5], [0, 350], { id: 'b', tag: 'b', xlabel: null, ylabel: 'Force (N)', yticks: [0, 100, 200, 300] });
  b.line(sec(fo.t), fo.v, { width: 0.3 }); o.push(b.render());
  const em = series(dir, 'emg-SOL');
  const emax = Math.max(...em.v.map(Math.abs)); const er = Math.ceil(emax / 0.1) * 0.1; console.log('  fig2: EMG max abs', emax.toFixed(4), 'mV');
  const c = new Panel({ x: 104, y: 36, w: 67, h: 22 }, [0, 5], [-er, er], { id: 'c', tag: 'c', xlabel: 'Time (s)', ylabel: 'EMG (mV)', yticks: [-er, 0, er], yfmt: v => v === 0 ? '0' : v.toFixed(1) });
  c.line(sec(em.t), em.v, { width: 0.12 }); o.push(c.render());
  const sg = readCsv(dir, 'sinais-SOL-S-1'); const t = sec(sg.cols[0].map(Number));
  const vs = sg.cols[sg.header.indexOf('Vs[mV]')].map(Number), vd = sg.cols[sg.header.indexOf('Vd[mV]')].map(Number);
  const ge = sg.cols[sg.header.indexOf('gExc-dend[nS]')].map(Number);
  const win = [2.0, 2.3];
  const d = new Panel({ x: 104, y: 74, w: 67, h: 20 }, win, [-10, 90], { id: 'd', tag: 'd', xlabel: null, ylabel: 'V (mV)', yticks: [0, 40, 80], xticks: [2.0, 2.1, 2.2, 2.3] });
  d.line(t, vs, { width: 0.25, bins: 1200 }).line(t, vd, { width: 0.25, stroke: VERM, dash: '0.9 0.5', bins: 1200 }); o.push(d.render());
  o.push(legend(128, 71.5, [{ label: 'soma', stroke: BLACK }]), legend(148, 71.5, [{ label: 'dendrite', stroke: VERM, dash: '0.9 0.5' }]));
  const gmax = Math.max(...ge.filter((x, i) => t[i] >= win[0] && t[i] <= win[1]));
  const e = new Panel({ x: 104, y: 97, w: 67, h: 8 }, win, [0, Math.ceil(gmax / 50) * 50], { id: 'e', xlabel: 'Time (s)', ylabel: 'g (nS)', ny: 2, xticks: [2.0, 2.1, 2.2, 2.3], xfmt: v => v.toFixed(1) });
  e.line(t, ge, { width: 0.2, bins: 1200 }); o.push(e.render());
  emit('Fig3', W, H, o.join('\n'));
  console.log('  fig2: dendritic excitatory conductance max in window', gmax.toFixed(1), 'nS');
})();

// ---------------------------------------------------------------- Fig. 3: synaptic decay defect
// Port of SynapseGroup for one synapse without short-term dynamics, driven as the neuron drives it
// (four calls per integration step: t, t + h/2, t + h/2, t + h). frozen = true reproduces the
// December 2016 code, which used the integration step in place of (t - t0).
function synapse({ alpha, beta, tmax, pulse, h, spikes, T, frozen }) {
  const TOL = 0.001, rInf = alpha * tmax / (alpha * tmax + beta), tauOn = 1 / (alpha * tmax + beta), tauOff = 1 / beta, expFinish = Math.exp(-pulse / tauOn);
  let non = 0, sumOn = 0, sumOff = 0, ron = 0, roff = 0, t0 = 0;
  const g = { ri: 0, t: 0, on: false, endOfPulse: 0 }, received = spikes.slice(), pulsing = [];
  function value(t) {
    const dt = frozen ? h : (t - t0);
    sumOn = non * rInf + (ron - non * rInf) * Math.exp(-dt / tauOn);
    sumOff = roff * Math.exp(-dt / tauOff);
    while (received.length && t > received[0] - TOL) {
      received.shift();
      if (!g.on) {
        const ri = g.ri * Math.exp(-(t - g.t - pulse) / tauOff);
        g.ri = ri; g.t = t; ron = sumOn + ri; roff = sumOff - ri; non += 1; t0 = t; pulsing.push(g); g.on = true;
      }
      g.endOfPulse = t + pulse;
    }
    while (pulsing.length && t > pulsing[0].endOfPulse - TOL) {
      const ri = rInf + (g.ri - rInf) * expFinish;
      g.ri = ri; t0 = t; ron = sumOn - ri; roff = sumOff + ri; non -= 1; pulsing.shift(); g.on = false;
    }
    return sumOn + sumOff;
  }
  const n = Math.round(T / h), ts = [], r = [];
  for (let k = 0; k < n; k++) { const t = k * h; ts.push(t); r.push(value(t)); value(t + h / 2); value(t + h / 2); value(t + h); }
  return { ts, r, rInf, tauOn, tauOff, rEnd: rInf * (1 - expFinish) };
}
(function fig3() {
  const W = 174, H = 62, o = [];
  const P = { alpha: 1.1, beta: 0.19, tmax: 1.0, pulse: 1.0, h: 0.05, spikes: [5.0], T: 40 };
  const ok = synapse({ ...P, frozen: false }), bad = synapse({ ...P, frozen: true });
  const peak = Math.max(...ok.r), plateau = bad.r[bad.r.length - 1], during = bad.r[Math.round(5.5 / P.h)];
  console.log('  fig3a: r_inf', ok.rInf.toFixed(4), 'tau_on', ok.tauOn.toFixed(4), 'tau_off', ok.tauOff.toFixed(4), 'peak (current)', peak.toFixed(4), 'analytic r_end', ok.rEnd.toFixed(4));
  console.log('  fig3a: 2016 code during pulse', during.toFixed(4), 'plateau', plateau.toFixed(4), 'plateau/peak', (plateau / peak).toFixed(4), '= 1/' + (peak / plateau).toFixed(2), 'value at 40 ms (current)', ok.r[ok.r.length - 1].toExponential(2));
  const a = new Panel({ x: 15, y: 7, w: 62, h: 42 }, [0, 40], [0, 0.8], { id: 'f3a', tag: 'a', xlabel: 'Time (ms)', ylabel: 'Bound fraction r', yticks: [0, 0.2, 0.4, 0.6, 0.8], xticks: [0, 10, 20, 30, 40], yfmt: v => v.toFixed(1) });
  a.poly(ok.ts, ok.r, { width: 0.35 }).poly(bad.ts, bad.r, { width: 0.35, stroke: VERM, dash: '1.2 0.6' });
  o.push(a.render());
  o.push(legend(40, 12, [{ label: 'closed form (current)', stroke: BLACK }, { label: 'December 2016 code', stroke: VERM, dash: '1.2 0.6' }]));
  const f1 = series(path.join(RUNS, 's17-full'), 'forca-SOL'), f2 = series(path.join(RUNS, 's17-frozen'), 'forca-SOL');
  const b = new Panel({ x: 103, y: 7, w: 68, h: 42 }, [0, 5], [0, 500], { id: 'f3b', tag: 'b', xlabel: 'Time (s)', ylabel: 'Soleus force (N)', yticks: [0, 100, 200, 300, 400, 500] });
  b.line(sec(f1.t), f1.v, { width: 0.35 }).line(sec(f2.t), f2.v, { width: 0.35, stroke: VERM, dash: '1.2 0.6' });
  o.push(b.render());
  emit('Fig4', W, H, o.join('\n'));
})();

// ---------------------------------------------------------------- Fig. 4: one integration step
(function fig4() {
  const W = 174, H = 64, o = [];
  const y1 = 12, h1 = 16, y2 = 36, h2 = 16;
  o.push(text(2, y1 + 9, 'Simulation', { bold: true }), text(2, y1 + 12.5, 'thread', { bold: true }));
  o.push(text(2, y2 + 9, 'Worker', { bold: true }), text(2, y2 + 12.5, 'threads', { bold: true }));
  const bx = (x, y, w, h, lines, fill) => box(x, y, w, h, { fill }) + boxText(x, y, w, h, lines, { boldFirst: true, lh: 3.3 });
  o.push(bx(22, y1, 22, h1, ['Nerves', 'stimulus', 'pulses']));
  o.push(bx(48, y1, 38, h1, ['Tracts, afferents, noise', 'random draws, in the', 'order of the serial loop'], '#F2F2F2'));
  o.push(bx(48, y2, 38, h2, ['Motoneurons, interneurons', 'Runge-Kutta, no draws,', 'chunks of 8 neurons']));
  o.push(bx(98, y1, 30, h1, ['Deliver spikes', 'fired in this step,', 'in serial order'], '#F2F2F2'));
  o.push(bx(132, y1, 20, h1, ['Muscles', 'force, EMG,', 'receptors']));
  o.push(bx(156, y1, 16, h1, ['Joint', 'torque,', 'angle']));
  o.push(arrow(44, y1 + h1 / 2, 48, y1 + h1 / 2), arrow(128, y1 + h1 / 2, 132, y1 + h1 / 2), arrow(152, y1 + h1 / 2, 156, y1 + h1 / 2));
  // barriers
  o.push(`<path d="M46,${y1 - 3}V${y2 + h2 + 2}" stroke="#000" stroke-width="0.3" stroke-dasharray="0.9 0.7" fill="none"/>`, text(46, y1 - 4.5, 'start barrier', { anchor: 'middle' }));
  o.push(`<path d="M92,${y1 - 3}V${y2 + h2 + 2}" stroke="#000" stroke-width="0.3" stroke-dasharray="0.9 0.7" fill="none"/>`, text(92, y1 - 4.5, 'end barrier', { anchor: 'middle' }));
  o.push(arrow(86, y1 + h1 / 2, 98, y1 + h1 / 2), elbow([[86, y2 + h2 / 2], [95, y2 + h2 / 2], [95, y1 + h1 / 2 + 3]]));
  o.push(elbow([[40, y1 + h1], [40, y2 + h2 / 2], [48, y2 + h2 / 2]]));
  o.push(text(98, y2 + 6, 'A spike delivered at step n is due at', {}), text(98, y2 + 9.5, 'step n + 1 or later, because every', {}), text(98, y2 + 13, 'synaptic delay exceeds one step', {}));
  o.push(`<path d="M22,${H - 3}H172" stroke="#000" stroke-width="0.25" fill="none" marker-end="url(#ah)"/>`, text(97, H - 4.4, 'one integration step (0.05 ms in the public scenarios)', { anchor: 'middle' }));
  emit('Fig2', W, H, o.join('\n'));
})();
