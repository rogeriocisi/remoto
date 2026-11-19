# ReMoto

ReMoto is a neuronal simulation system for studying the spinal cord networks responsible for muscle control. These networks are driven by descending commands, afferent feedback and electrical nerve stimulation. The simulator can be used to investigate phenomena at several levels of organization, from the neuronal membrane to whole-muscle behavior such as force generation. This is possible because each element (neurons, synapses, muscle fibers) has its own mathematical model, usually involving voltage- or neurotransmitter-dependent ionic channels.

ReMoto is useful for interpreting results of neurophysiological experiments in humans, proposing and testing hypotheses about neuronal dynamics and network processing, validating experimental protocols, and teaching neurophysiology.

The system includes motoneurons, muscle fibers (electrical activity and force generation), Renshaw cells, Ia and Ib inhibitory interneurons, and Ia and Ib afferents. Neurons are connected by chemical synapses, which can show depression or facilitation. The simulated nuclei control flexion and extension of the human ankle: Medial Gastrocnemius (MG), Lateral Gastrocnemius (LG), Soleus (SOL) and Tibialis Anterior (TA).

ReMoto was developed over more than a decade at the Biomedical Engineering Laboratory (LEB) of the University of São Paulo, by the authors of the papers listed in [How to cite](#how-to-cite).

## Repository status

This repository contains two versions of ReMoto side by side:

| | Legacy version | New version (in development) |
|---|---|---|
| Location | `src/`, `WebContent/` | `remoto-core/`, `remoto-storage/`, `remoto-web/`, `remoto-cli/`, `scenarios/` |
| Stack | Java 8, Struts 1, JSP, Java applets, Apache Tomcat 7, HSQLDB | Java 25, Spring Boot 4, Thymeleaf + htmx + uPlot, file storage (no database), a single executable JAR (no Docker) |
| Simulation models | Complete: all published models | Placeholder engine; the original models are being ported |
| Users | Local accounts stored in HSQLDB | Login with ORCID or GitHub (OIDC), plus guest access |
| Status | Unmaintained; depends on software without security updates | Infrastructure working and tested; not yet suitable for scientific results |

### Repository layout

```
remoto-core/       simulation models and engine (plain Java)
remoto-storage/    file-based persistence (JSON scenarios, CSV.gz results)
remoto-web/        multi-user web application (Spring Boot)
remoto-cli/        command-line runner for scripts and clusters
scenarios/         public scenarios (JSON)
deploy/            systemd service and configuration template
docs/              documentation of the new version (in Portuguese)
src/, WebContent/  legacy application
ref.bib            BibTeX references
```

## New version

Detailed documentation, including the REST API and the data layout, is in [docs/nova-versao.md](docs/nova-versao.md) (in Portuguese).

### Features

- **Multi-user without a database:** each user's scenarios, runs and results are kept in their own folder. Guests get a temporary area that is deleted when the session ends.
- **Fair simulation queue:** several users can run simulations at the same time; a large batch from one user does not block the others.
- **Reproducible runs:** every run stores the exact scenario, the random seed and the engine version.
- Live progress, interactive charts, CSV and PNG downloads, a REST API and immutable share links.
- Users can export or delete all of their data.

### Requirements

JDK 25. Maven is downloaded automatically by the Maven Wrapper (`./mvnw`, or `mvnw.cmd` on Windows).

### Build and test

```bash
./mvnw verify
```

### Run locally

```bash
./mvnw -pl remoto-web -am spring-boot:run -Dspring-boot.run.profiles=dev
```

Then open http://localhost:8080. You can use the site as a guest. The `dev` profile also enables test accounts, defined in `remoto-web/src/main/resources/application-dev.yml`; use them only for local development.

### Command line

```bash
./mvnw -pl remoto-cli -am package -DskipTests
java -jar remoto-cli/target/remoto-cli-3.0.0-SNAPSHOT.jar run scenarios/exemplo-sol-mg.json -o results/example
java -jar remoto-cli/target/remoto-cli-3.0.0-SNAPSHOT.jar validate scenarios/*.json
```

### Deployment

The web application is a single executable JAR with an embedded web server, installed as a systemd service on Linux. Login uses OIDC providers (ORCID, GitHub), so ReMoto stores no passwords. The service file and the configuration template are in [deploy/](deploy/); the step-by-step guide is in the "Instalação no servidor" section of [docs/nova-versao.md](docs/nova-versao.md).

### Roadmap

1. Record reference outputs from the legacy version for canonical scenarios (H-reflex, recruitment curve, reciprocal inhibition).
2. Port the original models to `remoto-core`, checking them against those reference outputs.
3. Convert the legacy configurations into JSON scenarios.
4. Add a scenario editor, parameter sweeps, a Python client and NWB export.

## Legacy version

> [!WARNING]
> The legacy version depends on software that no longer receives security updates (Struts 1, Tomcat 7) and on Java applets, which no current browser runs, so the applet-based charts are not displayed. Use it only locally, for reference and to compare results with the new version.

The system has been tested on Windows and Ubuntu.

### Prerequisites

> [!TIP]
> Use the exact versions of Apache Tomcat 7.0 and Eclipse IDE 2025-03 linked below; other versions may cause installation problems.

- JDK 8
- [Apache Tomcat 7.0](https://archive.apache.org/dist/tomcat/tomcat-7/v7.0.109/bin/)
- [Eclipse IDE for Java Developers](https://www.eclipse.org/downloads/packages/release/2025-03/r)

### Step 1: Install Eclipse plugins

1. Launch Eclipse.
2. Go to `Help` → `Install New Software...`
3. In the "Work with" field, enter `https://download.eclipse.org/releases/2025-03`
4. Expand `Web, XML, Java EE and OSGi Enterprise Development`.
5. Select and install:
   - Eclipse Java EE Developer Tools
   - Eclipse Java Web Developer Tools
   - Eclipse Web Developer Tools
   - JST Server Adapters
   - JST Server Adapters Extensions
   - JST UI
6. Accept the license agreements and restart Eclipse when prompted.

### Step 2: Clone and import the repository

1. Clone the repository:
   ```bash
   git clone https://github.com/rogeriocisi/remoto
   ```
   > [!NOTE]
   > The path of the cloned repository is called `remotoPath` in the steps below.
2. In Eclipse, go to `File` → `Open Projects from File System...` and select the cloned folder.

### Step 3: Configure Apache Tomcat

1. In Eclipse, go to `Window` → `Preferences`.
2. Expand `Server` → `Runtime Environments`.
3. Remove any existing Tomcat runtime environment.
4. Click `Add` and select `Apache` → `Apache Tomcat 7.0`.
5. Set the installation directory to your Tomcat 7.0 folder. Defaults:
   - Windows: `C:\Program Files\Apache Software Foundation\Tomcat 7.0`
   - Linux: `/usr/share/tomcat7`

### Step 4: Configure the project

1. In Project Explorer, select the `remoto` project.
2. Go to `Project` → `Properties`.
3. Under `Java Build Path` → `Libraries`, remove the existing `JRE System Library` (if present), then click `Add Library` → `JRE System Library` → `Next` → `Finish`.
4. Under `Java Compiler`, enable `Use default compliance settings`.
5. Click `Apply`.

### Step 5: Update the Java project facet

1. Open the `Problems` view.
2. Find the error "Java compiler level does not match the version of the installed Java project facet".
3. Right-click it, select `Quick Fix` and choose `Change Java project facet version to Java 1.8`.
4. Click `Finish`.

> [!NOTE]
> Two errors saying "Access restriction: The type 'Type' is not API" are expected and can be ignored.

### Step 6: Configure the server

1. Open the `Servers` view (`Window` → `Show View` → `Other...` → `Servers`).
2. Remove any existing servers.
3. Right-click in the view, select `New` → `Server`, choose `Tomcat v7.0 Server` and add the `remoto` project.
4. Double-click `Tomcat v7.0 Server at localhost` and, in the `Ports` tab:
   - set `HTTP/1.1` to `8080` (or `80` on a machine with a static IP);
   - set `Tomcat admin port` to any free port.
5. Save (Ctrl + S).

### Step 7: Configure the database

1. Edit `WebContent/db/start.sh` (Linux) or `WebContent/db/start.bat` (Windows):
   - path to `hsqldb.jar`: `remotoPath/WebContent/WEB-INF/lib/hsqldb.jar`
   - database path: `remotoPath/WebContent/db/remoto`
2. The application connects to the HSQLDB server on `localhost`. To use another host, add `-Dremoto.db.host=<IP>` to the Tomcat VM arguments (double-click the server → `Open launch configuration` → `Arguments`).

### Step 8: Launch the application

1. Start the database with `start.sh` (Linux) or `start.bat` (Windows).
2. Start the server from the `Servers` view in Eclipse.
3. Open `http://localhost:8080/remoto/overview.htm` (or `http://<your-ip>/remoto/overview.htm` on port 80).
4. After changing the code, save all files and restart the server from the `Servers` view.

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
