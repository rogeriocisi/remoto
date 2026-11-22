package br.remoto.model.Neuron;

import br.remoto.core.model.neuron.AfferentFiber;
import br.remoto.model.Nerve;
import br.remoto.model.Proprioceptors.GolgiTendonOrgan;
import br.remoto.model.Proprioceptors.MuscleSpindle;
import br.remoto.model.vo.NeuronVO;


/**
 * An afferent fiber of the original model (Ia, II, Ib): identity, receptor and spike train. The
 * axon (stimulation by the nerve, firing driven by the spindle or the tendon organ, conduction to
 * the spinal cord) is br.remoto.core.model.neuron.AfferentFiber.
 */
public class SensoryFiber extends Neuron
{
	private static final long serialVersionUID = 1L;

	protected Nerve nerve;

	protected GolgiTendonOrgan gto;
	protected MuscleSpindle spindle;

	private final AfferentFiber fiber;


	public SensoryFiber(NeuronVO neu, int index, Miscellaneous misc)
	{
		super(neu, index, misc);

		this.fiber = new AfferentFiber(AfferentFiber.Kind.of( getType() ), misc, t -> {
			propagateSpike(t);
			terminalSpikeTrain.add( Double.valueOf(t) );
		});

		reset();
	}


	/** The axon of this fiber, in br.remoto.core.model.neuron. */
	public AfferentFiber fiber()
	{
		return fiber;
	}


	public void reset()
	{
		super.reset();

		fiber.reset();
	}


	public void ensureCapacity(int size)
	{
		super.ensureCapacity(size);

		fiber.ensureCapacity(size);
	}


	public void atualize(double t)
	{
		try
		{
			// Propagate spike to all post-synaptic neurons
			fiber.deliver(t);

			// Stimulation by the nerve and firing driven by the receptor
			fiber.generate(t, nerve.getIntensity());
		}
		catch (Exception e)
		{
			System.out.println( "Error while atualizing AF: " + cd );
		}
	}


	public void setLatency(double axonVelocity, double stimulusSpinalDistance, double stimulusEndPlateDistance)
	{
		fiber.setLatency(axonVelocity, stimulusSpinalDistance, stimulusEndPlateDistance);
	}


	public void setAxonThreshold(double axonThreshold) {
		super.setAxonThreshold(axonThreshold);
		fiber.setAxonThreshold(axonThreshold);
	}


	public Nerve getNerve() {
		return nerve;
	}


	public void setNerve(Nerve nerve) {
		this.nerve = nerve;
	}


	// Ib fibers fire with the tendon organ
	public void setGTO(GolgiTendonOrgan golgiTendonOrgan) {
		gto = golgiTendonOrgan;

		if( fiber.kind() == AfferentFiber.Kind.IB )
			fiber.setFiringRate( gto == null ? null : gto::getIbFiringRate );
	}


	// Ia and II fibers fire with the spindle
	public void setSpindle(MuscleSpindle spindle) {
		this.spindle = spindle;

		if( fiber.kind() == AfferentFiber.Kind.IA )
			fiber.setFiringRate( spindle == null ? null : spindle::getIaFiringRate );
		else if( fiber.kind() == AfferentFiber.Kind.II )
			fiber.setFiringRate( spindle == null ? null : spindle::getIIFiringRate );
	}

	public double getRecruitmentThreshold() {
		return fiber.recruitmentThreshold();
	}

	public void setRecruitmentThreshold(double recruitmentThreshold) {
		fiber.setRecruitmentThreshold(recruitmentThreshold);
	}
}
