package br.remoto.legacy;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Uma configuração do ReMoto original, exportada do banco HSQLDB tabela por tabela.
 *
 * <p>É o conteúdo do campo {@code legacy} de um cenário com {@code "engine": "legacy"}. Guarda
 * as linhas exatamente como estavam no banco, para que o código original reconstrua a mesma
 * {@code Configuration} sem depender do banco.
 *
 * @param format          versão deste formato ({@value #FORMAT})
 * @param idConfiguration ID_CONFIGURATION original
 * @param tables          nome da tabela → colunas e linhas
 * @param settings        propriedades da {@code Configuration} que o ConfigurationDAO original não
 *                        gravava (modelos escolhidos, ganhos do fuso, ângulos...); ausente nos
 *                        cenários exportados direto do banco, onde valem os padrões do DAO
 */
public record LegacyConfig(String format, int idConfiguration, Map<String, Table> tables,
                           @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> settings) {

    public static final String FORMAT = "remoto-legacy-db/1";

    public LegacyConfig {
        tables = tables == null ? Map.of() : tables;
        settings = settings == null ? Map.of() : settings;
    }

    public LegacyConfig(String format, int idConfiguration, Map<String, Table> tables) {
        this(format, idConfiguration, tables, null);
    }

    /** Linhas de uma tabela: {@code rows[i][j]} é o valor da coluna {@code columns[j]}. */
    public record Table(List<String> columns, List<List<Object>> rows) {

        public Table {
            columns = columns == null ? List.of() : columns;
            rows = rows == null ? List.of() : rows;
        }
    }
}
