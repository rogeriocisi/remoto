package br.remoto.core.simulation.musculotendon;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

import br.remoto.core.model.muscle.RaikovaMuscle;
import br.remoto.core.simulation.setup.Configuration;
import br.remoto.core.simulation.MotorUnit;
import br.remoto.core.simulation.musculotendon.ExtrafusalMuscleSuperClass;
import br.remoto.core.simulation.util.Sample;

/**
 * The Raikova-Aladjov muscle of the original model: the samplers of its signals. The muscle is
 * br.remoto.core.model.muscle.RaikovaMuscle.
 */
public class Raikova extends ExtrafusalMuscleSuperClass
{
	private final RaikovaMuscle muscle;

	private final Map<br.remoto.core.model.muscle.MotorUnit, MotorUnit> byUnit = new IdentityHashMap<>();

	public Raikova(Configuration conf, String CdMuscle, MotorUnit[] mu, String cdMuscleModel) {
		super(conf, CdMuscle, mu, cdMuscleModel);

		samplerMotorUnitForceStore 			= new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerForceStore 					= new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerLengthNormStore 				= new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerVelocityStore 				= new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerAccelerationStore 			= new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());

		br.remoto.core.model.muscle.MotorUnit units[] = new br.remoto.core.model.muscle.MotorUnit[motorunits.length];
		for(int i = 0; i < motorunits.length; i++){
			units[i] = motorunits[i].unit();
			byUnit.put(units[i], motorunits[i]);
		}

		this.muscle = new RaikovaMuscle(units, conf.getStep());
		this.muscle.setUnitForceListener( (unit, t, f) ->
			samplerMotorUnitForceStore.sample(motorUnitForceStore, byUnit.get(unit).getIndexString(), t, f) );

		System.out.println("Creating Raikova muscle");
	}

	/** This muscle in br.remoto.core.model.muscle. */
	public RaikovaMuscle muscle()
	{
		return muscle;
	}

	public double instantMuscleForce(double t)
	{
		return muscle.muscleForce(t);
	}

	public double instantMotorUnitForce(String cdNeuron, double t)
	{
		MotorUnit mu = pickMotorUnit(cdNeuron);

		return muscle.unitForce(mu == null ? null : mu.unit(), t);
	}

	@Override
	public void atualize(double t) {
		muscle.step(t);

		lengthNorm = muscle.lengthNorm();
		stretchVelocity = muscle.stretchVelocity();
		stretchAcceleration = muscle.stretchAcceleration();
		force = muscle.force();

		samplerLengthNormStore.sample(lengthNormStore, t, lengthNorm);
		samplerVelocityStore.sample(velocityStore, t, stretchVelocity);
		samplerAccelerationStore.sample(accelerationStore, t, stretchAcceleration);
		samplerForceStore.sample(forceStore, t, force);
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
