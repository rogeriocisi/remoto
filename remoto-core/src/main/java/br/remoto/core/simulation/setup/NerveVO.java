package br.remoto.core.simulation.setup;

import java.io.Serializable;

import br.remoto.core.simulation.ModulatingSignal;

public class NerveVO implements Serializable
{
	private static final long serialVersionUID = 1L;

	protected String cdNerve;
	protected String cdJoint;
	protected double tini;
    protected double tfin;
    protected double amp;
    protected double freq;
    protected double modFreq;
    protected double modFactor;
    protected double stimulusSpinalEntry;
	protected double stimulusEndPlate;
	protected boolean active;

	protected boolean paired;
	protected double ampPaired;
	protected double delayPaired;
    protected double freqPaired;

	protected int index;

    protected ModulatingSignal signal;

	protected String cdSignal;
    protected double modulating_tini;
    protected double modulating_tfin;
	protected double modulating_amp;
	protected double modulating_freq;
	protected double width;
    protected double delay;

	// Not stored by the original DAO (in the scenario settings, "nerveStimulus"): the pulse width
	// and the axon threshold, by current (the original) or by charge with leak, with the
	// strength-duration time constants (ms) of the motor and of the sensory axons
	public static final String CURRENT_THRESHOLD = "current";
	public static final String CHARGE_THRESHOLD = "charge";
	public static final double DEFAULT_TAU_MOTOR = 0.45;
	public static final double DEFAULT_TAU_SENSORY = 0.65;

	protected double pulseWidth = br.remoto.core.model.stimulus.NerveStimulation.PULSE_WIDTH;
	protected String thresholdModel = CURRENT_THRESHOLD;
	protected double tauMotor = DEFAULT_TAU_MOTOR;
	protected double tauSensory = DEFAULT_TAU_SENSORY;
    //----------------------------------

	public NerveVO()
	{
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public double getAmp() {
		return amp;
	}

	public void setAmp(double amp) {
		this.amp = amp;
	}

	public String getCdNerve() {
		return cdNerve;
	}

	public void setCdNerve(String cdNerve) {
		this.cdNerve = cdNerve;
	}

	public double getFreq() {
		return freq;
	}

	public void setFreq(double freq) {
		this.freq = freq;
	}

	public void setModFreq(double modFreq) {
		this.modFreq = modFreq;
	}

	public double getModFreq() {
		return modFreq;
	}

	public void setModFactor(double modFactor) {
		this.modFactor = modFactor;
	}

	public double getModFactor() {
		return modFactor;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public double getStimulusEndPlate() {
		return stimulusEndPlate;
	}

	public void setStimulusEndPlate(double stimulusEndPlate) {
		this.stimulusEndPlate = stimulusEndPlate;
	}

	public double getStimulusSpinalEntry() {
		return stimulusSpinalEntry;
	}

	public void setStimulusSpinalEntry(double stimulusSpinalEntry) {
		this.stimulusSpinalEntry = stimulusSpinalEntry;
	}

	public double getTfin() {
		return tfin;
	}

	public void setTfin(double tfin) {
		this.tfin = tfin;
	}

	public double getTini() {
		return tini;
	}

	public void setTini(double tini) {
		this.tini = tini;
	}

	public boolean isPaired() {
		return paired;
	}

	public void setPaired(boolean paired) {
		this.paired = paired;
	}

	public double getAmpPaired() {
		return ampPaired;
	}

	public void setAmpPaired(double ampPaired) {
		this.ampPaired = ampPaired;
	}

	public double getDelayPaired() {
		return delayPaired;
	}

	public void setDelayPaired(double delayPaired) {
		this.delayPaired = delayPaired;
	}

	public double getFreqPaired() {
		return freqPaired;
	}

	public void setFreqPaired(double freqPaired) {
		this.freqPaired = freqPaired;
	}

	public double getModulating_freq() {
		return modulating_freq;
	}

	public void setModulating_freq(double modulating_freq) {
		this.modulating_freq = modulating_freq;
	}

	public double getDelay() {
		return delay;
	}

	public void setDelay(double delay) {
		this.delay = delay;
	}

	//Created by Vitor Chaud in 19/05/11
	//----------------------------------
	public ModulatingSignal getSignal() {
		return signal;
	}

	public void setSignal(ModulatingSignal signal) {
		this.signal = signal;
	}

	public String getCdSignal() {
		return cdSignal;
	}

	public void setCdSignal(String cdSignal) {
		this.cdSignal = cdSignal;
	}

	public double getModulating_amp() {
		return modulating_amp;
	}

	public void setModulating_amp(double modulating_amp) {
		this.modulating_amp = modulating_amp;
	}

	public double getModulating_tini() {
		return modulating_tini;
	}

	public void setModulating_tini(double modulating_tini) {
		this.modulating_tini = modulating_tini;
	}

	public double getModulating_tfin() {
		return modulating_tfin;
	}

	public void setModulating_tfin(double modulating_tfin) {
		this.modulating_tfin = modulating_tfin;
	}

	public double getWidth() {
		return width;
	}

	public void setWidth(double width) {
		this.width = width;
	}
	//----------------------------------

	public String getCdJoint() {
		return cdJoint;
	}

	public void setCdJoint(String cdJoint) {
		this.cdJoint = cdJoint;
	}

	public double getPulseWidth() {
		return pulseWidth;
	}

	public void setPulseWidth(double pulseWidth) {
		if( !(pulseWidth > 0) )
			throw new IllegalArgumentException("the pulse width must be greater than zero");
		this.pulseWidth = pulseWidth;
	}

	public String getThresholdModel() {
		return thresholdModel;
	}

	public void setThresholdModel(String thresholdModel) {
		if( !CURRENT_THRESHOLD.equals(thresholdModel) && !CHARGE_THRESHOLD.equals(thresholdModel) )
			throw new IllegalArgumentException("unknown threshold model: " + thresholdModel);
		this.thresholdModel = thresholdModel;
	}

	public double getTauMotor() {
		return tauMotor;
	}

	public void setTauMotor(double tauMotor) {
		if( !(tauMotor > 0) )
			throw new IllegalArgumentException("the time constant must be greater than zero");
		this.tauMotor = tauMotor;
	}

	public double getTauSensory() {
		return tauSensory;
	}

	public void setTauSensory(double tauSensory) {
		if( !(tauSensory > 0) )
			throw new IllegalArgumentException("the time constant must be greater than zero");
		this.tauSensory = tauSensory;
	}

	/** Whether the pulse and the threshold are the original ones (nothing to store in the settings). */
	public boolean hasOriginalStimulus() {
		return pulseWidth == br.remoto.core.model.stimulus.NerveStimulation.PULSE_WIDTH
				&& CURRENT_THRESHOLD.equals(thresholdModel)
				&& tauMotor == DEFAULT_TAU_MOTOR && tauSensory == DEFAULT_TAU_SENSORY;
	}
}
