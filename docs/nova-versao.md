# ReMoto — nova versão (Java 25, sem banco de dados, multiusuário)

Esqueleto da nova versão proposta na modernização do ReMoto. Convive com o código legado
(`src/` e `WebContent/`, Struts 1 + Tomcat 7), que continua intacto até ser substituído.

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
  uma thread porque o trabalho é de CPU.
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

- **Sem cópia de código:** o módulo `remoto-legacy-model` compila direto de `src/` só as classes
  alcançáveis a partir de `Simulation` e `ConfigurationDAO` (cerca de 105 classes); Struts,
  JSPs, applets e o JFreeChart antigo ficam de fora.
- **Reprodutível:** o código original usava `Math.random()` e `new Random()` sem seed. Agora
  todas essas chamadas passam por `br.remoto.util.Rng`, um gerador por thread que o motor
  inicializa com a `seed` do cenário. Sem seed, o site antigo se comporta como antes.
- **Configuração sem banco:** cada configuração do HSQLDB antigo vira um cenário JSON com as
  linhas de cada tabela (formato `remoto-legacy-db/1`, sem a tabela USER). Para montar a
  `Configuration`, o motor carrega essas linhas num HSQLDB **em memória**, privado e descartado
  em seguida, e chama o `ConfigurationDAO` original — assim as ~2 mil linhas de mapeamento não
  precisaram ser reescritas. Nada é gravado em disco.
- **Saídas:** `forca-<núcleo>` (N), `disparos-<núcleo>` (motoneurônios) e `torque` (N·m).
  O EMG ainda não: no código original ele é calculado na camada de gráficos (`ResultDisplay`).

### Garantias (testes em `remoto-legacy`)

| Teste | O que garante |
|---|---|
| JSON × banco | A mesma configuração lida do HSQLDB original e do cenário JSON produz saídas idênticas |
| Reprodutibilidade | Mesma seed → saídas idênticas; seed diferente → saídas diferentes |
| Golden master | As saídas dos três cenários públicos (40 ms) batem com os hashes em `golden-master.properties` |

Ao portar ou refatorar um modelo, o golden master mostra na hora se o resultado mudou.

### Exportar configurações do banco antigo

```bash
java -jar remoto-cli/target/remoto-cli-3.0.0-SNAPSHOT.jar export-legacy --db WebContent/db/remoto --list
java -jar remoto-cli/target/remoto-cli-3.0.0-SNAPSHOT.jar export-legacy --db WebContent/db/remoto --owner guest --out scenarios
java -jar remoto-cli/target/remoto-cli-3.0.0-SNAPSHOT.jar export-legacy --db WebContent/db/remoto --id 34,41 --out meus-cenarios
```

Os três cenários públicos do site antigo (`guest`: 17, 24 e 42) já estão em `scenarios/`.

### Desempenho

O motor original é pesado: na máquina de desenvolvimento, 40 ms simulados levam ~3 s na
primeira execução (com aquecimento da JVM). Por isso os cenários longos (5 a 30 s simulados)
levam minutos; o progresso aparece ao vivo e a CLI aceita `--duration-ms` para testes rápidos.

## Editor de cenários

Em "Meus cenários", o botão **Editar** abre o editor, montado a partir do editor do site antigo
(`WebContent/jsp` + `br.remoto.control.action`): o mesmo menu lateral, as mesmas telas, os mesmos
rótulos e unidades. Cenários públicos são só leitura; use **Copiar** antes de editar.

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

1. **EMG** no motor original, extraindo o cálculo de `ResultDisplay` para o `remoto-legacy-model`.
2. **Portar e limpar os modelos** para o `remoto-core` (records, `sealed interface` para as
   famílias de modelos), o que também elimina o HSQLDB em memória.
3. Desempenho do motor original (perfilar com JFR; paralelizar a atualização dos neurônios).
4. Varreduras de parâmetros, cliente Python, export NWB.
5. Licença do projeto (o repositório ainda não tem `LICENSE`).
