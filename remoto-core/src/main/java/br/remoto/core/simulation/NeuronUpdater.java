package br.remoto.core.simulation;

import br.remoto.core.model.random.Rng;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import br.remoto.core.model.synapse.Synapse;
import br.remoto.core.model.synapse.SynapseGroup;
import br.remoto.core.simulation.neuron.Interneuron;
import br.remoto.core.simulation.neuron.Motoneuron;
import br.remoto.core.simulation.neuron.Neuron;
import br.remoto.core.simulation.neuron.SpinalNeuron;

/**
 * Updates the neurons of one integration step with several threads, with exactly the results of
 * the sequential loop in {@link Simulation#run()}.
 *
 * <p>Three things make the two orders equivalent:
 * <ul>
 * <li><b>Spikes wait for the end of the step.</b> Every neuron holds the spikes it fires and
 * they are delivered after all neurons were updated, in the order of the sequential loop, so the
 * lists of received spikes come out the same. This needs every synaptic delay to be longer than
 * one step: a spike delivered during step t is then never due before step t + 1, wherever its
 * source sits in the loop. {@link #create} checks it and declines otherwise.</li>
 * <li><b>Random draws stay on the simulation thread.</b> Only motoneurons and interneurons run
 * in parallel, and only when their injected current is constant; they draw no random numbers.
 * Everything that draws (descending tracts, afferent fibers, stochastic signals) runs on the
 * calling thread in the order of the loop, so the sequence of draws does not change. Worker
 * threads are marked with {@link Rng#forbid()}: a draw there fails the simulation instead of
 * silently changing it.</li>
 * <li><b>No shared writes.</b> A neuron updates only its own state and its own synapses; the
 * nerve, the injected current and the miscellaneous parameters are only read.</li>
 * </ul>
 */
final class NeuronUpdater implements AutoCloseable
{
	/** Below this many parallel neurons the synchronization costs more than it saves. */
	private static final int MIN_PARALLEL_NEURONS = 64;

	/** Neurons per unit of work handed to a thread. */
	private static final int CHUNK = 8;

	private final Neuron all[];
	private final Neuron sequential[];
	private final Neuron parallel[];
	private final int chunks;

	private final Thread workers[];
	private final CyclicBarrier start;
	private final CyclicBarrier end;
	private final AtomicInteger nextChunk = new AtomicInteger();

	private volatile double time;
	private volatile boolean closed;
	private volatile Throwable failure;

	private NeuronUpdater(Neuron all[], Neuron sequential[], Neuron parallel[], int threads)
	{
		this.all = all;
		this.sequential = sequential;
		this.parallel = parallel;
		this.chunks = (parallel.length + CHUNK - 1) / CHUNK;

		this.start = new CyclicBarrier(threads);
		this.end = new CyclicBarrier(threads);
		this.workers = new Thread[threads - 1];

		for( Neuron neu : all )
			neu.setDeferSpikes(true);

		for( int i = 0; i < workers.length; i++ )
		{
			workers[i] = new Thread(this::workerLoop, "remoto-neurons-" + (i + 1));
			workers[i].setDaemon(true);
			workers[i].start();
		}
	}

	/**
	 * A parallel updater for these neurons, or null when the sequential loop must be kept: one
	 * thread, a synaptic delay not longer than one step, or too few neurons to be worth it.
	 */
	static NeuronUpdater create(Neuron neurons[][], int threads, double step)
	{
		if( threads < 2 )
			return null;

		List<Neuron> all = new ArrayList<>();
		List<Neuron> sequential = new ArrayList<>();
		List<Neuron> parallel = new ArrayList<>();

		for( Neuron nucleus[] : neurons )
		{
			if( nucleus == null )
				continue;

			for( Neuron neu : nucleus )
			{
				if( neu == null )
					return null;

				if( !deliveredLaterThanOneStep(neu, step) )
					return null;

				all.add(neu);

				if( runsInParallel(neu) )
					parallel.add(neu);
				else
					sequential.add(neu);
			}
		}

		if( parallel.size() < MIN_PARALLEL_NEURONS )
			return null;

		return new NeuronUpdater(all.toArray(new Neuron[0]), sequential.toArray(new Neuron[0]),
				parallel.toArray(new Neuron[0]), threads);
	}

	/** Deterministic neurons: no random draws and no state shared with other neurons. */
	private static boolean runsInParallel(Neuron neu)
	{
		if( neu.getClass() != Motoneuron.class && neu.getClass() != Interneuron.class )
			return false;

		Current current = ((SpinalNeuron)neu).getCurrent();

		// A modulating signal may draw random numbers and keeps its result in a field shared by
		// every neuron that receives the same current
		return current == null || current.getSignal() == null;
	}

	private static boolean deliveredLaterThanOneStep(Neuron neu, double step)
	{
		String key = neu.getSynapseKey();
		List synapses = neu.getTransmittingSynapses();

		for( int k = 0; k < synapses.size(); k++ )
		{
			Synapse g = ((SynapseGroup)synapses.get(k)).synapse(key);

			if( g == null || g.delay() < step + ReMoto.T_TOLERANCE )
				return false;
		}

		return true;
	}

	/** Updates every neuron at time t, then delivers the spikes fired, in the sequential order. */
	void step(double t)
	{
		time = t;
		nextChunk.set(0);

		await(start);

		// Draws happen here, on the calling thread, in the order of the sequential loop
		try
		{
			for( Neuron neu : sequential )
				neu.atualize(t);
		}
		catch( Throwable e )
		{
			failure = e;
		}

		work();

		await(end);

		Throwable e = failure;
		if( e != null )
		{
			if( e instanceof RuntimeException )
				throw (RuntimeException)e;
			if( e instanceof Error )
				throw (Error)e;
			throw new IllegalStateException(e);
		}

		for( Neuron neu : all )
			neu.flushSpikes();
	}

	private void work()
	{
		int c;

		while( (c = nextChunk.getAndIncrement()) < chunks )
		{
			int from = c * CHUNK;
			int to = Math.min(from + CHUNK, parallel.length);

			for( int i = from; i < to; i++ )
			{
				try
				{
					parallel[i].atualize(time);
				}
				catch( Throwable e )
				{
					failure = e;
				}
			}
		}
	}

	private void workerLoop()
	{
		Rng.forbid();

		while( true )
		{
			if( !awaitQuietly(start) || closed )
				return;

			work();

			if( !awaitQuietly(end) )
				return;
		}
	}

	private static void await(CyclicBarrier barrier)
	{
		try
		{
			barrier.await();
		}
		catch( InterruptedException e )
		{
			Thread.currentThread().interrupt();
			throw new IllegalStateException("parallel update interrupted", e);
		}
		catch( BrokenBarrierException e )
		{
			throw new IllegalStateException("parallel update broken", e);
		}
	}

	private static boolean awaitQuietly(CyclicBarrier barrier)
	{
		try
		{
			barrier.await();
			return true;
		}
		catch( InterruptedException | BrokenBarrierException e )
		{
			return false;
		}
	}

	int parallelNeurons()
	{
		return parallel.length;
	}

	@Override
	public void close()
	{
		closed = true;
		start.reset();
		end.reset();

		for( Thread worker : workers )
		{
			worker.interrupt();
			try
			{
				worker.join(1000);
			}
			catch( InterruptedException e )
			{
				Thread.currentThread().interrupt();
				break;
			}
		}

		for( Neuron neu : all )
		{
			neu.flushSpikes();
			neu.setDeferSpikes(false);
		}
	}
}
