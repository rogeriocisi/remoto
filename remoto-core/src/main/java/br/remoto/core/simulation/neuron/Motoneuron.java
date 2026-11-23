package br.remoto.core.simulation.neuron;

import java.util.ArrayList;

import br.remoto.core.model.channel.PulseChannel;
import br.remoto.core.model.neuron.MotoneuronMembrane;
import br.remoto.core.model.neuron.SpinalMembrane;
import br.remoto.core.simulation.Nerve;
import br.remoto.core.simulation.ReMoto;
import br.remoto.core.simulation.setup.NeuronVO;
import br.remoto.core.simulation.util.Signal;

/**
 * A motoneuron of the original model: identity, spike trains and the axon (stimulation by the
 * nerve, conduction latencies, collision of antidromic and orthodromic spikes). The two
 * compartment membrane is br.remoto.core.model.neuron.MotoneuronMembrane.
 */
public class Motoneuron extends SpinalNeuron
{
	private static final long serialVersionUID = 1L;

	protected double tAxonSpike;

	protected double latencyStimulusSpinal;
	protected double latencyStimulusEndPlate;
	protected double latencySpinalEndPlate;

	protected ArrayList orthodromicSpikeTrain;
	protected ArrayList antidromicSpikeTrain;

	protected int indexAntidromicSpike;
	protected int indexOrthodromicSpike;

	protected Miscellaneous misc;

	protected Nerve nerve;

	private final MotoneuronMembrane membrane;

	public Motoneuron(NeuronVO neu, int index, int indexCategory, Miscellaneous misc)
	{
		super(neu, index, misc);

		this.index = indexCategory;
		this.misc = misc;
		this.membrane = new MotoneuronMembrane(misc);

		reset();
	}

	public MotoneuronMembrane membrane()
	{
		return membrane;
	}

	public void reset()
	{
		super.reset();

		tAxonSpike = -ReMoto.T_MAX;

		if( orthodromicSpikeTrain != null )
			orthodromicSpikeTrain.clear();
		else
			orthodromicSpikeTrain = new ArrayList();

		if( antidromicSpikeTrain != null )
			antidromicSpikeTrain.clear();
		else
			antidromicSpikeTrain = new ArrayList();

		indexAntidromicSpike = 0;
		indexOrthodromicSpike = 0;
	}

	protected void injectionChanged()
	{
		if( current == null )
		{
			membrane.setInjection(null, null);
			return;
		}

		boolean soma = ReMoto.somaCompartment.equals( current.getCompartment() );
		boolean dendrite = ReMoto.dendriteCompartment.equals( current.getCompartment() );

		membrane.setInjection(soma ? injection() : null, dendrite ? injection() : null);
	}

	public void atualize(double t)
	{
		try
		{
			super.atualize(t);

			// Store signals as marked
			if( storedSignals == true )
			{
				signalStore.add( new Signal(ReMoto.gCa, membrane.ca().lastValue(), t) );
			}

			atualizeDendrite(t);
			atualizeSoma(t);
			atualizeAxon(t);

			double tAntidromicSpike = ReMoto.T_MAX;
			double tOrthodromicSpike = -ReMoto.T_MAX;

			// Verify antidromic propagation of axonal spikes and collision
			if( indexAntidromicSpike < antidromicSpikeTrain.size() )
			{
				tAntidromicSpike = ((Double)antidromicSpikeTrain.get(indexAntidromicSpike)).doubleValue();

				if( t > tAntidromicSpike + latencyStimulusSpinal )
				{
					// Verify collision between soma spike and axon spike
					if( t - membrane.tSomaSpike() >= miscellaneous.getMnSomaRefractoryPeriod() - ReMoto.T_TOLERANCE )
					{
						addSomaSpike(t);
					}

					indexAntidromicSpike++;
				}
			}

			// Verify orthodromic propagation of soma spikes and collision
			if( indexOrthodromicSpike < orthodromicSpikeTrain.size() )
			{
				tOrthodromicSpike = ((Double)orthodromicSpikeTrain.get(indexOrthodromicSpike)).doubleValue();

				if( t > tOrthodromicSpike + latencyStimulusSpinal )
				{
					// Verify collision between soma spike and axon spike
					if( t - tAxonSpike >= miscellaneous.getMnAxonRefractoryPeriod() - ReMoto.T_TOLERANCE )
					{
						addAxonSpike(t);
					}

					indexOrthodromicSpike++;
				}
			}

			// Verify collision in the middle of the axon
			if( Math.abs( tAntidromicSpike - tOrthodromicSpike ) < latencyStimulusSpinal )
			{
				// Increase indexes in order to collide both spikes
				indexAntidromicSpike++;
				indexOrthodromicSpike++;
			}
		}
		catch (Exception e)
		{
			System.out.println( "Error while atualizing MN: " + cd );
		}
	}

	protected void atualizeDendrite(double t)
	{
		try
		{
			membrane.stepDendrite(t);
		}
		catch(Exception e)
		{
			System.out.println( "MN.atualizeDendrite: " + e.getMessage() );
		}
	}

	protected void atualizeSoma(double t)
	{
		try
		{
			// Verify soma spike generation
			if( membrane.stepSoma(t) )
			{
				addSomaSpike(t);
				orthodromicSpikeTrain.add( new Double(t) );
			}

			// Verify PIC activation
			membrane.checkPersistentInwardCurrent(t);
		}
		catch(Exception e)
		{
			System.out.println( "MN.atualizeSoma: " + e.getMessage() );
		}
	}

	public void atualizeAxon(double t)
	{
		if( nerve == null || nerve.isActive() == false )
			return;

		// Verify axon spike generation
		// Verify colision between axon and soma spikes
		if( nerve.getMotorIntensity() > axonThreshold &&
			t - tAxonSpike > miscellaneous.getMnAxonRefractoryPeriod() )
		{
			addAxonSpike(t);
			antidromicSpikeTrain.add( new Double(t) );
		}
	}

	public void addDendritePIC(double t)
	{
		membrane.startPersistentInwardCurrent(t);
	}

	public void addSomaSpike(double t)
	{
		membrane.fireSoma(t);
		somaSpikeTrain.add(new Double(t));

		propagateSpike(t);
	}

	public void addAxonSpike(double t)
	{
		tAxonSpike = t;
		terminalSpikeTrain.add(new Double(t + latencyStimulusEndPlate));
	}

	public void setLatencies(double  axonVelocity, double stimulusSpinalDistance, double stimulusEndPlateDistance)
	{
		if( axonVelocity < 0.01 )
			axonVelocity = 0.01;

		latencyStimulusSpinal = (stimulusSpinalDistance/axonVelocity) * 1000.0;
		latencyStimulusEndPlate = (stimulusEndPlateDistance/axonVelocity) * 1000.0;
		latencySpinalEndPlate = ((stimulusSpinalDistance + stimulusEndPlateDistance)/axonVelocity) * 1000.0;

		latencyStimulusSpinal = Math.round(latencyStimulusSpinal/misc.getStep())*misc.getStep();
		latencyStimulusEndPlate = Math.round(latencyStimulusEndPlate/misc.getStep())*misc.getStep();
		latencySpinalEndPlate = Math.round(latencySpinalEndPlate/misc.getStep())*misc.getStep();
	}

	public double getLatencySpinalEndPlate() {
		return latencySpinalEndPlate;
	}

	public void setLatencySpinalEndPlate(double latencySpinalEndPlate) {
		this.latencySpinalEndPlate = latencySpinalEndPlate;
	}

	public double getLatencyStimulusEndPlate() {
		return latencyStimulusEndPlate;
	}

	public void setLatencyStimulusEndPlate(double latencyStimulusEndPlate) {
		this.latencyStimulusEndPlate = latencyStimulusEndPlate;
	}

	public double getLatencyStimulusSpinal() {
		return latencyStimulusSpinal;
	}

	public void setLatencyStimulusSpinal(double latencyStimulusSpinal) {
		this.latencyStimulusSpinal = latencyStimulusSpinal;
	}

	public double getAxonThreshold() {
		return axonThreshold;
	}

	public void setAxonThreshold(double axonThreshold) {
		this.axonThreshold = axonThreshold;
	}

	public Nerve getNerve() {
		return nerve;
	}

	public void setNerve(Nerve nerve) {
		this.nerve = nerve;
	}

	public PulseChannel getGCa() {
		return membrane.ca();
	}

	public void setGCa(PulseChannel gCa){
		membrane.setCa(gCa);
	}
}
