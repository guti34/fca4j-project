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
package fr.lirmm.fca4j.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.RCAFamily;
import fr.lirmm.fca4j.core.RCAFamily.FormalContext;
import fr.lirmm.fca4j.iset.ISet;

public class AttributeRenamer {

	public static enum MODE {
		SIMPLE, // default original concept are used (of the form
				// C_FormalContextName_ConceptNumber)
		FULL_INTENT, // option -ri rename relational attributes using concept full intents
		FULL_INTENT_NA, // option -ri -na rename relational attributes using concept full intents but
						// only with native attributes
		REDUCED_INTENT, // option -ra rename relational attributes using concept reduced intents
		REDUCED_INTENT_NA, // option -ra -na rename relational attributes using concept reduced intents but
							// only with native attributes
		REDUCED_INTENT_FULL_WHEN_EMPTY, // option -rai rename relational attributes using concept full intents only when
										// reduced intent is empty (object concepts)
		REDUCED_INTENT_FULL_WHEN_EMPTY_NA // option -rai -na rename relational attributes using concept full intents
											// only when reduced intent is empty (object concepts) but only with native
											// attributes
	};

	/**
	 * This public function rewrite the original relational attribute name
	 * 
	 * @param family
	 * @param attrName           initial attribute name to be renamed
	 * @param mode               renaming mode
	 * @param currentConcept     concerned concept
	 * @param conceptOrderFinder an action able to retrieve a disappeared concept in
	 *                           previously computed concept orders
	 * @return                   the rewritten attribute name
	 */
/*static int counter=0;
static int profondeur=0;
static int maxConceptName=0;
static HashMap<Integer,String> conceptNames=new HashMap<>();

	public static String build(RCAFamily family, String attrName, MODE mode, int currentConcept,
			ConceptOrderFinder conceptOrderFinder) {
		counter=0;
		profondeur=0;
		System.out.println("renaming "+attrName);
		ISet visited = family.getFactory().createSet();
		if (currentConcept >= 0) {
			visited.add(currentConcept);
		}
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Callable<String> task = () -> {
            return build(family, attrName, mode, currentConcept, visited, conceptOrderFinder);
        };

        Future<String> future = executor.submit(task);
        String result="";
        try {
            // On attend 2 secondes pour le résultat
            result = future.get(5, TimeUnit.SECONDS);
         } catch (TimeoutException e) {
            System.out.println("************************Timeout atteint, on annule la tâche.counter="+counter);
            future.cancel(true); // Interruption si possible
             result="TIMEOUT";
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            System.out.println("ERROR counter="+counter);
        } finally {
            executor.shutdownNow();
        }

		System.out.println("counter="+counter+" profondeur="+profondeur+"max concept name="+maxConceptName);
		return result;
	}
*/	
	public static String build(RCAFamily family, String attrName, MODE mode, int currentConcept,
			ConceptOrderFinder conceptOrderFinder) {
		ISet visited = family.getFactory().createSet();
		if (currentConcept >= 0) {
			visited.add(currentConcept);
		}
		return build(family, attrName, mode, currentConcept, visited, conceptOrderFinder);
	}
	/**
	 * This function is called by the public build function to rewrite the original
	 * relational attribute name by rewriting concepts of the form
	 * C_FormalContextName_Number with the intent or extent of the concept
	 * 
	 * @param family             the relational family
	 * @param attrName	         attribute name
	 * @param mode               renaming mode
	 * @param currentConcept     the current concerned concept
	 * @param visited            a collection of already visited concepts in
	 *                           construction, used to provide a stop condition
	 * @param conceptOrderFinder an action able to retrieve a disappeared concept in
	 *                           previously computed concept orders
	 * @return	                 the rewritten attribute name
	 */
	private static String build(RCAFamily family, String attrName, MODE mode, int currentConcept, ISet visited,
			ConceptOrderFinder conceptOrderFinder) {
//		counter++;
		// parse concept name to retrieve the name of the formal context and the number
		// of the concept
		int beg = attrName.indexOf("(C_");
		if (beg < 0)
			return attrName; // native attribute
		beg += 3;
		int end = beg;
		while (attrName.charAt(end) != '_') {
			end++;
		}
		String fcName = attrName.substring(beg, end);
		beg = end + 1;
		end = attrName.indexOf(')', beg);
		String strConcept = attrName.substring(beg, end);
		int concept = Integer.valueOf(strConcept);
		FormalContext fc = family.getFormalContext(fcName);
		if (concept == currentConcept) {
			attrName = attrName.replace("C_" + fcName + "_" + concept, "_SELF_");
			return attrName;
		}
		// detect ghost concepts: a concept of a previous step which has disappeared
		boolean ghostConcepts = !(fc.getOrder().getConcepts().contains(concept)) && concept >= 0
				&& conceptOrderFinder != null;
		// verify stop criteria: The concept has already been explored and may cause an
		// infinite loop in the construction process or the concept from a previous step
		// in this case the concept name is built with the concept extent
		if (visited.contains(concept) || ghostConcepts) {
			ConceptOrder conceptOrder;
			if (ghostConcepts)
				conceptOrder = conceptOrderFinder.findConceptOrder(fcName, concept);
			else
				conceptOrder = fc.getOrder();
			return buildConceptNameWithExtent(attrName, fc, conceptOrder, concept);
		} else {
			if (concept >= 0)
			{
				visited.add(concept);
//				if(visited.cardinality()>profondeur)
//					profondeur=visited.cardinality();
			}
		}
		// build attribute name
		if (mode != MODE.SIMPLE) {
			String conceptName = buildConceptNameWithIntent(family, fc, concept, mode,
					currentConcept < 0 ? concept : currentConcept, visited, conceptOrderFinder);
			attrName = attrName.replace("C_" + fcName + "_" + concept, conceptName);
			visited.remove(concept);
//			System.out.println("visited="+visited.cardinality());
		}
		return attrName;
	}

	/**
	 * This function build concept name from the concept intent
	 * 
	 * @param family
	 * @param fc
	 * @param concept
	 * @param mode               renaming mode
	 * @param currentConcept
	 * @param visited            a collection of already visited concepts in
	 *                           construction, used to provide a stop condition
	 * @param conceptOrderFinder an action able to retrieve a disappeared concept in
	 *                           previously computed concept orders
	 * @return
	 */
	private static String buildConceptNameWithIntent(RCAFamily family, FormalContext fc, int concept, MODE mode,
			int currentConcept, ISet visited, ConceptOrderFinder conceptOrderFinder) {
//		if(conceptNames.containsKey(concept))
//			return conceptNames.get(concept);
		// work with intent to build the concept name
		ISet rIntent = fc.getOrder().getConceptReducedIntent(concept);
		// count the native attributes of the concept
		int rIntentNativeCount = 0;
		for (Iterator<Integer> it = rIntent.iterator(); it.hasNext();) {
			if (!fc.isRelationalAttribute(it.next()))
				rIntentNativeCount++;
		}
		String conceptName;
		// build the concept from the reduced intent (-ra option)
		if ((rIntent.isEmpty() && mode == MODE.REDUCED_INTENT)
				|| (rIntentNativeCount == 0 && mode == MODE.REDUCED_INTENT_NA)) {
			conceptName = fc.getConceptName(concept);
		} else // build the concept from reduced or entire intent depending on option (-rai or
				// -ri)
		{
			ISet intent = fc.getOrder().getConceptIntent(concept).clone();
			ISet remainingIntent = intent.clone();
			remainingIntent.removeAll(rIntent);
			conceptName = "";

			// build reduced intent
			for (Iterator<Integer> it = rIntent.iterator(); it.hasNext();) {
				int numAttr = it.next();
				if ((mode == MODE.FULL_INTENT_NA || mode == MODE.REDUCED_INTENT_NA
						|| mode == MODE.REDUCED_INTENT_FULL_WHEN_EMPTY_NA) && fc.isRelationalAttribute(numAttr))
					continue;
				if (conceptName.length() > 0)
					conceptName += "&";
				String attrName = fc.getContext().getAttributeName(numAttr);
				conceptName += build(family, attrName, mode, currentConcept, visited, conceptOrderFinder);
			}
			// build inherited intent
			if (mode == MODE.FULL_INTENT || mode == MODE.FULL_INTENT_NA
					|| (mode == MODE.REDUCED_INTENT_FULL_WHEN_EMPTY && rIntent.isEmpty())
					|| (mode == MODE.REDUCED_INTENT_FULL_WHEN_EMPTY_NA && rIntent.isEmpty())) {
				if (intent.isEmpty()) {
					conceptName = "_ALL_OBJECTS_";
				} else if (!remainingIntent.isEmpty()) {
					// keyword when all attributes are inherited
					if (remainingIntent.cardinality() == fc.getContext().getAttributeCount()) {
						conceptName = "_ALL_ATTRIBUTES_";
						remainingIntent = fc.getContext().getFactory().createSet();
					} else 
						conceptName += "/_INH_/";
					// build the concept name from chosen intent
					for (Iterator<Integer> it = remainingIntent.iterator(); it.hasNext();) {
						int numAttr = it.next();
						if ((mode == MODE.FULL_INTENT_NA || mode == MODE.REDUCED_INTENT_FULL_WHEN_EMPTY_NA)
								&& fc.isRelationalAttribute(numAttr))
							continue;
						if (!conceptName.endsWith("/"))
							conceptName += "&";
						String attrName = fc.getContext().getAttributeName(numAttr);
						conceptName += build(family, attrName, mode, -1, visited, conceptOrderFinder);
					}
				}

			}			
		}
//		if(!conceptNames.containsKey(concept)) conceptNames.put(concept,conceptName);
//		if(conceptName.length()>maxConceptName)
//			maxConceptName=conceptName.length();
		return conceptName;
	}

	/**
	 * This function build the concept name from its extent. used when the concept
	 * name cannot be built from intent (current concept has disapeared or a cycle
	 * exist in intent construction)
	 * 
	 * @param attrName     attribute name
	 * @param fc           formal context
	 * @param conceptOrder concept order (aoc-poset or lattice)
	 * @param concept      concerned concept
	 * @return
	 */

	private static String buildConceptNameWithExtent(String attrName, FormalContext fc, ConceptOrder conceptOrder,
			int concept) {
		String conceptName = null;
		ISet extentToDisplay;
		// work with extent to build the concept name
		extentToDisplay = conceptOrder.getConceptReducedExtent(concept);
		// work with global extent when the reduced extent is empty
		if (extentToDisplay.isEmpty()) {
			extentToDisplay = conceptOrder.getConceptExtent(concept);
			// _ALL_ATTRIBUTES_ keyword is used when global extent is empty
			if (extentToDisplay.isEmpty())
				conceptName = "_ALL_ATTRIBUTES_";
			else // use inherited objects
			{
				// _ALL_OBJECTS_ keyword when all objects are inherited
				if (extentToDisplay.cardinality() == fc.getContext().getObjectCount()) {
					conceptName = "_ALL_OBJECTS_";
					extentToDisplay = fc.getContext().getFactory().createSet(); // clear extension to display
				}
				// otherwise display inherited objects
				else
					conceptName = "/_OBJ_/_INH_/";
			}
		} else // display reduced extent
		{
			conceptName = "/_OBJ_/";
		}
		// build concept name with chosen extent
		boolean first = true;
		for (Iterator<Integer> it = extentToDisplay.iterator(); it.hasNext();) {
			int numObj = it.next();
			if (!first)
				conceptName += "&";
			else
				first = false;
			conceptName += fc.getContext().getObjectName(numObj);
		}
		// replace native concept name by the built name from extent
		attrName = attrName.replace("C_" + fc.getName() + "_" + concept, conceptName);
		return attrName;

	}
}
