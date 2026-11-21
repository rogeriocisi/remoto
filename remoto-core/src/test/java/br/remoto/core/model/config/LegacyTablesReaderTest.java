package br.remoto.core.model.config;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O leitor das tabelas do banco original. A equivalência com o HSQLDB de verdade, linha a linha e
 * na mesma ordem, é testada no remoto-legacy, onde o banco está disponível.
 */
class LegacyTablesReaderTest {

    @Test
    void numericArredondaComAMetadeParaBaixoEIntegerTrunca() {
        ModelConfig c = LegacyTablesReader.read(1, Map.of("MISCELLANEOUS", table(
                List.of("PROPERTY", "ID_CONFIGURATION", "VALUE", "INDEX", "DIVISION"),
                row("a", 1, new BigDecimal("0.12345"), new BigDecimal("2.7"), 1),
                row("b", 1, new BigDecimal("0.12347"), 3, 0),
                row("c", 1, null, null, null))));

        assertThat(c.miscellaneous()).extracting(MiscellaneousParam::property).containsExactly("c", "a", "b");
        assertThat(c.miscellaneous().get(1).value()).isEqualTo(0.1234);
        assertThat(c.miscellaneous().get(1).index()).isEqualTo(2);
        assertThat(c.miscellaneous().get(1).division()).isTrue();
        assertThat(c.miscellaneous().get(2).value()).isEqualTo(0.1235);
        // nulo vira 0 e false, e vem primeiro no ORDER BY
        assertThat(c.miscellaneous().get(0)).isEqualTo(new MiscellaneousParam("c", null, 0, 0, false));
    }

    @Test
    void soEntramAsLinhasDaConfiguracao() {
        ModelConfig c = LegacyTablesReader.read(2, Map.of("MISCELLANEOUS", table(
                List.of("PROPERTY", "ID_CONFIGURATION", "VALUE"),
                row("x", 1, 1), row("y", 2, 2))));

        assertThat(c.id()).isEqualTo(2);
        assertThat(c.miscellaneous()).extracting(MiscellaneousParam::property).containsExactly("y");
    }

    @Test
    void recusaOQueOInsertRecusava() {
        assertThatThrownBy(() -> LegacyTablesReader.read(1, Map.of("MISCELLANEOUS", table(
                List.of("PROPERTY", "ID_CONFIGURATION", "NAO_EXISTE"), row("a", 1, 1)))))
                .hasMessageContaining("coluna desconhecida");
        assertThatThrownBy(() -> LegacyTablesReader.read(1, Map.of("MISCELLANEOUS", table(
                List.of("PROPERTY", "ID_CONFIGURATION"), row("a", 1), row("a", 1)))))
                .hasMessageContaining("chave primária repetida");
        assertThatThrownBy(() -> LegacyTablesReader.read(1, Map.of("TABELA_NOVA", table(
                List.of("X"), row(1)))))
                .hasMessageContaining("tabela desconhecida");

        // tabela desconhecida vazia e as tabelas que o DAO não lê são aceitas
        assertThat(LegacyTablesReader.read(1, Map.of(
                "TABELA_NOVA", table(List.of("X")),
                "SCENARIO_MODELS", table(List.of("ID_CONFIGURATION"), row(1)))).neuronTypes()).isEmpty();
    }

    @Test
    void aOrdenacaoDoHsqldbNaoEEstavelAcimaDe16Linhas() {
        // 40 linhas com a mesma chave de ordenação: um algoritmo estável não mexeria na ordem
        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            rows.add(i);
        }
        LegacyTablesReader.hsqldbSort(rows, Comparator.comparingInt(i -> 0));

        assertThat(rows).containsExactlyInAnyOrderElementsOf(range(40)).isNotEqualTo(range(40));

        // e ordena de fato
        List<Integer> shuffled = new ArrayList<>(List.of(5, 3, 9, 1, 7, 2, 8, 0, 6, 4, 15, 12, 19, 11, 17, 13, 18,
                10, 16, 14, 25, 22, 29, 21, 27, 23, 28, 20, 26, 24));
        LegacyTablesReader.hsqldbSort(shuffled, Comparator.naturalOrder());
        assertThat(shuffled).isEqualTo(range(30));
    }

    private static List<Integer> range(int n) {
        List<Integer> r = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            r.add(i);
        }
        return r;
    }

    @SafeVarargs
    private static TableData table(List<String> columns, List<Object>... rows) {
        return new TableData(columns, Arrays.asList(rows));
    }

    private static List<Object> row(Object... values) {
        return new ArrayList<>(Arrays.asList(values));
    }
}
