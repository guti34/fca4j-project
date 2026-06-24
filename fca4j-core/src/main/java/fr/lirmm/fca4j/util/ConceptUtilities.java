/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.util;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import fr.lirmm.fca4j.algo.ClosureDirect;
import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.IConceptOrder;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;

public class ConceptUtilities {

	public static Map<Integer, Double> computeStability(IConceptOrder conceptOrder) {
		HashMap<Integer, Integer> count = new HashMap<>();
		HashMap<Integer, PowerSetNumber> subsets = new HashMap<>();
		HashMap<Integer, Double> stability = new HashMap<>();
		ISet concepts = conceptOrder.getContext().getFactory().createSet();
		for (Iterator<Integer> it = conceptOrder.getBottomUpIterator(); it.hasNext();) {
			int concept = it.next();
			concepts.add(concept);
			count.put(concept, conceptOrder.getLowerCover(concept).cardinality());
			PowerSetNumber nbSubsets = PowerSetNumber
					.fromPowerOfTwo(conceptOrder.getConceptExtent(concept).cardinality());
			subsets.put(concept, nbSubsets);
		}
		while (!concepts.isEmpty()) {
			for (Iterator<Integer> it = concepts.iterator(); it.hasNext();) {
				int concept = it.next();
				if (count.get(concept) == 0) {
					concepts.remove(concept);

					PowerSetNumber denominator = PowerSetNumber
							.fromPowerOfTwo(conceptOrder.getConceptExtent(concept).cardinality());
					PowerSetNumber numerator = subsets.get(concept);
					double sta = numerator.divide(denominator);
					stability.put(concept, sta);

					for (Iterator<Integer> itParent = conceptOrder.getAllParents(concept).iterator(); itParent
							.hasNext();) {
						int parent = itParent.next();
						if (parent == concept)
							continue;
						try {
							subsets.get(parent).subtract(subsets.get(concept));
						} catch (Exception e) {
							System.out.println("subsets.get(parent)=" + subsets.get(parent));
							System.out.println("subsets.get(concept)=" + subsets.get(concept));
						}
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

	public static Map<Integer, Double> computeStability2(IConceptOrder conceptOrder) {
		HashMap<Integer, Integer> count = new HashMap<>();
		HashMap<Integer, BigInteger> subsets = new HashMap<>();
		HashMap<Integer, Double> stability = new HashMap<>();
		ISet concepts = conceptOrder.getContext().getFactory().createSet();
		for (Iterator<Integer> it = conceptOrder.getBottomUpIterator(); it.hasNext();) {
			int concept = it.next();
			concepts.add(concept);
			count.put(concept, conceptOrder.getLowerCover(concept).cardinality());
			BigInteger nbSubsets = BigInteger.ONE.shiftLeft(conceptOrder.getConceptExtent(concept).cardinality());
			subsets.put(concept, nbSubsets);
		}
		while (!concepts.isEmpty()) {
			for (Iterator<Integer> it = concepts.iterator(); it.hasNext();) {
				int concept = it.next();
				if (count.get(concept) == 0) {
					concepts.remove(concept);

					BigInteger denominator = BigInteger.ONE
							.shiftLeft(conceptOrder.getConceptExtent(concept).cardinality());
					BigDecimal sta = new BigDecimal(subsets.get(concept)).divide(new BigDecimal(denominator), 30,
							RoundingMode.HALF_UP);
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

	public static Map<Integer, String> buildDatalogDescriptor(IConceptOrder order, boolean deepSearch) {
		String[] attrNames=new String[order.getContext().getAttributeCount()];
		
		for(int attr=0;attr<order.getContext().getAttributeCount();attr++)
			attrNames[attr]=order.getContext().getAttributeName(attr);
		return buildDatalogDescriptor(order,attrNames,deepSearch);
	}
		public static Map<Integer, String> buildDatalogDescriptor(IConceptOrder order,  String[] attrNames,boolean deepSearch) {
		IBinaryContext ctx = order.getContext();
		Map<Integer, String> descriptors = new TreeMap<>();
		for (int concept : order.getConcepts()) {
			StringBuilder sb = new StringBuilder();
			sb.append("% Concept description of concept " + concept + " from " + ctx.getName());
			sb.append("\n");
			// assertion with main conjunction: reduced intent or intent if empty
			ISet factConjunction = order.getConceptReducedIntent(concept);
			if (factConjunction.isEmpty())
				factConjunction = order.getConceptIntent(concept);
			if (!factConjunction.isEmpty()) {
				sb.append("@Facts\n");
				sb.append(DlgpUtils.buildConjunction(factConjunction, attrNames));
				sb.append(".\n");
			}
			// constraints with intent of siblings
			ISet siblingsIntent = ctx.getFactory().createSet();
			ISet visited = ctx.getFactory().createSet();
			for (Iterator<Integer> it = order.getLowerCoverIterator(concept); it.hasNext();) {
				int parent = it.next();
				findImmediateSuccessorsIntent(order, order.getConceptIntent(concept), parent, siblingsIntent, visited,
						deepSearch);
			}
			for (Iterator<Integer> it = order.getUpperCoverIterator(concept); it.hasNext();) {
				int child = it.next();
				findImmediatePredecessorsIntent(order, order.getConceptIntent(concept), child, siblingsIntent, visited,
						deepSearch);
			}
			if (!siblingsIntent.isEmpty()) {
				sb.append("@Constraints\n");
				int count = 0;
				for (Iterator<Integer> it = siblingsIntent.iterator(); it.hasNext();) {
					ISet constraint = ctx.getFactory().createSet();
					constraint.add(it.next());
					sb.append("[constraint" + (++count) + "] ");
					sb.append("! :- ");
					sb.append(DlgpUtils.buildConjunction(constraint, attrNames));
					sb.append(".\n");
				}

			}
			// rules with intent of successors
			ISet successorsIntent = ctx.getFactory().createSet();
			visited = ctx.getFactory().createSet();
			findImmediateSuccessorsIntent(order, ctx.getFactory().createSet(), concept, successorsIntent, visited,
					true);
			successorsIntent.removeAll(factConjunction);
			if (!successorsIntent.isEmpty()) {
				sb.append("@Rules\n");
				int count = 0;
				for (Iterator<Integer> it = successorsIntent.iterator(); it.hasNext();) {
					ISet conclusion = ctx.getFactory().createSet();
					conclusion.add(it.next());
					sb.append("[rule" + (++count) + "] ");
					sb.append(DlgpUtils.buildConjunction(conclusion, attrNames));
					sb.append(":- ");
					sb.append(DlgpUtils.buildConjunction(factConjunction, attrNames));
					sb.append(".\n");
				}
			}
			descriptors.put(concept, sb.toString());
		}
		return descriptors;
	}

	private static void findImmediatePredecessorsIntent(IConceptOrder order, ISet cIntent, int current_concept,
			ISet immediatePredecessorsIntent, ISet visited, boolean deepSearch) {
		for (Iterator<Integer> it = order.getLowerCoverIterator(current_concept); it.hasNext();) {
			int pred = it.next();
			if (!visited.contains(pred)) {
				visited.add(pred);
				ISet reducedIntent = order.getConceptReducedIntent(pred);
				if (!reducedIntent.isEmpty() && !cIntent.intersects(order.getConceptReducedIntent(pred))) {
					immediatePredecessorsIntent.addAll(reducedIntent);
				} else if (deepSearch) {
					findImmediatePredecessorsIntent(order, cIntent, pred, immediatePredecessorsIntent, visited,
							deepSearch);
				}
			}
		}
	}

	private static void findImmediateSuccessorsIntent(IConceptOrder order, ISet cIntent, int current_concept,
			ISet immediateSuccessorsIntent, ISet visited, boolean deepSearch) {
		for (Iterator<Integer> it = order.getUpperCoverIterator(current_concept); it.hasNext();) {
			int succ = it.next();
			if (!visited.contains(succ)) {
				visited.add(succ);
				ISet reducedIntent = order.getConceptReducedIntent(succ);
				if (!reducedIntent.isEmpty() && !cIntent.intersects(order.getConceptReducedIntent(succ))) {
					immediateSuccessorsIntent.addAll(reducedIntent);
				} else if (deepSearch) {
					findImmediateSuccessorsIntent(order, cIntent, succ, immediateSuccessorsIntent, visited, deepSearch);
				}
			}
		}
	}

}
