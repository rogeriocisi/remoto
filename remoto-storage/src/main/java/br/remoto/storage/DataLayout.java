package br.remoto.storage;

import java.nio.file.Path;

/**
 * Organização do diretório de dados. É o "esquema" do armazenamento sem banco:
 *
 * <pre>
 * &lt;data&gt;/
 *   sessions/&lt;userId&gt;/scenarios/&lt;id&gt;.json
 *   sessions/&lt;userId&gt;/runs/&lt;runId&gt;/{manifest.json, config.json, results/*.csv.gz}
 * &lt;cenários públicos&gt;/&lt;id&gt;.json      (versionados no Git, só leitura)
 * </pre>
 *
 * Não há contas: cada sessão do navegador tem uma área, apagada quando a sessão termina.
 */
public final class DataLayout {

    private final Path root;
    private final Path publicScenarios;

    public DataLayout(Path root, Path publicScenarios) {
        this.root = root.toAbsolutePath().normalize();
        this.publicScenarios = publicScenarios.toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    public Path publicScenariosDir() {
        return publicScenarios;
    }

    public Path sessionsDir() {
        return root.resolve("sessions");
    }

    public Path userDir(UserId user) {
        return sessionsDir().resolve(user.value());
    }

    public Path scenariosDir(UserId user) {
        return userDir(user).resolve("scenarios");
    }

    public Path runsDir(UserId user) {
        return userDir(user).resolve("runs");
    }

    public Path runDir(UserId user, String runId) {
        return runsDir(user).resolve(Ids.requireSafe(runId));
    }
}
