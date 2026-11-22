package br.remoto.core.simulation.factory;

import java.util.List;

import br.remoto.core.simulation.setup.Configuration;
import br.remoto.core.simulation.Current;
import br.remoto.core.simulation.ModulatingSignal;
import br.remoto.core.simulation.Nerve;
import br.remoto.core.simulation.ReMoto;
import br.remoto.core.simulation.neuron.Motoneuron;
import br.remoto.core.simulation.neuron.Neuron;
import br.remoto.core.simulation.neuron.SensoryFiber;
import br.remoto.core.simulation.neuron.SpinalNeuron;
import br.remoto.core.simulation.setup.NerveVO;

public class StimulationFactory
{
	public void createNerves(Configuration conf, Neuron neurons[][], Nerve[] nerves)
	{
    	List listNerves = conf.getNerves();
    	boolean empty = true;

		// Convert List to Nerve[]
    	for(int n = 0; n < listNerves.size(); n++)
		{
    		NerveVO vo = (NerveVO)listNerves.get(n);

    		if( vo == null )
    			continue;

    		// This indicates that there is at least one nerve active
    		if( vo.isActive() == true )
    			empty = false;

			nerves[n] = new Nerve( vo, conf.getStep() );

			for(int x = 1; x < neurons.length; x++)
			{
				if( neurons[x] == null )
					continue;

				for(int y = 0; y < neurons[x].length; y++)
				{
					Neuron neu = neurons[x][y];

					if( neu.isActive() == false )
						continue;

					if( ( neu.getCdNucleus().endsWith( ReMoto.TA ) && !nerves[n].getCdNerve().equals( ReMoto.CPN ) ) ||
						( ( neu.getCdNucleus().endsWith( ReMoto.LG ) ||
							neu.getCdNucleus().endsWith( ReMoto.MG ) ||
							neu.getCdNucleus().endsWith( ReMoto.SOL ) ) && !nerves[n].getCdNerve().equals( ReMoto.PTN ) ) )
					{
						continue;
					}

					if( neu.getCategory().equals( ReMoto.MN ) )
					{
						((Motoneuron)neu).setNerve( nerves[n] );
					}
					else if( neu.getCategory().equals( ReMoto.AF ) )
					{
						((SensoryFiber)neu).setNerve( nerves[n] );
					}
				}
			}
		}

    	if( empty == true )
    		nerves = null;
	}

	public void injectCurrent(Configuration conf, Neuron neurons[][])
	{
		for(int x = ReMoto.indDT + 1; x < neurons.length; x++)
		{
			if( neurons[x] == null )
				continue;

			for(int y = 0; y < neurons[x].length; y++)
			{
				Neuron neu = neurons[x][y];

				if( neu.getCategory().equals( ReMoto.MN ) || neu.getCategory().equals( ReMoto.IN ) )
				{
				    Current inj = conf.getInjectedCurrent(neu.getCdNucleus(), neu.getType());

					// Set injected current
				    if( inj != null && inj.isActive() )
				    {
				    	inj.setStep( conf.getStep() );

				    	((SpinalNeuron)neu).setCurrent( inj );
				    }
				}
			}
		}
	}
}
