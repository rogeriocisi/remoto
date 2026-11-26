<img src="remoto-web/src/main/resources/static/img/remoto-icon.svg" alt="" width="64" align="left">

# ReMoto

**Website:** <https://rogeriocisi.github.io/remoto/>

[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.24488862.svg)](https://doi.org/10.5281/zenodo.24488862)

ReMoto is a neuronal simulation system for studying the spinal cord networks responsible for muscle control. These networks are driven by descending commands, afferent feedback and electrical nerve stimulation. The simulator can be used to investigate phenomena at several levels of organization, from the neuronal membrane to whole-muscle behavior such as force generation. This is possible because each element (neurons, synapses, muscle fibers) has its own mathematical model, usually involving voltage- or neurotransmitter-dependent ionic channels.

ReMoto is useful for interpreting results of neurophysiological experiments in humans, proposing and testing hypotheses about neuronal dynamics and network processing, validating experimental protocols, and teaching neurophysiology.

The system includes motoneurons, muscle fibers (electrical activity and force generation), Renshaw cells, Ia reciprocal, Ib and group II interneurons, Ia, II and Ib afferents with muscle spindles and Golgi tendon organs, descending tracts and the ankle joint. Neurons are connected by chemical synapses, which can show depression or facilitation. The simulated nuclei control flexion and extension of the human ankle: Medial Gastrocnemius (MG), Lateral Gastrocnemius (LG), Soleus (SOL) and Tibialis Anterior (TA).

ReMoto was developed over more than a decade at the Biomedical Engineering Laboratory (LEB) of the University of São Paulo, by the authors of the papers listed in [How to cite](#how-to-cite).

## Repository status

This repository contains the new version of ReMoto. Of the legacy version, only the parts the new one still uses are kept; the rest (the Struts/JSP site, applets and classes outside the build) was removed and remains in the Git history and in the parent repository, [rnwatanabe/remoto](https://github.com/rnwatanabe/remoto).

| | Legacy version | New version |
|---|---|---|
| Location | [rnwatanabe/remoto](https://github.com/rnwatanabe/remoto); here only `legacy-db/` (the original database, as a text file) | `remoto-*/` Maven modules, `scenarios/` |
| Stack | Java 8, Struts 1, JSP, Java applets, Apache Tomcat 7, HSQLDB | Java 25, Spring Boot 4, Thymeleaf + htmx + uPlot, file storage (no database), a single executable JAR (no Docker) |
| Simulation models | All published models | The same published models, ported to `remoto-core` with bit-for-bit identical results (`legacy` engine), plus the features of the Fortran versions as options, off by default |
| Users | Local accounts stored in HSQLDB | No login: each browser has its own area, kept by a 30-day cookie; scenarios are exported and imported as files |
| Language | English only | English and Portuguese, chosen by the browser language and switchable on every page; the simulator serves any language declared in `remoto.languages`, from the same pages |
| Status | Unmaintained; depends on software without security updates | Maintained and tested; the original models give bit-for-bit identical results, checked against the original code and database |

### Repository layout

```
remoto-core/          scenario format, the ReMoto models and engine ("legacy"), reader of the old database (plain Java)
remoto-storage/       file-based persistence (JSON scenarios, CSV.gz results)
remoto-web/           web application (Spring Boot, no login), including the scenario editor
remoto-cli/           command-line runner for scripts and clusters, and export of old configurations
scenarios/            public scenarios (JSON)
deploy/            systemd service, configuration template and the reverse-proxy examples (nginx, Caddy)
docs/              project website (`/`, `/pt/`, `/guide/`, `/guide/pt/`), served from the root of the domain by the application itself and mirrored on GitHub Pages, and documentation of the new version (in Portuguese)
legacy-db/         original HSQLDB database (text script), read by export-legacy without HSQLDB
ref.bib            BibTeX references
```

## New version

Detailed documentation, including the REST API and the data layout, is in [docs/current-version.md](docs/current-version.md).

### Features

- **No login, no database:** each browser gets its own area for scenarios, runs and results, identified by a 30-day cookie, so the work survives server restarts and closing the browser; an area is deleted after 30 days without use. To keep your work longer, or take it to another browser or computer, export scenarios as JSON (one file each, or all in a ZIP) and import them there; runs can be downloaded as a ZIP with every result in CSV. No personal data is stored.
- **In two languages:** the pages, the messages and the charts come in English or Portuguese, picked from the browser language (`Accept-Language`) and switchable from the header on any page, which is remembered for a year. The editor screens and the results menus keep the English labels of the original site, the ones the papers cite.
- **Fair simulation queue:** several people can run simulations at the same time; a large batch from one browser does not block the others, and limits per address and for the whole queue keep one visitor from taking over the server.
- **The original models:** the public scenarios of the old site ("Default - Soleus contraction", "ISB_2013 - Stretch Reflex", "JN - Watanabe and Kohn (2015)") run the same numerical code as before. Sixteen more experiments from the old database (isometric contraction, recruitment, H-reflex with reciprocal inhibition, nerve stimulation during a contraction, position tasks with Ia, II and Ib afferents, oscillatory commands, Renshaw inhibition), a scenario with every model element turned on and an ankle stretch reflex with an imposed joint angle are also public, as are sixteen scenarios converted from the examples of the Fortran version (`scenarios/projectfr-*.json`: the design of each experiment, with the model parameters of the Java ReMoto) and six converted from the force and position tasks of [ReMoto_AIDP](https://github.com/oliveiramarina/ReMoto_AIDP), the Fortran version for demyelinating diseases (`scenarios/aidp-*.json`: the healthy subject S0 and the demyelinated S1 and S2 of each task, which differ in the conduction velocities of the motor axons and, in S2, in a conduction block). Tests check that each converted scenario gives exactly the same results as the original database configuration. Results include muscle force, motoneuron spikes, joint torque and EMG (raw, and filtered with noise as on the old results page).
- **Scenario editor modeled on the original site:** copy a public scenario and edit it through the same menu, screens, fields and limits as the old editor: structure (descending commands, neural elements), neural parameters (synapses, kinetics, synaptic dynamics, conductances, motoneurons, interneurons, sensory fibers), proprioceptors (muscle spindle, GTO), biomechanics (joint and muscle models, musculotendon, muscle units, EMG, miscellaneous), stimulation (nerve, injected current) and run settings. Changes are saved exactly as the original `ConfigurationDAO` saved them, now without a database; settings the old DAO did not store (model choices, spindle gains, joint angles) are kept with the scenario.
- **Results screen modeled on the original site:** the same menus and graphics as the old results page: muscle, tendon, spindle, GTO and joint signals, spike rasters, instantaneous firing rate, interspike intervals and histograms for motoneurons, afferents, tracts and interneurons, membrane potentials, conductances and injected current of the stored neurons, neuron and motor-unit properties, EMG with the old filters and options, statistics and FFT, multiple subplots, and CSV/PNG downloads.
- **Features from the Fortran versions:** the Fortran version of ReMoto ([projectFR](https://github.com/rnwatanabe/projectFR), by R. N. Watanabe) had features the Java code did not. They are now here, implemented from the papers and off by default, so that scenarios without them give the original results: an imposed ankle angle (a perturbation), a time-varying fusimotor drive, nerve stimulation with a chosen pulse width and a current, leaky-charge or node-of-Ranvier threshold (the human axon model of Bostock, with the parameters of Howells et al., 2012), the gamma order of afferent firing, synaptic weights that fall with distance in any pathway, and tract commands that follow any rate series. Only the integrate-and-fire motoneuron was left out. From [ReMoto_AIDP](https://github.com/oliveiramarina/ReMoto_AIDP), the Fortran version adapted to demyelinating diseases (M. C. de Oliveira, R. N. Watanabe and A. F. Kohn, 2022), came the conduction block of a fraction of a motor nucleus, counted from the slowest axon: the blocked motoneurons still fire at the soma, but no spike reaches the end-plate.
- **Batch protocols:** `remoto sweep` runs a scenario once for each value of a parameter and writes the measures of every run to a table, with ready protocols for the H-reflex and M-wave recruitment curve and for force variability (see [Command line](#command-line)).
- **The papers:** the journal papers, conference proceedings and book chapter from the old site are listed, each with its DOI, in the publications section of the project site (`docs/`), which is where the documentation lives; the simulator links to it.
- **Reproducible runs:** every run stores the exact scenario, the random seed and the engine version; the same seed always gives the same results.
- **One server for both halves:** the descriptive site and the simulator are served by the same embedded Tomcat that starts with the application, on port 8081 by default (`server.port`, `SERVER_PORT`): the pages of `docs/` at the root — <http://localhost:8081/>, folder `remoto.site-dir` — and the simulator under `/simulator` — <http://localhost:8081/simulator/>. The path decides which of the two answers, so the links between them work the same in development and on the server, with nothing else running: no static server beside it and no proxy in front to stitch the two together (in production the reverse proxy only adds TLS and compression, and passes the whole domain through).
- Live progress, interactive charts, CSV and PNG downloads and a REST API.

### Requirements

JDK 25. Maven is downloaded automatically by the Maven Wrapper (`./mvnw`, or `mvnw.cmd` on Windows).

### Build and test

```bash
./mvnw verify
```

### Run locally

Use the start script for your shell. It finds a JDK 25 (from `JAVA_HOME`, the `PATH` or the usual install folders), so it works even if your default Java is older. What comes up is one process — the application, with its embedded Tomcat — serving both halves of the system on one port: the simulator under `/simulator` and the descriptive site at the root (see below). An optional argument sets the port (default 8081, the same port the service listens on in production, because 8080 is often already taken by something else).

| Shell | Command |
|---|---|
| Windows (cmd) | `scripts\start.cmd` or `scripts\start.cmd 9090` |
| Windows (PowerShell) | `.\scripts\start.ps1` or `.\scripts\start.ps1 -Port 9090` |
| Linux, macOS, Git Bash | `scripts/start.sh` or `scripts/start.sh 9090` |

If PowerShell refuses to run scripts, use `powershell -ExecutionPolicy Bypass -File .\scripts\start.ps1`, or the `.cmd` version.

Without the scripts, the equivalent command is below. Keep the quotes, because PowerShell otherwise splits the argument at the dots, and point `JAVA_HOME` to a JDK 25 first if needed:

```bash
./mvnw -pl remoto-web -am spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Then open http://localhost:8081/simulator/ (or http://localhost:8081/simulator/pt/ for Portuguese), and http://localhost:8081/ for the site. The port comes from `application.yml` (`server.port`, overridable by `SERVER_PORT`), so it is 8081 with or without the start scripts. There is no login. The simulator lives under `/simulator` in development too, because that is where it is served in production, with the descriptive site at the root of the domain.

#### The site and the simulator together

Both sides are one domain, and the links between them are relative to it: `/pt/` in the simulator's menu, `simulator/` in the pages of the site. The same Tomcat serves both, so that holds in development as it does in production: the path decides who answers, with `/simulator/**` going to the Spring application and everything else to the pages of [docs/](docs/), served from a second context by Tomcat's own file servlet (`SiteConfig`). Nothing else runs: no static server beside it, and no proxy in front of it to stitch the two together.

The folder is `remoto.site-dir` (`REMOTO_SITE_DIR`), `./docs` by default, which is where it is in the repository; in production it is the copy the deployment mirrors. If the folder is not there, the root answers nothing and the simulator comes up alone, which is what happens when the JAR runs by itself.

### Command line

```bash
./mvnw -pl remoto-cli -am package -DskipTests
java -jar remoto-cli/target/remoto-cli-4.0.0-SNAPSHOT.jar run scenarios/remoto-17-default-soleus-contraction.json --duration-ms 500 --threads 4 -o results/soleus
java -jar remoto-cli/target/remoto-cli-4.0.0-SNAPSHOT.jar sweep scenarios/remoto-17-default-soleus-contraction.json --protocol hm --range 5:40:1 -o results/hm-curve
java -jar remoto-cli/target/remoto-cli-4.0.0-SNAPSHOT.jar validate scenarios/*.json
java -jar remoto-cli/target/remoto-cli-4.0.0-SNAPSHOT.jar export-legacy --db legacy-db/remoto --list
```

`sweep` runs a scenario once for each value of a parameter and writes one row of measures per run to `sweep.csv`: `--protocol hm` is the H-reflex and M-wave recruitment curve (stimulus intensity), `--protocol force` the force variability (ISI mean of a descending tract), and `--param` with `--measure` any other batch (see `sweep --help`). `export-legacy` converts configurations from the old HSQLDB database file into JSON scenarios (it reads the file directly; HSQLDB is not needed). `--threads` sets how many threads update the neurons (default: CPU cores); the results are identical, bit for bit, with any value.

### Deployment

The site and the simulator are one system on one domain, and one service serves both: a single executable JAR with an embedded web server, installed as a systemd service, which answers at `/simulator` with the simulator and at the root with the descriptive pages of [docs/](docs/), read from the folder the deployment mirrors (`REMOTO_SITE_DIR`). The reverse proxy in front (nginx or Caddy) does the TLS and the compression and passes the whole domain through. There are no accounts to manage: no login, passwords or identity providers.

| Address | What |
|---|---|
| `/`, `/pt/` | the project description, in English and Portuguese |
| `/guide/`, `/guide/pt/` | the quick guide |
| `/simulator/`, `/simulator/pt/` | the simulator itself |

The pages redirect to the reader's language on the first visit, and the language selector of every page keeps the choice.

**The deployment domain is not in this repository.** Addresses inside the pages are relative, the contact e-mail is built from the host the page is served from — `remoto@<domain>`, see [docs/assets/site.js](docs/assets/site.js) — and where to deploy comes from this repository's GitHub settings, below.

#### Every push to `master` deploys

A push to `master` that passes the tests deploys the whole system. The `deploy` job of [.github/workflows/ci.yml](.github/workflows/ci.yml) builds the JAR, keeps it as a workflow artifact (so a rollback needs no rebuild), mirrors `docs/` and `scenarios/` to the server with `rsync --delete`, installs the JAR, restarts the service and waits for its health endpoint to answer. It can also be started by hand, with "Run workflow" on the Actions tab.

Two things worth knowing: anyone who can push to `master` can deploy, so protect the branch if that is not what you want; and the restart cancels running simulations and marks queued ones as interrupted, while stored scenarios and finished simulations are kept. Until the secrets below exist, the job deploys nothing and does not fail the build, so forks and clones keep working.

#### Secrets (Settings → Secrets and variables → Actions)

The address is a secret rather than a variable so that it stays masked in the public run logs. The user is not: it is a common word, and masking it would hide `deploy` from the whole log, step names and error messages included.

| Secret | What |
|---|---|
| `DEPLOY_HOST` | the server, by name or address |
| `DEPLOY_SSH_KEY` | that user's private key, whole, **with no passphrase** (the workflow has no way to type one) — `ssh-keygen -t ed25519 -f deploy_key -N ""`, with the public half in its `authorized_keys` |
| `DEPLOY_KNOWN_HOSTS` | the server's host key, so the connection is verified — `ssh-keyscan your-server` |

#### Variables (same page, Variables tab), all optional

| Variable | Default |
|---|---|
| `DEPLOY_USER` | `deploy` |
| `DEPLOY_PORT` | `22` |
| `DEPLOY_SITE_DIR` | `/var/www/remoto` |
| `DEPLOY_SCENARIOS_DIR` | `/opt/remoto/scenarios` |
| `DEPLOY_JAR_PATH` | `/opt/remoto/remoto-web.jar` |
| `DEPLOY_SERVICE` | `remoto` |
| `DEPLOY_HEALTH_URL` | `http://127.0.0.1:8081/simulator/actuator/health` — the port is the `SERVER_PORT` of `remoto.env` (8081 by default), so set this variable if the service does not listen on 8081 |
| `SITE_URL` | empty — the public address of the site, such as `https://example.org/`. Link previews (`og:image`, `og:url`) need an absolute address, so the deployment adds them to the pages only when this is set; the GitHub Pages mirror also uses it to send the *Simulator* buttons to the server, since the mirror has only the static pages |

The `pages` job publishes the same `docs/` to GitHub Pages; for it to run, *Settings → Pages → Source* must be **GitHub Actions**.

#### On the server, once

```bash
# the deploy user, and rsync, which the deployment needs on both ends
sudo useradd --system --create-home --shell /bin/bash deploy
sudo apt install rsync
sudo install -d -o deploy -g deploy -m 755 /var/www/remoto /opt/remoto /opt/remoto/scenarios

# the public half of DEPLOY_SSH_KEY
sudo install -d -o deploy -g deploy -m 700 ~deploy/.ssh
sudo -u deploy tee ~deploy/.ssh/authorized_keys < deploy_key.pub

# restarting the service is the only thing the deploy user may do as root
echo 'deploy ALL=(root) NOPASSWD: /usr/bin/systemctl restart remoto' \
  | sudo install -m 440 /dev/stdin /etc/sudoers.d/remoto-deploy
```

The files it writes are read by the `remoto` user that runs the service: the JAR and the public scenarios under `/opt/remoto` (mode 644) and the pages of the site under `/var/www/remoto`. The service itself — the `remoto` user, `/etc/remoto/remoto.env`, the systemd unit — and the reverse proxy are set up once, as described in the "Installing on the server" section of [docs/current-version.md](docs/current-version.md), with [deploy/nginx-remoto.conf.example](deploy/nginx-remoto.conf.example) or [deploy/Caddyfile.example](deploy/Caddyfile.example) for the proxy.

#### Adding a language

A complete `messages_<tag>.properties` in `remoto-web/src/main/resources`, and the tag in `remoto.languages` (`REMOTO_LANGUAGES=en,pt,es`). The simulator's pages are the same in every language — only the texts change — and its entry points follow the list: `/simulator/`, `/simulator/pt/`, `/simulator/es/`. `MessagesTest` fails while a key is missing from any of the files. The descriptive pages in `docs/` are one document per language, so a new language there means a new document (`/es/`, `/guide/es/`).

### Roadmap

1. ~~Run the original models in the new platform, with reference outputs (golden master).~~ Done.
2. ~~Convert the old configurations into JSON scenarios.~~ Done.
3. ~~Scenario editor with all the screens of the original site.~~ Done.
4. ~~Export EMG from the original models.~~ Done.
5. ~~Port the models to `remoto-core`, checked against the reference outputs, and drop the original model code and HSQLDB.~~ Done.
6. ~~Results screen with everything the old one had.~~ Done.
7. ~~English and Portuguese, in the application and on the project site.~~ Done.
8. ~~The features of the Fortran versions (projectFR and ReMoto_AIDP), and parameter sweeps on the command line.~~ Done, except the integrate-and-fire motoneuron.
9. Parameter sweeps in the web application, a Python client and NWB export.

## Legacy version

> [!WARNING]
> The legacy web application is no longer in this repository. It depends on software that no longer receives security updates (Struts 1, Tomcat 7) and on Java applets, which no current browser runs. To run it for reference, use the parent repository, [rnwatanabe/remoto](https://github.com/rnwatanabe/remoto).

## License

ReMoto is released under the [MIT License](LICENSE).

## Support

To report problems or ask questions, open an issue in this repository.

## How to cite

To cite the software itself, use its DOI: [10.5281/zenodo.24488862](https://doi.org/10.5281/zenodo.24488862). That is the concept
DOI, which always resolves to the latest version; each release also gets its own DOI.

If you use ReMoto, please cite the following papers:

- CISI, R. R. L.; KOHN, A. F. Simulation system of spinal cord motor nuclei and associated nerves and muscles, in a Web-based architecture. *Journal of Computational Neuroscience*, v. 25, n. 3, p. 520–542, 2008.
- ELIAS, L. A.; CHAUD, V. M.; KOHN, A. F. Models of passive and active dendrite motoneuron pools and their differences in muscle force control. *Journal of Computational Neuroscience*, v. 33, n. 3, p. 515–531, 2012.
- ELIAS, L. A.; KOHN, A. F. Individual and collective properties of computationally efficient motoneuron models of types S and F with active dendrites. *Neurocomputing*, v. 99, p. 521–533, 2013.
- WATANABE, R. N. et al. Influences of premotoneuronal command statistics on the scaling of motor output variability during isometric plantarflexion. *Journal of Neurophysiology*, v. 110, n. 11, p. 2592–2606, 2013.
- ELIAS, L. A.; WATANABE, R. N.; KOHN, A. F. Spinal mechanisms may provide a combination of intermittent and continuous control of human posture: predictions from a biologically based neuromusculoskeletal model. *PLoS Computational Biology*, v. 10, n. 11, p. e1003944, 2014.
- WATANABE, R. N.; KOHN, A. F. Fast oscillatory commands from the motor cortex can be decoded by the spinal cord for force control. *Journal of Neuroscience*, v. 35, n. 40, p. 13687–13697, 2015.
- OLIVEIRA, M. C.; WATANABE, R. N.; KOHN, A. F. Electrophysiological and functional signs of Guillain–Barré syndrome predicted by a multiscale neuromuscular computational model. *Journal of Neural Engineering*, v. 19, n. 5, p. 056022, 2022.

The BibTeX entries are in [ref.bib](ref.bib) and below:

```bibtex
@article{Cisi2008,
  author = {Cisi, R R L and Kohn, A F},
  title = {Simulation system of spinal cord motor nuclei and associated nerves and muscles, in a Web-based architecture},
  journal = {Journal of Computational Neuroscience},
  volume = {25},
  number = {3},
  pages = {520--542},
  year = {2008}
}

@article{Elias2012,
  author = {Elias, Leonardo Abdala and Chaud, Vitor Martins and Kohn, Andr{\'{e}} F{\'{a}}bio},
  title = {Models of passive and active dendrite motoneuron pools and their differences in muscle force control},
  journal = {Journal of Computational Neuroscience},
  volume = {33},
  number = {3},
  pages = {515--531},
  year = {2012}
}

@article{Elias2013,
  author = {Elias, Leonardo Abdala and Kohn, Andr{\'{e}} Fabio},
  title = {Individual and collective properties of computationally efficient motoneuron models of types S and F with active dendrites},
  journal = {Neurocomputing},
  volume = {99},
  pages = {521--533},
  year = {2013}
}

@article{Watanabe2013,
  author = {Watanabe, Renato Naville and Magalh{\~{a}}es, Fernando Henrique and Elias, Leonardo Abdala and Chaud, Vitor Martins and Mello, Emanuele Moraes and Kohn, Andre F},
  title = {Influences of premotoneuronal command statistics on the scaling of motor output variability during isometric plantarflexion},
  journal = {Journal of Neurophysiology},
  volume = {110},
  number = {11},
  pages = {2592--2606},
  year = {2013}
}

@article{Elias2014,
  author = {Elias, Leonardo Abdala and Watanabe, Renato Naville and Kohn, Andr{\'{e}} Fabio},
  title = {Spinal mechanisms may provide a combination of intermittent and continuous control of human posture: predictions from a biologically based neuromusculoskeletal model},
  journal = {PLoS Computational Biology},
  volume = {10},
  number = {11},
  pages = {e1003944},
  year = {2014}
}

@article{Watanabe2015,
  author = {Watanabe, R N and Kohn, A F},
  title = {Fast Oscillatory Commands from the Motor Cortex Can Be Decoded by the Spinal Cord for Force Control},
  journal = {Journal of Neuroscience},
  volume = {35},
  number = {40},
  pages = {13687--13697},
  year = {2015}
}

@article{Oliveira2022,
  author = {Oliveira, Marina Cardoso de and Watanabe, Renato Naville and Kohn, Andr{\'{e}} Fabio},
  title = {Electrophysiological and functional signs of Guillain-Barr{\'{e}} syndrome predicted by a multiscale neuromuscular computational model},
  journal = {Journal of Neural Engineering},
  volume = {19},
  number = {5},
  pages = {056022},
  doi = {10.1088/1741-2552/ac91f8},
  year = {2022}
}
```
