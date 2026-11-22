package br.remoto.core.simulation;

import java.io.Serializable;
import java.util.ArrayList;

import br.remoto.core.simulation.neuron.Miscellaneous;
import br.remoto.core.simulation.neuron.Motoneuron;

/**
 * A motor unit of the original model, as the muscle models read it. The unit itself (twitch
 * parameters, the state the muscle models advance, the MUAP of the EMG and the position in the
 * muscle) is br.remoto.core.model.muscle.MotorUnit.
 */
public class MotorUnit implements Serializable
{
	private static final long serialVersionUID = 1L;

	protected ArrayList endPlateSpikeTrain;

	protected String cdNucleus;
	protected String cdMuscleModel;

	protected double latencyStimulusEndPlate;

	protected Miscellaneous miscellaneous;

	private final br.remoto.core.model.muscle.MotorUnit unit;

	public static final int EMG = 1;
	public static final int TWITCH = 2;

	public MotorUnit( Motoneuron mn , String muscle_model)
	{
		this.latencyStimulusEndPlate = mn.getLatencyStimulusEndPlate();
		this.endPlateSpikeTrain = mn.getTerminalSpikeTrain();
		this.cdNucleus = mn.getCdNucleus();
		this.miscellaneous = mn.getMiscellaneous();
		this.cdMuscleModel = muscle_model;

		// The spike train of the motoneuron, as it was when the unit was created
		ArrayList train = endPlateSpikeTrain;
		this.unit = new br.remoto.core.model.muscle.MotorUnit(mn.getIndex(), mn.getType(), mn.getCd(),
				new br.remoto.core.model.muscle.MotorUnit.EndPlate()
				{
					public int size() { return train.size(); }
					public double spike(int i) { return ((Double)train.get(i)).doubleValue(); }
				});
		this.unit.setStoredSignals( mn.isStoredSignals() );
	}

	/** This unit in br.remoto.core.model.muscle. */
	public br.remoto.core.model.muscle.MotorUnit unit()
	{
		return unit;
	}

	public void reset( double sample, boolean emgAttenuation )
	{
		unit.resetEmg( emgAttenuation );
	}

	public double getEMGSignal(double t)
	{
		return unit.emg(t);
	}

	public void randomSpatialDistribution(double muscleThickness, double skinLayer, double tauAttenuation, double cteWidening)
	{
		unit.randomSpatialDistribution(muscleThickness, skinLayer, tauAttenuation, cteWidening);
	}

	public void linearSpatialDistribution(double muscleThickness, double skinLayer, double tauAttenuation, double cteWidening, int muNumber, int muTotal)
	{
		unit.linearSpatialDistribution(muscleThickness, skinLayer, tauAttenuation, cteWidening, muNumber, muTotal);
	}

	public void presetSpatialDistribution(double y, double z, double muscleThickness, double skinLayer, double tauAttenuation, double cteWidening, int muNumber, int muTotal)
	{
		unit.presetSpatialDistribution(y, z, tauAttenuation, cteWidening);
	}

	public double getEndPlateSpike(int index)
	{
		return ((Double)endPlateSpikeTrain.get(index)).doubleValue();
	}

	public int getNumberOfSpikesAtEndPlate()
	{
		return endPlateSpikeTrain.size();
	}

	// String.valueOf(index), kept because the force samplers ask for it on every step
	private transient String indexString;

	public String getIndexString() {
		if( indexString == null )
			indexString = String.valueOf( unit.index() );
		return indexString;
	}

	public String getType() {
		return unit.type();
	}
	public double getAmpEMG() {
		return unit.ampEMG();
	}
	public void setAmpEMG(double ampEMG) {
		unit.setAmpEMG(ampEMG);
	}
	public double getLambdaEMG() {
		return unit.lambdaEMG();
	}
	public void setLambdaEMG(double lambdaEMG) {
		unit.setLambdaEMG(lambdaEMG);
	}
	public String getCd() {
		return unit.cd();
	}
	public String getCdNucleus() {
		return cdNucleus;
	}
	public int getIndex() {
		return unit.index();
	}
	public double getPosZ() {
		return unit.posZ();
	}
	public void setPosZ(double posZ) {
		unit.setPosZ(posZ);
	}
	public double getPosY() {
		return unit.posY();
	}
	public void setPosY(double posY) {
		unit.setPosY(posY);
	}
	public double getAttenuationToSkin() {
		return unit.attenuationToSkin();
	}
	public void setAttenuationToSkin(double attenuationToSkin) {
		unit.setAttenuationToSkin(attenuationToSkin);
	}
	public double getTimeWidening() {
		return unit.timeWidening();
	}
	public void setTimeWidening(double timeWidening) {
		unit.setTimeWidening(timeWidening);
	}
	public Miscellaneous getMiscellaneous() {
		return miscellaneous;
	}
	public int getIndexSpike() {
		return unit.indexSpike();
	}
	public int getIteration() {
		return unit.iteration();
	}
	public double gettSpike() {
		return unit.tSpike();
	}
	public void setIndexSpike(int indexSpike) {
		unit.setIndexSpike(indexSpike);
	}
	public void setIteration(int iteration) {
		unit.setIteration(iteration);
	}
	public void settSpike(double tSpike) {
		unit.setTSpike(tSpike);
	}
	public double getGpeak() {
		return unit.gpeak();
	}
	public void setGpeak(double gpeak) {
		unit.setGpeak(gpeak);
	}
	public double getGmax() {
		return unit.gmax();
	}
	public void setGmax(double gmax) {
		unit.setGmax(gmax);
	}
	public double getTpeak() {
		return unit.tpeak();
	}
	public void setTpeak(double tpeak) {
		unit.setTpeak(tpeak);
	}
	public double getLatencyStimulusEndPlate() {
		return latencyStimulusEndPlate;
	}
	public double getGpeakRaikova() {
		return unit.gpeakRaikova();
	}
	public void setGpeakRaikova(double gpeakRaikova) {
		unit.setGpeakRaikova(gpeakRaikova);
	}
	public double getTpeakRaikova() {
		return unit.tpeakRaikova();
	}
	public void setTpeakRaikova(double tpeakRaikova) {
		unit.setTpeakRaikova(tpeakRaikova);
	}
	public double getTHalfRaikova() {
		return unit.tHalfRaikova();
	}
	public void setTHalfRaikova(double tHalfRaikova) {
		unit.setTHalfRaikova(tHalfRaikova);
	}
	public double getB() {
		return unit.b();
	}
	public void setB(double b) {
		unit.setB(b);
	}
	public double getBRaikova() {
		return unit.bRaikova();
	}
	public void setBRaikova(double bRaikova) {
		unit.setBRaikova(bRaikova);
	}
	public boolean isEmgAttenuation() {
		return unit.emgAttenuation();
	}
	public void setEmgAttenuation(boolean emgAttenuation) {
		unit.setEmgAttenuation(emgAttenuation);
	}
	public boolean isStoredSignals() {
		return unit.storedSignals();
	}
	public void setStoredSignals(boolean storedSignals) {
		unit.setStoredSignals(storedSignals);
	}
	public double getTwTet() {
		return unit.twTet();
	}
	public void setTwTet(double twTet){
		unit.setTwTet(twTet);
	}
	public double getTwTetRaikova() {
		return unit.twTetRaikova();
	}
	public void setTwTetRaikova(double twTetRaikova){
		unit.setTwTetRaikova(twTetRaikova);
	}
}
