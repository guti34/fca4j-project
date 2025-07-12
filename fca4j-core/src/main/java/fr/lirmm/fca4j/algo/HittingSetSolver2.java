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
public class HittingSetSolver2 implements AbstractAlgo<Set<ISet>> {

	private IBinaryContext context = null;
	private Set<ISet> result = new HashSet<ISet>();
	private Set<ISet> family;
	private Chrono chrono = null; // eventually a chrono to store execution time
	
	/**
	 * Instantiates a hitting sets solver.
	 *
	 * @param binCtx the bin ctx
	 * @param chrono the chrono
	 */
	public HittingSetSolver2(IBinaryContext binCtx, Chrono chrono) {
		super();
		this.context = binCtx;
		this.chrono = chrono;
	}

	/**
	 * Instantiates a hitting sets solver.
	 *
	 * @param binCtx the bin ctx
	 */
	public HittingSetSolver2(Set<ISet> family,IBinaryContext binCtx) {
		this(binCtx, null);
		this.family=family;
	}
	@Override
	public void run() {
		if(family!=null)
			result=computeMinimalHittingSets(family,null);	
	}
	/**
	 * Compute the minimal hitting sets for a family of subsets.
	 * 
	 * @param family a list of subset (F elements)
	 * @return The set of all minimal hitting sets
	 */
	public Set<ISet> computeMinimalHittingSets(Set<ISet> family) {
		return computeMinimalHittingSets(family, context.getFactory().createSet());
	}
		/**
		 * Compute the minimal hitting sets for a family of subsets.
		 * 
		 * @param family a list of subset (F elements)
		 * @return The set of all minimal hitting sets
		 */
		protected Set<ISet> computeMinimalHittingSets(Set<ISet> family,ISet prefix) {
        // Si la famille est vide, on a trouvé un hitting set valide
        if (family.isEmpty()) {
            result.add(prefix);
            return result;
        }

        // Choisir l’ensemble le plus petit pour commencer (heuristique)
        ISet smallest = family.iterator().next();
        for (ISet s : family) {
            if (s.cardinality() < smallest.cardinality()) {
                smallest = s;
            }
        }

        // Pour chaque élément dans cet ensemble
        for (Iterator<Integer> it=smallest.iterator();it.hasNext();) {
        	int element=it.next();
            ISet newPrefix = prefix.clone();
            newPrefix.add(element);

            // Construire la famille réduite : retirer tous les ensembles déjà touchés par "element"
            Set<ISet> reducedFamily = new HashSet<>();
            for (ISet s : family) {
                if (!s.contains(element)) {
                    reducedFamily.add(s.clone());
                }
            }

            // Appel récursif
            Set<ISet> subResults = computeMinimalHittingSets(reducedFamily, newPrefix);
            for (ISet hs : subResults) {
                // Ajouter seulement les hitting sets minimaux (pas de supersets)
                if (!isSubsumed(hs, result)) {
                    result.add(hs);
                }
            }
        }
        return result;
	}
    // Évite d’ajouter des hitting sets non minimaux
    private static boolean isSubsumed(ISet candidate, Set<ISet> existing) {
        for (Iterator<ISet> it=existing.iterator();it.hasNext();) {
        	ISet existingSet=it.next();
            if (candidate.containsAll(existingSet)) {
                return true; // le candidat est un surensemble -> pas minimal
            }
        }
        return false;
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
