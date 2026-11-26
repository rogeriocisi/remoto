package br.remoto.core.simulation.factory;

import br.remoto.core.model.random.Draws;
import br.remoto.core.model.random.Gradient;

import java.util.List;

import br.remoto.core.simulation.setup.Configuration;
import br.remoto.core.simulation.neuron.Neuron;
import br.remoto.core.simulation.ReMoto;
import br.remoto.core.model.synapse.SynapseGroup;
import br.remoto.core.model.channel.GatedChannel;
import br.remoto.core.model.channel.SodiumChannel;
import br.remoto.core.model.neuron.ContinuousMotoneuronMembrane;
import br.remoto.core.simulation.neuron.Interneuron;
import br.remoto.core.simulation.neuron.Motoneuron;
import br.remoto.core.simulation.neuron.SensoryFiber;
import br.remoto.core.simulation.neuron.SpinalNeuron;
import br.remoto.core.simulation.neuron.Miscellaneous;
import br.remoto.core.simulation.setup.ConductanceVO;
import br.remoto.core.simulation.setup.NeuronVO;
import br.remoto.core.simulation.util.ElectroCalculation;

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
				recruitmentThreshold = Gradient.linear(conf.getInitialRecruitmentThresholdIa(), conf.getFinalRecruitmentThresholdIa(), bias);
			}
			else if(sf.getType().equals("II")){
				recruitmentThreshold = Gradient.linear(conf.getInitialRecruitmentThresholdII(), conf.getFinalRecruitmentThresholdII(), bias);
			}
			else if(sf.getType().equals("Ib")){
				recruitmentThreshold = Gradient.linear(conf.getInitialRecruitmentThresholdIb(), conf.getFinalRecruitmentThresholdIb(), bias);
			}

			sf.setRecruitmentThreshold(recruitmentThreshold);

			if( distribution.equals( ReMoto.uniform ) )
			{
				sf.setAxonThreshold( Draws.uniform(reference.getAxonThreshold1(), reference.getAxonThreshold2() ) );
				axonVelocity = Draws.uniform(reference.getAxonVelocity1(), reference.getAxonVelocity2() );
			}
			else if( distribution.equals( ReMoto.gradualGaussian ) )
			{
				sf.setAxonThreshold( Gradient.linearWithNoise(reference.getAxonThreshold1(), reference.getAxonThreshold2(), bias, conf.getMiscellaneous( ReMoto.axonThresholdSTD ) ) );
				axonVelocity = Gradient.linearWithNoise(reference.getAxonVelocity1(), reference.getAxonVelocity2(), bias, conf.getMiscellaneous( ReMoto.axonVelocitySTD ) );
			}
			else
			{
				sf.setAxonThreshold( Gradient.linear(reference.getAxonThreshold1(), reference.getAxonThreshold2(), bias ) );
				axonVelocity = Gradient.linear(reference.getAxonVelocity1(), reference.getAxonVelocity2(), bias );
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

				double threshold = Draws.gaussian(pattern.getThreshold1(), pattern.getThreshold1() * conf.getMiscellaneous( ReMoto.thresholdSTD ));
				double thresholdCa = Draws.gaussian(pattern.getThresholdCa1(), pattern.getThresholdCa1() * conf.getMiscellaneous( ReMoto.thresholdCaSTD ) );

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

				// Conduction block of the slowest axons of the pool (scenario settings
				// "axonConductionBlock"), as in the demyelinating version of the Fortran ReMoto: the
				// velocity 0 becomes the minimum of 0.01 m/s, a latency of tens of seconds, so the spike
				// of the soma never reaches the terminal and the motor unit makes no force.
				int poolSize = referenceS.getQuantity() + referenceFR.getQuantity() + referenceFF.getQuantity();

				if( indexCategory <= (int)( poolSize * reference.getBlockedAxons() ) )
					axonVelocity = 0;

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

				// The continuous-kinetics membrane has no pulse channels: it takes the same
				// maximal conductances and reversal potentials directly
				if( ((Motoneuron)neu).membrane() instanceof ContinuousMotoneuronMembrane c )
					continuousConductances( c, pattern, bias, areaSoma );
			}
			else
			{
				neu = new Interneuron(reference, index, indexCategory, misc);

				double cm = reference.getCm();
				double area = reference.getTotalArea() / 1e8;
				// Set leak conductances - convert to uS
				neu.setGLeakSoma( calc.calcGLeak(area, reference.getRmSoma1()) );

				// Set capacitances - convert to nF
				neu.setCapacitanceSoma( calc.calcCapacitance(cm, area) );

				double threshold = Gradient.linear(reference.getThreshold1(), reference.getThreshold2(), bias );

				// Set threshold according to rheobase and gLeakSoma
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

			// The spike trains grow on demand. The original reserved one entry per millisecond of
			// simulation for every neuron (a firing rate of 1000 spikes/s): in a 30 s scenario with
			// thousands of neurons that was hundreds of megabytes before the first step.

		    // Create synapses
			ConductanceVO gExc = conf.getMarcovType(neu.getCdNucleus(), neu.getType(), ReMoto.excitatory);
			ConductanceVO gInh = conf.getMarcovType(neu.getCdNucleus(), neu.getType(), ReMoto.inhibitory);

			neu.setDendExcitSynapses( synapseGroup(gExc) );
			neu.setDendInhibSynapses( synapseGroup(gInh) );
			neu.setSomaExcitSynapses( synapseGroup(gExc) );
			neu.setSomaInhibSynapses( synapseGroup(gInh) );

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

	/**
	 * Persistent sodium over the pool, in S/cm², from the published parameter set of the model
	 * this membrane transcribes: it has a gradient of its own, which is why it is not a fraction
	 * of the fast sodium. The source grades it along a curve; here the interpolation is linear,
	 * like every other per-unit parameter of the ReMoto, until the comparison of the f-I curve
	 * says whether the curve shape matters.
	 *
	 * <p>The configuration's gmax is S/cm² times 1e-5, which is what makes
	 * {@code gmax * 1e6 * area} come out in nS; from S/cm² the same conductance in uS is
	 * {@code value * area / 100}.
	 */
	private static final double PERSISTENT_SODIUM_FIRST = 0.00043;
	private static final double PERSISTENT_SODIUM_LAST = 0.00067;

	/**
	 * The maximal conductances of the continuous-kinetics membrane, in uS: the ones the pulse
	 * channels share come from the same values (already scaled by area, in nS here), and the
	 * persistent sodium, which the original model does not have, from its own gradient.
	 *
	 * @param bias     where this neuron sits in the pool, 0 at the first and 1 at the last
	 * @param areaSoma the soma area in µm², the same one the other conductances were scaled by
	 */
	private static void continuousConductances(ContinuousMotoneuronMembrane c, NeuronVO pattern,
			double bias, double areaSoma)
	{
		c.setGNaBar( pattern.getGNaMVO().getGmax() / 1000 );
		c.setGKfBar( pattern.getGKNVO().getGmax() / 1000 );
		c.setGKsBar( pattern.getGKQVO().getGmax() / 1000 );
		c.setGCaLBar( pattern.getGCaPVO().getGmax() / 1000 );

		double gNap = Gradient.linear( PERSISTENT_SODIUM_FIRST, PERSISTENT_SODIUM_LAST, bias );
		c.setGNapBar( gNap * areaSoma / 100 );

		c.setENa( pattern.getGNaMVO().getE() );
		c.setEK( pattern.getGKNVO().getE() );
		c.setECaL( pattern.getGCaPVO().getE() );
		c.reset();
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
