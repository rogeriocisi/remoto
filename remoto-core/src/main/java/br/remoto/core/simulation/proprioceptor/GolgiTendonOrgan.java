package br.remoto.core.simulation.proprioceptor;

import java.util.ArrayList;

import br.remoto.core.simulation.setup.Configuration;
import br.remoto.core.simulation.ReMoto;
import br.remoto.core.model.receptor.CragoTendonOrgan;
import br.remoto.core.simulation.neuron.Miscellaneous;
import br.remoto.core.simulation.neuron.Motoneuron;
import br.remoto.core.simulation.neuron.Neuron;
import br.remoto.core.simulation.neuron.SensoryFiber;
import br.remoto.core.simulation.util.Sample;
import br.remoto.core.simulation.util.Signal;

public class GolgiTendonOrgan {
	private String cdNucleus;

	protected Sample samplerIbFiringRate;

	protected ArrayList IbFiringRateStore = new ArrayList();

	protected CragoTendonOrgan lumpedGTO;

	private double IbFiringRate;
	private double afferentNumber = 0;

	public GolgiTendonOrgan(String cdNucleus, Neuron neurons[][], Configuration conf, String gto_model){
		this.cdNucleus = cdNucleus;

		samplerIbFiringRate = new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());

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
					((SensoryFiber)neu).setGTO( this );
				}

				if( neu.getType().equals(ReMoto.Ib)){
					afferentNumber = afferentNumber + 1;
				}
			}
		}

		// The rate of the tendon organ is shared by the Ib fibers counted above
		if(gto_model.equals(ReMoto.lumpedGtoModelCrago))
			this.lumpedGTO = new CragoTendonOrgan(conf.getStep(), afferentNumber);
	}

	public void atualize(double t, double force){
		lumpedGTO.step(force);
		IbFiringRate = lumpedGTO.ibFiringRate();

		samplerIbFiringRate.sample(IbFiringRateStore, "", t, IbFiringRate);
	}

	public ArrayList getIbFiringRateStore() {
		return IbFiringRateStore;
	}

	public void setIbFiringRateStore(ArrayList ibFiringRateStore) {
		IbFiringRateStore = ibFiringRateStore;
	}

	public String getCdNucleus() {
		return cdNucleus;
	}

	public void setCdNucleus(String cdNucleus) {
		this.cdNucleus = cdNucleus;
	}

	public double getIbFiringRate() {
		return IbFiringRate;
	}

	public void setIbFiringRate(double ibFiringRate) {
		IbFiringRate = ibFiringRate;
	}
}
