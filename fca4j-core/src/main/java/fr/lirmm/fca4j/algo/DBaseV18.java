/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.algo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
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

import org.jgrapht.Graph;
import org.jgrapht.alg.TransitiveReduction;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.util.Chrono;
import fr.lirmm.fca4j.util.MinGenUtils;

public class DBaseV18 implements AbstractAlgo<List<Implication>> {
	protected int minSupport = 0;
	/** The matrix. */
	protected IBinaryContext context; // ressource de depart
	protected IBinaryContext clarifiedContext; // ressource pour le calcul
	protected List<ISet> attrClasses;
	protected ClosureDirect closureEngine;
	private int maxThreads;

	protected Chrono chrono = new Chrono("myChrono"); // eventually a chrono to store execution
	// time

	/** The implications. */
	protected List<Implication> implications;

	public DBaseV18(IBinaryContext context, int minSupport, int maxThreads) {
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

	public List<Implication> computeDBasis() {
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
		try {
			List<Implication> nonBinaryImplications = builder.run();
			System.out.println("nonBinaryImplications="+nonBinaryImplications.size());
			tempBasis.addAll(nonBinaryImplications);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
			if(impl.getPremise().cardinality()<2 || support.cardinality()>=minSupport)
				dBasis.add(new Implication(premise, conclusion, support));
		}
		chrono.stop("correct clarification and supports");
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

		chrono.start("computeMinimalGenerators");
		Set<ISet> minGens = computeMinimalGenerators(target);
		chrono.stop("computeMinimalGenerators");
		chrono.start("extractCovers");
		Set<ISet> covers = extractCovers(minGens);
		covers = computeMinimalCovers(covers, closures);
		chrono.stop("extractCovers");
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

	protected Set<ISet> extractCovers(Set<ISet> minGenerators) {
		Set<ISet> covers = new HashSet<>();

		for (ISet gen : minGenerators) {
			boolean isCover = true;
			for (Iterator<Integer> it = gen.iterator(); it.hasNext();) {
				int attr = it.next();
				ISet subset = gen.clone();
				subset.remove(attr);
				if (minGenerators.contains(subset)) {
					isCover = false;
					break;
				}
			}
			if (isCover)
				covers.add(gen);
		}
		return covers;
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

	protected Set<ISet> computeMinimalGenerators(int b) {
		List<ISet> hypergraph = new ArrayList<>();

		// Étape 1 : récupérer les objets ne contenant pas b (non-supports)
		ISet objectsWithoutB = createEmptySet();
		objectsWithoutB.fill(clarifiedContext.getObjectCount());
		objectsWithoutB.removeAll(clarifiedContext.getExtent(b));
		// build hypergraph
		for (Iterator<Integer> it = objectsWithoutB.iterator(); it.hasNext();) {
			int o = it.next();
			ISet edge = createEmptySet();
			edge.fill(clarifiedContext.getAttributeCount()); // M
			edge.removeAll(clarifiedContext.getIntent(o)); // M \ intent(o)
			edge.remove(b);
			hypergraph.add(edge);
		}
		// Étape 2 : appel au générateur de transversaux minimaux
	    // sort edges
	    hypergraph.sort(Comparator.comparingInt(ISet::cardinality));
		Set<ISet> result = new HashSet<>();
		ISet current = createEmptySet();
		chrono.start("generateTransversals");
		generateTransversals(hypergraph, 0, current, result);
		chrono.stop("generateTransversals");
		return result;
	}

	protected void generateTransversals(List<ISet> hypergraph, int index, ISet current, Set<ISet> result) {
		// Si toutes les hyperarêtes sont concernées : on a un transversal
		if (index == hypergraph.size()) {
			result.add(current.clone());
			return;
		}
		ISet edge = hypergraph.get(index);
		// Si l’arête est déjà là -> passer à la suivante
		if (current.intersects(edge)) {
			generateTransversals(hypergraph, index + 1, current, result);
			return;
		}

		// Sinon : pour chaque attribut de l’arête, essayer de l’ajouter
		for (Iterator<Integer> it = edge.iterator(); it.hasNext();) {
			int attr = it.next();
			if (current.contains(attr))
				continue;
			current.add(attr);
//			if (closureEngine.computeExtent(edge).cardinality() < minSupport) {
//				current.remove(attr);
//				break;
//			}
			generateTransversals(hypergraph, index + 1, current, result);
			current.remove(attr);
		}
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
}
