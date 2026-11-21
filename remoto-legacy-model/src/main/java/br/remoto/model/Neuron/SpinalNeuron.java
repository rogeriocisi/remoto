package br.remoto.model.Neuron;

import java.util.ArrayList;

import br.remoto.core.model.channel.PulseChannel;
import br.remoto.core.model.neuron.MotoneuronMembrane;
import br.remoto.core.model.neuron.SpinalMembrane;
import br.remoto.core.model.synapse.SynapseGroup;
import br.remoto.model.Current;
import br.remoto.model.ReMoto;
import br.remoto.model.vo.NeuronVO;
import br.remoto.util.Signal;


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
	public abstract SpinalMembrane membrane();


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
			SpinalMembrane m = membrane();

			signalStore.add( new Signal(ReMoto.Vd, getVd(), t) );
			signalStore.add( new Signal(ReMoto.Vs, m.vs(), t) );

			signalStore.add( new Signal(ReMoto.gNa, m.na().lastValue(), t) );
			signalStore.add( new Signal(ReMoto.gKf, m.kf().lastValue(), t) );
			signalStore.add( new Signal(ReMoto.gKs, m.ks().lastValue(), t) );

			if( m.dendExcit() != null ) signalStore.add( new Signal(ReMoto.gExcDend, m.dendExcit().lastValue(), t) );
			if( m.dendInhib() != null ) signalStore.add( new Signal(ReMoto.gInibDend, m.dendInhib().lastValue(), t) );
			if( m.somaExcit() != null ) signalStore.add( new Signal(ReMoto.gExcSoma, m.somaExcit().lastValue(), t) );
			if( m.somaInhib() != null ) signalStore.add( new Signal(ReMoto.gInibSoma, m.somaInhib().lastValue(), t) );
		}
	}


	// Only motoneurons have a dendritic compartment
	private MotoneuronMembrane dendrite()
	{
		return (MotoneuronMembrane)membrane();
	}


	public double getCapacitanceDend() {
		return membrane() instanceof MotoneuronMembrane m ? m.capacitanceDend() : 0;
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
		return membrane() instanceof MotoneuronMembrane m ? m.gCoupling() : 0;
	}
	public void setGCoupling(double coupling) {
		dendrite().setGCoupling(coupling);
	}
	public double getGLeakDend() {
		return membrane() instanceof MotoneuronMembrane m ? m.gLeakDend() : 0;
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
		return membrane() instanceof MotoneuronMembrane m ? m.thresholdCa() : 0;
	}
	public void setThresholdCa(double thresholdCa) {
		dendrite().setThresholdCa(thresholdCa);
	}
	public double getVd() {
		return membrane() instanceof MotoneuronMembrane m ? m.vd() : 0;
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
		return membrane().kf();
	}
	public void setGKf(PulseChannel gK) {
		membrane().setKf(gK);
	}
	public PulseChannel getGNa() {
		return membrane().na();
	}
	public void setGNa(PulseChannel gNa) {
		membrane().setNa(gNa);
	}
	public PulseChannel getGKs() {
		return membrane().ks();
	}
	public void setGKs(PulseChannel ks) {
		membrane().setKs(ks);
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
		return membrane() instanceof MotoneuronMembrane m ? m.tDendritePIC() : 0;
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
