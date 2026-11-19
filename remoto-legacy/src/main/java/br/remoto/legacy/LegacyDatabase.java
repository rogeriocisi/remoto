package br.remoto.legacy;

import br.remoto.core.scenario.Scenario;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lê o banco HSQLDB do ReMoto original (arquivos {@code remoto.script} e
 * {@code remoto.properties}) e exporta configurações como cenários JSON.
 *
 * <p>O banco é copiado para uma pasta temporária antes de abrir: o original nunca é alterado.
 * A tabela USER (dados pessoais) nunca é lida.
 */
public final class LegacyDatabase implements AutoCloseable {


    private final Path workDir;
    private final String url;
    private final Connection connection;

    public record ConfigurationInfo(int id, String owner, String name, String description,
                                    double durationMs, double stepMs) {
    }

    private LegacyDatabase(Path workDir, String url, Connection connection) {
        this.workDir = workDir;
        this.url = url;
        this.connection = connection;
    }

    /**
     * @param databasePath caminho sem extensão, por exemplo {@code WebContent/db/remoto}
     */
    public static LegacyDatabase open(Path databasePath) {
        Path script = Path.of(databasePath + ".script");
        if (!Files.isRegularFile(script)) {
            throw new IllegalArgumentException("banco legado não encontrado: " + script);
        }
        try {
            Path workDir = Files.createTempDirectory("remoto-legacy-db");
            // O banco foi gravado pelo HSQLDB 2.2; a sintaxe abaixo mudou nas versões 2.3+.
            String text = Files.readString(script, StandardCharsets.ISO_8859_1)
                    .replace("SET DATABASE TRANSACTION ROLLBACK ON DEADLOCK TRUE",
                            "SET DATABASE TRANSACTION ROLLBACK ON CONFLICT TRUE");
            Files.writeString(workDir.resolve("remoto.script"), text, StandardCharsets.ISO_8859_1);
            Path properties = Path.of(databasePath + ".properties");
            if (Files.isRegularFile(properties)) {
                Files.copy(properties, workDir.resolve("remoto.properties"), StandardCopyOption.REPLACE_EXISTING);
            }
            String url = "jdbc:hsqldb:file:" + workDir.resolve("remoto").toAbsolutePath().toString().replace('\\', '/');
            return new LegacyDatabase(workDir, url, DriverManager.getConnection(url, "SA", ""));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (SQLException e) {
            throw new IllegalStateException("não foi possível abrir o banco legado", e);
        }
    }

    /** URL JDBC da cópia aberta (para o DAO original ler direto, nos testes de equivalência). */
    public String jdbcUrl() {
        return url;
    }

    public List<ConfigurationInfo> configurations() {
        String sql = "SELECT ID_CONFIGURATION, CD_USER, NM_CONFIGURATION, DS_CONFIGURATION, TFIN, STEP"
                + " FROM CONFIGURATION ORDER BY ID_CONFIGURATION";
        List<ConfigurationInfo> list = new ArrayList<>();
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new ConfigurationInfo(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getDouble(5), rs.getDouble(6)));
            }
            return list;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public LegacyConfig export(int idConfiguration) {
        Map<String, LegacyConfig.Table> tables;
        try {
            tables = LegacyTables.export(connection, idConfiguration);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        if (!tables.containsKey("CONFIGURATION") || tables.get("CONFIGURATION").rows().isEmpty()) {
            throw new IllegalArgumentException("configuração " + idConfiguration + " não existe no banco legado");
        }
        return new LegacyConfig(LegacyConfig.FORMAT, idConfiguration, tables);
    }

    /** Cenário pronto para o site ou a CLI, com a duração e o passo originais. */
    public Scenario exportScenario(int idConfiguration) {
        ConfigurationInfo info = configurations().stream()
                .filter(c -> c.id() == idConfiguration)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("configuração " + idConfiguration + " não existe"));
        String description = (info.description() == null || info.description().isBlank())
                ? "Configuração " + idConfiguration + " do ReMoto original."
                : info.description();
        return new Scenario(null, info.name(), description, Scenario.CURRENT_SCHEMA_VERSION, idConfiguration,
                info.durationMs(), info.stepMs(), List.of(), Scenario.ENGINE_LEGACY,
                LegacyJson.toMap(export(idConfiguration)));
    }

    @Override
    public void close() {
        try (Statement st = connection.createStatement()) {
            st.execute("SHUTDOWN");
        } catch (SQLException ignored) {
            // já fechado
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // já fechado
        }
        try (var files = Files.walk(workDir)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // pasta temporária
        }
    }
}
