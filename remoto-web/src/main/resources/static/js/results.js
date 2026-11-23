// ReMoto — tela de resultados do motor original, no modelo da tela do site antigo (results.jsp):
// tipo, músculo, elemento neural, neurônio, gráfico e especificação; opções de EMG; análise
// (estatísticas e FFT); e os ajustes avançados (subgráficos, sobreposição, eixos, rótulos, cores).
// Os cálculos são do servidor (/api/runs/{id}/view); aqui ficam os menus e os gráficos uPlot.
(() => {
  'use strict';

  // textos no idioma da página (/js/i18n.js)
  const T = (key, ...args) => (window.T ? window.T(key, ...args) : key);

  const COLORS = { red: '#d62728', blue: '#1f5f99', green: '#2ca02c', yellow: '#bcbd22', black: '#222222', cyan: '#17becf' };
  const PALETTE = ['#1f5f99', '#d62728', '#2ca02c', '#9467bd', '#ff7f0e', '#17becf', '#8c564b', '#e377c2'];
  const NUMBERS = ['fcLow', 'fcHigh', 'emgDecimation', 'fftPoints', 'xmin', 'xmax', 'ymin', 'ymax',
    'fftXmin', 'fftXmax', 'fftYmin', 'fftYmax'];

  let state = null;

  function el(tag, attrs = {}, ...children) {
    const e = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs)) {
      if (k === 'class') e.className = v;
      else if (k === 'text') e.textContent = v;
      else if (k.startsWith('on')) e.addEventListener(k.substring(2), v);
      else if (v === true) e.setAttribute(k, '');
      else if (v !== false && v != null) e.setAttribute(k, v);
    }
    for (const c of children) if (c != null) e.append(c);
    return e;
  }

  function select(name, options, value) {
    const s = el('select', { name });
    for (const o of options) s.append(el('option', { value: o.value, text: o.label }));
    if (value != null && options.some((o) => o.value === value)) s.value = value;
    return s;
  }

  function labeled(text, control) {
    return el('label', { class: 'campo-resultado' }, el('span', { text }), control);
  }

  function input(name, value, attrs = {}) {
    return el('input', Object.assign({ name, value: value ?? '', size: 6 }, attrs));
  }

  function checkbox(name, checked) {
    return el('input', { type: 'checkbox', name, checked: !!checked });
  }

  // ---- montagem ----------------------------------------------------------------------------

  async function show(runId, root) {
    const menu = await (await fetch(`/api/runs/${runId}/menu`)).json();
    if (!menu.types || menu.types.length === 0) return false;
    state = { runId, menu, root, subplots: [], current: 0 };
    root.replaceChildren();
    root.hidden = false;
    root.append(el('h3', { text: 'Results' }));
    if (!menu.complete) {
      root.append(el('p', { class: 'vazio',
        text: T('oldRun') }));
    }
    const form = el('form', { class: 'form-resultados', onsubmit: (e) => { e.preventDefault(); plot(); } });
    state.form = form;
    root.append(form, el('div', { class: 'aviso-resultado', 'data-r': 'aviso' }), el('div', { 'data-r': 'graficos', class: 'subgraficos' }));
    rebuild();
    await plot();
    return true;
  }

  // valores atuais do formulário, para reconstruir os menus sem perder as escolhas
  function values() {
    const v = {};
    if (!state.form) return v;
    for (const e of state.form.elements) {
      if (!e.name) continue;
      v[e.name] = e.type === 'checkbox' ? e.checked : e.value;
    }
    return v;
  }

  function rebuild(changed) {
    const v = values();
    const m = state.menu;
    const f = state.form;
    f.replaceChildren();
    const type = v.type ?? m.types[0].value;
    const row = el('div', { class: 'linha-resultado' });
    row.append(labeled('Type', select('type', m.types, type)));
    f.append(row);

    let graphic = null;
    if (type === 'biomechanical') {
      const muscle = pick(v.nucleus, m.muscles);
      row.append(labeled('Muscle', select('nucleus', m.muscles, muscle)));
      const graphics = m.biomechanical[muscle] || [];
      graphic = pick(v.graphic, graphics);
      const g = graphics.find((x) => x.value === graphic);
      row.append(labeled('Graphic', select('graphic', graphics, graphic)));
      if (['muscleForce', 'EMG', 'MUsPosition', 'twitchPeak', 'contractionTime', 'halfRelaxation', 'amplitudeMUAP'].includes(graphic)) {
        const units = (m.motorUnits[muscle] || []).filter((u) => graphic === 'muscleForce' || graphic === 'EMG'
          ? !/^(S|FR|FF)$/.test(u.value) : !u.value.includes(':'));
        row.append(labeled('Motor units', select('neuron', units, pick(v.neuron, units))));
      }
      if (graphic === 'EMG') f.append(emgOptions(v));
      void g;
    } else if (type === 'neural') {
      const elements = m.neural.map((e) => ({ value: e.value, label: e.label }));
      const element = pick(v.element, elements);
      const e = m.neural.find((x) => x.value === element);
      row.append(labeled('Neural element', select('element', elements, element)));
      const nucleus = pick(v.nucleus, e.nuclei);
      if (e.nuclei.length > 1 || element !== 'dts') row.append(labeled(element === 'ins' ? 'Pool' : 'Muscle', select('nucleus', e.nuclei, nucleus)));
      else f.append(el('input', { type: 'hidden', name: 'nucleus', value: nucleus }));
      const graphics = e.graphics[nucleus] || [];
      graphic = pick(v.graphic, graphics);
      const g = graphics.find((x) => x.value === graphic);
      let neurons = e.neurons[nucleus] || [];
      if (g && g.stored) neurons = neurons.filter((n) => n.value.includes(':'));
      row.append(labeled(element === 'mns' ? 'Motoneuron' : element === 'afs' ? 'Afferent' : element === 'dts' ? 'Tract' : 'Interneuron',
        select('neuron', neurons, pick(v.neuron, neurons))));
      row.append(labeled('Graphic', select('graphic', graphics, graphic)));
      if (g && g.specs.length) row.append(labeled('Specification', select('spec', g.specs, pick(v.spec, g.specs))));
    } else if (type === 'properties') {
      const tables = m.properties;
      row.append(labeled('Table', select('table', tables, pick(v.table, tables))));
    }
    if (type === 'neural' || type === 'biomechanical') {
      f.append(analysisOptions(v));
      f.append(advancedOptions(v));
    }
    const buttons = el('div', { class: 'acoes acoes-resultado' },
      el('button', { type: 'submit', text: 'Plot' }),
      el('a', { href: '#', class: 'botao secundario', text: 'Download CSV', onclick: (ev) => { ev.preventDefault(); download(); } }),
      el('a', { href: '#', class: 'botao secundario', text: 'Download PNG', onclick: (ev) => { ev.preventDefault(); png(); } }));
    f.append(buttons);
    for (const e of f.elements) {
      if (['type', 'nucleus', 'element', 'graphic', 'neuron', 'analysis', 'analysisOn', 'advanced', 'numSubplots', 'emgFiltering'].includes(e.name)) {
        e.addEventListener('change', () => {
          rebuild(e.name);
          if (['type', 'nucleus', 'element', 'graphic', 'neuron'].includes(e.name) && !values().holdOn) plot();
          if (e.name === 'numSubplots') layoutSubplots();
        });
      }
    }
    void changed;
  }

  function pick(value, options) {
    if (!options || options.length === 0) return '';
    return options.some((o) => o.value === value) ? value : options[0].value;
  }

  function emgOptions(v) {
    const box = el('fieldset', { class: 'opcoes-resultado' }, el('legend', { text: 'EMG' }));
    const filtering = v.emgFiltering ?? true;
    box.append(labeled('Sampling', select('emgDecimation',
      [{ value: '1', label: 'all samples' }, { value: '2', label: '1 of 2' }, { value: '5', label: '1 of 5' }],
      v.emgDecimation ?? '1')));
    box.append(labeled('EMG filtering', checkbox('emgFiltering', filtering)));
    if (filtering) {
      box.append(labeled('From [Hz]', input('fcLow', v.fcLow ?? '50')), labeled('to [Hz]', input('fcHigh', v.fcHigh ?? '1000')));
      box.append(labeled('Filter', select('emgFilter', [
        { value: '1st2nd', label: '1st order low - 2nd order high' },
        { value: '3rd3rd', label: '3rd order low - 3rd order high' }], v.emgFilter ?? '1st2nd')));
    }
    box.append(labeled('EMG noise', checkbox('emgNoise', v.emgNoise ?? true)));
    box.append(labeled('EMG attenuation', checkbox('emgAttenuation', v.emgAttenuation ?? true)));
    return box;
  }

  function analysisOptions(v) {
    const box = el('fieldset', { class: 'opcoes-resultado' });
    box.append(labeled('Analysis', checkbox('analysisOn', v.analysisOn)));
    if (v.analysisOn) {
      const kind = v.analysis || 'parameters';
      box.append(select('analysis', [{ value: 'parameters', label: 'Basic statistical parameters' }, { value: 'fft', label: 'FFT' }], kind));
      if (kind === 'parameters') {
        for (const [name, label] of [['sPoints', 'Number of points'], ['sMean', 'Mean'], ['sVariance', 'Variance'],
          ['sStd', 'Standard deviation'], ['sCv', 'Coeficient of variation'], ['sMin', 'Minimum value'], ['sMax', 'Maximum value']]) {
          box.append(labeled(label, checkbox(name, v[name] ?? true)));
        }
      } else {
        box.append(labeled('Number of points used in FFT', input('fftPoints', v.fftPoints ?? '')),
          labeled('Periodic signal', checkbox('periodic', v.periodic)),
          labeled('X axis', input('fftXmin', v.fftXmin)), labeled('to', input('fftXmax', v.fftXmax)),
          labeled('Y axis', input('fftYmin', v.fftYmin)), labeled('to', input('fftYmax', v.fftYmax)));
      }
    }
    return box;
  }

  function advancedOptions(v) {
    const box = el('fieldset', { class: 'opcoes-resultado' });
    box.append(labeled('Advanced Graphic Settings', checkbox('advanced', v.advanced)));
    if (!v.advanced) return box;
    const n = Number(v.numSubplots || 1);
    const subplots = Array.from({ length: n }, (_, i) => ({ value: String(i), label: String(i + 1) }));
    box.append(labeled('Number of subplots', select('numSubplots', [1, 2, 3, 4, 5, 6, 7, 8].map((i) => ({ value: String(i), label: String(i) })), String(n))),
      labeled('Subplot', select('cdSubplot', subplots, pick(v.cdSubplot, subplots))),
      labeled('Hold On', checkbox('holdOn', v.holdOn)),
      labeled('X axis', input('xmin', v.xmin)), labeled('to', input('xmax', v.xmax)),
      labeled('Y axis', input('ymin', v.ymin)), labeled('to', input('ymax', v.ymax)),
      labeled('Title', input('titleLabel', v.titleLabel, { size: 24 })),
      labeled('X label', input('xLabel', v.xLabel, { size: 16 })),
      labeled('Y label', input('yLabel', v.yLabel, { size: 16 })),
      labeled('Legend', input('legendLabel', v.legendLabel, { size: 16 })),
      labeled('Random colors', checkbox('randomColors', v.randomColors)),
      labeled('Plot color', select('graphColor', Object.keys(COLORS).map((c) => ({ value: c, label: c[0].toUpperCase() + c.slice(1) })), v.graphColor ?? 'blue')),
      labeled('Darker', checkbox('darker', v.darker)),
      labeled('Chart height', input('chartHeight', v.chartHeight ?? '300')),
      labeled('Font size', input('fontSize', v.fontSize ?? '12')));
    return box;
  }

  // ---- consulta ----------------------------------------------------------------------------

  function query(v) {
    const p = new URLSearchParams();
    const type = v.type;
    if (type === 'properties') {
      const [element, nucleus] = (v.table || '').split(':');
      p.set('type', 'properties'); p.set('element', element); p.set('nucleus', nucleus);
      return p;
    }
    p.set('type', type);
    for (const k of ['element', 'nucleus', 'neuron', 'graphic', 'spec']) if (v[k]) p.set(k, v[k]);
    if (v.graphic === 'EMG') {
      p.set('emgFilter', v.emgFiltering === false ? 'noFiltering' : (v.emgFilter || '1st2nd'));
      p.set('emgNoise', !!v.emgNoise); p.set('emgAttenuation', !!v.emgAttenuation);
    }
    if (v.analysisOn) {
      p.set('analysis', v.analysis || 'parameters');
      p.set('periodic', !!v.periodic);
    }
    for (const k of NUMBERS) {
      if (v[k] != null && String(v[k]).trim() !== '' && (v.advanced || !['xmin', 'xmax', 'ymin', 'ymax'].includes(k))) {
        p.set(k, String(v[k]).replace(',', '.'));
      }
    }
    return p;
  }

  async function fetchView(params) {
    let r;
    try {
      r = await fetch(`/api/runs/${state.runId}/view?${params}`);
    } catch (e) {
      return { error: T('sseOffline') };
    }
    if (!r.ok) {
      const problem = await r.json().catch(() => ({}));
      return { error: problem.detail || `erro ${r.status}` };
    }
    return r.json();
  }

  async function plot() {
    const v = values();
    const aviso = state.root.querySelector('[data-r="aviso"]');
    aviso.textContent = '';
    if (v.type && v.type.startsWith('preset:')) return plotPreset(v.type.substring(7));
    if (v.type === 'summary' || v.type === 'properties') {
      const view = await fetchView(v.type === 'summary' ? new URLSearchParams({ type: 'summary' }) : query(v));
      return showTable(view);
    }
    const n = Number(v.advanced ? (v.numSubplots || 1) : 1);
    ensureSubplots(n);
    const index = v.advanced ? Math.min(Number(v.cdSubplot || 0), n - 1) : 0;
    const view = await fetchView(query(v));
    if (view.error) { aviso.textContent = view.error; return; }
    if (view.message) aviso.textContent = view.message;
    const slot = state.subplots[index];
    const overlay = v.advanced && v.holdOn && slot.views.length > 0;
    slot.views = overlay ? slot.views.concat([view]) : [view];
    slot.params = query(v);
    render(slot, v);
    state.current = index;
  }

  async function plotPreset(name) {
    const preset = state.menu.presets.find((p) => p.value === name);
    if (!preset) return;
    ensureSubplots(preset.subplots.length);
    const v = values();
    await Promise.all(preset.subplots.map(async (s, i) => {
      const p = new URLSearchParams();
      for (const k of ['type', 'element', 'nucleus', 'neuron', 'graphic', 'spec']) if (s[k]) p.set(k, s[k]);
      const view = await fetchView(p);
      const slot = state.subplots[i];
      slot.views = view.error ? [] : [view];
      slot.params = p;
      slot.title = s.label;
      render(slot, v, s.label);
    }));
  }

  function ensureSubplots(n) {
    const box = state.root.querySelector('[data-r="graficos"]');
    // a tela de propriedades ou de resumo deixa uma tabela no lugar dos gráficos
    if (box.querySelector('.tabela-resultados')) box.replaceChildren();
    while (state.subplots.length < n) {
      const div = el('div', { class: 'grafico' });
      box.append(div);
      state.subplots.push({ div, views: [], chart: null, params: null });
    }
    while (state.subplots.length > n) {
      const s = state.subplots.pop();
      if (s.chart) s.chart.destroy();
      s.div.remove();
    }
  }

  function layoutSubplots() {
    const v = values();
    ensureSubplots(Number(v.numSubplots || 1));
  }

  // ---- desenho -----------------------------------------------------------------------------

  function color(v, i) {
    if (v.advanced && v.randomColors) return PALETTE[Math.floor(Math.random() * PALETTE.length)];
    const base = v.advanced && v.graphColor ? COLORS[v.graphColor] : null;
    if (i === 0 && base) return v.darker ? shade(base) : base;
    const c = PALETTE[i % PALETTE.length];
    return v.advanced && v.darker ? shade(c) : c;
  }

  function shade(hex) {
    const n = parseInt(hex.slice(1), 16);
    const f = (x) => Math.max(0, Math.round(x * 0.6));
    return '#' + [(n >> 16) & 255, (n >> 8) & 255, n & 255].map((x) => f(x).toString(16).padStart(2, '0')).join('');
  }

  function render(slot, v, presetTitle) {
    if (slot.chart) { slot.chart.destroy(); slot.chart = null; }
    slot.div.replaceChildren();
    const views = slot.views.filter((x) => x && x.series);
    if (views.length === 0 || views.every((x) => x.series.length === 0)) {
      const msg = slot.views[0] && slot.views[0].message;
      slot.div.append(el('p', { class: 'vazio', text: presetTitle ? `${presetTitle}: ${msg || 'sem dados'}` : (msg || 'Sem dados.') }));
      return;
    }
    const first = views[0];
    const adv = !!v.advanced;
    const title = (adv && v.titleLabel) || presetTitle || first.title;
    const xLabel = (adv && v.xLabel) || first.xLabel;
    const yLabel = (adv && v.yLabel) || first.yLabel;
    const font = `${Number((adv && v.fontSize) || 12)}px system-ui, sans-serif`;
    const height = Number((adv && v.chartHeight) || 300);
    const series = [{}];
    const data = [null];
    let k = 0;
    for (const view of views) {
      const points = view.kind === 'pontos';
      const bars = view.kind === 'histograma';
      for (const s of view.series) {
        const c = color(v, k);
        const isStat = /^Mean/.test(s.label);
        series.push({
          label: (adv && v.legendLabel && k === 0) ? v.legendLabel : s.label,
          stroke: isStat ? '#888' : c,
          fill: bars ? c + '99' : undefined,
          width: isStat ? 1 : 1.5,
          dash: isStat && s.label !== 'Mean' ? [4, 4] : undefined,
          // dispersão: o construtor de pontos do uPlot desenha todos os pontos, sem esconder os densos
          paths: points && !isStat ? uPlot.paths.points() : bars ? uPlot.paths.bars({ size: [0.95, Infinity], align: 0 }) : uPlot.paths.linear(),
          points: points && !isStat ? { show: false, size: 3, fill: c, stroke: c } : { show: false },
        });
        data.push([s.x, s.y]);
        k++;
      }
    }
    const axis = { font, labelFont: font, stroke: getComputedStyle(document.documentElement).getPropertyValue('--texto').trim() || '#222' };
    const scales = { x: { time: false }, y: {} };
    if (adv) {
      const num = (x) => (x != null && String(x).trim() !== '' ? Number(String(x).replace(',', '.')) : null);
      const [x0, x1, y0, y1] = [num(v.xmin), num(v.xmax), num(v.ymin), num(v.ymax)];
      if (x0 != null && x1 != null) scales.x.range = [x0, x1];
      if (y0 != null && y1 != null) scales.y.range = [y0, y1];
    }
    const opts = {
      mode: 2,
      title,
      width: Math.max(320, slot.div.clientWidth || 600),
      height,
      scales,
      axes: [Object.assign({ label: xLabel }, axis), Object.assign({ label: yLabel }, axis)],
      series,
    };
    slot.chart = new uPlot(opts, data, slot.div);
    const stats = first.stats;
    if (stats && v.analysisOn && (v.analysis || 'parameters') === 'parameters') {
      const items = [];
      const fmt = (x) => (Number.isFinite(x) ? Number(x.toPrecision(6)) : x);
      if (v.sPoints ?? true) items.push(`Number of points: ${stats.points}`);
      if (v.sMean ?? true) items.push(`Mean: ${fmt(stats.mean)}`);
      if (v.sVariance ?? true) items.push(`Variance: ${fmt(stats.variance)}`);
      if (v.sStd ?? true) items.push(`Standard deviation: ${fmt(stats.std)}`);
      if (v.sCv ?? true) items.push(`Coeficient of variation: ${fmt(stats.cv)}`);
      if (v.sMin ?? true) items.push(`Minimum value: ${fmt(stats.min)}`);
      if (v.sMax ?? true) items.push(`Maximum value: ${fmt(stats.max)}`);
      slot.div.append(el('ul', { class: 'estatisticas' }, ...items.map((t) => el('li', { text: t }))));
    }
  }

  function showTable(view) {
    const box = state.root.querySelector('[data-r="graficos"]');
    state.subplots.forEach((s) => s.chart && s.chart.destroy());
    state.subplots = [];
    box.replaceChildren();
    const aviso = state.root.querySelector('[data-r="aviso"]');
    if (view.error || view.message) aviso.textContent = view.error || view.message;
    if (!view.table) return;
    const limit = 2000;
    const table = el('table', { class: 'tabela tabela-resultados' });
    table.append(el('thead', {}, el('tr', {}, ...view.table.columns.map((c) => el('th', { text: c })))));
    const body = el('tbody');
    for (const row of view.table.rows.slice(0, limit)) body.append(el('tr', {}, ...row.map((c) => el('td', { text: c }))));
    table.append(body);
    box.append(el('h4', { text: view.title }), el('div', { class: 'tabela-rolavel' }, table));
    if (view.table.rows.length > limit) {
      aviso.textContent = `Mostrando ${limit} de ${view.table.rows.length} linhas; o CSV tem todas.`;
    }
    state.tableParams = view.title === 'Summary' ? new URLSearchParams({ type: 'summary' }) : query(values());
  }

  function download() {
    const v = values();
    let params;
    if (v.type === 'summary' || v.type === 'properties') params = state.tableParams || query(v);
    else {
      const slot = state.subplots[state.current] || state.subplots[0];
      params = slot && slot.params ? slot.params : query(v);
    }
    window.location.href = `/api/runs/${state.runId}/view.csv?${params}`;
  }

  function png() {
    const slot = state.subplots[state.current] || state.subplots[0];
    if (!slot || !slot.chart) return;
    const canvas = slot.chart.ctx.canvas;
    const a = el('a', { href: canvas.toDataURL('image/png'), download: 'grafico.png' });
    document.body.append(a);
    a.click();
    a.remove();
  }

  function clear() {
    if (state) {
      state.subplots.forEach((s) => s.chart && s.chart.destroy());
      state.root.replaceChildren();
      state.root.hidden = true;
    }
    state = null;
  }

  window.ReMotoResults = { show, clear };
})();
