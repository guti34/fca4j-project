package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
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

public class DBaseCalculator implements AbstractAlgo<List<Implication>> {

	protected int minSupport = 0;
	/** The matrix. */
	protected IBinaryContext context; // ressource de depart

	protected Chrono chrono = null; // eventually a chrono to store execution
									// time
	ClosureDirect closureEngine; 
	
	/** The implications. */
	protected List<Implication> implications;

	public DBaseCalculator(IBinaryContext context) {
		this.context = context;
		this.implications = new ArrayList<>();
		closureEngine = new ClosureDirect(context);
	}

	@Override
	public void run() {
		implications = computeDBasis();
	}

	// main method to compute D-base
	/**
	 * Calcule la D-base avec le support des implications à partir d'un
	 * IBinaryContext.
	 *
	 * @param context Le contexte binaire représentant la matrice objets x
	 *                attributs.
	 * @return La liste des implications minimales avec leur support.
	 */
	public List<Implication> computeDBase2() {
		int numAttributes = context.getAttributeCount();
		HashSet<Implication> dBase = new HashSet<>();

		// Step 1 : Generate binary dependencies
		for (int attrConclusion = 0; attrConclusion < numAttributes; attrConclusion++) {
			ISet conclusionObjects = context.getExtent(attrConclusion); // Objets with attribute attrConclusion
			for (int attrPremise = 0; attrPremise < numAttributes; attrPremise++) {
				if (attrPremise != attrConclusion) {
					ISet support = context.getExtent(attrPremise);
					if (support.cardinality() > 0 && conclusionObjects.containsAll(support)) {
						ISet premiseAttributes = context.getFactory().createSet();
						premiseAttributes.add(attrPremise);
						ISet conclusionAttributes = context.getFactory().createSet();
						conclusionAttributes.add(attrConclusion);
						Implication dependency = new Implication(premiseAttributes, conclusionAttributes, support);
						dBase.add(dependency);
					}

				}
			}
		}

		// Étape 2 : Réduire les implications pour obtenir une D-base minimale
		List<Implication> minimalDBase = new ArrayList<>();
		Comparator<Implication> comparator = new Comparator<Implication>() {
			@Override
			public int compare(Implication a1, Implication a2) {
				return Integer.compare(a1.getPremise().cardinality(), a2.getPremise().cardinality());
			}
		};
		List<Implication> sortedList = new ArrayList<>(dBase);
		// Sort implication by cardinality
		Collections.sort(sortedList, new Comparator<Implication>() {
			@Override
			public int compare(Implication a1, Implication a2) {
				return Integer.compare(a1.getPremise().cardinality(), a2.getPremise().cardinality());
			}
		});

		for (Implication dep : sortedList) {
			ISet closure = computeClosure(dep.getPremise(), minimalDBase);
			ISet minimalConclusion = dep.getConclusion().newDifference(closure);

			if (!isDerivable(minimalConclusion, dep.getPremise(), minimalDBase)) {
//				if (!isDerivable(minimalConclusion, closure, minimalDBase)) {
				minimalDBase.add(new Implication(dep.getPremise(), minimalConclusion, dep.getSupport()));
			}
		}
		return minimalDBase;
	}

	public List<Implication> computeDBase() {
		PriorityQueue<Implication> results = new PriorityQueue<>(new Comparator<Implication>() {
			@Override
			public int compare(Implication a1, Implication a2) {
				return Integer.compare(a1.getPremise().cardinality(), a2.getPremise().cardinality());
			}
		}); // Store implications by premise cardinality
		int nbAttributes = context.getAttributeCount();
// calcul des irreductibles
		ISet irreducible = FastReduction.computeIrreductibleIntent(context);
		ISet notIrreducible = context.getFactory().createSet(nbAttributes);
		notIrreducible.fill(nbAttributes);
		notIrreducible.removeAll(irreducible);
		List<Integer> listAttr = notIrreducible.toList();
		listAttr.addAll(irreducible.toList());
		for (int attrConclusion = 0; attrConclusion < nbAttributes; attrConclusion++) {
//			for (int attrConclusion:listAttr) {
			Queue<State> queue = new LinkedList<>();
			ISet conclusionAttributes = context.getFactory().createSet();
			conclusionAttributes.add(attrConclusion);
			ISet conclusionObjects = context.getExtent(attrConclusion);
			// Initialize the stack with an empty set
			ISet initialSet = context.getFactory().createSet(); // Empty set
			queue.add(new State(initialSet, 0));

			// Parcourir les états pour l'élément en cours
			while (!queue.isEmpty()) {
				State currentState = queue.poll(); // Extraire un état
				ISet currentSubset = currentState.subset;
				int currentIndex = currentState.index;

				ISet support = getExtent(currentSubset);
				if (support.cardinality() >= minSupport) {
					if (conclusionObjects.containsAll(support)) {
						// create implication

						Implication dependency = new Implication(currentSubset.clone(), conclusionAttributes, support);
//			System.out.println(dependency);
						results.add(dependency);
					} else {
						// Generate new states
						for (int i = currentIndex; i < nbAttributes; i++) {
							if (i != attrConclusion) {
								// Ajouter un nouvel élément pour créer un nouveau sous-ensemble
								ISet nextSubset = currentSubset.clone();
								nextSubset.add(i);
								queue.add(new State(nextSubset, i + 1)); // a new state to the stack
							}
						}
					}
				}
			}
		}
		System.out.println("base size=" + results.size());
		// Étape 2 : Réduire les implications pour obtenir une D-base minimale
		List<Implication> minimalDBase = new ArrayList<>();
		for (Implication dep : results) {
			ISet closure = computeClosure(dep.getPremise(), minimalDBase);
			ISet minimalConclusion = dep.getConclusion().newDifference(closure);

			if (!isDerivable(minimalConclusion, dep.getPremise(), minimalDBase)) {
				minimalDBase.add(new Implication(dep.getPremise(), minimalConclusion, dep.getSupport()));
			}
		}
		System.out.println("D-base size=" + minimalDBase.size());
		return minimalDBase;

	}

	/**
	 * Calcule la fermeture d'un ensemble d'attributs sous un ensemble
	 * d'implications.
	 *
	 * @param attributes   Ensemble d'attributs de départ.
	 * @param dependencies Liste des implications à appliquer.
	 * @return La fermeture de l'ensemble d'attributs.
	 */
	private ISet computeClosure(ISet attributes, List<Implication> dependencies) {
		ISet closure = attributes.clone();
		boolean changed;

		do {
			changed = false;
			for (Implication dep : dependencies) {
				if (closure.containsAll(dep.getPremise())) {
					if (!closure.containsAll(dep.getConclusion())) {
						closure.addAll(dep.getConclusion());
						changed = true;
					}
				}
			}
		} while (changed);

		return closure;
	}

	/**
	 * Verify if an attribute set can be deduced from a base attribute set and a set
	 * of implications d'implications.
	 *
	 * @param attributes   Attribute set to verify
	 * @param base         Initial attribute set (prémise).
	 * @param dependencies A list of implications.
	 * @return true if the set can be deduced.
	 */
	private boolean isDerivable(ISet attributes, ISet base, List<Implication> dependencies) {
		ISet closure = computeClosure(base, dependencies);
		return closure.containsAll(attributes);
	}

	private ISet getExtent(ISet intent) {
		ISet result = context.getFactory().createSet();
		result.fill(context.getObjectCount());
		for (Iterator<Integer> it = intent.iterator(); it.hasNext();) {
			int numattr = it.next();
			result.retainAll(context.getExtent(numattr));
		}
		return result;
	}

	@Override
	public List<Implication> getResult() {
		return implications;
	}

	@Override
	public String getDescription() {
		return "DBase";
	}

	// Class to represent state in the stack
	private static class State {
		ISet subset; // current subset
		int index; // Next attribute to consider

		State(ISet subset, int index) {
			this.subset = subset;
			this.index = index;
		}
	}

	public List<Implication> computeDBasis() {
		List<Implication> dBasis = new ArrayList<>();

	    List<ISet> irreducibleIntents = FastReduction.computeIrreductibleIntent4notClarifiedContext(context);
		LinCbO linCbO = new LinCbO(context, chrono, new ClosureDirect(context), false);
		linCbO.run();
		List<Implication> result = linCbO.getResult();
		HashMap<Integer, List<ISet>> premises = new HashMap<>();
		for (Implication impl : result) {
			for (Iterator<Integer> it = impl.getConclusion().iterator(); it.hasNext();) {
				int attr = it.next();
				List<ISet> family = premises.get(attr);
				if (family == null) {
					family = new ArrayList<ISet>();
					premises.put(attr, family);
				}
				family.add(impl.getPremise());
			}
		}
		System.out.println("lincbo done");
		for(int attr:premises.keySet()) {
//			System.out.println("attr="+attr+" intent="+premises.get(attr));
		}
		List<ISet> intents=new ArrayList<>();
		for(int attr:premises.keySet()) {
			for(ISet set:premises.get(attr)) {
				intents.add(set);
//					System.out.println("premise of "+attr+"= "+set);
			}
		}
		// 1.2. Pour chaque attribut m du contexte, construire la famille F_m
		// et en extraire les minimal hitting sets
		for (int m = 0; m < context.getAttributeCount(); m++) {
			System.out.println("attribute="+m);

			// Build F_m = { I ∈ irreducibleIntents : m ∉ I }
			List<ISet> familyFm = new ArrayList<>();
//			for (ISet intent : premises.get(m)) {
				for (ISet intent : premises.get(m)) {
				if (!intent.contains(m)) {
					familyFm.add(intent);
				}
			}
			// sort family by cardinality
			familyFm.sort(Comparator.comparingInt(ISet::cardinality));
			intents.sort(Comparator.comparingInt(ISet::cardinality));
			// 1.3. Calculer les minimal hitting sets pour familyFm
//			Set<ISet> minimalPremises = computeMinimalHittingSets(intents);
			Set<ISet> hittingSets = computeMinimalHittingSets(familyFm);
			System.out.println("attribute="+m+"hittingSets done");
			
//			System.out.println("minimal hitting sets for "+m);
//			for(ISet set:hittingSets)
//				System.out.println("    "+set);
			Set<ISet> minimalPremises=getIntentCombinations(hittingSets);
			// 1.4. For each minimal set X, verify that the closure of X contains m
			for (ISet X : minimalPremises) {
				// compute the closure of X
				ISet closure = context.getFactory().createSet();
				ISet support = closureEngine.closure(closure, X, null, null);
				if (closure.contains(m)) {
					// Create implication X -> m and add to the D-basis
					ISet conclusion = context.getFactory().createSet();
					conclusion.add(m);
					ISet premise=X.newDifference(conclusion);
					premise=minimizePremise(premise, m);
					Implication newImplication=new Implication(premise, conclusion, support);
					dBasis.add(newImplication);
//					System.out.println(newImplication);
//					System.out.println("conclusion="+conclusion);
				}
				else System.out.println("oups");
			}
		}
		return dBasis;
	}
	public ISet minimizePremise(ISet premise, int attrConclusion) {
	    ISet minimalPremise = premise.clone();
	    boolean changed;
	    
	    do {
	        changed = false;
	        // On travaille sur une copie pour itérer sans problème
	        for (int attr : minimalPremise.clone().toList()) {
	            // On essaie de retirer attr
	            ISet candidate = minimalPremise.clone();
	            candidate.remove(attr);
	            // Si la clôture de candidate contient toujours la conclusion, on peut éliminer attr
				ISet closure1 = context.getFactory().createSet();
				ISet support1=closureEngine.closure(closure1, candidate, null, null);
				ISet closure2 = context.getFactory().createSet();
				ISet support2=closureEngine.closure(closure2, minimalPremise, null, null);
	            if (closure1.contains(attrConclusion)&&support1.cardinality()==support2.cardinality()) {
	                minimalPremise = candidate;
	                changed = true;
	                break; // Redémarrer la boucle avec la nouvelle prémisse
	            }
	        }
	    } while (changed);
	    
	    return minimalPremise;
	}
    private Set<ISet> getIntentCombinations(Set<ISet> hittingSets) {
        Set<ISet> result = new HashSet<>();
        result.add(context.getFactory().createSet());
        // Cas particulier : si la liste est vide, retourner un ensemble contenant l'ensemble vide.
        if (hittingSets == null || hittingSets.isEmpty()) {
             return result;
        }
          
        // Pour chaque hitting set, on construit les nouvelles combinaisons en y ajoutant un attribut.
        for (ISet hs : hittingSets) {
            Set<ISet> newResult = new HashSet<>();
            for (ISet combination : result) {
            	for(Iterator<Integer> it=hs.iterator();it.hasNext();)
            	{
            	int attr=it.next();	
                     // Créer une nouvelle combinaison en ajoutant 'attr'
                    ISet newCombination = combination.clone();
                    newCombination.add(attr);
                    newResult.add(newCombination);
                }
            }
            // La nouvelle liste de combinaisons devient le résultat courant
            result = newResult;
        }
        return result;
    }
	/**
	 * Compute the minimal hitting sets for a family of subsets.
	 * 
	 * @param family a list of subset (F elements)
	 * @return The set of all minimal hitting sets
	 */
	public Set<ISet> computeMinimalHittingSets(List<ISet> family) {
		// Si la famille est vide, l'ensemble vide est le seul hitting set.
		if (family.isEmpty()) {
			Set<ISet> result = new HashSet<>();
			result.add(context.getFactory().createSet());
			return result;
		}

		// Choisir arbitrairement un ensemble S dans la famille
		ISet S = family.get(0);
		Set<ISet> hittingSets = new HashSet<>();

		// Pour chaque attribut x de S, on construit récursivement des hitting sets
		for (Iterator<Integer> it = S.iterator(); it.hasNext();) {
			int x = it.next();
			// F_x = { T ∈ family : x ∉ T } (sets covered by x are removed)
			List<ISet> Fx = new ArrayList<>();
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

	/**
	 * Retire de l'ensemble donné tous les ensembles qui sont des sur-ensembles d'un
	 * autre.
	 * 
	 * @param sets Ensemble d'ensembles d'attributs
	 * @return Sous-ensemble minimal (pour inclusion)
	 */
	public Set<ISet> minimalElements(Set<ISet> sets) {
		Set<ISet> minimal = new HashSet<>(sets);
		for (ISet s1 : sets) {
			for (ISet s2 : sets) {
				if (s1 != s2 && s2.containsAll(s1)) {
					minimal.remove(s2);
				}
			}
		}
		return minimal;
	}

}
