// ReMoto — interações da página principal.
// htmx atualiza as listas; aqui ficam o token CSRF, o progresso via SSE e os gráficos uPlot.
(() => {
  'use strict';

  // textos no idioma da página (/js/i18n.js, montado a partir de messages*.properties)
  const T = (key, ...args) => (window.T ? window.T(key, ...args) : key);

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
    close.setAttribute('aria-label', T('close'));
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
  offline.textContent = T('offline');
  offline.hidden = true;
  document.body.append(offline);

  document.body.addEventListener('htmx:responseError', (event) => {
    const xhr = event.detail.xhr;
    const text = (xhr.responseText || '').replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim();
    notify(text && text.length < 300 ? text : T('requestFailed', xhr.status));
  });
  document.body.addEventListener('htmx:sendError', () => { offline.hidden = false; });
  document.body.addEventListener('htmx:afterRequest', (event) => {
    if (event.detail.successful) offline.hidden = true;
  });

  const panel = document.getElementById('detalhe');
  const welcome = document.getElementById('boas-vindas');
  const field = (name) => panel.querySelector(`[data-campo="${name}"]`);
  const FINISHED = ['DONE', 'FAILED', 'CANCELED'];
  const stacked = () => window.matchMedia('(max-width: 1000px)').matches;
  let source = null;
  let charts = [];
  let current = null;
  let lastStatus = null;

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
    field('nome').textContent = T('simulation');
    field('links').replaceChildren();
    clearCharts();
    if (source) source.close();
    fetch(`/api/runs/${runId}`).then((r) => r.ok ? r.json() : null).then((view) => {
      if (view && current === runId) field('nome').textContent = view.run.scenarioName;
    });

    const bar = panel.querySelector('progress');
    bar.hidden = false;
    bar.value = 0;
    lastStatus = null;
    source = new EventSource(`/api/runs/${runId}/events`);
    source.addEventListener('progresso', (event) => {
      const p = JSON.parse(event.data);
      const status = field('status');
      lastStatus = p.status;
      status.textContent = T(`status.${p.status}`);
      status.className = `status ${p.status}`;
      bar.value = Math.round(p.fraction * 100);
      if (FINISHED.includes(p.status)) {
        source.close();
        bar.hidden = true;
        if (p.status === 'DONE') {
          loadResults(runId).catch(() => notify(T('loadResultsFailed')));
        }
      }
    });
    source.onerror = () => {
      source.close();
      const status = field('status');
      if (current === runId && !FINISHED.includes(lastStatus)) {
        status.textContent = T('noConnection');
        status.className = 'status FAILED';
        notify(T('progressLost'));
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
    const zip = link(`/api/runs/${runId}/export.zip`, T('export.zip'));
    zip.download = '';
    zip.className = 'botao secundario pequeno';
    zip.title = T('export.zip.title');
    const config = link(`/api/runs/${runId}/config`, T('config.json'));
    config.className = 'botao secundario pequeno';
    config.target = '_blank';
    links.replaceChildren(zip, config);

    // motor original: a tela de resultados no modelo do site antigo; os arquivos ficam num bloco recolhível
    if (window.ReMotoResults && await window.ReMotoResults.show(runId, field('resultados'))) {
      const files = document.createElement('details');
      const summary = document.createElement('summary');
      summary.textContent = T('files', run.results.length);
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
        ? T('preview', name, preview.x.length, preview.totalPoints)
        : name;
      charts.push(new uPlot(chartOptions(name, title, yLabel(name, preview.header), box.clientWidth),
        [preview.x, preview.y], box));
    }
  }

  function yLabel(name, header) {
    if (name.startsWith('disparos-')) return T('y.motorUnit');
    const unit = /\[(.*)\]/.exec(header || '');
    const what = name.startsWith('forca-') ? T('y.force')
      : name.startsWith('emg-bruto-') ? T('y.rawEmg')
      : name.startsWith('emg-') ? T('y.emg')
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
      axes: [{ label: T('x.time') }, { label }],
      series: [
        { label: 't (ms)' },
        spikes
          ? { label: T('series.unit'), stroke: color, paths: () => null, points: { show: true, size: 3, fill: color } }
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
