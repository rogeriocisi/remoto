package br.remoto.core.engine.legacy;

import br.remoto.core.scenario.Scenario;
import br.remoto.core.simulation.ReMoto;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A escolha da membrana dos motoneurônios pelo campo {@code settings} do cenário. O padrão é a
 * membrana de pulso do original, então um cenário que não escolhe nada roda como sempre rodou —
 * é isso que mantém os cenários publicados idênticos.
 */
class MotoneuronModelOptionTest {

    private static final double DURATION_MS = 200;
    private static LegacyDatabase db;

    @BeforeAll
    static void open() {
        db = LegacyDatabase.open(Path.of(System.getProperty("remoto.legacy.db")));
    }

    @AfterAll
    static void close() {
        db.close();
    }

    @Test
    void semEscolhaOCenarioUsaAMembranaDePulso() {
        LegacyConfig original = db.export(17);
        assertThat(LegacyConfigurations.load(original).getCdMotoneuronModel())
                .isEqualTo(ReMoto.mnModelPulse);
    }

    @Test
    void aEscolhaFicaNoSettingsEVoltaAoReabrir() {
        LegacyConfig original = db.export(17);
        LegacyConfig with = new LegacyConfig(original.format(), original.idConfiguration(),
                original.tables(), Map.of("cdMotoneuronModel", ReMoto.mnModelContinuous));

        LegacyConfig stored = LegacyConfigurations.store(LegacyConfigurations.load(with), with);

        assertThat(stored.settings()).containsEntry("cdMotoneuronModel", ReMoto.mnModelContinuous);
        assertThat(LegacyConfigurations.load(stored).getCdMotoneuronModel())
                .isEqualTo(ReMoto.mnModelContinuous);

        // sem a escolha, o settings não ganha nada: um cenário publicado grava o que sempre gravou
        assertThat(LegacyConfigurations.store(LegacyConfigurations.load(original), original).settings())
                .doesNotContainKey("cdMotoneuronModel");
    }

    @Test
    void asDuasMembranasDaoResultadosDiferentes() {
        double pulso = soleusForce(run(Map.of()));
        double continua = soleusForce(run(Map.of("cdMotoneuronModel", ReMoto.mnModelContinuous)));

        // não é o mesmo modelo: o que não pode é a escolha não ter efeito
        assertThat(continua).isNotEqualTo(pulso);
    }

    private static double soleusForce(Recorder r) {
        String name = r.outputs.keySet().stream().filter(k -> k.startsWith("forca-")).findFirst().orElseThrow();
        return r.outputs.get(name).lines().skip(1)
                .mapToDouble(l -> Double.parseDouble(l.split(",")[1])).max().orElseThrow();
    }

    private static Recorder run(Map<String, Object> settings) {
        LegacyConfig original = db.export(17);
        LegacyConfig config = new LegacyConfig(original.format(), original.idConfiguration(),
                original.tables(), settings);
        Scenario base = db.exportScenario(17).withDuration(DURATION_MS);
        Scenario scenario = new Scenario(null, base.name(), "", base.schemaVersion(), base.seed(),
                DURATION_MS, base.stepMs(), Scenario.ENGINE_LEGACY, LegacyJson.toMap(config));
        Recorder r = new Recorder();
        new LegacyEngine(1).run(scenario, r);
        return r;
    }
}
