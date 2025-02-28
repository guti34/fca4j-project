/*
BSD 3-Clause License

Copyright (c) 2022 LIRMM
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

   * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
   * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
   * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Stack;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.ConceptOrderFamily;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.RCAFamily;
import fr.lirmm.fca4j.core.RCAFamily.FormalContext;
import fr.lirmm.fca4j.core.RCAFamily.RelationalContext;
import fr.lirmm.fca4j.core.operator.AbstractScalingOperator;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.util.ConceptOrderFinder;

/**
 * The Class ExploRCA.
 */
public abstract class ExploRCA {
	/**
	 * list the conceptExtent created. This is needed to assign a unique identifier
	 * to concepts through the whole process
	 */
	final private HashMap<String, HashMap<ConceptExtentKey, Integer>> conceptExtentsList;
	final private HashMap<String, HashMap<ConceptExtentKey, Integer>> conceptExtentsNumStep;
//    private HashMap<String,Integer> conceptIds;
	private int numstep;
	private int maxRegisteredConcept = -1;
	private boolean init = true;
	/**
	 * indicates if the process is ending
	 */
	private boolean end = false;
	private boolean clean=false;
	/**
	 * indicates if at least one new concept has been created during the last step
	 */
	private boolean newConcept = false;

	/**
	 * current relational context family
	 */
	protected RCAFamily relationalContextFamily;
	/**
	 * current concept order family
	 */
	protected ArrayList<ConceptOrderFamily> conceptOrderFamilies;
	/**
	 */
//	private StringBuffer trace;

	/**
	 * Instantiates a new explo RCA.
	 *
	 * @param rcf the rcf
	 */
	public ExploRCA(RCAFamily rcf, boolean clean) {
		this.clean=clean;
//		trace = new StringBuffer();
		this.relationalContextFamily = rcf;
		this.conceptOrderFamilies = new ArrayList<>();
		numstep = 0;
		conceptExtentsList = new HashMap<>();
		conceptExtentsNumStep = new HashMap<>();
//        conceptIds=new HashMap<>();
		for (FormalContext fc : rcf.getFormalContexts()) {
			conceptExtentsList.put(fc.getName(), new HashMap<ConceptExtentKey, Integer>());
			conceptExtentsNumStep.put(fc.getName(), new HashMap<ConceptExtentKey, Integer>());
//            conceptIds.put(fc.getName(), 0);
		}
	}

	/**
	 * generate the concept posets for current step and create the next step
	 * configuration (load it in case of automatic process).
	 */
	public void computeStep() {
		if (init) {
			init = false;
//            MyConcept.resetIdCounter();
		} else {
			extendContexts(relationalContextFamily);
		}
		conceptOrderFamilies.add(generateConceptOrders());
		numstep++;
	}

	/**
	 * Returns true if a stopCondition has been reached.
	 *
	 * @return true, if successful
	 */
	public boolean stopCondition() {
		return !newConcept;
	}

	private void extendContexts(RCAFamily family) {
		long time_before_extending = System.currentTimeMillis() / 1000;
		for (RelationalContext rc : family.getRelationalContexts()) {
			FormalContext fc = family.getTargetOf(rc);
			ConceptOrder targetOrder = fc.getOrder();
			AbstractScalingOperator op = rc.getOperator();
			HashMap<ConceptExtentKey, Integer> mapNumSteps = conceptExtentsNumStep.get(fc.getName());
			for (Iterator<Integer> it = targetOrder.getBasicIterator(); it.hasNext();) {
				int aConcept = it.next();
				ISet aConceptExtent = targetOrder.getConceptExtent(aConcept);
				Integer creationConceptStep = mapNumSteps.get(new ConceptExtentKey(aConceptExtent));
				if (creationConceptStep == numstep - 1) {
					ISet extent = fc.getContext().getFactory().createSet();
					for (int i = 0; i < family.getSourceOf(rc).getContext().getObjectCount(); i++) {
						if (op.scale(i, aConceptExtent, rc.getContext())) {
							extent.add(i);
						}
					}
//                    if (!extent.isEmpty()) 
					family.addAttribute(rc, aConcept, extent);
//                    else System.out.println("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");

				}
			}
		}

		if (clean)
			family.cleanUnusedRelationalAttributes();

		for (FormalContext ctx : relationalContextFamily.getFormalContexts()) {
			System.out.println("ctx " + ctx.getName() + " : " + ctx.getContext().getObjectCount() + " entities, "
					+ ctx.getContext().getAttributeCount() + " attributes");
		}
		long time_after_extending = System.currentTimeMillis() / 1000;
		System.out.println("contexts extended (" + (time_after_extending - time_before_extending) + "s )");
	}

	/**
	 * Generate concept orders.
	 *
	 * @return the my concept order family
	 */
	public ConceptOrderFamily generateConceptOrders() {
		ConceptOrderFamily cof = new ConceptOrderFamily();
		newConcept = false;
		for (FormalContext formalContext : relationalContextFamily.getFormalContexts()) {
			try {
				System.out.println(
						"computing ctx " + formalContext.getName() + " " + formalContext.getContext().getObjectCount()
								+ "x" + formalContext.getContext().getAttributeCount() + " .");
				AbstractAlgo<ConceptOrder> rca_algo = createAlgo(formalContext.getContext(), numstep);
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
						ArrayList<ConceptExtentKey> missingExtentList = new ArrayList<>();
						for (ConceptExtentKey key : conceptExtentsList.get(formalContext.getName()).keySet()) {
							boolean found=false;
							for (int c : conceptOrder.getConcepts()) {
								if (conceptOrder.getConceptExtent(c).equals(key.extent)) {
									found=true;
									break;
								}								
							}
							if(!found) missingExtentList.add(key);
						}
						for (ConceptExtentKey key : missingExtentList) {
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

	/**
	 * Creates the algo.
	 *
	 * @param context the context
	 * @param numstep the numstep
	 * @return the abstract algo
	 */
	protected abstract AbstractAlgo<ConceptOrder> createAlgo(IBinaryContext context, int numstep);

	private boolean controlCO(ConceptOrder conceptOrder) {
		HashSet<ConceptExtentKey> extents = new HashSet<>();
		for (Iterator<Integer> itc = conceptOrder.getBasicIterator(); itc.hasNext();) {
			int concept = itc.next();
			ConceptExtentKey cek = new ConceptExtentKey(conceptOrder.getConceptExtent(concept));
			if (extents.contains(cek))
				System.out.println("caramba !");
			else
				extents.add(cek);

		}
		System.out.println("total:" + extents.size() + "/" + conceptOrder.getConceptCount());
		return conceptOrder.getConceptCount() == extents.size();
	}

	/**
	 * Checks if is end.
	 *
	 * @return true, if is end
	 */
	public boolean isEnd() {
		return end;
	}

	/**
	 * Sets the end.
	 *
	 * @param end the new end
	 */
	public void setEnd(boolean end) {
		this.end = end;
	}


	/**
	 * Saves concept orders at the end of a given step.
	 *
	 * @param outputFolder the output folder
	 */
	/*
	 * public void saveConceptOrders(String outputFolder, int step) { int i = 0; try
	 * (FileWriter fw0 = new FileWriter(outputFolder + "/step" + step + ".dot")) {
	 * 
	 * GenerateSVGDot genSVGdot = new GenerateSVGDot(fw0,
	 * conceptOrderFamilies.peek()); genSVGdot.generateCode();
	 * 
	 * } catch (FileNotFoundException e) { e.printStackTrace(); } catch (IOException
	 * e) { e.printStackTrace(); }
	 * 
	 * for (ConceptOrder cPoset : conceptOrderFamilies.peek().getConceptOrders()) {
	 * 
	 * try (FileWriter fw = new FileWriter(outputFolder + "/step" + step + "-" + i +
	 * ".dot")) { GraphVizDotWriter dotWriter=new GraphVizDotWriter(new
	 * BufferedWriter(fw), cPoset, cPoset.getContext(),DisplayFormat.REDUCED,
	 * true,true, "BT"); dotWriter.write(); } catch (FileNotFoundException e) {
	 * e.printStackTrace(); } catch (IOException e) { e.printStackTrace(); } i++; }
	 * }
	 */
	/**
	 * Gets the rcf.
	 *
	 * @return the rcf
	 */
	public RCAFamily getRCF() {
		return relationalContextFamily;
	}

	/**
	 * Gets the num step.
	 *
	 * @return the num step
	 */
	public int getNumStep() {
		return numstep;
	}

	/**
	 * Gets the concept order family.
	 *
	 * @return the concept order family
	 */
	public ConceptOrderFamily getConceptOrderFamily() {
		if(conceptOrderFamilies.isEmpty()) return null;
		else return conceptOrderFamilies.get(conceptOrderFamilies.size()-1);
	}

	/*
	 * private int generateNewConceptId(FormalContext formalContext){ int
	 * newId=conceptIds.get(formalContext.getName())+1;
	 * conceptIds.put(formalContext.getName(), newId); return newId; }
	 */
	private ConceptOrder renamedConceptOrder(ConceptOrder order, FormalContext formalContext)
			throws CloneNotSupportedException {
		HashMap<ConceptExtentKey, Integer> extents = conceptExtentsList.get(formalContext.getName());
		HashMap<ConceptExtentKey, Integer> numSteps = conceptExtentsNumStep.get(formalContext.getName());
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
			ConceptExtentKey cek = new ConceptExtentKey(order.getConceptExtent(concept));
//            System.out.println("cek="+cek.hashCode()+ " extent size="+cek.extent.cardinality());
//            System.out.println("extent="+order.getConceptExtent(concept)+cek.hashCode());
//            ConceptExtentKey cek=new ConceptExtentKey(computeExtent(order,concept));
			if (extents.containsKey(cek)) {
				int existingC = extents.get(cek);
				renames.put(concept, existingC);
			} else {
				// choose next id for new concept
				numConcept += 1;
//                int newConceptId=generateNewConceptId(formalContext);
				extents.put(cek, numConcept);
				numSteps.put(cek, numstep);
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
			newConceptOrder.populate(concepts, edges, bitsets, true);
			return newConceptOrder;
		} catch (Exception e) {
			boolean b1 = controlCO(order);
			System.out.println("b1=" + b1);
			return null;
		}
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
	}	/**
	 * Find original concept order of a ghost concept
	 */
	/**
	 * The Class ConceptExtentKey.
	 */
	private class ConceptExtentKey implements Iterable<Integer> {

		ISet extent;

		/**
		 * Instantiates a new concept extent key.
		 *
		 * @param extent the extent
		 */
		ConceptExtentKey(ISet extent) {
			this.extent = extent.clone();
		}

		/**
		 * Iterator.
		 *
		 * @return the iterator
		 */
		@Override
		public Iterator<Integer> iterator() {
			return extent.iterator();
		}

		/**
		 * Equals.
		 *
		 * @param o the o
		 * @return true, if successful
		 */
		@Override
		public boolean equals(Object o) {
			return extent.equals(((ConceptExtentKey) o).extent);
		}

		/**
		 * Hash code.
		 *
		 * @return the int
		 */
		@Override
		public int hashCode() {
			// TODO
			// Ã§a fonctionne avec BITSET, Ã§a aurait du fonctionner avec TROVE_HASHSET
			return this.extent.hashCode();
		}

	}
	public ConceptOrderFinder createConceptFinder() {
		// TODO Auto-generated method stub
		return new GhostFinder();
	}

}
