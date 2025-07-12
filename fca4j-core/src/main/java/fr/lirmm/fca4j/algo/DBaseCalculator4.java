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

public class DBaseCalculator4 implements AbstractAlgo<List<Implication>> {

	protected int minSupport = 0;
	/** The matrix. */
	protected IBinaryContext context; // ressource de depart

	protected Chrono chrono = null; // eventually a chrono to store execution
									// time
	ClosureDirect closureEngine;

	/** The implications. */
	protected List<Implication> implications;

	public DBaseCalculator4(IBinaryContext context) {
		this.context = context;
		this.implications = new ArrayList<>();
		closureEngine = new ClosureDirect(context);
	}

	@Override
	public void run() {
		implications = computeDirectBase();
	}

	@Override
	public List<Implication> getResult() {
		return implications;
	}

	@Override
	public String getDescription() {
		return "DBase";
	}

	List<Implication> computeDirectBase() {
		List<Implication> base = new ArrayList<>();
		for (int b = 0; b < context.getAttributeCount(); b++) {
			Set<ISet> Fb = computeNegativeSupports(b);
//	        HittingSetSolver algo=new HittingSetSolver(Fb, context);
//	        HittingSetSolver2 algo=new HittingSetSolver2(Fb, context);
	        HittingSetSolver3 algo=new HittingSetSolver3(Fb, context);
//			MMCSHittingSetSolver algo = new MMCSHittingSetSolver(Fb, context);
			algo.run();
			ISet conclusion = context.getFactory().createSet();
			conclusion.add(b);
			for (ISet premise : algo.getResult()) {
				/*
				 * if (isValidImplication(premise, b)) { base.add(new Implication(premise,
				 * conclusion, 1)); }
				 */

				// Vérifie si b est déjà déductible de A via les implications déjà générées
				if (isValidImplication(premise, b)&& !RuleUtilities.computeClosure(premise, base).contains(b)) {
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
		return base;
	}

	Set<ISet> computeNegativeSupports(int b) {
		Set<ISet> Fb = new HashSet<>();
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
