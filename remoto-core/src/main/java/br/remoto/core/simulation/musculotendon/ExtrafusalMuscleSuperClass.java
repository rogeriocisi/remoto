package br.remoto.core.simulation.musculotendon;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Hashtable;

import br.remoto.core.simulation.setup.Configuration;
import br.remoto.core.simulation.ModulatingSignal;
import br.remoto.core.simulation.MotorUnit;
import br.remoto.core.simulation.ReMoto;
import br.remoto.core.simulation.util.Conversion;
import br.remoto.core.simulation.util.Sample;
import br.remoto.core.simulation.util.Signal;
import br.remoto.core.simulation.musculotendon.MusculotendonSuperClass;
import br.remoto.core.simulation.musculotendon.MuscleSuperClass;
import br.remoto.core.simulation.setup.MuscleVO;
import br.remoto.core.simulation.util.DoubleSeries;

public abstract class ExtrafusalMuscleSuperClass extends MuscleSuperClass
{
	protected MotorUnit motorunits[];
	protected Hashtable mn_mu = new Hashtable();

	protected DoubleSeries forceStore = new DoubleSeries();
	protected ArrayList motorUnitForceStore = new ArrayList();

	protected double optimalLength;

	protected double viscosityCoeficient;
	protected double elasticCoeficientOfParallelElement;
	protected double c_T;
    protected double k_T;
    protected double Lr_T;

	protected double slackTendonLength;

	protected double activationNormSType;
	protected double activationNormFType;

	protected DoubleSeries STypeMuscleActivationStore = new DoubleSeries();
	protected DoubleSeries FTypeMuscleActivationStore = new DoubleSeries();

	protected double timeScale;

	protected String muscleModel;

	protected MusculotendonSuperClass associatedMusculotendon;

	public ExtrafusalMuscleSuperClass(Configuration conf, String cdMuscle, MotorUnit[] mu, String cdMuscleModel) {
		super(conf, cdMuscle);
		this.muscleModel = cdMuscleModel;
		this.motorunits = mu;

		MuscleVO vo = conf.getMuscle(cdMuscle, "hill");

		c_T = vo.getC_T();
		k_T = vo.getK_T();
		Lr_T = vo.getLR_T();
	}

	public abstract void setInitialLengthNorm();

	public void reset(double sample, boolean emgAttenuation)
	{
		for(int n = 0; n < motorunits.length; n++)
		{
			motorunits[n].reset( sample, emgAttenuation );
		}
	}

	public abstract double instantMuscleForce(double t);

	public abstract double instantMotorUnitForce(String cdNeuron, double t);

	public double instantMuscleEMG(double t)
	{
		double output = 0;

		for(int n = 0; n < motorunits.length; n++)
		{
			output += motorunits[n].getEMGSignal(t);
		}

		return output;
	}

	public double instantMotorUnitEMG(String cdNeuron, double t)
	{
		// Pick mu in hashtable
		MotorUnit mu = pickMotorUnit(cdNeuron);

        if( mu == null )
        	return 0;

		return mu.getEMGSignal(t);
	}

	public abstract void atualize(double t);

	protected void atualizeActivation(double t){
		super.atualizeActivation(t);

		samplerSTypeMuscleActivationStore.sample(STypeMuscleActivationStore, t, activationNormSType);
		samplerFTypeMuscleActivationStore.sample(FTypeMuscleActivationStore, t, activationNormFType);
	}

	public double calculateLengthNormAux(double force, double t) {
		double lengthNormAux = 0;

		if(cdMuscle.equals("SOL")){
			ModulatingSignal signal = new ModulatingSignal();

			signal.setCdSignal(ReMoto.triangle);
			signal.setTini(100);
			signal.setTfin(110);
			signal.setAmp(0.00);
			signal.setWidth(5);

			lengthNormAux = 1 + signal.value(t);
		}
		else{
			lengthNormAux = 1;
		}

		return lengthNormAux;
	}

	// pickMotorUnit(motorunits[n].getCd()) for each n, resolved once: the force loop asked the
	// Hashtable for it on every step
	private transient MotorUnit pickedByPosition[];
	private transient MotorUnit pickedFrom[];

	protected MotorUnit pickMotorUnitAt(int n)
	{
		if( pickedByPosition == null || pickedFrom != motorunits )
		{
			MotorUnit picked[] = new MotorUnit[motorunits.length];

			for(int i = 0; i < motorunits.length; i++)
				picked[i] = pickMotorUnit(motorunits[i].getCd());

			pickedByPosition = picked;
			pickedFrom = motorunits;
		}

		return pickedByPosition[n];
	}

	public MotorUnit pickMotorUnit(String cdNeuron)
	{
		// Try to pick fiber in hashtable
		MotorUnit mu = (MotorUnit)mn_mu.get(cdNeuron);

		if( mu == null )
		{
			for(int n = 0; n < motorunits.length; n++)
	        {
	        	String cd = motorunits[n].getCd();

	        	if( cdNeuron.equals( cd ) )
	        	{
	        		mu = motorunits[n];

	        		mn_mu.put(cdNeuron, mu);

	        		break;
	        	}
	        }
		}

		return mu;
	}

	public MotorUnit[] getMotorunits() {
		return motorunits;
	}

	public void setFb(MotorUnit[] mu) {
		this.motorunits = mu;
	}

	public double getOptimalLength() {
		return optimalLength;
	}

	public void setOptimalLength(double optimalLength) {
		this.optimalLength = optimalLength;
	}

	public double getViscosityCoeficient() {
		return viscosityCoeficient;
	}

	public void setViscosityCoeficient(double viscosityCoeficient) {
		this.viscosityCoeficient = viscosityCoeficient;
	}

	public double getElasticCoeficientOfParallelElement() {
		return elasticCoeficientOfParallelElement;
	}

	public void setElasticCoeficientOfParallelElement(
			double elasticCoeficientOfParallelElement) {
		this.elasticCoeficientOfParallelElement = elasticCoeficientOfParallelElement;
	}

	public DoubleSeries getForceStore() {
		return forceStore;
	}

	public void setForceStore(DoubleSeries forceStore) {
		this.forceStore = forceStore;
	}

	public ArrayList getMotorUnitForceStore(String cdNeuron) {
		MotorUnit mu = pickMotorUnit(cdNeuron);
		ArrayList aux = new ArrayList();
		Signal signal;

		for(int i = 0; i < motorUnitForceStore.size(); i++){
			signal = (Signal) motorUnitForceStore.get(i);

			if(signal.getType().equals(String.valueOf(mu.getIndex()))){
				aux.add(signal);
			}
		}

		return aux;
	}

	public void setMotorUnitForceStore(ArrayList motorUnitForceStore) {
		this.motorUnitForceStore = motorUnitForceStore;
	}

	public double getActivationNormSType() {
		return activationNormSType;
	}

	public void setActivationNormSType(double activationNormSType) {
		this.activationNormSType = activationNormSType;
	}

	public double getActivationNormFType() {
		return activationNormFType;
	}

	public void setActivationNormFType(double activationNormFType) {
		this.activationNormFType = activationNormFType;
	}

	public DoubleSeries getSTypeMuscleActivationStore() {
		return STypeMuscleActivationStore;
	}

	public void setSTypeMuscleActivationStore(DoubleSeries sTypeMuscleActivationStore) {
		STypeMuscleActivationStore = sTypeMuscleActivationStore;
	}

	public DoubleSeries getFTypeMuscleActivationStore() {
		return FTypeMuscleActivationStore;
	}

	public void setFTypeMuscleActivationStore(DoubleSeries fTypeMuscleActivationStore) {
		FTypeMuscleActivationStore = fTypeMuscleActivationStore;
	}

	public void setAssociatedMusculotendon(
			MusculotendonSuperClass associatedMusculotendon) {
		this.associatedMusculotendon = associatedMusculotendon;
	}

	public double getTimeScale() {
		return timeScale;
	}

	public void setTimeScale(double timeScale) {
		this.timeScale = timeScale;
	}

	public abstract double getMaximumMuscleForce();

	public MusculotendonSuperClass getAssociatedMusculotendon() {
		return associatedMusculotendon;
	}

	public String getMuscleModel() {
		return muscleModel;
	}

	public void setMuscleModel(String muscleModel) {
		this.muscleModel = muscleModel;
	}

	public double getSlackTendonLength() {
		return slackTendonLength;
	}

	public void setSlackTendonLength(double slackTendonLength) {
		this.slackTendonLength = slackTendonLength;
	}

	public double getC_T() {
		return c_T;
	}

	public void setC_T(double c_T) {
		this.c_T = c_T;
	}

	public double getK_T() {
		return k_T;
	}

	public void setK_T(double k_T) {
		this.k_T = k_T;
	}

	public double getLR_T() {
		return Lr_T;
	}

	public void setLR_T(double Lr_T) {
		this.Lr_T = Lr_T;
	}
}