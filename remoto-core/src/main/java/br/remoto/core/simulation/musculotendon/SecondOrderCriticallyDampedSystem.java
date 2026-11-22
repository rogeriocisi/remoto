package br.remoto.core.simulation.musculotendon;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

import br.remoto.core.model.muscle.CriticallyDampedMuscle;
import br.remoto.core.simulation.setup.Configuration;
import br.remoto.core.simulation.MotorUnit;
import br.remoto.core.simulation.musculotendon.ExtrafusalMuscleSuperClass;
import br.remoto.core.simulation.util.Sample;

/**
 * The second-order critically damped (SOCDS) muscle of the original model: the samplers of its
 * signals. The muscle (twitches, saturation, force) is
 * br.remoto.core.model.muscle.CriticallyDampedMuscle.
 */
public class SecondOrderCriticallyDampedSystem extends ExtrafusalMuscleSuperClass
{
	private final CriticallyDampedMuscle muscle;

	private final Map<br.remoto.core.model.muscle.MotorUnit, MotorUnit> byUnit = new IdentityHashMap<>();

	public SecondOrderCriticallyDampedSystem(Configuration conf, String CdMuscle, MotorUnit[] mu, String cdMuscleModel) {
		super(conf, CdMuscle, mu, cdMuscleModel);

		br.remoto.core.model.muscle.MotorUnit units[] = new br.remoto.core.model.muscle.MotorUnit[motorunits.length];
		for(int i = 0; i < motorunits.length; i++){
			units[i] = motorunits[i].unit();
			byUnit.put(units[i], motorunits[i]);
		}

		// The twitches are sampled with the step of the motoneurons, the same of the simulation
		this.muscle = new CriticallyDampedMuscle(units, conf.getStep(), conf.getStep());
		this.muscle.setUnitForceListener( (unit, t, f) ->
			samplerMotorUnitForceStore.sample(motorUnitForceStore, byUnit.get(unit).getIndexString(), t, f) );

		samplerLengthNormStore 				= new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerVelocityStore 				= new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerAccelerationStore 			= new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerMotorUnitForceStore 			= new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerForceStore 					= new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());

		System.out.println("Creating SOCDS muscle");
	}

	/** This muscle in br.remoto.core.model.muscle. */
	public CriticallyDampedMuscle muscle()
	{
		return muscle;
	}

	public double instantMotorUnitForce(String cdNeuron, double t)
	{
		MotorUnit mu = pickMotorUnit(cdNeuron);

		return muscle.unitForce(mu == null ? null : mu.unit(), t);
	}

	public double instantMuscleForce(double t)
	{
		return muscle.muscleForce(t);
	}

	@Override
	public void atualize(double t) {
		muscle.step(t);

		lengthNorm = muscle.lengthNorm();
		stretchVelocity = muscle.stretchVelocity();
		stretchAcceleration = muscle.stretchAcceleration();
		force = muscle.force();

		samplerLengthNormStore.sample(lengthNormStore, "", t, lengthNorm);
		samplerVelocityStore.sample(velocityStore, "", t, stretchVelocity);
		samplerAccelerationStore.sample(accelerationStore, "", t, stretchAcceleration);
		samplerForceStore.sample(forceStore, "", t, force);
	}

	@Override
	public void setInitialLengthNorm() {
	}

	public void setParameters(String cdMuscle, ArrayList muscles){
	}

	public double getMaximumMuscleForce() {
		return muscle.maximumMuscleForce();
	}
}
