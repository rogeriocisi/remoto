
package br.remoto.model.Neuron;

import java.util.ArrayList;

import br.remoto.model.Current;
import br.remoto.model.ReMoto;
import br.remoto.core.model.synapse.SynapseGroup;
import br.remoto.core.model.channel.PulseChannel;
import br.remoto.model.vo.NeuronVO;
import br.remoto.util.Signal;

//Modified in 05-10-11 by L. A. Elias

public abstract class SpinalNeuron extends Neuron
{
	protected double threshold;
	protected double rn;
	protected double thresholdCa;
	
	protected ArrayList somaSpikeTrain = new ArrayList();
	protected ArrayList signalStore = new ArrayList();

	protected double tSomaSpike;
	protected double tDendritePIC;

	// Ionic conductances
	protected PulseChannel gKf;
	protected PulseChannel gKs;
	protected PulseChannel gNa;
	

	// Injected current
	protected Current current;

	// Electrotonic properties
	protected double vs;
	protected double vd;
	protected double capacitanceSoma;
	protected double capacitanceDend;
	protected double gLeakSoma;
	protected double gLeakDend;
	protected double gCoupling;
	
	// Synapses which this neuron receives
	protected SynapseGroup dendExcitSynapses; 
	protected SynapseGroup dendInhibSynapses; 
	protected SynapseGroup somaExcitSynapses; 
	protected SynapseGroup somaInhibSynapses; 

	
	public SpinalNeuron()
	{
		super();

		storedSignals = false;
	}
	
	
	public SpinalNeuron(NeuronVO neu, int index, Miscellaneous misc)
	{
		super(neu, index, misc);
	}
	
		
	public void reset()
	{
		super.reset();
		
		if( somaSpikeTrain != null )
			somaSpikeTrain.clear();
		else
			somaSpikeTrain = new ArrayList();
		
		signalStore.clear();

		tSomaSpike = -ReMoto.T_MAX;
		tDendritePIC = -ReMoto.T_MAX;

		vs = 0;
		vd = 0;

		if( gKf != null )
			gKf.reset();

		if( gKs != null )
			gKs.reset();

		if( gNa != null )
			gNa.reset();
		
		
		
		// rcisi@27mai2009 - error on keep the same...
		//current = null;
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
			signalStore.add( new Signal(ReMoto.Vd, vd, t) );
			signalStore.add( new Signal(ReMoto.Vs, vs, t) );

			signalStore.add( new Signal(ReMoto.gNa, gNa.lastValue(), t) );
			signalStore.add( new Signal(ReMoto.gKf, gKf.lastValue(), t) );
			signalStore.add( new Signal(ReMoto.gKs, gKs.lastValue(), t) );
			
			if( dendExcitSynapses != null ) signalStore.add( new Signal(ReMoto.gExcDend, dendExcitSynapses.lastValue(), t) );
			if( dendInhibSynapses != null ) signalStore.add( new Signal(ReMoto.gInibDend, dendInhibSynapses.lastValue(), t) );
			if( somaExcitSynapses != null ) signalStore.add( new Signal(ReMoto.gExcSoma, somaExcitSynapses.lastValue(), t) );
			if( somaInhibSynapses != null ) signalStore.add( new Signal(ReMoto.gInibSoma, somaInhibSynapses.lastValue(), t) );
		}
	}

	
	protected double saturate(double var)  // limita o valor de E a P0 ou EsE
	{
		if( var < ReMoto.P0 )
			return ReMoto.P0;
		else if( var > ReMoto.EsE )
			return ReMoto.EsE;
		else
			return var;
	}


	public double getCapacitanceDend() {
		return capacitanceDend;
	}


	public void setCapacitanceDend(double capacitanceDend) {
		this.capacitanceDend = capacitanceDend;
	}


	public double getCapacitanceSoma() {
		return capacitanceSoma;
	}


	public void setCapacitanceSoma(double capacitanceSoma) {
		this.capacitanceSoma = capacitanceSoma;
	}


	public double getGCoupling() {
		return gCoupling;
	}


	public void setGCoupling(double coupling) {
		gCoupling = coupling;
	}


	public double getGLeakDend() {
		return gLeakDend;
	}


	public void setGLeakDend(double leakDend) {
		gLeakDend = leakDend;
	}


	public double getGLeakSoma() {
		return gLeakSoma;
	}


	public void setGLeakSoma(double leakSoma) {
		gLeakSoma = leakSoma;
	}


	public Current getCurrent() {
		return current;
	}


	public void setCurrent(Current current) {
		this.current = current;
	}


	public double getThreshold() {
		return threshold;
	}


	public void setThreshold(double threshold) {
		this.threshold = threshold;
	}

	public double getThresholdCa() {
		return thresholdCa;
	}

	public void setThresholdCa(double thresholdCa) {
		this.thresholdCa = thresholdCa;
	}
	
	public double getVd() {
		return vd;
	}


	public void setVd(double vd) {
		this.vd = vd;
	}


	public double getVs() {
		return vs;
	}


	public void setVs(double vs) {
		this.vs = vs;
	}


	public PulseChannel getGKf() {
		return gKf;
	}


	public void setGKf(PulseChannel gK) {
		this.gKf = gK;
	}


	public PulseChannel getGNa() {
		return gNa;
	}


	public void setGNa(PulseChannel gNa) {
		this.gNa = gNa;
	}


	public PulseChannel getGKs() {
		return gKs;
	}


	public void setGKs(PulseChannel ks) {
		gKs = ks;
	}

	public SynapseGroup getDendInhibSynapses() {
		return dendInhibSynapses;
	}


	public void setDendInhibSynapses(SynapseGroup inhibPre) {
		this.dendInhibSynapses = inhibPre;
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
		return tSomaSpike;
	}


	public void setTSomaSpike(double somaSpike) {
		tSomaSpike = somaSpike;
	}
	
	public double getTDendritePIC() {
		return tDendritePIC;
	}

	public void setTDendriteSpike(double dendritePIC) {
		tDendritePIC = dendritePIC;		
	}

	public SynapseGroup getDendExcitSynapses() {
		return dendExcitSynapses;
	}


	public void setDendExcitSynapses(SynapseGroup excitPre) {
		this.dendExcitSynapses = excitPre;
	}


	public SynapseGroup getSomaExcitSynapses() {
		return somaExcitSynapses;
	}


	public void setSomaExcitSynapses(SynapseGroup excitPreSoma) {
		this.somaExcitSynapses = excitPreSoma;
	}


	public SynapseGroup getSomaInhibSynapses() {
		return somaInhibSynapses;
	}


	public void setSomaInhibSynapses(SynapseGroup inhibPreSoma) {
		this.somaInhibSynapses = inhibPreSoma;
	}


	public double getRn() {
		return rn;
	}


	public void setRn(double rn) {
		this.rn = rn;
	}
	
}
