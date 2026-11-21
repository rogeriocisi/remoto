package br.remoto.model.factory;

import java.util.List;

import br.remoto.model.Configuration;
import br.remoto.model.Neuron.Neuron;
import br.remoto.model.ReMoto;
import br.remoto.core.model.synapse.SynapseGroup;
import br.remoto.core.model.channel.GatedChannel;
import br.remoto.core.model.channel.SodiumChannel;
import br.remoto.model.Neuron.Interneuron;
import br.remoto.model.Neuron.Motoneuron;
import br.remoto.model.Neuron.SensoryFiber;
import br.remoto.model.Neuron.SpinalNeuron;
import br.remoto.model.Neuron.Miscellaneous;
import br.remoto.model.vo.ConductanceVO;
import br.remoto.model.vo.NeuronVO;
import br.remoto.util.Bias;
import br.remoto.util.Distribution;
import br.remoto.util.ElectroCalculation;

public class NeuronFactory 
{
	
	public Neuron create(List listNucleus, Configuration conf, Miscellaneous misc, NeuronVO reference, NeuronVO referenceS, NeuronVO referenceFR, NeuronVO referenceFF, int index)
	{
		// Bias is used for properties that have two values 
		double bias = 0;
		
		if( reference.getQuantity() > 1 )
			bias = (double)(index)/(double)(reference.getQuantity() - 1);

		// Verify which category of neuron will be created
		if( reference.getCategory().equals( ReMoto.AF ) )
		{
			SensoryFiber sf = new SensoryFiber(reference, index, misc);
			
			String distribution = ReMoto.gradualGaussian;
			
			double axonVelocity = 0;
			double stimulusSpinalDistance = conf.getNerve( reference.getCdNerve() ).getStimulusSpinalEntry();
			double stimulusEndPlateDistance = conf.getNerve( reference.getCdNerve() ).getStimulusEndPlate();

			double recruitmentThreshold = 0;
			
			if(sf.getType().equals("Ia")){
				recruitmentThreshold = Bias.gradual(conf.getInitialRecruitmentThresholdIa(), conf.getFinalRecruitmentThresholdIa(), bias);
			}
			else if(sf.getType().equals("II")){
				recruitmentThreshold = Bias.gradual(conf.getInitialRecruitmentThresholdII(), conf.getFinalRecruitmentThresholdII(), bias);
			}
			else if(sf.getType().equals("Ib")){
				recruitmentThreshold = Bias.gradual(conf.getInitialRecruitmentThresholdIb(), conf.getFinalRecruitmentThresholdIb(), bias);
			}
			
			sf.setRecruitmentThreshold(recruitmentThreshold);
			//System.out.println("recruitmentThreshold: " + recruitmentThreshold);
			
			if( distribution.equals( ReMoto.uniform ) )
			{
				sf.setAxonThreshold( Bias.uniform(reference.getAxonThreshold1(), reference.getAxonThreshold2() ) );
				axonVelocity = Bias.uniform(reference.getAxonVelocity1(), reference.getAxonVelocity2() );
			}
			else if( distribution.equals( ReMoto.gradualGaussian ) )
			{
				sf.setAxonThreshold( Bias.gradualGaussian(reference.getAxonThreshold1(), reference.getAxonThreshold2(), bias, conf.getMiscellaneous( ReMoto.axonThresholdSTD ) ) );
				axonVelocity = Bias.gradualGaussian(reference.getAxonVelocity1(), reference.getAxonVelocity2(), bias, conf.getMiscellaneous( ReMoto.axonVelocitySTD ) );
			}
			else
			{
				sf.setAxonThreshold( Bias.gradual(reference.getAxonThreshold1(), reference.getAxonThreshold2(), bias ) );
				axonVelocity = Bias.gradual(reference.getAxonVelocity1(), reference.getAxonVelocity2(), bias );
			}
			
			sf.setAxonConductionVelocity(axonVelocity);
			sf.setLatency( axonVelocity, stimulusSpinalDistance, stimulusEndPlateDistance  );

			return sf;
		}
		else if( reference.getCategory().equals( ReMoto.MN ) || reference.getCategory().equals( ReMoto.IN ) )
		{
			SpinalNeuron neu = null;
			ElectroCalculation calc = new ElectroCalculation();
			int indexCategory = index + 1;
			
			if( reference.getCategory().equals( ReMoto.MN ) )
			{
				if( reference.getType().equals( ReMoto.FR ) )
					indexCategory += referenceS.getQuantity();
				else if( reference.getType().equals( ReMoto.FF ) )
					indexCategory += referenceS.getQuantity() + referenceFR.getQuantity();
				
				neu = new Motoneuron(reference, index, indexCategory, misc);
				
				NeuronVO pattern = null;
				
				// Always true: no more used
				if( conf.isMerge() == true )
				{
					pattern = calc.getPatternMerged(conf, reference, referenceS, referenceFR, referenceFF, bias);
				}
				else
				{
					pattern = calc.getPattern(conf, reference, referenceS, referenceFF, bias);
				}
	
				double axonVelocity = pattern.getAxonVelocity1();
				double axonThreshold = pattern.getAxonThreshold1();
				double dSoma = pattern.getDsoma1(); 
				double dDend = pattern.getDdend1();
				double lSoma = pattern.getLsoma1();
				double lDend = pattern.getLdend1();
				double rmSoma = pattern.getRmSoma1();
				double rmDend = pattern.getRmDend1();
				double ri = pattern.getRi();
				double cm = pattern.getCm();
				double areaDend = Math.PI * dDend * lDend;
				double areaSoma = Math.PI * dSoma * lSoma;
	
				double gCoupling = calc.calcGCoupling(ri, lDend, dDend, lSoma, dSoma);
				double gLeakSoma = calc.calcGLeak(areaSoma, rmSoma);
				double gLeakDend = calc.calcGLeak(areaDend, rmDend);
				double rn = calc.calcRn(gCoupling, gLeakSoma, gLeakDend);
				
				double cd = calc.calcCapacitance(cm, areaDend);
				double cs = calc.calcCapacitance(cm, areaSoma);
	
				//double rheobase = Distribution.gaussianPoint(pattern.getRheobase1(), pattern.getRheobase1() * conf.getMiscellaneous( ReMoto.reobaseSTD ));
				//double threshold = rheobase * rn;
				
				double threshold = Distribution.gaussianPoint(pattern.getThreshold1(), pattern.getThreshold1() * conf.getMiscellaneous( ReMoto.thresholdSTD ));
				double thresholdCa = Distribution.gaussianPoint(pattern.getThresholdCa1(), pattern.getThresholdCa1() * conf.getMiscellaneous( ReMoto.thresholdCaSTD ) );

				// Set capacitances - converted to nF
				neu.setCapacitanceDend( cd );
				neu.setCapacitanceSoma( cs );
				
				// Set coupling conductance between soma and dendrites - converted to uS
				neu.setGCoupling( gCoupling );
				
				// Set leak conductances - converted to uS
				neu.setGLeakSoma( gLeakSoma );
				neu.setGLeakDend( gLeakDend );
		
				// Set threshold according to rheobase, gLeakSoma, gLeakDend and gCoupling
				neu.setThreshold( threshold );
				neu.setThresholdCa( thresholdCa );
				neu.setRn( rn );
	
				double stimulusSpinalDistance = conf.getNerve( reference.getCdNerve() ).getStimulusSpinalEntry();
				double stimulusEndPlateDistance = conf.getNerve( reference.getCdNerve() ).getStimulusEndPlate();

				// Set axon threshold and latencies
				
				if(conf.getRecruitmentOrderFES().equals("linear")){
					((Motoneuron)neu).setAxonThreshold( axonThreshold );
				}
				
				/*
				else if(conf.getRecruitmentOrderFES().equals("gaussian")){
					((Motoneuron)neu).setAxonThreshold( Distribution.gaussianPoint(15, 3) );
				}
				else if(conf.getRecruitmentOrderFES().equals("uniform")){
					((Motoneuron)neu).setAxonThreshold( Bias.uniform(12, 18) );
				}
				*/
				
				((Motoneuron)neu).setAxonConductionVelocity( axonVelocity );
				((Motoneuron)neu).setLatencies( axonVelocity, stimulusSpinalDistance, stimulusEndPlateDistance );
	
				// Set gMax according to the soma area and put in nS
				
				pattern.getGNaMVO().setGmax( pattern.getGNaMVO().getGmax() * 1e6 * areaSoma );
				pattern.getGNaHVO().setGmax( pattern.getGNaHVO().getGmax() * 1e6 * areaSoma );
				pattern.getGKNVO().setGmax( pattern.getGKNVO().getGmax() * 1e6 * areaSoma );
				pattern.getGKQVO().setGmax( pattern.getGKQVO().getGmax() * 1e6 * areaSoma );
				pattern.getGCaPVO().setGmax( pattern.getGCaPVO().getGmax() * 1e6 * areaDend );
					
				// Create Na and K conductances for somatic compartment
				neu.setGNa( sodium( pattern.getGNaMVO(), pattern.getGNaHVO() ) );
				neu.setGKf( fastPotassium( pattern.getGKNVO() ) );
				neu.setGKs( slowPotassium( pattern.getGKQVO() ) );
				
				// Create Ca conductances for dendritic compartment
				((Motoneuron)neu).setGCa( calcium( pattern.getGCaPVO() ) );
			}
			else
			{
				neu = new Interneuron(reference, index, indexCategory, misc);
				
				double cm = reference.getCm();
				//double areaSoma = (Math.PI * reference.getDsoma() * reference.getLsoma()) / 1e8;
				double area = reference.getTotalArea() / 1e8;
				// Set leak conductances - convert to uS
				neu.setGLeakSoma( calc.calcGLeak(area, reference.getRmSoma1()) );
			
				// Set capacitances - convert to nF
				neu.setCapacitanceSoma( calc.calcCapacitance(cm, area) );
			
				//double rheobase = Bias.gradual(reference.getRheobase1(), reference.getRheobase2(), bias );
	
				double threshold = Bias.gradual(reference.getThreshold1(), reference.getThreshold2(), bias );
				
				// Set threshold according to rheobase and gLeakSoma
				//neu.setThreshold( rheobase / neu.getGLeakSoma() );
				neu.setThreshold( threshold );
				
				
				neu.setRn( 1 / neu.getGLeakSoma() );
	
				// Set gMax according to the soma area and put in nS
				ConductanceVO gNaMVO = new ConductanceVO( reference.getGNaMVO() );
				ConductanceVO gNaHVO = new ConductanceVO( reference.getGNaHVO() );
				ConductanceVO gKNVO = new ConductanceVO( reference.getGKNVO() );
				ConductanceVO gKQVO = new ConductanceVO( reference.getGKQVO() );
				
				gNaMVO.setGmax( reference.getGNaMVO().getGmax() * 1e6 * area );
				gNaHVO.setGmax( reference.getGNaHVO().getGmax() * 1e6 * area );
				gKNVO.setGmax( reference.getGKNVO().getGmax() * 1e6 * area );
				gKQVO.setGmax( reference.getGKQVO().getGmax() * 1e6 * area );
					
				// Create Na and K conductances for somatic compartment
				neu.setGNa( sodium( gNaMVO, gNaHVO ) );
				neu.setGKf( fastPotassium( gKNVO ) );
				neu.setGKs( slowPotassium( gKQVO ) );
			}
			
			// Ensure an estimated initial capacity for the spike train
		    neu.ensureCapacity( (int)( conf.getTFin() ) );
	
		    // Create synapses
			ConductanceVO gExc = conf.getMarcovType(neu.getCdNucleus(), neu.getType(), ReMoto.excitatory);
			ConductanceVO gInh = conf.getMarcovType(neu.getCdNucleus(), neu.getType(), ReMoto.inhibitory);
			
			neu.setDendExcitSynapses( synapseGroup(gExc) );
			neu.setDendInhibSynapses( synapseGroup(gInh) );
			neu.setSomaExcitSynapses( synapseGroup(gExc) );
			neu.setSomaInhibSynapses( synapseGroup(gInh) );

			/* Included in StimulationFactory::injectCurrent(Configuration conf, Neuron neurons[][])
			 * 
		    // Get Current properties
		    Current inj = conf.getCurrent(neu.getCdNucleus(), neu.getType());
		    
			// Set injected current
		    if( inj != null && inj.isActive() )
		    {
		    	inj.setStep( conf.getStep() );
	
		    	if( inj.getSignal() == null )
		    	{
			    	ModulatingSignal signal = conf.getModulationSignal(reference.getType(), reference.getCdNucleus());
			    	inj.setSignal( signal );
		    	}
		    	
		    	// The same injected current for all neurons
		    	neu.setCurrent( inj );
		    }
		    */
			
			return neu;
		}
		
		// If reference is not AF, MN or IN, return null
		return null;
	}


	// Ionic channels of br.remoto.core.model.channel, from the conductance types of the
	// configuration: gmax goes from nS to uS, and the pulse lasts TPEAK
	private static SodiumChannel sodium(ConductanceVO m, ConductanceVO h)
	{
		return new SodiumChannel(m.getGmax() / 1000, m.getE(), m.getTpeak(),
				m.getAlpha(), m.getBeta(), h.getAlpha(), h.getBeta());
	}
	
	private static GatedChannel fastPotassium(ConductanceVO g)
	{
		return GatedChannel.fastPotassium(g.getGmax() / 1000, g.getE(), g.getTpeak(), g.getAlpha(), g.getBeta());
	}
	
	private static GatedChannel slowPotassium(ConductanceVO g)
	{
		return GatedChannel.slowPotassium(g.getGmax() / 1000, g.getE(), g.getTpeak(), g.getAlpha(), g.getBeta());
	}
	
	private static GatedChannel calcium(ConductanceVO g)
	{
		return GatedChannel.calcium(g.getGmax() / 1000, g.getE(), g.getTpeak(), g.getAlpha(), g.getBeta());
	}

	// Synapses of one kind on one compartment (br.remoto.core.model.synapse), with the kinetics of
	// the MARKOV_TYPE of the neuron
	private static SynapseGroup synapseGroup(ConductanceVO g)
	{
		return new SynapseGroup(g.getE(), g.getTpeak(), g.getTmax(), g.getAlpha(), g.getBeta(), g.isActive());
	}
}
