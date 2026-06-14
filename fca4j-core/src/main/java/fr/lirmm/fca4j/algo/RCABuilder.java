/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;

import org.jgrapht.graph.DefaultDirectedGraph;

import fr.lirmm.fca4j.algo.ExploRCA.GhostFinder;
import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.ConceptOrderFamily;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.RCAFamily;
import fr.lirmm.fca4j.core.RCAFamily.FormalContext;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.util.ConceptOrderFinder;

public abstract class  RCABuilder implements AbstractAlgo<RCAManager>{
	/**
	 * list the conceptExtent created. This is needed to assign a unique identifier
	 * to concepts through the whole process
	 */
	final private HashMap<String, HashMap<ISet, Integer>> conceptExtentsList;
	final private HashMap<String, HashMap<ISet, Integer>> conceptExtentsNumStep;
	
	protected RCAManager manager;
	protected RCAFamily rcf;
	protected ArrayList<ConceptOrderFamily> conceptOrderFamilies;
	protected int step=-1;
	protected boolean newConcept;
	protected boolean clean;
	private int maxRegisteredConcept = -1;
	
	public RCABuilder(RCAFamily rcf,boolean clean) {
		conceptExtentsList = new HashMap<>();
		conceptExtentsNumStep = new HashMap<>();
		this.rcf=rcf;
		this.clean=clean;
		manager=new RCAManager(rcf);
	}
	protected abstract AbstractAlgo<ConceptOrder> createAlgo(IBinaryContext context, int numstep); 	
	@Override
	public void run() {
		if (step<0) {
			step=0;
		} else {
//			extendContexts(rcf);
		}
//		RCAFamily newFamily=computeFamily();
//		conceptOrderFamilies.add(newFamily);
		step++;
	}
	/**
	 * Generate concept orders.
	 *
	 * @return the my concept order family
	 */
	
	public ConceptOrderFamily computeFamily() {
		ConceptOrderFamily cof = new ConceptOrderFamily();
		newConcept = false;
		for (FormalContext formalContext : rcf.getFormalContexts()) {
			try {
				System.out.println(
						"computing ctx " + formalContext.getName() + " " + formalContext.getContext().getObjectCount()
								+ "x" + formalContext.getContext().getAttributeCount() + " .");
				AbstractAlgo<ConceptOrder> rca_algo = createAlgo(formalContext.getContext(), step);
				rca_algo.run();
				ConceptOrder conceptOrder = rca_algo.getResult();
				// rename (ids)
				conceptOrder = renamedConceptOrder(conceptOrder, formalContext);
				formalContext.setOrder(conceptOrder);
				cof.addConceptOrder(conceptOrder);
				System.out.println("ctx " + formalContext.getName() + " computed.");

				// remove registered extent key for missing concepts
				if (conceptExtentsList.get(formalContext.getName()).size() != conceptOrder.getConceptCount()) {
					int nbGhost=conceptExtentsList.get(formalContext.getName()).size()-conceptOrder.getConceptCount();
					System.out.println("GHOST CONCEPTS FOUND =" + nbGhost);
					if (clean) {
						ArrayList<ISet> missingExtentList = new ArrayList<>();
						for (ISet key : conceptExtentsList.get(formalContext.getName()).keySet()) {
							boolean found=false;
							for (int c : conceptOrder.getConcepts()) {
								if (conceptOrder.getConceptExtent(c).equals(key)) {
									found=true;
									break;
								}								
							}
							if(!found) missingExtentList.add(key);
						}
						for (ISet key : missingExtentList) {
							conceptExtentsList.get(formalContext.getName()).remove(key);
						}
					}
				}
			} catch (CloneNotSupportedException ex) {
				ex.printStackTrace();
			}
		}
		return cof;
	}
	private ConceptOrder renamedConceptOrder(ConceptOrder order, FormalContext formalContext)
			throws CloneNotSupportedException {
		HashMap<ISet, Integer> extents = conceptExtentsList.get(formalContext.getName());
		HashMap<ISet, Integer> numSteps = conceptExtentsNumStep.get(formalContext.getName());
		HashMap<Integer, Integer> renames = new HashMap<>();
		int numConcept = 0;
		for (int c : order.getConcepts()) {
			if (c > numConcept)
				numConcept = c;
		}
		if (maxRegisteredConcept > numConcept)
			numConcept = maxRegisteredConcept;
		for (Iterator<Integer> it = order.getBasicIterator(); it.hasNext();/* numConcept++ */) {
			int concept = it.next();
			ISet cek = order.getConceptExtent(concept);
			if (extents.containsKey(cek)) {
				int existingC = extents.get(cek);
				renames.put(concept, existingC);
			} else {
				// choose next id for new concept
				numConcept += 1;
				extents.put(cek, numConcept);
				numSteps.put(cek, step);
				newConcept = true;
				renames.put(concept, numConcept);
				maxRegisteredConcept = numConcept;
			}
		}
		// store concept rextent and rintent
		int[] concepts = new int[order.getConceptCount()];
		int ex = 0, in = 1;
		BitSet[] bitsets = new BitSet[order.getConceptCount() * 2];
		int[] edges = new int[order.getEdgeCount() * 2];
		int edgeCounter = 0;
		for (Iterator<Integer> it = order.getBasicIterator(); it.hasNext(); in += 2, ex += 2) {
			int oldNumConcept = it.next();
			int newNumConcept = renames.get(oldNumConcept);
			concepts[ex / 2] = newNumConcept;
			ISet rextent = order.getConceptReducedExtent(oldNumConcept);
			bitsets[ex] = rextent.toBitSet();
			ISet rintent = order.getConceptReducedIntent(oldNumConcept);
			bitsets[in] = rintent.toBitSet();
			// edges
			for (Iterator<Integer> itUpper = order.getUpperCoverIterator(oldNumConcept); itUpper.hasNext();) {
				int newSource = renames.get(oldNumConcept);
				edges[edgeCounter] = newSource;
				int upperC = itUpper.next();
				int newTarget = renames.get(upperC);
				edges[edgeCounter + 1] = newTarget;
				edgeCounter += 2;
				assert newTarget != newSource;
			}
		}
		try {
			ConceptOrder newConceptOrder = new ConceptOrder(order.getId(), order.getContext(), order.getAlgoName());
			newConceptOrder.populate(concepts, edges, bitsets);
			newConceptOrder.buildExtentIntent();
			return newConceptOrder;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	public ConceptOrderFinder createConceptFinder() {
		return new GhostFinder();
	}
	public class GhostFinder implements ConceptOrderFinder {
	public ConceptOrder findConceptOrder(String formalContext,int numConcept) {
		for(int step=conceptOrderFamilies.size()-1;step>=0;step--)
		{
			ConceptOrderFamily orderFamily=conceptOrderFamilies.get(step);
			for(ConceptOrder order:orderFamily.getConceptOrders())
				if(order.getContext().getName().equals(formalContext) && order.getConcepts().contains(numConcept))
					return order;
		}
		return null;
	}
	}	
	@Override
	public RCAManager getResult() {
		// TODO Auto-generated method stub
		return manager;
	}

	@Override
	public String getDescription() {
		return "RCA";
	}
}
