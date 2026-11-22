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
    const zip = link(`/api/runs/${runId}/export.zip`, 'tudo (ZIP)');
    zip.download = '';
    zip.title = 'Configuração e todos os resultados em CSV, num .zip';
    links.replaceChildren(link(`/api/runs/${runId}/config`, 'configuração (JSON)'), ' ', zip);

    // motor original: a tela de resultados no modelo do site antigo; os arquivos ficam num bloco recolhível
    if (window.ReMotoResults && await window.ReMotoResults.show(runId, field('resultados'))) {
      const files = document.createElement('details');
      const summary = document.createElement('summary');
      summary.textContent = `arquivos (${run.results.length} resultados em CSV)`;
      files.append(summary);
      for (const name of run.results) files.append(link(`/api/runs/${runId}/results/${name}.csv`, `${name}.csv`));
      links.append(files);
      return;
    }

    for (const name of run.results) {
      links.append(link(`/api/runs/${runId}/results/${name}.csv`, `${name}.csv`),
                   link(`/api/runs/${runId}/results/${name}.png`, `${name}.png`));
      // prévia reduzida no servidor; o CSV completo fica no link acima
      const preview = await (await fetch(`/api/runs/${runId}/results/${name}/preview?max=8000`)).json();
      const box = document.createElement('div');
      box.className = 'grafico';
      graphs.append(box);
      const title = preview.totalPoints > preview.x.length
        ? `${name} (prévia: ${preview.x.length} de ${preview.totalPoints} pontos)`
        : name;
      charts.push(new uPlot(chartOptions(name, title, yLabel(name, preview.header), box.clientWidth),
        [preview.x, preview.y], box));
    }
  }

  function yLabel(name, header) {
    if (name.startsWith('disparos-')) return 'unidade motora';
    const unit = /\[(.*)\]/.exec(header || '');
    const what = name.startsWith('forca-') ? 'força'
      : name.startsWith('emg-bruto-') ? 'EMG bruto'
      : name.startsWith('emg-') ? 'EMG'
      : name;
    return unit ? `${what} (${unit[1]})` : what;
  }

  function chartOptions(name, title, label, width) {
    const spikes = name.startsWith('disparos-');
    const color = getComputedStyle(document.documentElement).getPropertyValue('--serie').trim() || '#1f5f99';
    return {
      title,
      width: Math.max(320, width),
      height: 260,
      scales: { x: { time: false } },
      axes: [{ label: 'tempo (ms)' }, { label }],
      series: [
        { label: 't (ms)' },
        spikes
          ? { label: 'unidade', stroke: color, paths: () => null, points: { show: true, size: 3, fill: color } }
          : { label, stroke: color, width: 1.5 },
      ],
    };
  }

  function clearCharts() {
    if (window.ReMotoResults) window.ReMotoResults.clear();
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
