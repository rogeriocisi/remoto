package br.remoto.core.simulation.joint;

import java.util.ArrayList;

import br.remoto.core.simulation.setup.Configuration;
import br.remoto.core.simulation.ReMoto;
import br.remoto.core.simulation.neuron.Miscellaneous;
import br.remoto.core.simulation.setup.JointVO;
import br.remoto.core.simulation.util.Signal;
import br.remoto.core.simulation.joint.AnkleSuperClass;
import br.remoto.core.simulation.joint.AnkleIsometricModel;
import br.remoto.core.simulation.joint.InvertedPendulumModel;
import br.remoto.core.simulation.joint.PositionTaskModel;
import br.remoto.core.simulation.musculotendon.MusculotendonSuperClass;
import br.remoto.core.simulation.musculotendon.MuscleSuperClass;
import br.remoto.core.simulation.musculotendon.ExtrafusalMuscleSuperClass;
import br.remoto.core.simulation.util.DoubleSeries;

public class Joint
{
	private static final long serialVersionUID = 1L;

	protected AnkleIsometricModel ankleModel;
	protected InvertedPendulumModel ankleModel2;
	protected PositionTaskModel ankleModel3;

	Configuration conf;
	String JointModel;

	public Joint(Configuration conf){
		this.conf = conf;
		this.JointModel = conf.getCdJointModel();
		System.out.println("Creating Joint");
	}

	public void atualize(double t){
		if(ReMoto.isometricAnkle(JointModel))
			ankleModel.atualize(t);
		else if(JointModel.equals("pendulum"))
			ankleModel2.atualize(t);
		else if(JointModel.equals("position"))
			ankleModel3.atualize(t);
	}

	public AnkleIsometricModel getAnkleModel() {
		return ankleModel;
	}

	public void setAnkleModel(AnkleIsometricModel ankleModel) {
		this.ankleModel = ankleModel;
	}

	public InvertedPendulumModel getAnkleModel2() {
		return ankleModel2;
	}

	public void setAnkleModel2(InvertedPendulumModel ankleModel2) {
		this.ankleModel2 = ankleModel2;
	}

	public PositionTaskModel getAnkleModel3() {
		return ankleModel3;
	}

	public void setAnkleModel3(PositionTaskModel ankleModel3) {
		this.ankleModel3 = ankleModel3;
	}

	public DoubleSeries getJointAngleStore() {
		if(JointModel.equals("pendulum"))
			return ankleModel2.getJointAngleStore();
		else if(JointModel.equals("position"))
			return ankleModel3.getJointAngleStore();
		else
			return ankleModel.getJointAngleStore();
	}

	public DoubleSeries getJointVelocityStore() {
		if(JointModel.equals("position"))
			return ankleModel3.getJointVelocityStore();
		else
			return ankleModel2.getJointVelocityStore();
	}

	public DoubleSeries getJointCenterMassStore(){
		return ankleModel2.getJointCenterMassStore();
	}

	public DoubleSeries getJointCenterPressureStore(){
		return ankleModel2.getJointCenterPressureStore();
	}

	public DoubleSeries getJointTorqueStore(){
		if(JointModel.equals("pendulum"))
			return ankleModel2.getJointTorqueStore();
		else if(JointModel.equals("position"))
			return ankleModel3.getJointTorqueStore();
		else
			return ankleModel.getJointTorqueStore();
	}

	public DoubleSeries getJointMuscleTorqueStore(){
		if(JointModel.equals("position"))
			return ankleModel3.getJointMuscleTorqueStore();
		else
			return ankleModel2.getJointMuscleTorqueStore();
	}

	public DoubleSeries getJointGravTorqueStore(){
		if(JointModel.equals("position"))
			return ankleModel3.getJointGravTorqueStore();
		else
			return ankleModel2.getJointGravTorqueStore();
	}

	public DoubleSeries getJointPassiveTorqueStore(){
		if(JointModel.equals("position"))
			return ankleModel3.getJointPassiveTorqueStore();
		else
			return ankleModel2.getJointPassiveTorqueStore();
	}

	public DoubleSeries getJointDisturbanceStore(){
		if(JointModel.equals("position"))
			return ankleModel3.getJointDisturbanceStore();
		else
			return ankleModel2.getJointDisturbanceStore();
	}
}
