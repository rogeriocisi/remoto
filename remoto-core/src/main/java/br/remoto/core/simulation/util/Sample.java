package br.remoto.core.simulation.util;

import java.util.ArrayList;

public class Sample
{
	private double frequency;
	private double baseFrequency;
	private int it;
	private int order;

	public Sample (double frequency, double baseFrequency){
		this.frequency = frequency;
		this.baseFrequency = baseFrequency;

		order = (int) (baseFrequency / frequency);

		it = 0;
	}

	public void sample(ArrayList storeList, String index, double t, double y){
		if(it == 0 || it == order){
			storeList.add(new Signal( index, y, t));
			it = 0;
		}

		it++;
	}

	/**
	 * Mesma decimação, para uma série de um observável só: guarda em vetores primitivos,
	 * sem criar um objeto por amostra. Os instantes e os valores são idênticos aos da
	 * variante acima.
	 */
	public void sample(DoubleSeries store, double t, double y){
		if(it == 0 || it == order){
			store.add(t, y);
			it = 0;
		}

		it++;
	}
}
