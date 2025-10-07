package fr.lirmm.fca4j.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import fr.lirmm.fca4j.algo.ClosureDirect;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.IConceptOrder;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;

public class ConceptUtilities {

	public static Map<Integer, Double> computeStability(IConceptOrder conceptOrder) {
		HashMap<Integer, Integer> count = new HashMap<>();
		HashMap<Integer, BigInteger> subsets = new HashMap<>();
		HashMap<Integer, Double> stability = new HashMap<>();
		ISet concepts = conceptOrder.getContext().getFactory().createSet();
		for (Iterator<Integer> it = conceptOrder.getBottomUpIterator(); it.hasNext();) {
			int concept = it.next();
			concepts.add(concept);
			count.put(concept, conceptOrder.getLowerCover(concept).cardinality());
			BigInteger nbSubsets=BigInteger.ONE.shiftLeft(conceptOrder.getConceptExtent(concept).cardinality());  
			subsets.put(concept, nbSubsets);
		}
		while (!concepts.isEmpty()) {
			for (Iterator<Integer> it = concepts.iterator(); it.hasNext();) {
				int concept = it.next();
				if (count.get(concept) == 0) {
					concepts.remove(concept);
					
					 BigInteger denominator=BigInteger.ONE.shiftLeft(conceptOrder.getConceptExtent(concept).cardinality());  
					 BigDecimal sta=new BigDecimal( subsets.get(concept)).divide(new BigDecimal(denominator), 30, RoundingMode.HALF_UP);
					 stability.put(concept, sta.doubleValue());
					
					for (Iterator<Integer> itParent = conceptOrder.getAllParents(concept).iterator(); itParent
							.hasNext();) {
						int parent = itParent.next();
						if (parent == concept)
							continue;
						subsets.put(parent, subsets.get(parent).subtract(subsets.get(concept)));
						if (conceptOrder.getUpperCover(concept).contains(parent)) {
							count.put(parent, count.get(parent) - 1);
						}
					}
					break;
				}
			}
		}
		return stability;
	}

	public static double computeNaiveStability(ISet extent, ISet intent, IBinaryContext context) {
		ClosureDirect closureEngine = new ClosureDirect(context);
		List<ISet> subExtents = powerSet(extent.iterator(), context.getFactory());
		double numerator = 0;
		for (ISet subset : subExtents) {
			if (closureEngine.computeIntent(subset).equals(intent)) {
				numerator++;
			}
		}
		int denominator = 1 << extent.cardinality();
		return (double) numerator / (double) denominator;
	}

	/**
	 * Construit le powerset à partir d'un Iterator<Integer>.
	 *
	 * @param it      itérateur sur les éléments
	 * @param factory factory pour créer les ISet
	 * @return la liste de tous les sous-ensembles possibles
	 */
	public static List<ISet> powerSet(Iterator<Integer> it, ISetFactory factory) {
		// Collecter les éléments de l'itérateur dans une liste indexable
		List<Integer> elements = new ArrayList<>();
		while (it.hasNext()) {
			elements.add(it.next());
		}

		int n = elements.size();
		int total = 1 << n; // 2^n sous-ensembles
		List<ISet> result = new ArrayList<>(total);

		// Génération des sous-ensembles
		for (int mask = 0; mask < total; mask++) {
			ISet subset = factory.createSet();
			for (int i = 0; i < n; i++) {
				if (((mask >> i) & 1) == 1) {
					subset.add(elements.get(i));
				}
			}
			result.add(subset);
		}

		return result;
	}

}
