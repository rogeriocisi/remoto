# ReMoto

ReMoto is a neuronal simulation system for studying the spinal cord networks responsible for muscle control. These networks are driven by descending commands, afferent feedback and electrical nerve stimulation. The simulator can be used to investigate phenomena at several levels of organization, from the neuronal membrane to whole-muscle behavior such as force generation. This is possible because each element (neurons, synapses, muscle fibers) has its own mathematical model, usually involving voltage- or neurotransmitter-dependent ionic channels.

ReMoto is useful for interpreting results of neurophysiological experiments in humans, proposing and testing hypotheses about neuronal dynamics and network processing, validating experimental protocols, and teaching neurophysiology.

The system includes motoneurons, muscle fibers (electrical activity and force generation), Renshaw cells, Ia and Ib inhibitory interneurons, and Ia and Ib afferents. Neurons are connected by chemical synapses, which can show depression or facilitation. The simulated nuclei control flexion and extension of the human ankle: Medial Gastrocnemius (MG), Lateral Gastrocnemius (LG), Soleus (SOL) and Tibialis Anterior (TA).

ReMoto was developed over more than a decade at the Biomedical Engineering Laboratory (LEB) of the University of São Paulo, by the authors of the papers listed in [How to cite](#how-to-cite).

## Repository status

This repository contains the new version of ReMoto. Of the legacy version, only the parts the new one still uses are kept; the rest (the Struts/JSP site, applets and classes outside the build) was removed and remains in the Git history and in the parent repository, [rnwatanabe/remoto](https://github.com/rnwatanabe/remoto).

| | Legacy version | New version |
|---|---|---|
| Location | [rnwatanabe/remoto](https://github.com/rnwatanabe/remoto); here only `legacy-db/` (the original database, as a text file) | `remoto-*/` Maven modules, `scenarios/` |
| Stack | Java 8, Struts 1, JSP, Java applets, Apache Tomcat 7, HSQLDB | Java 25, Spring Boot 4, Thymeleaf + htmx + uPlot, file storage (no database), a single executable JAR (no Docker) |
| Simulation models | All published models | The same published models, ported to `remoto-core` with bit-for-bit identical results (`legacy` engine), plus a small demo engine |
| Users | Local accounts stored in HSQLDB | Login with ORCID or GitHub (OIDC), plus guest access |
| Status | Unmaintained; depends on software without security updates | Maintained and tested; the original models give bit-for-bit identical results, checked against the original code and database |

### Repository layout

```
remoto-core/          scenario format, the ReMoto models and engines ("legacy" and demo), reader of the old database (plain Java)
remoto-storage/       file-based persistence (JSON scenarios, CSV.gz results)
remoto-web/           multi-user web application (Spring Boot), including the scenario editor
remoto-cli/           command-line runner for scripts and clusters, and export of old configurations
scenarios/            public scenarios (JSON)
deploy/            systemd service and configuration template
docs/              documentation of the new version (in Portuguese)
legacy-db/         original HSQLDB database (text script), read by export-legacy without HSQLDB
ref.bib            BibTeX references
```

## New version

Detailed documentation, including the REST API and the data layout, is in [docs/nova-versao.md](docs/nova-versao.md) (in Portuguese).

### Features

- **Multi-user without a database:** each user's scenarios, runs and results are kept in their own folder. Guests get a temporary area that is deleted when the session ends.
- **Fair simulation queue:** several users can run simulations at the same time; a large batch from one user does not block the others.
- **The original models:** the public scenarios of the old site ("Default - Soleus contraction", "ISB_2013 - Stretch Reflex", "JN - Watanabe and Kohn (2015)") run the same numerical code as before. Tests check that each converted scenario gives exactly the same results as the original database configuration. Results include muscle force, motoneuron spikes, joint torque and EMG (raw, and filtered with noise as on the old results page).
- **Scenario editor modeled on the original site:** copy a public scenario and edit it through the same menu, screens, fields and limits as the old editor: structure (descending commands, neural elements), neural parameters (synapses, kinetics, synaptic dynamics, conductances, motoneurons, interneurons, sensory fibers), proprioceptors (muscle spindle, GTO), biomechanics (joint and muscle models, musculotendon, muscle units, EMG, miscellaneous), stimulation (nerve, injected current) and run settings. Changes are saved exactly as the original `ConfigurationDAO` saved them, now without a database; settings the old DAO did not store (model choices, spindle gains, joint angles) are kept with the scenario.
- **Results screen modeled on the original site:** the same menus and graphics as the old results page: muscle, tendon, spindle, GTO and joint signals, spike rasters, instantaneous firing rate, interspike intervals and histograms for motoneurons, afferents, tracts and interneurons, membrane potentials, conductances and injected current of the stored neurons, neuron and motor-unit properties, EMG with the old filters and options, statistics and FFT, multiple subplots, and CSV/PNG downloads.
- **Reproducible runs:** every run stores the exact scenario, the random seed and the engine version; the same seed always gives the same results.
- Live progress, interactive charts, CSV and PNG downloads, a REST API and immutable share links.
- Users can export or delete all of their data.

### Requirements

JDK 25. Maven is downloaded automatically by the Maven Wrapper (`./mvnw`, or `mvnw.cmd` on Windows).

### Build and test

```bash
./mvnw verify
```

### Run locally

Use the start script for your shell. It finds a JDK 25 (from `JAVA_HOME`, the `PATH` or the usual install folders), so it works even if your default Java is older. An optional argument sets the port (default 8080).

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

Then open http://localhost:8080. You can use the site as a guest. The `dev` profile also enables test accounts, defined in `remoto-web/src/main/resources/application-dev.yml`; use them only for local development.

### Command line

```bash
./mvnw -pl remoto-cli -am package -DskipTests
java -jar remoto-cli/target/remoto-cli-3.0.0-SNAPSHOT.jar run scenarios/exemplo-sol-mg.json -o results/example
java -jar remoto-cli/target/remoto-cli-3.0.0-SNAPSHOT.jar run scenarios/remoto-17-default-soleus-contraction.json --duration-ms 500 --threads 4 -o results/soleus
java -jar remoto-cli/target/remoto-cli-3.0.0-SNAPSHOT.jar validate scenarios/*.json
java -jar remoto-cli/target/remoto-cli-3.0.0-SNAPSHOT.jar export-legacy --db legacy-db/remoto --list
```

`export-legacy` converts configurations from the old HSQLDB database file into JSON scenarios (it reads the file directly; HSQLDB is not needed). `--threads` sets how many threads update the neurons (default: CPU cores); the results are identical, bit for bit, with any value.

### Deployment

The web application is a single executable JAR with an embedded web server, installed as a systemd service on Linux. Login uses OIDC providers (ORCID, GitHub), so ReMoto stores no passwords. The service file and the configuration template are in [deploy/](deploy/); the step-by-step guide is in the "Instalação no servidor" section of [docs/nova-versao.md](docs/nova-versao.md).

### Roadmap

1. ~~Run the original models in the new platform, with reference outputs (golden master).~~ Done.
2. ~~Convert the old configurations into JSON scenarios.~~ Done.
3. ~~Scenario editor with all the screens of the original site.~~ Done.
4. ~~Export EMG from the original models.~~ Done.
5. ~~Port the models to `remoto-core`, checked against the reference outputs, and drop the original model code and HSQLDB.~~ Done.
6. ~~Results screen with everything the old one had.~~ Done.
7. Parameter sweeps, a Python client and NWB export.

## Legacy version

> [!WARNING]
> The legacy web application is no longer in this repository. It depends on software that no longer receives security updates (Struts 1, Tomcat 7) and on Java applets, which no current browser runs. To run it for reference, use the parent repository, [rnwatanabe/remoto](https://github.com/rnwatanabe/remoto).

## License

ReMoto is released under the [MIT License](LICENSE).

## Support

To report problems or ask questions, open an issue in this repository.

## How to cite

If you use ReMoto, please cite the following papers:

- CISI, R. R. L.; KOHN, A. F. Simulation system of spinal cord motor nuclei and associated nerves and muscles, in a Web-based architecture. *Journal of Computational Neuroscience*, v. 25, n. 3, p. 520–542, 2008.
- ELIAS, L. A.; CHAUD, V. M.; KOHN, A. F. Models of passive and active dendrite motoneuron pools and their differences in muscle force control. *Journal of Computational Neuroscience*, v. 33, n. 3, p. 515–531, 2012.
- ELIAS, L. A.; KOHN, A. F. Individual and collective properties of computationally efficient motoneuron models of types S and F with active dendrites. *Neurocomputing*, v. 99, p. 521–533, 2013.
- WATANABE, R. N. et al. Influences of premotoneuronal command statistics on the scaling of motor output variability during isometric plantarflexion. *Journal of Neurophysiology*, v. 110, n. 11, p. 2592–2606, 2013.
- ELIAS, L. A.; WATANABE, R. N.; KOHN, A. F. Spinal mechanisms may provide a combination of intermittent and continuous control of human posture: predictions from a biologically based neuromusculoskeletal model. *PLoS Computational Biology*, v. 10, n. 11, p. e1003944, 2014.
- WATANABE, R. N.; KOHN, A. F. Fast oscillatory commands from the motor cortex can be decoded by the spinal cord for force control. *Journal of Neuroscience*, v. 35, n. 40, p. 13687–13697, 2015.

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
```
