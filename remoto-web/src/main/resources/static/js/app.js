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

  // ---- avisos de erro: o htmx não troca o conteúdo quando o servidor responde com erro ----
  const notices = document.createElement('div');
  notices.className = 'avisos';
  notices.setAttribute('role', 'alert');
  document.body.append(notices);
  function notify(message) {
    if ([...notices.querySelectorAll('p')].some((p) => p.textContent === message)) return; // sem repetir
    const close = document.createElement('button');
    close.type = 'button';
    close.textContent = '×';
    close.setAttribute('aria-label', 'Fechar');
    const text = document.createElement('p');
    text.textContent = message;
    const box = document.createElement('div');
    box.className = 'aviso-flutuante';
    box.append(text, close);
    close.addEventListener('click', () => box.remove());
    notices.append(box);
    setTimeout(() => box.remove(), 12000);
  }
  window.ReMotoNotify = notify;

  const offline = document.createElement('div');
  offline.className = 'sem-conexao';
  offline.textContent = 'Sem conexão com o servidor. Tentando de novo…';
  offline.hidden = true;
  document.body.append(offline);

  document.body.addEventListener('htmx:responseError', (event) => {
    const xhr = event.detail.xhr;
    const text = (xhr.responseText || '').replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim();
    notify(text && text.length < 300 ? text : `Não foi possível concluir (erro ${xhr.status}).`);
  });
  document.body.addEventListener('htmx:sendError', () => { offline.hidden = false; });
  document.body.addEventListener('htmx:afterRequest', (event) => {
    if (event.detail.successful) offline.hidden = true;
  });

  const panel = document.getElementById('detalhe');
  const welcome = document.getElementById('boas-vindas');
  const field = (name) => panel.querySelector(`[data-campo="${name}"]`);
  const STATUS = { QUEUED: 'na fila', RUNNING: 'rodando', DONE: 'concluída', FAILED: 'falhou', CANCELED: 'cancelada' };
  const stacked = () => window.matchMedia('(max-width: 1000px)').matches;
  let source = null;
  let charts = [];
  let current = null;

  // ---- simulações: a linha inteira abre os resultados (os botões da linha fazem só o que dizem) ----
  document.addEventListener('click', (event) => {
    const row = event.target.closest('[data-run-id]');
    if (row && !event.target.closest('a, button, form, input, label')) showRun(row.dataset.runId);
  });
  document.addEventListener('keydown', (event) => {
    const row = event.target.closest && event.target.closest('[data-run-id]');
    if (row && event.target === row && (event.key === 'Enter' || event.key === ' ')) {
      event.preventDefault();
      showRun(row.dataset.runId);
    }
  });
  // o servidor pede para abrir a simulação que acabou de criar (botão Rodar)
  document.body.addEventListener('abrir-execucao', (event) => showRun(event.detail.value));

  function markSelected() {
    document.querySelectorAll('.linha-execucao').forEach((tr) =>
      tr.classList.toggle('selecionada', tr.dataset.runId === current));
  }

  function showRun(runId) {
    current = runId;
    markSelected();
    if (welcome) welcome.hidden = true;
    panel.hidden = false;
    field('id').textContent = runId;
    field('nome').textContent = 'Simulação';
    field('links').replaceChildren();
    clearCharts();
    if (source) source.close();
    fetch(`/api/runs/${runId}`).then((r) => r.ok ? r.json() : null).then((view) => {
      if (view && current === runId) field('nome').textContent = view.run.scenarioName;
    });

    const bar = panel.querySelector('progress');
    bar.hidden = false;
    bar.value = 0;
    source = new EventSource(`/api/runs/${runId}/events`);
    source.addEventListener('progresso', (event) => {
      const p = JSON.parse(event.data);
      const status = field('status');
      status.textContent = STATUS[p.status] || p.status;
      status.className = `status ${p.status}`;
      bar.value = Math.round(p.fraction * 100);
      if (['DONE', 'FAILED', 'CANCELED'].includes(p.status)) {
        source.close();
        bar.hidden = true;
        if (p.status === 'DONE') {
          loadResults(runId).catch(() => notify('Não foi possível carregar os resultados desta simulação.'));
        }
      }
    });
    source.onerror = () => {
      source.close();
      const status = field('status');
      if (current === runId && !['concluída', 'falhou', 'cancelada'].includes(status.textContent)) {
        status.textContent = 'sem conexão';
        status.className = 'status FAILED';
        notify('Perdi a conexão com o progresso da simulação. Clique na linha dela para tentar de novo.');
      }
    };
    if (stacked()) panel.scrollIntoView({ behavior: 'smooth', block: 'start' });
    else panel.parentElement.scrollTo({ top: 0, behavior: 'smooth' });
  }

  // ---- cenários: abas "Públicos" / "Meus cenários" e busca, mantidas quando a lista é recarregada ----
  function setupScenarios(root) {
    const box = root.querySelector('.cenarios');
    if (!box) return;
    const tab = box.dataset.abaInicial || sessionStorage.getItem('remoto.aba') || 'publicos';
    const search = box.querySelector('.busca');
    search.value = sessionStorage.getItem('remoto.busca') || '';
    const show = (name) => {
      sessionStorage.setItem('remoto.aba', name);
      box.querySelectorAll('.aba').forEach((b) => {
        const on = b.dataset.aba === name;
        b.classList.toggle('ativa', on);
        b.setAttribute('aria-selected', on);
      });
      box.querySelectorAll('.painel-aba').forEach((p) => { p.hidden = p.dataset.painel !== name; });
    };
    const filter = () => {
      const q = search.value.trim().toLowerCase();
      sessionStorage.setItem('remoto.busca', search.value);
      box.querySelectorAll('.painel-aba').forEach((p) => {
        let visible = 0;
        p.querySelectorAll('.cenario').forEach((li) => {
          const match = !q || li.dataset.busca.includes(q);
          li.hidden = !match;
          if (match) visible++;
        });
        const none = p.querySelector('.sem-resultado');
        if (none) none.hidden = !q || visible > 0;
      });
    };
    box.querySelectorAll('.aba').forEach((b) => b.addEventListener('click', () => show(b.dataset.aba)));
    search.addEventListener('input', filter);
    box.addEventListener('click', (event) => {
      const d = event.target.closest('.descricao');
      if (d) d.classList.toggle('aberta');
    });
    show(tab);
    filter();
  }

  document.body.addEventListener('htmx:afterSwap', (event) => {
    if (event.detail.target.id === 'cenarios') setupScenarios(event.detail.target);
    if (event.detail.target.id === 'execucoes') markSelected();
  });

  // ?run=<id>: o editor manda para cá depois de "Rodar este cenário"
  const requested = new URLSearchParams(location.search).get('run');
  if (requested) {
    history.replaceState(null, '', location.pathname);
    showRun(requested);
  }

  async function loadResults(runId) {
    const response = await fetch(`/api/runs/${runId}`);
    if (!response.ok) throw new Error(`erro ${response.status}`);
    const run = await response.json();
    const links = field('links');
    const graphs = field('graficos');
    const zip = link(`/api/runs/${runId}/export.zip`, 'Exportar (ZIP)');
    zip.download = '';
    zip.className = 'botao secundario pequeno';
    zip.title = 'Baixa a simulação num .zip: a configuração usada e todos os resultados em CSV';
    const config = link(`/api/runs/${runId}/config`, 'Configuração (JSON)');
    config.className = 'botao secundario pequeno';
    config.target = '_blank';
    links.replaceChildren(zip, config);

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
