# ReMoto — nova versão (Java 25, sem banco de dados, multiusuário)

Esqueleto da nova versão proposta na modernização do ReMoto. Convive com o código legado
(`src/` e `WebContent/`, Struts 1 + Tomcat 7), que continua intacto até ser substituído.

> **Estado:** a infraestrutura (multiusuário, fila, armazenamento, API, interface, CLI, Docker,
> CI) funciona e tem testes. O **motor de simulação ainda é provisório**
> (`PlaceholderPoolEngine`): o próximo passo é portar os modelos originais para o `remoto-core`.

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
| Entrega | Docker / docker compose, GitHub Actions |

## Módulos

```
remoto-core     modelos e motor em Java puro (sem web, sem armazenamento)
remoto-storage  repositórios em arquivo: cenários, execuções, perfis, compartilhamentos
remoto-web      aplicação Spring Boot multiusuário (API REST + páginas)
remoto-cli      execução sem servidor: remoto run cenario.json
scenarios/      cenários públicos versionados no Git (o antigo usuário "guest")
```

## Como rodar

Requer JDK 25. O Maven é baixado pelo wrapper.

```bash
# testes de todos os módulos
./mvnw verify

# servidor em modo desenvolvimento (usuários de teste em application-dev.yml)
./mvnw -pl remoto-web -am spring-boot:run -Dspring-boot.run.profiles=dev
# → http://localhost:8080

# CLI
./mvnw -pl remoto-cli -am package -DskipTests
java -jar remoto-cli/target/remoto-cli-3.0.0-SNAPSHOT.jar run scenarios/exemplo-sol-mg.json -o resultados/teste

# Docker
docker compose up --build
```

No Windows, use `mvnw.cmd` no lugar de `./mvnw`.

### Produção (OIDC)

1. Registre o ReMoto em cada provedor (ORCID, GitHub...) com o redirect
   `https://<host>/login/oauth2/code/<provedor>`.
2. Defina `SPRING_PROFILES_ACTIVE=prod` e as variáveis `ORCID_CLIENT_ID`, `ORCID_CLIENT_SECRET`,
   `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` (ou remova de `application-prod.yml` os provedores
   que não usar).
3. Sem o perfil `dev`, o app só sobe com pelo menos um provedor OIDC configurado — de propósito,
   para que os usuários de teste nunca cheguem à produção.

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
O esquema vai crescer quando os modelos originais forem portados (`schemaVersion` 2+).

## Próximos passos

1. **Testes de caracterização do legado**: rodar os cenários canônicos (reflexo H, curva de
   recrutamento, inibição recíproca) no código antigo e guardar as saídas como referência.
2. **Portar o motor**: condutâncias, Markov, motoneurônios S/F, interneurônios, sinapses,
   Hill/Raikova/SOCDS, fuso (Mileusnic, Prochazka), OTG, articulação — para `remoto-core`, com
   os testes do passo 1 passando. Usar `sealed interface` para as famílias de modelos.
3. **`remoto-cli export-legacy`**: ler o HSQLDB atual uma última vez e gravar as 30
   configurações como JSON (`guest` → `scenarios/`).
4. Editor de cenários na interface, varreduras de parâmetros, cliente Python, export NWB.
5. Licença do projeto (o repositório ainda não tem `LICENSE`).
