package br.remoto.web.ui;

import br.remoto.core.scenario.Scenario;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Os temas da lista de cenários públicos só servem se acompanharem os cenários: um cenário novo
 * sem tema cairia no grupo "outros", e um id escrito errado na classificação nunca casaria com
 * cenário nenhum. Os dois testes abaixo falham em cada um desses casos.
 */
class ScenarioGroupsTest {

    private static final Path SCENARIOS = Path.of("..", "scenarios");

    private static Set<String> publicIds() throws IOException {
        try (Stream<Path> files = Files.list(SCENARIOS)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - ".json".length()))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }

    @Test
    void todoCenarioPublicoTemTema() throws IOException {
        List<String> semTema = publicIds().stream()
                .filter(id -> ScenarioGroups.UNCLASSIFIED.equals(ScenarioGroups.themeOf(id)))
                .sorted()
                .toList();

        assertThat(semTema).as("cenários públicos sem tema no ScenarioGroups").isEmpty();
    }

    @Test
    void todoTemaApontaParaUmCenarioQueExiste() throws IOException {
        Set<String> existem = publicIds();
        List<String> fantasmas = ScenarioGroups.classified().stream()
                .filter(id -> !existem.contains(id))
                .sorted()
                .toList();

        assertThat(fantasmas).as("ids classificados que não existem em scenarios/").isEmpty();
    }

    /** Os grupos saem na ordem declarada, nenhum vazio, e nenhum cenário se perde pelo caminho. */
    @Test
    void osGruposReunemTodosOsCenarios() {
        List<Scenario> cenarios = List.of(cenario("remoto-17-default-soleus-contraction"),
                cenario("projectfr-h-reflex-m-wave"), cenario("aidp-force-task-s0"),
                cenario("modelo-completo"));

        List<ScenarioGroups.Group> grupos = ScenarioGroups.of(cenarios);

        assertThat(grupos).extracting(ScenarioGroups.Group::key)
                .containsExactly("contraction", "reflexes", "demyelination", "complete");
        assertThat(grupos).allSatisfy(g -> {
            assertThat(g.scenarios()).as("grupo %s", g.key()).isNotEmpty();
            assertThat(g.label()).as("rótulo de %s", g.key()).isNotBlank().doesNotContain("scenarios.group");
        });
        assertThat(grupos.stream().mapToLong(g -> g.scenarios().size()).sum()).isEqualTo(cenarios.size());
    }

    private static Scenario cenario(String id) {
        return new Scenario(id, id, "", Scenario.CURRENT_SCHEMA_VERSION, 1, 100, 0.05, null, null);
    }
}
