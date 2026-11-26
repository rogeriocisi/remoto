package br.remoto.core.simulation.neuron;

import java.util.ArrayList;

import br.remoto.core.model.channel.PulseChannel;
import br.remoto.core.model.neuron.ContinuousMotoneuronMembrane;
import br.remoto.core.model.neuron.Membrane;
import br.remoto.core.model.neuron.MotoneuronMembraneModel;
import br.remoto.core.model.neuron.SpinalMembrane;
import br.remoto.core.model.synapse.SynapseGroup;
import br.remoto.core.simulation.Current;
import br.remoto.core.simulation.ReMoto;
import br.remoto.core.simulation.setup.NeuronVO;
import br.remoto.core.simulation.util.Signal;

/**
 * A spinal neuron of the original model. Its membrane (potentials, channels, synapses and the
 * Runge-Kutta integration) lives in br.remoto.core.model.neuron; this class keeps the identity of
 * the neuron, its spike trains, the injected current and the signals stored for display.
 */
public abstract class SpinalNeuron extends Neuron
{
	protected double rn;

	protected ArrayList somaSpikeTrain = new ArrayList();
	protected ArrayList signalStore = new ArrayList();

	// Injected current
	protected Current current;

	public SpinalNeuron()
	{
		super();

		storedSignals = false;
	}

	public SpinalNeuron(NeuronVO neu, int index, Miscellaneous misc)
	{
		super(neu, index, misc);
	}

	/** The membrane of this neuron, in br.remoto.core.model.neuron. */
	public abstract Membrane membrane();

	public void reset()
	{
		super.reset();

		if( somaSpikeTrain != null )
			somaSpikeTrain.clear();
		else
			somaSpikeTrain = new ArrayList();

		signalStore.clear();

		membrane().reset();
	}

	public void ensureCapacity(int size)
	{
		super.ensureCapacity(size);

		somaSpikeTrain.ensureCapacity(size);
	}

	public void atualize(double t)
	{
		// Store signals as marked
		if( storedSignals == true )
		{
			injectionStepTime = t;
			injectionRecorded = false;

			Membrane m = membrane();

			signalStore.add( new Signal(ReMoto.Vd, getVd(), t) );
			signalStore.add( new Signal(ReMoto.Vs, m.vs(), t) );

			// Each formulation reports its own conductances: the original reads the pulse
			// channels, the continuous one reads the gates and adds the persistent sodium
			if( m instanceof SpinalMembrane p )
			{
				signalStore.add( new Signal(ReMoto.gNa, p.na().lastValue(), t) );
				signalStore.add( new Signal(ReMoto.gKf, p.kf().lastValue(), t) );
				signalStore.add( new Signal(ReMoto.gKs, p.ks().lastValue(), t) );
			}
			else if( m instanceof ContinuousMotoneuronMembrane c )
			{
				signalStore.add( new Signal(ReMoto.gNa, c.gNa(), t) );
				signalStore.add( new Signal(ReMoto.gKf, c.gKf(), t) );
				signalStore.add( new Signal(ReMoto.gKs, c.gKs(), t) );
				signalStore.add( new Signal(ReMoto.gNap, c.gNap(), t) );
			}

			if( m.dendExcit() != null ) signalStore.add( new Signal(ReMoto.gExcDend, m.dendExcit().lastValue(), t) );
			if( m.dendInhib() != null ) signalStore.add( new Signal(ReMoto.gInibDend, m.dendInhib().lastValue(), t) );
			if( m.somaExcit() != null ) signalStore.add( new Signal(ReMoto.gExcSoma, m.somaExcit().lastValue(), t) );
			if( m.somaInhib() != null ) signalStore.add( new Signal(ReMoto.gInibSoma, m.somaInhib().lastValue(), t) );
		}
	}

	// The injected current the membrane used in each step, kept for neurons that store their
	// signals: with a random modulation it cannot be recomputed after the simulation
	private transient double injectionStepTime = Double.NaN;
	private transient boolean injectionRecorded;

	/**
	 * The injection the membrane calls. For a neuron that stores its signals, the value of the
	 * first call of each step (the first Runge-Kutta stage, at the step time) also goes to the
	 * signal store; the current is evaluated exactly as many times as without the record.
	 */
	protected java.util.function.DoubleUnaryOperator injection()
	{
		if( current == null )
			return null;

		Current c = current;

		if( !storedSignals )
			return c::getCurrent;

		return time -> {
			double value = c.getCurrent(time);

			if( !injectionRecorded && time == injectionStepTime )
			{
				injectionRecorded = true;
				signalStore.add( new Signal(ReMoto.inj, value, time) );
			}

			return value;
		};
	}

	// Only motoneurons have a dendritic compartment
	private MotoneuronMembraneModel dendrite()
	{
		return (MotoneuronMembraneModel)membrane();
	}

	public double getCapacitanceDend() {
		return membrane() instanceof MotoneuronMembraneModel m ? m.capacitanceDend() : 0;
	}
	public void setCapacitanceDend(double capacitanceDend) {
		dendrite().setCapacitanceDend(capacitanceDend);
	}
	public double getCapacitanceSoma() {
		return membrane().capacitanceSoma();
	}
	public void setCapacitanceSoma(double capacitanceSoma) {
		membrane().setCapacitanceSoma(capacitanceSoma);
	}
	public double getGCoupling() {
		return membrane() instanceof MotoneuronMembraneModel m ? m.gCoupling() : 0;
	}
	public void setGCoupling(double coupling) {
		dendrite().setGCoupling(coupling);
	}
	public double getGLeakDend() {
		return membrane() instanceof MotoneuronMembraneModel m ? m.gLeakDend() : 0;
	}
	public void setGLeakDend(double leakDend) {
		dendrite().setGLeakDend(leakDend);
	}
	public double getGLeakSoma() {
		return membrane().gLeakSoma();
	}
	public void setGLeakSoma(double leakSoma) {
		membrane().setGLeakSoma(leakSoma);
	}
	public Current getCurrent() {
		return current;
	}
	public void setCurrent(Current current) {
		this.current = current;
		injectionChanged();
	}
	/** Hands the injected current to the membrane, in the compartment it goes to. */
	protected abstract void injectionChanged();

	public double getThreshold() {
		return membrane().threshold();
	}
	public void setThreshold(double threshold) {
		membrane().setThreshold(threshold);
	}
	public double getThresholdCa() {
		return membrane() instanceof MotoneuronMembraneModel m ? m.thresholdCa() : 0;
	}
	public void setThresholdCa(double thresholdCa) {
		dendrite().setThresholdCa(thresholdCa);
	}
	public double getVd() {
		return membrane() instanceof MotoneuronMembraneModel m ? m.vd() : 0;
	}
	public void setVd(double vd) {
		dendrite().setVd(vd);
	}
	public double getVs() {
		return membrane().vs();
	}
	public void setVs(double vs) {
		membrane().setVs(vs);
	}
	public PulseChannel getGKf() {
		return membrane() instanceof SpinalMembrane p ? p.kf() : null;
	}
	public void setGKf(PulseChannel gK) {
		if( membrane() instanceof SpinalMembrane p ) p.setKf(gK);
	}
	public PulseChannel getGNa() {
		return membrane() instanceof SpinalMembrane p ? p.na() : null;
	}
	public void setGNa(PulseChannel gNa) {
		if( membrane() instanceof SpinalMembrane p ) p.setNa(gNa);
	}
	public PulseChannel getGKs() {
		return membrane() instanceof SpinalMembrane p ? p.ks() : null;
	}
	public void setGKs(PulseChannel ks) {
		if( membrane() instanceof SpinalMembrane p ) p.setKs(ks);
	}
	public SynapseGroup getDendInhibSynapses() {
		return membrane().dendInhib();
	}
	public void setDendInhibSynapses(SynapseGroup inhibPre) {
		membrane().setDendInhib(inhibPre);
	}
	public ArrayList getSomaSpikeTrain() {
		return somaSpikeTrain;
	}
	public void setSomaSpikeTrain(ArrayList spikeTrain) {
		this.somaSpikeTrain = spikeTrain;
	}
	public ArrayList getSignalStore() {
		return signalStore;
	}
	public void setSignalStore(ArrayList signalStore) {
		this.signalStore = signalStore;
	}
	public double getTSomaSpike() {
		return membrane().tSomaSpike();
	}
	public void setTSomaSpike(double somaSpike) {
		membrane().setTSomaSpike(somaSpike);
	}
	public double getTDendritePIC() {
		return membrane() instanceof MotoneuronMembraneModel m ? m.tDendritePIC() : 0;
	}
	public void setTDendriteSpike(double dendritePIC) {
		dendrite().setTDendritePIC(dendritePIC);
	}
	public SynapseGroup getDendExcitSynapses() {
		return membrane().dendExcit();
	}
	public void setDendExcitSynapses(SynapseGroup excitPre) {
		membrane().setDendExcit(excitPre);
	}
	public SynapseGroup getSomaExcitSynapses() {
		return membrane().somaExcit();
	}
	public void setSomaExcitSynapses(SynapseGroup excitPreSoma) {
		membrane().setSomaExcit(excitPreSoma);
	}
	public SynapseGroup getSomaInhibSynapses() {
		return membrane().somaInhib();
	}
	public void setSomaInhibSynapses(SynapseGroup inhibPreSoma) {
		membrane().setSomaInhib(inhibPreSoma);
	}
	public double getRn() {
		return rn;
	}
	public void setRn(double rn) {
		this.rn = rn;
	}
}
