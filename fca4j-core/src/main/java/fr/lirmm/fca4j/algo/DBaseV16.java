package fr.lirmm.fca4j.algo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;

import org.jgrapht.alg.util.Pair;

import fr.lirmm.fca4j.core.BinaryContext;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.core.RuleBasis;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;
import fr.lirmm.fca4j.util.RuleUtilities;

public class DBaseV16 implements AbstractAlgo<List<Implication>> {
	protected int minSupport = 0;
	/** The matrix. */
	protected IBinaryContext context; // ressource de depart
	protected ClosureDirect closureEngine;

	protected Chrono chrono = new Chrono("myChrono"); // eventually a chrono to store execution
	// time

	/** The implications. */
	protected List<Implication> implications;

	public DBaseV16(IBinaryContext context) {
		this.context = context;
		closureEngine = new ClosureDirect(context);
	}

	@Override
	public void run() {
		// compute d-basis
		implications = computeDBasis();
		for (String serie : chrono.getSerieNames()) {
			System.out.println(serie + " time: " + chrono.getResult(serie));
		}
	}

	@Override
	public List<Implication> getResult() {
		return implications;
	}

	@Override
	public String getDescription() {
		return "DBase";
	}

	public List<Implication> computeDBasis() {

		List<Implication> binaryBasis = new ArrayList<>();
		// find all binary implications
		for (int attr1 = 0; attr1 < context.getAttributeCount(); attr1++)
			for (int attr2 = 0; attr2 < context.getAttributeCount(); attr2++) {
				if (attr1 == attr2)
					continue;
				boolean valid = true;
				int support = 0;
				if (context.getExtent(attr2).containsAll(context.getExtent(attr1))) {
					ISet premise = context.getFactory().createSet();
					premise.add(attr1);
					ISet conclusion = context.getFactory().createSet();
					conclusion.add(attr2);
					binaryBasis.add(new Implication(premise, conclusion, context.getExtent(attr1)));
				}
			}
		List<Implication> dBasis = new ArrayList<>(binaryBasis);

		for (int target = 0; target < context.getAttributeCount(); target++) {
			List<Implication> tempBasis = new ArrayList<>(binaryBasis);
			System.out.println("traitement attr=" + target);
			chrono.start("computeMinimalGenerators");
			Set<ISet> minGens = computeMinimalGenerators(target);
			System.out.println("minGens=" + minGens.size());
			chrono.stop("computeMinimalGenerators");
			chrono.start("extractCovers");
			Set<ISet> covers = extractCovers(minGens);
			chrono.stop("extractCovers");
			System.out.println("covers=" + covers.size());
			chrono.start("verify validity");
			for (ISet premise : covers) {
				ISet conclusion = context.getFactory().createSet();
				conclusion.add(target);
				tempBasis.add(new Implication(premise, conclusion, 0));
			}
			System.out.println("tempBasis before" + tempBasis.size());
			// Sort implication by cardinality
			Collections.sort(tempBasis, new Comparator<Implication>() {
				@Override
				public int compare(Implication a1, Implication a2) {
					return Integer.compare(a1.getPremise().cardinality(), a2.getPremise().cardinality());
				}
			});
			tempBasis = buildDirectBase(tempBasis);
			System.out.println("tempBasis after" + tempBasis.size());
			tempBasis.removeAll(binaryBasis);
			dBasis.addAll(tempBasis);
			chrono.stop("verify validity");
		}
		// Sort implication by cardinality
		Collections.sort(dBasis, new Comparator<Implication>() {
			@Override
			public int compare(Implication a1, Implication a2) {
				return Integer.compare(a1.getPremise().cardinality(), a2.getPremise().cardinality());
			}
		});
		/*
		 * // Optimisation from V2 List<Implication> sigmaDo = new ArrayList<>(); for
		 * (Implication impl : dBasis) { ISet A = impl.getPremise().clone(); ISet B =
		 * impl.getConclusion().clone(); // copie de la conclusion for (Implication
		 * implC : dBasis) { // Si les prémisses sont égales, on fait l’union des
		 * conclusions. if (implC.getPremise().equals(A)) {
		 * B.addAll(implC.getConclusion()); } // Si la prémisse de implC est strictement
		 * incluse dans A, retirer ses attributs de la conclusion.
		 * if(A.containsAll(implC.getPremise()) &&
		 * (A.cardinality()>implC.getPremise().cardinality())){
		 * B.removeAll(implC.getConclusion()); } } if (!B.isEmpty()) { sigmaDo.add(new
		 * Implication(A, B,0)); // addImplication(new Implication(A,
		 * B,supportBidon),sigmaDo); } }
		 */

		// filter redundants
		System.out.println("dBasis before: " + dBasis.size());
		chrono.start("filter redundancy");
//		List<Implication> directBase = buildDirectBase(dBasis);
		List<Implication> directBase = reduceBase(dBasis);
		System.out.println("dBasis after: " + directBase.size());
		chrono.stop("filter redundancy");
		return directBase;

//		return dBasis;
	}

	public Set<ISet> extractCovers(Set<ISet> minGenerators) {
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

	public Set<ISet> computeMinimalGenerators(int b) {
		List<ISet> hypergraph = new ArrayList<>();

		// Étape 1 : récupérer les objets ne contenant pas b (non-supports)
		ISet objectsWithoutB = context.getFactory().createSet();
		objectsWithoutB.fill(context.getObjectCount());
		objectsWithoutB.removeAll(context.getExtent(b));
		// build hypergraph
		for (Iterator<Integer> it = objectsWithoutB.iterator(); it.hasNext();) {
			int o = it.next();
			ISet edge = context.getFactory().createSet();
			edge.fill(context.getAttributeCount()); // M
			edge.removeAll(context.getIntent(o)); // M \ intent(o)
			edge.remove(b);
			hypergraph.add(edge);
		}

		// Étape 2 : appel au générateur de transversaux minimaux
		Set<ISet> result = new HashSet<>();
		ISet current = context.getFactory().createSet();
		generateTransversals(hypergraph, 0, current, result);
		return result;
	}

	private void generateTransversals(List<ISet> hypergraph, int index, ISet current, Set<ISet> result) {
		// Si tous les hyperarêtes sont frappées : on a un transversal
		if (index == hypergraph.size()) {
			result.add(current.clone());
			return;
		}

		ISet edge = hypergraph.get(index);
		// Si l’arête est déjà frappée → passer à la suivante
		if (!current.newIntersect(edge).isEmpty()) {
			generateTransversals(hypergraph, index + 1, current, result);
			return;
		}

		// Sinon : pour chaque attribut de l’arête, essayer de l’ajouter
		for (Iterator<Integer> it = edge.iterator(); it.hasNext();) {
			int attr = it.next();
			if (current.contains(attr))
				continue;
			current.add(attr);
			generateTransversals(hypergraph, index + 1, current, result);
			current.remove(attr);
		}
	}

	public List<Implication> buildDirectBase(List<Implication> candidateBase) {
		ClosureEngine engine = new ClosureEngine();
		List<Implication> directBase = new ArrayList<>();

		// On suppose que les implications sont triées par taille de prémisse
		candidateBase.sort(Comparator.comparingInt(impl -> impl.getPremise().cardinality()));

		for (Implication impl : candidateBase) {
			ISet closure = engine.computeClosure(impl.getPremise());

			if (!closure.containsAll(impl.getConclusion())
					/*|| !RuleUtilities.isDirect(impl.getConclusion(), engine.getBase())*/) {
				engine.add(impl);
				directBase.add(impl);
			}
			// Sinon : l'implication est redondante et déjà couverte par la base actuelle
		}

		return directBase;
	}

	public List<Implication> reduceBase(List<Implication> candidateBase) {
		List<Implication> result = new ArrayList<>();
		HashMap<Integer, Set<ISet>> closuresByConclusion = new HashMap<>();
		for (int attr = 0; attr < context.getAttributeCount(); attr++) {
			closuresByConclusion.put(attr, new LinkedHashSet<>());
		}
		HashSet<Implication> premises = new HashSet<>();
		for (Implication impl : candidateBase) {
			boolean redundant = false;
			int conclusion = impl.getConclusion().first();
			Set<ISet> closureList = closuresByConclusion.get(conclusion);
			ISet closure=RuleUtilities.computeDirectClosure(impl.getPremise(), result);
			for (ISet set : closureList) {
				if (closure.contains(conclusion) && closure.containsAll(set)) {
					redundant = true;
					break;
				}
			}
			if (!redundant) {
				result.add(impl);
				if(closure.contains(conclusion)) closureList.add(closure);
			}
		}
		return result;
	}

	public class ClosureEngine {
		private final Map<Integer, List<Implication>> indexByAttribute = new HashMap<>();
		private final List<Implication> base = new ArrayList<>();

		public ClosureEngine() {
		}

		public void add(Implication impl) {
			base.add(impl);
			for (Iterator<Integer> it = impl.getPremise().iterator(); it.hasNext();) {
				int attr = it.next();
				indexByAttribute.computeIfAbsent(attr, k -> new ArrayList<>()).add(impl);
			}
		}

		public ISet computeClosure(ISet input) {
			ISet closure = input.clone();
			Deque<Integer> frontier = new ArrayDeque<>();
			Set<Integer> visited = new HashSet<>();

			for (Iterator<Integer> it = closure.iterator(); it.hasNext();) {
				int attr = it.next();
				frontier.add(attr);
				visited.add(attr);
			}

			while (!frontier.isEmpty()) {
				int current = frontier.poll();
				List<Implication> candidates = indexByAttribute.get(current);
				if (candidates == null)
					continue;

				for (Implication impl : candidates) {
					if (!closure.containsAll(impl.getPremise()))
						continue;
					int conclusion = impl.getConclusion().first();
					if (!closure.contains(conclusion)) {
						closure.add(conclusion);
						if (!visited.contains(conclusion)) {
							frontier.add(conclusion);
							visited.add(conclusion);
						}
					}
				}
			}

			return closure;
		}

		public boolean implies(ISet premise, ISet conclusion) {
			ISet closure = computeClosure(premise);
			return closure.containsAll(conclusion);
		}

		public List<Implication> getBase() {
			return base;
		}
	}
}
