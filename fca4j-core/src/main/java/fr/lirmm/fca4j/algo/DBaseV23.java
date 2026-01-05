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

public class DBaseV23 implements AbstractAlgo<List<Implication>> {
	protected int minSupport = 0;
	/** The matrix. */
	protected IBinaryContext context; // ressource de depart
	protected IBinaryContext clarifiedContext; // ressource pour le calcul
	protected List<ISet> attrClasses;
	protected ClosureDirect closureEngine;
	private int maxThreads;
	protected Map<Integer, ISet> binaryPremises = new HashMap<>();

	protected Chrono chrono = new Chrono("myChrono"); // eventually a chrono to store execution
	// time

	/** The implications. */
	protected List<Implication> implications;

	public DBaseV23(IBinaryContext context, int minSupport, int maxThreads) {
		this.minSupport = minSupport;
		this.context = context;
		this.closureEngine = new ClosureDirect(context);
		this.maxThreads = maxThreads;
	}

	@Override
	public void run() {
		// compute d-basis
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
		chrono.start("clarify");

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
			System.out.println("nonBinaryImplications=" + nonBinaryImplications.size());
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
		// adapt results to not clarified context and compute supports
		for (Implication impl : tempBasis) {
			ISet premise = convert(impl.getPremise());
			ISet conclusion = convert(impl.getConclusion());
			ISet support = closureEngine.computeExtent(premise);
			if (impl.getPremise().cardinality() < 2 || support.cardinality() >= minSupport) {
				Implication implWithSupport = new Implication(premise, conclusion, support);
				dBasis.add(implWithSupport);
			}
		}
		chrono.stop("correct clarification and supports");
		chrono.start("buildEBasis");
		List<Implication> eBasis = buildEBasis(dBasis);
//		List<Implication> eBasis=dBasis;
		chrono.stop("buildEBasis");
		return eBasis;
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

		chrono.start("computeMinimalGenerators");
		Set<ISet> minGens = computeMinimalGenerators(target);
		chrono.stop("computeMinimalGenerators");
		chrono.start("computeMinimalCovers");
		Set<ISet> covers = computeMinimalCovers(minGens, closures);
		chrono.stop("computeMinimalCovers");
		chrono.start("create implications");
		for (ISet premise : covers) {
			if (premise.cardinality() > 1) {
				ISet conclusion = createEmptySet();
				conclusion.add(target);
				basis.add(new Implication(premise, conclusion, 0));
			}
		}
		chrono.stop("create implications");
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
	 * @param b attribute
	 * @return all minimal generators of attribute b
	 */
	protected Set<ISet> computeMinimalGenerators(int b) {

		// Pré-calcul des extents des attributs (lecture seule, thread-safe)
		ISet[] attrExtents = new ISet[clarifiedContext.getAttributeCount()];
		for (int a = 0; a < attrExtents.length; a++) {
			attrExtents[a] = clarifiedContext.getExtent(a);
		}
		// 1. Construction du hypergraphe
		List<ISet> hypergraph = new ArrayList<>();

		ISet objectsWithoutB = createEmptySet();
		objectsWithoutB.fill(clarifiedContext.getObjectCount());
		objectsWithoutB.removeAll(clarifiedContext.getExtent(b));

		for (Iterator<Integer> it = objectsWithoutB.iterator(); it.hasNext();) {
			int o = it.next();
			ISet edge = createEmptySet();
			edge.fill(clarifiedContext.getAttributeCount());
			edge.removeAll(clarifiedContext.getIntent(o));
			edge.remove(b);
			// OPT 1 : élimination des attributs binaires connus
			if (binaryPremises.get(b) != null) {
				edge.removeAll(binaryPremises.get(b));
			}
			// CAS CRITIQUE : arête vide
			if (edge.isEmpty()) {
				// Aucun générateur minimal possible
				return Collections.emptySet();
			}
			hypergraph.add(edge);
		}

		// -------------------------
		// edge size ordering
		// -------------------------
		hypergraph.sort(Comparator.comparingInt(ISet::cardinality));
		// -------------------------
		// rare-first ordering
		// -------------------------
		Map<Integer, Integer> freq = new HashMap<>();

		for (ISet edge : hypergraph) {
			for (Iterator<Integer> it = edge.iterator(); it.hasNext();) {
				int a = it.next();
				freq.put(a, freq.getOrDefault(a, 0) + 1);
			}
		}
		for (int i = 0; i < hypergraph.size(); i++) {
			ISet edge = hypergraph.get(i);

			List<Integer> attrs = new ArrayList<>();
			for (Iterator<Integer> it = edge.iterator(); it.hasNext();) {
				attrs.add(it.next());
			}

			attrs.sort(Comparator.comparingInt(a -> freq.getOrDefault(a, 0)));

			ISet ordered = createEmptySet();
			for (int a : attrs) {
				ordered.add(a);
			}

			hypergraph.set(i, ordered);
		}

		Set<ISet> covers = new HashSet<>();
		ISet current = createEmptySet();

		generateCovers(hypergraph, 0, current, covers, attrExtents);

		return covers;
	}

	protected void generateCovers(List<ISet> hypergraph, int index, ISet current, Set<ISet> result,
			ISet[] attrExtents) {

		// Toutes les arêtes sont couvertes → générateur trouvé
		if (index == hypergraph.size()) {
			result.add(current.clone());
			return;
		}

		ISet edge = hypergraph.get(index);

		// Arête déjà couverte
		if (current.intersects(edge)) {
			generateCovers(hypergraph, index + 1, current, result, attrExtents);
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
						support = attrExtents[a].clone();
					} else {
						support.retainAll(attrExtents[a]);
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
					generateCovers(hypergraph, index + 1, current, result, attrExtents);
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

	public class ParallelBasisBuilder {

		private static final int POISON_PILL = -1;

		private final BlockingQueue<Integer> queue;
		private final List<Implication> resultList = Collections.synchronizedList(new ArrayList<>());
		private final ISet[] closures;
		private final int nbThreads;

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
	 * Construction de la E-Basis à partir de la D-Basis conformément à Adaricheva
	 * et al. (2013), Section 9.
	 *
	 * Hypothèse : la D-basis est déjà calculée. Si des D-cycles sont détectés, la
	 * D-basis est retournée telle quelle.
	 */
	/**
	 * Construit la E-basis si le système est sans D-cycles, sinon retourne la
	 * D-basis inchangée.
	 */
	public List<Implication> buildEBasis(List<Implication> basis) {
		// 1) Détection des D-cycles
		if (!RuleUtilities.findDCycles(basis).isEmpty()) {
			// Pas de E-basis possible
			System.out.println("E-basis cannot be built: " + RuleUtilities.findDCycles(basis).size()+" DCycle(s) found");
			for (ISet set : RuleUtilities.findDCycles(basis))
				System.out.println(set);
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
				if (impl.getPremise().cardinality() == 1) {
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
