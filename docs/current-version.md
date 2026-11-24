# ReMoto — current version (Java 25, no database, no login)

The current version of ReMoto, the result of modernizing the original site (Struts 1 + Tomcat 7).
The numerical models were ported to `remoto-core`, with output identical, bit for bit, to that of
the original code; of the legacy, only the original database is kept, in `legacy-db/`, read without
HSQLDB. The JSP site and the original code remain in the history and in the parent repository.

> **State:** the infrastructure (login-free areas, queue, storage, API, interface, CLI, CI) works
> and is tested. The **original ReMoto models run on the new platform** (the `legacy` engine, see
> [Original engine](#original-engine-legacy)), ported to `remoto-core`, with no database at any
> step, with reproducible results checked against the original code and database. It is the only
> engine: the provisional one, which served only for demonstration, was removed.

## Stack

| Layer | Technology |
|---|---|
| Language | Java 25 LTS (records, enums, `RandomGenerator`) |
| Build | multi-module Maven + Maven Wrapper (`./mvnw`) |
| Web | Spring Boot 4.1 (Spring MVC, Spring Security 7, Actuator) |
| Login | none: each browser has its own area (a 30-day cookie); scenarios go in and out as files (.json, .zip) |
| Interface | Thymeleaf + htmx 2 + uPlot (interactive charts) |
| Languages | English and Portuguese, from the browser language, with a selector on every page |
| Figures | JFreeChart 1.5 (PNG for papers) |
| Persistence | Files: JSON (scenarios, manifests) and CSV.gz (results) — **no database** |
| Execution | A fair queue per area, a limit per origin address and a pool of platform threads (CPU-bound) |
| Progress | Server-Sent Events |
| Delivery | one executable JAR (embedded web server) running as a systemd service; CI on GitHub Actions |

**No Docker.** With no database, ReMoto is a single Java process that writes to a folder. All the
server needs is JDK 25 and the `remoto-web.jar` file; a container would only add one more tool to
install and maintain.

## Modules

```
remoto-core          scenario, the ReMoto models, the "legacy" engine, reader of the old database (plain Java, no dependencies)
remoto-storage       file-based repositories: scenarios, runs and the area of each browser
remoto-web           Spring Boot application with no login (REST API + pages), including the scenario editor
remoto-cli           running without a server: remoto run / sweep / validate / export-legacy
scenarios/           public scenarios versioned in Git (the old "guest" user)
```

## How to run

Requires JDK 25. Maven is downloaded by the wrapper.

The simplest way to bring the site up in development mode is the start script for your shell. It
looks for a JDK 25 (in `JAVA_HOME`, in the `PATH` or in the usual install folders), so it works
even if the machine's default Java is older. The optional argument is the port (8081 by default, the
same port the service listens on in production, because 8080 is often already taken):

| Shell | Command |
|---|---|
| Windows (cmd) | `scripts\start.cmd` or `scripts\start.cmd 9090` |
| Windows (PowerShell) | `.\scripts\start.ps1` or `.\scripts\start.ps1 -Port 9090` |
| Linux, macOS, Git Bash | `scripts/start.sh` or `scripts/start.sh 9090` |

If PowerShell refuses to run scripts, use
`powershell -ExecutionPolicy Bypass -File .\scripts\start.ps1`, or the `.cmd` version.

The equivalent commands, without the scripts:

```bash
# tests of every module
./mvnw verify

# server in development mode (templates re-read on every request)
./mvnw -pl remoto-web -am spring-boot:run "-Dspring-boot.run.profiles=dev"
# → http://localhost:8081/simulator/  (and /simulator/pt/ for Portuguese)

# CLI
./mvnw -pl remoto-cli -am package -DskipTests
java -jar remoto-cli/target/remoto-cli-4.0.0-SNAPSHOT.jar run scenarios/remoto-17-default-soleus-contraction.json --duration-ms 500 -o results/test
# batch protocol: the H/M curve, from 5 to 40 mA (see "Batch protocols")
java -jar remoto-cli/target/remoto-cli-4.0.0-SNAPSHOT.jar sweep scenarios/remoto-17-default-soleus-contraction.json --protocol hm --range 5:40:1 -o results/hm-curve
```

On Windows, use `mvnw.cmd` instead of `./mvnw`. In PowerShell, keep the quotes in
`"-Dspring-boot.run.profiles=dev"`: without them, PowerShell breaks the argument at the dots. If
the machine's default Java is older than 25, point `JAVA_HOME` at the JDK 25 first
(`$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.2"`, adjusting the path).

## Installing on the server (Linux, no Docker)

The supporting files are in `deploy/`: the systemd unit (`remoto.service`), the configuration
template (`remoto.env.example`) and the reverse-proxy examples (`nginx-remoto.conf.example` and
`Caddyfile.example`).

```bash
# 1. JDK 25 (Temurin, Corretto or the distribution's package)
java -version

# 2. Build the JAR (on your machine or on the server itself)
./mvnw -pl remoto-web -am package -DskipTests

# 3. System user, folders and files
sudo useradd --system --home-dir /var/lib/remoto --shell /usr/sbin/nologin remoto
sudo install -d -o remoto -g remoto -m 750 /var/lib/remoto
sudo install -D -m 644 remoto-web/target/remoto-web-4.0.0-SNAPSHOT.jar /opt/remoto/remoto-web.jar
sudo mkdir -p /opt/remoto/scenarios && sudo cp scenarios/*.json /opt/remoto/scenarios/
sudo install -D -m 600 deploy/remoto.env.example /etc/remoto/remoto.env   # edit: port, folders, limits

# 4. Service
sudo cp deploy/remoto.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now remoto

# 5. Follow it
systemctl status remoto
journalctl -u remoto -f
curl -s http://localhost:8081/simulator/actuator/health
```

The app runs with `context-path` `/simulator` (`application.yml`, overridable by
`SERVER_SERVLET_CONTEXT_PATH`), because on the domain it shares the place with the descriptive
site, which sits at the root. What puts each thing on its own path is the reverse proxy:
`deploy/nginx-remoto.conf.example` has the static root, the `proxy_pass` of `/simulator/` with no
path rewriting, and what live progress (SSE) needs — `proxy_buffering off` and a generous
`proxy_read_timeout`; `deploy/Caddyfile.example` is the same thing for Caddy, which streams and
accepts any body size without being told to. In both, the port is the `SERVER_PORT` of
`remoto.env` (8081, the default of `application.yml`), and the actuator is kept off the public address — the deployment's health check
asks for it on the machine itself.

- **Updating:** it is automatic. Every push to `master` that passes the tests sends the site, the
  public scenarios and the new JAR to the server and restarts the service (the `deploy` job of
  `.github/workflows/ci.yml`; the *Secrets* and *Variables* are in the README, section
  "Deployment"). By hand, replace `/opt/remoto/remoto-web.jar` and run
  `sudo systemctl restart remoto`. Either way, running simulations are cancelled and queued ones
  are marked as interrupted; the scenarios and finished simulations of each area stay where they
  are.
- **Backup:** optional. The areas live in `/var/lib/remoto/sessions` and last up to 30 days
  without use; copying that folder preserves the work of whoever is using the site (when moving to
  another machine, for example). The public scenarios are in Git, and what is meant to last each
  person keeps by exporting.
- **HTTPS:** the proxy in front (nginx, Caddy or Apache) takes care of 443; uncomment
  `SERVER_FORWARD_HEADERS_STRATEGY=native` in `remoto.env`. Without it, the per-origin limit sees
  everyone with the proxy's address.
- **New public scenarios:** copy the JSON to `/opt/remoto/scenarios/`; no restart needed.
- **Windows:** the same `java -jar` can become a service with WinSW, using the variables of
  `remoto.env.example`.

## No login and no database

### Areas and the cookie

There are no accounts and no login. Each browser gets an area of its own, identified by a random
id (`g-` + 32 hexadecimal digits) in the `remoto-area` cookie (HttpOnly, SameSite=Lax and Secure
under HTTPS), which lasts **30 days** and is renewed on every visit; everything the person does
(copies of scenarios, edits, simulations) lives in that area. That is why scenarios and
simulations survive server restarts and crashes, and closing the browser. The area is only deleted
after 30 days without use, by the daily cleanup task, or when the person asks
(`DELETE /api/me`). The period is a single one, `remoto.retention.area-days` (30 by default): it
governs the cleanup, the cookie and the notice on the page. Since merely reading does not change
the files' dates, each area records its last use in a hidden file (`.ultimo-uso`), at most once an
hour. The id is also kept in the session, so that whoever blocks cookies stays in the same area
while it lasts. A tampered cookie cannot reach another folder: `UserId` only accepts the format it
generates itself.

To keep the work for longer, or take it to another computer or browser, the person **exports**:
each scenario as JSON (the same file the CLI runs), every scenario in a .zip, a whole simulation in
a .zip (configuration, manifest and every result as CSV). There, or after the period, they
**import** the .json files, or the .zip with them, into "My scenarios". The import checks each
scenario the way the editor and the queue check it (format, duration, steps, engine and the model
configuration), refuses files over 5 MB and imports up to 100 scenarios at a time; the refused ones
are listed with the file name and the reason. The site stores no personal data: the cookie carries
only the area's random id.

The `UserId` arrives in the cookie, which can be changed in the browser, but is only accepted in
the format the server generates; that is why it is always safe as a folder name. Scenario and run
ids are ULIDs validated (`Ids.requireSafe`) against path traversal.

### File layout

```
data/
└── sessions/<userId>/         one folder per area (browser)
    ├── .ultimo-uso            date of last use, for the cleanup
    ├── scenarios/<ulid>.json
    └── runs/<ulid>/
        ├── manifest.json      status, seed, engine version, configuration hash, dates
        ├── config.json        frozen copy of the configuration used
        └── results/*.csv.gz   time series and spikes
```

### What replaces the database's guarantees

| Guarantee | How |
|---|---|
| Atomicity | writes to a temporary file and renames it (`StorageFiles.writeAtomically`) |
| Concurrency between areas | one lock per area (`UserLocks`); different areas write in parallel |
| Editing in two tabs | ETag = SHA-256 of the file; `PUT` with a stale `If-Match` → `412` |
| Isolation | every access goes through the `UserId` of the `Caller`; another browser gets `404` |
| Recovery after a crash | on startup, `QUEUED`/`RUNNING` runs become `FAILED` ("interrupted"); the areas remain reachable through the cookie |
| Taking the data away | exporting each scenario (or all of them in a `.zip`) and each simulation; through the API, the whole area as well (`/api/me/export`) |
| Cleanup | a daily task: areas unused for 30 days and old runs that are not pinned |

### Several people simulating at the same time

- Up to `remoto.simulation.slots` simulations in parallel (by default: the CPU cores); each one
  takes a thread because the work is CPU-bound. With fewer slots than cores, each simulation of the
  original engine uses `threads-per-run` threads (by default: cores ÷ slots); the result does not
  change.
- **Fair queue** (`FairQueue`): one queue per area, dispatched round-robin. A sweep of 50 runs from
  one area does not block another area's single simulation.
- Quotas per area: `max-active-per-user` (2 by default) and `max-queued-per-user` (20 by default →
  `429`).
- With no login, opening another area is easy (another browser, a private window), so there is also
  `max-per-origin` (20 by default), which adds up the simulations running or queued across every
  area of the same address, and `max-queued-total` (200 by default), the ceiling of the server's
  queue; both answer `429` and accept 0 to turn them off. A room with everyone behind the same
  address (NAT) shares the per-origin limit: raise it if that is the case.
- A real limit: **one server instance**. Several machines would need a shared queue and common
  storage (S3/MinIO) — see the proposal.

## Languages

The simulator comes out **in English or in Portuguese** today, and the person can switch at any
time. They are always the same pages: what changes are the texts, which come from
`messages_<code>.properties`.

The languages are declared in `remoto.languages` (`RemotoProperties`; the `REMOTO_LANGUAGES`
variable), in order — the first is the default. Adding a language is adding the complete text file
and the code to that list: no new page, no new route, no language `if` in the code. `MessagesTest`
fails while a key is missing from any of the files, and `ThirdLanguageIntegrationTest` brings the
application up with a third, made-up language to prove that the path does not depend on there
being two.

### Which language

In order (`RemotoLocaleResolver`):

1. the `?lang=` of the selector, in this very request;
2. the **language entry point** in the address itself (`/simulator/pt`): the path states the
   language, so it beats the cookie — it is what makes the link from the project site open in its
   own language;
3. the person's earlier choice, in the `remoto-lang` cookie (HttpOnly, SameSite=Lax, Secure under
   HTTPS), which lasts a year;
4. the browser's `Accept-Language`, which carries the language of the region the visit comes from:
   any variant of a declared language will do (pt, pt-BR and pt-PT open in Portuguese);
5. the first declared language, for all the others and for the browser that says nothing.

It is the browser's language, not the country of the address: there is no geolocation database in
the project, and the header gets it right more often (someone outside Brazil with the browser in
Portuguese keeps reading in Portuguese).

The entry point is resolved in `RemotoLocaleResolver`, and not in the controller, because the
language has to be decided before the page starts being built: `LocaleModelAdvice`, which builds
the selector and the `hreflang` links, runs before the controller method.

### The selector

It sits in the top right corner of every page and is an ordinary link. On the **entry points**
(`/simulator/` and `/simulator/<code>/`) it leads to the other language's entry point, which is a
linkable address and what the project site uses; on the **inner screens** (editor, results) it
leads to the same screen with `?lang=`, keeping the other parameters, so that switching language
does not take anyone away from where they were. With three languages or more, one link appears for
each of the others (`LangLinks` returns a list, not "the other of the two"). Since the choice
becomes a cookie, the page also works with JavaScript turned off.

### Where the texts live

| Where | What |
|---|---|
| `messages.properties` | English, the default bundle (it serves any language without a file of its own) |
| `messages_pt.properties` | Portuguese |
| `/js/i18n.js` | the `js.*` keys, assembled by `I18nScriptController` for the language of the request; the browser uses `window.T("key", ...)`. It also publishes `window.ReMotoBase`, the application's base path, which `app.js` and `results.js` use to build the API addresses |
| `br.remoto.web.i18n.Messages` | `Messages.msg("key", ...)` on the server, including in objects that are not beans (editor fields, result views) |

`MessagesTest` fails if a key exists in one file and is missing from another, or if some text is
empty: a new text without a translation would show up on the page as the raw key.

The date format of simulations (day/month or month/day) and the decimal separator of durations
("5,0 s simulados" / "5.0 s simulated") also follow the language.

### What stays in English in every language

- The **editor screens** and the **menus of the results screen**: they are the labels, the units
  and the names of the old site (`LegacyScreens`, `ResultMenu`), the ones the papers cite.
  Translating them would put the interface out of step with the literature.
- The **names and descriptions of the scenarios**, which come from the old database and the files
  in `scenarios/`, and the names of the results (`forca-SOL`, `disparos-SOL`).
- The **bibliographic data** of the publications section of the site (titles, authors, venues).

### The version

The header shows the version the way the old site did ("Current Version: 2.1"): the number comes
from the `pom` through Maven's resource filtering (`remoto.version: @project.version@`) and the
page shows major.minor — "Version 4.0" or "Versão 4.0".

### The project site

The presentation in `docs/` is static and is served at the **root of the domain**, with the
simulator at `/simulator`; GitHub Pages publishes the same folder as a mirror. There are four
pages, one document per language (they are two parallel texts, not the same HTML with the strings
swapped):

| Address | File |
|---|---|
| `/` | `docs/index.html` |
| `/pt/` | `docs/pt/index.html` |
| `/guide/` | `docs/guide/index.html` |
| `/guide/pt/` | `docs/guide/pt/index.html` |

The CSS lives in one place (`docs/assets/site.css`), and `canonical` and `hreflang` are
**relative**, pointing at each other: that way the same folder works at the root of the domain and
under the mirror's path, and the deployment domain does not enter the repository. In
`docs/pt/guia/` a redirect was left to `/guide/pt/`, the guide's old address.

A small script, in the `<head>` of the four pages, sends the first visit to the version for the
browser's language; whoever clicks the selector fixes the choice (`localStorage`, key
`remoto.lang`, written through the `data-lang` attribute of the selector's links), and the redirect
never undoes it. Without JavaScript there is no redirect, and the selector still works.

The **contact e-mail** is not written in the pages: `docs/assets/site.js` builds it from the domain
the page is served from (`remoto@<domain>`, from `remoto.example.com` or `www.example.com` to
`remoto@example.com`, with second-level domains such as `com.br` handled). Where a mailbox makes no
sense — `localhost`, an IP address, the mirror on `github.io` — nothing is filled in and the
contact block is left with the GitHub issues alone, which is also what appears with JavaScript
turned off. `scripts/site-domain-test.mjs` checks that rule against a table of hosts, and runs in
CI.

`scripts/pages-figure.mjs` regenerates the opening figure in the two home pages, with the labels of
each language.

## Main page

The page has two columns, each with its own scrolling, so that the results are always in view:

- **On the left**, *My simulations* (scenario, status, creation and run time, from start to finish)
  and, below it, the *Scenarios*, in two tabs (*Public* and *My scenarios*), searchable by name, id
  or description. Each scenario shows the id and the first two lines of the description (a click
  shows the rest) and the buttons Run, Edit, Copy, Export and, on your own, Delete.
- **On the right**, the results of the open simulation. **Run** opens the new simulation there,
  with live progress; the editor's "Run this scenario" goes back to the page with it open. A click
  on a row of *My simulations* (or Enter, from the keyboard) opens another.

Every finished simulation has **Export** (the .zip with the manifest, the configuration and the
results as CSV), and the **Import…** of *My simulations* brings that .zip back, with a new id: the
manifest and the configuration are checked (against the hash recorded in the manifest), the names
of the results are validated, and the .zip may have up to 5,000 files and 2 GB uncompressed. On
narrow screens, the two columns sit one below the other.

## REST API

Every path below is relative to the application's base path, which is `/simulator` (see
"Installing on the server"): `GET /api/me` is served at `/simulator/api/me`.

| Method | Path | Description |
|---|---|---|
| GET | `/api/me` | the browser's area (id and space used) |
| GET | `/api/me/export` | the whole area (.zip) |
| DELETE | `/api/me` | cancels the simulations, deletes the area and the cookie |
| GET | `/api/scenarios/public` | public scenarios |
| POST | `/api/scenarios/public/{id}/copy` | copies a public scenario into the area |
| GET | `/api/scenarios/public/{id}/export` | downloads a public scenario (.json) |
| GET / POST | `/api/scenarios` | my scenarios / create |
| GET / PUT / DELETE | `/api/scenarios/{id}` | read (with `ETag`) / replace (`If-Match`) / delete |
| GET | `/api/scenarios/{id}/export` | downloads the scenario (.json, without the id) |
| GET | `/api/scenarios/export` | downloads every scenario of the area (.zip, one .json each) |
| POST | `/api/scenarios/import` | imports `files` (.json or .zip), multipart; returns the created and the refused ones |
| POST | `/api/runs` | runs: `{"scenario": {...}}` or `{"scenarioId"}`, `{"publicScenarioId"}` |
| GET | `/api/runs`, `/api/runs/{id}` | my runs / detail with the names of the results |
| GET | `/api/runs/{id}/events` | live progress (SSE, `progresso` event) |
| DELETE | `/api/runs/{id}` | cancels (if active) or deletes |
| PUT | `/api/runs/{id}/pinned` | pins (`true`) to escape retention |
| GET | `/api/runs/{id}/config` | the exact configuration used |
| GET | `/api/runs/{id}/export.zip` | the whole run: manifest, configuration and results as CSV |
| POST | `/api/runs/import` | imports an exported .zip (`file`, multipart) as a new run of the area |
| GET | `/api/runs/{id}/results/{name}.csv` | result as CSV |
| GET | `/api/runs/{id}/results/{name}.png` | figure as PNG |
| GET | `/api/runs/{id}/menu` | menus of the results screen for the run (original engine) |
| GET | `/api/runs/{id}/view?type=&element=&nucleus=&neuron=&graphic=&spec=...` | one chart or table of the results screen, as JSON |
| GET | `/api/runs/{id}/view.csv?...` | the same, as CSV (the old site's "file" option) |

Requests that change data require the CSRF token (the `XSRF-TOKEN` cookie → the `X-XSRF-TOKEN`
header). Errors follow RFC 9457 (`application/problem+json`).

## Scenario format (schemaVersion 1)

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

The same configuration + the same `seed` + the same engine version reproduce the result bit for
bit.

The `engine` field chooses the engine. There is one, `"legacy"` (the default when the field is
missing), which reads the model configuration from the `legacy` field, described below; the field
remains for future engines that read the same scenario. An unknown engine is refused with `400`. A
provisional engine, with a simplified set of motoneurons per nucleus driven by a command current
and a noise (the `nuclei` field), served as a demonstration while the models were being ported and
was removed; old runs of it still open, with the simple charts.

## Original engine (`legacy`)

The published ReMoto models (S/F motoneurons with active dendrites, conductances, Markov,
interneurons, synapses with depression/facilitation, Hill/Raikova/SOCDS, spindle, GTO, joint) run
on the new platform **with the same results, bit for bit, as the numerical code of the old site**:

- **Ported to `remoto-core`:** the models live in `br.remoto.core.model` (channels, synapses,
  membranes, afferent fibers, tracts, signals, stimuli, motor units, muscles, spindles, GTOs,
  joints), and the assembly of the network and the simulation loop in `br.remoto.core.simulation`:
  the `Simulation`, the factories, the classes of each element (which tie the configuration to the
  models) and, in `simulation.setup`, the `Configuration` with the objects the editor edits. The
  original code (104 files from the `src/` of the parent repository, which used to live in the
  `remoto-legacy-model` module) was replaced slice by slice, and the module ceased to exist;
  Struts, JSPs, applets and the old JFreeChart had already left the repository.
- **Reproducible:** the original code used `Math.random()` and `new Random()` with no seed. Now
  every one of those calls goes through `Rng` (`br.remoto.core.model.random`), one generator per
  thread which the engine initializes with the scenario's `seed`.
- **Synapse fix (engine `remoto-legacy-2b5-rng1-syn1`):** in December 2016 the parent repository
  replaced, in `MultSynapticConductance` (today `SynapseGroup`, in `remoto-core`), the time since
  the last event (`t − t0`) by the integration step in the decay of `Ron` and `Roff`. Since `ron`
  and `roff` only change when a pulse begins or ends, the conductance became frozen between
  events: an isolated spike rose to a seventh of the peak and decayed no further. The engine goes
  back to the closed-form solution of Lytton (1996), which is the one of the 2011 version and the
  one in force when the 2015 paper was configured; `SynapseGroupTest` compares the conductance
  with that solution. Results from different engine versions are not comparable.
- **No prints in the loop:** the original code printed to the console at every step of every
  descending tract (`"Poisson " + mean`), at every firing of a synapse with dynamics, at every
  step of the position task and of the spindle simulator, and for every neuron and synapse
  created. That was removed, as were the debugging prints of the assembly; the error messages and
  the model creation warnings were kept.
- **Performance:** synapses and muscle force without text lookups at every step, and parallel
  update of the neurons, with the same result bit for bit (see [Performance](#performance)).
- **Configuration without a database:** each configuration of the old HSQLDB becomes a JSON
  scenario with the rows of each table (format `remoto-legacy-db/1`, without the USER table). To
  run, those rows become typed parameters in `remoto-core` (`br.remoto.core.model.config`: one
  record per table, gathered in `ModelConfig`), and `ConfigurationAssembler` (the reading of the
  original `ConfigurationDAO`, without JDBC) builds the `Configuration` from them, with the same
  mapping as always. The reader (`LegacyTablesReader`) reproduces what HSQLDB did:
  `NUMERIC(128,4)` rounded to 4 places with half down, a decimal in an integer column truncated,
  null read as 0, and the row order of the `ORDER BY` of each query of the DAO, with the same
  sorting algorithm as HSQLDB 2.7.3. That algorithm is not stable, and the order of ties (the
  tracts of one nucleus, for example) decides the order in which the neurons are created and, with
  it, the order of the draws. Saving from the editor also does without the database:
  `ConfigurationTables` (in `simulation.setup`) generates the rows the original `saveConfiguration`
  inserted, and `LegacySchema` (the original schema, in `legacy-schema.sql`) does to them what
  HSQLDB did: the same types and roundings (`setDouble` goes through `BigDecimal.valueOf`),
  `NOT NULL`, the primary key and the order of the `SELECT` (that of the primary key, or that of
  insertion in the tables without one).
- **Outputs:** `forca-<nucleus>` (N), `disparos-<nucleus>` (motoneurons), `torque` (N·m) and the
  EMG of each muscle, in mV:
  - `emg-bruto-<nucleus>`: the sum of the MUAPs of the motor units (the original
    `instantMuscleEMG`, with attenuation);
  - `emg-<nucleus>`: what the results screen of the old site showed by default — the raw EMG with
    noise (the mean of |EMG| times the `noiseEMG` of the configuration) and a 50 to 1000 Hz
    band-pass (`ButterworthImpulseInvariance`). In the original code that calculation lived in the
    charting layer (`ResultDisplay`); now the engine does it at the end of the simulation. The
    only difference is the noise, which comes from a generator with the scenario's seed instead of
    `Math.random()`.

  Besides those, the engine records the data of the [results screen](#results-screen): the series
  of the muscle (length, velocity, acceleration, activations, forces of the elements, pennation
  angle, normalized force), of the tendon, of the musculotendon, of the spindle (Ia, II) and of
  the GTO (Ib) and of the joint (angle, velocity, centers of mass and of pressure, torques,
  disturbance); the spikes of each group (motoneurons at the soma and at the terminal, afferents
  at the terminal and at the stimulus point, interneurons and tracts by type); the mean ISI each
  type of tract used; tables with the properties of each motoneuron, interneuron, afferent and
  motor unit; the signals of the stored neurons (Vs, Vd, conductances and the injected current,
  one table per neuron); the raw EMG without attenuation and that of each stored unit; the summary
  of the spikes (the "summary" page of the old site); and the `catalogo`, which describes every
  output. All of that is already computed by the simulation: nothing is drawn at random and the
  basic outputs do not change. The mean ISI of the tracts and the injected current are recorded
  during the simulation, with the value the model used, because the random and gamma signals draw
  at every evaluation and cannot be recomputed afterwards. Two series of the original were
  divisions by zero and no longer come out: the rate of the tendon organ of a muscle with no
  active Ib fibers (the original divides the rate by their number: NaN, then infinity) and the
  normalized force of a muscle with no motor units (maximum force 0). No fiber read the first one,
  so the dynamics do not change; only the "complete" hash of the golden master of 17 changed. In
  the summary of the spikes, the frequencies (of the first interval, of the first four and of the
  steady state) come from the distinct instants of the train, in order: the reset of a tract
  records a spike that may coincide with the first one, and the noises of Gaussian intervals
  sometimes repeat an instant, which gave an interval of 0 and an infinite frequency (the
  "complete" hash of 42 changed because of that). The spike count is still that of the train.

### Guarantees (tests in `br.remoto.core.engine.legacy`; those of the editor, in `remoto-web`)

| Test | What it guarantees |
|---|---|
| Original database × reference | The 30 configurations of the original database (40 ms, seed = ID) match hashes generated when the configuration was still assembled by the original `ConfigurationDAO` reading from HSQLDB (before that, a test compared the two paths field by field) |
| Export without HSQLDB | The export reads `remoto.script` directly and reproduces the `remoto-*` scenarios of `scenarios/`, apart from the description, written by hand in those that had none, and the gamma tracts of 24 and 42, published as Poisson (the three of the old site had been exported by HSQLDB) |
| No NaN in the series | In configurations 17 and 18, no series has NaN or infinity: the tendon organ with no Ib fibers and the normalized force of a muscle with no motor units do not come out; the summary of `projectfr-neural-tract`, with repeated spikes at t = 0, has no infinite frequency (`LegacyEngineTest`) |
| Complete model | `modelo-completo.json` turns on every element of the model, and each one (muscles, spindles, GTOs, afferents, interneurons, tracts, joint) appears in the outputs |
| Signals over time | With the imposed angle, the ankle follows the initial angle plus the signal, and the length and the spindles follow it; with the modulated fusimotor drive, nothing changes before the signal starts, and the static one increases the firing of the IIs; in the isolated spindle, the dynamic one increases the Ia on stretch and the static one the II (`SignalsOverTimeTest`, `SpindleTest`); `estiramento-tornozelo.json` imposes the trapezoid |
| Stimulus and afferents | By current, the pulse width does not change who fires; by charge, the 1 ms pulse recruits almost the same axons and the 0.2 ms one none; each width needs the intensity of the Lapicque curve (`LeakyChargeTest`); the chosen gamma order changes the regularity of the afferents, and 0 is that of the original (`NerveAndAfferentOptionsTest`, `AfferentFiberTest`) |
| Node of Ranvier | At rest, the node sits at the resting state of table 1 of Howells et al. (2012); the threshold of the configuration holds for a 1 ms pulse; the intensity-duration constant comes out of the model, around 0.4 ms in the motor and 0.5 ms in the sensory one; there is refractoriness (`AxonNodeTest`); in the motor one, the 1 ms pulse recruits almost the same axons as the current threshold, and the 0.2 ms one none (`NerveAndAfferentOptionsTest`) |
| projectFR examples | The 16 `projectfr-*.json` are what the generator produces, and each one validates and runs (`ProjectFrExamplesTest`) |
| ReMoto_AIDP tasks | The 6 `aidp-*.json` are what the generator produces, and each one validates and runs (`AidpExamplesTest`) |
| Conduction block | With 10% of the nucleus blocked, the 90 slowest motoneurons fire at the soma but carry no spike to the terminal, and the fastest ones do; with the whole nucleus blocked, the soleus produces no force; the fraction is kept in `settings` and comes back on reopening, and outside [0, 1] it is refused (`AxonConductionBlockTest`) |
| Rate series | The "series" shape interpolates the points, makes steps and is 0 outside the window; the 100 axons of CM_ext follow 0 Hz, a step to 200 Hz and a ramp back (around 2000 spikes on the plateau and 1000 on the ramp); the series is kept in `settings` (`ModulationTest`, `TractSeriesTest`) |
| Batch protocols | The parameter changes a table cell, the `settings`, the seed or the duration; the measures; in the H/M curve of configuration 17, with no current no motoneuron fires in the window of the M wave and, with 40 mA, all of them do; in force variability, the longer interval gives less force (`SweepTest`); `remoto sweep` writes one row per simulation (`RemotoCliTest`) |
| Decay with distance | With decay in the synapses of the CM_ext tract on the soleus, the share of the tract in the conductance of each motoneuron falls and is larger in the middle of the nucleus than at the ends; the value is kept in `settings` and comes back on reopening (`SynapseDistanceDecayTest`) |
| Reproducibility | Same seed gives identical outputs; a different seed, different outputs |
| Golden master | The outputs of configurations 17, 24 and 42 of the database (the three public scenarios of the old site, as they are there), with 40 ms, match the hashes in `golden-master.properties` (force, spikes and torque in one hash; EMG in another; every output, those of the results screen included, in a third) |
| Editor × original DAO | Editing every screen at once and saving gives the same tables the original `saveConfiguration` wrote to an HSQLDB, in the three public scenarios of the old site (`LegacyEditorTest`); saving without editing preserves the tables and the simulation (`LegacyStoreTest`) |
| Synapse × analytical solution | A synapse driven as in the Runge-Kutta follows the closed-form solution of Lytton (1996): it rises during the pulse and decays afterwards (`SynapseGroupTest`, in `remoto-core`) |
| 1 thread × 4 threads | The three public scenarios of the old site give identical outputs with any number of threads, and a worker thread cannot draw at random (`ParallelUpdateTest`) |

When porting or refactoring a model, the golden master shows at once whether the result changed.

### Exporting configurations from the old database

```bash
java -jar remoto-cli/target/remoto-cli-4.0.0-SNAPSHOT.jar export-legacy --db legacy-db/remoto --list
java -jar remoto-cli/target/remoto-cli-4.0.0-SNAPSHOT.jar export-legacy --db legacy-db/remoto --owner guest --out scenarios
java -jar remoto-cli/target/remoto-cli-4.0.0-SNAPSHOT.jar export-legacy --db legacy-db/remoto --id 34,41 --out my-scenarios
```

### Batch protocols

`remoto sweep` runs a scenario once for each value of a parameter and writes to `sweep.csv` one
row per simulation, with the value and the measures (`br.remoto.core.protocol`). Two protocols are
ready:

- `--protocol hm`: the recruitment curve of the H reflex and the M wave. It varies the intensity
  (mA) of the stimulus of a nerve (`--nerve`, PTN by default) and measures the peak-to-peak of the
  EMG of the muscle (`--muscle`, SOL by default) and how many motoneurons fired in the windows of
  the M wave (from 2 to 20 ms after the start of the stimulus) and of the H reflex (from 20 to
  50 ms), which `--m-window` and `--h-window` change. The nerve is activated; with no stimulus
  window, it gives a single pulse, at the START of the table. The simulation goes to 10 ms after
  the end of the H window.
- `--protocol force`: force variability. It varies the mean interval (ms) of a descending tract
  (`--tract`, CM_ext by default) and measures the mean, the standard deviation and the coefficient
  of variation of the muscle force, and how many motoneurons fired, in a window (`--from` and
  `--to`; the default is the second half of the simulation, after the transient).

Any other batch is a `--param` and one or more `--measure`:

```bash
java -jar remoto-cli/target/remoto-cli-4.0.0-SNAPSHOT.jar sweep scenario.json \
  --param NERVE:CD_NERVE=PTN:PEAK --range 5:40:1 \
  --measure "M=p2p(emg-SOL,7,25)" --measure "H=p2p(emg-SOL,25,55)" --keep-runs -o results/batch
```

The parameter is a cell of the tables (`TABLE:KEY=VALUE[,KEY=VALUE]:COLUMN`; every matching row
changes), a value of the `settings` (`settings.nerveStimulus.PTN.pulseWidth`), the `seed`
(repetitions with different draws) or the `durationMs`. The measure is
`[name=]function(output[,from,to])`, with the window in ms: `p2p`, `max`, `min`, `mean`, `sd` and
`cv` on a series (force, EMG, angle...), and `count` and `units` on a spike train. The values come
from `--values 5,10,20` or from `--range FROM:TO:STEP`; `--keep-runs` also stores the results of
each simulation. The simulations run one after another, and the site does not have the batch
protocols yet.

The public scenarios of `scenarios/` are in the [Public scenarios](#public-scenarios) section. The
database is a text file (`remoto.script`, with one `CREATE TABLE` per table and one `INSERT` per
row), and `LegacyDatabase` reads it directly, without HSQLDB.

### Public scenarios

| File | Scenario | Simulated |
|---|---|---|
| `remoto-17-…` | *Default - Soleus contraction*: the soleus driven by 100 descending axons (Poisson) | 5 s |
| `remoto-24-…` | *ISB_2013 - Stretch Reflex*: the four muscles, with Ia afferents; the triceps surae receives the command (tract in Poisson) | 30 s |
| `remoto-42-…` | *JN - Watanabe and Kohn (2015)*: the soleus, with the command modulated at 20 Hz from 3 s on (tract in Poisson) | 15 s |
| `remoto-14-…` | *Isometric contraction*: the triceps surae (SOL, MG and LG) with 400 descending axons (Poisson, 11 ms) | 5 s |
| `remoto-16-…` | *Recruitment*: the same, with the interval of the axons falling as a ramp from 20 to 4 ms | 20 s |
| `remoto-18-…` | *Neuroscience Meeting 2012*: H reflex of the soleus and the pathway of reciprocal inhibition through the peroneal nerve | 0.2 s |
| `remoto-19-…` | *Biceps Brachii - Stim Ia*: pulses at 5 Hz on the nerve during a contraction (in the SOL nucleus) | 30 s |
| `remoto-25-…` | *Position Task*: ankle position task with the triceps surae and Ia afferents | 30 s |
| `remoto-26-…` | *Position Task - IIa*: the same, with group II afferents | 30 s |
| `remoto-27-…` | *Position Task - IIa no Ia*: the same, with the group II afferents only | 30 s |
| `remoto-28-…` | *Position Task - Ib*: number 25 with Ib afferents and Ib interneurons | 30 s |
| `remoto-32-…` | *variability_gamma*: isometric contraction of the triceps surae, interval of 3 ms | 5 s |
| `remoto-34-…` | *Identification_SOL_MG*: the triceps surae with the command modulated at 20 Hz from 2 s on | 15 s |
| `remoto-35-…` | *Identification_LG*: the two gastrocnemii, interval of 3 ms | 1.5 s |
| `remoto-36-…` | *Identification SOL_MG*: number 34 with 1.5 s (the modulation does not get to start) | 1.5 s |
| `remoto-38-…` | *IdentSOL*: the soleus, interval of 12 ms | 1.5 s |
| `remoto-39-…` | *IsometricTaskAM*: isometric contraction of the triceps surae with Ia afferents | 8 s |
| `remoto-41-…` | *AMmodRenshaw*: the soleus, with the command modulated at 20 Hz from 3 s on | 15 s |
| `remoto-44-…` | *identSOLRenshaw*: the soleus with the Renshaw cells (recurrent inhibition) | 1.5 s |
| `modelo-completo` | Every element of the model turned on, starting from number 24 (details in the scenario description) | 2 s |
| `estiramento-tornozelo` | Stretch reflex: number 24 with the ankle at an imposed angle, a 5° trapezoid in dorsiflexion from 700 ms on | 1.5 s |
| `projectfr-antidromic-stimulation` | *AntidromicSimulationofMNandRC*: Antidromic stimulation of 5 soleus motoneurons connected to 2 Renshaw cells | 0.6 s |
| `projectfr-force-variability` | *ForceVariability*: Isometric force of the triceps surae with 400 axons in a gamma process, at 140 spikes/s | 16 s |
| `projectfr-h-reflex-m-wave` | *H-ReflexM-WaveCurve*: H reflex and M wave of the soleus with a 12 mA pulse; the curve comes from `remoto sweep` | 0.05 s |
| `projectfr-ankle-impedance` | *ImpedanceAnkle*: The ankle disturbed by a sum of 200 sinusoids, with the four muscles passive | 2 s |
| `projectfr-renshaw-injected-current` | *InjectedCurrentRenshawCellPool*: 350 Renshaw cells with injected current, without motoneurons | 0.1 s |
| `projectfr-isometric-closed-loop` | *IsometricClosedLoop*: The soleus with a descending command and Ia feedback, and the ankle on a 1 Hz sinusoid | 5 s |
| `projectfr-mn-pool-renshaw` | *MNPoolWithRenshawCells*: The soleus with recurrent inhibition by the Renshaw cells | 5 s |
| `projectfr-motor-unit-pool-emg` | *MotorUnitPoolEMG*: 800 motor units with constant current in the dendrite: the force and the EMG | 5 s |
| `projectfr-descending-command` | *MotorUnitWithDescendingCommand*: The soleus driven by 400 descending axons at 60 spikes/s | 1 s |
| `projectfr-node-axons` | *MotorUnitWithNodeAxonsAndDescendingCommand*: 3 motoneurons with a node of Ranvier at the stimulus point, step of 0.005 ms | 0.1 s |
| `projectfr-hill-muscle` | *MuscleHillExample*: The Hill muscle of the soleus, with the ankle on a 1 Hz sinusoid | 1 s |
| `projectfr-muscle-spindle` | *MuscleSpindleExample*: The spindle of the tibialis anterior on a 0.25 Hz sinusoidal stretch | 20 s |
| `projectfr-neural-tract` | *NeuralTractExample*: 400 descending axons on their own, at 83 spikes/s | 10 s |
| `projectfr-neural-tract-unit` | *NeuralTractUnitExample*: One descending axon with intervals of 4 ms and their statistics | 10 s |
| `projectfr-postural-control` | *PosturalControl*: Upright stance with the four muscles, Ia, II and Ib afferents and interneurons; the body falls in about 1.3 s | 12 s |
| `projectfr-reciprocal-inhibition` | *reciprocalInhibition*: Reciprocal inhibition of the soleus by a pulse on the common peroneal nerve | 1.1 s |
| `aidp-force-task-s0` | *ForceTask*, S0: Isometric triceps surae, with the descending command rising from 40 to 250 spikes/s in 2 s and the motor axons from 44 to 53 m/s | 12 s |
| `aidp-force-task-s1` | *ForceTask*, S1: The same task with the demyelinated velocities (from 5 to 51 m/s in the S units, from 52 to 40 m/s in the FF ones) | 12 s |
| `aidp-force-task-s2` | *ForceTask*, S2: The velocities of S1 and the conduction block of 65% of each motor nucleus | 12 s |
| `aidp-position-task-s0` | *PositionTask*, S0: The foot free to rotate from 5° on, against 10% of the maximum voluntary torque, with the four muscles and the Ia afferents | 12 s |
| `aidp-position-task-s1` | *PositionTask*, S1: The same task with the demyelinated velocities | 12 s |
| `aidp-position-task-s2` | *PositionTask*, S2: The velocities of S1 and the 65% block | 12 s |

The first three are the public ones of the old site (user `guest`). The following are
configurations of other users of the database, published with a description written from what each
one turns on; the tables are those of the database, with two exceptions: the gamma tracts became
Poisson (see below), and the *Position Task* ones (25 to 28) carry the ankle in position task in
their `settings`. The database did not store the joint model, and with the ankle isometric and the
default muscle the spindle is not integrated and the afferents do not fire; with the position one,
the foot moves against a load of 15% of the maximum voluntary torque, and the afferents respond.
`modelo-completo` starts from number 24 and turns on the II and Ib afferents, the interneurons
(reciprocal Ia, Ib, group II and Renshaw) of the extensors and of the flexors, the six descending
tracts with Poisson and a pulse on the tibial nerve, and chooses, in the `settings`, the Hill
muscle and the ankle as an inverted pendulum; the rates and the pulse were chosen to show every
element, not to reproduce an experiment. `estiramento-tornozelo` also starts from number 24 and
chooses, in the `settings`, the Hill muscle and the ankle at an imposed angle: at 700 ms the foot
is taken 5° into dorsiflexion in 100 ms, stays for 100 ms and comes back in 100 ms; the spindles
of the triceps surae respond, and the Ia afferents excite the motoneurons. It shows the feature; it
does not reproduce an experiment.

The 16 `projectfr-*` convert the examples of the `Examples` folder of
[projectFR](https://github.com/rnwatanabe/projectFR), the Fortran version of ReMoto (R. N.
Watanabe). Each one has the design of the example (what is simulated, for how long, with what
command, stimulus, mechanics and fusimotor drive) on top of configuration 24, which has the four
muscles and every table; the parameters of the models are those of ReMoto in Java, and when the
example changes a connectivity, a conductance or the dynamics of a synapse (projectFR uses the same
structure, in µS instead of nS), the scenario changes too. The batches of the examples become one
scenario and the `remoto sweep` command in the description (the H/M curve, force variability, the
trials of postural control), and what has no equivalent is also in the description: the chain of 50
nodes of Ranvier becomes one node at the stimulus point, and the isolated spindle receives its
length from the ankle, calibrated for the range of the example. The rates that do not give a whole
mean interval (the MEAN column of the database is an integer) use the interval 0 and a constant
signal with the *rate* target. The files come from a generator (`ProjectFrExamples`, in the tests
of `remoto-cli`); `ProjectFrExamplesTest` checks that the files are what it produces and runs each
one, and `-Dremoto.gerar.cenarios=<folder>` writes them again. projectFR is GPL-3: from the
examples come the design of the experiments and some values, not code.

The 6 `aidp-*` convert the force and position tasks of
[ReMoto_AIDP](https://github.com/oliveiramarina/ReMoto_AIDP), the Fortran version adapted to
demyelinating diseases (M. C. de Oliveira, R. N. Watanabe and A. F. Kohn, 2022; the last commit is
from September 2022, and projectFR stopped in December 2019). Each task has the three subjects from
there, which in the files `confForceTask_S*.rmto` and `confPositionTask_S*.rmto` differ only in the
conduction velocity ranges of the motor axons and, in S2, in the block: the healthy S0 conducts
from 44 to 53 m/s (the S units from 44 to 51, the FR ones from 51 to 52, the FF ones from 52 to
53); in S1 and S2 the range of the S units drops to 5 to 51 m/s and that of the FF ones falls from
52 to 40, so that the thickest axons become the slowest; and S2 blocks 65% of each nucleus,
counting from the slowest. In the force task, the triceps surae stays isometric and the 400
descending axons go from 40 to 250 spikes/s in 2 s; in the position one, the foot rotates freely
from 5° on against 10% of the maximum voluntary torque, the tibialis anterior comes in, and the
command goes from 40 to 72.5 spikes/s. In the first 2.5 s, the soleus force at the end of the
simulation falls from 3221 N (S0) to 1853 N (S1) and 1176 N (S2), and the ankle angle of the
position task goes from −1.7° (S0) to +0.9° (S2): the foot no longer holds the plantar flexion.

What has no equivalent is in the description of each scenario. ReMoto_AIDP draws the velocity of
each axon from a Gaussian truncated to the range, with a mean and a deviation of its own
(`meanvel`, `stdvel`); here the velocity follows the original's ramp from one end of the range to
the other, because the coefficient of variation of ReMoto in Java (`axonVelocitySTD`, from the
*Miscellaneous* screen) is the same for the motor and the sensory axons, and ReMoto_AIDP does not
demyelinate the sensory ones. The order of the gamma process of the command, which there is 10 in
the first couple of seconds and 1 (force) or 7 (position) afterwards, stays at that of the stretch
the experiment measures. Raikova's twitch as the activation of the Hill muscle does not exist here
(Raikova's is another muscle model, without Hill's mechanics), and the maximum voluntary torque of
the position task is that of ReMoto in Java, 79 N·m, and not the 12 N·m from there; the fraction,
10%, is that of the experiment. The files come from a generator (`AidpExamples`, in the tests of
`remoto-cli`), with the same `AidpExamplesTest` and the same `-Dremoto.gerar.cenarios=<folder>`
option. ReMoto_AIDP is GPL-3: from the tasks come the design of the experiments and the velocity
ranges, not code.

**Tracts with a gamma process.** The gamma process of the descending tracts uses as its order the
`std` of the `NeuronVO`, which the original `ConfigurationDAO` did not store: it wrote the `order`
field, which nothing reads, in the `STD` column, and read it back into the same field. So, in the
configurations of the database with a gamma tract, the tract only fires at t = 0, and the
motoneurons receive no command; on the old site, the Std had to be typed into the descending
commands screen in every session. In the configurations published with gamma tracts (24, 25 to 28,
32, 34 to 36, 38, 39, 41, 42 and 44), the tracts became Poisson, with the same mean interval. In 32
(*variability_gamma*), that erases the very difference it studied; for a gamma tract, choose
*Gamma* and the order in *Std* on the *Descending Commands* screen: the editor now stores the Std
(see [Scenario editor](#scenario-editor)). Left out were the tests (29, 33, 37 and 40), numbers 20
and 21 (with no descending command, the pulses on the nerve do not make the motoneurons fire) and
their variations (22 and 23), the variations close to 14 (30 and 31) and the 100 s one (43).

### Performance

The original engine is heavy: one simulated second of the public scenarios takes 17 to 30 s with 4
threads. The long scenarios (5 to 30 simulated seconds) take minutes; the progress appears live and
the CLI accepts `--duration-ms` for quick tests.

The result is the same, bit for bit, before and after what follows.

Profiled with JFR, the time went to the synapses (`getValue`/`getCurrent`, 4 groups × 4 stages of
the Runge-Kutta per motoneuron at every step), to lookups in a `Hashtable` by a text key assembled
at every spike, and to the muscle force. It now goes like this:
- the key of each neuron and the conductance each spike turns on are resolved once, and not at
  every spike; a marker per conductance replaces the sweep of the list of active pulses; the value
  of the synapse is reused when the Runge-Kutta asks for the same instant twice with no event in
  between (k2 and k3); the motor units of each muscle are resolved once;
- **parallel update of the neurons** (`NeuronUpdater`): motoneurons and interneurons go to several
  threads; tracts, afferents and whatever else draws random numbers stay on the simulation's
  thread, in the original order, so that the random sequence does not change; the spikes of each
  step are delivered at the end of the step, in the order of the sequential loop. This is
  equivalent to the original loop because every synaptic delay is longer than the step (checked
  during assembly; if it is not, the engine uses the sequential loop). A worker thread that tries
  to draw fails the simulation (`Rng.forbid`) instead of changing it silently.
  `ParallelUpdateTest` requires identical outputs with 1 and 4 threads, and the golden master did
  not change.

Total time (1 simulated second, a machine with 4 physical cores; the outputs match byte for byte):

| Scenario | Before | 1 thread | 4 threads |
|---|---|---|---|
| 17, Soleus contraction | ~51 s | ~46 s | ~23 s |
| 24, ISB 2013 stretch reflex | 71 s | 65 s | 30 s |
| 42, Watanabe and Kohn (2015) | 32 s | 28 s | 17 s |

With 4 threads the simulation's thread becomes the bottleneck: it carries the tracts and afferents,
the muscle force, the spindle and the delivery of the spikes. Eight threads do not beat four on a
CPU with 4 physical cores. Threads: `--threads` in the CLI (by default: the CPU cores) and
`remoto.simulation.threads-per-run` on the server (by default: cores ÷ slots, so that simultaneous
simulations do not compete for the CPU).

## Scenario editor

In "My scenarios", the **Edit** button opens the editor, built from the editor of the old site
(`WebContent/jsp` + `br.remoto.control.action`, today only in the parent repository): the same side
menu, the same screens, the same labels and units. Public scenarios belong to everyone and are
read-only: **Edit** on a public scenario creates a copy in "My scenarios" and opens the editor on
it (**Copy** only creates the copy).

**How it works (the `br.remoto.web.editor` package, in `remoto-web`):**

1. The scenario becomes the original `Configuration` (`LegacyConfigurations.load`).
2. Each screen lists the same objects the original Action put in the form (`NeuronVO`,
   `ConductanceVO`, `NerveVO`, `Current`, `MotorUnitVO`, `MiscellaneousVO`), through the same
   methods of the `Configuration`, and edits the same JavaBean properties as the JSPs.
3. On saving, the screen repeats the `setConfProperties` of the Action (limits such as at most 1000
   neurons per type, minimum ISI, minimum step, minimum refractory periods).
4. The `Configuration` is written back into the scenario's tables the way the **original
   `ConfigurationDAO.saveConfiguration`** wrote them (`ConfigurationTables`), with no database.

Tests guarantee that loading and saving without editing gives back the same tables and the same
simulation (`LegacyStoreTest`), and that editing every screen saves what the original DAO saved
(`LegacyEditorTest`). Two differences, both from the original code: `DT_CONFIGURATION` becomes the
date of the save, and the `NOTHING` table (which the DAO only writes and nobody reads) is kept as
it was.

**Screens (all of those of the old menu):**

| Group | Screens |
|---|---|
| — | Scenario (name, description, seed) |
| Structure | Descending Commands, Neural Elements |
| Neural Parameters | Synapses, Kinetic Properties, Synaptic Dynamics, Conductances, Motoneurons, Interneurons, Sensory Fibers |
| Proprioceptors | Muscle Spindle, Golgi Tendon Organ |
| Biomechanical Elements | Biomechanical Elements (joint and muscle models) |
| Biomechanical Parameters | Joint, Muscle Units, Musculotendon, Electromyogram, Miscellaneous |
| Stimulation | Nerve Stimulation, Injected Current |
| Simulation | Duration and Time Step (on the old site, *Run → Simulation Start*; here the screen only keeps the duration and the step, and what runs it is the "Run this scenario" button) |

**Fields the original DAO did not store.** Some old screens edited properties of the
`Configuration` that `ConfigurationDAO` did not save (they held only for the session): joint,
muscle, spindle, GTO and MUAP models, distribution of the motor units, FES recruitment order, ankle
and knee angles, gamma frequencies and spindle gains. On the new platform they live in the
scenario's `legacy.settings` field (`LegacySettings`) and are applied after the loading by the DAO;
scenarios exported from the database have no `settings` and use the DAO's defaults.

The neurons that store signals (the *Additional configuration* section of the motoneuron and
interneuron screens: "Index of MNs that will have signals stored") also live in the `settings`,
under the `storedNeurons` key ("nucleus:type" → the two indices). With no choice, the original's
hold: the first and the middle one of each type of motoneuron, and the first of each type of
interneuron.

The *Std* of the descending tracts (the *Descending Commands* screen) also lives in the `settings`,
under the `tractStd` key ("DT:type" → value). In the gamma process it is the order, which must be a
whole number; in the Gaussian one, the standard deviation of the interval. The original DAO wrote
another field in the `STD` column (`order`, which nothing reads), and the Std was lost on saving,
leaving the gamma tracts mute after t = 0. The tables are still the same as the DAO's; without
`tractStd`, the DAO's value holds (0).

The features the old site did not have (and the Fortran versions do; see item 13 of
[Next steps](#next-steps)) also live in the `settings`, and without them everything is as in the
original:

- **Angle imposed on the ankle** (the *Joint* screen, and the *Imposed angle* joint model in
  *Biomechanical Elements*, `cdJointModel` = `"imposed"`): the isometric ankle following the initial
  angle plus a signal, in degrees (`jointAngleSignal`: the shape and the parameters of a modulating
  signal, like those of the tracts). The length of the muscles and the spindles follow; the spindle
  is only integrated with the Hill muscle.
- **Fusimotor drive over time** (the *Muscle Spindle* screen): signals added to the constant
  dynamic and static γ firing (`gammaDynamicSignal`, `gammaStaticSignal`), never below zero.
- **Pulse and threshold of the nerve stimulus** (the *Nerve Stimulation* screen, `nerveStimulus`:
  nerve → `pulseWidth`, `thresholdModel`, `tauMotor`, `tauSensory`). The original's pulse is 1 ms
  long, and the axon fires when the intensity passes its threshold (`"current"`). By leaky charge
  (`"charge"`), each axon integrates the stimulus with the time constant of its type (motor or
  sensory) and fires when the charge passes the threshold: a pulse of width *d* needs
  `rheobase / (1 − e^(−d/τ))` (Lapicque), and the chronaxie is τ ln 2. The thresholds of the tables
  are still the current of a 1 ms pulse. The defaults, 0.45 ms in the motor axons and 0.65 ms in
  the sensory ones, are of the order of those measured in human nerves, in which the constant of
  the sensory ones is the larger (which is why long pulses favour the H reflex); check the
  literature before using them in a piece of work.
- **Node of Ranvier at the stimulus point** (the third threshold option, `thresholdModel` =
  `"node"`): each axon of the nerve, motor or afferent, has a node of Ranvier and the neighbouring
  internode, linked by the Barrett-Barrett pathway, with the channels of Bostock's human axon model
  (transient and persistent sodium, fast and slow potassium, h current in the internode, leakage
  and pumps) and the motor and sensory parameters of Howells, Trevillion, Bostock and Burke (2012,
  *J Physiol* 590:1625–1640, tables 1 and 2 and the appendix). The stimulus enters the node with
  the scale of the configuration's threshold (the current of a 1 ms pulse is still the threshold),
  and the model gives the intensity-duration curve, the refractoriness, the supernormality and the
  accommodation; the time constants of the charge threshold are not used. Conduction from the
  stimulus point is still a delay, and the spikes that come from the soma or from the receptor do
  not pass through the node. The appendix published on PubMed Central has the equations cut off on
  the left: the voltages follow the circuit of figure 1, and the pumps are those that keep the
  resting potentials of table 1 (computed that way, they give −41 and −9.2 pA in the motor one,
  against −33 and −7.9 pA from the table; some detail of the original differs).
- **Gamma order of the afferents** (the sensory fibers screen, `afferentGammaOrder`: "muscle:type"
  → a whole order). Firing from the receptor is a gamma process; the original fixed the order in
  the code (Ia 25, Ib 30, II 400, and the II goes back to 25 after the first spike). A higher order
  makes the firing more regular; 0 keeps the original's.
- **Signals as a series of points** in the imposed angle and in the fusimotor drive: the *series*
  shape, with the points in `points` (pairs of [time in ms, value]) and, in the editor, in the
  *Series points* field; `projectfr-ankle-impedance` uses a sum of 200 sinusoids sampled every
  0.5 ms.
- **Rate series of the tracts** (the *series* modulation and the new part of the *Descending
  Commands* screen, `tractSeries`: "DT:type" → a list of pairs of [time in ms, value]): the signal
  follows the points, interpolated, from the first to the last (two points at the same time make a
  step). With the *rate* target, the value adds to the rate of the mean interval; with the mean
  interval at 0, it is the rate of each axon itself, in spikes/s. In the editor, the points are
  written as "0 20; 1000 40".
- **Conduction block of the motor axons** (the *Conduction block* part of the motoneuron screen,
  `axonConductionBlock`: "nucleus" → the blocked fraction, from 0 to 1): the fraction of the
  nucleus, counting from the slowest axon, whose conduction is blocked. The motoneuron keeps firing
  at the soma, but the velocity becomes 0 and the delay to the end-plate becomes that of the
  minimum of 0.01 m/s (tens of seconds), so that no spike reaches the terminal and the motor unit
  produces no force; in the table of motoneuron properties, the velocity comes out as 0. It is the
  block of [ReMoto_AIDP](https://github.com/oliveiramarina/ReMoto_AIDP) (Oliveira, Watanabe and
  Kohn, 2022), which there sets the velocity of the blocked ones to zero. It holds for the motor
  nuclei only, and the order is that of the index in the nucleus, in which the S units come first.
- **Load of the position task** (the *Load of the position task* field of the *Joint* screen,
  `percentageOfMVC`): the fraction of the maximum voluntary torque (79 N·m) that the pedal opposes
  to the foot, in the *Position Task* joint model. The original `ConfigurationAssembler` fixed it
  at 0.15, and it is only stored when it leaves that value.
- **Decay with distance** (the *Distance decay* column of the *Synaptic Parameters* screen,
  `synapseDistanceDecay`: "preNucleus>nucleus:type" → dec, in mm²): each synapse of that type gets
  the weight `dec / (dec + d²)`, with *d* the distance between the two cells along the
  caudal-rostral column, and is not created below 1%, as the original's Renshaw ones are not.
  Motoneurons and interneurons sit where the original put them (the initial and final positions of
  each nucleus, in *Miscellaneous*); the afferents of a muscle spread over its motor nucleus, and
  the descending axons over the whole column. Between Renshaw cells and motoneurons, 0 keeps the
  factors of the *Miscellaneous* screen.

**Still left out:** the choice of joint (the converted scenarios only have ankle data), the muscle
boxes of *Biomechanical Elements* (the original Action did not save them; use *Neural Elements*),
and the neuromodulation level and the CV of the Ca⁺⁺ threshold (the original Action did not save
those either).

## Results screen

When a run of the original engine is opened, the page shows the results screen of the old site
(`results.jsp` + `ResultsAction` + `ResultDisplay`), with the same menus, names and axes:

| Menu | Options |
|---|---|
| Type | *Biomechanical Elements*, *Neural Elements*, *Properties*, *Summary* and the ready-made layouts (*Spikes MNs*, *Spikes Ias*, *Forces*, *Lengths*, *General*, *generalForSoleus*/*MG*/*LG*/*TA*) |
| Biomechanical | the 37 charts from there: muscle, tendon, musculotendon, motor units (twitch peak, contraction time, half relaxation, MUAP, position in the cross-section), EMG, joint and the curves of one quantity against another |
| Neural | motoneurons (18 charts: raster at the soma or at the terminal, instantaneous frequency, ISI, histogram, soma and dendrite potentials, conductances, injected current, properties, Vs × dVs/dt, stimulus rate), afferents, tracts and interneurons; for all of them, one type or one stored neuron |
| EMG | filter (1st/2nd or 3rd/3rd order, or none), cut-offs, noise, attenuation, sampling, and the EMG of a stored unit |
| Analysis | statistics (points, mean, variance, standard deviation, CV, minimum, maximum, with the lines of the mean ± SD) and FFT (points, periodic signal, axes) |
| Advanced Graphic Settings | up to 8 subplots, overlay (*Hold On*), axis limits, title, labels, legend, colour, height and font |

Every chart downloads as CSV (like the "file" option from there) and as PNG. The calculations are on
the server (`br.remoto.web.results`): `ResultMenu` builds the menus from the run's `catalogo`, and
`ResultViews` does what `processGraphic` did, from the recorded data. The EMG with the default
options is the engine's `emg-<muscle>`; with other options it is redone from the raw EMG, with the
noise of the same generator. Runs older than this screen only have force, spikes, torque and EMG.

Where the old code had plain mistakes, what the screen announced is what holds: the histogram of
tracts, afferents and interneurons and the frequency of the tracts could not find the neurons; the
"gInhi" conductance did not exist (it is "gInib"); the names of the tracts (*ExcINs*, *InhINs*)
pointed at the noises; the stimulus rate × force paired up lists that were out of order; and the
"Vs x Vdot" showed the injected current of each stage of the Runge-Kutta, a leftover of debugging —
here it is the phase plane of the soma, Vs against dVs/dt. The input resistance of the motoneurons
always uses both compartments (there, only in "All MNs").

## Next steps

1. **Port and clean up the models** into `remoto-core` (records, a `sealed interface` for the
   families of models), in phases, each one with outputs identical bit for bit to the original
   engine:
   1. ~~Typed configuration, with no HSQLDB at run time.~~ Done: `br.remoto.core.model.config`.
   2. The numerical core, **slice by slice**: each family of models goes into `remoto-core` and the
      original engine starts using it at once, instead of a new engine in parallel that would only
      be comparable at the end (the three public scenarios load the same 83 classes). At each
      slice, the golden master, 1 × 4 threads and 1 s of the three scenarios check bit for bit.
      Done: pulse ion channels (`br.remoto.core.model.channel`: `SodiumChannel` m³h and
      `GatedChannel` for fast K n⁴, slow K q² and persistent Ca p) and synapses
      (`br.remoto.core.model.synapse`: `SynapseGroup`, Lytton's aggregation, `Synapse` and
      `SynapticDynamics`); randomness (`br.remoto.core.model.random`: `Rng`, `Draws` with the
      Gaussian draw from 12 uniforms, the gamma and the uniform ones, and `Gradient`, the
      interpolation of a parameter along the pool); the twitch of the motor unit
      (`muscle.AlphaTwitch`, the alpha function as a 2nd-order IIR, used by SOCDS and by Hill); and
      the EMG band-pass (`emg.BandPassFilter`). The original filter zeroed the first two samples of
      the raw EMG it received; that is now explicit in `LegacyOutputs`. The
      `ButterworthBilinear`, built in the joint models and never used, left them. And the membrane
      of the spinal neurons (`br.remoto.core.model.neuron`: `MotoneuronMembrane`, two compartments
      with the PIC in the dendrite, and `InterneuronMembrane`, one compartment): potentials,
      channels, synapses, leaks and the Runge-Kutta. The `Motoneuron` and `Interneuron` classes of
      the original were left as a shell, with the identity, the spike trains and the axon
      (latencies and antidromic collision). Two asymmetries of the original were kept: the order of
      the sum of the currents and the refractory test (`t − last > refractory` in the motoneuron,
      `t > last + refractory` in the interneuron), which in floating point are not equivalent. The
      "VsxVdot" recording at every stage of the Runge-Kutta is gone, a leftover of debugging that
      stored the injected current and reached no output. The modulating signals
      (`br.remoto.core.model.signal.Modulation`: constant, sinusoid, AM, square, ramp, triangle,
      trapezoid, random, gamma and `custom`) and the spikes of the descending tracts and of the
      noises (`br.remoto.core.model.tract.TractSpikeGenerator`: Poisson, gamma of whole order and
      Gaussian intervals, with the mean modulated on the interval or on the rate) went too.
      `ModulatingSignal` remains as the object the editor fills in and saves, and computes the
      values through `Modulation`; `NeuralTract` was left as a shell. The particularities of the
      original were kept: the sinusoid loses 7 after 120 s, the threshold of the gamma process is
      drawn at every reset, the reset records in the train an initial spike that reaches no
      synapse, and the integral of the rate of the gamma process does not go back to zero. The
      `custom` shape no longer stores in the signal the amplitude it uses (12.35 and then 13.75);
      the computed value is the same. The signal's `isActive(t)` and the tract's `setSignal`, which
      nobody called, are gone. The injected current
      (`br.remoto.core.model.stimulus.InjectedCurrent`, the fixed amplitude plus the signal) and
      the nerve stimulation (`stimulus.NerveStimulation`: 1 ms pulses with the period modulated by
      the signal, which adds to the frequency, and the paired train) went too. `Current` and
      `Nerve` were left as a shell; from `Nerve` went the test fields and methods nothing used. The
      nerve's signal is assembled with the start, the end, the amplitude and the frequency of the
      stimulus itself, and the paired train always uses the nerve's frequency (that of the paired
      one is not copied from the configuration): both things are the original's and were kept. None
      of the three public scenarios turns on a nerve or a current; the equivalence was checked with
      variants of 17 that do.
   3. ~~Afferents, motor units, muscles, proprioceptors and joint.~~ Done, with the same checking
      and eight more variants of 17 that swap the models through the scenario's `settings` field
      (Hill, Raikova, *DistributionMoments*, pendulum, position task, Prochazka spindle, linear and
      pre-defined distribution of the units). The models are fixed by the `ConfigurationDAO`
      (SOCDS, isometric ankle, Mileusnic spindle, Crago GTO), but the editor swaps them through the
      `settings`; with SOCDS and the isometric ankle the spindle is not even integrated.
      - `br.remoto.core.model.neuron.AfferentFiber`: the Ia, II and Ib fibers (nerve stimulus,
        firing from the receptor in a gamma process, conduction). The reset draws the thresholds of
        the three types, and the II fiber starts drawing with the order of the Ia after the first
        spike, as in the original.
      - `muscle.MotorUnit` (twitch parameters, the state the muscles advance, the
        Hermite-Rodriguez MUAP of the EMG and the position in the cross-section),
        `CriticallyDampedMuscle` (SOCDS), `HillMuscle` and `Tendon`, `RaikovaMuscle` and
        `DistributionMomentsMuscle` (which never computed force: it stays at 0). Hill computes the
        four stages of the Runge-Kutta and advances by Euler, and creates at every step a derived
        generator it does not use; both were kept, because they change the force and the sequence
        of randomness.
      - `receptor.MileusnicSpindle` (bag1, bag2 and chain fibers, with the bodies copied from the
        original by script; the chain gains are never assigned and stay at 0), `ProchazkaSpindle`
        and `CragoTendonOrgan`.
      - `joint.IsometricAnkle`, `InvertedPendulumAnkle`, `PositionTaskAnkle` and
        `MusculotendonGeometry` (the polynomials of length and moment arm, copied by script). In the
        isometric ankle the knee arrives in radians and is compared with 0 and 90, so that MG and LG
        only have coefficients with the knee extended; in the pendulum and in the position task it
        is in degrees. The last two compute the torques of the Runge-Kutta stages with the angle at
        the start of the step and draw the disturbance at every stage.

      Gone, unused: `Vestibular`, the spindle simulator of the old servlets
      (`br.remoto.servlet`, `br.remoto.spindle_simulator`) and the `javax.servlet` dependency. The
      classes of the original that remain in those packages keep only the sampling of the signals.
   4. ~~The engine runs on `remoto-core` alone, the editor saves without a database and
      `remoto-legacy-model` ceases to exist.~~ Done. What was left of the original (the
      `Simulation`, the `NeuronUpdater`, the factories, the classes of each element, the
      `Configuration` and the VOs) went to `br.remoto.core.simulation`, in UTF-8 and without the
      commented-out and dead code (the writing of the `.sim`, the static registry of simulations,
      the configuration of the old results screen, the `BasicDAO` and the `User`). Of the
      `ConfigurationDAO`, the reading was kept as `ConfigurationAssembler` and the writing as
      `ConfigurationTables`; the export of the old database reads `remoto.script` as text. HSQLDB
      left the dependencies. Before taking the original code out, the three public scenarios with
      1 s, the 30 configurations of the database with 100 ms and 18 variants of 17 (the muscle,
      joint and spindle models, the distribution of the units, the nerve, the injected current and
      the signals and distributions of the tracts) gave outputs identical, bit for bit, to those of
      the original code; the editor's saves (without editing, editing every screen and reopening)
      gave the same tables in the 33 configurations; and the export without HSQLDB gave identical
      files in the 30. The tests that compared with the DAO through HSQLDB became golden masters
      generated by it. The UML diagrams of the original code are in `docs/uml-original/`.

      Afterwards, the `remoto-legacy` module, which had been left as an adaptation only, was
      dissolved: the engine (`LegacyEngine` and the conversion of the scenario) and the reading of
      the old database (`LegacyDatabase`) went to `br.remoto.core.engine.legacy`, and the editor to
      `br.remoto.web.editor`, its only user. The conversion of the scenario's `legacy` field
      stopped using Jackson, and `remoto-core` is still free of dependencies. The names recorded in
      the scenarios (`"engine": "legacy"`, the `remoto-legacy-db/1` format and the engine version)
      did not change, and the result is still the same, bit for bit.
2. ~~Performance of the original engine.~~ Done: see "Performance" in
   [Original engine](#original-engine-legacy). The next gain: taking the muscle force off the
   simulation's thread, separating what draws at random (spindle, disturbances) from what does not.
3. Parameter sweeps, Python client, NWB export.
4. ~~Licence of the project.~~ Done: MIT, in `LICENSE`.
5. ~~A results screen with everything the old site's had.~~ Done: see
   [Results screen](#results-screen).
6. ~~A single engine and more experiment scenarios.~~ Done: the provisional engine left, and
   `scenarios/` gained four experiments from the old database (14, 16, 18 and 19) and
   `modelo-completo`; 24 and 42 now have their tracts in Poisson. See
   [Public scenarios](#public-scenarios).
7. ~~No login, with export and import of scenarios.~~ Done: see
   [No login and no database](#no-login-and-no-database). Gone are the OIDC login (ORCID, GitHub),
   the test users, the profiles, account deletion and the sharing links (the exported file plays
   that part); and in came the limit of simulations per origin address and the ceiling of the
   queue.
8. ~~Order of the gamma process of the tracts.~~ Done: the editor stores the *Std* of the tracts in
   the `settings` (`tractStd`), and a gamma tract with an order fires; see
   [Scenario editor](#scenario-editor).
9. ~~More configurations from the old database.~~ Done: those that had gamma tracts (25 to 28, 32,
   34 to 36, 38, 39, 41 and 44) were published with Poisson, and the *Position Task* ones with the
   ankle in position task; see [Public scenarios](#public-scenarios).
10. ~~Data that survives restarts.~~ Done: the area stopped depending on the session (which lived
    only in the server's memory and, on stopping, erased the areas) and came to be identified by a
    30-day cookie; see [Areas and the cookie](#areas-and-the-cookie).
11. ~~A site in two languages.~~ Done: English and Portuguese in the application and in the
    presentation, from the browser's language and with a selector on every page; see
    [Languages](#languages).
12. **Gamma process orders of the original works.** If the orders used in the publications are
    recovered, the scenarios with gamma tracts can go back to gamma, with the `tractStd`.
13. **What the Fortran versions have and this one does not.**
    [projectFR](https://github.com/rnwatanabe/projectFR) (R. N. Watanabe, last commit in December
    2019) is a version of ReMoto in Fortran. The core of the models coincides with that of this
    version (the Hill muscle of *Virtual Muscle* with type I and II fibers, SOCDS with saturation,
    the Mileusnic spindle, the GTO of Lin and Crago, the Hermite-Rodriguez EMG with attenuation and
    widening, synapses with dynamics, gamma tracts, antidromic collision, interneurons, inverted
    pendulum); here there is, in addition, the active dendrite with the PIC (there it is passive),
    the Raikova and distribution-moments muscles, the Prochazka spindle and the position task.

    [ReMoto_AIDP](https://github.com/oliveiramarina/ReMoto_AIDP) (M. C. de Oliveira, with R. N.
    Watanabe and A. F. Kohn, last commit in September 2022) starts from projectFR and adapts it to
    the study of demyelinating diseases (Oliveira, Watanabe and Kohn, 2022, *Journal of Neural
    Engineering* 19:056022). It takes to Fortran precisely three things this version already had —
    the position task (`jointAnklePositionTask.f90`, with the same inertia of the foot, (4/3)·m·d²,
    as `PositionTaskAnkle.java`), the twitch of Raikova and Aladjov (2002) as an activation model
    and the fix of the force-velocity of the Hill muscle, in which projectFR had lost the
    multiplication of the numerator by the velocity and its unit — and adds two things that were
    not here: the conduction velocity of each axon drawn from a truncated Gaussian (here the
    distribution along the nucleus is the original's ramp, with an optional coefficient of
    variation) and the conduction block of a fraction of the motor nucleus. The force and position
    tasks from there, with the three subjects, are the `aidp-*` scenarios. What was missing here:
    - [x] **Imposed ankle angle**: the angle follows a given trajectory (a perturbation), and the
      length of the muscles and the spindles follow it (the *ImpedanceAnkle* example from there).
      Done: the *Imposed angle* joint model and the signal of the *Joint* screen; an example in the
      `estiramento-tornozelo` scenario.
    - [x] **Fusimotor drive varying over time**: the dynamic and static γ firing as signals, and
      not constants (the *PosturalControl* example from there varies them as a ramp). Done: the
      signals of the *Muscle Spindle* screen.
    - [x] **Nerve stimulus with a configurable pulse width** and a leaky-charge threshold (the
      intensity-duration curve), as an option; the default is still the original's (a 1 ms pulse, a
      current threshold). Done: the new part of the *Nerve Stimulation* screen.
    - [x] **Configurable gamma order of the afferents** per type (Ia, Ib, II) and muscle; here it
      was fixed in the code (25, 30 and 400). Done: the new part of the sensory fibers screen.
    - [x] **Axon with nodes of Ranvier** for motoneurons and afferents: internodes and nodes with
      fast and persistent sodium, fast and slow potassium, h current and an ionic pump; here the
      axon was a conduction delay with a threshold. Done at the stimulus point, with Bostock's
      model and the parameters of Howells et al. (2012): the *Node of Ranvier* threshold option, on
      the *Nerve Stimulation* screen. Conduction is still a delay (there, a chain of nodes can
      replace it), and the spikes from the soma and from the receptor do not pass through the node.
    - [x] **Connections that weaken with distance** in any pathway (the weight
      `dec / (dec + distance²)`); here it was only between motoneurons and Renshaw cells. Done: the
      *Distance decay* column of the *Synaptic Parameters* screen, with positions for afferents and
      tracts.
    - [x] **Batch protocols**: sweeping a parameter over several simulations, such as the
      recruitment curve of the H reflex and the M wave (stimulus intensity) and force variability
      (descending command). Done on the command line: `remoto sweep`, with the two ready protocols
      and arbitrary batches (see [Batch protocols](#batch-protocols)); the site does not have them
      yet.
    - [x] **Tract command as an arbitrary rate series**, besides the ready-made shapes (sinusoid,
      AM, ramp...). Done: the *series* modulation of the *Descending Commands* screen.
    - [x] **Conduction block** of a fraction of the motor axons of the nucleus, counting from the
      slowest (ReMoto_AIDP sets the velocity of the blocked ones to zero, and subject S2 of the
      tasks blocks 65%). Done: the *Conduction block* part of the motoneuron screen
      (`axonConductionBlock`), with the `aidp-force-task-s2` and `aidp-position-task-s2` scenarios.

    Those that change the model live in the `settings` (see *Fields the original DAO did not store*,
    in the Editor), and without them the simulations are the same as before. Two are missing:
    - [ ] **Integrate-and-fire motoneuron** (without ion channels), for fast simulations.
    - [ ] **Conduction velocity drawn from a truncated Gaussian** per type of unit, as in
      ReMoto_AIDP. The `axonVelocitySTD` of the *Miscellaneous* screen already puts Gaussian noise
      on the ramp, but it is a single coefficient of variation for the motor and the sensory axons,
      and ReMoto_AIDP uses a mean and a deviation of its own for each nucleus and does not
      demyelinate the sensory ones.

    projectFR and ReMoto_AIDP are GPL-3 and this version is MIT: the items must be implemented from
    the papers and the equations, without copying the code, or with the licence agreed with the
    authors.
