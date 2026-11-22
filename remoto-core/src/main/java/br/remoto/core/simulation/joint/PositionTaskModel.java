package br.remoto.core.simulation.joint;

import br.remoto.core.model.joint.IsometricAnkle;
import br.remoto.core.model.joint.PositionTaskAnkle;
import br.remoto.core.simulation.setup.Configuration;
import br.remoto.core.simulation.joint.AnkleSuperClass;
import br.remoto.core.simulation.musculotendon.MusculotendonSuperClass;
import br.remoto.core.simulation.util.Sample;

/**
 * The position task of the original model: the samplers of its signals. The joint is
 * br.remoto.core.model.joint.PositionTaskAnkle.
 */
public class PositionTaskModel extends AnkleSuperClass
{
	private static final long serialVersionUID = 1L;

	private final PositionTaskAnkle ankle;
	private final IsometricAnkle.Actuator actuators[];

	public PositionTaskModel(Configuration conf, MusculotendonSuperClass[] musculotendons){
		super(conf.getJointVO());

		samplerJointAngleStore = new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerJointVelocityStore = new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerJointTorqueStore = new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerJointMuscleTorqueStore = new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerJointGravTorqueStore = new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerJointPassiveTorqueStore = new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerJointCenterMassStore = new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerJointCenterPressureStore = new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerJointDisturbanceStore = new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());

		this.ankle = new PositionTaskAnkle(conf.getJointAngle(), conf.getKneeAngle(), conf.getpercentageOfMVC(), conf.getStep());
		this.actuators = InvertedPendulumModel.actuators(musculotendons);

		kneeAngle = ankle.kneeAngle();
		percentageOfMVC = conf.getpercentageOfMVC();
	}

	public void atualize(double t){
		ankle.prepare(t);

		// The samples are taken before the update: they hold the values of the previous step
		samplerJointAngleStore.sample(jointAngleStore, "", t, ankle.angle() * (180/Math.PI));
		samplerJointVelocityStore.sample(jointVelocityStore,"", t, ankle.velocity() * (180/Math.PI));
		samplerJointTorqueStore.sample(jointTorqueStore, "", t, ankle.jointTorque());
		samplerJointMuscleTorqueStore.sample(jointMuscleTorqueStore, "", t, ankle.muscleTorque());
		samplerJointGravTorqueStore.sample(jointGravTorqueStore, "", t, ankle.gravitationalTorque());
		samplerJointPassiveTorqueStore.sample(jointPassiveTorqueStore, "", t, ankle.passiveTorque());
		samplerJointCenterMassStore.sample(jointCenterMassStore, "", t, ankle.centerMass() * 1000);
		samplerJointCenterPressureStore.sample(jointCenterPressureStore, "", t, ankle.centerPressure() * 1000);
		samplerJointDisturbanceStore.sample(jointDisturbanceStore, "", t, ankle.disturbance());

		ankle.advance(t, actuators);

		angle = ankle.angle();
		velocity = ankle.velocity();
		acceleration = ankle.acceleration();
		torque = ankle.muscleTorque();
		gravitationalTorque = ankle.gravitationalTorque();
		passiveTorque = ankle.passiveTorque();
		centerMass = ankle.centerMass();
		centerPressure = ankle.centerPressure();
		disturbance = ankle.disturbance();
	}
}
