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

	// For threads that must not draw: the parallel update of neurons relies on every draw
	// happening on the simulation thread, in the order of the sequential loop
	private static final Random FORBIDDEN = new Random(0)
	{
		private static final long serialVersionUID = 1L;

		@Override
		protected int next(int bits)
		{
			throw new ForbiddenDraw();
		}
	};

	/**
	 * Thrown when a thread marked with forbid() draws a random number. An Error, so that the
	 * catch (Exception) blocks of the model do not swallow it.
	 */
	public static final class ForbiddenDraw extends Error
	{
		private static final long serialVersionUID = 1L;

		ForbiddenDraw()
		{
			super("random draw outside the simulation thread");
		}
	}

	private Rng()
	{
	}

	/** Makes any draw on the current thread fail (see ForbiddenDraw). */
	public static void forbid()
	{
		CURRENT.set(FORBIDDEN);
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
