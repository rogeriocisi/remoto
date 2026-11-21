package br.remoto.legacy;

import br.remoto.model.Configuration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tabelas aleatórias que os dados reais quase não exercitam: centenas de empates no ORDER BY,
 * valores com mais de 4 casas, decimais em colunas inteiras, nulos e linhas de outras
 * configurações. O HSQLDB de verdade e o leitor tipado têm de montar a mesma configuração.
 */
class TypedConfigurationEdgeCasesTest {

    private static final int ID = 7;
    private static final String[] NUCLEI = {"SOL", "MG", "LG", "TA", "DT", "IN_ext"};
    private static final String[] COMPARTMENTS = {"D", "S", null};

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3, 4, 5})
    void tabelasAleatoriasDaoAMesmaConfiguracaoQueOHsqldb(long seed) {
        Random rnd = new Random(seed);
        Map<String, LegacyConfig.Table> tables = new LinkedHashMap<>();

        tables.put("CONFIGURATION", new LegacyConfig.Table(
                List.of("ID_CONFIGURATION", "CD_USER", "NM_CONFIGURATION", "TFIN", "STEP", "MERGE", "KEEP_PROPERTIES",
                        "STORE_RESULTS", "STORE_SIGNALS"),
                List.of(row(ID, "guest", "x", decimal(rnd), new BigDecimal("0.05"), 1, 0, 1, 0))));

        // NEURON_TYPE: muitos empates em (CD_NUCLEUS, INDEX)
        List<List<Object>> neurons = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            int id = rnd.nextInt(10) == 0 ? ID + 1 : ID;
            neurons.add(row("T" + i, id, NUCLEI[rnd.nextInt(NUCLEI.length)], "TR", decimal(rnd), nullable(rnd),
                    rnd.nextInt(3), rnd.nextBoolean() ? new BigDecimal("2.7") : rnd.nextInt(5), rnd.nextInt(200),
                    rnd.nextInt(2), rnd.nextInt(5) == 0 ? null : rnd.nextInt(4), "P"));
        }
        tables.put("NEURON_TYPE", new LegacyConfig.Table(
                List.of("CD_NEURON_TYPE", "ID_CONFIGURATION", "CD_NUCLEUS", "CD_CATEGORY", "THRESHOLD1",
                        "THRESHOLD2", "MEAN", "STD", "QUANTITY", "ACTIVE", "INDEX", "DISTRIBUTION"),
                neurons));

        // CONDUCTANCE_TYPE: empates em (COMPARTMENT, INDEX), compartimento nulo
        List<List<Object>> synapses = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            synapses.add(row("G" + rnd.nextInt(60), ID, NUCLEI[rnd.nextInt(NUCLEI.length)],
                    NUCLEI[rnd.nextInt(NUCLEI.length)] + i, decimal(rnd), decimal(rnd), decimal(rnd), nullable(rnd),
                    COMPARTMENTS[rnd.nextInt(COMPARTMENTS.length)], rnd.nextInt(2), rnd.nextInt(3)));
        }
        tables.put("CONDUCTANCE_TYPE", new LegacyConfig.Table(
                List.of("CD_CONDUCTANCE_TYPE", "ID_CONFIGURATION", "CD_NUCLEUS", "CD_NUCLEUS_PRE", "GMAX", "E",
                        "CONNECTIVITY", "DELAY", "COMPARTMENT", "ACTIVE", "INDEX"),
                synapses));

        // MISCELLANEOUS e HILL (sem chave primária: os empates ficam na ordem das linhas)
        List<List<Object>> misc = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            misc.add(row("p" + i, ID, "d", decimal(rnd), rnd.nextInt(4), rnd.nextInt(2)));
        }
        tables.put("MISCELLANEOUS", new LegacyConfig.Table(
                List.of("PROPERTY", "ID_CONFIGURATION", "DESCRIPTION", "VALUE", "INDEX", "DIVISION"), misc));
        List<List<Object>> hill = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            hill.add(row(ID, "M" + rnd.nextInt(5), decimal(rnd), decimal(rnd), rnd.nextInt(3)));
        }
        tables.put("HILL_TYPE_MUSCULOTENDON_PARAMETERS", new LegacyConfig.Table(
                List.of("ID_CONFIGURATION", "CD_MUSCLE", "OPTIMAL_LENGTH", "MAXIMUM_MUSCLE_FORCE", "INDEX"), hill));

        LegacyConfig config = dedupe(new LegacyConfig(LegacyConfig.FORMAT, ID, tables));

        Configuration throughHsqldb = LegacyConfigurations.loadThroughHsqldb(config);
        Configuration typed = LegacyConfigurations.load(config);

        assertThat(throughHsqldb.getNeuronTypes()).hasSizeGreaterThan(200);
        assertThat(DeepCompare.differences(throughHsqldb, typed)).isEmpty();
    }

    /** Remove chaves primárias repetidas, que o banco recusaria. */
    private static LegacyConfig dedupe(LegacyConfig config) {
        Map<String, int[]> keys = Map.of(
                "NEURON_TYPE", new int[] {0, 1, 2},
                "CONDUCTANCE_TYPE", new int[] {0, 1, 2, 3});
        Map<String, LegacyConfig.Table> tables = new LinkedHashMap<>();
        config.tables().forEach((name, table) -> {
            int[] key = keys.get(name);
            if (key == null) {
                tables.put(name, table);
                return;
            }
            Map<List<Object>, List<Object>> unique = new LinkedHashMap<>();
            for (List<Object> r : table.rows()) {
                List<Object> k = Arrays.stream(key).mapToObj(r::get).toList();
                unique.putIfAbsent(k, r);
            }
            tables.put(name, new LegacyConfig.Table(table.columns(), new ArrayList<>(unique.values())));
        });
        return new LegacyConfig(config.format(), config.idConfiguration(), tables);
    }

    /** Um decimal com até 7 casas, às vezes negativo. */
    private static BigDecimal decimal(Random rnd) {
        return BigDecimal.valueOf(rnd.nextLong(-20_000_000, 200_000_000), 7);
    }

    private static Object nullable(Random rnd) {
        return rnd.nextInt(4) == 0 ? null : decimal(rnd);
    }

    private static List<Object> row(Object... values) {
        return new ArrayList<>(Arrays.asList(values));
    }
}
