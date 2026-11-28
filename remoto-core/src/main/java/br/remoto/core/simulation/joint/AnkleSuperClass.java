package br.remoto.core.simulation.joint;

import java.util.ArrayList;

import br.remoto.core.simulation.musculotendon.MuscleSuperClass;
import br.remoto.core.simulation.neuron.Miscellaneous;
import br.remoto.core.simulation.setup.JointVO;
import br.remoto.core.simulation.util.Sample;
import br.remoto.core.simulation.util.Signal;
import br.remoto.core.simulation.util.DoubleSeries;

public abstract class AnkleSuperClass
{
	private static final long serialVersionUID = 1L;

	protected String cd;
	protected String name;
	protected int numNuclei;
	protected int numMotorNuclei;
	protected int numNerves;
	protected int ind;

	protected Sample samplerJointAngleStore;
	protected Sample samplerJointVelocityStore;
	protected Sample samplerJointMuscleTorqueStore;
	protected Sample samplerJointTorqueStore;
	protected Sample samplerJointGravTorqueStore;
	protected Sample samplerJointPassiveTorqueStore;
	protected Sample samplerJointCenterMassStore;
	protected Sample samplerJointCenterPressureStore;
	protected Sample samplerJointDisturbanceStore;

	protected DoubleSeries jointAngleStore = new DoubleSeries();
	protected DoubleSeries jointVelocityStore = new DoubleSeries();
	protected DoubleSeries jointMuscleTorqueStore = new DoubleSeries();
	protected DoubleSeries jointTorqueStore = new DoubleSeries();
	protected DoubleSeries jointGravTorqueStore = new DoubleSeries();
	protected DoubleSeries jointPassiveTorqueStore = new DoubleSeries();
	protected DoubleSeries jointCenterMassStore = new DoubleSeries();
	protected DoubleSeries jointCenterPressureStore = new DoubleSeries();
	protected DoubleSeries jointDisturbanceStore = new DoubleSeries();

	protected double angle;
	protected double refAngle = 0 * (Math.PI/180);
	protected double velocity = 0;
	protected double acceleration = 0;
	protected double torque;
	protected double gravitationalTorque;
	protected double passiveTorque;
	protected double centerMass;
	protected double centerPressure;
	protected double percentageOfMVC;
	protected double disturbance;

	protected double kneeAngle;

	public AnkleSuperClass(JointVO vo){
		System.out.println("Creating Ankle Joint");

		cd = vo.getCd();
		name = vo.getName();
		numNuclei = vo.getNumNuclei();
		numMotorNuclei = vo.getNumMotorNuclei();
		numNerves = vo.getNumNerves();
		ind = vo.getInd();

		angle = 0 * (Math.PI/180);
		torque = 0;

		kneeAngle = 0;
	}

	public abstract void atualize(double t);

	public double getAngle() {
		return angle;
	}

	public void setAngle(double angle) {
		this.angle = angle;
	}

	public double getVelocity() {
		return velocity;
	}

	public void setVelocity(double velocity) {
		this.velocity = velocity;
	}

	public double getCenterMass() {
		return centerMass;
	}

	public void setCenterPressure(double centerPressure) {
		this.centerPressure = centerPressure;
	}

	public double getTorque() {
		return torque;
	}

	public void setTorque(double torque) {
		this.torque = torque;
	}

	public double getGravTorque() {
		return gravitationalTorque;
	}

	public void setGravTorque(double gravitationalTorque) {
		this.gravitationalTorque = gravitationalTorque;
	}
	public double getPassiveTorque() {
		return passiveTorque;
	}

	public void setPassiveTorque(double passiveTorque) {
		this.passiveTorque = passiveTorque;
	}

	public DoubleSeries getJointAngleStore() {
		return jointAngleStore;
	}

	public void setJointAngleStore(DoubleSeries jointAngleStore) {
		this.jointAngleStore = jointAngleStore;
	}

	public DoubleSeries getJointVelocityStore() {
		return jointVelocityStore;
	}

	public void setJointVelocityStore(DoubleSeries jointVelocityStore) {
		this.jointVelocityStore = jointVelocityStore;
	}

	public DoubleSeries getJointCenterMassStore() {
		return jointCenterMassStore;
	}

	public void setJointCenterMassStore(DoubleSeries jointCenterMassStore) {
		this.jointCenterMassStore = jointCenterMassStore;
	}

	public DoubleSeries getJointCenterPressureStore() {
		return jointCenterPressureStore;
	}

	public void setJointCenterPressureStore(DoubleSeries jointCenterPressureStore) {
		this.jointCenterPressureStore = jointCenterPressureStore;
	}

	public DoubleSeries getJointTorqueStore() {
		return jointTorqueStore;
	}

	public void setJointDisturbanceStore(DoubleSeries jointDisturbanceStore) {
		this.jointDisturbanceStore = jointDisturbanceStore;
	}

	public DoubleSeries getJointDisturbanceStore() {
		return jointDisturbanceStore;
	}

	public void setJointTorqueStore(DoubleSeries jointTorqueStore) {
		this.jointTorqueStore = jointTorqueStore;
	}

	public DoubleSeries getJointMuscleTorqueStore() {
		return jointMuscleTorqueStore;
	}

	public void setJointMuscleTorqueStore(DoubleSeries jointMuscleTorqueStore) {
		this.jointMuscleTorqueStore = jointMuscleTorqueStore;
	}

	public DoubleSeries getJointGravTorqueStore() {
		return jointGravTorqueStore;
	}

	public void setJointGravTorqueStore(DoubleSeries jointGravTorqueStore) {
		this.jointGravTorqueStore = jointGravTorqueStore;
	}

	public DoubleSeries getJointPassiveTorqueStore() {
		return jointPassiveTorqueStore;
	}

	public void setJointPassiveTorqueStore(DoubleSeries jointPassiveTorqueStore) {
		this.jointPassiveTorqueStore = jointPassiveTorqueStore;
	}

	public String getCd() {
		return cd;
	}

	public void setCd(String cd) {
		this.cd = cd;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getNumNuclei() {
		return numNuclei;
	}

	public void setNumNuclei(int numNuclei) {
		this.numNuclei = numNuclei;
	}

	public int getNumMotorNuclei() {
		return numMotorNuclei;
	}

	public void setNumMotorNuclei(int numMotorNuclei) {
		this.numMotorNuclei = numMotorNuclei;
	}

	public int getNumNerves() {
		return numNerves;
	}

	public void setNumNerves(int numNerves) {
		this.numNerves = numNerves;
	}

	public int getInd() {
		return ind;
	}

	public void setInd(int ind) {
		this.ind = ind;
	}

	public double getKneeAngle() {
		return kneeAngle;
	}

	public void setKneeAngle(double kneeAngle) {
		this.kneeAngle = kneeAngle;
	}

	public double getpercentageOfMVC() {
		return percentageOfMVC;
	}

	public void setpercentageOfMVC(double percentageOfMVC) {
		this.percentageOfMVC = percentageOfMVC;
	}

	public double getDisturbance() {
		return disturbance;
	}

	public void setDisturbance(double disturbance) {
		this.disturbance = disturbance;
	}
}