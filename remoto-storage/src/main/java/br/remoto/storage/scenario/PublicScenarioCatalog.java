package br.remoto.storage.scenario;

import br.remoto.core.scenario.Scenario;
import br.remoto.storage.DataLayout;
import br.remoto.storage.Ids;
import br.remoto.storage.JsonCodec;
import br.remoto.storage.StorageFiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Cenários públicos (o antigo usuário "guest"): arquivos JSON versionados no Git, só leitura.
 * O id é o nome do arquivo, por exemplo {@code scenarios/remoto-17-default-soleus-contraction.json}.
 */
public final class PublicScenarioCatalog {

    private final DataLayout layout;
    private final JsonCodec json;

    public PublicScenarioCatalog(DataLayout layout, JsonCodec json) {
        this.layout = layout;
        this.json = json;
    }

    public List<Scenario> list() {
        return StorageFiles.filesEndingWith(layout.publicScenariosDir(), ".json").stream()
                .map(this::load)
                .toList();
    }

    public Optional<Scenario> find(String id) {
        if (!Ids.isSafe(id)) {
            return Optional.empty();
        }
        Path file = layout.publicScenariosDir().resolve(id + ".json");
        return Files.isRegularFile(file) ? Optional.of(load(file)) : Optional.empty();
    }

    private Scenario load(Path file) {
        String name = file.getFileName().toString();
        return json.read(file, Scenario.class).withId(name.substring(0, name.length() - ".json".length()));
    }
}
