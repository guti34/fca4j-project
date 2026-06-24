/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import fr.lirmm.fca4j.core.CsrConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.IConceptOrder;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

/**
 * Builds the full concept lattice with a parallel Close-by-One enumeration on
 * extents, working on a clarified context and expanding the result back to the
 * original context via the equivalence classes.
 *
 * <ol>
 * <li>Phase 0: pick the working orientation (transpose the input when it has more
 * attributes than objects, to enumerate on the shorter attribute axis) and clarify
 * it once (attribute and object equivalence classes kept). The orientation choice
 * is made here, around the enumeration kernel, exactly as in the native path.</li>
 * <li>Phase 1: parallel Close-by-One on extents (runCbO). Every closed extent is
 * generated exactly once from a single canonical parent by intersecting with a
 * higher-indexed attribute column, validated by a linear canonicity test. The
 * intersection of closed extents is closed, so no Galois closure is needed; and
 * canonicity guarantees uniqueness, so there is no shared deduplication map.
 * Canonical sub-trees are independent and forked into a {@link ForkJoinPool}.</li>
 * <li>Phase 2: clarified-space order (buildOrder):
 * <ul>
 * <li>materialise every concept with empty reduced sets;</li>
 * <li>reduced intents derived <em>directly</em>: attribute {@code a} is
 * introduced at the concept whose extent equals {@code cols[a]} (because
 * {@code a' == cols[a]}), found in O(1) through the extent index — no AOC-poset
 * pass;</li>
 * <li>Hasse diagram by direct lower covers, computed in parallel. For a concept
 * of extent {@code A} the candidate children are {@code A INTER m'} over the
 * meet-IRREDUCIBLE attributes {@code m} (sound because the context is clarified)
 * with {@code |A INTER m'| < |A|}; the lower covers are the maximal candidates;</li>
 * <li>reduced extents derived <em>directly</em> once the covers are known:
 * {@code rextent(c) = extent(c) \ UNION extent(child)} over the direct lower
 * covers, in parallel.</li>
 * </ul>
 * Full intents are NOT materialised by default (the JSON output uses reduced
 * intents only); set computeFullIntents for downstream uses such as rule
 * extraction.</li>
 * <li>Phase 3: expand the clarified-space order back to the working context via
 * the equivalence classes. By default only the reduced sets are remapped
 * ({@link IConceptOrder#substitutionReduced}); the full substitution is used when
 * full intents have been materialised. The Hasse structure is unchanged. Finally,
 * if the working orientation was the transpose, dualise the order back to the
 * original context ({@link IConceptOrder#dual}).</li>
 * </ol>
 *
 * <p>
 * The reduced extents/intents (the introducer labelling) are computed straight
 * from the lattice the enumeration already produces, exactly like the native C
 * kernel: there is no separate AOC-poset / Hermes computation. An object is
 * introduced at the smallest concept that still contains it (extent minus the
 * union of the children's extents); an attribute is introduced at the concept
 * whose extent is that attribute's column.
 */
public class Lattice_ParallelCbO implements AbstractAlgo<IConceptOrder> {

	private IBinaryContext matrix; // points to the clarified context during computation
	private IConceptOrder order;
	protected ISetFactory factory;
	private Chrono chrono;

	/** Number of worker threads for the parallel cover phase. */
	private int parallelism = Runtime.getRuntime().availableProcessors();

	/**
	 * Whether to materialise full intents. Not needed for the LATTICE -> JSON
	 * output (the writer emits reduced intents only), so off by default; enable it
	 * if full intents are required downstream (e.g. rule extraction).
	 */
	private boolean computeFullIntents = false;

	/** extent (ISet) -> concept id in the clarified-space order (concurrent). */
	private final ConcurrentHashMap<ISet, Integer> conceptByExtent = new ConcurrentHashMap<>();

	// ---- instrumentation ---------------------------------------------------
	private long totalPairsTested = 0;
	private int totalNewConcepts = 0;
	private long totalDuplicateMeets = 0;

	/**
	 * Instantiates the lattice builder.
	 *
	 * @param matrix the binary context
	 * @param chrono an optional chrono (may be null)
	 */
	public Lattice_ParallelCbO(IBinaryContext matrix, Chrono chrono) {
		super();
		this.matrix = matrix;
		this.factory = matrix.getFactory();
		this.chrono = chrono;
	}

	public Lattice_ParallelCbO(IBinaryContext matrix) {
		this(matrix, null);
	}

	// ========================================================================
	// Phase 1: parallel canonical enumeration (Close-by-One on extents)
	// ========================================================================

	/** Generator columns: cols[i] = extent of clarified attribute i. */
	private ISet[] cols;
	/** Number of clarified attributes (the extension alphabet). */
	private int attrCount;
	/** Number of objects. */
	private int objectCount;
	/** Extension attempts (intersections) and canonicity failures. */
	private LongAdder enumAttempts;
	private LongAdder enumFailures;

	/** Fork while the worker's local queue is short (self-balancing granularity). */
	private static final int SURPLUS = 3;

	/**
	 * Enumerates every closed extent exactly once with a parallel Close-by-One on
	 * extents. Each concept is generated from a single canonical parent by
	 * intersecting with a higher-indexed attribute column; a linear canonicity test
	 * rejects non-canonical generations. Because the intersection of closed extents
	 * is closed, no Galois closure is needed. There is no shared deduplication map:
	 * canonicity guarantees uniqueness, so each task writes its concept once.
	 * Canonical sub-trees are independent and forked into the pool.
	 */
	private void runCbO() {
		if (chrono != null) {
			chrono.start("enum");
		}
		attrCount = matrix.getAttributeCount();
		cols = new ISet[attrCount];
		for (int i = 0; i < attrCount; i++) {
			cols[i] = matrix.getExtent(i);
		}
		enumAttempts = new LongAdder();
		enumFailures = new LongAdder();

		objectCount = matrix.getObjectCount();
		ISet top = factory.createSet(objectCount);
		top.fill(objectCount); // top extent = all objects
		boolean[] inBtop = new boolean[attrCount];
		for (int k = 0; k < attrCount; k++) {
			inBtop[k] = cols[k].containsAll(top); // attribute held by all objects
		}

		ForkJoinPool pool = new ForkJoinPool(Math.max(1, parallelism));
		try {
			pool.invoke(new CbOTask(top, inBtop, -1));
		} finally {
			pool.shutdown();
		}

		totalPairsTested = enumAttempts.sum();
		totalDuplicateMeets = enumFailures.sum();
		totalNewConcepts = conceptByExtent.size();
		if (chrono != null) {
			chrono.stop("enum");
		}
	}

	/**
	 * One node of the canonical enumeration: registers its extent, then extends it
	 * with each higher-indexed attribute that passes the canonicity test, forking
	 * the canonical children.
	 */
	private final class CbOTask extends RecursiveAction {
		private final ISet ext;
		private final boolean[] inB; // inB[k] == (ext subseteq cols[k]) == (k in intent(ext))
		private final int y; // last attribute added on the path to ext

		CbOTask(ISet ext, boolean[] inB, int y) {
			this.ext = ext;
			this.inB = inB;
			this.y = y;
		}

		@Override
		protected void compute() {
			// unique by canonicity: no dedup, just register
			conceptByExtent.put(ext, -1);

			ArrayList<CbOTask> forked = null;
			// reusable scratch for the canonicity test: ei = ext INTER cols[i] is
			// computed in place, so non-canonical extensions (~96% of attempts) cost
			// no allocation; only canonical extents are materialised.
			ISet scratch = factory.createSet(objectCount);
			for (int i = y + 1; i < attrCount; i++) {
				if (inB[i]) {
					continue; // i already in the intent (ext subseteq cols[i])
				}
				enumAttempts.increment();
				scratch.setTo(ext);
				scratch.retainAll(cols[i]); // scratch = ext INTER cols[i]

				// canonicity: no k < i, k not in intent(ext), with ei subseteq cols[k]
				boolean canonical = true;
				for (int k = 0; k < i; k++) {
					if (!inB[k] && cols[k].containsAll(scratch)) {
						canonical = false;
						break;
					}
				}
				if (!canonical) {
					enumFailures.increment();
					continue;
				}

				// canonical: materialise the extent now (only ~1 attempt in 23)
				ISet ei = scratch.clone();

				// intent(ei): unchanged below i (canonical), i added, recompute >= i
				boolean[] inBi = new boolean[attrCount];
				System.arraycopy(inB, 0, inBi, 0, i);
				inBi[i] = true;
				for (int k = i + 1; k < attrCount; k++) {
					inBi[k] = inB[k] || cols[k].containsAll(ei);
				}

				CbOTask child = new CbOTask(ei, inBi, i);
				if (getSurplusQueuedTaskCount() <= SURPLUS) {
					child.fork();
					if (forked == null) {
						forked = new ArrayList<>();
					}
					forked.add(child);
				} else {
					child.compute(); // run inline when the pool is busy
				}
			}
			if (forked != null) {
				for (int f = forked.size() - 1; f >= 0; f--) {
					forked.get(f).join();
				}
			}
		}
	}

	// ========================================================================
	// Phase 2: clarified-space ConceptOrder with direct Hasse covers and
	//          directly-derived reduced sets (no AOC-poset pass)
	// ========================================================================

	/**
	 * Builds the clarified-space {@link CsrConceptOrder}: concepts with their
	 * reduced sets derived directly from the lattice, then the Hasse diagram by
	 * direct lower covers (parallel). Full intents are reconstructed only when
	 * computeFullIntents is set.
	 */
	private IConceptOrder buildOrder() throws Exception {
		if (chrono != null) {
			chrono.start("order");
		}
		IConceptOrder clarOrder = new CsrConceptOrder("Lattice_ParallelCbO", matrix, getDescription());

		// 1) Materialise concepts. Reduced sets start empty and are filled directly
		//    below (no Hermes/AOC-poset pass). The full extent is the concept's own
		//    extent; the full intent is left empty and rebuilt on demand (step 4).
		long tMaterialize0 = System.nanoTime();
		final int n = conceptByExtent.size();
		ArrayList<ISet> extents = new ArrayList<>(n);
		ArrayList<Integer> ids = new ArrayList<>(n);
		ISet[] extentOfId = new ISet[n];
		for (ISet extent : conceptByExtent.keySet()) {
			ISet rextent = factory.createSet(matrix.getObjectCount());
			ISet rintent = factory.createSet(matrix.getAttributeCount());
			int id = clarOrder.addConcept(factory.clone(extent),
					factory.createSet(matrix.getAttributeCount()), rextent, rintent);
			conceptByExtent.put(extent, id);
			extents.add(extent);
			ids.add(id);
			extentOfId[id] = extent;
		}
		long tMaterialize = System.nanoTime() - tMaterialize0;

		// 1b) Reduced INTENTS, derived directly (same rule as the C kernel):
		//     attribute a is introduced at the concept whose extent equals cols[a]
		//     (a' == cols[a]). One O(1) index lookup per attribute; no dependence on
		//     the Hasse diagram, so this runs right after materialisation.
		long tRint0 = System.nanoTime();
		for (int a = 0; a < attrCount; a++) {
			Integer introId = conceptByExtent.get(cols[a]);
			if (introId != null) {
				clarOrder.getConceptReducedIntent(introId).add(a);
			}
		}
		long tRint = System.nanoTime() - tRint0;

		// 2) Direct Hasse via lower covers (parallel computation). For concept c
		//    (extent A), candidate children are D = A INTER m' for the meet-IRREDUCIBLE
		//    attributes m (safe here: the context is clarified) with |D| < |A|; the
		//    lower covers are the maximal candidates. Concept ids are looked up only
		//    for the surviving covers (a handful per concept) instead of for every
		//    candidate, which removes the bulk of the ConcurrentHashMap lookups.
		//    The computation is independent and read-only, so it runs in parallel;
		//    only the graph mutation (the bulk edge insertion) is sequential.
		final ISet irreducibleAttrs = FastReduction.computeIrreductibleIntent(matrix);
		final ISet[] irrExtents = new ISet[irreducibleAttrs.cardinality()];
		{
			int t = 0;
			for (Iterator<Integer> it = irreducibleAttrs.iterator(); it.hasNext();) {
				irrExtents[t++] = matrix.getExtent(it.next());
			}
		}
		final int[][] lowerCoversOf = new int[n][];

		// per-phase CPU time summed across worker threads (the ratio is what matters,
		// since GC/scheduling noise scales all phases of a run together)
		final LongAdder nsCand = new LongAdder();
		final LongAdder nsMax = new LongAdder();
		final LongAdder nsGet = new LongAdder();

		final int objCount = matrix.getObjectCount();
		ForkJoinPool pool = new ForkJoinPool(Math.max(1, parallelism));
		try {
			// Submitting the parallel stream to this pool forces it to run on this
			// pool (Java 8 idiom) rather than the shared common pool.
			pool.submit(() -> IntStream.range(0, n).parallel().forEach(idx -> {
				ISet a = extents.get(idx);
				int cardA = a.cardinality();
				long c0 = System.nanoTime();

				// candidate child extents (duplicates allowed: equal extents are
				// dropped by the maximal selection; no id lookup yet)
				ISet scratch = factory.createSet(objCount);
				ArrayList<ISet> cand = new ArrayList<>();
				for (int k = 0; k < irrExtents.length; k++) {
					scratch.setTo(a);
					scratch.retainAll(irrExtents[k]);
					if (scratch.cardinality() < cardA) {
						cand.add(scratch.clone());
					}
				}
				long c1 = System.nanoTime();

				// maximal candidates = lower covers (allocation-free; cand is small,
				// bounded by the number of irreducible attributes). cand may contain
				// duplicate extents from different columns: an equal extent is kept
				// once via the j<i tie-break.
				int cc = cand.size();
				int[] card = new int[cc];
				for (int i = 0; i < cc; i++) {
					card[i] = cand.get(i).cardinality();
				}

				ArrayList<ISet> covers = new ArrayList<>();
				for (int i = 0; i < cc; i++) {
					ISet di = cand.get(i);
					boolean maximal = true;
					for (int j = 0; j < cc; j++) {
						if (j == i) {
							continue;
						}
						if ((card[j] > card[i] || (card[j] == card[i] && j < i))
								&& cand.get(j).containsAll(di)) {
							maximal = false;
							break;
						}
					}
					if (maximal) {
						covers.add(di);
					}
				}
				long c2 = System.nanoTime();

				// look up concept ids only for the surviving covers
				int[] arr = new int[covers.size()];
				for (int k = 0; k < arr.length; k++) {
					arr[k] = conceptByExtent.get(covers.get(k));
				}
				long c3 = System.nanoTime();
				nsCand.add(c1 - c0);
				nsMax.add(c2 - c1);
				nsGet.add(c3 - c2);
				lowerCoversOf[idx] = arr;
			})).get();
		} catch (ExecutionException ee) {
			Throwable cause = ee.getCause();
			throw (cause instanceof Exception) ? (Exception) cause : ee;
		} finally {
			pool.shutdown();
		}

		// Edge insertion: flatten covers into (child, parent) arrays and insert them
		// in one bulk call (no per-edge listener events; extrema recomputed once).
		long tEdges0 = System.nanoTime();
		int totalEdges = 0;
		for (int idx = 0; idx < n; idx++) {
			totalEdges += lowerCoversOf[idx].length;
		}
		int[] lowers = new int[totalEdges];
		int[] greaters = new int[totalEdges];
		int p = 0;
		for (int idx = 0; idx < n; idx++) {
			int parent = ids.get(idx);
			for (int child : lowerCoversOf[idx]) {
				lowers[p] = child;
				greaters[p] = parent;
				p++;
			}
		}
		clarOrder.addPrecedenceConnections(lowers, greaters);
		long tEdges = System.nanoTime() - tEdges0;

		// 3) Reduced EXTENTS, derived directly (same rule as the C kernel): an object
		//    is introduced at the smallest concept still containing it, i.e.
		//    rextent(c) = extent(c) \ UNION extent(child) over the direct lower
		//    covers. Now that the covers are known, compute it in parallel — each
		//    task writes its own concept's reduced extent (disjoint slots).
		long tRext0 = System.nanoTime();
		final ISet[] extById = extentOfId;
		final int[][] lc = lowerCoversOf;
		ForkJoinPool rexPool = new ForkJoinPool(Math.max(1, parallelism));
		try {
			rexPool.submit(() -> IntStream.range(0, n).parallel().forEach(idx -> {
				int cid = ids.get(idx);
				ISet re = factory.clone(extById[cid]);
				for (int child : lc[idx]) {
					re.removeAll(extById[child]);
				}
				clarOrder.setReducedExtent(cid, re);
			})).get();
		} catch (ExecutionException ee) {
			Throwable cause = ee.getCause();
			throw (cause instanceof Exception) ? (Exception) cause : ee;
		} finally {
			rexPool.shutdown();
		}
		long tRext = System.nanoTime() - tRext0;

		// 4) Full intents are NOT needed for the LATTICE -> JSON output: the writer
		//    emits reduced intents only. Computed only on demand (rule extraction).
		long tIntents = 0;
		if (computeFullIntents) {
			long tIntents0 = System.nanoTime();
			clarOrder.computeIntents();
			tIntents = System.nanoTime() - tIntents0;
		}

		System.out.println(String.format(
				"order breakdown: materialize=%d ms (wall, seq) | rIntents=%d ms (wall, seq)"
						+ " | cover phase CPU-ms summed over %d threads: cand=%d max=%d get=%d"
						+ " | edges=%d ms (wall, seq) | rExtents=%d ms (wall, par)"
						+ " | computeIntents=%d ms (wall, seq)",
				tMaterialize / 1_000_000, tRint / 1_000_000, parallelism, nsCand.sum() / 1_000_000,
				nsMax.sum() / 1_000_000, nsGet.sum() / 1_000_000, tEdges / 1_000_000,
				tRext / 1_000_000, tIntents / 1_000_000));

		if (chrono != null) {
			chrono.stop("order");
		}
		return clarOrder;
	}

	// ========================================================================
	// AbstractAlgo
	// ========================================================================

	@Override
	public void run() {
		try {
			IBinaryContext original = matrix;

			// Enumerate on the shorter attribute axis (CbO's alphabet is the working
			// context's attributes; canonicity cost grows with their number). If the
			// context has more attributes than objects, work on the transpose and
			// dualise the order back at the end — same orientation choice as the
			// native path, made here in Java around the enumeration kernel.
			boolean transposed = original.getAttributeCount() > original.getObjectCount();
			IBinaryContext base = transposed ? original.transpose() : original;

			// Phase 0: clarify `base`. Its equivalence classes are relative to `base`,
			// so the substitution remaps clarified -> base; dual then maps base -> original.
			if (chrono != null) {
				chrono.start("clarify");
			}
			Clarification clar = new Clarification(base, base.getName(), true, true, false);
			clar.run();
			IBinaryContext clarified = clar.getResult();
			List<ISet> attrClasses = clar.getAttributeClasses();
			List<ISet> objClasses = clar.getObjectClasses();
			if (chrono != null) {
				chrono.stop("clarify");
			}

			// Phases 1-2 on the clarified context.
			matrix = clarified;
			factory = clarified.getFactory();
			conceptByExtent.clear();

			runCbO();
			order = buildOrder();

			// Phase 3: correction by equivalence classes (clarified -> base).
			// The JSON output reads only reduced sets + Hasse, so by default remap
			// only the reduced sets (full extents would be remapped for nothing).
			if (chrono != null) {
				chrono.start("expand");
			}
			if (computeFullIntents) {
				order.substitution(base, attrClasses, objClasses);
			} else {
				order.substitutionReduced(base, attrClasses, objClasses);
			}
			if (chrono != null) {
				chrono.stop("expand");
			}

			// If we worked on the transpose, dualise the order back to the original
			// (swaps extents/intents and reduced sets, reverses the Hasse diagram).
			if (transposed) {
				if (chrono != null) {
					chrono.start("dual");
				}
				order.dual(original);
				if (chrono != null) {
					chrono.stop("dual");
				}
			}

			matrix = original;
			factory = original.getFactory();
			logStats();
		} catch (Exception ex) {
			Logger.getLogger(Lattice_ParallelCbO.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	@Override
	public String getDescription() {
		return "ParallelCbO";
	}

	@Override
	public IConceptOrder getResult() {
		return order;
	}

	// ========================================================================
	// Instrumentation accessors
	// ========================================================================

	/**
	 * Sets the number of worker threads for the parallel cover phase.
	 *
	 * @param parallelism the parallelism (>= 1)
	 */
	public void setParallelism(int parallelism) {
		this.parallelism = parallelism;
	}

	public int getParallelism() {
		return parallelism;
	}

	/**
	 * Enables/disables materialising full intents (off by default; not needed for
	 * the JSON output, required for rule extraction).
	 *
	 * @param computeFullIntents whether to compute full intents
	 */
	public void setComputeFullIntents(boolean computeFullIntents) {
		this.computeFullIntents = computeFullIntents;
	}

	public boolean isComputeFullIntents() {
		return computeFullIntents;
	}

	public int getTotalConcepts() {
		return totalNewConcepts;
	}

	public double getRedundancyRate() {
		return totalPairsTested == 0 ? 0.0
				: (double) totalDuplicateMeets / (double) totalPairsTested;
	}

	private void logStats() {
		for (String serie : chrono.getSerieNames())
			System.out.println(serie + ": " + chrono.getResult(serie));
		System.out.println("=== Lattice ParallelCbO stats ===");
		System.out.println("attributes         : " + attrCount);
		System.out.println("concepts (total)   : " + totalNewConcepts);
		System.out.println("extension attempts : " + totalPairsTested);
		System.out.println("canonicity failures: " + totalDuplicateMeets);
		System.out.println(String.format("redundancy rate    : %.3f", getRedundancyRate()));
		System.out.println("edges (Hasse)      : " + (order == null ? 0 : order.getEdgeCount()));
	}
}
