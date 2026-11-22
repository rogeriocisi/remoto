package br.remoto.core.simulation.musculotendon;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Hashtable;

import br.remoto.core.simulation.setup.Configuration;
import br.remoto.core.simulation.MotorUnit;
import br.remoto.core.simulation.ReMoto;
import br.remoto.core.simulation.util.Sample;
import br.remoto.core.simulation.util.Signal;

public class TendonSuperClass implements Serializable
{
	private static final long serialVersionUID = 1L;

	protected Sample sampler1;
	protected Sample sampler2;

	protected Configuration conf;

	public TendonSuperClass(Configuration conf)
	{
		this.conf = conf;
	}
}