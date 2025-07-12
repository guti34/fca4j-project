package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
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

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.core.RuleBasis;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;
import fr.lirmm.fca4j.util.RuleUtilities;

public class DBaseCalculator14 implements AbstractAlgo<List<Implication>> {
	ISet supportBidon;
	protected int minSupport = 0;
	/** The matrix. */
	protected IBinaryContext context; // ressource de depart

	protected Chrono chrono = new Chrono("myChrono"); // eventually a chrono to store execution
	// time

	/** The implications. */
	protected List<Implication> implications;
	protected List<Implication> dqBasis;

	public DBaseCalculator14(IBinaryContext context) {
		this.context = context;
		this.implications = new ArrayList<>();
		supportBidon = context.getFactory().createSet();
	}

	@Override
	public void run() {
		implications = computeDBasis();
		System.out.println("computeMinimalGenerators " + chrono.getResult("computeMinimalGenerators"));
		System.out.println("extractCovers " + chrono.getResult("extractCovers"));
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

		LinCbO linCbO = new LinCbO(context, chrono, new ClosureDirect(context), false);
		linCbO.run();
		dqBasis = linCbO.getResult();
		System.out.println("lincbo done");
		List<Implication> dBasis = new ArrayList<>();

		for (int target = 0; target < context.getAttributeCount(); target++) {
			System.out.println("traitement attr=" + target);
			chrono.start("computeMinimalGenerators");
			Set<ISet> minGens = computeMinimalGenerators(target);
			System.out.println("minGens="+minGens.size());
			chrono.stop("computeMinimalGenerators");
			chrono.start("extractCovers");
			Set<ISet> covers = extractCovers(minGens);
			chrono.stop("extractCovers");

			for (ISet premise : covers) {
				ISet conclusion = context.getFactory().createSet();
				conclusion.add(target);
				dBasis.add(new Implication(premise, conclusion, 0));
			}
		}
		// Sort implication by cardinality
		Collections.sort(dBasis, new Comparator<Implication>() {
			@Override
			public int compare(Implication a1, Implication a2) {
				return Integer.compare(a1.getPremise().cardinality(), a2.getPremise().cardinality());
			}
		});
		return dBasis;
		// ⚠️ Optional: order D-basis according to dependency depth (topological sort)
//	        return sortImplications(dBasis);

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
	public Set<ISet> computeMinimalGenerators(int target) {
	    Set<ISet> result = new HashSet<>();

	    ISet fullSet = context.getFactory().createSet();
	    fullSet.fill(context.getAttributeCount());
	    fullSet.remove(target);

	    ISet current = context.getFactory().createSet();
	    explore(current, fullSet.iterator(),fullSet, target, result);

	    return result;
	}
	private void explore(ISet current, Iterator<Integer> iterator, ISet available, int target, Set<ISet> result) {
	    if (RuleUtilities.computeClosure(current, dqBasis).contains(target)) {
			if (isMinimal(current, target)) {
				result.add(current.clone());
			}
	        return;
	    }

		while (iterator.hasNext()) {
			int attr = iterator.next();
			if (!current.contains(attr)) {
				current.add(attr);
				// On crée un nouvel itérateur à partir de l'état courant (copie)
				ISet remaining = available.clone();
				remaining.clear(attr); // nécessite un helper si non natif

				explore(current, remaining.iterator(), available, target, result);
				current.remove(attr); // backtrack
			}
		}
	}

	private boolean isMinimal(ISet candidate, int target) {
		Iterator<Integer> it = candidate.iterator();

		while (it.hasNext()) {
			int attr = it.next();

			ISet subset = candidate.clone();
			subset.remove(attr);
			if (RuleUtilities.computeClosure(subset, dqBasis).contains(target)) {
				return false; // Ce sous-ensemble implique déjà target
			}
		}

		return true; // Aucun sous-ensemble ne permet d'inférer target
	}

}
