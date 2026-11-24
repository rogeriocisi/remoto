package br.remoto.core.engine.legacy;

import br.remoto.core.scenario.Scenario;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O bloqueio de condução dos axônios motores, que o original não tinha (a versão em Fortran para
 * doenças desmielinizantes bloqueia uma fração do núcleo): os motoneurônios bloqueados continuam
 * disparando no soma, mas nenhum disparo chega à placa motora, e a força do músculo cai.
 */
class AxonConductionBlockTest {

    /** A configuração 17 (sóleo: 800 S, 50 FR e 50 FF, com os aferentes Ia e o comando descendente). */
    private static final int POOL = 900;
    private static final double FRACTION = 0.1;
    private static final int BLOCKED = (int) (POOL * FRACTION);

    private static LegacyDatabase db;

    @BeforeAll
    static void open() {
        db = LegacyDatabase.open(Path.of(System.getProperty("remoto.legacy.db")));
    }

    @AfterAll
    static void close() {
        db.close();
    }

    /**
     * Com 10% do núcleo bloqueado, nenhum dos 90 motoneurônios mais lentos leva disparo ao terminal,
     * embora disparem no soma; sem o bloqueio, eles levam. Os mais rápidos continuam levando nos
     * dois casos.
     */
    @Test
    void osAxoniosMaisLentosNaoLevamDisparoAoTerminal() {
        Recorder none = run(Map.of());
        Recorder blocked = run(Map.of("axonConductionBlock", Map.of("SOL", FRACTION)));

        Set<Integer> somaNone = units(none, "disparos-soma-SOL");
        Set<Integer> somaBlocked = units(blocked, "disparos-soma-SOL");
        Set<Integer> terminalNone = units(none, "disparos-SOL");
        Set<Integer> terminalBlocked = units(blocked, "disparos-SOL");

        // os bloqueados disparam no soma nos dois casos: o bloqueio é no axônio
        assertThat(upTo(somaNone, BLOCKED)).as("soma, sem bloqueio").isNotEmpty();
        assertThat(upTo(somaBlocked, BLOCKED)).as("soma, com bloqueio")
                .hasSizeGreaterThan(upTo(somaNone, BLOCKED).size() / 2);

        // no terminal, só os bloqueados desaparecem
        assertThat(upTo(terminalNone, BLOCKED)).as("terminal, sem bloqueio")
                .isEqualTo(upTo(somaNone, BLOCKED));
        assertThat(upTo(terminalBlocked, BLOCKED)).as("terminal, com bloqueio").isEmpty();
        assertThat(above(terminalBlocked, BLOCKED)).as("terminal dos não bloqueados").isNotEmpty();
    }

    /** Com o núcleo todo bloqueado, o sóleo não faz força. */
    @Test
    void semAxoniosOMusculoNaoFazForca() {
        double force = lastForce(run(Map.of()));
        double blocked = lastForce(run(Map.of("axonConductionBlock", Map.of("SOL", 1.0))));

        assertThat(force).isGreaterThan(1);
        assertThat(blocked).isLessThan(force / 100);
    }

    @Test
    void oBloqueioFicaNoSettingsEVoltaAoReabrir() {
        LegacyConfig original = db.export(17);
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("axonConductionBlock", Map.of("SOL", 0.65));
        LegacyConfig with = new LegacyConfig(original.format(), original.idConfiguration(), original.tables(), settings);

        LegacyConfig stored = LegacyConfigurations.store(LegacyConfigurations.load(with), with);

        assertThat(stored.settings().get("axonConductionBlock")).isEqualTo(Map.of("SOL", 0.65));
        // sem bloqueio, o settings não ganha nada
        assertThat(LegacyConfigurations.store(LegacyConfigurations.load(original), original).settings())
                .doesNotContainKey("axonConductionBlock");
    }

    @Test
    void fracoesInvalidasSaoRecusadas() {
        LegacyConfig original = db.export(17);
        for (Object fraction : List.of(-0.1, 1.5)) {
            LegacyConfig with = new LegacyConfig(original.format(), original.idConfiguration(), original.tables(),
                    Map.of("axonConductionBlock", Map.of("SOL", fraction)));
            assertThatThrownBy(() -> LegacyConfigurations.load(with)).as("%s", fraction)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ------------------------------------------------------------------ apoio

    /** A configuração 17 com o settings dado, nos primeiros 300 ms. */
    private static Recorder run(Map<String, Object> settings) {
        LegacyConfig original = db.export(17);
        LegacyConfig config = new LegacyConfig(original.format(), original.idConfiguration(), original.tables(),
                settings.isEmpty() ? null : settings);
        Scenario base = db.exportScenario(17);
        Scenario scenario = new Scenario(null, base.name(), "", base.schemaVersion(), base.seed(), 300,
                base.stepMs(), Scenario.ENGINE_LEGACY, LegacyJson.toMap(config));
        Recorder r = new Recorder();
        new LegacyEngine(1).run(scenario, r);
        return r;
    }

    /** As unidades com algum disparo na saída. */
    private static Set<Integer> units(Recorder r, String name) {
        Set<Integer> units = new HashSet<>();
        String csv = r.outputs.get(name);
        if (csv != null) {
            csv.lines().skip(1).forEach(l -> units.add(Integer.parseInt(l.split(",")[1])));
        }
        return units;
    }

    private static Set<Integer> upTo(Set<Integer> units, int last) {
        return units.stream().filter(u -> u <= last).collect(java.util.stream.Collectors.toSet());
    }

    private static Set<Integer> above(Set<Integer> units, int last) {
        return units.stream().filter(u -> u > last).collect(java.util.stream.Collectors.toSet());
    }

    /** A força do sóleo no último instante registrado (N). */
    private static double lastForce(Recorder r) {
        String csv = r.outputs.get("forca-SOL");
        assertThat(csv).as("força do sóleo").isNotNull();
        String last = csv.lines().skip(1).reduce((a, b) -> b).orElseThrow();
        return Double.parseDouble(last.split(",")[1]);
    }
}
