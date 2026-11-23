package br.remoto.core.model.neuron;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * O axônio mielinizado no ponto do estímulo do nervo: um nó de Ranvier e o internó vizinho, ligados
 * pela via de Barrett-Barrett (sob e através da mielina), no modelo de axônio humano de Bostock,
 * com os parâmetros de Howells, Trevillion, Bostock e Burke (2012, J Physiol 590:1625-1640),
 * tabelas 1 e 2 e apêndice, para axônios motores e sensitivos.
 *
 * <p>No nó: sódio transitório ({@code m³h}) e persistente ({@code mp³}, 1,07% da permeabilidade),
 * pela equação de Goldman-Hodgkin-Katz com seletividade de 0,9 para o Na⁺; potássio rápido
 * ({@code n⁴}) e lento ({@code s}); vazamento. No internó: potássio rápido e lento, a corrente h
 * (HCN, {@code q}, seletividade de 0,097 para o Na⁺) e vazamento. As bombas de Na⁺/K⁺ são correntes
 * constantes. As capacitâncias são a do nó, a da mielina e a do axolema internodal; as
 * concentrações e as constantes de velocidade, as do artigo, a 36 °C.
 *
 * <p>Duas escolhas desta implementação, por o apêndice publicado no PubMed Central ter as equações
 * cortadas à esquerda:
 * <ul>
 *   <li>as equações das duas tensões são as do circuito da figura 1 (a mielina entre o espaço
 *   periaxonal e o meio externo), sem desprezar nenhum termo; o artigo divide a do internó só por
 *   {@code C_ax}, o que dá o mesmo resultado, porque a capacitância da mielina é 200 vezes menor;</li>
 *   <li>as correntes das bombas são as que mantêm os potenciais de repouso da tabela 1 (−84,4 mV no
 *   nó e −84,6 mV no internó do motor). Calculadas assim, elas dão −41 e −9,2 pA no motor, contra
 *   −33 e −7,9 pA da tabela: algum detalhe do modelo original difere desta reconstrução.</li>
 * </ul>
 *
 * <p>O estímulo entra como corrente no nó. A escala é a do limiar da configuração (a corrente, em
 * mA, de um pulso de 1 ms): a corrente injetada é {@code estímulo / limiar} vezes a corrente que
 * faz o modelo disparar com um pulso de 1 ms. Assim, os limiares de cada axônio continuam valendo,
 * e o modelo dá o resto: a curva intensidade-duração (constante de tempo de cerca de 0,4 ms no
 * motor e 0,5 ms no sensitivo), a refratariedade, a supernormalidade e a acomodação.
 *
 * <p>A integração usa Euler exponencial nas comportas e Euler implícito linearizado nas tensões,
 * com subpassos de até {@value #FINE_STEP} ms enquanto o nó está ativo (estímulo, despolarização,
 * disparo) e um passo por passo da simulação quando ele só se recupera. Parado no repouso, o modelo
 * não calcula nada.
 */
public final class AxonNode {

    /** Os dois conjuntos de parâmetros do artigo. */
    public enum Kind {
        MOTOR, SENSORY
    }

    /** Subpasso máximo (ms) enquanto o nó está ativo: o limiar fica a menos de 1% do convergido. */
    static final double FINE_STEP = 0.005;

    /** O nó dispara quando a tensão passa deste valor (mV) subindo; rearma abaixo de {@link #REARM}. */
    static final double SPIKE = 0;
    private static final double REARM = -60;

    /** Acima desta tensão (mV), o nó é integrado com subpassos finos. */
    private static final double ACTIVE = -75;

    private static final double FARADAY = 96485.33;
    private static final double GAS = 8.314462;
    private static final double TEMPERATURE = 273.15 + 36;
    private static final double RT_F = GAS * TEMPERATURE / FARADAY * 1000; // mV

    private static final double NA_I = 9;
    private static final double NA_O = 144.2;
    private static final double K_I = 155;
    private static final double K_O = 4.5;

    private static final double C_NODE = 1.4; // pF
    private static final double C_MYELIN = 1.55;
    private static final double C_AXOLEMMA = 327;

    private static final double SEL_NA = 0.9;
    private static final double SEL_H = 0.097;
    private static final double E_K = reversal(0);
    private static final double E_H = reversal(SEL_H);

    // Kf e Ks: iguais no motor e no sensitivo (tabela 2)
    private static final double[] ALPHA_N = {0.0221, -90.8, 7.7};
    private static final double[] BETA_N = {0.0393, -73.6, 7.35};
    private static final double[] ALPHA_S = {0.00563, -23.5, 12.7};
    private static final double[] BETA_S = {0.00341, -91.1, 11.7};

    private static final Map<String, Double> THRESHOLDS = new ConcurrentHashMap<>();

    /** Os parâmetros de um tipo de axônio (tabelas 1 e 2; nS, mV, ms⁻¹). */
    private record Params(double pNa, double napFraction, double gKsNode, double gKsInternode, double gKfNode,
            double gKfInternode, double gH, double gLeakNode, double gLeakInternode, double gBB, double restNode,
            double restInternode, double[] alphaM, double[] betaM, double[] alphaMp, double[] betaMp,
            double[] alphaH, double[] betaH, double[] q) {
    }

    private static final Params MOTOR = new Params(4.35e-9, 0.0107, 56.7, 0.57, 18.2, 207, 2.95, 1.97, 4, 35.9,
            -84.4, -84.6,
            new double[] {6.54, -18.5, 10.3}, new double[] {0.302, -22.8, 9.16},
            new double[] {3.27, -36.5, 10.3}, new double[] {0.151, -40.8, 9.16},
            new double[] {0.126, -115.1, 15.6}, new double[] {8.60, -32.9, 19.0},
            new double[] {0.00522, -107.3, -12.2});

    private static final Params SENSORY = new Params(4.35e-9, 0.0107, 29.1, 1.74, 19.4, 205, 4.1, 1.69, 3.65, 40.3,
            -80.3, -81.3,
            new double[] {6.25, -18.3, 10.3}, new double[] {0.289, -22.6, 9.16},
            new double[] {3.13, -36.3, 10.3}, new double[] {0.145, -40.6, 9.16},
            new double[] {0.153, -113.8, 11.9}, new double[] {10.5, -31.6, 14.5},
            new double[] {0.00522, -94.2, -12.2});

    private final Kind kind;
    private final Params p;
    private final double step;
    private final int substeps;
    /** pA injetados por mA de estímulo. */
    private final double gain;

    private final double pumpNode;
    private final double pumpInternode;
    private final double[] restGates = new double[8];

    // tensões (mV) e comportas: m, mp, h, n, s no nó; n*, s*, q no internó
    private double e;
    private double eInternode;
    private final double[] gates = new double[8];

    private boolean atRest = true;
    private boolean armed = true;

    /**
     * @param step passo da simulação (ms)
     * @param threshold1ms o limiar do axônio na configuração: a intensidade (mA) de um pulso de 1 ms
     *     que o faz disparar; maior que zero
     */
    public AxonNode(Kind kind, double step, double threshold1ms) {
        this(kind, step, gain(kind, step, threshold1ms), kind == Kind.MOTOR ? MOTOR : SENSORY);
    }

    /** @param gain pA injetados por unidade de estímulo */
    private AxonNode(Kind kind, double step, double gain, Params params) {
        this.kind = kind;
        this.p = params;
        this.step = step;
        this.substeps = Math.max(1, (int) Math.ceil(step / FINE_STEP - 1e-9));
        this.gain = gain;

        e = p.restNode();
        eInternode = p.restInternode();
        steadyGates(e, eInternode, restGates);
        System.arraycopy(restGates, 0, gates, 0, 8);
        double bb = p.gBB() * (e - eInternode);
        pumpNode = -(nodeChannels(e) + bb);
        pumpInternode = -(internodeChannels(eInternode) - bb);
    }

    /**
     * Avança um passo com a intensidade do estímulo neste passo (mA).
     *
     * @return se o nó disparou neste passo
     */
    public boolean step(double stimulus) {
        if (stimulus == 0 && atRest) {
            return false;
        }
        atRest = false;
        double injected = stimulus * gain;
        boolean fine = stimulus != 0 || e > ACTIVE || !armed;
        boolean fired = false;
        int n = fine ? substeps : 1;
        double h = step / n;
        for (int i = 0; i < n; i++) {
            advance(h, injected);
            if (armed && e > SPIKE) {
                fired = true;
                armed = false;
            } else if (!armed && e < REARM) {
                armed = true;
            }
        }
        if (stimulus == 0 && armed && nearRest()) {
            e = p.restNode();
            eInternode = p.restInternode();
            System.arraycopy(restGates, 0, gates, 0, 8);
            atRest = true;
        }
        return fired;
    }

    /** Tensão do nó (mV). */
    public double potential() {
        return e;
    }

    /** Tensão do axolema internodal (mV). */
    public double internodePotential() {
        return eInternode;
    }

    /** Correntes das bombas (pA) no nó e no internó, as que mantêm o repouso. */
    public double[] pumps() {
        return new double[] {pumpNode, pumpInternode};
    }

    public Kind kind() {
        return kind;
    }

    /**
     * A corrente (pA) de um pulso de 1 ms que faz o modelo disparar a partir do repouso, com o passo
     * dado: a referência da escala do estímulo. Calculada uma vez por tipo e passo.
     */
    public static double thresholdCurrent(Kind kind, double step) {
        return THRESHOLDS.computeIfAbsent(kind + "@" + step, k -> calibrate(kind, step));
    }

    private static double calibrate(Kind kind, double step) {
        int pulse = (int) Math.round(1.0 / step);
        double lo = 0;
        double hi = 100_000;
        for (int i = 0; i < 50 && hi - lo > hi * 1e-9; i++) {
            double mid = (lo + hi) / 2;
            if (fires(kind, step, mid, pulse)) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return hi;
    }

    /** Se um pulso de {@code steps} passos com {@code current} pA faz o nó disparar em até 5 ms. */
    static boolean fires(Kind kind, double step, double current, int steps) {
        AxonNode node = new AxonNode(kind, step, 1.0, kind == Kind.MOTOR ? MOTOR : SENSORY);
        int total = steps + (int) Math.round(5 / step);
        for (int i = 0; i < total; i++) {
            if (node.step(i < steps ? current : 0)) {
                return true;
            }
        }
        return false;
    }

    private static double gain(Kind kind, double step, double threshold1ms) {
        if (!(threshold1ms > 0)) {
            throw new IllegalArgumentException("o limiar do axônio deve ser maior que zero: " + threshold1ms);
        }
        return thresholdCurrent(kind, step) / threshold1ms;
    }

    private boolean nearRest() {
        if (Math.abs(e - p.restNode()) > 1e-4 || Math.abs(eInternode - p.restInternode()) > 1e-4) {
            return false;
        }
        for (int i = 0; i < 8; i++) {
            if (Math.abs(gates[i] - restGates[i]) > 1e-6) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ integração

    private void advance(double h, double injected) {
        // comportas: Euler exponencial, com as tensões do início do subpasso
        relax(0, alphaM(e), betaM(e), h);
        relax(1, f1(p.alphaMp(), e), f2(p.betaMp(), e), h);
        relax(2, f2(p.alphaH(), e), betaH(e), h);
        relax(3, f1(ALPHA_N, e), f2(BETA_N, e), h);
        relax(4, f1(ALPHA_S, e), f2(BETA_S, e), h);
        relax(5, f1(ALPHA_N, eInternode), f2(BETA_N, eInternode), h);
        relax(6, f1(ALPHA_S, eInternode), f2(BETA_S, eInternode), h);
        double x = Math.exp((eInternode - p.q()[1]) / p.q()[2]);
        relax(7, p.q()[0] * x, p.q()[0] / x, h);

        // tensões: Euler implícito com as correntes linearizadas em torno das tensões atuais.
        // (C_n + C_my) dE/dt − C_my dE*/dt = I_inj − I_nó − I_BB
        // (C_ax + C_my) dE*/dt − C_my dE/dt = I_BB − I_internó
        double iNode = nodeChannels(e) + pumpNode;
        double gNode = (nodeChannels(e + 1e-3) + pumpNode - iNode) / 1e-3;
        double iInternode = internodeChannels(eInternode) + pumpInternode;
        double gInternode = (internodeChannels(eInternode + 1e-3) + pumpInternode - iInternode) / 1e-3;
        double gBB = p.gBB();
        double bb = gBB * (e - eInternode);

        double a11 = (C_NODE + C_MYELIN) / h + gNode + gBB;
        double a12 = -C_MYELIN / h - gBB;
        double a22 = (C_AXOLEMMA + C_MYELIN) / h + gInternode + gBB;
        double b1 = injected - iNode - bb;
        double b2 = bb - iInternode;
        double det = a11 * a22 - a12 * a12;
        e += (b1 * a22 - a12 * b2) / det;
        eInternode += (a11 * b2 - a12 * b1) / det;
    }

    private void relax(int i, double alpha, double beta, double h) {
        double sum = alpha + beta;
        double inf = alpha / sum;
        gates[i] = inf + (gates[i] - inf) * Math.exp(-h * sum);
    }

    /** Correntes iônicas do nó (pA), sem a bomba. */
    private double nodeChannels(double v) {
        double m = gates[0];
        double mp = gates[1];
        double hh = gates[2];
        double n = gates[3];
        double s = gates[4];
        double n2 = n * n;
        return (m * m * m * hh + p.napFraction() * mp * mp * mp) * ghk(v)
                + p.gKfNode() * n2 * n2 * (v - E_K)
                + p.gKsNode() * s * (v - E_K)
                + p.gLeakNode() * (v - p.restNode());
    }

    /** Correntes iônicas do internó (pA), sem a bomba. */
    private double internodeChannels(double v) {
        double n = gates[5];
        double s = gates[6];
        double q = gates[7];
        double n2 = n * n;
        return p.gKfInternode() * n2 * n2 * (v - E_K)
                + p.gKsInternode() * s * (v - E_K)
                + p.gH() * q * (v - E_H)
                + p.gLeakInternode() * (v - p.restInternode());
    }

    /** Corrente de Goldman-Hodgkin-Katz pelos canais de Na⁺ totalmente abertos (pA). */
    private double ghk(double v) {
        double u = v / RT_F;
        if (Math.abs(u) < 1e-9) {
            u = 1e-9;
        }
        double eu = Math.exp(u);
        double conc = SEL_NA * (NA_O - NA_I * eu) + (1 - SEL_NA) * (K_O - K_I * eu);
        // P (cm³/s) × F u × concentração (mM = 10⁻⁶ mol/cm³) dá A; × 10¹² dá pA
        return p.pNa() * FARADAY * u * conc / (1 - eu) * 1e6;
    }

    private double alphaM(double v) {
        return f1(p.alphaM(), v);
    }

    private double betaM(double v) {
        return f2(p.betaM(), v);
    }

    private double betaH(double v) {
        double[] c = p.betaH();
        return c[0] / (1 + Math.exp((c[1] - v) / c[2]));
    }

    private void steadyGates(double v, double vInternode, double[] out) {
        out[0] = steady(alphaM(v), betaM(v));
        out[1] = steady(f1(p.alphaMp(), v), f2(p.betaMp(), v));
        out[2] = steady(f2(p.alphaH(), v), betaH(v));
        out[3] = steady(f1(ALPHA_N, v), f2(BETA_N, v));
        out[4] = steady(f1(ALPHA_S, v), f2(BETA_S, v));
        out[5] = steady(f1(ALPHA_N, vInternode), f2(BETA_N, vInternode));
        out[6] = steady(f1(ALPHA_S, vInternode), f2(BETA_S, vInternode));
        double x = Math.exp((vInternode - p.q()[1]) / p.q()[2]);
        out[7] = steady(p.q()[0] * x, p.q()[0] / x);
    }

    private static double steady(double alpha, double beta) {
        return alpha / (alpha + beta);
    }

    /** {@code A (E − B) / (1 − e^((B − E)/C))}. */
    private static double f1(double[] c, double v) {
        double x = v - c[1];
        return Math.abs(x) < 1e-7 ? c[0] * c[2] : c[0] * x / (1 - Math.exp(-x / c[2]));
    }

    /** {@code A (B − E) / (1 − e^((E − B)/C))}. */
    private static double f2(double[] c, double v) {
        double x = c[1] - v;
        return Math.abs(x) < 1e-7 ? c[0] * c[2] : c[0] * x / (1 - Math.exp(-x / c[2]));
    }

    /** Potencial de reversão (mV) de um canal com seletividade {@code sel} para o Na⁺ (o resto, K⁺). */
    private static double reversal(double sel) {
        return RT_F * Math.log((sel * NA_O + (1 - sel) * K_O) / (sel * NA_I + (1 - sel) * K_I));
    }
}
