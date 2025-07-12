package fr.lirmm.fca4j.algo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.util.Chrono;

public class DBaseCalculator6 implements AbstractAlgo<List<Implication>> {

	protected int minSupport = 0;
	/** The matrix. */
	protected IBinaryContext context; // ressource de depart

	protected Chrono chrono = null; // eventually a chrono to store execution
									// time
	ClosureDirect closureEngine;

	/** The implications. */
	protected List<Implication> implications;

	public DBaseCalculator6(IBinaryContext context) {
		this.context = context;
		this.implications = new ArrayList<>();
		closureEngine = new ClosureDirect(context);
	}

	@Override
	public void run() {
		ClosureDirect closureEngine = new ClosureDirect(context);
		LinCbO linCbO = new LinCbO(context, chrono, closureEngine, false);
		linCbO.run();
		List<Implication> dgBasis = linCbO.getResult();
		List<Implication> dBasis = new ArrayList<>();
		computeBaseWithHittingSets(dBasis);		
		for (Implication dg : dgBasis) {
			ClosureTracer tracer = new ClosureTracer(dBasis);
			ISet closure = tracer.computeClosure(dg.getPremise());
			for (Iterator<Integer> it = dg.getConclusion().iterator(); it.hasNext();) {
				int conclusion = it.next();
				if (!closure.contains(conclusion)) {
					ISet minimalPremise = tracer.getReducedAttributes(dg.getPremise());
					ISet conc = context.getFactory().createSet();
					conc.add(conclusion);
					Implication newImplication = new Implication(minimalPremise, conc, dg.getSupport());
					dBasis.add(newImplication);
				}
			}
		}
		implications = dBasis;
	}

	@Override
	public List<Implication> getResult() {
		return implications;
	}

	@Override
	public String getDescription() {
		return "DBase";
	}

	public class ClosureTracer {
		List<Implication> basis;
		Deque<Implication> justification = new ArrayDeque<>();

		public ClosureTracer(List<Implication> basis) {
			this.basis = basis;
		}

		protected ISet computeClosure(ISet attrs) {
			ISet closure = attrs.clone();
			boolean changed;
			do {
				changed = false;
				for (Implication imp : basis) {
					if (closure.containsAll(imp.getPremise())) {
						if (closure.containsAll(imp.getConclusion())) {
							justification.push(imp);
						} else {
							closure.addAll(imp.getConclusion());
							changed = true;
						}
					}
				}
			} while (changed);
			return closure;
		}

		// Récupère les attributs d'origine ayant causé l'ajout de "target"
		public ISet getReducedAttributes(ISet inputAttrs) {
			ISet reducedIntent = inputAttrs.clone();
			ISet visited = context.getFactory().createSet();
			Deque<Implication> justification2 = new ArrayDeque<>();
			justification2.addAll(justification);
			while (!justification2.isEmpty()) {
				Implication current = justification2.pop();
				if (visited.containsAll(current.getConclusion()))
					continue;
//	            visited.add(current.getConclusion().iterator().next());

				if (reducedIntent.containsAll(current.getPremise())
						&& reducedIntent.containsAll(current.getConclusion())) {
					reducedIntent.removeAll(current.getConclusion());
				}
			}
			return reducedIntent;
		}

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
				if (isValidImplication(premise, b)/* && !RuleUtilities.computeClosure(premise, base).contains(b)*/) {
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
