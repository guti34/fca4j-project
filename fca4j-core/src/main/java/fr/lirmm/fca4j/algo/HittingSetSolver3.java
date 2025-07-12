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
public class HittingSetSolver3 implements AbstractAlgo<Set<ISet>> {

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
	public HittingSetSolver3(IBinaryContext binCtx, Chrono chrono) {
		super();
		this.context = binCtx;
		this.chrono = chrono;
	}

	/**
	 * Instantiates a hitting sets solver.
	 *
	 * @param binCtx the bin ctx
	 */
	public HittingSetSolver3(Set<ISet> family,IBinaryContext binCtx) {
		this(binCtx, null);
		this.family=family;
	}
	@Override
	public void run() {
		if(family!=null) {
			Set<ISet> myResult=new HashSet<ISet>();
			computeMinimalHittingSets(family,myResult);	
			result=myResult;
		}
	}
	/**
	 * Compute the minimal hitting sets for a family of subsets.
	 * 
	 * @param family a list of subset (F elements)
	 * @return The set of all minimal hitting sets
	 */
	public void computeMinimalHittingSets(Set<ISet> currentFamily,Set<ISet> myResult) {
		computeMinimalHittingSets(context.getFactory().createSet(),currentFamily, myResult);
	}
		/**
		 * Compute the minimal hitting sets for a family of subsets.
		 * 
		 * @param family a list of subset (F elements)
		 */
		protected void computeMinimalHittingSets(ISet current,Set<ISet> myFamily,Set<ISet> myResult) {
			System.out.println("result="+myResult+" current="+current+" myFamily="+myFamily);
        // Si la famille est vide, on a trouvé un hitting set valide
        if (myFamily.isEmpty()) {
        	myResult.add(current);
            return;
        }

        // Choisir l’ensemble le plus petit pour commencer (heuristique)
        ISet smallest = null;
        for (ISet s : myFamily) {
            if (smallest==null || s.cardinality() < smallest.cardinality()) {
                smallest = s;
            }
        }

        // Pour chaque élément dans cet ensemble
        for (Iterator<Integer> it=smallest.iterator();it.hasNext();) {
        	int element=it.next();
            ISet newPrefix = current.clone();
            newPrefix.add(element);

            // Construire la famille réduite : retirer tous les ensembles déjà touchés par "element"
            Set<ISet> reducedFamily = new HashSet<>();
            for (ISet s : myFamily) {
                if (!s.contains(element)) {
                    reducedFamily.add(s.clone());
                }
            }

            // Appel récursif
            boolean isMinimal=true;
            for(ISet set:myResult) {
            	System.out.println("set="+set);
            	System.out.println("newPrefix="+newPrefix);
            	if(newPrefix.containsAll(set)) {
            		isMinimal=false;
            		break;
            	}
            }
            if(isMinimal) {
            	System.out.println("element="+element);
            	computeMinimalHittingSets(newPrefix,reducedFamily,myResult);
            }
         }
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
