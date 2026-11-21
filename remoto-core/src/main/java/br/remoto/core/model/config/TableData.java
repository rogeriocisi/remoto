package br.remoto.core.model.config;

import java.util.List;

/**
 * Uma tabela do banco original, como está no cenário: {@code rows[i][j]} é o valor da coluna
 * {@code columns[j]} (texto, número ou nulo).
 */
public record TableData(List<String> columns, List<List<Object>> rows) {

    public TableData {
        columns = columns == null ? List.of() : List.copyOf(columns);
        rows = rows == null ? List.of() : rows;
    }
}
