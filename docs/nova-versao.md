# ReMoto — nova versão (Java 25, sem banco de dados, multiusuário)

Esqueleto da nova versão proposta na modernização do ReMoto. Do código legado (Struts 1 +
Tomcat 7) ficam só os modelos numéricos, no módulo `remoto-legacy-model`, e o banco original em `legacy-db/`; o site
JSP e as classes fora do build foram removidos e continuam no histórico e no repositório pai.

> **Estado:** a infraestrutura (multiusuário, fila, armazenamento, API, interface, CLI, CI)
> funciona e tem testes. Os **modelos originais do ReMoto já rodam na nova plataforma**
> (motor `legacy`, ver [Motor original](#motor-original-legacy)), com resultados reprodutíveis
> e conferidos contra o banco antigo. O motor provisório (`placeholder`) fica só para
> demonstração.

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 25 LTS (records, enums, `RandomGenerator`) |
| Build | Maven multimódulo + Maven Wrapper (`./mvnw`) |
| Web | Spring Boot 4.1 (Spring MVC, Spring Security 7, Actuator) |
| Login | OIDC/OAuth2 (ORCID, GitHub; outros provedores por configuração) — sem senhas locais |
| Interface | Thymeleaf + htmx 2 + uPlot (gráficos interativos) |
| Figuras | JFreeChart 1.5 (PNG para artigos) |
| Persistência | Arquivos: JSON (cenários, manifestos) e CSV.gz (resultados) — **sem banco** |
| Execução | Fila justa por usuário + pool de threads de plataforma (CPU-bound) |
| Progresso | Server-Sent Events |
| Entrega | um JAR executável (servidor web embutido) rodando como serviço systemd; CI no GitHub Actions |

**Sem Docker.** Sem banco de dados, o ReMoto é um único processo Java que grava numa pasta. Basta
o JDK 25 no servidor e o arquivo `remoto-web.jar`; um contêiner só acrescentaria mais uma
ferramenta para instalar e manter.

## Módulos

```
remoto-core          cenário, interface dos motores e motor provisório (Java puro)
remoto-storage       repositórios em arquivo: cenários, execuções, perfis, compartilhamentos
remoto-legacy-model  modelos numéricos originais, compilados direto de src/ (sem cópia)
remoto-legacy        motor "legacy": exporta o banco antigo para JSON e roda os modelos originais
remoto-web           aplicação Spring Boot multiusuário (API REST + páginas)
remoto-cli           execução sem servidor: remoto run / validate / export-legacy
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

# servidor em modo desenvolvimento (usuários de teste em application-dev.yml)
./mvnw -pl remoto-web -am spring-boot:run "-Dspring-boot.run.profiles=dev"
# → http://localhost:8080

# CLI
./mvnw -pl remoto-cli -am package -DskipTests
java -jar remoto-cli/target/remoto-cli-3.0.0-SNAPSHOT.jar run scenarios/exemplo-sol-mg.json -o resultados/teste
```

No Windows, use `mvnw.cmd` no lugar de `./mvnw`. No PowerShell, mantenha as aspas em
`"-Dspring-boot.run.profiles=dev"`: sem elas, o PowerShell quebra o argumento nos pontos. Se o
Java padrão da máquina for anterior ao 25, aponte o `JAVA_HOME` para o JDK 25 antes
(`$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.2"`, ajustando o caminho).

### Produção (OIDC)

1. Registre o ReMoto em cada provedor (ORCID, GitHub...) com o redirect
   `https://<host>/login/oauth2/code/<provedor>`.
2. Preencha `ORCID_CLIENT_ID`, `ORCID_CLIENT_SECRET`, `GITHUB_CLIENT_ID` e `GITHUB_CLIENT_SECRET`
   em `/etc/remoto/remoto.env` (ver abaixo), ou remova de `application-prod.yml` os provedores
   que não usar.
3. Sem o perfil `dev`, o app só sobe com pelo menos um provedor OIDC configurado — de propósito,
   para que os usuários de teste nunca cheguem à produção.

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
sudo install -D -m 600 deploy/remoto.env.example /etc/remoto/remoto.env   # editar: OIDC etc.

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
  Execuções em andamento são canceladas e as que estavam na fila ficam marcadas como interrompidas.
- **Backup:** tudo o que importa está em `/var/lib/remoto` (rsync, restic ou o backup do LEB).
- **HTTPS:** coloque nginx ou Apache na frente (porta 443 → 8080) e descomente
  `SERVER_FORWARD_HEADERS_STRATEGY=native` no `remoto.env`.
- **Novos cenários públicos:** copie o JSON para `/opt/remoto/scenarios/`; não precisa reiniciar.
- **Windows:** o mesmo `java -jar` pode virar serviço com o WinSW, usando as variáveis do
  `remoto.env.example`.

## Multiusuário sem banco

### Identidade

| Quem | `UserId` | Onde ficam os dados |
|---|---|---|
| Usuário OIDC | `u-` + hash(emissor \| sub) | `data/users/<id>/` |
| Usuário local (dev) | `u-` + hash("local" \| username) | `data/users/<id>/` |
| Convidado | `g-` + id aleatório guardado na sessão | `data/tmp/guest/<id>/` (apagado quando a sessão termina) |

O `UserId` nunca vem de texto digitado, então é sempre seguro como nome de pasta. Ids de
cenários e execuções são ULIDs validados (`Ids.requireSafe`) contra path traversal.

### Layout dos arquivos

```
data/
├── users/<userId>/
│   ├── profile.json
│   ├── scenarios/<ulid>.json
│   └── runs/<ulid>/
│       ├── manifest.json      status, seed, versão do motor, hash da configuração, datas
│       ├── config.json        cópia congelada da configuração usada
│       └── results/*.csv.gz   séries temporais e disparos
├── tmp/guest/<userId>/...     mesma estrutura, temporária
└── shares/<sha256>.json       cenários compartilhados (imutáveis; o link é o hash do conteúdo)
```

### O que substitui as garantias do banco

| Garantia | Como |
|---|---|
| Atomicidade | grava em arquivo temporário e renomeia (`StorageFiles.writeAtomically`) |
| Concorrência entre usuários | uma trava por usuário (`UserLocks`); usuários diferentes gravam em paralelo |
| Edição em duas abas | ETag = SHA-256 do arquivo; `PUT` com `If-Match` desatualizado → `412` |
| Isolamento | todo acesso passa pelo `UserId` do `Caller`; outro usuário recebe `404` |
| Recuperação após queda | ao subir, execuções `QUEUED`/`RUNNING` viram `FAILED` ("interrompida") |
| Exportar/apagar conta (LGPD) | `.zip` da pasta do usuário / apagar a pasta |
| Limpeza | tarefa diária apaga execuções antigas não fixadas e áreas de convidado abandonadas |

### Vários usuários simulando ao mesmo tempo

- Até `remoto.simulation.slots` simulações em paralelo (padrão: núcleos da CPU); cada uma ocupa
  uma thread porque o trabalho é de CPU. Com menos slots que núcleos, cada simulação do motor
  original usa `threads-per-run` threads (padrão: núcleos ÷ slots); o resultado não muda.
- **Fila justa** (`FairQueue`): uma fila por usuário, despachada em rodízio. Uma varredura de 50
  execuções de um usuário não bloqueia a simulação única de outro.
- Cotas por usuário: `max-active-per-user` (padrão 2) e `max-queued-per-user` (padrão 20 → `429`).
- Limite real: **uma instância do servidor**. Para várias máquinas seria preciso fila
  compartilhada e armazenamento comum (S3/MinIO) — ver a proposta.

## API REST

| Método | Caminho | Descrição |
|---|---|---|
| GET | `/api/me` | quem sou eu (convidado ou usuário) |
| GET | `/api/me/export` | baixa todos os meus dados (.zip) — exige login |
| DELETE | `/api/me` | apaga minha conta e todos os dados — exige login |
| GET | `/api/scenarios/public` | cenários públicos |
| POST | `/api/scenarios/public/{id}/copy` | copia um cenário público para mim |
| GET / POST | `/api/scenarios` | meus cenários / criar |
| GET / PUT / DELETE | `/api/scenarios/{id}` | ler (com `ETag`) / substituir (`If-Match`) / apagar |
| POST | `/api/scenarios/{id}/share` | gera link imutável — exige login |
| GET | `/api/shares/{hash}` | cenário compartilhado |
| POST | `/api/shares/{hash}/fork` | copia um compartilhado para mim |
| POST | `/api/runs` | executa: `{"scenario": {...}}` ou `{"scenarioId"}`, `{"publicScenarioId"}`, `{"shareHash"}` |
| GET | `/api/runs`, `/api/runs/{id}` | minhas execuções / detalhe com nomes dos resultados |
| GET | `/api/runs/{id}/events` | progresso ao vivo (SSE, evento `progresso`) |
| DELETE | `/api/runs/{id}` | cancela (se ativa) ou apaga |
| PUT | `/api/runs/{id}/pinned` | fixa (`true`) para escapar da retenção |
| GET | `/api/runs/{id}/config` | configuração exata usada |
| GET | `/api/runs/{id}/results/{nome}.csv` | resultado em CSV |
| GET | `/api/runs/{id}/results/{nome}.png` | figura em PNG |

Requisições que alteram dados exigem o token CSRF (cookie `XSRF-TOKEN` → cabeçalho
`X-XSRF-TOKEN`). Erros seguem RFC 9457 (`application/problem+json`).

## Formato do cenário (schemaVersion 1)

```json
{
  "name": "Exemplo",
  "description": "…",
  "schemaVersion": 1,
  "seed": 42,
  "durationMs": 1000,
  "stepMs": 0.05,
  "nuclei": [ { "nucleus": "SOL", "motoneurons": 100, "driveNa": 5.0, "noiseNa": 1.0 } ]
}
```

A mesma configuração + a mesma `seed` + a mesma versão do motor reproduzem o resultado bit a bit.

O campo `engine` escolhe o motor: `"placeholder"` (padrão, usa `nuclei`) ou `"legacy"` (usa o
campo `legacy`, descrito abaixo). Um motor desconhecido é recusado com `400`.

## Motor original (`legacy`)

Os modelos publicados do ReMoto (motoneurônios S/F com dendritos ativos, condutâncias, Markov,
interneurônios, sinapses com depressão/facilitação, Hill/Raikova/SOCDS, fuso, OTG, articulação)
rodam na nova plataforma **com o mesmo código numérico do site antigo**:

- **Sem cópia de código:** o módulo `remoto-legacy-model` traz do `src/` do repositório pai só as
  classes alcançáveis a partir de `Simulation` e `ConfigurationDAO` (104 arquivos), sem
  alteração numérica; Struts, JSPs, applets e o JFreeChart antigo foram removidos do repositório.
- **Reprodutível:** o código original usava `Math.random()` e `new Random()` sem seed. Agora
  todas essas chamadas passam por `br.remoto.util.Rng`, um gerador por thread que o motor
  inicializa com a `seed` do cenário. Sem seed, o site antigo se comporta como antes.
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
  removido; as mensagens de erro e os avisos de criação de modelos ficaram.
- **Desempenho:** sinapses e força muscular sem buscas por texto a cada passo, e atualização
  paralela dos neurônios, com o mesmo resultado bit a bit (ver [Desempenho](#desempenho)).
- **Configuração sem banco:** cada configuração do HSQLDB antigo vira um cenário JSON com as
  linhas de cada tabela (formato `remoto-legacy-db/1`, sem a tabela USER). Para rodar, essas
  linhas viram parâmetros tipados no `remoto-core` (`br.remoto.core.model.config`: um record por
  tabela, reunidos em `ModelConfig`), e o `ConfigurationDAO` original monta a `Configuration` a
  partir deles, com o mesmo mapeamento de sempre e sem JDBC. O leitor (`LegacyTablesReader`)
  reproduz o que o HSQLDB fazia: `NUMERIC(128,4)` arredondado para 4 casas com a metade para
  baixo, decimal em coluna inteira truncado, nulo lido como 0, e a ordem das linhas do
  `ORDER BY` de cada consulta do DAO, com o mesmo algoritmo de ordenação do HSQLDB 2.7.3. Esse
  algoritmo não é estável, e a ordem dos empates (os tratos de um mesmo núcleo, por exemplo)
  decide a ordem de criação dos neurônios e, com ela, a dos sorteios. Só a gravação do editor
  ainda passa por um HSQLDB em memória.
- **Saídas:** `forca-<núcleo>` (N), `disparos-<núcleo>` (motoneurônios), `torque` (N·m) e
  o EMG de cada músculo, em mV:
  - `emg-bruto-<núcleo>`: soma dos MUAPs das unidades motoras (`instantMuscleEMG` original,
    com atenuação);
  - `emg-<núcleo>`: o que a tela de resultados do site antigo mostrava por padrão — o EMG bruto
    com ruído (média de |EMG| × `noiseEMG` da configuração) e passa-banda de 50 a 1000 Hz
    (`ButterworthImpulseInvariance`). No código original esse cálculo ficava na camada de
    gráficos (`ResultDisplay`); agora o motor o faz ao final da simulação. A única diferença é
    o ruído, que vem de um gerador com a seed do cenário em vez de `Math.random()`.

### Garantias (testes em `remoto-legacy`)

| Teste | O que garante |
|---|---|
| JSON × banco | A mesma configuração lida do HSQLDB original e do cenário JSON produz saídas idênticas |
| Parâmetros tipados × HSQLDB | A `Configuration` montada dos parâmetros tipados é idêntica, campo a campo e na ordem das listas, à que o DAO montava pelo HSQLDB, nas 30 configurações do banco original, nos três cenários públicos e em tabelas aleatórias com empates, nulos e valores com mais de 4 casas (`TypedConfigurationTest`, `TypedConfigurationEdgeCasesTest`) |
| Reprodutibilidade | Mesma seed → saídas idênticas; seed diferente → saídas diferentes |
| Golden master | As saídas dos três cenários públicos (40 ms) batem com os hashes em `golden-master.properties` (força, disparos e torque num hash; EMG noutro) |
| Sinapse × solução analítica | Uma sinapse acionada como no Runge-Kutta segue a solução fechada de Lytton (1996): sobe durante o pulso e decai depois (`SynapseGroupTest`, no `remoto-core`) |
| 1 thread × 4 threads | Os três cenários públicos dão saídas idênticas com qualquer número de threads, e uma thread de trabalho não consegue sortear (`ParallelUpdateTest`) |

Ao portar ou refatorar um modelo, o golden master mostra na hora se o resultado mudou.

### Exportar configurações do banco antigo

```bash
java -jar remoto-cli/target/remoto-cli-3.0.0-SNAPSHOT.jar export-legacy --db legacy-db/remoto --list
java -jar remoto-cli/target/remoto-cli-3.0.0-SNAPSHOT.jar export-legacy --db legacy-db/remoto --owner guest --out scenarios
java -jar remoto-cli/target/remoto-cli-3.0.0-SNAPSHOT.jar export-legacy --db legacy-db/remoto --id 34,41 --out meus-cenarios
```

Os três cenários públicos do site antigo (`guest`: 17, 24 e 42) já estão em `scenarios/`.

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

**Como funciona (pacote `br.remoto.legacy.editor`):**

1. O cenário vira a `Configuration` original (`LegacyConfigurations.load`).
2. Cada tela lista os mesmos objetos que o Action original colocava no formulário (`NeuronVO`,
   `ConductanceVO`, `NerveVO`, `Current`, `MotorUnitVO`, `MiscellaneousVO`), pelos mesmos
   métodos da `Configuration`, e edita as mesmas propriedades JavaBean dos JSPs.
3. Ao salvar, a tela repete o `setConfProperties` do Action (limites como no máximo 1000
   neurônios por tipo, ISI mínimo, passo mínimo, períodos refratários mínimos).
4. A `Configuration` é gravada de volta com o **`ConfigurationDAO.saveConfiguration` original**,
   num HSQLDB em memória, e exportada de novo para o JSON do cenário.

Testes (`LegacyStoreTest`) garantem que carregar e gravar sem editar devolve as mesmas tabelas e
a mesma simulação. Duas diferenças, ambas do código original: `DT_CONFIGURATION` passa a ser a
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
| Run | Simulation Start |

**Campos que o DAO original não gravava.** Algumas telas antigas editavam propriedades da
`Configuration` que o `ConfigurationDAO` não salvava (valiam só durante a sessão): modelos de
articulação, músculo, fuso, OTG e MUAP, distribuição das unidades motoras, ordem de recrutamento
FES, ângulos do tornozelo e do joelho, frequências gama e ganhos do fuso. Na nova plataforma elas
ficam no campo `legacy.settings` do cenário (`LegacySettings`) e são aplicadas depois do
carregamento pelo DAO; cenários exportados do banco não têm `settings` e usam os padrões do DAO.

**Ainda de fora:** a escolha de articulação (os cenários convertidos só têm dados do tornozelo),
as caixas de músculo de *Biomechanical Elements* (o Action original não as salvava; use *Neural
Elements*), e o nível de neuromodulação, o CV do limiar de Ca⁺⁺ e os índices de neurônios com
sinal guardado (o Action original também não os salvava).

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
   4. O motor roda só sobre o `remoto-core`, o editor passa a gravar os parâmetros tipados (o
      que elimina o HSQLDB de vez) e o `remoto-legacy-model` deixa de existir.
2. ~~Desempenho do motor original.~~ Feito: ver "Desempenho" em [Motor original](#motor-original-legacy).
   Próximo ganho: tirar da thread da simulação a força muscular, separando o que sorteia
   (fuso, distúrbios) do que não sorteia.
3. Varreduras de parâmetros, cliente Python, export NWB.
4. ~~Licença do projeto.~~ Feito: MIT, em `LICENSE`.
