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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;

import org.jgrapht.alg.util.Pair;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.core.RuleBasis;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;
import fr.lirmm.fca4j.util.RuleUtilities;

public class DBaseCalculator16 implements AbstractAlgo<List<Implication>> {
	protected int minSupport = 0;
	/** The matrix. */
	protected IBinaryContext context; // ressource de depart
	protected ClosureDirect closureEngine;

	protected Chrono chrono = new Chrono("myChrono"); // eventually a chrono to store execution
	// time

	/** The implications. */
	protected List<Implication> implications;

	public DBaseCalculator16(IBinaryContext context) {
		this.context = context;
		closureEngine = new ClosureDirect(context);
	}

	@Override
	public void run() {
		implications = computeDBasis();
		for(String serie:chrono.getSerieNames())
		{		
		System.out.println(serie + chrono.getResult(serie));
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

		List<Implication> dBasis = new ArrayList<>();
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
					dBasis.add(new Implication(premise, conclusion, context.getExtent(attr1)));
				}
			}

		for (int target = 0; target < context.getAttributeCount(); target++) {
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
				if (isValidImplication(premise, target)) {
					ISet conclusion = context.getFactory().createSet();
					conclusion.add(target);
					dBasis.add(new Implication(premise, conclusion, 0));
				}
			}
			chrono.stop("verify validity");
		}
		// Sort implication by cardinality
		Collections.sort(dBasis, new Comparator<Implication>() {
			@Override
			public int compare(Implication a1, Implication a2) {
				return Integer.compare(a1.getPremise().cardinality(), a2.getPremise().cardinality());
			}
		});
		// filter redundants
		chrono.start("filter redundancy");
		List<Implication> dBasisFiltered = new ArrayList<>();
		for (Implication imp : dBasis) {
			if (!RuleUtilities.isDerivable(imp.getConclusion().iterator().next(), imp.getPremise(), dBasisFiltered))
				dBasisFiltered.add(imp);
		}
		chrono.stop("filter redundancy");
		return dBasisFiltered;

	}

	boolean isValidImplication(ISet premise, int conclusion) {
		for (int obj = 0; obj < context.getObjectCount(); obj++) {
			ISet intent = context.getIntent(obj);
			if (intent.containsAll(premise) && !intent.contains(conclusion))
				return false;
		}
		return true;
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

	private boolean isMinimal(ISet candidate, Iterable<ISet> set) {
		for (ISet existing : set) {
			if (existing.containsAll(candidate))
				return false;
		}
		return true;
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
}
