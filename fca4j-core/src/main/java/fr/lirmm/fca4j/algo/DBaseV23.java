/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.jgrapht.alg.TransitiveReduction;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.util.Chrono;
import fr.lirmm.fca4j.util.RuleUtilities;

/**
 * Implementation of the D-Basis construction algorithm for Formal Concept Analysis.
 *
 * <p>
 * This algorithm computes an ordered-direct implicational basis (the D-basis)
 * from a binary context. The construction is organized as follows:
 * </p>
 *
 * <ol>
 *   <li>Context clarification (attribute equivalence removal)</li>
 *   <li>Computation of all binary implications</li>
 *   <li>Independent computation of non-binary implications for each attribute</li>
 *   <li>Optional reduction to the E-basis when no D-cycles are present</li>
 * </ol>
 *
 * <p>
 * The algorithm relies on the characterization of minimal generators of
 * attributes as minimal transversals of a hypergraph.
 * Each attribute is processed independently, allowing natural parallelization.
 * </p>
 *
 * <p>
 * The resulting basis is ordered-direct: all binary implications precede
 * non-binary ones, and no fixed-point iteration is required for closure.
 * </p>
 */
public class DBaseV23 implements AbstractAlgo<List<Implication>> {
    /** Minimum support threshold for implications */
    protected int minSupport = 0;

    /** Original binary context */
    protected IBinaryContext context;

    /**
     * Clarified context where equivalent attributes have been merged.
     * All computations are performed on this reduced context.
     */
    protected IBinaryContext clarifiedContext;

    /**
     * Equivalence classes of attributes induced by clarification.
     * Used to rewrite implications back to the original context.
     */
    protected List<ISet> attrClasses;

    /** Closure engine used for closure and support computation */
    protected ClosureDirect closureEngine;

    /** Maximum number of worker threads */
    private int maxThreads;

    /**
     * For each attribute b, stores attributes a such that a → b
     * is a known binary implication.
     * Used as a pruning rule during hypergraph construction.
     */
    protected Map<Integer, ISet> binaryPremises = new HashMap<>();

    /** Execution time monitoring */
    protected Chrono chrono = new Chrono("myChrono");

    /** Resulting implicational basis */
    protected List<Implication> implications;

	public DBaseV23(IBinaryContext context, int minSupport, int maxThreads) {
		this.minSupport = minSupport;
		this.context = context;
		this.closureEngine = new ClosureDirect(context);
		this.maxThreads = maxThreads;
	}

    /**
     * Executes the complete D-basis computation pipeline.
     */
    @Override
    public void run() {
        implications = computeDBasis();

        for (String serie : chrono.getSerieNames()) {
            System.out.println(serie + " time: " + chrono.getResult(serie));
        }
        System.out.println("Total time: " + chrono.getResult());
    }

	@Override
	public List<Implication> getResult() {
		return implications;
	}

	@Override
	public String getDescription() {
		return "DBase";
	}

	public IBinaryContext getContext() {
		return context;
	}

	protected List<Implication> computeDBasis() {
		// clarify context and compute corresponding equivalence rules
		chrono.start("clarify");
		List<Implication> eqBasis = clarify();
		chrono.stop("clarify");

		closureEngine.setContext(clarifiedContext);
		if (clarifiedContext.getAttributeCount() < context.getAttributeCount()) {
//			System.out.println("Warning: the context is not clarified. Computed with "
//					+ clarifiedContext.getAttributeCount() + "/" + context.getAttributeCount() + " attributes");
		}
		// compute binary basis
		chrono.start("compute binary basis");
		List<Implication> binaryBasis = computeBinaryBasis();
		chrono.stop("compute binary basis");

		List<Implication> tempBasis = new ArrayList<>(binaryBasis);

		// compute non binary basis
		chrono.start("compute non binary basis");
		ISet[] closures = computeAttributesClosure();
		ParallelBasisBuilder builder = new ParallelBasisBuilder(closures);
		List<Implication> nonBinaryImplications;
		try {
			nonBinaryImplications = builder.run();
			tempBasis.addAll(nonBinaryImplications);
		} catch (InterruptedException e) {
			e.printStackTrace();
			return null;
		}
		chrono.stop("compute non binary basis");
		// rewrite results
		chrono.start("rewrite results");
		closureEngine.setContext(context);
		List<Implication> dBasis = new ArrayList<>();
		for (Implication impl : eqBasis) {
			ISet support = closureEngine.computeExtent(impl.getPremise());
			impl.setSupport(support);
			dBasis.add(impl);
		}
		chrono.stop("rewrite results");

		chrono.start("correct clarification and supports");
		nonBinaryImplications.clear();
		// adapt results to not clarified context and compute supports
		for (Implication impl : tempBasis) {
			ISet premise = convert(impl.getPremise());
			ISet conclusion = convert(impl.getConclusion());
			ISet support = closureEngine.computeExtent(premise);
			Implication implWithSupport = new Implication(premise, conclusion, support);
			// add directly binary implications
			// populate non binary implications list to compute EBasis
			if (impl.getPremise().cardinality() < 2) {
				dBasis.add(implWithSupport);
			} else if (support.cardinality() >= minSupport) {
				nonBinaryImplications.add(implWithSupport);
			}
		}
		chrono.stop("correct clarification and supports");
		chrono.start("buildEBasis");
		dBasis.addAll(buildEBasis(nonBinaryImplications));
		chrono.stop("buildEBasis");
		return dBasis;
	}

	private ISet convert(ISet set) {
		ISet newSet = createEmptySet();
		for (Iterator<Integer> it = set.iterator(); it.hasNext();) {
			newSet.add(attrClasses.get(it.next()).first());
		}
		return newSet;
	}

	protected ISet createEmptySet() {
		return context.getFactory().createSet();
	}

	protected ISet[] computeAttributesClosure() {
		ISet[] results = new ISet[clarifiedContext.getAttributeCount()];
		for (int attr = 0; attr < clarifiedContext.getAttributeCount(); attr++) {
			ISet attribute = createEmptySet();
			attribute.add(attr);
			ISet closure = createEmptySet();
			closureEngine.closure(closure, attribute, null, null);
			results[attr] = closure;
		}
		return results;
	}

	protected List<Implication> clarify() {

		// clarify the context
		Clarification clarificateur = new Clarification(context, context.getName(), true, true, false);
		clarificateur.run();
		clarifiedContext = clarificateur.getResult();
		attrClasses = clarificateur.getAttributeClasses();
		assert clarifiedContext.getAttributeCount() == attrClasses.size();
		List<Implication> equivBasis = new ArrayList<>();
		for (int attr = 0; attr < attrClasses.size(); attr++) {
			ISet attrClass = attrClasses.get(attr);
			if (attrClass.cardinality() > 1) {
				Iterator<Integer> classIterator = attrClass.iterator();
				int first = classIterator.next();
				ISet conclusion = createEmptySet();
				conclusion.add(first);
				while (classIterator.hasNext()) {
					ISet premise = createEmptySet();
					premise.add(classIterator.next());
					equivBasis.add(new Implication(premise, conclusion, 0));
					equivBasis.add(new Implication(conclusion, premise, 0));
				}
			}
		}
		return equivBasis;
	}

	protected List<Implication> computeNonBinaryBasis(int target, ISet[] closures) {
		List<Implication> basis = new ArrayList<>();

		Set<ISet> minGens = computeMinimalGenerators(target);
		Set<ISet> covers = computeMinimalCovers(minGens, closures);
		for (ISet premise : covers) {
			if (premise.cardinality() > 1) {
				ISet conclusion = createEmptySet();
				conclusion.add(target);
				basis.add(new Implication(premise, conclusion, 0));
			}
		}
		return basis;
	}

	protected List<Implication> computeBinaryBasis() {
		// build graph of binary dependencies between attributes
		SimpleDirectedGraph<Integer, ImplicationEdge> graph = new SimpleDirectedGraph<>(ImplicationEdge.class);
		int alwaysTrueAttribute = -1;
		for (int attr1 = 0; attr1 < clarifiedContext.getAttributeCount(); attr1++) {
			if (clarifiedContext.getExtent(attr1).cardinality() == clarifiedContext.getObjectCount())
				alwaysTrueAttribute = attr1;
			else
				for (int attr2 = 0; attr2 < clarifiedContext.getAttributeCount(); attr2++) {
					if (attr1 == attr2)
						continue;
					if (clarifiedContext.getExtent(attr2).containsAll(clarifiedContext.getExtent(attr1))) {
						ISet premise = createEmptySet();
						premise.add(attr1);
						ISet conclusion = createEmptySet();
						conclusion.add(attr2);
						graph.addVertex(attr1);
						graph.addVertex(attr2);
						graph.addEdge(attr1, attr2, new ImplicationEdge(
								new Implication(premise, conclusion, clarifiedContext.getExtent(attr1))));
						ISet premises = binaryPremises.get(attr2);
						if (premises == null) {
							premises = context.getFactory().createSet();
							binaryPremises.put(attr2, premises);
						}
						premises.add(attr1);
					}
				}
		}
		List<Implication> binaryBasis = new ArrayList<>();
		// search always true attribute if exist
		if (alwaysTrueAttribute >= 0) {
			ISet premise = createEmptySet();
			ISet conclusion = createEmptySet();
			conclusion.add(alwaysTrueAttribute);
			binaryBasis.add(new Implication(premise, conclusion, context.getObjectCount()));
			graph.removeVertex(alwaysTrueAttribute);
		}
		// remove some edges with transitive reduction
		TransitiveReduction.INSTANCE.reduce(graph);
		for (ImplicationEdge edge : getEdgesInTopologicalOrder(graph)) {
			binaryBasis.add(edge.getImplication());
		}
		return binaryBasis;
	}

	protected List<ImplicationEdge> getEdgesInTopologicalOrder(SimpleDirectedGraph<Integer, ImplicationEdge> graph) {
		List<ImplicationEdge> orderedEdges = new ArrayList<>();

		TopologicalOrderIterator<Integer, ImplicationEdge> topologicalIterator = new TopologicalOrderIterator<>(graph);

		while (topologicalIterator.hasNext()) {
			int current = topologicalIterator.next();
			Set<ImplicationEdge> outgoing = graph.outgoingEdgesOf(current);

			for (ImplicationEdge edge : outgoing) {
				orderedEdges.add(edge);
			}
		}

		return orderedEdges;
	}

	protected Set<ISet> computeMinimalCovers(Set<ISet> covers, ISet[] closures) {
		Set<ISet> result = new HashSet<>(covers);
		for (ISet g : covers) {
			ISet union = null;
			for (ISet h : covers) {
				if (g != h) {
					// case 1
					if (g.containsAll(h)) { // if h <= g
						result.remove(g);
					}
					// case 2
					else {
						if (union == null) {
							// build union of closure of each attributes of g
							union = createEmptySet();
							if (g.cardinality() > 1)
								for (Iterator<Integer> it = g.iterator(); it.hasNext();)
									union.addAll(closures[it.next()]);
						}
						if (g.cardinality() > 1) {
							if (union.containsAll(h)) {
								result.remove(g);
							}
						}
					}
				}
			}
		}
		return result;
	}

    /**
     * Computes all minimal generators of an attribute b.
     *
     * <p>
     * A minimal generator of b is a minimal set X of attributes such that
     * X → b holds in the context.
     * </p>
     *
     * <p>
     * The computation is reduced to the enumeration of minimal transversals
     * of a hypergraph:
     * </p>
     *
     * <ul>
     *   <li>Vertices: attributes ≠ b</li>
     *   <li>Each hyperedge corresponds to an object not having b</li>
     *   <li>A transversal hits all such hyperedges</li>
     * </ul>
     *
     * <p>
     * Binary premises already known for b are removed as a pruning rule.
     * </p>
     *
     * @param b target attribute
     * @return the set of minimal generators of b
     */
    public Set<ISet> computeMinimalGenerators(int b) {

		// 1. Construction du hypergraphe
		List<ISet> hypergraph = new ArrayList<>();

        // Objects not supporting attribute b
        ISet objectsWithoutB = createEmptySet();
        objectsWithoutB.fill(clarifiedContext.getObjectCount());
        objectsWithoutB.removeAll(clarifiedContext.getExtent(b));

        for (Iterator<Integer> it = objectsWithoutB.iterator(); it.hasNext();) {
            int o = it.next();

            // Hyperedge = attributes missing in object o
            ISet edge = createEmptySet();
            edge.fill(clarifiedContext.getAttributeCount());
            edge.removeAll(clarifiedContext.getIntent(o));
            edge.remove(b);

            // OPT 1: remove known binary premises
            if (binaryPremises.get(b) != null) {
                edge.removeAll(binaryPremises.get(b));
            }

            // Empty edge ⇒ no generator exists
            if (edge.isEmpty()) {
                return Collections.emptySet();
            }

            hypergraph.add(edge);
        }

		// -------------------------
		// edge size ordering
		// -------------------------
		hypergraph.sort(Comparator.comparingInt(ISet::cardinality));
		
		Set<ISet> covers = new HashSet<>();
		ISet current = createEmptySet();

		generateCovers(hypergraph, 0, current, covers);

		return covers;
	}

    /**
     * Recursive enumeration of minimal transversals of the hypergraph.
     *
     * <p>
     * The algorithm explores attribute choices edge by edge, ensuring:
     * </p>
     * <ul>
     *   <li>Coverage of all hyperedges</li>
     *   <li>Minimality by subset checking</li>
     *   <li>Early pruning when support becomes zero</li>
     * </ul>
     */
    protected void generateCovers(
        List<ISet> hypergraph,
        int index,
        ISet current,
        Set<ISet> result) {

		// Toutes les arêtes sont couvertes → générateur trouvé
		if (index == hypergraph.size()) {
			result.add(current.clone());
			return;
		}

		ISet edge = hypergraph.get(index);

		// Arête déjà couverte
		if (current.intersects(edge)) {
			generateCovers(hypergraph, index + 1, current, result);
			return;
		}

		// Choix d’un attribut de l’arête
		for (Iterator<Integer> it = edge.iterator(); it.hasNext();) {
			int attr = it.next();

			if (current.contains(attr))
				continue;

			current.add(attr);
			boolean tooSmall = false;
			if (minSupport > 0) {
				// PRUNE 1 : support == 0 when minSupport > 0
				ISet support = null;
				for (Iterator<Integer> it2 = current.iterator(); it2.hasNext();) {
					int a = it2.next();
					if (support == null) {
						support = clarifiedContext.getExtent(a).clone();
					} else {
						support.retainAll(clarifiedContext.getExtent(a));
						if (support.cardinality() == 0) {
							tooSmall = true;
							break;
						}
					}
				}
			}
			if (!tooSmall) {

				// PRUNE 2 : minimalité
				if (!isAlreadyCovered(current, hypergraph)) {
					generateCovers(hypergraph, index + 1, current, result);
				}
			}

			current.remove(attr);
		}
	}

	private boolean isAlreadyCovered(ISet candidate, List<ISet> hypergraph) {

		for (Iterator<Integer> it = candidate.iterator(); it.hasNext();) {
			int attr = it.next();

			ISet subset = candidate.clone();
			subset.remove(attr);

			boolean coversAll = true;
			for (ISet edge : hypergraph) {
				if (!subset.intersects(edge)) {
					coversAll = false;
					break;
				}
			}

			if (coversAll)
				return true; // candidate non-cover
		}

		return false;
	}

	protected class ImplicationEdge extends DefaultEdge {
		private final Implication implication;

		public ImplicationEdge(Implication implication) {
			this.implication = implication;
		}

		public Implication getImplication() {
			return implication;
		}

		@Override
		public String toString() {
			return implication.toString();
		}
	}

    /**
     * Parallel builder for non-binary implications.
     *
     * <p>
     * Each attribute is processed independently:
     * minimal generators of b depend only on b and the clarified context.
     * </p>
     *
     * <p>
     * This makes attribute-level parallelism sound and optimal.
     * </p>
     */
    public class ParallelBasisBuilder {

		private static final int POISON_PILL = -1;

		private final BlockingQueue<Integer> queue;
		private final List<Implication> resultList = Collections.synchronizedList(new ArrayList<>());
		private final ISet[] closures;
		private final int nbThreads;
		private double counter=0.0;

		public ParallelBasisBuilder(ISet[] closures) {
			this.closures = closures;
			this.queue = new LinkedBlockingQueue<>();
			if (maxThreads < 1) {
				int cores = Runtime.getRuntime().availableProcessors();
				this.nbThreads = Math.min(cores, context.getAttributeCount());
			} else
				this.nbThreads = 1; // mono
		}

		public List<Implication> run() throws InterruptedException {
			ExecutorService executor = Executors.newFixedThreadPool(nbThreads);

			// Lancer les workers
			for (int i = 0; i < nbThreads; i++) {
				executor.submit(() -> {
					try {
						while (true) {
							int attr = queue.take();
							if (attr == POISON_PILL) {
								break; // Fin pour ce worker
							}else {
								System.out.println(++counter);								
							}
							// Calculer et stocker
							List<Implication> basisPart = computeNonBinaryBasis(attr, closures);
							resultList.addAll(basisPart);
						}
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				});
			}

			// Placer les jobs dans la queue
			for (int attr = 0; attr < clarifiedContext.getAttributeCount(); attr++) {
				queue.put(attr);
			}

			// Envoyer les pilules empoisonnées
			for (int i = 0; i < nbThreads; i++) {
				queue.put(POISON_PILL);
			}

			// Attendre la fin
			executor.shutdown();
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

			return resultList;
		}
	}

    /**
     * Builds the E-basis from the D-basis when no D-cycles are present.
     *
     * <p>
     * According to Adaricheva et al. (2013), the E-basis exists iff
     * the dependency graph of the D-basis is acyclic.
     * </p>
     *
     * <p>
     * The reduction keeps all binary implications and selects,
     * for each conclusion x, the premises with minimal closures.
     * </p>
     */
    public List<Implication> buildEBasis(List<Implication> basis) {
		// 1) Détection des D-cycles
		if (!RuleUtilities.findDCycles(basis).isEmpty()) {
			// Pas de E-basis possible
			System.out.println(
					"E-basis cannot be built: " + RuleUtilities.findDCycles(basis).size() + " DCycle(s) found");
//			for (ISet set : RuleUtilities.findDCycles(basis))
//				System.out.println(set);
			return basis;
		}

		// 2) Regroupement des implications par conclusion
		Map<Integer, List<Implication>> byConclusion = groupByConclusion(basis);

		List<Implication> eBasis = new ArrayList<>();

		// 3) Pour chaque élément x, calcul de M*(x)
		for (Map.Entry<Integer, List<Implication>> entry : byConclusion.entrySet()) {
			List<Implication> impls = entry.getValue();

			// (a) garder toutes les implications binaires
			List<Implication> nonBinary = new ArrayList<>();
			for (Implication impl : impls) {
				if (impl.getPremise().cardinality() <= 1) {
					eBasis.add(impl);
				} else {
					nonBinary.add(impl);
				}
			}

			// (b) filtrer les non-binaires par minimalité de φ(X)
			eBasis.addAll(filterByMinimalClosure(nonBinary));
		}

		return eBasis;
	}

	/**
	 * Filtre les implications X -> x en ne gardant que celles dont φ(X) est
	 * minimale par inclusion.
	 */
	private List<Implication> filterByMinimalClosure(List<Implication> impls) {
		List<Implication> result = new ArrayList<>();
		int n = impls.size();

		if (n <= 1)
			return impls;

		// Pré-calcul des fermetures
		Map<Implication, ISet> closures = new HashMap<>(n);
		for (Implication impl : impls) {
			ISet closure = createEmptySet();
			closureEngine.closure(closure, impl.getPremise(), null, null);
			closures.put(impl, closure);
		}

		for (int i = 0; i < n; i++) {
			Implication implI = impls.get(i);
			ISet ci = closures.get(implI);

			boolean dominated = false;
			for (int j = 0; j < n; j++) {
				if (i == j)
					continue;

				Implication implJ = impls.get(j);
				ISet cj = closures.get(implJ);

				// cj ⊂ ci
				if (cj.containsAll(ci) == false && ci.containsAll(cj)) {
					dominated = true;
					break;
				}
			}

			if (!dominated)
				result.add(implI);
		}

		return result;
	}

	/**
	 * Regroupe les implications par attribut de conclusion. On suppose que les
	 * conclusions sont des singletons.
	 */
	private Map<Integer, List<Implication>> groupByConclusion(List<Implication> basis) {
		Map<Integer, List<Implication>> map = new HashMap<>();

		for (Implication impl : basis) {
			Iterator<Integer> it = impl.getConclusion().iterator();
			if (!it.hasNext())
				continue;

			int x = it.next();
			map.computeIfAbsent(x, k -> new ArrayList<>()).add(impl);
		}
		return map;
	}

}
