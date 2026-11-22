package br.remoto.core.simulation.setup;

import br.remoto.core.model.config.LegacySchema;
import br.remoto.core.model.config.TableData;
import br.remoto.core.simulation.Current;
import br.remoto.core.simulation.ReMoto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Grava uma {@link Configuration} de volta nas tabelas do cenário, sem banco de dados.
 *
 * <p>Repete o que o site antigo fazia ao salvar: o {@code ConfigurationDAO.saveConfiguration}
 * original apagava as linhas da configuração e inseria as novas, tabela por tabela, a partir dos
 * mesmos objetos da {@code Configuration}; depois o cenário era exportado com um {@code SELECT}.
 * Aqui cada método monta as linhas que aquele {@code INSERT} gravaria, e o {@link LegacySchema}
 * faz o que o HSQLDB fazia com elas (tipos, arredondamento, {@code NOT NULL}, chave primária e
 * ordem). As tabelas que o DAO não gravava (as globais, {@code SCENARIO_MODELS}) ficam como estavam.
 */
public final class ConfigurationTables {
    /** Tabelas apagadas e regravadas pelo saveConfiguration original. */
    private static final Set<String> WRITTEN = Set.of("CONFIGURATION", "INTERFACE_PARAMETERS",
            "HILL_TYPE_MUSCULOTENDON_PARAMETERS", "NERVE", "MOTORUNIT_TYPE", "NEURON_TYPE", "DESCENDING_COMMANDS",
            "CONDUCTANCE_TYPE", "MARKOV_TYPE", "SYNAPTIC_DYNAMICS", "INJECTED_CURRENT", "MISCELLANEOUS");

    /** Lidas sem filtro de configuração, compartilhadas por todas. */
    private static final Set<String> GLOBAL = Set.of("CATEGORY", "NUCLEUS", "JOINTS", "MU_POSITION");

    private final Configuration conf;
    private final int id;
    private final Map<String, List<Map<String, Object>>> inserted = new LinkedHashMap<>();

    private ConfigurationTables(Configuration conf) {
        this.conf = conf;
        this.id = conf.getId();
    }

    /**
     * @param conf     a configuração editada, com o ID_CONFIGURATION do cenário
     * @param user     dono da configuração (CD_USER)
     * @param now      data da gravação (DT_CONFIGURATION)
     * @param original as tabelas do cenário antes da edição
     * @return todas as tabelas do esquema, em ordem de nome, como o export do banco as devolveria
     * @throws IllegalArgumentException se o banco recusaria a gravação (nulo numa coluna NOT NULL,
     *                                  chave primária repetida, tabela ou coluna desconhecida)
     */
    public static Map<String, TableData> write(Configuration conf, String user, LocalDateTime now,
                                               Map<String, TableData> original) {
        ConfigurationTables w = new ConfigurationTables(conf);
        w.configuration(user, now);
        w.interfaceParameters();
        w.muscles("HILL_TYPE_MUSCULOTENDON_PARAMETERS");
        w.nerves();
        w.motorUnitTypes();
        w.neuronTypes();
        w.synapseTypes();
        w.markovTypes();
        w.dynamicTypes();
        w.injectedCurrents();
        w.miscellaneous();
        w.muscles("NOTHING");
        return w.export(original == null ? Map.of() : original);
    }

    private Map<String, TableData> export(Map<String, TableData> original) {
        Map<String, TableData> result = new LinkedHashMap<>();
        for (LegacySchema.Table table : LegacySchema.tables()) {
            if (table.name().equals("USER")) {
                continue;
            }
            List<List<Object>> rows = new ArrayList<>();
            // o que estava no cenário e o DAO não apagou
            TableData before = original.get(table.name());
            if (before != null && !WRITTEN.contains(table.name())) {
                int idColumn = before.columns().indexOf("ID_CONFIGURATION");
                for (List<Object> raw : before.rows()) {
                    Map<String, Object> values = new LinkedHashMap<>();
                    for (int i = 0; i < before.columns().size(); i++) {
                        values.put(before.columns().get(i), i < raw.size() ? raw.get(i) : null);
                    }
                    List<Object> row = LegacySchema.row(table, values);
                    boolean ofThisConfiguration = GLOBAL.contains(table.name()) || idColumn < 0
                            || ((Number) row.get(table.indexOf("ID_CONFIGURATION"))).longValue() == id;
                    if (ofThisConfiguration) {
                        rows.add(row);
                    }
                }
            }
            for (Map<String, Object> values : inserted.getOrDefault(table.name(), List.of())) {
                rows.add(LegacySchema.row(table, values));
            }
            rows = LegacySchema.inStoredOrder(table, rows);
            result.put(table.name(), new TableData(table.columnNames(), rows));
        }
        for (String name : original.keySet()) {
            if (LegacySchema.table(name).isEmpty() && !original.get(name).rows().isEmpty()) {
                throw new IllegalArgumentException("tabela desconhecida: " + name);
            }
        }
        return result;
    }

    private Map<String, Object> insert(String table) {
        Map<String, Object> row = new LinkedHashMap<>();
        inserted.computeIfAbsent(table, k -> new ArrayList<>()).add(row);
        return row;
    }

    // ---- os store* do ConfigurationDAO original, na mesma ordem --------------------------------

    private void configuration(String user, LocalDateTime now) {
        Map<String, Object> r = insert("CONFIGURATION");
        r.put("ID_CONFIGURATION", id);
        r.put("NM_CONFIGURATION", conf.getName());
        r.put("DS_CONFIGURATION", conf.getDescription());
        r.put("CD_USER", user);
        r.put("DT_CONFIGURATION", now.withNano(0));
        r.put("TFIN", conf.getTFin());
        r.put("STEP", conf.getStep());
        r.put("MERGE", conf.isMerge());
        r.put("KEEP_PROPERTIES", conf.isKeepProperties());
        r.put("STORE_RESULTS", conf.isStoreResults());
        r.put("STORE_SIGNALS", conf.isStoreSignals());
    }

    private void interfaceParameters() {
        Map<String, Object> r = insert("INTERFACE_PARAMETERS");
        r.put("ID_CONFIGURATION", id);
        r.put("CD_NUCLEUS", conf.getCdNucleus());
    }

    /** HILL_TYPE_MUSCULOTENDON_PARAMETERS, e a mesma coisa em NOTHING (que ninguém lê). */
    private void muscles(String table) {
        for (Object o : conf.getMuscles()) {
            MuscleVO muscle = (MuscleVO) o;
            Map<String, Object> r = insert(table);
            r.put("ID_CONFIGURATION", id);
            r.put("CD_MUSCLE", muscle.getCdMuscle());
            r.put("OPTIMAL_LENGTH", muscle.getOptimalLength());
            r.put("VISCOSITY_COEFICIENT", muscle.getViscosityCoeficient());
            r.put("ELASTIC_COEF_PARALLEL", muscle.getElasticCoeficientOfParallelElement());
            r.put("C_T", muscle.getC_T());
            r.put("K_T", muscle.getK_T());
            r.put("LR_T", muscle.getLR_T());
            r.put("PENNATION_ANGLE", muscle.getPennationAngle());
            r.put("SLACK_TENDON_LENGTH", muscle.getSlackTendonLength());
            r.put("MAXIMUM_MUSCLE_FORCE", muscle.getMaximumMuscleForce());
            r.put("MUSCLE_MASS", muscle.getMuscleMass());
            r.put("INDEX", muscle.getIndex());
        }
    }

    private void nerves() {
        for (Object o : conf.getAllNerves()) {
            NerveVO nerve = (NerveVO) o;
            Map<String, Object> r = insert("NERVE");
            r.put("ID_CONFIGURATION", id);
            r.put("CD_NERVE", nerve.getCdNerve());
            r.put("CD_JOINT", nerve.getCdJoint());
            r.put("ACTIVE", nerve.isActive());
            r.put("STIMULUS_SPINAL_ENTRY", nerve.getStimulusSpinalEntry());
            r.put("STIMULUS_END_PLATE", nerve.getStimulusEndPlate());
            r.put("INDEX", nerve.getIndex());
            r.put("START", nerve.getTini());
            r.put("STOP", nerve.getTfin());
            r.put("PEAK", nerve.getAmp());
            r.put("FREQUENCY", nerve.getFreq());
            r.put("CD_SIGNAL", nerve.getCdSignal());
            r.put("TINI", nerve.getModulating_tini());
            r.put("TFIN", nerve.getModulating_tfin());
            r.put("AMP", nerve.getModulating_amp());
            r.put("FREQ", nerve.getModulating_freq());
            r.put("WIDTH", nerve.getWidth());
            r.put("DELAY", nerve.getDelay());
        }
    }

    /** O DAO original não gravava o INDEX dos tipos de unidade motora: fica nulo. */
    private void motorUnitTypes() {
        for (Object o : conf.getMotorunitsTypes()) {
            MotorUnitVO mu = (MotorUnitVO) o;
            Map<String, Object> r = insert("MOTORUNIT_TYPE");
            r.put("ID_CONFIGURATION", id);
            r.put("CD_MOTORUNIT_TYPE", mu.getType());
            r.put("CD_NUCLEUS", mu.getCdNucleus());
            r.put("TWITCH_TENSION1", mu.getTwitchTension1());
            r.put("TWITCH_TENSION2", mu.getTwitchTension2());
            r.put("TETANIC_TENSION1", mu.getMaxTension1());
            r.put("TETANIC_TENSION2", mu.getMaxTension2());
            r.put("TAU_FORCE1", mu.getContractionTime1());
            r.put("TAU_FORCE2", mu.getContractionTime2());
            r.put("AMP_EMG1", mu.getAmpEMG1());
            r.put("AMP_EMG2", mu.getAmpEMG2());
            r.put("DUR_EMG1", mu.getLambdaEMG1());
            r.put("DUR_EMG2", mu.getLambdaEMG2());
        }
    }

    /**
     * NEURON_TYPE e, para cada trato descendente, a linha de DESCENDING_COMMANDS com o sinal dele.
     * CD_NUCLEUS_PRE não era gravado (fica nulo); MEAN e STD (a ordem do processo) vão para colunas
     * inteiras e são truncados.
     */
    private void neuronTypes() {
        for (Object o : conf.getNeuronTypes()) {
            NeuronVO neu = (NeuronVO) o;
            Map<String, Object> r = insert("NEURON_TYPE");
            r.put("ID_CONFIGURATION", id);
            r.put("CD_NEURON_TYPE", neu.getType());
            r.put("CD_CATEGORY", neu.getCategory());
            r.put("CD_NUCLEUS", neu.getCdNucleus());
            r.put("THRESHOLD1", neu.getThreshold1());
            r.put("THRESHOLD2", neu.getThreshold2());
            r.put("RM_DEND1", neu.getRmDend1());
            r.put("RM_DEND2", neu.getRmDend2());
            r.put("RM_SOMA1", neu.getRmSoma1());
            r.put("RM_SOMA2", neu.getRmSoma2());
            r.put("D_DEND1", neu.getDdend1());
            r.put("D_DEND2", neu.getDdend2());
            r.put("L_DEND1", neu.getLdend1());
            r.put("L_DEND2", neu.getLdend2());
            r.put("D_SOMA1", neu.getDsoma1());
            r.put("D_SOMA2", neu.getDsoma2());
            r.put("L_SOMA1", neu.getLsoma1());
            r.put("L_SOMA2", neu.getLsoma2());
            r.put("TOTAL_AREA", neu.getTotalArea());
            r.put("DISTRIBUTION", neu.getDistribution());
            r.put("QUANTITY", neu.getQuantity());
            r.put("MEAN", (double) neu.getMean());
            r.put("STD", (double) neu.getOrder());
            r.put("AXON_VELOCITY1", neu.getAxonVelocity1());
            r.put("AXON_VELOCITY2", neu.getAxonVelocity2());
            r.put("AXON_THRESHOLD1", neu.getAxonThreshold1());
            r.put("AXON_THRESHOLD2", neu.getAxonThreshold2());
            r.put("CM", neu.getCm());
            r.put("RI", neu.getRi());
            r.put("CD_NERVE", neu.getCdNerve());
            r.put("INDEX", neu.getIndex());
            r.put("ACTIVE", neu.isActive());
            r.put("THRESHOLDCA1", neu.getThresholdCa1());
            r.put("THRESHOLDCA2", neu.getThresholdCa2());

            if (neu.getCategory().equals(ReMoto.TR)) {
                Map<String, Object> s = insert("DESCENDING_COMMANDS");
                s.put("ID_CONFIGURATION", id);
                s.put("CD_NUCLEUS", neu.getCdNucleus());
                s.put("CD_NEURON_TYPE", neu.getType());
                s.put("CD_SIGNAL", neu.getCdSignal());
                s.put("MOD_TYPE", neu.getModType());
                s.put("TINI", neu.getIni());
                s.put("TFIN", neu.getFin());
                s.put("AMP", neu.getAmp());
                s.put("FREQ", neu.getFreq());
                s.put("WIDTH", neu.getWidth());
                s.put("DELAY", neu.getDelay());
            }
        }
    }

    private void synapseTypes() {
        for (Object o : conf.getSynapseTypes().values()) {
            ConductanceVO g = (ConductanceVO) o;
            Map<String, Object> r = insert("CONDUCTANCE_TYPE");
            r.put("CD_CONDUCTANCE_TYPE", g.getCdConductanceType());
            r.put("ID_CONFIGURATION", id);
            r.put("CD_NUCLEUS", g.getCdNucleus());
            r.put("CD_NUCLEUS_PRE", g.getCdNucleusPre());
            r.put("GMAX", g.getGmax());
            r.put("E", g.getE());
            r.put("CONNECTIVITY", g.getConnectivity());
            r.put("COMPARTMENT", g.getCompartment());
            r.put("ACTIVE", g.isActive());
            r.put("DELAY", g.getDelay());
            r.put("INDEX", g.getIndex());
        }
    }

    private void markovTypes() {
        for (Object o : conf.getMarcovTypes().values()) {
            ConductanceVO g = (ConductanceVO) o;
            Map<String, Object> r = insert("MARKOV_TYPE");
            r.put("CD_MARKOV_TYPE", g.getCdConductanceType());
            r.put("ID_CONFIGURATION", id);
            r.put("CD_NUCLEUS", g.getCdNucleus());
            r.put("E", g.getE());
            r.put("GMAX", g.getGmax());
            r.put("TPEAK", g.getTpeak());
            r.put("TMAX", g.getTmax());
            r.put("ALPHA", g.getAlpha());
            r.put("BETA", g.getBeta());
            r.put("INDEX", g.getIndex());
        }
    }

    private void dynamicTypes() {
        for (Object o : conf.getDynamicTypes()) {
            DynamicVO vo = (DynamicVO) o;
            Map<String, Object> r = insert("SYNAPTIC_DYNAMICS");
            r.put("ID_CONFIGURATION", id);
            r.put("CD_NUCLEUS", vo.getCdNucleus());
            r.put("CD_NUCLEUS_PRE", vo.getCdNucleusPre());
            r.put("CD_CONDUCTANCE_TYPE", vo.getCdConductanceType());
            r.put("DYNAMIC_TYPE", vo.getDynamicType());
            r.put("TAU", vo.getTau());
            r.put("VARIATION", vo.getVariation());
        }
    }

    private void injectedCurrents() {
        for (Object o : conf.getInjectedCurrents()) {
            Current inj = (Current) o;
            Map<String, Object> r = insert("INJECTED_CURRENT");
            r.put("ID_CONFIGURATION", id);
            r.put("CD_NUCLEUS", inj.getCdNucleus());
            r.put("CD_NEURON_TYPE", inj.getCdNeuronType());
            r.put("CD_SIGNAL", inj.getCdSignal());
            r.put("TINI", inj.getIni());
            r.put("TFIN", inj.getFin());
            r.put("AMP", inj.getAmp());
            r.put("FREQ", inj.getFreq());
            r.put("WIDTH", inj.getWidth());
            r.put("COMPARTMENT", inj.getCompartment());
            r.put("IMAX", inj.getImax());
            r.put("ACTIVE", inj.isActive());
            r.put("INDEX", inj.getIndex());
            r.put("DELAY", inj.getDelay());
        }
    }

    private void miscellaneous() {
        for (Object o : conf.getMiscellaneous()) {
            MiscellaneousVO vo = (MiscellaneousVO) o;
            Map<String, Object> r = insert("MISCELLANEOUS");
            r.put("ID_CONFIGURATION", id);
            r.put("PROPERTY", vo.getProperty());
            r.put("DESCRIPTION", vo.getDescription());
            r.put("VALUE", vo.getValue());
            r.put("INDEX", vo.getIndex());
            r.put("DIVISION", vo.isDivision());
        }
    }
}
