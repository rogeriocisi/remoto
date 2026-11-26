package br.remoto.core.model.neuron;

import br.remoto.core.model.synapse.SynapseGroup;

import java.util.function.DoubleUnaryOperator;

/**
 * Motoneurônio de dois compartimentos com cinética contínua: a mesma topologia de
 * {@link MotoneuronMembrane} — soma e dendrito acoplados —, com as condutâncias iônicas descritas
 * por comportas de Hodgkin-Huxley integradas a cada passo, em vez dos pulsos de duração fixa de
 * Destexhe (1997).
 *
 * <p>No soma são cinco comportas: sódio rápido (m³h), sódio persistente (p³), potássio rápido
 * (n⁴) e potássio lento (r²). No dendrito, a comporta L do cálcio tipo L, cuja condutância é
 * escalada pelo ganho de neuromodulação γ. A diferença que importa em relação ao modelo de pulso
 * é que a corrente persistente passa a ser um estado da membrana, e não um evento disparado pelo
 * limiar do soma: é daí que vêm os platôs de despolarização e a biestabilidade.
 *
 * <p><b>Unidades.</b> As mesmas do resto do ReMoto: potenciais medidos a partir do repouso (mV),
 * condutâncias em µS, capacitâncias em nF e correntes em nA. É também a convenção em que o
 * conjunto de parâmetros de origem está escrito, com E_Na = 120, E_K = −10 e a fuga revertendo no
 * repouso.
 *
 * <p><b>Integração.</b> As comportas avançam por Euler exponencial, que é a solução exata de
 * x' = (x∞ − x)/τ com v congelado no passo — o mesmo que o método {@code cnexp} das formulações
 * de origem. Os potenciais avançam por Runge-Kutta de 4ª ordem, como no modelo de pulso, com as
 * comportas congeladas dentro do passo. O dendrito é integrado primeiro, com o potencial do soma
 * do passo anterior, e depois o soma, com o dendrito já atualizado.
 *
 * <p><b>Disparo.</b> Aqui o potencial de ação é produzido pela própria dinâmica, então
 * {@link #fireSoma(double)} apenas registra o instante, para quem cuida do axônio e das colisões,
 * e {@link #checkPersistentInwardCurrent(double)} não faz nada: a PIC é contínua.
 */
public final class ContinuousMotoneuronMembrane implements MotoneuronMembraneModel {

    /** Potencial de repouso, referência das fugas (mV). Como em {@link SpinalMembrane}. */
    private static final double E0 = 0.0;
    /** Limite inferior do potencial (mV). */
    private static final double P0 = -16.0;
    /** Limite superior do potencial (mV), que é também o potencial de equilíbrio do sódio. */
    private static final double ESE = 120.0;
    /** "Nenhum disparo ainda". */
    private static final double NEVER = -1_000_000;

    // Coeficientes das taxas α e β, na convenção de potencial relativo ao repouso.
    private static final double M_ALPHA_A = 0.64, M_ALPHA_V = 15.0, M_ALPHA_K = 4.0;
    private static final double M_BETA_A = 0.56, M_BETA_V = 40.0, M_BETA_K = 5.0;
    private static final double H_ALPHA_A = 0.928, H_ALPHA_V = 17.0, H_ALPHA_TAU = 18.0;
    private static final double H_BETA_A = 9.0, H_BETA_V = 40.0, H_BETA_K = 5.0;
    private static final double P_ALPHA_A = 0.64, P_ALPHA_V = 5.0, P_ALPHA_K = 4.0;
    private static final double P_BETA_A = 0.56, P_BETA_V = 30.0, P_BETA_K = 5.0;
    private static final double N_ALPHA_A = 0.08, N_ALPHA_V = 15.0, N_ALPHA_K = 7.0;
    private static final double N_BETA_A = 2.0, N_BETA_V = 10.0, N_BETA_TAU = 40.0;
    private static final double R_ALPHA_A = 3.5, R_ALPHA_V = 55.0, R_ALPHA_K = 4.0;
    /** Meia-ativação da comporta L, relativa a {@link #vTraubDend} (mV). */
    private static final double L_HALF = -30.0;

    private final MembraneSettings settings;

    // Soma
    private double vs;
    private double capacitanceSoma;
    private double gLeakSoma;
    private double threshold;
    private double tSomaSpike;
    private double gNaBar;
    private double gNapBar;
    private double gKfBar;
    private double gKsBar;
    private double eNa = 120.0;
    private double eK = -10.0;
    private double vTraubSoma = 0.0;
    private double rInact = 0.05;
    private double m;
    private double h;
    private double p;
    private double n;
    private double r;

    // Dendrito
    private double vd;
    private double capacitanceDend;
    private double gLeakDend;
    private double gCoupling;
    private double thresholdCa;
    private double tDendritePIC;
    private double gCaLBar;
    private double eCaL = 140.0;
    private double vTraubDend = 35.0;
    private double lTau = 60.0;
    private double caL;

    private SynapseGroup dendExcit;
    private SynapseGroup dendInhib;
    private SynapseGroup somaExcit;
    private SynapseGroup somaInhib;

    private DoubleUnaryOperator somaInjection;
    private DoubleUnaryOperator dendriteInjection;

    public ContinuousMotoneuronMembrane(MembraneSettings settings) {
        this.settings = settings;
        reset();
    }

    /** Volta ao repouso: potenciais em 0 e comportas no estado estacionário desse potencial. */
    @Override
    public void reset() {
        tSomaSpike = NEVER;
        tDendritePIC = NEVER;
        vs = 0;
        vd = 0;
        m = steadyState(alphaM(0), betaM(0));
        h = steadyState(alphaH(0), betaH(0));
        p = steadyState(alphaP(0), betaP(0));
        n = steadyState(alphaN(0), betaN(0));
        r = steadyState(alphaR(0), rInact);
        caL = lInf(0);
    }

    /**
     * Registra o disparo do soma. Diferente do modelo de pulso, nada é aberto aqui: o potencial
     * de ação vem da própria cinética.
     */
    @Override
    public void fireSoma(double t) {
        tSomaSpike = t;
    }

    @Override
    public void stepDendrite(double t) {
        caL = advanceGate(caL, lInf(vd), lTau);

        double k1 = dVdDt(t, saturate(vd));
        double k2 = dVdDt(t + settings.stepByTwo(), saturate(vd + settings.stepByTwo() * k1));
        double k3 = dVdDt(t + settings.stepByTwo(), saturate(vd + settings.stepByTwo() * k2));
        double k4 = dVdDt(t + settings.step(), saturate(vd + settings.step() * k3));
        vd = saturate(vd + settings.stepBySix() * (k1 + 2 * k2 + 2 * k3 + k4));
    }

    @Override
    public boolean stepSoma(double t) {
        advanceSomaGates(vs);

        double k1 = dVsDt(t, saturate(vs));
        double k2 = dVsDt(t + settings.stepByTwo(), saturate(vs + settings.stepByTwo() * k1));
        double k3 = dVsDt(t + settings.stepByTwo(), saturate(vs + settings.stepByTwo() * k2));
        double k4 = dVsDt(t + settings.step(), saturate(vs + settings.step() * k3));
        vs = saturate(vs + settings.stepBySix() * (k1 + 2 * k2 + 2 * k3 + k4));

        return vs > threshold && t - tSomaSpike > settings.motoneuronSomaRefractoryPeriod();
    }

    /** Não faz nada: a corrente persistente é contínua, não um evento. */
    @Override
    public void checkPersistentInwardCurrent(double t) {
        // sem efeito neste modelo
    }

    /**
     * Registra o instante, para quem acompanha a PIC de fora. A corrente em si segue a comporta L
     * e não é iniciada por evento.
     */
    @Override
    public void startPersistentInwardCurrent(double t) {
        tDendritePIC = t;
    }

    private void advanceSomaGates(double v) {
        m = advanceGateFromRates(m, alphaM(v), betaM(v));
        h = advanceGateFromRates(h, alphaH(v), betaH(v));
        p = advanceGateFromRates(p, alphaP(v), betaP(v));
        n = advanceGateFromRates(n, alphaN(v), betaN(v));
        r = advanceGateFromRates(r, alphaR(v), rInact);
    }

    /** Euler exponencial a partir de α e β: exato para v congelado no passo. */
    private double advanceGateFromRates(double gate, double alpha, double beta) {
        double sum = alpha + beta;
        if (sum <= 0) {
            return gate;
        }
        return advanceGate(gate, alpha / sum, 1 / sum);
    }

    /** Euler exponencial a partir de x∞ e τ. */
    private double advanceGate(double gate, double inf, double tau) {
        if (tau <= 0) {
            return inf;
        }
        return inf + (gate - inf) * Math.exp(-settings.step() / tau);
    }

    private static double steadyState(double alpha, double beta) {
        double sum = alpha + beta;
        return sum <= 0 ? 0 : alpha / sum;
    }

    private double dVdDt(double t, double v) {
        double iIonic = gCaLBar * caL * settings.gammaCa() * (eCaL - v);

        double iSynaptic = 0;
        if (dendExcit != null) {
            iSynaptic += dendExcit.current(t, v);
        }
        if (dendInhib != null) {
            iSynaptic += dendInhib.current(t, v);
        }

        double iSoma = gCoupling * (vs - v);
        double iLeak = gLeakDend * (E0 - v);
        double iInjected = injected(dendriteInjection, t);

        return (iIonic + iSynaptic + iSoma + iLeak + iInjected) / capacitanceDend;
    }

    private double dVsDt(double t, double v) {
        double gNa = gNaBar * m * m * m * h;
        double gNap = gNapBar * p * p * p;
        double gKf = gKfBar * n * n * n * n;
        double gKs = gKsBar * r * r;

        double iIonic = (gNa + gNap) * (eNa - v) + (gKf + gKs) * (eK - v);

        double iSynaptic = 0;
        if (somaExcit != null) {
            iSynaptic += somaExcit.current(t, v);
        }
        if (somaInhib != null) {
            iSynaptic += somaInhib.current(t, v);
        }

        double iDendrite = gCoupling * (vd - v);
        double iLeak = gLeakSoma * (E0 - v);
        double iInjected = injected(somaInjection, t);

        return (iIonic + iSynaptic + iDendrite + iLeak + iInjected) / capacitanceSoma;
    }

    // --- taxas, na forma das equações de origem -------------------------------------------

    private double alphaM(double v) {
        return M_ALPHA_A * vtrap(M_ALPHA_V - (v - vTraubSoma), M_ALPHA_K);
    }

    private double betaM(double v) {
        return M_BETA_A * vtrap((v - vTraubSoma) - M_BETA_V, M_BETA_K);
    }

    private double alphaH(double v) {
        return H_ALPHA_A * Math.exp((H_ALPHA_V - (v - vTraubSoma)) / H_ALPHA_TAU);
    }

    private double betaH(double v) {
        return H_BETA_A / (Math.exp((H_BETA_V - (v - vTraubSoma)) / H_BETA_K) + 1);
    }

    private double alphaP(double v) {
        return P_ALPHA_A * vtrap(P_ALPHA_V - (v - vTraubSoma), P_ALPHA_K);
    }

    private double betaP(double v) {
        return P_BETA_A * vtrap((v - vTraubSoma) - P_BETA_V, P_BETA_K);
    }

    private double alphaN(double v) {
        return N_ALPHA_A * vtrap(N_ALPHA_V - (v - vTraubSoma), N_ALPHA_K);
    }

    private double betaN(double v) {
        return N_BETA_A * Math.exp((N_BETA_V - (v - vTraubSoma)) / N_BETA_TAU);
    }

    private double alphaR(double v) {
        return R_ALPHA_A / (Math.exp((R_ALPHA_V - (v - vTraubSoma)) / R_ALPHA_K) + 1);
    }

    private double lInf(double v) {
        return 1 / (Math.exp(-((v - vTraubDend) - L_HALF)) + 1);
    }

    /** Evita o 0 no denominador das taxas, como na formulação de origem. */
    static double vtrap(double x, double y) {
        if (Math.abs(x / y) < 1e-6) {
            return y * (1 - x / y / 2);
        }
        return x / (Math.exp(x / y) - 1);
    }

    private static double saturate(double v) {
        if (v < P0) {
            return P0;
        } else if (v > ESE) {
            return ESE;
        }
        return v;
    }

    private static double injected(DoubleUnaryOperator injection, double t) {
        return injection == null ? 0 : injection.applyAsDouble(t);
    }

    // --- estado das comportas, para teste e para os sinais gravados ------------------------

    /** Ativação do sódio rápido. */
    public double gateM() {
        return m;
    }

    /** Inativação do sódio rápido. */
    public double gateH() {
        return h;
    }

    /** Ativação do sódio persistente. */
    public double gateP() {
        return p;
    }

    /** Ativação do potássio rápido. */
    public double gateN() {
        return n;
    }

    /** Ativação do potássio lento. */
    public double gateR() {
        return r;
    }

    /** Ativação do cálcio tipo L no dendrito. */
    public double gateL() {
        return caL;
    }

    /** Condutância do sódio rápido no último passo (µS). */
    public double gNa() {
        return gNaBar * m * m * m * h;
    }

    /** Condutância do sódio persistente no último passo (µS). */
    public double gNap() {
        return gNapBar * p * p * p;
    }

    /** Condutância do potássio rápido no último passo (µS). */
    public double gKf() {
        return gKfBar * n * n * n * n;
    }

    /** Condutância do potássio lento no último passo (µS). */
    public double gKs() {
        return gKsBar * r * r;
    }

    /** Condutância do cálcio tipo L já escalada por γ (µS). */
    public double gCaL() {
        return gCaLBar * caL * settings.gammaCa();
    }

    @Override
    public double calciumConductance() {
        return gCaL();
    }

    // --- acessores ------------------------------------------------------------------------

    @Override
    public void setInjection(DoubleUnaryOperator soma, DoubleUnaryOperator dendrite) {
        this.somaInjection = soma;
        this.dendriteInjection = dendrite;
    }

    @Override
    public double vs() {
        return vs;
    }

    @Override
    public void setVs(double vs) {
        this.vs = vs;
    }

    @Override
    public double capacitanceSoma() {
        return capacitanceSoma;
    }

    @Override
    public void setCapacitanceSoma(double c) {
        this.capacitanceSoma = c;
    }

    @Override
    public double gLeakSoma() {
        return gLeakSoma;
    }

    @Override
    public void setGLeakSoma(double g) {
        this.gLeakSoma = g;
    }

    @Override
    public double threshold() {
        return threshold;
    }

    @Override
    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    @Override
    public double tSomaSpike() {
        return tSomaSpike;
    }

    @Override
    public void setTSomaSpike(double t) {
        this.tSomaSpike = t;
    }

    @Override
    public double vd() {
        return vd;
    }

    @Override
    public void setVd(double vd) {
        this.vd = vd;
    }

    @Override
    public double capacitanceDend() {
        return capacitanceDend;
    }

    @Override
    public void setCapacitanceDend(double c) {
        this.capacitanceDend = c;
    }

    @Override
    public double gLeakDend() {
        return gLeakDend;
    }

    @Override
    public void setGLeakDend(double g) {
        this.gLeakDend = g;
    }

    @Override
    public double gCoupling() {
        return gCoupling;
    }

    @Override
    public void setGCoupling(double g) {
        this.gCoupling = g;
    }

    @Override
    public double thresholdCa() {
        return thresholdCa;
    }

    @Override
    public void setThresholdCa(double t) {
        this.thresholdCa = t;
    }

    @Override
    public double tDendritePIC() {
        return tDendritePIC;
    }

    @Override
    public void setTDendritePIC(double t) {
        this.tDendritePIC = t;
    }

    @Override
    public SynapseGroup dendExcit() {
        return dendExcit;
    }

    @Override
    public void setDendExcit(SynapseGroup g) {
        this.dendExcit = g;
    }

    @Override
    public SynapseGroup dendInhib() {
        return dendInhib;
    }

    @Override
    public void setDendInhib(SynapseGroup g) {
        this.dendInhib = g;
    }

    @Override
    public SynapseGroup somaExcit() {
        return somaExcit;
    }

    @Override
    public void setSomaExcit(SynapseGroup g) {
        this.somaExcit = g;
    }

    @Override
    public SynapseGroup somaInhib() {
        return somaInhib;
    }

    @Override
    public void setSomaInhib(SynapseGroup g) {
        this.somaInhib = g;
    }

    /** Condutância máxima do sódio rápido (µS). */
    public double gNaBar() {
        return gNaBar;
    }

    public void setGNaBar(double g) {
        this.gNaBar = g;
    }

    /** Condutância máxima do sódio persistente (µS). */
    public double gNapBar() {
        return gNapBar;
    }

    public void setGNapBar(double g) {
        this.gNapBar = g;
    }

    /** Condutância máxima do potássio rápido (µS). */
    public double gKfBar() {
        return gKfBar;
    }

    public void setGKfBar(double g) {
        this.gKfBar = g;
    }

    /** Condutância máxima do potássio lento (µS). */
    public double gKsBar() {
        return gKsBar;
    }

    public void setGKsBar(double g) {
        this.gKsBar = g;
    }

    /** Condutância máxima do cálcio tipo L no dendrito (µS). */
    public double gCaLBar() {
        return gCaLBar;
    }

    public void setGCaLBar(double g) {
        this.gCaLBar = g;
    }

    /** Potencial de equilíbrio do sódio (mV acima do repouso). */
    public double eNa() {
        return eNa;
    }

    public void setENa(double e) {
        this.eNa = e;
    }

    /** Potencial de equilíbrio do potássio (mV em relação ao repouso). */
    public double eK() {
        return eK;
    }

    public void setEK(double e) {
        this.eK = e;
    }

    /** Potencial de equilíbrio do cálcio (mV acima do repouso). */
    public double eCaL() {
        return eCaL;
    }

    public void setECaL(double e) {
        this.eCaL = e;
    }

    /** Deslocamento de potencial das taxas do soma (mV). */
    public double vTraubSoma() {
        return vTraubSoma;
    }

    public void setVTraubSoma(double v) {
        this.vTraubSoma = v;
    }

    /** Deslocamento de potencial da comporta L (mV). */
    public double vTraubDend() {
        return vTraubDend;
    }

    public void setVTraubDend(double v) {
        this.vTraubDend = v;
    }

    /** Constante de tempo da comporta L (ms). */
    public double lTau() {
        return lTau;
    }

    public void setLTau(double tau) {
        this.lTau = tau;
    }

    /** Taxa de inativação do potássio lento (1/ms). */
    public double rInact() {
        return rInact;
    }

    public void setRInact(double rate) {
        this.rInact = rate;
    }
}
