package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.util.Chrono;
import fr.lirmm.fca4j.util.RuleUtilities;

public class DBaseCalculator9 implements AbstractAlgo<List<Implication>> {

	protected int minSupport = 0;
	/** The matrix. */
	protected IBinaryContext context; // ressource de depart

	protected Chrono chrono = null; // eventually a chrono to store execution
									// time
	/** The implications. */
	protected List<Implication> implications;

	public DBaseCalculator9(IBinaryContext context) {
		this.context = context;
		this.implications = computeDirectBase();
	}

	@Override
	public void run() {
	}

	@Override
	public List<Implication> getResult() {
		return implications;
	}

	@Override
	public String getDescription() {
		return "DBase";
	}

	protected List<Implication> computeDirectBase() {

		List<ISet> objectIntents = new ArrayList<>();

		// Step 1: Get intent of each object (set of attributes it has)
		for (int numObj = 0; numObj < context.getObjectCount(); numObj++)
			objectIntents.add(context.getIntent(numObj));

		// Step 2: For each attribute b, build family F_b of counterexamples
		List<Implication> base = new ArrayList<>();
		for (int b = 0; b < context.getAttributeCount(); b++) {
			List<ISet> counterexamples = new ArrayList<>();
			for (ISet intent : objectIntents)
				if (!intent.contains(b))
					counterexamples.add(intent.clone());
			ISet universe = context.getFactory().createSet();
			for (ISet set : counterexamples) {
				universe.addAll(set);
			}
			List<Integer> attrList = universe.toList();

			Set<ISet> hittingSets = new HashSet<>();
			generateMinimalHittingSets(counterexamples, context.getFactory().createSet(), attrList, 0, hittingSets);

			for (ISet hs : hittingSets) {
				if (isValidImplication(hs, b)) {
					ISet conclusion = context.getFactory().createSet();
					conclusion.add(b);
					base.add(new Implication(hs, conclusion, 0));
				}
			}
		}
		return base;
	}

	private void generateMinimalHittingSets(List<ISet> families, ISet current, List<Integer> attrs, int idx, Set<ISet> results) {
		if (coversAll(current, families)) {
			for (ISet existing : results) {
				if (existing.containsAll(current))
					return;
			}
			results.add(current.clone());
			return;
		}
		if (idx >= attrs.size())
			return;

// Include attrs[idx]
		current.add(attrs.get(idx));
		generateMinimalHittingSets(families, current, attrs, idx + 1, results);
		current.remove(attrs.get(idx));

// Exclude attrs[idx]
		generateMinimalHittingSets(families, current, attrs, idx + 1, results);
	}

	private boolean coversAll(ISet candidate, List<ISet> families) {
		for (ISet fam : families) {
			if (candidate.newIntersect(fam).cardinality() == 0)
				return false;
		}
		return true;
	}

	boolean isValidImplication(ISet premise, int conclusion) {
		for (int obj = 0; obj < context.getObjectCount(); obj++) {
			ISet intent = context.getIntent(obj);
			if (intent.containsAll(premise) && !intent.contains(conclusion))
				return false;
		}
		return true;
	}

}
