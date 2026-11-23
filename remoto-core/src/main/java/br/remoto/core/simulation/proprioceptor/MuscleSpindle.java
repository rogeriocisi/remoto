package br.remoto.core.simulation.proprioceptor;

import java.util.ArrayList;

import br.remoto.core.model.receptor.MileusnicSpindle;
import br.remoto.core.model.receptor.ProchazkaSpindle;
import br.remoto.core.simulation.setup.Configuration;
import br.remoto.core.simulation.ModulatingSignal;
import br.remoto.core.simulation.ReMoto;
import br.remoto.core.simulation.neuron.Neuron;
import br.remoto.core.simulation.neuron.SensoryFiber;
import br.remoto.core.simulation.util.Sample;

/**
 * The muscle spindle of a nucleus in the original model: the afferent fibers it drives and the
 * samplers of the firing rates. The spindle is br.remoto.core.model.receptor.MileusnicSpindle or
 * ProchazkaSpindle.
 */
public class MuscleSpindle {
	String cdNucleus;
	String spindle_model;

	protected Sample samplerIaFiringRateStore;
	protected Sample samplerIIFiringRateStore;

	protected double gammaDynamicMNFiringRate;
	protected double gammaStaticMNFiringRate;

	protected double IaFiringRate;
	protected double IIFiringRate;

	protected ArrayList IaFiringRateStore = new ArrayList();
	protected ArrayList IIFiringRateStore = new ArrayList();

	private MileusnicSpindle mileusnic;

	// Fusimotor drive over time (added to the constant rates); null when the scenario does not modulate it
	private final ModulatingSignal gammaDynamicSignal;
	private final ModulatingSignal gammaStaticSignal;
	private ProchazkaSpindle prochazka;

	public MuscleSpindle(String cdNucleus, Neuron neurons[][], Configuration conf, double gamma_dynamic, double gamma_static, String spindle_model) {
		this.cdNucleus = cdNucleus;
		this.spindle_model = spindle_model;

		samplerIaFiringRateStore = new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerIIFiringRateStore = new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());

		this.gammaDynamicMNFiringRate = gamma_dynamic;
		this.gammaStaticMNFiringRate = gamma_static;
		this.gammaDynamicSignal = active(conf.getGammaDynamicSignal());
		this.gammaStaticSignal = active(conf.getGammaStaticSignal());

		if(spindle_model.equals(ReMoto.spindleModelProchazka)){
			this.prochazka = new ProchazkaSpindle();
		}
		else if(spindle_model.equals(ReMoto.spindleModelMileusnic)){
			this.mileusnic = new MileusnicSpindle(cdNucleus, conf.getStep(), gammaDynamicMNFiringRate, gammaStaticMNFiringRate,
					conf.getPrimaryBag1Gain(), conf.getPrimaryBag2AndChainGain(), conf.getSecondaryBag2AndChainGain());
		}

		for(int x = ReMoto.indDT + 1; x < neurons.length; x++)
		{
			if( neurons[x] == null )
				continue;

			for(int y = 0; y < neurons[x].length; y++)
			{
				Neuron neu = neurons[x][y];

				if( neu.isActive() == false )
					continue;

				if( !neu.getCdNucleus().endsWith( cdNucleus ))
				{
					continue;
				}

				if( neu.getCategory().equals( ReMoto.AF ) )
				{
					((SensoryFiber)neu).setSpindle( this );
				}
			}
		}
	}

	public void atualize(double t, double fascicleLength, double velocity, double acceleration){
		if( prochazka != null ){
			prochazka.step(fascicleLength, velocity);
			IaFiringRate = prochazka.iaFiringRate();
			IIFiringRate = prochazka.iiFiringRate();
		}
		else if( mileusnic != null ){
			if( gammaDynamicSignal != null || gammaStaticSignal != null ){
				mileusnic.setFusimotor(fusimotor(gammaDynamicMNFiringRate, gammaDynamicSignal, t),
						fusimotor(gammaStaticMNFiringRate, gammaStaticSignal, t));
			}
			mileusnic.step(t, fascicleLength, velocity, acceleration);
			IaFiringRate = mileusnic.iaFiringRate();
			IIFiringRate = mileusnic.iiFiringRate();
		}

		samplerIaFiringRateStore.sample(IaFiringRateStore, "", t, IaFiringRate);
		samplerIIFiringRateStore.sample(IIFiringRateStore, "", t, IIFiringRate);
	}

	/** The constant rate plus the signal, never negative (Hz). */
	private static double fusimotor(double rate, ModulatingSignal signal, double t) {
		return signal == null ? rate : Math.max(0, rate + signal.value(t));
	}

	private static ModulatingSignal active(ModulatingSignal signal) {
		return signal == null || signal.getCdSignal() == null || ReMoto.none.equals(signal.getCdSignal()) ? null : signal;
	}

	public String getCdNucleus() {
		return cdNucleus;
	}
	public void setCdNucleus(String cdNucleus) {
		this.cdNucleus = cdNucleus;
	}
	public ArrayList getIaFiringRateStore() {
		return IaFiringRateStore;
	}
	public ArrayList getIIFiringRateStore() {
		return IIFiringRateStore;
	}
	public double getIaFiringRate() {
		return IaFiringRate;
	}
	public double getIIFiringRate() {
		return IIFiringRate;
	}
	public String getSpindle_model() {
		return spindle_model;
	}
}
