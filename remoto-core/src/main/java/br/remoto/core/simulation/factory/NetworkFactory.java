package br.remoto.core.simulation.factory;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import br.remoto.core.simulation.setup.Configuration;
import br.remoto.core.simulation.MotorUnit;
import br.remoto.core.simulation.musculotendon.ExtrafusalMuscleSuperClass;
import br.remoto.core.simulation.musculotendon.DistributionMoments;
import br.remoto.core.simulation.musculotendon.Hill;
import br.remoto.core.simulation.musculotendon.Raikova;
import br.remoto.core.simulation.musculotendon.SecondOrderCriticallyDampedSystem;
import br.remoto.core.simulation.neuron.Neuron;
import br.remoto.core.simulation.ReMoto;
import br.remoto.core.simulation.neuron.Interneuron;
import br.remoto.core.simulation.neuron.Motoneuron;
import br.remoto.core.simulation.neuron.Miscellaneous;
import br.remoto.core.simulation.setup.ConductanceVO;
import br.remoto.core.simulation.setup.JointVO;
import br.remoto.core.simulation.setup.MotorUnitVO;
import br.remoto.core.simulation.setup.Nucleus;
import br.remoto.core.simulation.setup.NeuronVO;
import br.remoto.core.simulation.util.Point;

public class NetworkFactory
{
	public void createNeurons(Configuration conf, Neuron[][] neurons)
	{
		NeuronFactory neuFactory = new NeuronFactory();
    	List nuclei = conf.getNuclei();
    	List neuronTypes = conf.getNeuronTypes();

    	// Create only one Miscellaneous object for all neurons, in order to save memory
    	Miscellaneous misc = new Miscellaneous();

    	misc.setStep( conf.getStep() );
    	misc.setStepByTwo( conf.getStep()/2.0 );
    	misc.setStepBySix( conf.getStep()/6.0 );
    	misc.setGammaCa( conf.getMiscellaneous( ReMoto.gammaCa ) );
    	misc.setMnSomaRefractoryPeriod( conf.getMiscellaneous( ReMoto.mnSomaRefractoryPeriod ) );
    	misc.setMnAxonRefractoryPeriod( conf.getMiscellaneous( ReMoto.mnAxonRefractoryPeriod ) );
    	misc.setRcSomaRefractoryPeriod( conf.getMiscellaneous( ReMoto.inRcSomaRefractoryPeriod ) );
    	misc.setIaInSomaRefractoryPeriod( conf.getMiscellaneous( ReMoto.inIaSomaRefractoryPeriod ) );
    	misc.setIbInSomaRefractoryPeriod( conf.getMiscellaneous( ReMoto.inIbSomaRefractoryPeriod ) );
    	misc.setGIISomaRefractoryPeriod( conf.getMiscellaneous(ReMoto.gIISomaRefractoryPeriod ) );
    	misc.setAfAxonRefractoryPeriod( conf.getMiscellaneous( ReMoto.afAxonRefractoryPeriod ) );

		// --------------------------------------------------
	    // Make a loop among motor nuclei, excluding DT nuclei

    	JointVO jointVO = conf.getJointVO();

    	for(int x = 1; x < jointVO.getNumNuclei() + 1; x++)
		{
	    	Nucleus nuc = (Nucleus)nuclei.get(x);

	    	List listNeurons = new ArrayList();

			// Get references to use in MN an IN creation
	    	NeuronVO referenceS = conf.getCompleteNeuronType(nuc.getCd(), ReMoto.S);
	    	NeuronVO referenceFR = conf.getCompleteNeuronType(nuc.getCd(), ReMoto.FR);
	    	NeuronVO referenceFF = conf.getCompleteNeuronType(nuc.getCd(), ReMoto.FF);

			if( referenceS != null )
				getIonicConductance(conf, referenceS);

			if( referenceFR != null )
				getIonicConductance(conf, referenceFR);

			if( referenceFF != null )
				getIonicConductance(conf, referenceFF);

	    	// ---------------------------------------
		    // Make a loop to create neuron array pool
		    for(int n = 0; n < neuronTypes.size(); n++)
			{
				NeuronVO reference = (NeuronVO)neuronTypes.get(n);

				if( !reference.isActive() || reference.getQuantity() == 0 || !reference.getCdNucleus().equals( nuc.getCd() ) )
		    		continue;

				if( reference.getCategory().equals(ReMoto.MN) || reference.getCategory().equals(ReMoto.IN) )
				{
					getIonicConductance(conf, reference);
				}

				for(int index = 0; index < reference.getQuantity(); index++)
				{
					// ------------------------------------------------
					// Create neuron according to its category and type
					// ------------------------------------------------
					Neuron neu = neuFactory.create(listNeurons, conf, misc, reference, referenceS, referenceFR, referenceFF, index);

					// neu is null if reference is not AF, MN or IN
					if( neu == null )
						continue;

					// Store neuron signals
					if( conf.isStoreSignals() == true )
					{
					    // Store membrane potential for the marked indexes
				    	// The signal store grows on demand (the original reserved it whole, 7 signals per
				    	// step for the entire run, before the simulation started)
					    if( neu.getCategory().equals(ReMoto.MN) &&
						    ( index + 1 == reference.getIndexStoreVm1() || index + 1 == reference.getIndexStoreVm2() ) )
					    {
						    ((Motoneuron)neu).setStoredSignals(true);
					    }
					    else if( neu.getCategory().equals(ReMoto.IN) && index + 1 == reference.getIndexStoreVm1() )
					    {
					    	((Interneuron)neu).setStoredSignals(true);
					    }
					}

			    	// Put neuron in the temporary array
				    listNeurons.add( neu );
				}
			}

		    // Transform ArrayList in Array to speed-up simulations
		    Neuron[] arrayNucleus = new Neuron[listNeurons.size()];

		    for(int n = 0; n < listNeurons.size(); n++)
			{
				arrayNucleus[n] = (Neuron)listNeurons.get(n);
			}

			neurons[x] = arrayNucleus;
		}

    	// Make the spatial positioning of the neurons
    	createNeuralColumn(conf, neurons);
	}

	private void getIonicConductance(Configuration conf, NeuronVO reference)
	{
		ConductanceVO gNaM = conf.getMarcovType(reference.getCdNucleus(), ReMoto.gNaM + "-" + reference.getType(), ReMoto.excitatory);
		ConductanceVO gNaH = conf.getMarcovType(reference.getCdNucleus(), ReMoto.gNaH + "-" + reference.getType(), ReMoto.excitatory);
		ConductanceVO gKN = conf.getMarcovType(reference.getCdNucleus(),  ReMoto.gKN  + "-" + reference.getType(), ReMoto.inhibitory);
		ConductanceVO gKQ = conf.getMarcovType(reference.getCdNucleus(),  ReMoto.gKQ  + "-" + reference.getType(), ReMoto.inhibitory);
		ConductanceVO gCaP = conf.getMarcovType(reference.getCdNucleus(),  ReMoto.gCaP  + "-" + reference.getType(), ReMoto.excitatory);

		reference.setGNaMVO( new ConductanceVO( gNaM ) );
		reference.setGNaHVO( new ConductanceVO( gNaH ) );
		reference.setGKNVO( new ConductanceVO( gKN ) );
		reference.setGKQVO( new ConductanceVO( gKQ ) );
		reference.setGCaPVO(new ConductanceVO( gCaP ) );
	}

	// Create column in the spinal cord ventral horn, placing all simulated cells
	private void createNeuralColumn(Configuration conf, Neuron neurons[][])
	{
    	List nuclei = conf.getNuclei();
		int numS[] = new int[ neurons.length ];
		int numFR[] = new int[ neurons.length ];
		int numFF[] = new int[ neurons.length ];
		int numRC[] = new int[ neurons.length ];
		int numIaIn[] = new int[ neurons.length ];
		int numIbIn[] = new int[ neurons.length ];
		int numGII[] = new int[ neurons.length ];
		double nucleusXini[] = new double[ neurons.length ];
		double nucleusXend[] = new double[ neurons.length ];

    	// ------------------------
	    // Make a loop among nuclei
		// The loop begins with the index of the first regular nucleus
		JointVO jointVO = conf.getJointVO();

    	for(int x = 1; x < jointVO.getNumNuclei() + 1; x++)
		{
	    	Nucleus nuc = (Nucleus)nuclei.get(x);
	    	String cdNucleus = nuc.getCd();

			numS[x] = 0;
			numFR[x] = 0;
			numFF[x] = 0;
			numRC[x] = 0;
			numIaIn[x] = 0;
			numIbIn[x] = 0;
			numGII[x] = 0;

			if( conf.getNeuronType(cdNucleus, ReMoto.S) != null && conf.getNeuronType(cdNucleus, ReMoto.S).isActive() )
				numS[x] = conf.getNeuronType(cdNucleus, ReMoto.S).getQuantity();
			if( conf.getNeuronType(cdNucleus, ReMoto.FR) != null && conf.getNeuronType(cdNucleus, ReMoto.FR).isActive() )
				numFR[x] = conf.getNeuronType(cdNucleus, ReMoto.FR).getQuantity();
			if( conf.getNeuronType(cdNucleus, ReMoto.FF) != null && conf.getNeuronType(cdNucleus, ReMoto.FF).isActive() )
				numFF[x] = conf.getNeuronType(cdNucleus, ReMoto.FF).getQuantity();
			if( conf.getNeuronType(cdNucleus, ReMoto.RC) != null && conf.getNeuronType(cdNucleus, ReMoto.RC).isActive() )
				numRC[x] = conf.getNeuronType(cdNucleus, ReMoto.RC).getQuantity();
			if( conf.getNeuronType(cdNucleus, ReMoto.IaIn) != null && conf.getNeuronType(cdNucleus, ReMoto.IaIn).isActive() )
				numIaIn[x] = conf.getNeuronType(cdNucleus, ReMoto.IaIn).getQuantity();
			if( conf.getNeuronType(cdNucleus, ReMoto.IbIn) != null && conf.getNeuronType(cdNucleus, ReMoto.IbIn).isActive() )
				numIbIn[x] = conf.getNeuronType(cdNucleus, ReMoto.IbIn).getQuantity();
			if( conf.getNeuronType(cdNucleus, ReMoto.gII) != null && conf.getNeuronType(cdNucleus, ReMoto.gII).isActive() )
				numGII[x] = conf.getNeuronType(cdNucleus, ReMoto.gII).getQuantity();

			nucleusXini[x] = conf.getMiscellaneous( "xIni" + cdNucleus );
			nucleusXend[x] = conf.getMiscellaneous( "xEnd" + cdNucleus );
		}

		// The loop begins with the index of the first regular nucleus
		for(int x = 1; x < neurons.length; x++)
		{
			for(int y = 0; y < neurons[x].length; y++)
			{
		    	String type = neurons[x][y].getType();
				int index = neurons[x][y].getIndex();
		    	double quantity = 0;

		    	if( type.equals( ReMoto.gII) )
		    		quantity = numGII[x];
		    	else if( type.equals( ReMoto.RC ) )
		    		quantity = numRC[x];
		    	else if( type.equals( ReMoto.IaIn ) )
		    		quantity = numIaIn[x];
		    	else if( type.equals( ReMoto.IbIn ) )
		    		quantity = numIbIn[x];
		    	if( type.equals( ReMoto.S ) )
		    		quantity = numS[x] + numFR[x] + numFF[x];
		    	else if( type.equals( ReMoto.FR ) )
		    		quantity = numS[x] + numFR[x] + numFF[x];
		    	else if( type.equals( ReMoto.FF ) )
		    		quantity = numS[x] + numFR[x] + numFF[x];

		    	index--;
		    	quantity--;

		    	if( quantity <= 0  )
		    		continue;

		    	double xPosition = nucleusXini[x] + ( nucleusXend[x] - nucleusXini[x] ) * (index/quantity);

				neurons[x][y].setXPosition( xPosition );
			}
		}
	}
}
