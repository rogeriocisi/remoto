package br.remoto.model.Neuron;

import br.remoto.core.model.tract.TractSpikeGenerator;
import br.remoto.model.ModulatingSignal;
import br.remoto.model.vo.NeuronVO;


/**
 * A descending tract or a noise source of the original model: identity and spike train. The
 * spikes come from br.remoto.core.model.tract.TractSpikeGenerator, modulated by the signal of the
 * tract.
 *
 * @author RRCisi
 */
public class NeuralTract extends Neuron
{
	private static final long serialVersionUID = 1L;

	// Noises have no signal of their own: this one never modulates
	protected ModulatingSignal signal = new ModulatingSignal();

	private final TractSpikeGenerator generator;


	public NeuralTract(NeuronVO neu, int index, Miscellaneous misc, boolean createSignal)
	{
		super(neu, index, misc);

		if( createSignal == true )
		{
			signal = new ModulatingSignal();

			signal.setAmp( neu.getAmp() );
			signal.setCdSignal( neu.getCdSignal() );
			signal.setFreq( neu.getFreq() );
			signal.setModFreq( neu.getModFreq() );
			signal.setTini( neu.getIni() );
			signal.setTfin( neu.getFin() );
			signal.setModType( neu.getModType() );
			signal.setWidth( neu.getWidth() );
			signal.setDelay( neu.getDelay() );
			signal.setModFactor( neu.getModFactor() );
		}

		this.generator = new TractSpikeGenerator(
				TractSpikeGenerator.Distribution.of( neu.getDistribution() ),
				neu.getMean(), neu.getStd(), signal.modulation(),
				"rate".equals( signal.getModType() ), misc.getStep(),
				new TractSpikeGenerator.Output()
				{
					public void fire(double t)
					{
						propagateSpike(t);
					}

					public void record(double t)
					{
						terminalSpikeTrain.add( Double.valueOf(t) );
					}

					public void unrecordFirst()
					{
						terminalSpikeTrain.remove(0);
					}
				});

		reset();
	}


	public void reset()
	{
		super.reset();

		generator.reset();
	}


	public void atualize(double t)
	{
		try
		{
			generator.step(t);
		}
		catch (Exception e)
		{
			System.out.println( "Error while atualizing TR: " + cd );
		}
	}


	public double meanValue(double time)
	{
		return generator.meanValue(time);
	}


	public ModulatingSignal getSignal() {
		return signal;
	}
}
