package br.remoto.core.simulation.musculotendon;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Hashtable;

import br.remoto.core.simulation.setup.Configuration;
import br.remoto.core.simulation.MotorUnit;
import br.remoto.core.simulation.ReMoto;
import br.remoto.core.simulation.util.Sample;
import br.remoto.core.simulation.util.Signal;
import br.remoto.core.simulation.util.DoubleSeries;

public abstract class MuscleSuperClass implements Serializable
{
	private static final long serialVersionUID = 1L;

	protected Sample samplerSTypeMuscleActivationStore;
	protected Sample samplerFTypeMuscleActivationStore;
	protected Sample samplerMuscleActivationStore;
	protected Sample samplerLengthStore;
	protected Sample samplerLengthNormStore;
	protected Sample samplerVelocityStore;
	protected Sample samplerAccelerationStore;
	protected Sample samplerForceParallelElementStore;
	protected Sample samplerForceViscousElementStore;
	protected Sample samplerForceActiveSTypeStore;
	protected Sample samplerForceActiveFTypeStore;
	protected Sample samplerForceStore;
	protected Sample samplerMotorUnitForceStore;
	protected Sample samplerPennationAngleStore;

	protected DoubleSeries lengthStore = new DoubleSeries();

	protected DoubleSeries lengthNormStore = new DoubleSeries();
	protected DoubleSeries velocityStore = new DoubleSeries();
	protected DoubleSeries accelerationStore = new DoubleSeries();

	protected DoubleSeries muscleActivationStore = new DoubleSeries();

	protected DoubleSeries forceParallelElementStore = new DoubleSeries();
	protected DoubleSeries forceViscousElementStore = new DoubleSeries();

	protected DoubleSeries forceActiveSTypeStore = new DoubleSeries();
	protected DoubleSeries forceActiveFTypeStore = new DoubleSeries();

	protected DoubleSeries pennationAngleStore = new DoubleSeries();

	protected double activationNorm;

	protected String cdMuscle;

	protected double force;
	protected double forceNorm;

	protected double length;
	protected double lengthNorm;
	protected double pennationAngle;

	protected double lastNormLength = 0;
	protected double lastStretchVelocity = 0;

	protected double velocity = 0;
	protected double acceleration = 0;
	protected double stretchVelocity = 0;
	protected double stretchAcceleration = 0;
	protected double shorteningVelocity = 0;
	protected double shorteningAcceleration = 0;

	protected Configuration conf;

	public MuscleSuperClass(Configuration conf, String cdMuscle)
	{
		this.conf = conf;
		this.cdMuscle = cdMuscle;

		samplerSTypeMuscleActivationStore 	= new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerFTypeMuscleActivationStore 	= new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		samplerMuscleActivationStore 		= new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
	}

	public String getCdMuscle() {
		return cdMuscle;
	}

	public void setCdMuscle(String cdMuscle) {
		this.cdMuscle = cdMuscle;
	}

	public double getLength() {
		return length;
	}

	public void setLength(double length) {
		this.length = length;
	}

	public double getPennationAngle() {
		return pennationAngle;
	}

	public void setPennationAngle(double pennationAngle) {
		this.pennationAngle = pennationAngle;
	}

	public double getLengthNorm() {
		return lengthNorm;
	}

	public void setLengthNorm(double lengthNorm) {
		this.lengthNorm = lengthNorm;
	}

	public DoubleSeries getLengthNormStore() {
		return lengthNormStore;
	}

	public void setLengthNormStore(DoubleSeries lengthNormStore) {
		this.lengthNormStore = lengthNormStore;
	}

	public DoubleSeries getVelocityStore() {
		return velocityStore;
	}

	public void setVelocityStore(DoubleSeries velocityStore) {
		this.velocityStore = velocityStore;
	}

	public DoubleSeries getAccelerationStore() {
		return accelerationStore;
	}

	public void setAccelerationStore(DoubleSeries accelerationStore) {
		this.accelerationStore = accelerationStore;
	}

	public void calculateVelocityAndAccelerationUsingDifferentiation(double t){
		if(t == 0){
			stretchVelocity = 0;
			stretchAcceleration = 0;
		}else{
			stretchVelocity = (lengthNorm - lastNormLength) / conf.getStep();
			stretchAcceleration = (stretchVelocity - lastStretchVelocity) / conf.getStep();
		}

		lastNormLength = lengthNorm;
		lastStretchVelocity = stretchVelocity;
	}

	protected void atualizeActivation(double t){
		samplerMuscleActivationStore.sample(muscleActivationStore, t, activationNorm);
	}

	public double getStretchVelocity() {
		return stretchVelocity;
	}

	public void setStretchVelocity(double stretchVelocity) {
		this.stretchVelocity = stretchVelocity;
	}

	public double getStretchAcceleration() {
		return stretchAcceleration;
	}

	public void setStretchAcceleration(double stretchAcceleration) {
		this.stretchAcceleration = stretchAcceleration;
	}

	public double getActivationNorm() {
		return activationNorm;
	}

	public void setActivationNorm(double activationNorm) {
		this.activationNorm = activationNorm;
	}

	public DoubleSeries getMuscleActivationStore() {
		return muscleActivationStore;
	}

	public void setMuscleActivationStore(DoubleSeries muscleActivationStore) {
		this.muscleActivationStore = muscleActivationStore;
	}

	public DoubleSeries getForceParallelElementStore() {
		return forceParallelElementStore;
	}

	public void setForceParallelElementStore(DoubleSeries forceParallelElementStore) {
		this.forceParallelElementStore = forceParallelElementStore;
	}

	public DoubleSeries getForceViscousElementStore() {
		return forceViscousElementStore;
	}

	public void setForceViscousElementStore(DoubleSeries forceViscousElementStore) {
		this.forceViscousElementStore = forceViscousElementStore;
	}

	public DoubleSeries getForceActiveSTypeStore() {
		return forceActiveSTypeStore;
	}

	public void setForceActiveSTypeStore(DoubleSeries forceActiveSTypeStore) {
		this.forceActiveSTypeStore = forceActiveSTypeStore;
	}

	public DoubleSeries getForceActiveFTypeStore() {
		return forceActiveFTypeStore;
	}

	public void setForceActiveFTypeStore(DoubleSeries forceActiveFTypeStore) {
		this.forceActiveFTypeStore = forceActiveFTypeStore;
	}

	public double getForceNorm() {
		return forceNorm;
	}

	public void setForceNorm(double forceNorm) {
		this.forceNorm = forceNorm;
	}

	public double getForce() {
		return force;
	}

	public void setForce(double force) {
		this.force = force;
	}

	public double getShorteningVelocity() {
		return shorteningVelocity;
	}

	public void setShorteningVelocity(double shorteningVelocity) {
		this.shorteningVelocity = shorteningVelocity;
	}

	public double getShorteningAcceleration() {
		return shorteningAcceleration;
	}

	public void setShorteningAcceleration(double shorteningAcceleration) {
		this.shorteningAcceleration = shorteningAcceleration;
	}

	public DoubleSeries getLengthStore() {
		return lengthStore;
	}

	public void setLengthStore(DoubleSeries lengthStore) {
		this.lengthStore = lengthStore;
	}

	public DoubleSeries getPennationAngleStore() {
		return pennationAngleStore;
	}

	public void setPennationAngleStore(DoubleSeries pennationAngleStore) {
		this.pennationAngleStore = pennationAngleStore;
	}
}

