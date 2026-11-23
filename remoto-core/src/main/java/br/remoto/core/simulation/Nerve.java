package br.remoto.core.simulation;

import br.remoto.core.model.stimulus.LeakyCharge;
import br.remoto.core.model.stimulus.NerveStimulation;
import br.remoto.core.simulation.setup.NerveVO;

/**
 * A stimulated nerve of a simulation: the properties of the configuration and the intensity of
 * the stimulus at each step, from br.remoto.core.model.stimulus.NerveStimulation. Motoneurons and
 * afferent fibers of the nerve read the intensity to fire their axons: the intensity itself, with
 * the threshold by current of the original, or the intensity equivalent to the charge with leak
 * of each kind of axon (br.remoto.core.model.stimulus.LeakyCharge).
 */
public class Nerve extends NerveVO
{
	private static final long serialVersionUID = 1L;

	private final NerveStimulation stimulation;

	// Threshold by charge: one integrator for the motor axons and one for the sensory axons (the
	// axons of a kind see the same stimulus and have the same time constant); null = by current
	private final LeakyCharge motorCharge;
	private final LeakyCharge sensoryCharge;
	private double motorIntensity;
	private double sensoryIntensity;

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
		this.pulseWidth = vo.getPulseWidth();
		this.thresholdModel = vo.getThresholdModel();
		this.tauMotor = vo.getTauMotor();
		this.tauSensory = vo.getTauSensory();

		// The frequency of the paired train is not copied from the configuration: it is always
		// the frequency of the nerve
		this.stimulation = new NerveStimulation(tini, tfin, amp, freq, signal.modulation(),
				paired, delayPaired, ampPaired, freqPaired, step, pulseWidth);

		boolean charge = CHARGE_THRESHOLD.equals(thresholdModel);
		this.motorCharge = charge ? new LeakyCharge(tauMotor, step) : null;
		this.sensoryCharge = charge ? new LeakyCharge(tauSensory, step) : null;

		this.freq = stimulation.freq();
		this.freqPaired = stimulation.freqPaired();
	}

	public void atualize(double t)
	{
		stimulation.step(t);

		if( motorCharge != null )
		{
			motorIntensity = motorCharge.step(stimulation.intensity());
			sensoryIntensity = sensoryCharge.step(stimulation.intensity());
		}
	}

	public double getIntensity() {
		return stimulation.intensity();
	}

	/** What the motor axons compare with their threshold. */
	public double getMotorIntensity() {
		return motorCharge == null ? stimulation.intensity() : motorIntensity;
	}

	/** What the sensory axons compare with their threshold. */
	public double getSensoryIntensity() {
		return sensoryCharge == null ? stimulation.intensity() : sensoryIntensity;
	}
}
