package br.remoto.core.simulation.joint;

import br.remoto.core.model.joint.IsometricAnkle;
import br.remoto.core.simulation.ModulatingSignal;
import br.remoto.core.simulation.ReMoto;
import br.remoto.core.simulation.setup.Configuration;
import br.remoto.core.simulation.joint.AnkleSuperClass;
import br.remoto.core.simulation.musculotendon.MusculotendonSuperClass;
import br.remoto.core.simulation.util.Sample;

/**
 * The isometric ankle of the original model: the samplers of angle and torque. The joint is
 * br.remoto.core.model.joint.IsometricAnkle.
 */
public class AnkleIsometricModel extends AnkleSuperClass
{
	private static final long serialVersionUID = 1L;

	private final IsometricAnkle ankle;
	private final IsometricAnkle.Actuator actuators[];

	public AnkleIsometricModel(Configuration conf, MusculotendonSuperClass[] musculotendons){
		super(conf.getJointVO());

		samplerJointAngleStore = new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerJointTorqueStore = new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());

		ModulatingSignal angleSignal = conf.getJointAngleSignal();
		this.ankle = ReMoto.imposedAngle.equals(conf.getCdJointModel()) && angleSignal != null
				? new IsometricAnkle(conf.getJointAngle(), conf.getKneeAngle(), angleSignal::value)
				: new IsometricAnkle(conf.getJointAngle(), conf.getKneeAngle());

		this.actuators = new IsometricAnkle.Actuator[musculotendons.length];
		for(int i = 0; i < musculotendons.length; i++){
			MusculotendonSuperClass mt = musculotendons[i];
			if( mt != null )
				actuators[i] = new IsometricAnkle.Actuator() {
					public double force() { return mt.getForce(); }
					public double momentArm() { return mt.getMomentArm(); }
				};
		}

		kneeAngle = ankle.kneeAngle();
	}

	public void atualize(double t){
		// The samples are taken before the update: they hold the values of the previous step
		samplerJointAngleStore.sample(jointAngleStore, t, angle*(180/Math.PI));
		samplerJointTorqueStore.sample(jointTorqueStore, t, torque);

		ankle.step(t, actuators);

		angle = ankle.angle();
		torque = ankle.torque();
	}
}
