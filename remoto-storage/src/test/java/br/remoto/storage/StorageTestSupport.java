package br.remoto.storage;

import br.remoto.core.scenario.Scenario;

import java.util.Map;

public final class StorageTestSupport {

    private StorageTestSupport() {
    }

    public static Scenario scenario(String name) {
        return new Scenario(null, name, "teste", Scenario.CURRENT_SCHEMA_VERSION, 42, 100, 0.05,
                Scenario.ENGINE_LEGACY, Map.of("format", "remoto-legacy-db/1"));
    }

    public static UserId user(String subject) {
        return UserId.registered("https://issuer.test", subject);
    }
}
