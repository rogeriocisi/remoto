package br.remoto.legacy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lê as tabelas de uma configuração de um banco legado (arquivo ou memória) no formato do cenário. */
final class LegacyTables {

    /** Tabelas compartilhadas por todas as configurações. */
    static final Set<String> GLOBAL_TABLES = Set.of("CATEGORY", "NUCLEUS", "JOINTS", "MU_POSITION");
    /** Nunca exportada (dados pessoais). */
    static final Set<String> SKIPPED_TABLES = Set.of("USER");

    private LegacyTables() {
    }

    static Map<String, LegacyConfig.Table> export(Connection connection, int idConfiguration) throws SQLException {
        Map<String, LegacyConfig.Table> tables = new LinkedHashMap<>();
        for (String table : tableNames(connection)) {
            if (SKIPPED_TABLES.contains(table)) {
                continue;
            }
            LegacyConfig.Table data = GLOBAL_TABLES.contains(table)
                    ? read(connection, table, null)
                    : read(connection, table, idConfiguration);
            if (data != null) {
                tables.put(table, data);
            }
        }
        return tables;
    }

    private static List<String> tableNames(Connection connection) throws SQLException {
        List<String> names = new ArrayList<>();
        String sql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES"
                + " WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME";
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        return names;
    }

    private static LegacyConfig.Table read(Connection connection, String table, Integer idConfiguration) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM " + table + " WHERE 1 = 0")) {
            ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                columns.add(md.getColumnName(i));
            }
        }
        if (idConfiguration != null && !columns.contains("ID_CONFIGURATION")) {
            return null; // tabela que não pertence a nenhuma configuração
        }
        String sql = "SELECT * FROM " + table + (idConfiguration == null ? "" : " WHERE ID_CONFIGURATION = ?");
        List<List<Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (idConfiguration != null) {
                ps.setInt(1, idConfiguration);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    List<Object> row = new ArrayList<>(columns.size());
                    for (int i = 1; i <= columns.size(); i++) {
                        row.add(jsonValue(rs.getObject(i)));
                    }
                    rows.add(row);
                }
            }
        }
        return new LegacyConfig.Table(columns, rows);
    }

    private static Object jsonValue(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toString();
        }
        if (value instanceof BigDecimal bd) {
            return bd.stripTrailingZeros().scale() <= 0 ? bd.setScale(0) : bd.stripTrailingZeros();
        }
        return value;
    }
}
