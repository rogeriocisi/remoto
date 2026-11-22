package br.remoto.core.simulation.factory;

import br.remoto.core.model.random.Rng;

import java.util.ArrayList;
import java.util.List;

import br.remoto.core.simulation.setup.Configuration;
import br.remoto.core.simulation.neuron.Interneuron;
import br.remoto.core.simulation.neuron.Neuron;
import br.remoto.core.simulation.neuron.SpinalNeuron;
import br.remoto.core.simulation.ReMoto;
import br.remoto.core.simulation.neuron.Motoneuron;
import br.remoto.core.simulation.setup.ConductanceVO;
import br.remoto.core.simulation.setup.DynamicVO;

import br.remoto.core.model.synapse.SynapseGroup;
import br.remoto.core.model.synapse.SynapticDynamics;
import br.remoto.core.simulation.setup.DynamicVO;

public class SynapsesFactory
{
	// ??? teste para saber o numero de sinapses de uma simulacao de larga escala

	public void createSynapses(Configuration conf, Neuron neurons[][])
	{
		List nt = new ArrayList();
		List in = new ArrayList();
		List rc = new ArrayList();
		List noises = new ArrayList();

		// Store NT, IN and Noises to use bellow
		for(int x = 0; x < neurons.length; x++)
		{
			for(int n = 0; n < neurons[x].length; n++)
			{
				Neuron neu = neurons[x][n];

				if( neu.getCategory().equals( ReMoto.TR ) )
					nt.add( neu );
				else if( neu.getCategory().equals( ReMoto.IN ) )
					in.add( neu );
				else if( neu.getCategory().equals( ReMoto.Noise ) )
					noises.add( neu );

				if( neu.getType().equals( ReMoto.RC ) )
					rc.add( neu );
			}
		}

		// The loop begins with the index of the first regular nucleus
		for(int x = ReMoto.indDT + 1; x < neurons.length; x++)
		{
			// MNs, INs and Noises of each nucleus
			List mnNucleus = new ArrayList();
			List rcNucleus = new ArrayList();
			List inNucleus = new ArrayList();

			Neuron neu = null;

			// Collect neurons of this nucleus and put on the lists
			for(int n = 0; n < neurons[x].length; n++)
			{
				neu = neurons[x][n];

				if( neu.getCategory().equals( ReMoto.MN ) )
					mnNucleus.add( neu );
				else if( neu.getType().equals( ReMoto.RC ) )
					rcNucleus.add( neu );
				else if( neu.getType().equals( ReMoto.IaIn ) || neu.getType().equals( ReMoto.IbIn ) || neu.getType().equals( ReMoto.gII ) )
					inNucleus.add( neu );
			}

			// ---------------------------------
			// 1) MNï¿½s synapses inside a nucleus
			// 		a) DT over MN
			// 		b) Noises over MN
			// 		c) IaIn/IbIn/PS over MN
			// 		d) RC over MN
			// 		e) MN over RC
			// ---------------------------------

			for(int m = 0; m < mnNucleus.size(); m++)
			{
				Neuron mnNeu = (Neuron)mnNucleus.get(m);

				// --------------
				// a) DTs and MNs
				for(int d = 0; d < nt.size(); d++)
				{
					Neuron dtNeu = (Neuron)nt.get(d);

					// Place DTs equaly distributed in relation to MN pool
					ConductanceVO g = conf.getSynapseType(dtNeu, mnNeu);
					double connectivity = conf.getSynapticConnectivity(g);
					double rand = Rng.random();

					if( connectivity < rand || connectivity < 0.01 )
						continue;

					createSynapticConductance(conf, g, dtNeu, mnNeu, 1.0);
				}

				// -----------------
				// b) Noises and MNs
				if( noises != null )
				{
					for(int d = 0; d < noises.size(); d++)
					{
						Neuron noise = (Neuron)noises.get(d);

				    	if( !noise.getType().equals( ReMoto.NoiseExcitatoryMN ) && !noise.getType().equals( ReMoto.NoiseInhibitoryMN ) )
				    		continue;

			    		ConductanceVO g = conf.getSynapseType(noise, mnNeu);
						double connectivity = conf.getSynapticConnectivity(g);

						if( connectivity < 0.01 )
							continue;

						// b) Synapses Noises over MN
						// Make synapse only between MN and its respectively noise
						if( d == m )
						{
							createSynapticConductance(conf, g, noise, mnNeu, connectivity);
						}
					}
				}

				// --------------------
				// c) IaIn/IbIn/gII and MNs
				for(int ins = 0; ins < in.size(); ins++)
				{
					neu = (Neuron)in.get(ins);

					// RC synapses are considered bellow
					if( neu.getType().equals( ReMoto.RC ) )
						continue;

					// Place Ins equaly distributed in relation to MN pool
					ConductanceVO g = conf.getSynapseType(neu, mnNeu);
					double connectivity = conf.getSynapticConnectivity(g);
					double rand = Rng.random();

					if( connectivity < rand || connectivity < 0.01 )
						continue;

					createSynapticConductance(conf, g, neu, mnNeu, 1);
				}

				// ------------------------------------------------------------------
				// RCs and MNs: all RCs and MNs of any nucleus can change connections
				for(int r = 0; r < rc.size(); r++)
				{
					Neuron rcNeu = (Neuron)rc.get(r);

					// ----------------------
					// d) Synapses RC over MN

					ConductanceVO g = conf.getSynapseType(rcNeu, mnNeu);
					double connectivity = conf.getSynapticConnectivity(g);
					double distance = Math.abs( rcNeu.getXPosition() - mnNeu.getXPosition() );
					double rand = Rng.random();

					if( connectivity > 0.01 && rand < connectivity)
					{
						double weight = conf.getMiscellaneous( ReMoto.decliningRCtoMN ) / (conf.getMiscellaneous( ReMoto.decliningRCtoMN ) + distance * distance);

						if( weight >= 0.01 )
						{
							createSynapticConductance(conf, g, rcNeu, mnNeu, weight);
						}
					}

					// ----------------------
					// e) Synapses MN over RC

					g = conf.getSynapseType(mnNeu, rcNeu);
					connectivity = conf.getSynapticConnectivity(g);
					rand = Rng.random();

					if( connectivity > 0.01 && rand < connectivity)
					{
						double weight = conf.getMiscellaneous( ReMoto.decliningMNtoRC ) / (conf.getMiscellaneous( ReMoto.decliningMNtoRC ) + distance * distance);

						if( weight >= 0.01 )
						{
							createSynapticConductance(conf, g, mnNeu, rcNeu, weight);
						}
					}
				}
			}

			// ---------------------------------
			// 2) Noises over RC
			// ---------------------------------

			for(int r = 0; r < rcNucleus.size(); r++)
			{
				Neuron rcNeu = (Neuron)rcNucleus.get(r);

				// teste de gMaxTot

				// --------------
				// Noises and RCs

				if( noises != null )
				{
					for(int d = 0; d < noises.size(); d++)
					{
						Neuron noise = (Neuron)noises.get(d);

				    	if( !noise.getType().equals( ReMoto.NoiseExcitatoryRC ) && !noise.getType().equals( ReMoto.NoiseInhibitoryRC ) )
				    		continue;

			    		ConductanceVO g = conf.getSynapseType(noise, rcNeu);
						double connectivity = conf.getSynapticConnectivity(g);

						if( connectivity < 0.01 )
							continue;

						// Synapses Noises over RC
						// Make synapse only between RC and its respectively noise
						if( noise.getIndex() == ((Interneuron)rcNeu).getIndex() )
						{
							createSynapticConductance(conf, g, noise, rcNeu, connectivity);
						}
					}
				}
			}

			// ---------------------------------
			// 3) Other Ins synapses
			// ---------------------------------

			for(int i = 0; i < inNucleus.size(); i++)
			{
				Neuron inNeu = (Neuron)inNucleus.get(i);

				// DTs over Ins
				for(int d = 0; d < nt.size(); d++)
				{
					Neuron dtNeu = (Neuron)nt.get(d);

					// Place DTs equaly distributed in relation to In pool
					ConductanceVO g = conf.getSynapseType(dtNeu, inNeu);
					double connectivity = conf.getSynapticConnectivity(g);
					double rand = Rng.random();

					if( connectivity < rand || connectivity < 0.01 )
						continue;

					createSynapticConductance(conf, g, dtNeu, inNeu, 1.0);
				}

				// Ins over Ins
				for(int ins = 0; ins < in.size(); ins++)
				{
					neu = (Neuron)in.get(ins);

					ConductanceVO g = conf.getSynapseType(neu, inNeu);
					double connectivity = conf.getSynapticConnectivity(g);
					double rand = Rng.random();

					if( connectivity < rand || connectivity < 0.01 )
						continue;

					createSynapticConductance(conf, g, neu, inNeu, 1.0);
				}
			}
		}

		// --------------------------------------
		// 4) Ia's/Ib's/II's synapses among the nuclei
		// 		a) Ia over MN
		// 		b) Ia over IaIn
		//		c) Ia over gII
		//		d) Ib over MN
		// 		e) Ib over IbIn
		//		f) II over MN
		//		g) II over IaIn
		//		h) II over gII
		// --------------------------------------

		// The loop begins with the index of the first regular nucleus
		for(int y = 1; y < neurons.length; y++)
		{
			List iaNucleus = new ArrayList();
			List ibNucleus = new ArrayList();
			List iiNucleus = new ArrayList();

			// Collect neurons of this nucleus and put on the lists
			for(int n = 0; n < neurons[y].length; n++)
			{
				Neuron neu = neurons[y][n];

				if( neu.getType().equals( ReMoto.Ia ) )
					iaNucleus.add( neu );
				else if( neu.getType().equals( ReMoto.Ib ) )
					ibNucleus.add( neu );
				else if( neu.getType().equals( ReMoto.II) )
					iiNucleus.add( neu );
			}

			// There is no synapse to create
			if( iaNucleus.size() == 0 && ibNucleus.size() == 0 && iiNucleus.size() == 0 )
				continue;

			// For the nucleus y, make synapses of afferences over other cells, in all nucleus
			for(int x = 1; x < neurons.length; x++)
			{
				List mnNucleus = new ArrayList();
				List iaInNucleus = new ArrayList();
				List ibInNucleus = new ArrayList();
				List gIINucleus = new ArrayList();

				// Collect neurons of this nucleus and put on the lists
				for(int n = 0; n < neurons[x].length; n++)
				{
					Neuron neu = neurons[x][n];

					if( neu.getCategory().equals( ReMoto.MN ) )
						mnNucleus.add( neu );
					else if( neu.getType().equals( ReMoto.IaIn ) )
						iaInNucleus.add( neu );
					else if( neu.getType().equals( ReMoto.IbIn ) )
						ibInNucleus.add( neu );
					else if( neu.getType().equals( ReMoto.gII ) )
						gIINucleus.add( neu );
				}

				// There is no synapse to create
				if( mnNucleus.size() == 0 && iaInNucleus.size() == 0 && ibInNucleus.size() == 0 && gIINucleus.size() == 0 )
					continue;

				for(int ia = 0; ia < iaNucleus.size(); ia++)
				{
					Neuron iafNeu = (Neuron)iaNucleus.get(ia);

					// ------------------------
					// a) Synapses: Ia over MNs
					for(int a = 0; a < mnNucleus.size(); a++)
					{
						Neuron mnNeu = (Neuron)mnNucleus.get(a);

						ConductanceVO g = conf.getSynapseType(iafNeu, mnNeu);
						double connectivity = conf.getSynapticConnectivity(g);
						double rand = Rng.random();

						if( connectivity <= rand )
							continue;

						createSynapticConductance(conf, g, iafNeu, mnNeu, 1);
					}

					// ------------------------
					// b) Synapses Ia over IaIn
					for(int a = 0; a < iaInNucleus.size(); a++)
					{
						Neuron iaInNeu = (Neuron)iaInNucleus.get(a);

						ConductanceVO g = conf.getSynapseType(iafNeu, iaInNeu);
						double connectivity = conf.getSynapticConnectivity(g);
						double rand = Rng.random();

						if( connectivity < rand )
							continue;

						createSynapticConductance(conf, g, iafNeu, iaInNeu, 1);
					}

					// ------------------------
					// c) Synapses Ia over gII
					for(int a = 0; a < gIINucleus.size(); a++)
					{
						Neuron gIINeu = (Neuron)gIINucleus.get(a);

						ConductanceVO g = conf.getSynapseType(iafNeu, gIINeu);
						double connectivity = conf.getSynapticConnectivity(g);
						double rand = Rng.random();

						if( connectivity < rand )
							continue;

						createSynapticConductance(conf, g, iafNeu, gIINeu, 1);
					}
				}

				for(int ib = 0; ib < ibNucleus.size(); ib++)
				{
					Neuron ibNeu = (Neuron)ibNucleus.get(ib);

					// ------------------------
					// d) Synapses: Ib over MNs
					for(int mn = 0; mn < mnNucleus.size(); mn++)
					{
						Neuron mnNeu = (Neuron)mnNucleus.get(mn);

						ConductanceVO g = conf.getSynapseType(ibNeu, mnNeu);
						double connectivity = conf.getSynapticConnectivity(g);
						double rand = Rng.random();

						if( connectivity <= rand )
							continue;

						createSynapticConductance(conf, g, ibNeu, mnNeu, 1);
					}

					// ------------------------
					// e) Synapses Ib over IbIn
					for(int ibin = 0; ibin < ibInNucleus.size(); ibin++)
					{
						Neuron ibInNeu = (Neuron)ibInNucleus.get(ibin);

						ConductanceVO g = conf.getSynapseType(ibNeu, ibInNeu);
						double connectivity = conf.getSynapticConnectivity(g);
						double rand = Rng.random();

						if( connectivity < rand )
							continue;

						createSynapticConductance(conf, g, ibNeu, ibInNeu, 1);
					}
				}

				for(int ii = 0; ii < iiNucleus.size(); ii++)
				{
					Neuron iiNeu = (Neuron)iiNucleus.get(ii);

					// ------------------------
					// f) Synapses: II over MNs
					for(int mn = 0; mn < mnNucleus.size(); mn++)
					{
						Neuron mnNeu = (Neuron)mnNucleus.get(mn);

						ConductanceVO g = conf.getSynapseType(iiNeu, mnNeu);
						double connectivity = conf.getSynapticConnectivity(g);
						double rand = Rng.random();

						if( connectivity <= rand )
							continue;

						createSynapticConductance(conf, g, iiNeu, mnNeu, 1);
					}

					// ------------------------
					// g) Synapses II over IaIn
					for(int iain = 0; iain < iaInNucleus.size(); iain++)
					{
						Neuron iaInNeu = (Neuron)iaInNucleus.get(iain);

						ConductanceVO g = conf.getSynapseType(iiNeu, iaInNeu);
						double connectivity = conf.getSynapticConnectivity(g);
						double rand = Rng.random();

						if( connectivity < rand )
							continue;

						createSynapticConductance(conf, g, iiNeu, iaInNeu, 1);
					}

					// ------------------------
					// h) Synapses II over gIIn
					for(int gii = 0; gii < gIINucleus.size(); gii++)
					{
						Neuron gIINeu = (Neuron)gIINucleus.get(gii);

						ConductanceVO g = conf.getSynapseType(iiNeu, gIINeu);
						double connectivity = conf.getSynapticConnectivity(g);
						double rand = Rng.random();

						if( connectivity < rand )
							continue;

						createSynapticConductance(conf, g, iiNeu, gIINeu, 1);
					}
				}
			}
		}
	}

	public void createSynapticConductance(Configuration conf, ConductanceVO gType, Neuron neuronPre, Neuron neuronPos, double weight)
	{
		if( gType == null )
			return;

		if( gType.isActive() == false )
			return;

		// ??? teste para saber o numero de sinapses de uma simulacao de larga escala

		// Set dynamics type (depressing, facilitating or none)  ????
		DynamicVO dynamics = conf.getDynamicType(gType.getCdConductanceType(), gType.getCdNucleusPre(), gType.getCdNucleus());
		gType.setDynamics( dynamics );

		String cd = neuronPre.getSynapseKey();

		// Verify synapse polarity and location (which compartment the synapse is located)
		if( gType.getE() > 0 )
		{
			if( gType.getCompartment() != null && gType.getCompartment().equals( ReMoto.somaCompartment ) )
			{
				addSynapse( ((SpinalNeuron)neuronPos).getSomaExcitSynapses(), gType, weight, cd );
				neuronPre.getTransmittingSynapses().add( ((SpinalNeuron)neuronPos).getSomaExcitSynapses() );
			}
			else
			{
				addSynapse( ((SpinalNeuron)neuronPos).getDendExcitSynapses(), gType, weight, cd );
				neuronPre.getTransmittingSynapses().add( ((SpinalNeuron)neuronPos).getDendExcitSynapses() );
			}
		}
		else
		{
			if( gType.getCompartment() != null && gType.getCompartment().equals( ReMoto.somaCompartment ) )
			{
				addSynapse( ((SpinalNeuron)neuronPos).getSomaInhibSynapses(), gType, weight, cd );
				neuronPre.getTransmittingSynapses().add( ((SpinalNeuron)neuronPos).getSomaInhibSynapses() );
			}
			else
			{
				addSynapse( ((SpinalNeuron)neuronPos).getDendInhibSynapses(), gType, weight, cd );
				neuronPre.getTransmittingSynapses().add( ((SpinalNeuron)neuronPos).getDendInhibSynapses() );
			}
		}
	}

	// One synapse of br.remoto.core.model.synapse: gmax goes from nS to uS, scaled by the weight
	private static void addSynapse(SynapseGroup group, ConductanceVO g, double weight, String cd)
	{
		group.add(cd, (g.getGmax() * weight) / 1000.0, g.getDelay(), dynamics(g.getDynamics()));
	}

	// Same reading as the original DynamicProperties: no VO means no dynamics, an unknown type too
	private static SynapticDynamics dynamics(DynamicVO vo)
	{
		if( vo == null )
			vo = new DynamicVO();

		SynapticDynamics.Kind kind = SynapticDynamics.Kind.NONE;
		if( vo.getDynamicType().equals( ReMoto.depressing ) )
			kind = SynapticDynamics.Kind.DEPRESSING;
		else if( vo.getDynamicType().equals( ReMoto.facilitating ) )
			kind = SynapticDynamics.Kind.FACILITATING;

		return new SynapticDynamics(kind, vo.getTau(), vo.getVariation());
	}
}
