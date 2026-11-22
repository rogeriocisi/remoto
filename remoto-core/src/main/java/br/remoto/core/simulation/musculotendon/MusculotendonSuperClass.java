package br.remoto.core.simulation.musculotendon;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Hashtable;

import br.remoto.core.model.joint.MusculotendonGeometry;
import br.remoto.core.simulation.setup.Configuration;
import br.remoto.core.simulation.MotorUnit;
import br.remoto.core.simulation.ReMoto;
import br.remoto.core.simulation.joint.AnkleIsometricModel;
import br.remoto.core.simulation.joint.InvertedPendulumModel;
import br.remoto.core.simulation.joint.PositionTaskModel;
import br.remoto.core.simulation.musculotendon.ExtrafusalMuscleSuperClass;
import br.remoto.core.simulation.musculotendon.NonInnervatedTendon;
import br.remoto.core.simulation.proprioceptor.GolgiTendonOrgan;
import br.remoto.core.simulation.proprioceptor.MuscleSpindle;
import br.remoto.core.simulation.util.Conversion;
import br.remoto.core.simulation.util.Sample;
import br.remoto.core.simulation.util.Signal;

public class MusculotendonSuperClass
{
	protected Sample sampler1;
	protected Sample sampler2;

	protected ArrayList lengthStore = new ArrayList();
	protected ArrayList momentArmStore = new ArrayList();

	ExtrafusalMuscleSuperClass extrafusalMuscle;
	NonInnervatedTendon tendon;

	GolgiTendonOrgan gto;
	MuscleSpindle spindle;

	AnkleIsometricModel associatedJoint;
	InvertedPendulumModel associatedJoint2;
	PositionTaskModel associatedJoint3;

	private String cdMuscle;

	private double length;
	private double initialLength;
	private double velocity = 0;
	private double momentArm;

	private double force;

	// Length and moment arm at the ankle, in br.remoto.core.model.joint
	private final MusculotendonGeometry geometry;

	Configuration conf;
	String JointModel;

	public MusculotendonSuperClass(Configuration conf, ExtrafusalMuscleSuperClass extrafusalMuscle, NonInnervatedTendon tendon,
									MuscleSpindle spindle, GolgiTendonOrgan gto) {
		this.extrafusalMuscle = extrafusalMuscle;
		this.tendon = tendon;
		this.gto = gto;
		this.spindle = spindle;
		this.cdMuscle = extrafusalMuscle.getCdMuscle();
		this.geometry = new MusculotendonGeometry(this.cdMuscle);
		this.conf = conf;
		this.JointModel = conf.getCdJointModel();

		sampler1 = new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		sampler2 = new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());

		System.out.println("Creating musculotendon nucleus: " + extrafusalMuscle.getCdMuscle());
	}

	private void calculateLength(String cdMuscle, double ankleAngle, double kneeAngle){
		geometry.updateLength(ankleAngle, kneeAngle);
		length = geometry.length();
	}

	private void calculateMomentArmAnkle(String cdMuscle, double ankleAngle, double kneeAngle){
		geometry.updateMomentArm(ankleAngle, kneeAngle);
		momentArm = geometry.momentArm();
	}

	public void atualize(double t){
		if(JointModel.equals("isometric")){
			calculateLength(cdMuscle, associatedJoint.getAngle()*(180/Math.PI), associatedJoint.getKneeAngle());
			if (t == 0)	extrafusalMuscle.setInitialLengthNorm();
			calculateMomentArmAnkle(cdMuscle, associatedJoint.getAngle()*(180/Math.PI),  associatedJoint.getKneeAngle());

			extrafusalMuscle.atualize(t);

			if(extrafusalMuscle.getMuscleModel().equals("hill")){
				tendon.atualize(t,
						length,
						extrafusalMuscle.getLengthNorm() * extrafusalMuscle.getOptimalLength(),
						extrafusalMuscle.getPennationAngle());

				force = tendon.getForce();
				gto.atualize(t, tendon.getForce());
				spindle.atualize(t, extrafusalMuscle.getLengthNorm(), extrafusalMuscle.getStretchVelocity(), extrafusalMuscle.getStretchAcceleration() );
			}
			else{
				force = extrafusalMuscle.getForce();
				gto.atualize(t, force);
			}

			sampler1.sample(lengthStore, "", t, length);
			sampler2.sample(momentArmStore, "", t, momentArm);
		}
		else if(JointModel.equals("pendulum")){
			calculateLength(cdMuscle, associatedJoint2.getAngle()*(180/Math.PI), associatedJoint2.getKneeAngle());
			if (t == 0)	extrafusalMuscle.setInitialLengthNorm();
			calculateMomentArmAnkle(cdMuscle, associatedJoint2.getAngle()*(180/Math.PI),  associatedJoint2.getKneeAngle());

			extrafusalMuscle.atualize(t);

			if(extrafusalMuscle.getMuscleModel().equals("hill")){
				tendon.atualize(t,
						length,
						extrafusalMuscle.getLengthNorm() * extrafusalMuscle.getOptimalLength(),
						extrafusalMuscle.getPennationAngle());

				force = tendon.getForce();
				gto.atualize(t, tendon.getForce());
			}
			else{
				force = extrafusalMuscle.getForce();
				gto.atualize(t, force);
			}

			spindle.atualize(t, extrafusalMuscle.getLengthNorm(), extrafusalMuscle.getStretchVelocity(), extrafusalMuscle.getStretchAcceleration() );

			sampler1.sample(lengthStore, "", t, length);
			sampler2.sample(momentArmStore, "", t, momentArm);
		}
		else if(JointModel.equals("position")){
			calculateLength(cdMuscle, associatedJoint3.getAngle()*(180/Math.PI), associatedJoint3.getKneeAngle());
			if (t == 0)	extrafusalMuscle.setInitialLengthNorm();
			calculateMomentArmAnkle(cdMuscle, associatedJoint3.getAngle()*(180/Math.PI),  associatedJoint3.getKneeAngle());

			extrafusalMuscle.atualize(t);

			if(extrafusalMuscle.getMuscleModel().equals("hill")){
				tendon.atualize(t,
						length,
						extrafusalMuscle.getLengthNorm() * extrafusalMuscle.getOptimalLength(),
						extrafusalMuscle.getPennationAngle());

				force = tendon.getForce();
				gto.atualize(t, tendon.getForce());
			}
			else{
				force = extrafusalMuscle.getForce();
				gto.atualize(t, force);
			}

			spindle.atualize(t, extrafusalMuscle.getLengthNorm(), extrafusalMuscle.getStretchVelocity(), extrafusalMuscle.getStretchAcceleration() );

			sampler1.sample(lengthStore, "", t, length);
			sampler2.sample(momentArmStore, "", t, momentArm);
		}
	}

	// Polynomial fit from the data reported in Arnold et al. (2010) -- [L.A.E. 31/05/2012]

	public ExtrafusalMuscleSuperClass getMuscle(){
		return extrafusalMuscle;
	}

	public void setMuscle(ExtrafusalMuscleSuperClass muscle) {
		// TODO Auto-generated method stub
		extrafusalMuscle = muscle;
	}

	public NonInnervatedTendon getTendon() {
		return tendon;
	}

	public void setTendon(NonInnervatedTendon tendon) {
		this.tendon = tendon;
	}

	public String getCdMuscle() {
		return cdMuscle;
	}

	public void setCdMuscle(String cdMuscle) {
		this.cdMuscle = cdMuscle;
	}

	public GolgiTendonOrgan getGto() {
		return gto;
	}

	public void setGto(GolgiTendonOrgan gto) {
		this.gto = gto;
	}

	public MuscleSpindle getSpindle() {
		return spindle;
	}

	public void setSpindle(MuscleSpindle spindle) {
		this.spindle = spindle;
	}

	public double getLength() {
		return length;
	}

	public void setLength(double length) {
		this.length = length;
	}

	public double getInitialLength() {
		return initialLength;
	}

	public void setInitialLength(double initialLength) {
		this.initialLength = initialLength;
	}

	public double getVelocity() {
		return velocity;
	}

	public void setVelocity(double velocity) {
		this.velocity = velocity;
	}

	public double getMomentArm() {
		return momentArm;
	}

	public void setMomentArm(double momentArm) {
		this.momentArm = momentArm;
	}

	public double getForce() {
		return force;
	}

	public void setForce(double force) {
		this.force = force;
	}

	public AnkleIsometricModel getAssociatedJoint() {
		return associatedJoint;
	}

	public void setAssociatedJoint(AnkleIsometricModel associatedJoint) {
		this.associatedJoint = associatedJoint;
	}

	public InvertedPendulumModel getAssociatedJoint2() {
		return associatedJoint2;
	}

	public void setAssociatedJoint2(InvertedPendulumModel associatedJoint2) {
		this.associatedJoint2 = associatedJoint2;
	}

	public PositionTaskModel getAssociatedJoint3() {
		return associatedJoint3;
	}

	public void setAssociatedJoint3(PositionTaskModel associatedJoint3) {
		this.associatedJoint3 = associatedJoint3;
	}

	public ArrayList getLengthStore() {
		return lengthStore;
	}

	public void setLengthStore(ArrayList lengthStore) {
		this.lengthStore = lengthStore;
	}

	public ArrayList getMomentArmStore() {
		return momentArmStore;
	}

	public void setMomentArmStore(ArrayList momentArmStore) {
		this.momentArmStore = momentArmStore;
	}
}

