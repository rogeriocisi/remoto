package br.remoto.core.simulation.musculotendon;

import java.util.ArrayList;

import br.remoto.core.model.muscle.DistributionMomentsMuscle;
import br.remoto.core.simulation.setup.Configuration;
import br.remoto.core.simulation.MotorUnit;
import br.remoto.core.simulation.musculotendon.ExtrafusalMuscleSuperClass;
import br.remoto.core.simulation.util.Sample;

/**
 * The distribution-moments muscle of the original model: the samplers of its signals. The muscle
 * is br.remoto.core.model.muscle.DistributionMomentsMuscle.
 */
public class DistributionMoments extends ExtrafusalMuscleSuperClass
{
	private final DistributionMomentsMuscle muscle;

	public DistributionMoments(Configuration conf, String CdMuscle, MotorUnit[] mu, String cdMuscleModel) {
		super(conf, CdMuscle, mu, cdMuscleModel);

		br.remoto.core.model.muscle.MotorUnit units[] = new br.remoto.core.model.muscle.MotorUnit[motorunits.length];
		for(int i = 0; i < motorunits.length; i++)
			units[i] = motorunits[i].unit();

		this.muscle = new DistributionMomentsMuscle(units);

		samplerMotorUnitForceStore 	= new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerForceStore 			= new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());

		System.out.println("Creating Distributed Moments muscle");
	}

	/** This muscle in br.remoto.core.model.muscle. */
	public DistributionMomentsMuscle muscle()
	{
		return muscle;
	}

	@Override
	public double instantMuscleForce(double t) {
		return muscle.activation(t, true);
	}

	@Override
	public double instantMotorUnitForce(String cdNeuron, double t) {
		return muscle.activation(t, false);
	}

	@Override
	public void atualize(double t) {
		muscle.step(t);

		activationNormSType = muscle.activationNormSType();
		activationNormFType = muscle.activationNormFType();
		activationNorm = muscle.activationNorm();
		atualizeActivation(t);

		samplerForceStore.sample(forceStore, "", t, force);
	}

	@Override
	public void setInitialLengthNorm() {
	}

	public void setParameters(String cdMuscle, ArrayList muscles){
	}

	@Override
	public double getMaximumMuscleForce() {
		return 0;
	}
}
