package br.remoto.storage.scenario;

import br.remoto.core.scenario.Scenario;
import br.remoto.storage.DataLayout;
import br.remoto.storage.Hashing;
import br.remoto.storage.JsonCodec;
import br.remoto.storage.StorageFiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Compartilhamento por link sem banco: o cenário é gravado em {@code shares/<sha256>.json}.
 * O link é o hash do conteúdo, então aponta sempre para exatamente a mesma configuração
 * (bom para citar em artigos) e compartilhar duas vezes o mesmo cenário dá o mesmo link.
 */
public final class ShareRepository {

    private static final Pattern HASH = Pattern.compile("^[0-9a-f]{64}$");

    private final DataLayout layout;
    private final JsonCodec json;

    public ShareRepository(DataLayout layout, JsonCodec json) {
        this.layout = layout;
        this.json = json;
    }

    /** @return o hash que identifica o compartilhamento */
    public String share(Scenario scenario) {
        byte[] bytes = json.toBytes(scenario.withId(null));
        String hash = Hashing.sha256Hex(bytes);
        Path file = layout.sharesDir().resolve(hash + ".json");
        if (!Files.exists(file)) {
            StorageFiles.writeAtomically(file, bytes);
        }
        return hash;
    }

    public Optional<Scenario> find(String hash) {
        if (hash == null || !HASH.matcher(hash).matches()) {
            return Optional.empty();
        }
        Path file = layout.sharesDir().resolve(hash + ".json");
        return Files.isRegularFile(file) ? Optional.of(json.read(file, Scenario.class)) : Optional.empty();
    }
}
