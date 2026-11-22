package br.remoto.core.simulation.musculotendon;

import java.util.ArrayList;

import br.remoto.core.model.muscle.Tendon;
import br.remoto.core.simulation.setup.Configuration;
import br.remoto.core.simulation.musculotendon.TendonSuperClass;
import br.remoto.core.simulation.util.Sample;

/**
 * The tendon of the original model: the samplers of length and force. The tendon is
 * br.remoto.core.model.muscle.Tendon.
 */
public class NonInnervatedTendon extends TendonSuperClass
{
	protected ArrayList lengthStore = new ArrayList();
	protected ArrayList forceStore = new ArrayList();

	private final Tendon tendon;
	private final double slackLength;

	public NonInnervatedTendon(Configuration conf, double muscleOptimalLength, double muscleMaximumForce, double slackLength, double c_T, double k_T, double Lr_T)
	{
		super(conf);

		this.tendon = new Tendon(muscleMaximumForce, slackLength, c_T, k_T, Lr_T);
		this.slackLength = slackLength;

		sampler1 = new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
		sampler2 = new Sample(conf.getDecimationFrequency(), 1000 / conf.getStep());
	}

	public void atualize(double t, double musculotendonLength, double muscleLength, double pennationAngle)
	{
		tendon.step(t, musculotendonLength, muscleLength, pennationAngle);

		sampler1.sample(lengthStore, "", t, tendon.length());
		sampler2.sample(forceStore, "", t, tendon.force());
	}

	public double getSlackLengthNorm() {
		return slackLength;
	}
	public double getLength() {
		return tendon.length();
	}
	public double getForce() {
		return tendon.force();
	}
	public double getLengthNorm() {
		return tendon.lengthNorm();
	}
	public ArrayList getLengthStore() {
		return lengthStore;
	}
	public ArrayList getForceStore() {
		return forceStore;
	}
}
