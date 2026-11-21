package br.remoto.core.model.config;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Lê as tabelas do banco original (o campo {@code legacy.tables} de um cenário) para um
 * {@link ModelConfig}, com o mesmo resultado que o {@code ConfigurationDAO} original obtinha do
 * HSQLDB.
 *
 * <p>Por isso reproduz o que o banco fazia com os valores e com a ordem das linhas:
 * <ul>
 * <li>{@code NUMERIC(128,4)} arredonda para 4 casas, com a metade para baixo: 0,12345 vira 0,1234
 * e 0,12347 vira 0,1235. Um decimal numa coluna {@code INTEGER} é truncado ({@code SQL CONVERT
 * TRUNCATE}): 2,7 vira 2.</li>
 * <li>Nulo vira 0, {@code false} ou {@code null}, como {@code getDouble}, {@code getInt},
 * {@code getBoolean} e {@code getString} do JDBC.</li>
 * <li>A ordem é a do {@code ORDER BY} de cada consulta do DAO, com nulos primeiro; os empates
 * ficam na ordem da chave primária, que é a ordem em que o banco percorre a tabela, ou na ordem
 * das linhas quando a tabela não tem chave. Texto se compara por código de caractere.</li>
 * <li>Coluna desconhecida ou chave primária repetida são erro, como eram no {@code INSERT}.</li>
 * </ul>
 */
public final class LegacyTablesReader {

    private enum Type { TEXT, INTEGER, NUMERIC, OTHER }

    private record Order(String column, boolean descending) {
    }

    /**
     * Uma tabela do banco original: colunas e tipos (do esquema), chave primária, se a consulta
     * do DAO filtra por ID_CONFIGURATION, e o ORDER BY dela.
     */
    private record Spec(String name, Map<String, Type> columns, List<String> primaryKey,
                        boolean byConfiguration, List<Order> orderBy) {
    }

    private LegacyTablesReader() {
    }

    public static ModelConfig read(int idConfiguration, Map<String, TableData> tables) {
        Map<String, TableData> t = tables == null ? Map.of() : tables;
        checkTableNames(t);
        return new ModelConfig(idConfiguration,
                rows(CONFIGURATION, t, idConfiguration, r -> new SimulationParams(
                        r.integer("ID_CONFIGURATION"), r.text("CD_USER"), r.text("NM_CONFIGURATION"),
                        r.text("DS_CONFIGURATION"), r.number("TFIN"), r.number("STEP"), r.flag("MERGE"),
                        r.flag("KEEP_PROPERTIES"), r.flag("STORE_RESULTS"), r.flag("STORE_SIGNALS"))),
                rows(JOINTS, t, idConfiguration, r -> new JointParams(
                        r.text("CD_JOINT"), r.text("NM_JOINT"), r.integer("NUM_NUCLEI"), r.integer("INDEX"))),
                rows(NUCLEUS, t, idConfiguration, r -> new NucleusParams(
                        r.text("CD_NUCLEUS"), r.text("NM_NUCLEUS"), r.text("LAYER"), r.integer("INDEX"),
                        r.text("JOINT"))),
                rows(CATEGORY, t, idConfiguration, r -> new CategoryParams(
                        r.text("CD_CATEGORY"), r.text("NM_CATEGORY"))),
                rows(NERVE, t, idConfiguration, r -> new NerveParams(
                        r.text("CD_NERVE"), r.text("CD_JOINT"), r.number("STIMULUS_SPINAL_ENTRY"),
                        r.number("STIMULUS_END_PLATE"), r.flag("ACTIVE"), r.integer("INDEX"), r.number("START"),
                        r.number("STOP"), r.number("PEAK"), r.number("FREQUENCY"), r.text("CD_SIGNAL"),
                        r.number("TINI"), r.number("TFIN"), r.number("AMP"), r.number("FREQ"), r.number("WIDTH"),
                        r.number("DELAY"))),
                rows(DESCENDING_COMMANDS, t, idConfiguration, r -> new DescendingCommandParams(
                        r.text("CD_NUCLEUS"), r.text("CD_NEURON_TYPE"), r.text("CD_SIGNAL"), r.text("MOD_TYPE"),
                        r.number("TINI"), r.number("TFIN"), r.number("AMP"), r.number("FREQ"), r.number("WIDTH"),
                        r.number("DELAY"))),
                rows(INJECTED_CURRENT, t, idConfiguration, r -> new InjectedCurrentParams(
                        r.text("CD_NUCLEUS"), r.text("CD_NEURON_TYPE"), r.text("COMPARTMENT"), r.number("IMAX"),
                        r.text("CD_SIGNAL"), r.number("TINI"), r.number("TFIN"), r.number("AMP"), r.number("FREQ"),
                        r.number("WIDTH"), r.flag("ACTIVE"), r.integer("INDEX"), r.number("DELAY"))),
                rows(CONDUCTANCE_TYPE, t, idConfiguration, r -> new SynapseParams(
                        r.text("CD_CONDUCTANCE_TYPE"), r.text("CD_NUCLEUS"), r.text("CD_NUCLEUS_PRE"),
                        r.number("GMAX"), r.number("E"), r.number("CONNECTIVITY"), r.number("DELAY"),
                        r.text("COMPARTMENT"), r.flag("ACTIVE"), r.integer("INDEX"))),
                rows(MARKOV_TYPE, t, idConfiguration, r -> new IonicChannelParams(
                        r.text("CD_MARKOV_TYPE"), r.text("CD_NUCLEUS"), r.number("TPEAK"), r.number("TMAX"),
                        r.number("ALPHA"), r.number("BETA"), r.number("GMAX"), r.number("E"), r.integer("INDEX"))),
                rows(SYNAPTIC_DYNAMICS, t, idConfiguration, r -> new SynapticDynamicsParams(
                        r.text("CD_NUCLEUS"), r.text("CD_NUCLEUS_PRE"), r.text("CD_CONDUCTANCE_TYPE"),
                        r.text("DYNAMIC_TYPE"), r.number("TAU"), r.number("VARIATION"))),
                rows(NEURON_TYPE, t, idConfiguration, r -> new NeuronTypeParams(
                        r.text("CD_NEURON_TYPE"), r.text("CD_NUCLEUS"), r.text("CD_NUCLEUS_PRE"),
                        r.text("CD_CATEGORY"), r.number("THRESHOLD1"), r.number("THRESHOLD2"),
                        r.number("AXON_VELOCITY1"), r.number("AXON_VELOCITY2"), r.number("AXON_THRESHOLD1"),
                        r.number("AXON_THRESHOLD2"), r.number("RM_SOMA1"), r.number("RM_SOMA2"),
                        r.number("RM_DEND1"), r.number("RM_DEND2"), r.number("D_DEND1"), r.number("D_DEND2"),
                        r.number("L_DEND1"), r.number("L_DEND2"), r.number("D_SOMA1"), r.number("D_SOMA2"),
                        r.number("L_SOMA1"), r.number("L_SOMA2"), r.number("CM"), r.number("RI"),
                        r.number("TOTAL_AREA"), r.text("DISTRIBUTION"), r.text("CD_NERVE"), r.integer("MEAN"),
                        r.integer("STD"), r.integer("QUANTITY"), r.flag("ACTIVE"), r.integer("INDEX"),
                        r.number("THRESHOLDCA1"), r.number("THRESHOLDCA2"))),
                rows(MOTORUNIT_TYPE, t, idConfiguration, r -> new MotorUnitTypeParams(
                        r.text("CD_MOTORUNIT_TYPE"), r.text("CD_NUCLEUS"), r.number("TWITCH_TENSION1"),
                        r.number("TWITCH_TENSION2"), r.number("TETANIC_TENSION1"), r.number("TETANIC_TENSION2"),
                        r.number("TAU_FORCE1"), r.number("TAU_FORCE2"), r.number("AMP_EMG1"), r.number("AMP_EMG2"),
                        r.number("DUR_EMG1"), r.number("DUR_EMG2"), r.integer("INDEX"))),
                rows(MISCELLANEOUS, t, idConfiguration, r -> new MiscellaneousParam(
                        r.text("PROPERTY"), r.text("DESCRIPTION"), r.number("VALUE"), r.integer("INDEX"),
                        r.flag("DIVISION"))),
                rows(MU_POSITION, t, idConfiguration, r -> new MotorUnitPosition(
                        r.text("CD_NUCLEUS"), r.text("CD_MU"), r.number("POSITION_Y"), r.number("POSITION_Z"))),
                rows(HILL, t, idConfiguration, r -> new HillMuscleParams(
                        r.text("CD_MUSCLE"), r.number("OPTIMAL_LENGTH"), r.number("VISCOSITY_COEFICIENT"),
                        r.number("ELASTIC_COEF_PARALLEL"), r.number("C_T"), r.number("K_T"), r.number("LR_T"),
                        r.number("PENNATION_ANGLE"), r.number("SLACK_TENDON_LENGTH"),
                        r.number("MAXIMUM_MUSCLE_FORCE"), r.number("MUSCLE_MASS"), r.integer("INDEX"))),
                rows(INTERFACE_PARAMETERS, t, idConfiguration, r -> new InterfaceParams(r.text("CD_NUCLEUS"))));
    }

    // ---- esquema do banco original e consultas do ConfigurationDAO ----------------------------

    private static final Spec CONFIGURATION = spec("CONFIGURATION",
            "ID_CONFIGURATION:I CD_USER:S NM_CONFIGURATION:S DS_CONFIGURATION:S DT_CONFIGURATION:O TFIN:N STEP:N "
                    + "MERGE:I KEEP_PROPERTIES:I STORE_RESULTS:I STORE_SIGNALS:I",
            "ID_CONFIGURATION", true, "NM_CONFIGURATION");
    private static final Spec JOINTS = spec("JOINTS",
            "CD_JOINT:S NM_JOINT:S NUM_NUCLEI:I INDEX:I", "CD_JOINT", false, "INDEX");
    private static final Spec NUCLEUS = spec("NUCLEUS",
            "CD_NUCLEUS:S NM_NUCLEUS:S LAYER:S INDEX:I JOINT:S", "CD_NUCLEUS", false, "INDEX");
    private static final Spec CATEGORY = spec("CATEGORY",
            "CD_CATEGORY:S NM_CATEGORY:S", "CD_CATEGORY", false, "NM_CATEGORY desc");
    private static final Spec MISCELLANEOUS = spec("MISCELLANEOUS",
            "PROPERTY:S ID_CONFIGURATION:I DESCRIPTION:S VALUE:N INDEX:I DIVISION:I",
            "PROPERTY ID_CONFIGURATION", true, "INDEX");
    private static final Spec NERVE = spec("NERVE",
            "CD_NERVE:S CD_JOINT:S ID_CONFIGURATION:I STIMULUS_SPINAL_ENTRY:N STIMULUS_END_PLATE:N ACTIVE:I INDEX:I "
                    + "START:N STOP:N PEAK:N FREQUENCY:N CD_SIGNAL:S TINI:N TFIN:N AMP:N FREQ:N WIDTH:N DELAY:N",
            "CD_NERVE ID_CONFIGURATION", true, "INDEX");
    private static final Spec HILL = spec("HILL_TYPE_MUSCULOTENDON_PARAMETERS",
            "ID_CONFIGURATION:I CD_MUSCLE:S OPTIMAL_LENGTH:N VISCOSITY_COEFICIENT:N ELASTIC_COEF_PARALLEL:N C_T:N "
                    + "K_T:N LR_T:N PENNATION_ANGLE:N SLACK_TENDON_LENGTH:N MAXIMUM_MUSCLE_FORCE:N MUSCLE_MASS:N INDEX:I",
            "", true, "INDEX");
    private static final Spec CONDUCTANCE_TYPE = spec("CONDUCTANCE_TYPE",
            "CD_CONDUCTANCE_TYPE:S ID_CONFIGURATION:I CD_NUCLEUS:S CD_NUCLEUS_PRE:S GMAX:N E:N CONNECTIVITY:N "
                    + "DELAY:N COMPARTMENT:S ACTIVE:I INDEX:I",
            "CD_CONDUCTANCE_TYPE ID_CONFIGURATION CD_NUCLEUS CD_NUCLEUS_PRE", true, "COMPARTMENT INDEX");
    private static final Spec NEURON_TYPE = spec("NEURON_TYPE",
            "CD_NEURON_TYPE:S ID_CONFIGURATION:I CD_NUCLEUS:S CD_NUCLEUS_PRE:S CD_CATEGORY:S THRESHOLD1:N "
                    + "THRESHOLD2:N AXON_VELOCITY1:N AXON_VELOCITY2:N AXON_THRESHOLD1:N AXON_THRESHOLD2:N RM_SOMA1:N "
                    + "RM_SOMA2:N RM_DEND1:N RM_DEND2:N D_DEND1:N D_DEND2:N L_DEND1:N L_DEND2:N D_SOMA1:N D_SOMA2:N "
                    + "L_SOMA1:N L_SOMA2:N CM:N RI:N TOTAL_AREA:N DISTRIBUTION:S CD_NERVE:S MEAN:I STD:I QUANTITY:I "
                    + "ACTIVE:I INDEX:I THRESHOLDCA1:N THRESHOLDCA2:N",
            "CD_NEURON_TYPE ID_CONFIGURATION CD_NUCLEUS", true, "CD_NUCLEUS INDEX");
    private static final Spec MARKOV_TYPE = spec("MARKOV_TYPE",
            "CD_MARKOV_TYPE:S ID_CONFIGURATION:I CD_NUCLEUS:S TPEAK:N TMAX:N ALPHA:N BETA:N GMAX:N E:N INDEX:I",
            "CD_MARKOV_TYPE ID_CONFIGURATION CD_NUCLEUS INDEX", true, "INDEX");
    private static final Spec MOTORUNIT_TYPE = spec("MOTORUNIT_TYPE",
            "CD_MOTORUNIT_TYPE:S ID_CONFIGURATION:I CD_NUCLEUS:S TWITCH_TENSION1:N TWITCH_TENSION2:N "
                    + "TETANIC_TENSION1:N TETANIC_TENSION2:N TAU_FORCE1:N TAU_FORCE2:N AMP_EMG1:N AMP_EMG2:N "
                    + "DUR_EMG1:N DUR_EMG2:N INDEX:I",
            "CD_MOTORUNIT_TYPE ID_CONFIGURATION CD_NUCLEUS", true, "CD_MOTORUNIT_TYPE INDEX");
    private static final Spec SYNAPTIC_DYNAMICS = spec("SYNAPTIC_DYNAMICS",
            "ID_CONFIGURATION:I CD_NUCLEUS:S CD_NUCLEUS_PRE:S CD_CONDUCTANCE_TYPE:S DYNAMIC_TYPE:S TAU:N VARIATION:N",
            "ID_CONFIGURATION CD_NUCLEUS CD_NUCLEUS_PRE CD_CONDUCTANCE_TYPE", true, "");
    private static final Spec MU_POSITION = spec("MU_POSITION",
            "CD_NUCLEUS:S CD_MU:S POSITION_Y:N POSITION_Z:N", "CD_NUCLEUS CD_MU", false, "");
    private static final Spec INTERFACE_PARAMETERS = spec("INTERFACE_PARAMETERS",
            "ID_CONFIGURATION:I CD_NUCLEUS:S", "ID_CONFIGURATION", true, "");
    private static final Spec DESCENDING_COMMANDS = spec("DESCENDING_COMMANDS",
            "ID_CONFIGURATION:I CD_NUCLEUS:S CD_NEURON_TYPE:S CD_SIGNAL:S MOD_TYPE:S TINI:N TFIN:N AMP:N FREQ:N "
                    + "WIDTH:N DELAY:N",
            "ID_CONFIGURATION CD_NUCLEUS CD_NEURON_TYPE", true, "CD_NUCLEUS");
    private static final Spec INJECTED_CURRENT = spec("INJECTED_CURRENT",
            "ID_CONFIGURATION:I CD_NUCLEUS:S CD_NEURON_TYPE:S COMPARTMENT:S IMAX:N CD_SIGNAL:S TINI:N TFIN:N AMP:N "
                    + "FREQ:N WIDTH:N ACTIVE:I INDEX:I DELAY:N",
            "ID_CONFIGURATION CD_NUCLEUS CD_NEURON_TYPE", true, "CD_NUCLEUS INDEX");

    /** Tabelas que o DAO não lê, mas que existem no cenário: aceitas e ignoradas. */
    private static final Set<String> IGNORED = Set.of("NOTHING", "SCENARIO_MODELS");

    /** Todas as tabelas conhecidas, para recusar nomes desconhecidos como o INSERT recusava. */
    private static final Set<String> KNOWN = new HashSet<>(List.of(CONFIGURATION.name, JOINTS.name, NUCLEUS.name,
            CATEGORY.name, MISCELLANEOUS.name, NERVE.name, HILL.name, CONDUCTANCE_TYPE.name, NEURON_TYPE.name,
            MARKOV_TYPE.name, MOTORUNIT_TYPE.name, SYNAPTIC_DYNAMICS.name, MU_POSITION.name,
            INTERFACE_PARAMETERS.name, DESCENDING_COMMANDS.name, INJECTED_CURRENT.name));

    /** Tabela desconhecida com linhas é erro, como era o INSERT; vazia era ignorada. */
    private static void checkTableNames(Map<String, TableData> tables) {
        for (Map.Entry<String, TableData> e : tables.entrySet()) {
            String name = e.getKey();
            if (e.getValue() != null && !e.getValue().rows().isEmpty()
                    && !KNOWN.contains(name) && !IGNORED.contains(name)) {
                throw new IllegalArgumentException("tabela desconhecida: " + name);
            }
        }
    }

    private static Spec spec(String name, String columns, String primaryKey, boolean byConfiguration,
                             String orderBy) {
        Map<String, Type> types = new LinkedHashMap<>();
        for (String c : columns.split(" ")) {
            String[] p = c.split(":");
            types.put(p[0], switch (p[1]) {
                case "S" -> Type.TEXT;
                case "I" -> Type.INTEGER;
                case "N" -> Type.NUMERIC;
                default -> Type.OTHER;
            });
        }
        List<String> pk = primaryKey.isBlank() ? List.of() : List.of(primaryKey.split(" "));
        List<Order> order = new ArrayList<>();
        if (!orderBy.isBlank()) {
            String[] p = orderBy.split(" ");
            for (int i = 0; i < p.length; i++) {
                boolean desc = i + 1 < p.length && p[i + 1].equals("desc");
                order.add(new Order(p[i], desc));
                if (desc) {
                    i++;
                }
            }
        }
        return new Spec(name, types, pk, byConfiguration, order);
    }

    // ---- leitura --------------------------------------------------------------------------------

    private static <T> List<T> rows(Spec spec, Map<String, TableData> tables, int idConfiguration,
                                    Function<Row, T> mapper) {
        TableData data = tables.get(spec.name);
        if (data == null || data.rows().isEmpty()) {
            return List.of();
        }
        for (String column : data.columns()) {
            if (!spec.columns.containsKey(column)) {
                throw new IllegalArgumentException("coluna desconhecida em " + spec.name + ": " + column);
            }
        }

        List<Map<String, Object>> converted = new ArrayList<>(data.rows().size());
        Set<List<Object>> keys = new HashSet<>();
        for (List<Object> raw : data.rows()) {
            Map<String, Object> row = new HashMap<>();
            for (int i = 0; i < data.columns().size(); i++) {
                String column = data.columns().get(i);
                Object value = i < raw.size() ? raw.get(i) : null;
                row.put(column, convert(spec.columns.get(column), value, spec.name, column));
            }
            if (!spec.primaryKey.isEmpty()) {
                List<Object> key = spec.primaryKey.stream().map(row::get).toList();
                if (!keys.add(key)) {
                    throw new IllegalArgumentException("chave primária repetida em " + spec.name + ": " + key);
                }
            }
            converted.add(row);
        }

        // ordem em que o banco percorre a tabela: a da chave primária, ou a de inserção
        if (!spec.primaryKey.isEmpty()) {
            converted.sort(comparing(spec.primaryKey.stream().map(c -> new Order(c, false)).toList()));
        }
        if (spec.byConfiguration) {
            converted.removeIf(row -> !Long.valueOf(idConfiguration).equals(row.get("ID_CONFIGURATION")));
        }
        // ORDER BY da consulta do DAO, com o algoritmo do HSQLDB: ele não é estável, e a ordem dos
        // empates (tratos de um mesmo núcleo, por exemplo) decide a ordem de criação dos neurônios
        if (!spec.orderBy.isEmpty()) {
            hsqldbSort(converted, comparing(spec.orderBy));
        }

        List<T> result = new ArrayList<>(converted.size());
        for (Map<String, Object> row : converted) {
            result.add(mapper.apply(new Row(row)));
        }
        return result;
    }

    /**
     * O mesmo algoritmo de {@code org.hsqldb.lib.ArraySort.sort} (HSQLDB 2.7.3), que o banco usa
     * no ORDER BY: quicksort com mediana de três nos trechos com mais de 16 linhas, depois
     * inserção. Os empates saem numa ordem que depende do algoritmo, por isso ele é copiado.
     */
    static <T> void hsqldbSort(List<T> rows, Comparator<? super T> c) {
        int n = rows.size();
        if (n < 2) {
            return;
        }
        @SuppressWarnings("unchecked")
        T[] a = (T[]) rows.toArray();
        quickSort(a, c, 0, n - 1);
        insertionSort(a, c, 0, n - 1);
        for (int i = 0; i < n; i++) {
            rows.set(i, a[i]);
        }
    }

    private static <T> void quickSort(T[] a, Comparator<? super T> c, int l, int r) {
        if (r - l <= 16) {
            return;
        }
        int i = (r + l) >>> 1;
        if (c.compare(a[i], a[l]) < 0) {
            swap(a, l, i);
        }
        if (c.compare(a[r], a[l]) < 0) {
            swap(a, l, r);
        }
        if (c.compare(a[r], a[i]) < 0) {
            swap(a, i, r);
        }
        int j = r - 1;
        swap(a, i, j);
        i = l;
        int v = j;
        while (true) {
            while (c.compare(a[++i], a[v]) < 0) {
                // avança
            }
            while (c.compare(a[v], a[--j]) < 0) {
                // recua
            }
            if (j < i) {
                break;
            }
            swap(a, i, j);
        }
        swap(a, i, r - 1);
        quickSort(a, c, l, j);
        quickSort(a, c, i + 1, r);
    }

    private static <T> void insertionSort(T[] a, Comparator<? super T> c, int lo, int hi) {
        for (int i = lo + 1; i <= hi; i++) {
            int j = i;
            while (j > lo && c.compare(a[i], a[j - 1]) < 0) {
                j--;
            }
            if (i != j) {
                T moved = a[i];
                System.arraycopy(a, j, a, j + 1, i - j);
                a[j] = moved;
            }
        }
    }

    private static <T> void swap(T[] a, int i, int j) {
        T t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    private static Comparator<Map<String, Object>> comparing(List<Order> order) {
        Comparator<Map<String, Object>> comparator = (a, b) -> 0;
        for (Order o : order) {
            Comparator<Map<String, Object>> c = (a, b) -> compareValues(a.get(o.column), b.get(o.column));
            comparator = comparator.thenComparing(o.descending ? c.reversed() : c);
        }
        return comparator;
    }

    /** Nulo é o menor valor; texto por código de caractere; números pelo valor. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareValues(Object a, Object b) {
        if (a == null || b == null) {
            return a == null ? (b == null ? 0 : -1) : 1;
        }
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) {
            return x.compareTo(y);
        }
        return ((Comparable) a).compareTo(b);
    }

    /** Converte para o tipo da coluna: String, Long (INTEGER, truncado) ou BigDecimal (NUMERIC, 4 casas). */
    private static Object convert(Type type, Object value, String table, String column) {
        if (value == null) {
            return null;
        }
        return switch (type) {
            case TEXT -> value instanceof BigDecimal d ? d.toPlainString() : value.toString();
            case INTEGER -> decimal(value, table, column).setScale(0, RoundingMode.DOWN).longValueExact();
            case NUMERIC -> decimal(value, table, column).setScale(4, RoundingMode.HALF_DOWN);
            case OTHER -> value;
        };
    }

    private static BigDecimal decimal(Object value, String table, String column) {
        if (value instanceof BigDecimal d) {
            return d;
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return BigDecimal.valueOf(((Number) value).longValue());
        }
        if (value instanceof BigInteger i) {
            return new BigDecimal(i);
        }
        if (value instanceof Boolean b) {
            return b ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (value instanceof Double || value instanceof Float) {
            // o mesmo que o Jackson faz ao ler um número de ponto flutuante como BigDecimal
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        if (value instanceof String s) {
            try {
                return new BigDecimal(s.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("valor não numérico em " + table + "." + column + ": " + s, e);
            }
        }
        throw new IllegalArgumentException("valor inválido em " + table + "." + column + ": " + value);
    }

    /** Uma linha já convertida, lida como o DAO lia: nulo vira 0, false ou null. */
    private record Row(Map<String, Object> values) {

        String text(String column) {
            return (String) values.get(column);
        }

        double number(String column) {
            Object v = values.get(column);
            return v == null ? 0.0 : ((BigDecimal) v).doubleValue();
        }

        int integer(String column) {
            Object v = values.get(column);
            return v == null ? 0 : Math.toIntExact((Long) v);
        }

        boolean flag(String column) {
            Object v = values.get(column);
            return v != null && (Long) v != 0;
        }
    }
}
