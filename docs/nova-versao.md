# ReMoto — nova versão (Java 25, sem banco de dados, sem login)

Nova versão do ReMoto, resultado da modernização do site original (Struts 1 + Tomcat 7). Os
modelos numéricos foram portados para o `remoto-core`, com saídas idênticas bit a bit às do código
original; do legado fica só o banco original em `legacy-db/`, lido sem o HSQLDB. O site JSP e o
código original continuam no histórico e no repositório pai.

> **Estado:** a infraestrutura (áreas sem login, fila, armazenamento, API, interface, CLI, CI)
> funciona e tem testes. Os **modelos originais do ReMoto rodam na nova plataforma** (motor
> `legacy`, ver [Motor original](#motor-original-legacy)), portados para o `remoto-core`, sem
> banco de dados em nenhuma etapa, com resultados reprodutíveis e conferidos contra o código e o
> banco originais. É o único motor: o provisório, que servia só para demonstração, foi removido.

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 25 LTS (records, enums, `RandomGenerator`) |
| Build | Maven multimódulo + Maven Wrapper (`./mvnw`) |
| Web | Spring Boot 4.1 (Spring MVC, Spring Security 7, Actuator) |
| Login | nenhum: cada navegador tem sua área (cookie de 30 dias); os cenários entram e saem como arquivos (.json, .zip) |
| Interface | Thymeleaf + htmx 2 + uPlot (gráficos interativos) |
| Idiomas | inglês e português, pelo idioma do navegador, com seletor em toda página |
| Figuras | JFreeChart 1.5 (PNG para artigos) |
| Persistência | Arquivos: JSON (cenários, manifestos) e CSV.gz (resultados) — **sem banco** |
| Execução | Fila justa por área, limite por endereço de origem + pool de threads de plataforma (CPU-bound) |
| Progresso | Server-Sent Events |
| Entrega | um JAR executável (servidor web embutido) rodando como serviço systemd; CI no GitHub Actions |

**Sem Docker.** Sem banco de dados, o ReMoto é um único processo Java que grava numa pasta. Basta
o JDK 25 no servidor e o arquivo `remoto-web.jar`; um contêiner só acrescentaria mais uma
ferramenta para instalar e manter.

## Módulos

```
remoto-core          cenário, modelos do ReMoto, motor "legacy", leitura do banco antigo (Java puro, sem dependências)
remoto-storage       repositórios em arquivo: cenários, execuções e a área de cada navegador
remoto-web           aplicação Spring Boot sem login (API REST + páginas), com o editor de cenários
remoto-cli           execução sem servidor: remoto run / sweep / validate / export-legacy
scenarios/           cenários públicos versionados no Git (o antigo usuário "guest")
```

## Como rodar

Requer JDK 25. O Maven é baixado pelo wrapper.

O jeito mais simples de subir o site em modo desenvolvimento é o script de start do seu terminal.
Ele procura um JDK 25 (no `JAVA_HOME`, no `PATH` ou nas pastas de instalação comuns), então
funciona mesmo se o Java padrão da máquina for mais antigo. O argumento opcional é a porta
(padrão 8080):

| Terminal | Comando |
|---|---|
| Windows (cmd) | `scripts\start.cmd` ou `scripts\start.cmd 9090` |
| Windows (PowerShell) | `.\scripts\start.ps1` ou `.\scripts\start.ps1 -Port 9090` |
| Linux, macOS, Git Bash | `scripts/start.sh` ou `scripts/start.sh 9090` |

Se o PowerShell bloquear scripts, use
`powershell -ExecutionPolicy Bypass -File .\scripts\start.ps1` ou a versão `.cmd`.

Os comandos equivalentes, sem os scripts:

```bash
# testes de todos os módulos
./mvnw verify

# servidor em modo desenvolvimento (templates relidos a cada requisição)
./mvnw -pl remoto-web -am spring-boot:run "-Dspring-boot.run.profiles=dev"
# → http://localhost:8080

# CLI
./mvnw -pl remoto-cli -am package -DskipTests
java -jar remoto-cli/target/remoto-cli-3.0.0-SNAPSHOT.jar run scenarios/remoto-17-default-soleus-contraction.json --duration-ms 500 -o resultados/teste
# protocolo em lote: a curva H/M, de 5 a 40 mA (ver "Protocolos em lote")
java -jar remoto-cli/target/remoto-cli-3.0.0-SNAPSHOT.jar sweep scenarios/remoto-17-default-soleus-contraction.json --protocol hm --range 5:40:1 -o resultados/curva-hm
```

No Windows, use `mvnw.cmd` no lugar de `./mvnw`. No PowerShell, mantenha as aspas em
`"-Dspring-boot.run.profiles=dev"`: sem elas, o PowerShell quebra o argumento nos pontos. Se o
Java padrão da máquina for anterior ao 25, aponte o `JAVA_HOME` para o JDK 25 antes
(`$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.2"`, ajustando o caminho).

## Instalação no servidor (Linux, sem Docker)

Os arquivos de apoio estão em `deploy/`: a unidade systemd (`remoto.service`) e o modelo de
configuração (`remoto.env.example`).

```bash
# 1. JDK 25 (Temurin, Corretto ou o pacote da distribuição)
java -version

# 2. Gerar o JAR (na sua máquina ou no próprio servidor)
./mvnw -pl remoto-web -am package -DskipTests

# 3. Usuário de sistema, pastas e arquivos
sudo useradd --system --home-dir /var/lib/remoto --shell /usr/sbin/nologin remoto
sudo install -d -o remoto -g remoto -m 750 /var/lib/remoto
sudo install -D -m 644 remoto-web/target/remoto-web-3.0.0-SNAPSHOT.jar /opt/remoto/remoto-web.jar
sudo mkdir -p /opt/remoto/scenarios && sudo cp scenarios/*.json /opt/remoto/scenarios/
sudo install -D -m 600 deploy/remoto.env.example /etc/remoto/remoto.env   # editar: porta, pastas, limites

# 4. Serviço
sudo cp deploy/remoto.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now remoto

# 5. Acompanhar
systemctl status remoto
journalctl -u remoto -f
curl -s http://localhost:8080/actuator/health
```

- **Atualizar:** substitua `/opt/remoto/remoto-web.jar` e rode `sudo systemctl restart remoto`.
  Execuções em andamento são canceladas e as que estavam na fila ficam marcadas como
  interrompidas; os cenários e as simulações terminadas de cada área continuam lá.
- **Backup:** opcional. As áreas ficam em `/var/lib/remoto/sessions` e duram até 30 dias sem uso;
  copiar essa pasta preserva o trabalho de quem está usando o site (numa troca de máquina, por
  exemplo). Os cenários públicos estão no Git, e o que é para durar cada pessoa guarda exportando.
- **HTTPS:** coloque nginx ou Apache na frente (porta 443 → 8080) e descomente
  `SERVER_FORWARD_HEADERS_STRATEGY=native` no `remoto.env`. Sem isso, o limite por endereço de
  origem vê todo mundo com o endereço do proxy.
- **Novos cenários públicos:** copie o JSON para `/opt/remoto/scenarios/`; não precisa reiniciar.
- **Windows:** o mesmo `java -jar` pode virar serviço com o WinSW, usando as variáveis do
  `remoto.env.example`.

## Sem login e sem banco

### Áreas e o cookie

Não há contas nem login. Cada navegador ganha uma área própria, identificada por um id aleatório
(`g-` + 32 dígitos hexadecimais) no cookie `remoto-area` (HttpOnly, SameSite=Lax e Secure sob
HTTPS), que dura **30 dias** e se renova a cada visita; tudo o que a pessoa faz (cópias de
cenários, edições, simulações) fica nessa área. Por isso cenários e simulações sobrevivem a
reinícios e quedas do servidor e a fechar o navegador. A área só é apagada depois de 30 dias sem
uso, pela tarefa diária de limpeza, ou quando a pessoa pede (`DELETE /api/me`). O prazo é um só,
`remoto.retention.area-days` (padrão 30): vale para a limpeza, para o cookie e para o aviso da
página. Como só ler não muda a data dos arquivos, cada área marca o último uso num
arquivo oculto (`.ultimo-uso`), no máximo uma vez por hora. O id também fica na sessão, para quem
bloqueia cookies continuar com a mesma área enquanto ela durar. Um cookie adulterado não alcança
outra pasta: o `UserId` só aceita o formato que ele mesmo gera.

Para guardar o trabalho por mais tempo, ou levá-lo a outro computador ou navegador, a pessoa
**exporta**: cada cenário em JSON (o mesmo arquivo que a CLI roda), todos os cenários num .zip,
uma simulação inteira num .zip (configuração, manifesto e todos os resultados em CSV). Lá, ou
depois do prazo, ela **importa** os .json, ou o .zip com eles, para "Meus cenários". A importação
confere cada cenário como o editor e a fila conferem (formato, duração, passos, motor e a
configuração do modelo), recusa arquivos de mais de 5 MB e importa até 100 cenários de uma vez;
os recusados aparecem com o nome do arquivo e o motivo. O site não guarda nenhum dado pessoal: o
cookie leva só o id aleatório da área.

O `UserId` chega pelo cookie, que pode ser alterado no navegador, mas só é aceito no formato que
o servidor gera; por isso é sempre seguro como nome de pasta. Ids de cenários e execuções são
ULIDs validados (`Ids.requireSafe`) contra path traversal.

### Layout dos arquivos

```
data/
└── sessions/<userId>/         uma pasta por área (navegador)
    ├── .ultimo-uso            data do último uso, para a limpeza
    ├── scenarios/<ulid>.json
    └── runs/<ulid>/
        ├── manifest.json      status, seed, versão do motor, hash da configuração, datas
        ├── config.json        cópia congelada da configuração usada
        └── results/*.csv.gz   séries temporais e disparos
```

### O que substitui as garantias do banco

| Garantia | Como |
|---|---|
| Atomicidade | grava em arquivo temporário e renomeia (`StorageFiles.writeAtomically`) |
| Concorrência entre áreas | uma trava por área (`UserLocks`); áreas diferentes gravam em paralelo |
| Edição em duas abas | ETag = SHA-256 do arquivo; `PUT` com `If-Match` desatualizado → `412` |
| Isolamento | todo acesso passa pelo `UserId` do `Caller`; outro navegador recebe `404` |
| Recuperação após queda | ao subir, execuções `QUEUED`/`RUNNING` viram `FAILED` ("interrompida"); as áreas continuam acessíveis pelo cookie |
| Levar os dados | exportar cada cenário (ou todos num `.zip`) e cada simulação; pela API, também a área inteira (`/api/me/export`) |
| Limpeza | tarefa diária: áreas sem uso há 30 dias e execuções antigas não fixadas |

### Várias pessoas simulando ao mesmo tempo

- Até `remoto.simulation.slots` simulações em paralelo (padrão: núcleos da CPU); cada uma ocupa
  uma thread porque o trabalho é de CPU. Com menos slots que núcleos, cada simulação do motor
  original usa `threads-per-run` threads (padrão: núcleos ÷ slots); o resultado não muda.
- **Fila justa** (`FairQueue`): uma fila por área, despachada em rodízio. Uma varredura de 50
  execuções de uma área não bloqueia a simulação única de outra.
- Cotas por área: `max-active-per-user` (padrão 2) e `max-queued-per-user` (padrão 20 → `429`).
- Sem login, abrir outra área é fácil (outro navegador, uma janela anônima), então há também
  `max-per-origin` (padrão 20), que soma as simulações rodando ou na fila de todas as áreas do
  mesmo endereço, e `max-queued-total` (padrão 200), o teto da fila do servidor; os dois
  respondem `429` e aceitam 0 para desligar.
  Uma sala com todos atrás do mesmo endereço (NAT) divide o limite por origem: aumente-o se for
  o caso.
- Limite real: **uma instância do servidor**. Para várias máquinas seria preciso fila
  compartilhada e armazenamento comum (S3/MinIO) — ver a proposta.

## Dois idiomas

O site sai **em inglês ou em português**, e a pessoa pode trocar a qualquer momento.

### Qual idioma

Por ordem (`RemotoLocaleResolver`):

1. a escolha da pessoa, no cookie `remoto-lang` (HttpOnly, SameSite=Lax, Secure sob HTTPS), que
   dura um ano;
2. o `Accept-Language` do navegador, que traz o idioma da região de onde se acessa: qualquer
   variante de português (pt, pt-BR, pt-PT) abre em português;
3. inglês, para os demais idiomas e para o navegador que não diz nada.

É o idioma do navegador, não o país do endereço: não há base de geolocalização no projeto, e o
cabeçalho acerta mais (quem está fora do Brasil com o navegador em português continua lendo em
português). O seletor fica no canto superior direito de toda página e é um link comum
(`?lang=pt`, `?lang=en`) que mantém os outros parâmetros, de modo que trocar de idioma no editor
não tira ninguém da tela em que estava. Como a escolha vira cookie, a página também funciona com
JavaScript desligado.

### Onde ficam os textos

| Onde | O quê |
|---|---|
| `messages.properties` | inglês, o pacote padrão (vale para qualquer idioma que não seja português) |
| `messages_pt.properties` | português |
| `/js/i18n.js` | as chaves `js.*`, montadas pelo `I18nScriptController` para o idioma do pedido; o navegador usa `window.T("chave", ...)` |
| `br.remoto.web.i18n.Messages` | `Messages.msg("chave", ...)` no servidor, inclusive nos objetos que não são beans (campos do editor, vistas dos resultados) |

`MessagesTest` falha se uma chave existir num arquivo e faltar no outro, ou se algum texto estiver
vazio: um texto novo sem tradução apareceria na página como a chave crua.

O formato da data das simulações (dia/mês ou mês/dia) e o separador decimal das durações
("5,0 s simulados" / "5.0 s simulated") também seguem o idioma.

### O que fica em inglês nos dois idiomas

- As **telas do editor** e os **menus da tela de resultados**: são os rótulos, as unidades e os
  nomes do site antigo (`LegacyScreens`, `ResultMenu`), que os artigos citam. Traduzi-los
  desencontraria a interface da literatura.
- Os **nomes e as descrições dos cenários**, que vêm do banco antigo e dos arquivos de
  `scenarios/`, e os nomes dos resultados (`forca-SOL`, `disparos-SOL`).
- Os **dados bibliográficos** da página de publicações (títulos, autores, veículos).

### A versão

O cabeçalho mostra a versão como o site antigo mostrava ("Current Version: 2.1"): o número vem do
`pom` pelo filtro de recursos do Maven (`remoto.version: @project.version@`) e a página mostra
maior.menor — "Versão 3.0" ou "Version 3.0".

### O site do projeto (GitHub Pages)

A apresentação em `docs/` é estática, então são dois arquivos: `docs/index.html` (inglês) e
`docs/pt/index.html` (português), com o CSS num só lugar (`docs/assets/site.css`), `canonical` e
`hreflang` apontando um para o outro. Um script pequeno manda a primeira visita para a versão do
idioma do navegador; quem clica no seletor fixa a escolha (`localStorage`), que o redirecionamento
nunca desfaz. Sem JavaScript não há redirecionamento, e o seletor continua funcionando. O
`scripts/pages-figure.mjs` regenera a figura de abertura nas duas páginas, com os rótulos de cada
idioma.

## Página principal

A página tem duas colunas, cada uma com a própria rolagem, para que os resultados fiquem sempre
à vista:

- **À esquerda**, *Minhas simulações* (cenário, status, criação e tempo de execução, do início ao
  fim) e, abaixo, os *Cenários*, em duas abas (*Públicos* e *Meus cenários*), com busca por
  nome, id ou descrição. Cada cenário mostra o id e as duas primeiras linhas da descrição (um
  clique mostra o resto) e os botões Rodar, Editar, Copiar, Exportar e, nos seus, Apagar.
- **À direita**, os resultados da simulação aberta. **Rodar** abre a simulação nova ali, com o
  progresso ao vivo; o "Rodar este cenário" do editor volta para a página com ela aberta. Um
  clique numa linha de *Minhas simulações* (ou Enter, pelo teclado) abre outra.

Cada simulação terminada tem **Exportar** (o .zip com o manifesto, a configuração e os resultados
em CSV), e o **Importar…** de *Minhas simulações* traz esse .zip de volta, com um id novo: o
manifesto e a configuração são conferidos (pelo hash gravado no manifesto), os nomes dos
resultados são validados, e o .zip pode ter até 5.000 arquivos e 2 GB descompactados. Em telas
estreitas, as duas colunas ficam uma embaixo da outra.

## API REST

| Método | Caminho | Descrição |
|---|---|---|
| GET | `/api/me` | a área do navegador (id e espaço ocupado) |
| GET | `/api/me/export` | a área inteira (.zip) |
| DELETE | `/api/me` | cancela as simulações, apaga a área e o cookie |
| GET | `/api/scenarios/public` | cenários públicos |
| POST | `/api/scenarios/public/{id}/copy` | copia um cenário público para a área |
| GET | `/api/scenarios/public/{id}/export` | baixa um cenário público (.json) |
| GET / POST | `/api/scenarios` | meus cenários / criar |
| GET / PUT / DELETE | `/api/scenarios/{id}` | ler (com `ETag`) / substituir (`If-Match`) / apagar |
| GET | `/api/scenarios/{id}/export` | baixa o cenário (.json, sem o id) |
| GET | `/api/scenarios/export` | baixa todos os cenários da área (.zip, um .json cada) |
| POST | `/api/scenarios/import` | importa arquivos `files` (.json ou .zip), multipart; devolve os criados e os recusados |
| POST | `/api/runs` | executa: `{"scenario": {...}}` ou `{"scenarioId"}`, `{"publicScenarioId"}` |
| GET | `/api/runs`, `/api/runs/{id}` | minhas execuções / detalhe com nomes dos resultados |
| GET | `/api/runs/{id}/events` | progresso ao vivo (SSE, evento `progresso`) |
| DELETE | `/api/runs/{id}` | cancela (se ativa) ou apaga |
| PUT | `/api/runs/{id}/pinned` | fixa (`true`) para escapar da retenção |
| GET | `/api/runs/{id}/config` | configuração exata usada |
| GET | `/api/runs/{id}/export.zip` | a execução inteira: manifesto, configuração e resultados em CSV |
| POST | `/api/runs/import` | importa um .zip exportado (`file`, multipart) como execução nova da área |
| GET | `/api/runs/{id}/results/{nome}.csv` | resultado em CSV |
| GET | `/api/runs/{id}/results/{nome}.png` | figura em PNG |
| GET | `/api/runs/{id}/menu` | menus da tela de resultados para a execução (motor original) |
| GET | `/api/runs/{id}/view?type=&element=&nucleus=&neuron=&graphic=&spec=...` | um gráfico ou tabela da tela de resultados, em JSON |
| GET | `/api/runs/{id}/view.csv?...` | o mesmo, em CSV (a opção "file" do site antigo) |

Requisições que alteram dados exigem o token CSRF (cookie `XSRF-TOKEN` → cabeçalho
`X-XSRF-TOKEN`). Erros seguem RFC 9457 (`application/problem+json`).

## Formato do cenário (schemaVersion 1)

```json
{
  "name": "Default - Soleus contraction",
  "description": "…",
  "schemaVersion": 1,
  "seed": 17,
  "durationMs": 5000,
  "stepMs": 0.05,
  "engine": "legacy",
  "legacy": { "format": "remoto-legacy-db/1", "idConfiguration": 17, "tables": { "…": "…" } }
}
```

A mesma configuração + a mesma `seed` + a mesma versão do motor reproduzem o resultado bit a bit.

O campo `engine` escolhe o motor. Há um, `"legacy"` (o padrão quando o campo falta), que lê a
configuração do modelo no campo `legacy`, descrito abaixo; o campo fica para motores futuros que
leiam o mesmo cenário. Um motor desconhecido é recusado com `400`. Um motor provisório, com um
conjunto simplificado de motoneurônios por núcleo, movidos por uma corrente de comando e um
ruído (campo `nuclei`), serviu de demonstração enquanto os modelos eram portados e foi removido; execuções antigas dele continuam
abrindo, com os gráficos simples.

## Motor original (`legacy`)

Os modelos publicados do ReMoto (motoneurônios S/F com dendritos ativos, condutâncias, Markov,
interneurônios, sinapses com depressão/facilitação, Hill/Raikova/SOCDS, fuso, OTG, articulação)
rodam na nova plataforma **com os mesmos resultados, bit a bit, do código numérico do site
antigo**:

- **Portados para o `remoto-core`:** os modelos ficam em `br.remoto.core.model` (canais,
  sinapses, membranas, fibras aferentes, tratos, sinais, estímulos, unidades motoras, músculos,
  fusos, OTG, articulações), e a montagem da rede e o laço de simulação em
  `br.remoto.core.simulation`: a `Simulation`, as fábricas, as classes de cada elemento (que
  ligam a configuração aos modelos) e, em `simulation.setup`, a `Configuration` com os objetos que
  o editor edita. O código original (104 arquivos do `src/` do repositório pai, que ficavam no
  módulo `remoto-legacy-model`) foi substituído fatia por fatia, e o módulo deixou de existir;
  Struts, JSPs, applets e o JFreeChart antigo já tinham saído do repositório.
- **Reprodutível:** o código original usava `Math.random()` e `new Random()` sem seed. Agora
  todas essas chamadas passam por `Rng` (`br.remoto.core.model.random`), um gerador por thread
  que o motor inicializa com a `seed` do cenário.
- **Correção das sinapses (motor `remoto-legacy-2b5-rng1-syn1`):** em dezembro de 2016 o
  repositório pai trocou, em `MultSynapticConductance` (hoje `SynapseGroup`, no `remoto-core`), o tempo desde o último evento
  (`t − t0`) pelo passo de integração no decaimento de `Ron` e `Roff`. Como `ron` e `roff` só
  mudam quando um pulso começa ou termina, a condutância passou a ficar congelada entre eventos:
  um disparo isolado subia a um sétimo do pico e não decaía mais. O motor volta à solução
  fechada de Lytton (1996), que é a da versão de 2011 e a vigente quando o artigo de 2015 foi
  configurado; `SynapseGroupTest` compara a condutância com essa solução. Resultados de
  versões diferentes do motor não são comparáveis.
- **Sem prints no laço:** o código original imprimia no console a cada passo de cada trato
  descendente (`"Poisson " + mean`), a cada disparo de sinapse com dinâmica, a cada passo da
  tarefa de posição e do simulador de fuso, e a cada neurônio e sinapse criados. Isso foi
  removido, assim como os prints de depuração da montagem; as mensagens de erro e os avisos de
  criação de modelos ficaram.
- **Desempenho:** sinapses e força muscular sem buscas por texto a cada passo, e atualização
  paralela dos neurônios, com o mesmo resultado bit a bit (ver [Desempenho](#desempenho)).
- **Configuração sem banco:** cada configuração do HSQLDB antigo vira um cenário JSON com as
  linhas de cada tabela (formato `remoto-legacy-db/1`, sem a tabela USER). Para rodar, essas
  linhas viram parâmetros tipados no `remoto-core` (`br.remoto.core.model.config`: um record por
  tabela, reunidos em `ModelConfig`), e o `ConfigurationAssembler` (a leitura do
  `ConfigurationDAO` original, sem JDBC) monta a `Configuration` a partir deles, com o mesmo
  mapeamento de sempre. O leitor (`LegacyTablesReader`)
  reproduz o que o HSQLDB fazia: `NUMERIC(128,4)` arredondado para 4 casas com a metade para
  baixo, decimal em coluna inteira truncado, nulo lido como 0, e a ordem das linhas do
  `ORDER BY` de cada consulta do DAO, com o mesmo algoritmo de ordenação do HSQLDB 2.7.3. Esse
  algoritmo não é estável, e a ordem dos empates (os tratos de um mesmo núcleo, por exemplo)
  decide a ordem de criação dos neurônios e, com ela, a dos sorteios. A gravação do editor
  também dispensa o banco: o `ConfigurationTables` (em `simulation.setup`) gera as linhas que o
  `saveConfiguration` original inseria, e o `LegacySchema` (o esquema original, em
  `legacy-schema.sql`) faz o que o HSQLDB fazia com elas: os mesmos tipos e arredondamentos
  (`setDouble` passa por `BigDecimal.valueOf`), `NOT NULL`, chave primária e a ordem do
  `SELECT` (a da chave primária, ou a de inserção nas tabelas sem chave).
- **Saídas:** `forca-<núcleo>` (N), `disparos-<núcleo>` (motoneurônios), `torque` (N·m) e
  o EMG de cada músculo, em mV:
  - `emg-bruto-<núcleo>`: soma dos MUAPs das unidades motoras (`instantMuscleEMG` original,
    com atenuação);
  - `emg-<núcleo>`: o que a tela de resultados do site antigo mostrava por padrão — o EMG bruto
    com ruído (média de |EMG| × `noiseEMG` da configuração) e passa-banda de 50 a 1000 Hz
    (`ButterworthImpulseInvariance`). No código original esse cálculo ficava na camada de
    gráficos (`ResultDisplay`); agora o motor o faz ao final da simulação. A única diferença é
    o ruído, que vem de um gerador com a seed do cenário em vez de `Math.random()`.

  Além dessas, o motor grava os dados da [tela de resultados](#tela-de-resultados): as séries do
  músculo (comprimento, velocidade, aceleração, ativações, forças dos elementos, ângulo de
  penação, força normalizada), do tendão, do musculotendão, do fuso (Ia, II) e do OTG (Ib) e da
  articulação (ângulo, velocidade, centros de massa e de pressão, torques, distúrbio); os disparos
  de cada grupo (motoneurônios no soma e no terminal, aferentes no terminal e no ponto de
  estímulo, interneurônios e tratos por tipo); o ISI médio que cada tipo de trato usou; tabelas
  com as propriedades de cada motoneurônio, interneurônio, aferente e unidade motora; os sinais
  dos neurônios guardados (Vs, Vd, condutâncias e a corrente injetada, numa tabela por neurônio);
  o EMG bruto sem atenuação e o de cada unidade guardada; o resumo dos disparos (a página
  "summary" do site antigo); e o `catalogo`, que descreve cada saída. Tudo isso já é calculado
  pela simulação: nada é sorteado e as saídas básicas não mudam. O ISI médio dos tratos e a
  corrente injetada são gravados durante a simulação, com o valor que o modelo usou, porque os
  sinais aleatório e gama sorteiam a cada avaliação e não podem ser recalculados depois. Duas
  séries do original eram divisões por zero e não saem mais: a taxa do órgão tendinoso de um
  músculo sem fibras Ib ativas (o original divide a taxa pelo número delas: NaN, depois infinito) e
  a força normalizada de um músculo sem unidades motoras (força máxima 0). Nenhuma fibra lia a
  primeira, então a dinâmica não muda; só o hash "completo" do golden master do 17 mudou. No
  resumo dos disparos, as frequências (do primeiro intervalo, dos quatro primeiros e do regime)
  saem dos instantes distintos do trem, em ordem: o reset de um trato anota um disparo que pode
  coincidir com o primeiro, e os ruídos de intervalos gaussianos às vezes repetem um instante,
  o que dava intervalo 0 e frequência infinita (o hash "completo" do 42 mudou por isso). A contagem
  de disparos continua a do trem.

### Garantias (testes em `br.remoto.core.engine.legacy`; os do editor, no `remoto-web`)

| Teste | O que garante |
|---|---|
| Banco original × referência | As 30 configurações do banco original (40 ms, seed = ID) batem com hashes gerados quando a configuração ainda era montada pelo `ConfigurationDAO` original lendo do HSQLDB (antes, um teste comparava os dois caminhos campo a campo) |
| Export sem HSQLDB | O export lê o `remoto.script` direto e reproduz os cenários `remoto-*` de `scenarios/`, fora a descrição, escrita à mão nos que não tinham uma, e os tratos gama do 24 e do 42, publicados como Poisson (os três do site antigo tinham sido exportados pelo HSQLDB) |
| Sem NaN nas séries | Nas configurações 17 e 18, nenhuma série tem NaN nem infinito: o órgão tendinoso sem fibras Ib e a força normalizada de músculo sem unidades motoras não saem; o resumo do `projectfr-neural-tract`, com disparos repetidos em t = 0, não tem frequência infinita (`LegacyEngineTest`) |
| Modelo completo | O `modelo-completo.json` liga todos os elementos do modelo, e cada um (músculos, fusos, OTG, aferentes, interneurônios, tratos, articulação) aparece nas saídas |
| Sinais no tempo | Com o ângulo imposto, o tornozelo segue o ângulo inicial mais o sinal, e o comprimento e os fusos acompanham; com o fusimotor modulado, nada muda antes do início do sinal, e o estático aumenta o disparo dos II; no fuso isolado, o dinâmico aumenta a Ia no estiramento e o estático, a II (`SignalsOverTimeTest`, `SpindleTest`); o `estiramento-tornozelo.json` impõe o trapézio |
| Estímulo e aferentes | Por corrente, a duração do pulso não muda quem dispara; por carga, o pulso de 1 ms recruta quase os mesmos axônios e o de 0,2 ms, nenhum; cada duração precisa da intensidade da curva de Lapicque (`LeakyChargeTest`); a ordem gama escolhida muda a regularidade dos aferentes, e 0 é a do original (`NerveAndAfferentOptionsTest`, `AfferentFiberTest`) |
| Nó de Ranvier | Parado, o nó fica no repouso da tabela 1 de Howells et al. (2012); o limiar da configuração vale para um pulso de 1 ms; a constante intensidade-duração sai do modelo, de cerca de 0,4 ms no motor e 0,5 ms no sensitivo; há refratariedade (`AxonNodeTest`); no motor, o pulso de 1 ms recruta quase os mesmos axônios que o limiar de corrente, e o de 0,2 ms, nenhum (`NerveAndAfferentOptionsTest`) |
| Exemplos do projectFR | Os 16 `projectfr-*.json` são o que o gerador produz, e cada um valida e roda (`ProjectFrExamplesTest`) |
| Tarefas do ReMoto_AIDP | Os 6 `aidp-*.json` são o que o gerador produz, e cada um valida e roda (`AidpExamplesTest`) |
| Bloqueio de condução | Com 10% do núcleo bloqueado, os 90 motoneurônios mais lentos disparam no soma mas não levam disparo ao terminal, e os mais rápidos levam; com o núcleo todo bloqueado, o sóleo não faz força; a fração fica no `settings` e volta ao reabrir, e fora de [0, 1] é recusada (`AxonConductionBlockTest`) |
| Série de taxas | A forma "series" interpola os pontos, faz degraus e vale 0 fora da janela; os 100 axônios do CM_ext seguem 0 Hz, um degrau para 200 Hz e uma rampa de volta (cerca de 2000 disparos no platô e 1000 na rampa); a série fica no `settings` (`ModulationTest`, `TractSeriesTest`) |
| Protocolos em lote | O parâmetro muda uma célula de tabela, o `settings`, a seed ou a duração; as medidas; na curva H/M da configuração 17, sem corrente nenhum motoneurônio dispara na janela da onda M e, com 40 mA, todos; na variabilidade da força, o intervalo maior dá força menor (`SweepTest`); o `remoto sweep` grava uma linha por simulação (`RemotoCliTest`) |
| Decaimento com a distância | Com decaimento nas sinapses do trato CM_ext no sóleo, a parte do trato na condutância de cada motoneurônio cai e fica maior no meio do núcleo que nas pontas; o valor fica no `settings` e volta ao reabrir (`SynapseDistanceDecayTest`) |
| Reprodutibilidade | Mesma seed → saídas idênticas; seed diferente → saídas diferentes |
| Golden master | As saídas das configurações 17, 24 e 42 do banco (os três cenários públicos do site antigo, como estão lá), com 40 ms, batem com os hashes em `golden-master.properties` (força, disparos e torque num hash; EMG noutro; todas as saídas, as da tela de resultados incluídas, num terceiro) |
| Editor × DAO original | Editar todas as telas de uma vez e gravar dá as mesmas tabelas que o `saveConfiguration` original gravava num HSQLDB, nos três cenários públicos do site antigo (`LegacyEditorTest`); gravar sem editar preserva as tabelas e a simulação (`LegacyStoreTest`) |
| Sinapse × solução analítica | Uma sinapse acionada como no Runge-Kutta segue a solução fechada de Lytton (1996): sobe durante o pulso e decai depois (`SynapseGroupTest`, no `remoto-core`) |
| 1 thread × 4 threads | Os três cenários públicos do site antigo dão saídas idênticas com qualquer número de threads, e uma thread de trabalho não consegue sortear (`ParallelUpdateTest`) |

Ao portar ou refatorar um modelo, o golden master mostra na hora se o resultado mudou.

### Exportar configurações do banco antigo

```bash
java -jar remoto-cli/target/remoto-cli-3.0.0-SNAPSHOT.jar export-legacy --db legacy-db/remoto --list
java -jar remoto-cli/target/remoto-cli-3.0.0-SNAPSHOT.jar export-legacy --db legacy-db/remoto --owner guest --out scenarios
java -jar remoto-cli/target/remoto-cli-3.0.0-SNAPSHOT.jar export-legacy --db legacy-db/remoto --id 34,41 --out meus-cenarios
```

### Protocolos em lote

O `remoto sweep` roda um cenário uma vez para cada valor de um parâmetro e grava em `sweep.csv`
uma linha por simulação, com o valor e as medidas (`br.remoto.core.protocol`). Dois protocolos
estão prontos:

- `--protocol hm`: a curva de recrutamento do reflexo H e da onda M. Varia a intensidade (mA) do
  estímulo de um nervo (`--nerve`, padrão PTN) e mede o pico a pico do EMG do músculo
  (`--muscle`, padrão SOL) e quantos motoneurônios dispararam nas janelas da onda M (de 2 a 20 ms
  depois do início do estímulo) e do reflexo H (de 20 a 50 ms), que `--m-window` e `--h-window`
  mudam. O nervo é ativado; sem janela de estímulo, ele dá um pulso só, no START da tabela. A
  simulação vai até 10 ms depois do fim da janela do H.
- `--protocol force`: a variabilidade da força. Varia o intervalo médio (ms) de um trato
  descendente (`--tract`, padrão CM_ext) e mede a média, o desvio-padrão e o coeficiente de
  variação da força do músculo, e quantos motoneurônios dispararam, numa janela (`--from` e
  `--to`; o padrão é a segunda metade da simulação, depois do transitório).

Qualquer outro lote é um `--param` e uma ou mais `--measure`:

```bash
java -jar remoto-cli/target/remoto-cli-3.0.0-SNAPSHOT.jar sweep cenario.json \
  --param NERVE:CD_NERVE=PTN:PEAK --range 5:40:1 \
  --measure "M=p2p(emg-SOL,7,25)" --measure "H=p2p(emg-SOL,25,55)" --keep-runs -o resultados/lote
```

O parâmetro é uma célula das tabelas (`TABELA:CHAVE=VALOR[,CHAVE=VALOR]:COLUNA`; todas as linhas
que batem mudam), um valor do `settings` (`settings.nerveStimulus.PTN.pulseWidth`), a `seed`
(repetições com sorteios diferentes) ou a `durationMs`. A medida é `[nome=]função(saída[,de,até])`,
com a janela em ms: `p2p`, `max`, `min`, `mean`, `sd` e `cv` numa série (força, EMG, ângulo...), e
`count` e `units` num trem de disparos. Os valores vêm de `--values 5,10,20` ou de
`--range DE:ATÉ:PASSO`; `--keep-runs` grava também os resultados de cada simulação. As simulações
rodam uma depois da outra, e o site ainda não tem os protocolos em lote.

Os cenários públicos de `scenarios/` estão na seção [Cenários públicos](#cenários-públicos). O
banco é um arquivo de texto (`remoto.script`, com um `CREATE TABLE` por tabela e um `INSERT` por
linha), e o `LegacyDatabase` o lê direto, sem o HSQLDB.

### Cenários públicos

| Arquivo | Cenário | Simulado |
|---|---|---|
| `remoto-17-…` | *Default - Soleus contraction*: o sóleo movido por 100 axônios descendentes (Poisson) | 5 s |
| `remoto-24-…` | *ISB_2013 - Stretch Reflex*: os quatro músculos, com aferentes Ia; o tríceps sural recebe o comando (trato em Poisson) | 30 s |
| `remoto-42-…` | *JN - Watanabe and Kohn (2015)*: o sóleo, com o comando modulado a 20 Hz a partir de 3 s (trato em Poisson) | 15 s |
| `remoto-14-…` | *Isometric contraction*: o tríceps sural (SOL, MG e LG) com 400 axônios descendentes (Poisson, 11 ms) | 5 s |
| `remoto-16-…` | *Recruitment*: o mesmo, com o intervalo dos axônios caindo em rampa de 20 para 4 ms | 20 s |
| `remoto-18-…` | *Neuroscience Meeting 2012*: reflexo H do sóleo e a via da inibição recíproca pelo nervo fibular | 0,2 s |
| `remoto-19-…` | *Biceps Brachii - Stim Ia*: pulsos a 5 Hz no nervo durante uma contração (no núcleo SOL) | 30 s |
| `remoto-25-…` | *Position Task*: tarefa de posição do tornozelo com o tríceps sural e aferentes Ia | 30 s |
| `remoto-26-…` | *Position Task - IIa*: o mesmo, com aferentes do grupo II | 30 s |
| `remoto-27-…` | *Position Task - IIa no Ia*: o mesmo, só com os aferentes do grupo II | 30 s |
| `remoto-28-…` | *Position Task - Ib*: o 25 com aferentes Ib e interneurônios Ib | 30 s |
| `remoto-32-…` | *variability_gamma*: contração isométrica do tríceps sural, intervalo de 3 ms | 5 s |
| `remoto-34-…` | *Identification_SOL_MG*: o tríceps sural com o comando modulado a 20 Hz a partir de 2 s | 15 s |
| `remoto-35-…` | *Identification_LG*: os dois gastrocnêmios, intervalo de 3 ms | 1,5 s |
| `remoto-36-…` | *Identification SOL_MG*: o 34 com 1,5 s (a modulação não chega a começar) | 1,5 s |
| `remoto-38-…` | *IdentSOL*: o sóleo, intervalo de 12 ms | 1,5 s |
| `remoto-39-…` | *IsometricTaskAM*: contração isométrica do tríceps sural com aferentes Ia | 8 s |
| `remoto-41-…` | *AMmodRenshaw*: o sóleo, com o comando modulado a 20 Hz a partir de 3 s | 15 s |
| `remoto-44-…` | *identSOLRenshaw*: o sóleo com as células de Renshaw (inibição recorrente) | 1,5 s |
| `modelo-completo` | Todos os elementos do modelo ligados, a partir do 24 (detalhes na descrição do cenário) | 2 s |
| `estiramento-tornozelo` | Reflexo de estiramento: o 24 com o tornozelo em ângulo imposto, um trapézio de 5° em dorsiflexão a partir de 700 ms | 1,5 s |
| `projectfr-antidromic-stimulation` | *AntidromicSimulationofMNandRC*: Estimulação antidrômica de 5 motoneurônios do sóleo ligados a 2 células de Renshaw | 0,6 s |
| `projectfr-force-variability` | *ForceVariability*: Força isométrica do tríceps sural com 400 axônios num processo gama, a 140 disparos/s | 16 s |
| `projectfr-h-reflex-m-wave` | *H-ReflexM-WaveCurve*: Reflexo H e onda M do sóleo com um pulso de 12 mA; a curva sai do `remoto sweep` | 0,05 s |
| `projectfr-ankle-impedance` | *ImpedanceAnkle*: O tornozelo perturbado por uma soma de 200 senoides, com os quatro músculos passivos | 2 s |
| `projectfr-renshaw-injected-current` | *InjectedCurrentRenshawCellPool*: 350 células de Renshaw com corrente injetada, sem motoneurônios | 0,1 s |
| `projectfr-isometric-closed-loop` | *IsometricClosedLoop*: O sóleo com comando descendente e realimentação Ia, e o tornozelo numa senoide de 1 Hz | 5 s |
| `projectfr-mn-pool-renshaw` | *MNPoolWithRenshawCells*: O sóleo com inibição recorrente pelas células de Renshaw | 5 s |
| `projectfr-motor-unit-pool-emg` | *MotorUnitPoolEMG*: 800 unidades motoras com corrente constante no dendrito: a força e o EMG | 5 s |
| `projectfr-descending-command` | *MotorUnitWithDescendingCommand*: O sóleo movido por 400 axônios descendentes a 60 disparos/s | 1 s |
| `projectfr-node-axons` | *MotorUnitWithNodeAxonsAndDescendingCommand*: 3 motoneurônios com nó de Ranvier no ponto do estímulo, passo de 0,005 ms | 0,1 s |
| `projectfr-hill-muscle` | *MuscleHillExample*: O músculo de Hill do sóleo, com o tornozelo numa senoide de 1 Hz | 1 s |
| `projectfr-muscle-spindle` | *MuscleSpindleExample*: O fuso do tibial anterior num estiramento senoidal de 0,25 Hz | 20 s |
| `projectfr-neural-tract` | *NeuralTractExample*: 400 axônios descendentes sozinhos, a 83 disparos/s | 10 s |
| `projectfr-neural-tract-unit` | *NeuralTractUnitExample*: Um axônio descendente com intervalos de 4 ms e a estatística deles | 10 s |
| `projectfr-postural-control` | *PosturalControl*: Postura ereta com os quatro músculos, aferentes Ia, II e Ib e interneurônios; o corpo cai em cerca de 1,3 s | 12 s |
| `projectfr-reciprocal-inhibition` | *reciprocalInhibition*: Inibição recíproca do sóleo por um pulso no nervo fibular comum | 1,1 s |
| `aidp-force-task-s0` | *ForceTask*, S0: Tríceps sural isométrico, com o comando descendente subindo de 40 para 250 disparos/s em 2 s e os axônios motores de 44 a 53 m/s | 12 s |
| `aidp-force-task-s1` | *ForceTask*, S1: A mesma tarefa com as velocidades desmielinizadas (de 5 a 51 m/s nas unidades S, de 52 a 40 m/s nas FF) | 12 s |
| `aidp-force-task-s2` | *ForceTask*, S2: As velocidades do S1 e o bloqueio de condução de 65% de cada núcleo motor | 12 s |
| `aidp-position-task-s0` | *PositionTask*, S0: O pé livre para girar a partir de 5°, contra 10% do torque voluntário máximo, com os quatro músculos e os aferentes Ia | 12 s |
| `aidp-position-task-s1` | *PositionTask*, S1: A mesma tarefa com as velocidades desmielinizadas | 12 s |
| `aidp-position-task-s2` | *PositionTask*, S2: As velocidades do S1 e o bloqueio de 65% | 12 s |

Os três primeiros são os públicos do site antigo (usuário `guest`). Os seguintes são
configurações de outros usuários do banco, publicadas com uma descrição escrita a partir do que
cada uma liga; as tabelas são as do banco, com duas exceções: os tratos gama passaram a Poisson
(ver abaixo), e as *Position Task* (25 a 28) levam no `settings` o tornozelo em tarefa de
posição. O banco não gravava o modelo de articulação, e com o tornozelo isométrico e o músculo
padrão o fuso não é integrado e os aferentes não disparam; com o de posição, o pé se move contra
uma carga de 15% do torque voluntário máximo, e os aferentes respondem. O `modelo-completo` parte do 24 e liga os aferentes II
e Ib, os interneurônios (Ia recíprocos, Ib, grupo II e Renshaw) dos extensores e dos flexores, os
seis tratos descendentes com Poisson e um pulso no nervo tibial, e escolhe, no `settings`, o
músculo de Hill e o tornozelo em pêndulo invertido; as taxas e o pulso foram escolhidos para
mostrar todos os elementos, não para reproduzir um experimento. O `estiramento-tornozelo` também
parte do 24 e escolhe, no `settings`, o músculo de Hill e o tornozelo em ângulo imposto: em 700 ms
o pé é levado 5° em dorsiflexão em 100 ms, fica 100 ms e volta em 100 ms; os fusos do tríceps sural
respondem, e os Ia excitam os motoneurônios. Mostra o recurso; não reproduz um experimento.

Os 16 `projectfr-*` convertem os exemplos da pasta `Examples` do
[projectFR](https://github.com/rnwatanabe/projectFR), a versão em Fortran do ReMoto (R. N.
Watanabe). Cada um tem o desenho do exemplo (o que é simulado, por quanto tempo, com que comando,
estímulo, mecânica e fusimotor) sobre a configuração 24, que tem os quatro músculos e todas as
tabelas; os parâmetros dos modelos são os do ReMoto em Java, e quando o exemplo muda uma
conectividade, uma condutância ou a dinâmica de uma sinapse (o projectFR usa a mesma estrutura, em
µS em vez de nS), o cenário muda também. Os lotes dos exemplos viram um cenário e o comando do
`remoto sweep` na descrição (a curva H/M, a variabilidade da força, as tentativas do controle
postural), e o que não tem equivalente também está na descrição: a cadeia de 50 nós de Ranvier vira
um nó no ponto do estímulo, e o fuso sozinho recebe o comprimento pelo tornozelo, calibrado para a
faixa do exemplo. As taxas que não dão um intervalo médio inteiro (a coluna MEAN do banco é
inteira) usam o intervalo 0 e um sinal constante com o alvo *rate*. Os arquivos saem de um gerador
(`ProjectFrExamples`, nos testes do `remoto-cli`); o `ProjectFrExamplesTest` confere que os arquivos
são o que ele produz e roda cada um, e `-Dremoto.gerar.cenarios=<pasta>` os grava de novo. O
projectFR é GPL-3: dos exemplos vêm o desenho dos experimentos e alguns valores, não código.

Os 6 `aidp-*` convertem as tarefas de força e de posição do
[ReMoto_AIDP](https://github.com/oliveiramarina/ReMoto_AIDP), a versão em Fortran adaptada a doenças
desmielinizantes (M. C. de Oliveira, R. N. Watanabe e A. F. Kohn, 2022; o último commit é de
setembro de 2022, e o projectFR parou em dezembro de 2019). Cada tarefa tem os três sujeitos de lá,
que nos arquivos `confForceTask_S*.rmto` e `confPositionTask_S*.rmto` diferem só nas faixas de
velocidade de condução dos axônios motores e, no S2, no bloqueio: o S0 saudável conduz de 44 a
53 m/s (as unidades S de 44 a 51, as FR de 51 a 52, as FF de 52 a 53); no S1 e no S2 a faixa das S
desce para 5 a 51 m/s e a das FF cai de 52 para 40, de modo que os axônios mais grossos passam a
ser os mais lentos; e o S2 bloqueia 65% de cada núcleo, a contar do mais lento. Na tarefa de força,
o tríceps sural fica isométrico e os 400 axônios descendentes vão de 40 para 250 disparos/s em 2 s;
na de posição, o pé gira livre a partir de 5° contra 10% do torque voluntário máximo, o tibial
anterior entra, e o comando vai de 40 para 72,5 disparos/s. Nos 2,5 s iniciais, a força do sóleo no
fim da simulação cai de 3221 N (S0) para 1853 N (S1) e 1176 N (S2), e o ângulo do tornozelo da
tarefa de posição passa de −1,7° (S0) a +0,9° (S2): o pé não sustenta mais a flexão plantar.

O que não tem equivalente está na descrição de cada cenário. O ReMoto_AIDP sorteia a velocidade de
cada axônio de uma gaussiana truncada na faixa, com média e desvio próprios (`meanvel`, `stdvel`);
aqui a velocidade segue a rampa do original de uma ponta à outra da faixa, porque o coeficiente de
variação do ReMoto em Java (`axonVelocitySTD`, da tela *Miscellaneous*) é o mesmo para os axônios
motores e para os sensoriais, e o ReMoto_AIDP não desmieliniza os sensoriais. A ordem do processo
gama do comando, que lá é 10 no primeiro par de segundos e 1 (força) ou 7 (posição) depois, fica na
do trecho que o experimento mede. O abalo de Raikova como ativação do músculo de Hill não existe
aqui (o de Raikova é outro modelo de músculo, sem a mecânica de Hill), e o torque voluntário máximo
da tarefa de posição é o do ReMoto em Java, 79 N·m, e não os 12 N·m de lá; a fração, 10%, é a do
experimento. Os arquivos saem de um gerador (`AidpExamples`, nos testes do `remoto-cli`), com o
mesmo `AidpExamplesTest` e a mesma opção `-Dremoto.gerar.cenarios=<pasta>`. O ReMoto_AIDP é GPL-3:
das tarefas vêm o desenho dos experimentos e as faixas de velocidade, não código.

**Tratos com processo gama.** O processo gama dos tratos descendentes usa como ordem o `std` do
`NeuronVO`, que o `ConfigurationDAO` original não gravava: ele gravava na coluna `STD` o campo
`order`, que nada lê, e a lia de volta no mesmo campo. Assim, nas configurações do banco com um
trato gama, o trato só dispara em t = 0, e os motoneurônios não recebem o comando; no site
antigo, o Std precisava ser digitado na tela de comandos descendentes a cada sessão. Nas
configurações publicadas com tratos gama (24, 25 a 28, 32, 34 a 36, 38, 39, 41, 42 e 44), os
tratos passaram a Poisson, com o mesmo intervalo médio. No 32 (*variability_gamma*), isso
apaga a diferença que ele estudava; para um trato gama, escolha *Gamma* e a ordem em *Std* na
tela *Descending Commands*: o editor agora grava o Std (ver [Editor de cenários](#editor-de-cenários)).
Ficaram de fora os testes (29, 33, 37 e 40), a 20 e a 21 (sem comando descendente, os pulsos no nervo não
fazem os motoneurônios dispararem) e as variações delas (22 e 23), as variações próximas da 14
(30 e 31) e a de 100 s (43).

### Desempenho

O motor original é pesado: um segundo simulado dos cenários públicos leva de 17 a 30 s com 4
threads. Os cenários longos (5 a 30 s simulados) levam minutos; o progresso aparece ao vivo e a
CLI aceita `--duration-ms` para testes rápidos.

O resultado é o mesmo, bit a bit, antes e depois do que segue.

Perfilado com JFR, o tempo ia para as sinapses (`getValue`/`getCurrent`, 4 grupos × 4 estágios
do Runge-Kutta por motoneurônio a cada passo), para buscas em `Hashtable` por chave de texto
montada a cada disparo e para a força muscular. Ficou assim:
- a chave de cada neurônio e a condutância que cada disparo liga são resolvidas uma vez, e
  não a cada disparo; um marcador por condutância substitui a varredura da lista de pulsos
  ativos; o valor da sinapse é reaproveitado quando o Runge-Kutta pede o mesmo instante duas
  vezes sem nenhum evento (k2 e k3); as unidades motoras de cada músculo são resolvidas uma vez;
- **atualização paralela dos neurônios** (`NeuronUpdater`): motoneurônios e interneurônios
  vão para várias threads; tratos, aferentes e o que mais sorteia números ficam na thread da
  simulação, na ordem original, de modo que a sequência aleatória não muda; os disparos de
  cada passo são entregues no fim do passo, na ordem do laço sequencial. Isso equivale ao laço
  original porque todo atraso sináptico é maior que o passo (verificado na montagem; se não
  for, o motor usa o laço sequencial). Uma thread de trabalho que tente sortear falha a
  simulação (`Rng.forbid`) em vez de mudá-la em silêncio. `ParallelUpdateTest` exige saídas
  idênticas com 1 e 4 threads, e o golden master não mudou.

Tempo total (1 s simulado, máquina de 4 núcleos físicos; as saídas conferem byte a byte):

| Cenário | Antes | 1 thread | 4 threads |
|---|---|---|---|
| 17, Soleus contraction | ~51 s | ~46 s | ~23 s |
| 24, ISB 2013 stretch reflex | 71 s | 65 s | 30 s |
| 42, Watanabe e Kohn (2015) | 32 s | 28 s | 17 s |

Com 4 threads a thread da simulação vira o gargalo: nela ficam tratos e aferentes, a força
muscular, o fuso e a entrega dos disparos. Oito threads não ganham de quatro numa CPU com 4
núcleos físicos. Threads: `--threads` na CLI (padrão: núcleos da CPU) e
`remoto.simulation.threads-per-run` no servidor (padrão: núcleos ÷ slots, para que as
simulações simultâneas não disputem a CPU).

## Editor de cenários

Em "Meus cenários", o botão **Editar** abre o editor, montado a partir do editor do site antigo
(`WebContent/jsp` + `br.remoto.control.action`, hoje só no repositório pai): o mesmo menu lateral, as mesmas telas, os mesmos
rótulos e unidades. Cenários públicos são de todos e só leitura: o **Editar** de um cenário
público cria uma cópia em "Meus cenários" e abre o editor nela (o **Copiar** só cria a cópia).

**Como funciona (pacote `br.remoto.web.editor`, no `remoto-web`):**

1. O cenário vira a `Configuration` original (`LegacyConfigurations.load`).
2. Cada tela lista os mesmos objetos que o Action original colocava no formulário (`NeuronVO`,
   `ConductanceVO`, `NerveVO`, `Current`, `MotorUnitVO`, `MiscellaneousVO`), pelos mesmos
   métodos da `Configuration`, e edita as mesmas propriedades JavaBean dos JSPs.
3. Ao salvar, a tela repete o `setConfProperties` do Action (limites como no máximo 1000
   neurônios por tipo, ISI mínimo, passo mínimo, períodos refratários mínimos).
4. A `Configuration` é gravada de volta nas tabelas do cenário como o
   **`ConfigurationDAO.saveConfiguration` original** as gravava (`ConfigurationTables`), sem
   banco de dados.

Testes garantem que carregar e gravar sem editar devolve as mesmas tabelas e a mesma simulação
(`LegacyStoreTest`), e que editar todas as telas grava o mesmo que o DAO original gravava
(`LegacyEditorTest`). Duas diferenças, ambas do código original: `DT_CONFIGURATION` passa a ser a
data da gravação, e a tabela `NOTHING` (que o DAO só escreve e ninguém lê) é mantida como estava.

**Telas (todas as do menu antigo):**

| Grupo | Telas |
|---|---|
| — | Scenario (nome, descrição, seed) |
| Structure | Descending Commands, Neural Elements |
| Neural Parameters | Synapses, Kinetic Properties, Synaptic Dynamics, Conductances, Motoneurons, Interneurons, Sensory Fibers |
| Proprioceptors | Muscle Spindle, Golgi Tendon Organ |
| Biomechanical Elements | Biomechanical Elements (modelos de articulação e de músculo) |
| Biomechanical Parameters | Joint, Muscle Units, Musculotendon, Electromyogram, Miscellaneous |
| Stimulation | Nerve Stimulation, Injected Current |
| Simulation | Duration and Time Step (no site antigo, *Run → Simulation Start*; aqui a tela só guarda a duração e o passo, e quem roda é o botão "Rodar este cenário") |

**Campos que o DAO original não gravava.** Algumas telas antigas editavam propriedades da
`Configuration` que o `ConfigurationDAO` não salvava (valiam só durante a sessão): modelos de
articulação, músculo, fuso, OTG e MUAP, distribuição das unidades motoras, ordem de recrutamento
FES, ângulos do tornozelo e do joelho, frequências gama e ganhos do fuso. Na nova plataforma elas
ficam no campo `legacy.settings` do cenário (`LegacySettings`) e são aplicadas depois do
carregamento pelo DAO; cenários exportados do banco não têm `settings` e usam os padrões do DAO.

Os neurônios que guardam sinais (a seção *Additional configuration* das telas de motoneurônios e
de interneurônios: "Index of MNs that will have signals stored") também ficam no `settings`, na
chave `storedNeurons` ("núcleo:tipo" → os dois índices). Sem escolha, valem os do original: o
primeiro e o do meio de cada tipo de motoneurônio, e o primeiro de cada tipo de interneurônio.

O *Std* dos tratos descendentes (tela *Descending Commands*) também fica no `settings`, na chave
`tractStd` ("DT:tipo" → valor). No processo gama ele é a ordem, que deve ser inteira; no
gaussiano, o desvio-padrão do intervalo. O DAO original gravava na coluna `STD` outro campo
(`order`, que nada lê), e o Std se perdia ao gravar, deixando os tratos gama mudos depois de
t = 0. As tabelas continuam iguais às do DAO; sem `tractStd`, vale o do DAO (0).

Os recursos que o site antigo não tinha (e as versões em Fortran têm; ver o item 13 dos
[Próximos passos](#próximos-passos)) também ficam no `settings`, e sem eles tudo é como no
original:

- **Ângulo imposto ao tornozelo** (tela *Joint*, e o modelo de articulação *Imposed angle* em
  *Biomechanical Elements*, `cdJointModel` = `"imposed"`): o tornozelo isométrico seguindo o ângulo
  inicial mais um sinal, em graus (`jointAngleSignal`: a forma e os parâmetros de um sinal
  modulante, como os dos tratos). O comprimento dos músculos e os fusos acompanham; o fuso só é
  integrado com o músculo de Hill.
- **Fusimotor no tempo** (tela *Muscle Spindle*): sinais somados aos disparos γ dinâmico e
  estático constantes (`gammaDynamicSignal`, `gammaStaticSignal`), nunca abaixo de zero.
- **Pulso e limiar do estímulo do nervo** (tela *Nerve Stimulation*, `nerveStimulus`: nervo →
  `pulseWidth`, `thresholdModel`, `tauMotor`, `tauSensory`). O pulso do original tem 1 ms, e o
  axônio dispara quando a intensidade passa do seu limiar (`"current"`). Por carga com vazamento
  (`"charge"`), cada axônio integra o estímulo com a constante de tempo do seu tipo (motor ou
  sensitivo) e dispara quando a carga passa do limiar: um pulso de duração *d* precisa de
  `reobase / (1 − e^(−d/τ))` (Lapicque), e a cronaxia é τ ln 2. Os limiares das tabelas continuam
  sendo a corrente de um pulso de 1 ms. Os padrões, 0,45 ms nos axônios motores e 0,65 ms nos
  sensitivos, são da ordem dos medidos em nervos humanos, em que a constante dos sensitivos é a
  maior (por isso pulsos longos favorecem o reflexo H); conferir na literatura antes de usá-los
  num trabalho.
- **Nó de Ranvier no ponto do estímulo** (a terceira opção do limiar, `thresholdModel` =
  `"node"`): cada axônio do nervo, motor ou aferente, tem um nó de Ranvier e o internó vizinho,
  ligados pela via de Barrett-Barrett, com os canais do modelo de axônio humano de Bostock (sódio
  transitório e persistente, potássio rápido e lento, corrente h no internó, vazamento e bombas) e
  os parâmetros motores e sensitivos de Howells, Trevillion, Bostock e Burke (2012, *J Physiol*
  590:1625–1640, tabelas 1 e 2 e apêndice). O estímulo entra no nó com a escala do limiar da
  configuração (a corrente de um pulso de 1 ms continua sendo o limiar), e o modelo dá a curva
  intensidade-duração, a refratariedade, a supernormalidade e a acomodação; as constantes de tempo
  do limiar por carga não são usadas. A condução a partir do ponto do estímulo continua sendo um
  atraso, e os disparos que vêm do soma ou do receptor não passam pelo nó. O apêndice publicado no
  PubMed Central tem as equações cortadas à esquerda: as tensões seguem o circuito da figura 1, e
  as bombas são as que mantêm os potenciais de repouso da tabela 1 (calculadas assim, dão −41 e
  −9,2 pA no motor, contra −33 e −7,9 pA da tabela; algum detalhe do original difere).
- **Ordem gama dos aferentes** (tela das fibras sensoriais, `afferentGammaOrder`: "músculo:tipo"
  → ordem inteira). O disparo pelo receptor é um processo gama; o original fixava a ordem no
  código (Ia 25, Ib 30, II 400, e a II volta para 25 depois do primeiro disparo). Ordem maior
  deixa o disparo mais regular; 0 mantém a do original.
- **Sinais como série de pontos** no ângulo imposto e no fusimotor: a forma *series*, com os
  pontos em `points` (pares [tempo em ms, valor]) e, no editor, no campo *Series points*; o
  `projectfr-ankle-impedance` usa uma soma de 200 senoides amostrada a cada 0,5 ms.
- **Série de taxas dos tratos** (a modulação *series* e a parte nova da tela *Descending
  Commands*, `tractSeries`: "DT:tipo" → lista de pares [tempo em ms, valor]): o sinal segue os
  pontos, interpolados, do primeiro ao último (dois pontos no mesmo tempo fazem um degrau). Com o
  alvo *rate*, o valor soma à taxa do intervalo médio; com o intervalo médio 0, é a própria taxa
  de cada axônio, em disparos/s. No editor, os pontos são escritos como "0 20; 1000 40".
- **Bloqueio de condução dos axônios motores** (a parte *Conduction block* da tela de
  motoneurônios, `axonConductionBlock`: "núcleo" → a fração bloqueada, de 0 a 1): a fração do
  núcleo, a contar do axônio mais lento, cuja condução é bloqueada. O motoneurônio continua
  disparando no soma, mas a velocidade vira 0 e o atraso até a placa motora passa a ser o do mínimo
  de 0,01 m/s (dezenas de segundos), de modo que nenhum disparo chega ao terminal e a unidade motora
  não faz força; na tabela de propriedades dos motoneurônios, a velocidade sai 0. É o bloqueio do
  [ReMoto_AIDP](https://github.com/oliveiramarina/ReMoto_AIDP) (Oliveira, Watanabe e Kohn, 2022),
  que lá põe a velocidade dos bloqueados em zero. Vale só para os núcleos motores, e a ordem é a do
  índice no núcleo, em que as unidades S vêm primeiro.
- **Carga da tarefa de posição** (o campo *Load of the position task* da tela *Joint*,
  `percentageOfMVC`): a fração do torque voluntário máximo (79 N·m) que o pedal opõe ao pé, no
  modelo de articulação *Position Task*. O `ConfigurationAssembler` original a fixava em 0,15, e só
  é gravada quando sai desse valor.
- **Decaimento com a distância** (a coluna *Distance decay* da tela *Synaptic Parameters*,
  `synapseDistanceDecay`: "núcleoPré>núcleo:tipo" → dec, em mm²): cada sinapse do tipo ganha o
  peso `dec / (dec + d²)`, sendo *d* a distância entre as duas células na coluna caudal-rostral, e
  não é criada abaixo de 1%, como as de Renshaw do original. Motoneurônios e interneurônios ficam
  onde o original os punha (as posições inicial e final de cada núcleo, em *Miscellaneous*); os
  aferentes de um músculo se espalham sobre o seu núcleo motor, e os axônios descendentes, pela
  coluna toda. Entre células de Renshaw e motoneurônios, 0 mantém os fatores da tela
  *Miscellaneous*.

**Ainda de fora:** a escolha de articulação (os cenários convertidos só têm dados do tornozelo),
as caixas de músculo de *Biomechanical Elements* (o Action original não as salvava; use *Neural
Elements*), e o nível de neuromodulação e o CV do limiar de Ca⁺⁺ (o Action original também não
os salvava).

## Tela de resultados

Ao abrir uma execução do motor original, a página mostra a tela de resultados do site antigo
(`results.jsp` + `ResultsAction` + `ResultDisplay`), com os mesmos menus, nomes e eixos:

| Menu | Opções |
|---|---|
| Type | *Biomechanical Elements*, *Neural Elements*, *Properties*, *Summary* e os layouts prontos (*Spikes MNs*, *Spikes Ias*, *Forces*, *Lengths*, *General*, *generalForSoleus*/*MG*/*LG*/*TA*) |
| Biomechanical | os 37 gráficos de lá: músculo, tendão, musculotendão, unidades motoras (pico do abalo, tempo de contração, meia relaxação, MUAP, posição na secção), EMG, articulação e as curvas de uma grandeza contra outra |
| Neural | motoneurônios (18 gráficos: raster no soma ou no terminal, frequência instantânea, ISI, histograma, potenciais do soma e do dendrito, condutâncias, corrente injetada, propriedades, Vs × dVs/dt, taxa de estímulo), aferentes, tratos e interneurônios; para todos, um tipo ou um neurônio guardado |
| EMG | filtro (1ª/2ª ou 3ª/3ª ordem, ou nenhum), cortes, ruído, atenuação, amostragem, e o EMG de uma unidade guardada |
| Analysis | estatísticas (pontos, média, variância, desvio-padrão, CV, mínimo, máximo, com as linhas da média ± DP) e FFT (pontos, sinal periódico, eixos) |
| Advanced Graphic Settings | até 8 subgráficos, sobreposição (*Hold On*), limites dos eixos, título, rótulos, legenda, cor, altura e fonte |

Cada gráfico baixa em CSV (como a opção "file" de lá) e em PNG. Os cálculos ficam no servidor
(`br.remoto.web.results`): o `ResultMenu` monta os menus a partir do `catalogo` da execução, e o
`ResultViews` faz o que o `processGraphic` fazia a partir dos dados gravados. O EMG com as opções
padrão é o `emg-<músculo>` do motor; com outras, é refeito a partir do EMG bruto, com o ruído do
mesmo gerador. Execuções anteriores a esta tela só têm força, disparos, torque e EMG.

Onde o código antigo tinha erros evidentes, vale o que a tela anunciava: o histograma de tratos,
aferentes e interneurônios e a frequência dos tratos não achavam os neurônios; a condutância "gInhi" não
existia (é "gInib"); os nomes dos tratos (*ExcINs*, *InhINs*) apontavam para os ruídos; a taxa de
estímulo × força emparelhava listas fora de ordem; e o "Vs x Vdot" mostrava a corrente injetada
de cada estágio do Runge-Kutta, um resto de depuração — aqui é o plano de fase do soma, Vs contra
dVs/dt. A resistência de entrada dos motoneurônios usa sempre os dois compartimentos (lá, só em
"All MNs").

## Próximos passos

1. **Portar e limpar os modelos** para o `remoto-core` (records, `sealed interface` para as
   famílias de modelos), em fases, cada uma com saídas idênticas bit a bit ao motor original:
   1. ~~Configuração tipada, sem HSQLDB na execução.~~ Feito: `br.remoto.core.model.config`.
   2. Núcleo numérico, **por fatias**: cada família de modelos vai para o `remoto-core` e o
      motor original passa a usá-la na mesma hora, em vez de um motor novo em paralelo que só
      seria comparável no fim (os três cenários públicos carregam as mesmas 83 classes). A cada
      fatia, golden master, 1 × 4 threads e 1 s dos três cenários conferem bit a bit.
      Feito: canais iônicos de pulso (`br.remoto.core.model.channel`: `SodiumChannel` m³h e
      `GatedChannel` para K rápido n⁴, K lento q² e Ca persistente p) e sinapses
      (`br.remoto.core.model.synapse`: `SynapseGroup`, a agregação de Lytton, `Synapse` e
      `SynapticDynamics`); aleatoriedade (`br.remoto.core.model.random`: `Rng`, `Draws` com os
      sorteios gaussiano de 12 uniformes, gama e uniforme, e `Gradient`, a interpolação de um
      parâmetro ao longo do pool); o abalo da unidade motora (`muscle.AlphaTwitch`, a função alfa
      como IIR de 2ª ordem, usada pelo SOCDS e pelo Hill); e o passa-faixa do EMG
      (`emg.BandPassFilter`). O filtro original zerava as duas primeiras amostras do EMG bruto que
      recebia; isso agora está explícito em `LegacyOutputs`. O `ButterworthBilinear`, construído
      nos modelos de articulação e nunca usado, saiu deles. E a membrana dos neurônios espinhais
      (`br.remoto.core.model.neuron`: `MotoneuronMembrane`, dois compartimentos com a PIC no
      dendrito, e `InterneuronMembrane`, um compartimento): potenciais, canais, sinapses, fugas e o
      Runge-Kutta. As classes `Motoneuron` e `Interneuron` do original ficaram como casca, com a
      identidade, os trens de disparo e o axônio (latências e colisão antidrômica). Foram mantidas
      duas assimetrias do original: a ordem da soma das correntes e o teste do refratário
      (`t − último > refratário` no motoneurônio, `t > último + refratário` no interneurônio), que
      em ponto flutuante não são equivalentes. Saiu a gravação "VsxVdot" a cada estágio do
      Runge-Kutta, um resto de depuração que guardava a corrente injetada e não chegava a nenhuma
      saída. Os sinais modulantes (`br.remoto.core.model.signal.Modulation`: constante, senoide,
      AM, quadrada, rampa, triângulo, trapézio, aleatório, gama e `custom`) e os disparos dos
      tratos descendentes e dos ruídos (`br.remoto.core.model.tract.TractSpikeGenerator`:
      Poisson, gama de ordem inteira e intervalos gaussianos, com a média modulada no intervalo
      ou na taxa) também foram. O `ModulatingSignal` segue como o objeto que o editor preenche e
      grava, e calcula os valores pelo `Modulation`; o `NeuralTract` ficou como casca. Ficaram as
      particularidades do original: a senoide perde 7 depois de 120 s, o limiar do processo gama
      é sorteado em todo reset, o reset anota no trem um disparo inicial que não chega às
      sinapses e a integral da taxa do processo gama não volta a zero. A forma `custom` deixou de
      gravar no sinal a amplitude que usa (12,35 e depois 13,75); o valor calculado é o mesmo.
      Saíram o `isActive(t)` do sinal e o `setSignal` do trato, que ninguém chamava. A corrente
      injetada (`br.remoto.core.model.stimulus.InjectedCurrent`, a amplitude fixa mais o sinal) e
      a estimulação do nervo (`stimulus.NerveStimulation`: pulsos de 1 ms com o período modulado
      pelo sinal, que soma à frequência, e o trem pareado) também. `Current` e `Nerve` ficaram
      como casca; do `Nerve` saíram os campos e métodos de teste que nada usava. O sinal do nervo
      é montado com o início, o fim, a amplitude e a frequência do próprio estímulo, e o trem
      pareado usa sempre a frequência do nervo (a do pareado não é copiada da configuração): as
      duas coisas são do original e ficaram. Nenhum dos três cenários públicos liga nervo ou
      corrente; a equivalência foi conferida com variantes do 17 que os ligam.
   3. ~~Aferentes, unidades motoras, músculos, proprioceptores e articulação.~~ Feito, com a
      mesma conferência e mais oito variantes do 17 que trocam os modelos pelo campo `settings`
      do cenário (Hill, Raikova, *DistributionMoments*, pêndulo, tarefa de posição, fuso de
      Prochazka, distribuição linear e pré-definida das unidades). Os modelos são fixados pelo
      `ConfigurationDAO` (SOCDS, tornozelo isométrico, fuso de Mileusnic, OTG de Crago), mas o
      editor os troca pelo `settings`; com SOCDS e tornozelo isométrico o fuso nem é integrado.
      - `br.remoto.core.model.neuron.AfferentFiber`: as fibras Ia, II e Ib (estímulo do nervo,
        disparo pelo receptor num processo gama, condução). O reset sorteia os limiares dos três
        tipos, e a fibra II passa a sortear com a ordem da Ia depois do primeiro disparo, como no
        original.
      - `muscle.MotorUnit` (parâmetros do abalo, o estado que os músculos avançam, o MUAP de
        Hermite-Rodriguez do EMG e a posição na secção), `CriticallyDampedMuscle` (SOCDS),
        `HillMuscle` e `Tendon`, `RaikovaMuscle` e `DistributionMomentsMuscle` (que nunca calculou
        força: fica em 0). O Hill calcula os quatro estágios do Runge-Kutta e avança por Euler, e
        cria a cada passo um gerador derivado que não usa; os dois ficaram, porque mudam a força e
        a sequência de aleatoriedade.
      - `receptor.MileusnicSpindle` (fibras bag1, bag2 e chain, com os corpos copiados do
        original por script; os ganhos da chain nunca são atribuídos e ficam em 0),
        `ProchazkaSpindle` e `CragoTendonOrgan`.
      - `joint.IsometricAnkle`, `InvertedPendulumAnkle`, `PositionTaskAnkle` e
        `MusculotendonGeometry` (os polinômios de comprimento e braço de momento, copiados por
        script). No tornozelo isométrico o joelho chega em radianos e é comparado com 0 e 90, de
        modo que MG e LG só têm coeficientes com o joelho estendido; no pêndulo e na tarefa de
        posição ele fica em graus. Os dois últimos calculam os torques dos estágios do
        Runge-Kutta com o ângulo do início do passo e sorteiam o distúrbio em cada estágio.

      Saíram, sem uso: o `Vestibular`, o simulador de fuso dos servlets antigos
      (`br.remoto.servlet`, `br.remoto.spindle_simulator`) e a dependência `javax.servlet`. As
      classes do original que restam nesses pacotes guardam só a amostragem dos sinais.
   4. ~~O motor roda só sobre o `remoto-core`, o editor grava sem banco e o
      `remoto-legacy-model` deixa de existir.~~ Feito. O que restava do original (a `Simulation`,
      o `NeuronUpdater`, as fábricas, as classes de cada elemento, a `Configuration` e os VOs)
      foi para `br.remoto.core.simulation`, em UTF-8 e sem o código comentado e morto (a gravação
      do `.sim`, o registro estático de simulações, a configuração da tela de resultados antiga,
      o `BasicDAO` e o `User`). Do `ConfigurationDAO` ficaram a leitura, como
      `ConfigurationAssembler`, e a gravação, como `ConfigurationTables`; o export do banco antigo
      lê o `remoto.script` como texto. O HSQLDB saiu das dependências. Antes de tirar o código
      original, os três cenários públicos com 1 s, as 30 configurações do banco com 100 ms e 18
      variantes do 17 (os modelos de músculo, articulação e fuso, a distribuição das unidades, o
      nervo, a corrente injetada e os sinais e distribuições dos tratos) deram saídas idênticas,
      bit a bit, às do código original; as gravações do editor (sem editar, editando todas as
      telas e reabrindo) deram as mesmas tabelas nas 33 configurações; e o export sem HSQLDB deu
      arquivos idênticos nas 30. Os testes que comparavam com o DAO pelo HSQLDB viraram golden
      masters gerados por ele. Os diagramas UML do código original estão em `docs/uml-original/`.

      Depois, o módulo `remoto-legacy`, que tinha ficado só como adaptação, foi dissolvido: o
      motor (`LegacyEngine` e a conversão do cenário) e a leitura do banco antigo
      (`LegacyDatabase`) foram para `br.remoto.core.engine.legacy`, e o editor para `br.remoto.web.editor`, no seu único usuário. A conversão do campo `legacy`
      do cenário deixou de usar o Jackson, e o `remoto-core` segue sem dependências. Os nomes
      gravados nos cenários (`"engine": "legacy"`, o formato `remoto-legacy-db/1` e a versão do
      motor) não mudaram, e o resultado continua o mesmo, bit a bit.
2. ~~Desempenho do motor original.~~ Feito: ver "Desempenho" em [Motor original](#motor-original-legacy).
   Próximo ganho: tirar da thread da simulação a força muscular, separando o que sorteia
   (fuso, distúrbios) do que não sorteia.
3. Varreduras de parâmetros, cliente Python, export NWB.
4. ~~Licença do projeto.~~ Feito: MIT, em `LICENSE`.
5. ~~Tela de resultados com tudo o que a do site antigo tinha.~~ Feito: ver
   [Tela de resultados](#tela-de-resultados).
6. ~~Um motor só e mais cenários de experimentos.~~ Feito: o motor provisório saiu, e
   `scenarios/` ganhou quatro experimentos do banco antigo (14, 16, 18 e 19) e o
   `modelo-completo`; o 24 e o 42 passaram a ter os tratos em Poisson. Ver
   [Cenários públicos](#cenários-públicos).
7. ~~Sem login, com exportação e importação de cenários.~~ Feito: ver
   [Sem login e sem banco](#sem-login-e-sem-banco). Saíram o login OIDC (ORCID, GitHub), os
   usuários de teste, os perfis, a exclusão de conta e os links de compartilhamento (o arquivo
   exportado faz esse papel); e entraram o limite de
   simulações por endereço de origem e o teto da fila.
8. ~~Ordem do processo gama dos tratos.~~ Feito: o editor grava o *Std* dos tratos no
   `settings` (`tractStd`), e um trato gama com ordem dispara; ver
   [Editor de cenários](#editor-de-cenários).
9. ~~Mais configurações do banco antigo.~~ Feito: as que tinham tratos gama (25 a 28, 32, 34 a
   36, 38, 39, 41 e 44) foram publicadas com Poisson, e as *Position Task* com o tornozelo em
   tarefa de posição; ver [Cenários públicos](#cenários-públicos).
10. ~~Dados que sobrevivem a reinícios.~~ Feito: a área deixou de depender da sessão (que ficava
    só na memória do servidor e, ao parar, apagava as áreas) e passou a ser identificada por um
    cookie de 30 dias; ver [Áreas e o cookie](#áreas-e-o-cookie).
11. ~~Site em dois idiomas.~~ Feito: inglês e português na aplicação e na apresentação, pelo
    idioma do navegador e com seletor em toda página; ver [Dois idiomas](#dois-idiomas).
12. **Ordens do processo gama dos trabalhos originais.** Se as ordens usadas nas publicações
    forem recuperadas, os cenários com tratos gama podem voltar a ser gama, com o `tractStd`.
13. **O que as versões em Fortran têm e esta não.** O [projectFR](https://github.com/rnwatanabe/projectFR)
    (R. N. Watanabe, último commit em dezembro de 2019) é uma versão do ReMoto em Fortran. O
    núcleo dos modelos coincide com o desta versão (músculo de Hill do *Virtual Muscle* com
    fibras I e II, SOCDS com saturação, fuso de Mileusnic, OTG de Lin e Crago, EMG de
    Hermite-Rodriguez com atenuação e alargamento, sinapses com dinâmica, tratos gama, colisão
    antidrômica, interneurônios, pêndulo invertido); aqui há a mais o dendrito ativo com a PIC
    (lá ele é passivo), os músculos de Raikova e de momentos, o fuso de Prochazka e a tarefa de
    posição.

    O [ReMoto_AIDP](https://github.com/oliveiramarina/ReMoto_AIDP) (M. C. de Oliveira, com R. N.
    Watanabe e A. F. Kohn, último commit em setembro de 2022) parte do projectFR e o adapta ao
    estudo de doenças desmielinizantes (Oliveira, Watanabe e Kohn, 2022, *Journal of Neural
    Engineering* 19:056022). Ele leva ao Fortran justamente três coisas que esta versão já tinha
    — a tarefa de posição (`jointAnklePositionTask.f90`, com a mesma inércia do pé, (4/3)·m·d², do
    `PositionTaskAnkle.java`), o abalo de Raikova e Aladjov (2002) como modelo de ativação e a
    correção da força-velocidade do músculo de Hill, em que o projectFR havia perdido a
    multiplicação do numerador pela velocidade e a unidade dela — e acrescenta duas coisas que não
    havia aqui: a velocidade de condução de cada axônio sorteada de uma gaussiana truncada (aqui a
    distribuição pelo núcleo é a rampa do original, com um coeficiente de variação opcional) e o
    bloqueio de condução de uma fração do núcleo motor. As tarefas de força e de posição de lá, com
    os três sujeitos, são os cenários `aidp-*`. O que faltava aqui:
    - [x] **Ângulo do tornozelo imposto**: o ângulo segue uma trajetória dada (perturbação), e
      o comprimento dos músculos e os fusos acompanham (o exemplo *ImpedanceAnkle* de lá).
      Feito: o modelo de articulação *Imposed angle* e o sinal da tela *Joint*; exemplo no
      cenário `estiramento-tornozelo`.
    - [x] **Fusimotor variável no tempo**: os disparos γ dinâmico e estático como sinais, e
      não constantes (o exemplo *PosturalControl* de lá os varia em rampa). Feito: os sinais
      da tela *Muscle Spindle*.
    - [x] **Estímulo do nervo com duração de pulso configurável** e limiar por carga com
      vazamento (curva intensidade-duração), como opção; o padrão continua o do original
      (pulso de 1 ms, limiar de corrente). Feito: a parte nova da tela *Nerve Stimulation*.
    - [x] **Ordem gama dos aferentes configurável** por tipo (Ia, Ib, II) e músculo; aqui era
      fixa no código (25, 30 e 400). Feito: a parte nova da tela das fibras sensoriais.
    - [x] **Axônio com nós de Ranvier** para motoneurônios e aferentes: internós e nós com
      sódio rápido e persistente, potássio rápido e lento, corrente h e bomba iônica; aqui o
      axônio era um atraso de condução com limiar. Feito no ponto do estímulo, com o modelo de
      Bostock e os parâmetros de Howells et al. (2012): a opção *Node of Ranvier* do limiar, na
      tela *Nerve Stimulation*. A condução continua sendo um atraso (lá, uma cadeia de nós pode
      substituí-lo), e os disparos do soma e do receptor não passam pelo nó.
    - [x] **Conexões que enfraquecem com a distância** em qualquer via (peso
      `dec / (dec + distância²)`); aqui era só entre motoneurônios e células de Renshaw. Feito: a
      coluna *Distance decay* da tela *Synaptic Parameters*, com posições para aferentes e tratos.
    - [x] **Protocolos em lote**: varrer um parâmetro em várias simulações, como a curva de
      recrutamento do reflexo H e da onda M (intensidade do estímulo) e a variabilidade da
      força (comando descendente). Feito na linha de comando: `remoto sweep`, com os dois
      protocolos prontos e lotes quaisquer (ver [Protocolos em lote](#protocolos-em-lote)); o
      site ainda não os tem.
    - [x] **Comando dos tratos como série de taxas qualquer**, além das formas prontas
      (senoide, AM, rampa...). Feito: a modulação *series* da tela *Descending Commands*.

    - [x] **Bloqueio de condução** de uma fração dos axônios motores do núcleo, a contar do mais
      lento (o ReMoto_AIDP põe a velocidade dos bloqueados em zero, e o sujeito S2 das tarefas
      bloqueia 65%). Feito: a parte *Conduction block* da tela de motoneurônios
      (`axonConductionBlock`), com os cenários `aidp-force-task-s2` e `aidp-position-task-s2`.

    Os que mudam o modelo ficam no `settings` (ver *Campos que o DAO original não gravava*, no
    Editor), e sem eles as simulações são as mesmas de antes. Faltam dois:
    - [ ] **Motoneurônio integra-e-dispara** (sem canais iônicos), para simulações rápidas.
    - [ ] **Velocidade de condução sorteada de uma gaussiana truncada** por tipo de unidade, como
      no ReMoto_AIDP. O `axonVelocitySTD` da tela *Miscellaneous* já põe ruído gaussiano sobre a
      rampa, mas é um coeficiente de variação único para os axônios motores e os sensoriais, e o
      ReMoto_AIDP usa média e desvio próprios para cada núcleo e não desmieliniza os sensoriais.

    O projectFR e o ReMoto_AIDP são GPL-3 e esta versão é MIT: os itens devem ser implementados a
    partir dos artigos e das equações, sem copiar o código, ou com a licença combinada com os
    autores.
