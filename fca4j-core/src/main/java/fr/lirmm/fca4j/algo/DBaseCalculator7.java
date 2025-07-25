package fr.lirmm.fca4j.algo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import fr.lirmm.fca4j.algo.DBaseCalculator6.ClosureTracer;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.util.Chrono;
import fr.lirmm.fca4j.util.RuleUtilities;

public class DBaseCalculator7 implements AbstractAlgo<List<Implication>> {

	protected int minSupport = 0;
	/** The matrix. */
	protected IBinaryContext context; // ressource de depart

	protected Chrono chrono = null; // eventually a chrono to store execution
									// time
	/** The implications. */
	protected List<Implication> implications;

	public DBaseCalculator7(IBinaryContext context) {
		this.context = context;
		this.implications = new ArrayList<>();
	}

	@Override
	public void run() {
		DBaseCalculator15 calculator = new DBaseCalculator15(context) {
			public List<Implication> buildInitBase() {
				DBaseV16 calculator2 = new DBaseV16(context);
				calculator2.run();
				return new ArrayList<>(calculator2.getResult());
			}
		};
	calculator.run();

	List<Implication> list=new ArrayList<>(calculator.getResult());
	implications=list;
	/*
	 * for(Iterator<Implication>
	 * it=RuleUtilities.iteratorImplications(list);it.hasNext();) {
	 * implications.add(it.next()); }
	 */ }

	@Override
	public List<Implication> getResult() {
		return implications;
	}

	@Override
	public String getDescription() {
		return "DBase";
	}

	List<Implication> computeBaseWithHittingSets(List<Implication> base) {
		for (int b = 0; b < context.getAttributeCount(); b++) {
			List<ISet> Fb = computeNegativeSupports(b);
			MMCSHittingSetSolver algo = new MMCSHittingSetSolver(Fb, context);
			algo.run();
			ISet conclusion = context.getFactory().createSet();
			conclusion.add(b);
			for (ISet premise : algo.getResult()) {

				// Vérifie si b est déjà déductible de A via les implications déjà générées
				if (isValidImplication(premise, b) /* && !RuleUtilities.computeClosure(premise, base).contains(b) */) {
					base.add(new Implication(premise, conclusion.clone(), 0));
				}

			}

			// Implications singleton : a → b
			for (int a = 0; a < context.getAttributeCount(); a++) {
				if (a != b) {
					ISet premise = context.getFactory().createSet();
					premise.add(a);
					if (isValidImplication(premise, b)) {
						base.add(new Implication(premise, conclusion, 0));
					}
				}
			}
		}
		// Sort implication by cardinality
		Collections.sort(base, new Comparator<Implication>() {
			@Override
			public int compare(Implication a1, Implication a2) {
				return Integer.compare(a1.getPremise().cardinality(), a2.getPremise().cardinality());
			}
		});

		return base;
	}

	List<ISet> computeNegativeSupports(int b) {
		List<ISet> Fb = new ArrayList<>();
		for (int obj = 0; obj < context.getObjectCount(); obj++) {
			ISet support = context.getIntent(obj);
			if (!support.contains(b) /* && support.cardinality()>0 */)
				Fb.add(support);
		}
		return Fb;
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
