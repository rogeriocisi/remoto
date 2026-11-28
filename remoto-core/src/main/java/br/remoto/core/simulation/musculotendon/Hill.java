package br.remoto.core.simulation.musculotendon;

import br.remoto.core.model.muscle.HillMuscle;
import br.remoto.core.simulation.setup.Configuration;
import br.remoto.core.simulation.MotorUnit;
import br.remoto.core.simulation.musculotendon.ExtrafusalMuscleSuperClass;
import br.remoto.core.simulation.setup.MuscleVO;
import br.remoto.core.simulation.util.Sample;

/**
 * The Hill-type muscle of the original model: the samplers of its signals. The muscle is
 * br.remoto.core.model.muscle.HillMuscle.
 */
public class Hill extends ExtrafusalMuscleSuperClass
{
	private final HillMuscle muscle;

	public Hill(Configuration conf, String cdMuscle, MotorUnit[] mu, String cdMuscleModel){
		super(conf, cdMuscle, mu, cdMuscleModel);

		MuscleVO vo = conf.getMuscle(cdMuscle, "hill");

		samplerLengthStore 					= new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerLengthNormStore 				= new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerVelocityStore 				= new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerAccelerationStore 			= new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerForceParallelElementStore 	= new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerForceViscousElementStore 	= new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerForceActiveSTypeStore 		= new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerForceActiveFTypeStore 		= new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerForceStore 					= new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerPennationAngleStore 			= new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());

		br.remoto.core.model.muscle.MotorUnit units[] = new br.remoto.core.model.muscle.MotorUnit[motorunits.length];
		for(int i = 0; i < motorunits.length; i++)
			units[i] = motorunits[i].unit();

		// The twitches are sampled with the step of the motoneurons, the same of the simulation
		this.muscle = new HillMuscle(cdMuscle, units,
				new HillMuscle.Parameters(vo.getOptimalLength(), vo.getPennationAngle(), vo.getSlackTendonLength(),
						vo.getMaximumMuscleForce(), vo.getMuscleMass(), vo.getViscosityCoeficient(),
						vo.getElasticCoeficientOfParallelElement()),
				conf.getStep(), conf.getStep(),
				() -> associatedMusculotendon.getTendon().getForce());

		optimalLength = vo.getOptimalLength();
		slackTendonLength = vo.getSlackTendonLength();
		viscosityCoeficient = vo.getViscosityCoeficient();
		elasticCoeficientOfParallelElement = vo.getElasticCoeficientOfParallelElement();
	}

	/** This muscle in br.remoto.core.model.muscle. */
	public HillMuscle muscle()
	{
		return muscle;
	}

	public void setInitialLengthNorm(){
		muscle.setInitialLengthNorm();

		lengthNorm = muscle.lengthNorm();
		length = muscle.length();
	}

	public void atualize(double t)
	{
		// The samples are taken before the update: they hold the values of the previous step
		samplerLengthStore.sample(lengthStore, t, muscle.length());
		samplerLengthNormStore.sample(lengthNormStore, t, muscle.lengthNorm());
		samplerVelocityStore.sample(velocityStore, t, muscle.velocity());
		samplerAccelerationStore.sample(accelerationStore, t, muscle.acceleration());
		samplerForceParallelElementStore.sample(forceParallelElementStore, t, muscle.forceParallelElement());
		samplerForceViscousElementStore.sample(forceViscousElementStore, t, muscle.forceViscousElement());
		samplerForceActiveSTypeStore.sample(forceActiveSTypeStore, t, muscle.forceActiveSType());
		samplerForceActiveFTypeStore.sample(forceActiveFTypeStore, t, muscle.forceActiveFType());
		samplerPennationAngleStore.sample(pennationAngleStore, t, muscle.pennationAngle() * 180 / Math.PI);
		samplerForceStore.sample(forceStore, t, muscle.force());

		muscle.step(t);

		activationNormSType = muscle.activationNormSType();
		activationNormFType = muscle.activationNormFType();
		activationNorm = muscle.activationNorm();
		atualizeActivation(t);

		pennationAngle = muscle.pennationAngle();
		length = muscle.length();
		lengthNorm = muscle.lengthNorm();
		velocity = muscle.velocity();
		acceleration = muscle.acceleration();
		stretchVelocity = muscle.stretchVelocity();
		stretchAcceleration = muscle.stretchAcceleration();
		force = muscle.force();
		forceNorm = muscle.forceNorm();
	}

	@Override
	public double instantMuscleForce(double t) {
		return force;
	}

	@Override
	public double instantMotorUnitForce(String cdNeuron, double t) {
		return 0;
	}

	public double getMaximumMuscleForce() {
		return muscle.maximumMuscleForce();
	}

	public double getMass() {
		return muscle.mass();
	}
}
