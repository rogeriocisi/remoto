// ReMoto — interações da página principal.
// htmx atualiza as listas; aqui ficam o token CSRF, o progresso via SSE e os gráficos uPlot.
(() => {
  'use strict';

  const cookie = (name) => {
    const entry = document.cookie.split('; ').find((c) => c.startsWith(name + '='));
    return entry ? decodeURIComponent(entry.substring(name.length + 1)) : null;
  };

  // Spring Security (csrf.spa()) grava o token no cookie XSRF-TOKEN; o htmx o envia no cabeçalho.
  document.body.addEventListener('htmx:configRequest', (event) => {
    const token = cookie('XSRF-TOKEN');
    if (token) event.detail.headers['X-XSRF-TOKEN'] = token;
  });

  const panel = document.getElementById('detalhe');
  const field = (name) => panel.querySelector(`[data-campo="${name}"]`);
  let source = null;
  let charts = [];

  document.addEventListener('click', (event) => {
    const button = event.target.closest('[data-run-id]');
    if (button) showRun(button.dataset.runId);
  });

  function showRun(runId) {
    panel.hidden = false;
    field('id').textContent = runId;
    field('links').replaceChildren();
    clearCharts();
    if (source) source.close();

    const bar = panel.querySelector('progress');
    source = new EventSource(`/api/runs/${runId}/events`);
    source.addEventListener('progresso', (event) => {
      const p = JSON.parse(event.data);
      field('status').textContent = p.status;
      bar.value = Math.round(p.fraction * 100);
      if (['DONE', 'FAILED', 'CANCELED'].includes(p.status)) {
        source.close();
        if (p.status === 'DONE') loadResults(runId);
      }
    });
    source.onerror = () => source.close();
    panel.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  async function loadResults(runId) {
    const run = await (await fetch(`/api/runs/${runId}`)).json();
    const links = field('links');
    const graphs = field('graficos');
    links.replaceChildren(link(`/api/runs/${runId}/config`, 'configuração (JSON)'));

    for (const name of run.results) {
      links.append(link(`/api/runs/${runId}/results/${name}.csv`, `${name}.csv`),
                   link(`/api/runs/${runId}/results/${name}.png`, `${name}.png`));
      const text = await (await fetch(`/api/runs/${runId}/results/${name}.csv`)).text();
      const rows = text.trim().split('\n').slice(1);
      const x = new Float64Array(rows.length);
      const y = new Float64Array(rows.length);
      rows.forEach((line, i) => {
        const comma = line.indexOf(',');
        x[i] = +line.substring(0, comma);
        y[i] = +line.substring(comma + 1);
      });
      const box = document.createElement('div');
      box.className = 'grafico';
      graphs.append(box);
      charts.push(new uPlot(chartOptions(name, box.clientWidth), [x, y], box));
    }
  }

  function chartOptions(name, width) {
    const spikes = name.startsWith('disparos-');
    const color = getComputedStyle(document.documentElement).getPropertyValue('--serie').trim() || '#1f5f99';
    return {
      title: name,
      width: Math.max(320, width),
      height: 260,
      scales: { x: { time: false } },
      axes: [{ label: 'tempo (ms)' }, { label: spikes ? 'unidade motora' : 'força (u.a.)' }],
      series: [
        { label: 't (ms)' },
        spikes
          ? { label: 'unidade', stroke: color, paths: () => null, points: { show: true, size: 3, fill: color } }
          : { label: 'força', stroke: color, width: 1.5 },
      ],
    };
  }

  function clearCharts() {
    charts.forEach((c) => c.destroy());
    charts = [];
    field('graficos').replaceChildren();
  }

  function link(href, text) {
    const a = document.createElement('a');
    a.href = href;
    a.textContent = text;
    return a;
  }
})();
