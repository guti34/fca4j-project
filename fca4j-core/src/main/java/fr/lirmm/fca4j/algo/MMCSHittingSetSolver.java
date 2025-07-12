package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.Collections;
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
public class MMCSHittingSetSolver implements AbstractAlgo<List<ISet>> {

	private IBinaryContext context = null;
	private List<ISet> result = new ArrayList<ISet>();
	private List<ISet> family;
	private Chrono chrono = null; // eventually a chrono to store execution time
	
	/**
	 * Instantiates a hitting sets solver.
	 *
	 * @param binCtx the bin ctx
	 * @param chrono the chrono
	 */
	public MMCSHittingSetSolver(IBinaryContext binCtx, Chrono chrono) {
		super();
		this.context = binCtx;
		this.chrono = chrono;
	}

	/**
	 * Instantiates a hitting sets solver.
	 *
	 * @param binCtx the bin ctx
	 */
	public MMCSHittingSetSolver(List<ISet> family,IBinaryContext binCtx) {
		this(binCtx, null);
		this.family=family;
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
	public List<ISet> computeMinimalHittingSets(List<ISet> family) {
        ISet universe = context.getFactory().createSet();
        for (ISet s : family) universe.addAll(s);

        mmcs(family, context.getFactory().createSet(), universe);
        return result;
	}
	   private void mmcs(List<ISet> family, ISet current, ISet candidates) {
	        // Vérifier si current est un hitting set
	        boolean isHitting = true;
	        Set<ISet> uncovered = new HashSet<>();
	        for (ISet s : family) {
	            if (s.newIntersect(current).cardinality()==0) // disjoint 
	            {
	                uncovered.add(s);
	                isHitting = false;
	            }
	        }

	        if (isHitting) {
	            // Vérifier la minimalité
	            for (ISet r : result) {
	                if (current.containsAll(r) ) return; // pas minimal
	            }
	            result.add(current.clone());
	            return;
	        }

	        // Choisir un ensemble non couvert (heuristique : plus petit)
	        ISet nextSet = uncovered.iterator().next();
	        for(Iterator<Integer>it=nextSet.iterator();it.hasNext();) {
	        	int e=it.next();
	            if (!candidates.contains(e)) continue;

	            ISet newCurrent = current.clone();
	            newCurrent.add(e);

	            ISet newCandidates = candidates.clone();
	            newCandidates.remove(e); // on ne réessaie pas e

	            mmcs(family, newCurrent, newCandidates);
	        }
	    }
	
	@Override
	public List<ISet> getResult() {
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
