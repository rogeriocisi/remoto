package br.remoto.model.Neuron;

import java.util.ArrayList;

import br.remoto.core.model.neuron.InterneuronMembrane;
import br.remoto.model.ReMoto;
import br.remoto.model.vo.NeuronVO;


/**
 * An interneuron of the original model (Renshaw cell, Ia, Ib and group II interneurons): identity
 * and spike train. The one compartment membrane is
 * br.remoto.core.model.neuron.InterneuronMembrane.
 */
public class Interneuron extends SpinalNeuron
{
	private static final long serialVersionUID = 1L;

	private final InterneuronMembrane membrane;


	public Interneuron(NeuronVO neu, int index, int indexCategory, Miscellaneous misc)
	{
		super(neu, index, misc);
		this.index = indexCategory;
		this.membrane = new InterneuronMembrane(misc);

		reset();

		// Set refractory period
		if( type.equals( ReMoto.RC ) )
			membrane.setSomaRefractoryPeriod( miscellaneous.getRcSomaRefractoryPeriod() );
		else if( type.equals( ReMoto.IaIn ) )
			membrane.setSomaRefractoryPeriod( miscellaneous.getIaInSomaRefractoryPeriod() );
		else if( type.equals( ReMoto.IbIn ) )
			membrane.setSomaRefractoryPeriod( miscellaneous.getIbInSomaRefractoryPeriod() );
		else if( type.equals( ReMoto.gII ) )
			membrane.setSomaRefractoryPeriod( miscellaneous.getGIISomaRefractoryPeriod() );
	}


	public InterneuronMembrane membrane()
	{
		return membrane;
	}


	protected void injectionChanged()
	{
		membrane.setInjection( current == null ? null : current::getCurrent );
	}


	public void atualize(double t)
	{
		try
		{
			super.atualize(t);

			atualizeSoma(t);
		}
		catch (Exception e)
		{
			System.out.println( "Error while atualizing IN: " + cd );
		}
	}


	protected void atualizeSoma(double t)
	{
		try
		{
			if( membrane.stepSoma(t) )
			{
				addSomaSpike(t);
			}
		}
		catch(Exception e)
		{
			System.out.println( e.getMessage() );
		}
	}


	public void addSomaSpike(double t)
	{
		membrane.fireSoma(t);
		somaSpikeTrain.add(new Double(t));

		propagateSpike(t);
	}


	// For interneurons, terminalSpikeTrain is the same as somaSpikeTrain
	public ArrayList getTerminalSpikeTrain()
	{
		return somaSpikeTrain;
	}
}
