package br.remoto.core.simulation.neuron;

import br.remoto.core.model.tract.TractSpikeGenerator;
import br.remoto.core.simulation.ModulatingSignal;
import br.remoto.core.simulation.setup.NeuronVO;

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

	// The first tract of each type keeps the mean interval it used at each step (ISI mean value
	// of the results screen): with a random modulation, it cannot be recomputed afterwards
	private final boolean recordsMean;
	private double[] meanTimes = new double[0];
	private double[] meanValues = new double[0];
	private int meanCount;

	public NeuralTract(NeuronVO neu, int index, Miscellaneous misc, boolean createSignal)
	{
		super(neu, index, misc);

		this.recordsMean = index == 0 && br.remoto.core.simulation.ReMoto.TR.equals( neu.getCategory() );

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
			signal.setSeries( neu.getRateSeriesPoints() );
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

					public void mean(double t, double mean)
					{
						if( recordsMean )
							recordMean(t, mean);
					}
				});

		reset();
	}

	public void reset()
	{
		super.reset();

		meanCount = 0;

		generator.reset();
	}

	private void recordMean(double t, double mean)
	{
		if( meanCount == meanTimes.length )
		{
			int size = Math.max(1024, meanCount * 2);
			meanTimes = java.util.Arrays.copyOf(meanTimes, size);
			meanValues = java.util.Arrays.copyOf(meanValues, size);
		}
		meanTimes[meanCount] = t;
		meanValues[meanCount] = mean;
		meanCount++;
	}

	/** Whether this tract kept the mean interval it used (the first tract of each type). */
	public boolean isRecordingMean() {
		return recordsMean;
	}

	/** Instants (ms) at which the generator evaluated the mean interval. */
	public double[] getMeanTimes() {
		return java.util.Arrays.copyOf(meanTimes, meanCount);
	}

	/** The mean interval (ms) used at each of those instants. */
	public double[] getMeanValues() {
		return java.util.Arrays.copyOf(meanValues, meanCount);
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
