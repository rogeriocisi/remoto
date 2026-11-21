package br.remoto.model;

import java.io.Serializable;

import br.remoto.core.model.signal.Modulation;


/**
 * A modulating signal of the configuration (descending command, injected current, nerve
 * stimulation, biomechanical input), as edited and stored by the original model. Its values come
 * from br.remoto.core.model.signal.Modulation, rebuilt whenever a property changes.
 */
public class ModulatingSignal implements Serializable
{
	private static final long serialVersionUID = 1L;

    private String cdNeuronType;
    private String cdSignal;
    private String cdNucleus;
    private String modType;
    private double tini;
    private double tfin;
    private double amp;
    private double freq;
    private double width;
    private double delay;
    private double modFreq;
    private double modFactor;

    // Rebuilt from the properties above when one of them changes
    private transient Modulation modulation;


    public ModulatingSignal()
    {
    	this.cdSignal = ReMoto.none;
    }


    public ModulatingSignal( String cdNeuronType, String cdNucleus )
    {
    	this.cdNeuronType = cdNeuronType;
    	this.cdNucleus = cdNucleus;
    	this.cdSignal = ReMoto.none;
    }


    public double value(double time)
    {
    	return modulation().value(time);
    }


    /** This signal in br.remoto.core.model.signal. */
    public Modulation modulation()
    {
    	Modulation m = modulation;

    	if( m == null )
    	{
    		m = new Modulation(Modulation.Shape.of(cdSignal), tini, tfin, amp, freq, width, delay, modFreq, modFactor);
    		modulation = m;
    	}

    	return m;
    }


	public double getAmp() {
		return amp;
	}
	public void setAmp(double amp) {
		this.amp = amp;
		this.modulation = null;
	}
	public String getCdNucleus() {
		return cdNucleus;
	}
	public void setCdNucleus(String cdNucleus) {
		this.cdNucleus = cdNucleus;
	}
	public String getCdSignal() {
		return cdSignal;
	}
	public void setCdSignal(String cdSignal) {
		this.cdSignal = cdSignal;
		this.modulation = null;
	}
	public double getFreq() {
		return freq;
	}
	public void setModFreq(double modFreq) {
		this.modFreq = modFreq;
		this.modulation = null;
	}
	public double getModFactor() {
		return modFactor;
	}
	public void setModFactor(double modFactor) {
		this.modFactor = modFactor;
		this.modulation = null;
	}
	public double getModFreq() {
		return modFreq;
	}
	public void setFreq(double freq) {
		this.freq = freq;
		this.modulation = null;
	}
	public double getTfin() {
		return tfin;
	}
	public void setTfin(double tfin) {
		this.tfin = tfin;
		this.modulation = null;
	}
	public double getTini() {
		return tini;
	}
	public void setTini(double tini) {
		this.tini = tini;
		this.modulation = null;
	}
	public double getWidth() {
		return width;
	}
	public void setWidth(double width) {
		this.width = width;
		this.modulation = null;
	}
	public double getDelay() {
		return delay;
	}
	public void setDelay(double delay){
		this.delay = delay;
		this.modulation = null;
	}
	public String getCdNeuronType() {
		return cdNeuronType;
	}
	public void setCdNeuronType(String cdNeuron) {
		this.cdNeuronType = cdNeuron;
	}
	public String getModType() {
		return modType;
	}
	public void setModType(String type) {
		this.modType = type;
	}

}
