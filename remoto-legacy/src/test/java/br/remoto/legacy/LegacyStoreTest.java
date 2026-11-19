package br.remoto.legacy;

import br.remoto.core.scenario.Scenario;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Carregar e gravar de volta com o DAO original, sem editar nada, não pode mudar a configuração. */
class LegacyStoreTest {

    private static LegacyDatabase db;

    @BeforeAll
    static void open() {
        db = LegacyDatabase.open(Path.of(System.getProperty("remoto.legacy.db")));
    }

    @AfterAll
    static void close() {
        db.close();
    }

    @ParameterizedTest
    @ValueSource(ints = {17, 24, 42})
    void gravarSemEditarPreservaAsTabelas(int id) {
        LegacyConfig original = db.export(id);

        LegacyConfig stored = LegacyConfigurations.store(LegacyConfigurations.load(original), original);

        for (Map.Entry<String, LegacyConfig.Table> e : original.tables().entrySet()) {
            LegacyConfig.Table after = stored.tables().get(e.getKey());
            assertThat(after).as("tabela %s", e.getKey()).isNotNull();
            assertThat(after.columns()).as("colunas de %s", e.getKey()).isEqualTo(e.getValue().columns());
            assertThat(rows(after)).as("linhas de %s", e.getKey()).isEqualTo(rows(e.getValue()));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {17})
    void gravarSemEditarMantemAsSaidasDaSimulacao(int id) throws Exception {
        LegacyConfig original = db.export(id);
        LegacyConfig stored = LegacyConfigurations.store(LegacyConfigurations.load(original), original);
        Scenario base = db.exportScenario(id).withDuration(40);
        Scenario roundTrip = new Scenario(null, base.name(), base.description(), base.schemaVersion(), base.seed(),
                base.durationMs(), base.stepMs(), List.of(), Scenario.ENGINE_LEGACY, LegacyJson.toMap(stored));

        Recorder a = new Recorder();
        new LegacyEngine().run(base, a);
        Recorder b = new Recorder();
        new LegacyEngine().run(roundTrip, b);

        assertThat(b.sha256()).isEqualTo(a.sha256());
    }

    /**
     * Conjunto de linhas como texto (a ordem física pode mudar ao regravar). DT_CONFIGURATION é a
     * data da última gravação: o DAO original a atualiza a cada save, então fica de fora.
     */
    private static Set<String> rows(LegacyConfig.Table table) {
        int ignored = table.columns().indexOf("DT_CONFIGURATION");
        Set<String> set = new HashSet<>();
        for (List<Object> row : table.rows()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < row.size(); i++) {
                Object v = i == ignored ? "*" : row.get(i);
                sb.append(v instanceof Number n ? new java.math.BigDecimal(n.toString()).stripTrailingZeros().toPlainString() : v)
                        .append('|');
            }
            set.add(sb.toString());
        }
        return set;
    }
}
