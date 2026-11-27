package br.remoto.core.engine.legacy;

import br.remoto.core.scenario.Scenario;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cada sinapse entra num compartimento só do neurônio pós-sináptico, o que a coluna COMPARTMENT
 * diz: não existem duas sinapses, uma no soma e outra no dendrito, para o mesmo par (a chave da
 * CONDUCTANCE_TYPE não inclui o compartimento). Aqui ficam as duas pontas disso no motor:
 * <ul>
 *   <li>no motoneurônio, que tem dois compartimentos, a escolha decide em qual deles a sinapse
 *       entra;</li>
 *   <li>no interneurônio, que tem um só e cuja membrana (InterneuronMembrane) integra os grupos
 *       dendríticos, a escolha não vale e a sinapse entra onde ela é lida. Uma configuração que
 *       diga "S" ali — o editor chegou a gravar isso sozinho — punha a sinapse num grupo que
 *       ninguém lê, e a entrada sumia da simulação sem aviso.
 * </ul>
 */
class SynapseCompartmentTest {

    private static final double SHORT_RUN_MS = 40;
    private static LegacyDatabase db;

    @BeforeAll
    static void open() {
        db = LegacyDatabase.open(Path.of(System.getProperty("remoto.legacy.db")));
    }

    @AfterAll
    static void close() {
        db.close();
    }

    /**
     * Na configuração 17, as sinapses sobre os motoneurônios do sóleo (aferentes Ia e tratos
     * descendentes) estão no dendrito. Postas no soma, a condutância excitatória dendrítica deles
     * zera: a sinapse mudou mesmo de compartimento, não foi para os dois.
     */
    @Test
    void noMotoneuronioOCompartimentoEscolhidoDecideOndeASinapseEntra() {
        double noDendrito = condutanciaExcitatoriaDendritica(null);
        double noSoma = condutanciaExcitatoriaDendritica("SOL");

        assertThat(noDendrito).isPositive();
        assertThat(noSoma).isZero();
    }

    @Test
    void sinapseSobreInterneuronioChegaMesmoComOCompartimentoDoSoma() throws Exception {
        double semCompartimento = condutanciaExcitatoriaNosInterneuronios(false);
        double comSoma = condutanciaExcitatoriaNosInterneuronios(true);

        assertThat(semCompartimento).isPositive();
        assertThat(comSoma).isEqualTo(semCompartimento);
    }

    /**
     * A condutância excitatória máxima somada nos dendritos dos motoneurônios do sóleo (µS), em
     * 5 ms da configuração 17; com {@code poolNoSoma}, as sinapses sobre os motoneurônios desse
     * núcleo vão todas para o soma.
     */
    private static double condutanciaExcitatoriaDendritica(String poolNoSoma) {
        LegacyConfig original = db.export(17);
        LegacyConfig config = poolNoSoma == null ? original
                : comCompartimento(original, "S",
                        (cols, row) -> poolNoSoma.equals(row.get(cols.indexOf("CD_NUCLEUS")))
                                && ((String) row.get(cols.indexOf("CD_CONDUCTANCE_TYPE"))).contains("-MN"));
        Scenario base = db.exportScenario(17);
        Scenario scenario = new Scenario(null, base.name(), "", base.schemaVersion(), base.seed(), 5,
                base.stepMs(), Scenario.ENGINE_LEGACY, LegacyJson.toMap(config));

        return somaDoResumo(executa(scenario), f -> f[0].equals("SOL") && f[1].equals("MN"));
    }

    /**
     * A condutância excitatória máxima somada nos interneurônios do modelo completo (µS), com o
     * compartimento das sinapses sobre interneurônio como está na configuração ou trocado por "S".
     */
    private static double condutanciaExcitatoriaNosInterneuronios(boolean somaNosInterneuronios) throws Exception {
        Scenario base = new JsonCodec().fromBytes(
                Files.readAllBytes(scenariosDir().resolve("modelo-completo.json")), Scenario.class);
        LegacyConfig original = LegacyJson.toConfig(base.legacy());
        LegacyConfig config = !somaNosInterneuronios ? original
                : comCompartimento(original, "S",
                        (cols, row) -> !((String) row.get(cols.indexOf("CD_CONDUCTANCE_TYPE"))).contains("-MN"));
        Scenario scenario = new Scenario(null, base.name(), "", base.schemaVersion(), base.seed(), SHORT_RUN_MS,
                base.stepMs(), Scenario.ENGINE_LEGACY, LegacyJson.toMap(config));

        return somaDoResumo(executa(scenario), f -> f[1].equals("IN"));
    }

    /** A configuração com o compartimento trocado nas linhas que {@code quais} escolhe. */
    private static LegacyConfig comCompartimento(LegacyConfig config, String compartimento,
                                                 java.util.function.BiPredicate<List<String>, List<Object>> quais) {
        Map<String, LegacyConfig.Table> tables = new LinkedHashMap<>(config.tables());
        LegacyConfig.Table t = tables.get("CONDUCTANCE_TYPE");
        int compartment = t.columns().indexOf("COMPARTMENT");
        List<List<Object>> rows = new ArrayList<>();
        int trocadas = 0;
        for (List<Object> row : t.rows()) {
            List<Object> copy = new ArrayList<>(row);
            if (quais.test(t.columns(), copy)) {
                copy.set(compartment, compartimento);
                trocadas++;
            }
            rows.add(copy);
        }
        assertThat(trocadas).as("linhas trocadas de compartimento").isPositive();
        tables.put("CONDUCTANCE_TYPE", new LegacyConfig.Table(t.columns(), rows));
        return new LegacyConfig(config.format(), config.idConfiguration(), tables, config.settings());
    }

    private static String[] executa(Scenario scenario) {
        Recorder r = new Recorder();
        new LegacyEngine(1).run(scenario, r);
        return r.outputs.get("tabela-resumo").split("\n");
    }

    /**
     * A soma da coluna "gmax_exc_dendrito[uS]" nas linhas que {@code quais} escolhe, por núcleo e
     * categoria. O resumo só traz os grupos dendríticos: uma sinapse que foi para o soma some dele.
     */
    private static double somaDoResumo(String[] lines, Predicate<String[]> quais) {
        List<String> header = List.of(lines[0].split(","));
        int nucleus = header.indexOf("nucleo");
        int category = header.indexOf("categoria");
        int gmax = header.indexOf("gmax_exc_dendrito[uS]");
        double total = 0;
        int neuronios = 0;
        for (int i = 1; i < lines.length; i++) {
            String[] f = lines[i].split(",");
            if (quais.test(new String[] {f[nucleus], f[category]})) {
                total += Double.parseDouble(f[gmax]);
                neuronios++;
            }
        }
        assertThat(neuronios).as("neurônios no resumo").isPositive();
        return total;
    }

    private static Path scenariosDir() {
        return Path.of(System.getProperty("remoto.legacy.db")).getParent().getParent().resolve("scenarios");
    }
}
