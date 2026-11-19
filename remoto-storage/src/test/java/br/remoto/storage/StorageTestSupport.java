package br.remoto.storage;

import br.remoto.core.scenario.Nucleus;
import br.remoto.core.scenario.NucleusSpec;
import br.remoto.core.scenario.Scenario;

import java.util.List;

public final class StorageTestSupport {

    private StorageTestSupport() {
    }

    public static Scenario scenario(String name) {
        return new Scenario(null, name, "teste", Scenario.CURRENT_SCHEMA_VERSION, 42, 100, 0.05,
                List.of(new NucleusSpec(Nucleus.SOL, 20, 5, 1)));
    }

    public static UserId user(String subject) {
        return UserId.registered("https://issuer.test", subject);
    }
}
