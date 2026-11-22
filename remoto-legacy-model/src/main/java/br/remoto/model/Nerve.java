package br.remoto.model;


import br.remoto.core.model.stimulus.NerveStimulation;
import br.remoto.model.vo.NerveVO;


/**
 * A stimulated nerve of a simulation: the properties of the configuration and the intensity of
 * the stimulus at each step, from br.remoto.core.model.stimulus.NerveStimulation. Motoneurons and
 * afferent fibers of the nerve read the intensity to fire their axons.
 */
public class Nerve extends NerveVO
{
	private static final long serialVersionUID = 1L;

	private final NerveStimulation stimulation;


	public Nerve(NerveVO vo, double step)
	{
		this.cdNerve = vo.getCdNerve();
		this.tini = vo.getTini();
		this.tfin = vo.getTfin();
		this.amp = vo.getAmp();
		this.freq = vo.getFreq();
		this.modFreq = vo.getModFreq();
		this.active = vo.isActive();
		this.index = vo.getIndex();
		this.stimulusSpinalEntry = vo.getStimulusSpinalEntry();
		this.stimulusEndPlate = vo.getStimulusEndPlate();
		this.setPaired( vo.isPaired() );
		this.setDelayPaired( vo.getDelayPaired() );
		this.setAmpPaired( vo.getAmpPaired() );

		this.signal = vo.getSignal();
		this.cdSignal = vo.getCdSignal();
		this.modulating_amp = vo.getModulating_amp();
		this.modulating_freq = vo.getModulating_freq();
		this.width = vo.getWidth();
		this.delay = vo.getDelay();

		// The frequency of the paired train is not copied from the configuration: it is always
		// the frequency of the nerve
		this.stimulation = new NerveStimulation(tini, tfin, amp, freq, signal.modulation(),
				paired, delayPaired, ampPaired, freqPaired, step);

		this.freq = stimulation.freq();
		this.freqPaired = stimulation.freqPaired();
	}


	public void atualize(double t)
	{
		stimulation.step(t);
	}


	public double getIntensity() {
		return stimulation.intensity();
	}
}
