package br.remoto.storage;

import java.nio.file.Path;

/**
 * Organização do diretório de dados. É o "esquema" do armazenamento sem banco:
 *
 * <pre>
 * &lt;data&gt;/
 *   users/&lt;userId&gt;/profile.json
 *   users/&lt;userId&gt;/scenarios/&lt;id&gt;.json
 *   users/&lt;userId&gt;/runs/&lt;runId&gt;/{manifest.json, config.json, results/*.csv.gz}
 *   tmp/guest/&lt;userId&gt;/...          (mesma estrutura, apagada ao fim da sessão)
 *   shares/&lt;sha256&gt;.json             (cenários compartilhados, imutáveis)
 * &lt;cenários públicos&gt;/&lt;id&gt;.json      (versionados no Git, só leitura)
 * </pre>
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

    public Path usersDir() {
        return root.resolve("users");
    }

    public Path guestsDir() {
        return root.resolve("tmp").resolve("guest");
    }

    public Path sharesDir() {
        return root.resolve("shares");
    }

    public Path userDir(UserId user) {
        return (user.isGuest() ? guestsDir() : usersDir()).resolve(user.value());
    }

    public Path profileFile(UserId user) {
        return userDir(user).resolve("profile.json");
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
