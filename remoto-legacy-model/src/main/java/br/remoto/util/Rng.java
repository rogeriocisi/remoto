package br.remoto.util;

import java.util.Random;

/**
 * Source of randomness for simulations.
 *
 * Each thread has its own generator. Code that runs a simulation can fix the seed with
 * seed(long) to obtain reproducible results; without a seed the behaviour is the same as the
 * former Math.random() / new Random() calls.
 */
public final class Rng
{
	private static final ThreadLocal<Random> CURRENT = new ThreadLocal<Random>()
	{
		@Override
		protected Random initialValue()
		{
			return new Random();
		}
	};

	private Rng()
	{
	}

	/** Makes the current thread's sequence reproducible. */
	public static void seed(long seed)
	{
		CURRENT.set(new Random(seed));
	}

	/** Back to an unseeded generator. */
	public static void reset()
	{
		CURRENT.remove();
	}

	/** Replacement for Math.random(). */
	public static double random()
	{
		return CURRENT.get().nextDouble();
	}

	/** Replacement for new Random(): derived from the current sequence. */
	public static Random newRandom()
	{
		return new Random(CURRENT.get().nextLong());
	}
}
