package br.remoto.core.simulation.neuron;

import java.util.ArrayList;
import java.util.List;

import br.remoto.core.model.synapse.Synapse;
import br.remoto.core.model.synapse.SynapseGroup;
import br.remoto.core.simulation.setup.NeuronVO;

public abstract class Neuron extends NeuronProperties
{
	protected String cd;
	protected Miscellaneous miscellaneous;

	// Main spike train
	protected ArrayList terminalSpikeTrain = new ArrayList();

	// Synapses made by this neuron
	protected List transmittingSynapses = new ArrayList();

	// For some neurons, store internal signals
	protected boolean storedSignals;

	// Slice and region in the rostro-caudal axis (placing procedure)
	protected double xPosition;

	protected double axonThreshold;
	protected double axonConductionVelocity;

	// Key of this neuron in the synapses it makes (nucleus + code), built once instead of on
	// every spike
	private transient String synapseKey;
	private transient String synapseKeyNucleus;
	private transient String synapseKeyCd;

	// The conductance each transmitting synapse resolves this neuron to, looked up once after
	// the network is built (same result as the per-spike Hashtable lookup it replaces)
	private transient Synapse resolvedConductances[];

	// Spikes held until the end of the integration step (parallel update, see Simulation)
	private transient boolean deferSpikes;
	private transient double pendingSpikes[];
	private transient int pendingCount;

	public abstract void atualize(double t);

	public Neuron()
	{
	}

	public Neuron(NeuronVO neu, int index, Miscellaneous misc)
	{
		this();

		this.category = neu.getCategory();
		this.type = neu.getType();
		this.cdNucleus = neu.getCdNucleus();
		this.active = neu.isActive();
		this.index = index;
		this.miscellaneous = misc;
		this.cd = category + " " + type + " " + (index + 1);
	}

	public void reset()
	{
		if( terminalSpikeTrain != null )
			terminalSpikeTrain.clear();
		else
			terminalSpikeTrain = new ArrayList();
	}

	protected void propagateSpike(double t)
	{
		if( deferSpikes )
		{
			if( pendingSpikes == null )
				pendingSpikes = new double[4];
			else if( pendingCount == pendingSpikes.length )
				pendingSpikes = java.util.Arrays.copyOf(pendingSpikes, pendingCount * 2);

			pendingSpikes[pendingCount++] = t;
			return;
		}

		deliverSpike(t);
	}

	// Start all post-synaptic conductances
	private void deliverSpike(double t)
	{
		int size = transmittingSynapses.size();

		if( resolvedConductances == null || resolvedConductances.length != size )
		{
			String key = getSynapseKey();
			Synapse resolved[] = new Synapse[size];

			for(int k = 0; k < size; k++)
				resolved[k] = ((SynapseGroup)transmittingSynapses.get(k)).synapse(key);

			resolvedConductances = resolved;
		}

		for(int k = 0; k < size; k++)
		{
			((SynapseGroup)transmittingSynapses.get(k)).receiveSpike(t, resolvedConductances[k]);
		}
	}

	/** Holds the spikes of this neuron until {@link #flushSpikes()} (or delivers them at once). */
	public void setDeferSpikes(boolean deferSpikes)
	{
		this.deferSpikes = deferSpikes;
	}

	/** Delivers the spikes held during the step, in the order they were fired. */
	public void flushSpikes()
	{
		for(int i = 0; i < pendingCount; i++)
			deliverSpike(pendingSpikes[i]);

		pendingCount = 0;
	}

	/** Key of this neuron in the synapses it makes: nucleus followed by code. */
	public String getSynapseKey()
	{
		if( synapseKey == null || synapseKeyNucleus != cdNucleus || synapseKeyCd != cd )
		{
			synapseKeyNucleus = cdNucleus;
			synapseKeyCd = cd;
			synapseKey = cdNucleus + cd;
		}

		return synapseKey;
	}

	public void ensureCapacity(int size)
	{
		terminalSpikeTrain.ensureCapacity(size);
	}

	public String getCd() {
		return cd;
	}

	public void setCd(String cd) {
		this.cd = cd;
	}

	public List getTransmittingSynapses() {
		return transmittingSynapses;
	}

	public void setTransmittingSynapses(List posSynapses) {
		this.transmittingSynapses = posSynapses;
		this.resolvedConductances = null;
	}

	public Miscellaneous getMiscellaneous() {
		return miscellaneous;
	}

	public boolean isStoredSignals() {
		return storedSignals;
	}

	public void setStoredSignals(boolean storeVM) {
		this.storedSignals = storeVM;
	}

	public double getXPosition() {
		return xPosition;
	}

	public void setXPosition(double position) {
		xPosition = position;
	}

	public ArrayList getTerminalSpikeTrain() {
		return terminalSpikeTrain;
	}

	public void setTerminalSpikeTrain(ArrayList terminalSpikeTrain) {
		this.terminalSpikeTrain = terminalSpikeTrain;
	}

	public void setMiscellaneous(Miscellaneous misc) {
		this.miscellaneous = misc;
	}

	public double getAxonThreshold() {
		return axonThreshold;
	}

	public void setAxonThreshold(double axonThreshold) {
		this.axonThreshold = axonThreshold;
	}

	public double getAxonConductionVelocity() {
		return axonConductionVelocity;
	}

	public void setAxonConductionVelocity(double axonConductionVelocity) {
		this.axonConductionVelocity = axonConductionVelocity;
	}
}
