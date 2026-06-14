/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
						
import java.util.HashMap;
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

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

/**
 * Builds the full concept lattice with a parallel Close-by-One enumeration on
 * extents, working on a clarified context (like Hermes) and expanding the result
 * back to the original context via the equivalence classes.
 *
 * <ol>
 * <li>Phase 0: clarify the input context once (attribute and object equivalence
 * classes kept). Hermes then runs on the already-clarified context, so the
 * original is not clarified twice.</li>
 * <li>Phase 1: AOC-poset with Hermes on the clarified context, used only as the
 * source of reduced extents/intents (indexed by extent).</li>
 * <li>Phase 2: parallel Close-by-One on extents (runCbO). Every closed extent is
 * generated exactly once from a single canonical parent by intersecting with a
 * higher-indexed attribute column, validated by a linear canonicity test. The
 * intersection of closed extents is closed, so no Galois closure is needed; and
 * canonicity guarantees uniqueness, so there is no shared deduplication map.
 * Canonical sub-trees are independent and forked into a {@link ForkJoinPool}.</li>
 * <li>Phase 3: Hasse diagram by direct lower covers, computed in parallel
 * (buildOrder). For a concept of extent A the candidate children are
 * {@code A INTER m'} over the meet-IRREDUCIBLE attributes m (sound because the
 * context is clarified) with {@code |A INTER m'| < |A|}; the lower covers are the
 * maximal candidates. Concept ids are looked up only for the surviving covers,
 * and the cover edges are inserted in a single bulk call. No transitive
 * reduction, no per-concept Galois closure. Full intents are NOT materialised by
 * default (the JSON output uses reduced intents only); set computeFullIntents for
 * downstream uses such as rule extraction.</li>
 * <li>Phase 4: expand the clarified-space order back to the original context via
 * the equivalence classes. By default only the reduced sets are remapped
 * ({@link ConceptOrder#substitutionReduced}); the full substitution is used when
 * full intents have been materialised. The Hasse structure is unchanged.</li>
 * </ol>
 *
 * <p>
 * Reduced extents/intents come from Hermes: an AOC (introducer) concept keeps
 * Hermes's reduced sets, a non-introducer concept has empty reduced sets.
 */
public class Lattice_ParallelCbO implements AbstractAlgo<ConceptOrder> {

	private IBinaryContext matrix; // points to the clarified context during computation
	private ConceptOrder order;
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

	/** The AOC-poset produced by Hermes (source of reduced extents/intents). */
	private ConceptOrder aoc;
	/** extent (ISet) -> AOC concept id, for introducer concepts only. */
	private final HashMap<ISet, Integer> aocConceptByExtent = new HashMap<>();

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
	// Phase 1: AOC-poset with Hermes (reduced extents/intents only)
	// ========================================================================

	/**
	 * Runs Hermes on the clarified context and indexes each AOC (introducer)
	 * concept by its extent, so buildOrder can reuse Hermes's reduced
	 * extents/intents. Does NOT register concepts for enumeration: the canonical
	 * enumeration (runCbO) discovers every concept on its own.
	 *
	 * @throws Exception on Hermes failure
	 */
	private void buildAOC() throws Exception {
		if (chrono != null) {
			chrono.start("aoc");
		}
		AOC_poset_Hermes hermes = new AOC_poset_Hermes(matrix, chrono);
		aoc = hermes.computeGSH();
		if (chrono != null) {
			chrono.stop("aoc");
		}
		for (Iterator<Integer> it = aoc.getBasicIterator(); it.hasNext();) {
			int c = it.next();
			ISet extent = factory.clone(aoc.getConceptExtent(c));
			aocConceptByExtent.put(extent, c);
		}
	}

	// ========================================================================
	// Phase 2: parallel canonical enumeration (Close-by-One on extents)
	// ========================================================================

	/** Generator columns: cols[i] = extent of clarified attribute i. */
	private ISet[] cols;
	/** Number of clarified attributes (the extension alphabet). */
	private int attrCount;
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

		int objectCount = matrix.getObjectCount();
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
			for (int i = y + 1; i < attrCount; i++) {
				if (inB[i]) {
					continue; // i already in the intent (ext subseteq cols[i])
				}
				enumAttempts.increment();
				ISet ei = ext.newIntersect(cols[i]); // closed and strictly smaller

				// canonicity: no k < i, k not in intent(ext), with ei subseteq cols[k]
				boolean canonical = true;
				for (int k = 0; k < i; k++) {
					if (!inB[k] && cols[k].containsAll(ei)) {
						canonical = false;
						break;
					}
				}
				if (!canonical) {
					enumFailures.increment();
					continue;
				}

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
	// Phase 3: clarified-space ConceptOrder with direct Hasse covers
	// ========================================================================

	/**
	 * Builds the clarified-space {@link ConceptOrder}: concepts with their reduced
	 * sets and empty full intents, then the Hasse diagram by direct lower covers
	 * (parallel) over the meet-irreducible columns, with bulk edge insertion. Full
	 * intents are reconstructed only when computeFullIntents is set.
	 */
	private ConceptOrder buildOrder() throws Exception {
		if (chrono != null) {
			chrono.start("order");
		}
		ConceptOrder clarOrder = new ConceptOrder("Lattice_ParallelCbO", matrix, getDescription());

		long tMaterialize0 = System.nanoTime();
		// 1) Materialise concepts. Full intent is left empty; rebuilt in step 3.
		ArrayList<ISet> extents = new ArrayList<>(conceptByExtent.size());
		ArrayList<Integer> ids = new ArrayList<>(conceptByExtent.size());
		for (ISet extent : conceptByExtent.keySet()) {
			ISet rextent;
			ISet rintent;
			Integer aocId = aocConceptByExtent.get(extent);
			if (aocId != null) {
				rextent = factory.clone(aoc.getConceptReducedExtent(aocId));
				rintent = factory.clone(aoc.getConceptReducedIntent(aocId));
			} else {
				rextent = factory.createSet(matrix.getObjectCount());
				rintent = factory.createSet(matrix.getAttributeCount());
			}
			int id = clarOrder.addConcept(factory.clone(extent),
					factory.createSet(matrix.getAttributeCount()), rextent, rintent);
			conceptByExtent.put(extent, id);
			extents.add(extent);
			ids.add(id);
		}
		long tMaterialize = System.nanoTime() - tMaterialize0;

		// 2) Direct Hasse via lower covers (parallel computation). For concept c
		//    (extent A), candidate children are D = A INTER m' for the meet-IRREDUCIBLE
		//    attributes m (safe here: the context is clarified) with |D| < |A|; the
		//    lower covers are the maximal candidates. Concept ids are looked up only
		//    for the surviving covers (a handful per concept) instead of for every
		//    candidate, which removes the bulk of the ConcurrentHashMap lookups.
		//    The computation is independent and read-only, so it runs in parallel;
		//    only the graph mutation the bulk edge insertion is sequential, since
		//    JGraphT is not safe for concurrent writes.
		final ISet irreducibleAttrs = FastReduction.computeIrreductibleIntent(matrix);
		final ISet[] irrExtents = new ISet[irreducibleAttrs.cardinality()];
		{
			int t = 0;
			for (Iterator<Integer> it = irreducibleAttrs.iterator(); it.hasNext();) {
				irrExtents[t++] = matrix.getExtent(it.next());
			}
		}
		final int n = extents.size();
		final int[][] lowerCoversOf = new int[n][];

		// per-phase CPU time summed across worker threads (the ratio is what matters,
		// since GC/scheduling noise scales all phases of a run together)
		final LongAdder nsCand = new LongAdder();
		final LongAdder nsMax = new LongAdder();
		final LongAdder nsGet = new LongAdder();

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
				ArrayList<ISet> cand = new ArrayList<>();
				for (int k = 0; k < irrExtents.length; k++) {
					ISet d = a.newIntersect(irrExtents[k]);
					if (d.cardinality() < cardA) {
						cand.add(d);
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

		// 3) Full intents are NOT needed for the LATTICE -> JSON output: the writer
		//    emits reduced intents only (ConceptOrderJSONWriter.build(., false, .)).
		//    Computed only on demand (rule extraction needs them).
		long tIntents = 0;
		if (computeFullIntents) {
			long tIntents0 = System.nanoTime();
			clarOrder.computeIntents();
			tIntents = System.nanoTime() - tIntents0;
		}

		System.out.println(String.format(
				"order breakdown: materialize=%d ms (wall, seq) | cover phase CPU-ms summed over %d threads:"
						+ " cand=%d max=%d get=%d | edges=%d ms (wall, seq) | computeIntents=%d ms (wall, seq)",
				tMaterialize / 1_000_000, parallelism, nsCand.sum() / 1_000_000,
				nsMax.sum() / 1_000_000, nsGet.sum() / 1_000_000, tEdges / 1_000_000,
				tIntents / 1_000_000));

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

			// Auto-orientation: the CbO alphabet is the working context's attributes,
			// and the canonicity cost grows ~quadratically with it, so enumerate on
			// the SMALLER dimension. If objects < attributes, transpose (objects become
			// the alphabet) and dualise the resulting order back at the end.
			boolean transposed = original.getObjectCount() < original.getAttributeCount();
			IBinaryContext base = transposed ? original.transpose() : original;

			// Phase 0: clarify once. Hermes then runs on the clarified context.
			if (chrono != null) {
				chrono.start("clarify");
			}
			Clarification clar = new Clarification(original, original.getName(), true, true, false);
			clar.run();
			IBinaryContext clarified = clar.getResult();
			List<ISet> attrClasses = clar.getAttributeClasses();
			List<ISet> objClasses = clar.getObjectClasses();
			if (chrono != null) {
				chrono.stop("clarify");
			}

			// Phases 1-3 on the clarified context.
			matrix = clarified;
			factory = clarified.getFactory();
			conceptByExtent.clear();
			aocConceptByExtent.clear();

			buildAOC();
			runCbO();
			order = buildOrder();

			// Phase 4: correction by equivalence classes (clarified -> original).
			// The JSON output reads only reduced sets + Hasse, so by default remap
			// only the reduced sets (full extents would be remapped for nothing).
			if (chrono != null) {
				chrono.start("expand");
			}
			if (computeFullIntents) {
				order.substitution(original, attrClasses, objClasses);
			} else {
				order.substitutionReduced(original, attrClasses, objClasses);
			}
			if (chrono != null) {
				chrono.stop("expand");
			}
			// If we worked on the transpose, dualise the order back to the original.
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
	public ConceptOrder getResult() {
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
		for(String serie:chrono.getSerieNames())
			System.out.println(serie+": "+chrono.getResult(serie));
		System.out.println("=== Lattice ParallelCbO stats ===");
		System.out.println("attributes         : " + attrCount);
		System.out.println("concepts (total)   : " + totalNewConcepts);
		System.out.println("extension attempts : " + totalPairsTested);
		System.out.println("canonicity failures: " + totalDuplicateMeets);
		System.out.println(String.format("redundancy rate    : %.3f", getRedundancyRate()));
		System.out.println("edges (Hasse)      : " + (order == null ? 0 : order.getEdgeCount()));
	}
}
