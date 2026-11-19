package br.remoto.legacy;

import br.remoto.dao.BasicDAO;
import br.remoto.dao.ConfigurationDAO;
import br.remoto.model.Configuration;
import br.remoto.model.vo.User;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reconstrói a {@code Configuration} do código original.
 *
 * <p>O {@code ConfigurationDAO} legado só sabe ler de um banco JDBC. Para reaproveitá-lo sem
 * reescrever as ~2 mil linhas de mapeamento, as linhas do cenário JSON são carregadas num HSQLDB
 * <em>em memória</em>, privado e descartado logo depois — nada é gravado em disco. Quando os
 * modelos forem portados para o remoto-core com parâmetros tipados, esta classe deixa de existir.
 *
 * <p>O DAO legado usa uma conexão estática; por isso as cargas são serializadas.
 */
public final class LegacyConfigurations {

    private static final AtomicLong COUNTER = new AtomicLong();
    private static final String URL_PROPERTY = "remoto.db.url";

    private LegacyConfigurations() {
    }

    /** A partir das linhas exportadas (cenário JSON). */
    public static synchronized Configuration load(LegacyConfig config) {
        String url = "jdbc:hsqldb:mem:remoto-legacy-" + COUNTER.incrementAndGet();
        try (Connection connection = DriverManager.getConnection(url, "SA", "")) {
            try (Statement st = connection.createStatement()) {
                for (String ddl : schema()) {
                    st.execute(ddl);
                }
            }
            for (Map.Entry<String, LegacyConfig.Table> entry : config.tables().entrySet()) {
                insert(connection, entry.getKey(), entry.getValue());
            }
            try {
                return loadFromJdbc(url, config.idConfiguration());
            } finally {
                try (Statement st = connection.createStatement()) {
                    st.execute("SHUTDOWN");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("não foi possível montar a configuração legada", e);
        }
    }

    /**
     * Grava uma {@code Configuration} editada de volta no formato do cenário, usando o
     * {@code ConfigurationDAO.saveConfiguration} original — o mesmo que o site antigo chamava ao
     * salvar. As tabelas que esse DAO não grava (as globais, SCENARIO_MODELS...) ficam como estavam.
     */
    public static synchronized LegacyConfig store(Configuration conf, LegacyConfig original) {
        String url = "jdbc:hsqldb:mem:remoto-legacy-" + COUNTER.incrementAndGet();
        try (Connection connection = DriverManager.getConnection(url, "SA", "")) {
            try (Statement st = connection.createStatement()) {
                for (String ddl : schema()) {
                    st.execute(ddl);
                }
            }
            for (Map.Entry<String, LegacyConfig.Table> entry : original.tables().entrySet()) {
                insert(connection, entry.getKey(), entry.getValue());
            }
            String previous = System.getProperty(URL_PROPERTY);
            System.setProperty(URL_PROPERTY, url);
            boolean saved;
            try {
                new BasicDAO().disconnect();
                conf.setId(original.idConfiguration());
                saved = new ConfigurationDAO().saveConfiguration(conf, new User(owner(original)));
            } finally {
                new BasicDAO().disconnect();
                if (previous == null) {
                    System.clearProperty(URL_PROPERTY);
                } else {
                    System.setProperty(URL_PROPERTY, previous);
                }
            }
            if (!saved) {
                throw new IllegalStateException("o ConfigurationDAO original não conseguiu gravar a configuração");
            }
            Map<String, LegacyConfig.Table> tables = LegacyTables.export(connection, original.idConfiguration());
            try (Statement st = connection.createStatement()) {
                st.execute("SHUTDOWN");
            }
            // NOTHING: o saveConfiguration original insere linhas a cada gravação e nunca as apaga, e
            // nenhum código lê essa tabela. Mantém o conteúdo original para não crescer a cada edição.
            if (original.tables().containsKey("NOTHING")) {
                tables.put("NOTHING", original.tables().get("NOTHING"));
            }
            return new LegacyConfig(LegacyConfig.FORMAT, original.idConfiguration(), tables);
        } catch (SQLException e) {
            throw new IllegalStateException("não foi possível gravar a configuração legada", e);
        }
    }

    /** CD_USER da linha de CONFIGURATION (o DAO grava a configuração em nome de um usuário). */
    private static String owner(LegacyConfig config) {
        LegacyConfig.Table table = config.tables().get("CONFIGURATION");
        if (table != null && !table.rows().isEmpty()) {
            int column = table.columns().indexOf("CD_USER");
            if (column >= 0 && table.rows().get(0).get(column) != null) {
                return table.rows().get(0).get(column).toString();
            }
        }
        return "guest";
    }

    /** Direto de um banco JDBC (o arquivo original), usando o DAO legado sem alterações. */
    public static synchronized Configuration loadFromJdbc(String url, int idConfiguration) {
        String previous = System.getProperty(URL_PROPERTY);
        System.setProperty(URL_PROPERTY, url);
        try {
            new BasicDAO().disconnect();
            Configuration conf = new ConfigurationDAO().getConfiguration(idConfiguration);
            if (conf == null || conf.getGeneral() == null) {
                throw new IllegalStateException("configuração " + idConfiguration + " não encontrada");
            }
            return conf;
        } finally {
            new BasicDAO().disconnect();
            if (previous == null) {
                System.clearProperty(URL_PROPERTY);
            } else {
                System.setProperty(URL_PROPERTY, previous);
            }
        }
    }

    private static void insert(Connection connection, String table, LegacyConfig.Table data) throws SQLException {
        if (!table.matches("[A-Z_]+") || data.rows().isEmpty()) {
            return;
        }
        for (String column : data.columns()) {
            if (!column.matches("[A-Z_0-9]+")) {
                throw new IllegalArgumentException("coluna inválida: " + column);
            }
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(data.columns().size(), "?"));
        String sql = "INSERT INTO " + table + " (" + String.join(",", data.columns()) + ") VALUES (" + placeholders + ")";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (List<Object> row : data.rows()) {
                for (int i = 0; i < data.columns().size(); i++) {
                    ps.setObject(i + 1, i < row.size() ? row.get(i) : null);
                }
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static List<String> schema() {
        try (InputStream in = LegacyConfigurations.class.getResourceAsStream("legacy-schema.sql")) {
            if (in == null) {
                throw new IllegalStateException("legacy-schema.sql ausente");
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return Arrays.stream(text.split("\\R"))
                    .map(String::trim)
                    .filter(l -> !l.isEmpty() && !l.startsWith("--"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
