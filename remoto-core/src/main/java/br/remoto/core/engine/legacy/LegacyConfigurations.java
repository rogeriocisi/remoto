package br.remoto.core.engine.legacy;

import br.remoto.core.model.config.LegacyTablesReader;
import br.remoto.core.model.config.ModelConfig;
import br.remoto.core.model.config.TableData;
import br.remoto.core.simulation.setup.Configuration;
import br.remoto.core.simulation.setup.ConfigurationAssembler;
import br.remoto.core.simulation.setup.ConfigurationTables;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converte entre as tabelas de um cenário e a {@code Configuration} do ReMoto original, sem banco
 * de dados.
 *
 * <p>Para rodar ou editar, as tabelas viram parâmetros tipados ({@link ModelConfig}, lidos pelo
 * {@link LegacyTablesReader} com a semântica do HSQLDB), e o {@link ConfigurationAssembler} monta a
 * {@code Configuration} com o mesmo mapeamento do {@code ConfigurationDAO} original. Para gravar,
 * o {@link ConfigurationTables} gera as linhas que o {@code saveConfiguration} original gravaria.
 */
public final class LegacyConfigurations {

    private LegacyConfigurations() {
    }

    /** A partir das linhas exportadas (cenário JSON). */
    public static Configuration load(LegacyConfig config) {
        Configuration conf = ConfigurationAssembler.assemble(modelConfig(config));
        LegacySettings.apply(conf, config.settings());
        return conf;
    }

    /** As tabelas do cenário como parâmetros tipados, na ordem em que o DAO original as lia. */
    public static ModelConfig modelConfig(LegacyConfig config) {
        return LegacyTablesReader.read(config.idConfiguration(), tables(config));
    }

    /**
     * Grava uma {@code Configuration} editada de volta no formato do cenário, como o site antigo
     * fazia ao salvar. As tabelas que o DAO original não gravava (as globais, SCENARIO_MODELS...)
     * ficam como estavam.
     *
     * @throws IllegalStateException se o banco original recusaria a gravação
     */
    public static LegacyConfig store(Configuration conf, LegacyConfig original) {
        conf.setId(original.idConfiguration());
        Map<String, TableData> written;
        try {
            written = ConfigurationTables.write(conf, owner(original), LocalDateTime.now(), tables(original));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("não foi possível gravar a configuração: " + e.getMessage(), e);
        }
        Map<String, LegacyConfig.Table> tables = new LinkedHashMap<>();
        written.forEach((name, t) -> tables.put(name, new LegacyConfig.Table(t.columns(), t.rows())));
        // NOTHING: o saveConfiguration original insere linhas a cada gravação e nunca as apaga, e
        // nenhum código lê essa tabela. Mantém o conteúdo original para não crescer a cada edição.
        if (original.tables().containsKey("NOTHING")) {
            tables.put("NOTHING", original.tables().get("NOTHING"));
        }
        return new LegacyConfig(LegacyConfig.FORMAT, original.idConfiguration(), tables,
                LegacySettings.capture(conf));
    }

    private static Map<String, TableData> tables(LegacyConfig config) {
        Map<String, TableData> tables = new LinkedHashMap<>();
        config.tables().forEach((name, table) -> tables.put(name, new TableData(table.columns(), table.rows())));
        return tables;
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
}
