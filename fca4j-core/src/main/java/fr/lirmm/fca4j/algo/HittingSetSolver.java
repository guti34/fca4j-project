package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

/**
 * Compute the minimal hitting sets for a family of subsets.
 * 
 * @param family a list of subset (F elements)
 * @return The set of all minimal hitting sets
 */
public class HittingSetSolver implements AbstractAlgo<Set<ISet>> {

	private IBinaryContext context = null;
	private Set<ISet> result = null;
	private Set<ISet> family;
	private Chrono chrono = null; // eventually a chrono to store execution time
	
	/**
	 * Instantiates a hitting sets solver.
	 *
	 * @param binCtx the bin ctx
	 * @param chrono the chrono
	 */
	public HittingSetSolver(IBinaryContext binCtx, Chrono chrono) {
		super();
		this.context = binCtx;
		this.chrono = chrono;
	}

	/**
	 * Instantiates a hitting sets solver.
	 *
	 * @param binCtx the bin ctx
	 */
	public HittingSetSolver(Set<ISet> family,IBinaryContext binCtx) {
		this(binCtx, null);
		this.family=family;
	}
	private ISet createEmptySet() {
		return context.getFactory().createSet();
	}
	@Override
	public void run() {
		if(family!=null)
			result=computeMinimalHittingSets(family);	
	}
	/**
	 * Compute the minimal hitting sets for a family of subsets.
	 * 
	 * @param family a list of subset (F elements)
	 * @return The set of all minimal hitting sets
	 */
	public Set<ISet> computeMinimalHittingSets(Set<ISet> family) {
		// Si la famille est vide, l'ensemble vide est le seul hitting set.
		if (family.isEmpty()) {
			Set<ISet> result = new HashSet<>();
			result.add(createEmptySet());
			return result;
		}

		// Choisir arbitrairement un ensemble S dans la famille
		ISet S = family.iterator().next();
		Set<ISet> hittingSets = new HashSet<>();

		// Pour chaque attribut x de S, on construit récursivement des hitting sets
		for (Iterator<Integer> it = S.iterator(); it.hasNext();) {
			int x = it.next();
			// F_x = { T ∈ family : x ∉ T } (sets covered by x are removed)
			Set<ISet> Fx = new HashSet<>();
			for (ISet T : family) {
				if (!T.contains(x)) {
					Fx.add(T);
				}
			}
			// compute recursively hitting sets for Fx
			Set<ISet> Hx = computeMinimalHittingSets(Fx);
			// for each we add x
			for (ISet h : Hx) {
				ISet hPrime = h.clone();
				hPrime.add(x);
				hittingSets.add(hPrime);
			}
		}
//System.out.println("hitting sets="+hittingSets.size());	    
		// Pruning : remove from hittingSets all non minimal sets
		Set<ISet> minimals = filterMinimal(hittingSets);
//System.out.println("minimals="+minimals.size());	    
		return minimals;
	}

	private Set<ISet> filterMinimal(Set<ISet> sets) {
//		System.out.println("set cardinality=" + sets.size());
		Set<ISet> minimal = new HashSet<>();
		for (ISet s : sets) {
			boolean isMinimal = true;
			for (ISet t : sets) {
				if (t != s && s.containsAll(t)) {
					isMinimal = false;
					break;
				}
			}
			if (isMinimal)
				minimal.add(s);
		}
		return minimal;
	}

	@Override
	public Set<ISet> getResult() {
		return result;
	}

	/**
	 * Gets the description.
	 *
	 * @return the description
	 */
	@Override
	public String getDescription() {
		return "HittingSetSolver";
	}

}
