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

import java.util.Iterator;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.RCAFamily;
import fr.lirmm.fca4j.core.RCAFamily.FormalContext;
import fr.lirmm.fca4j.iset.ISet;

public class AttributeRenamer {

	public static enum MODE {
		SIMPLE, // default
		FULL_INTENT, // option -ri
		FULL_INTENT_NA, // option -ri -na
		REDUCED_INTENT, // option -ra
		REDUCED_INTENT_NA, // option -ra -na
		REDUCED_INTENT_FULL_WHEN_EMPTY, // option -rai
		REDUCED_INTENT_FULL_WHEN_EMPTY_NA // option -rai -na
	};
/**
 * 
 * @param family
 * @param attrName initial attribute name to be renamed
 * @param mode  renaming mode
 * @param currentConcept  concerned concept
 * @param conceptOrderFinder  an action able to retrieve a disappeared concept in previously computed concept orders
 * @return
 */
	public static String build(RCAFamily family, String attrName, MODE mode, int currentConcept,
			ConceptOrderFinder conceptOrderFinder) {
		ISet visited = family.getFactory().createSet();
		if (currentConcept >= 0)
			visited.add(currentConcept);
		return build(family, attrName, mode, currentConcept, visited, conceptOrderFinder);
	}
/**
 * 
 * @param family
 * @param attrName
 * @param mode renaming mode
 * @param currentConcept
 * @param visited a collection of already visited concepts in construction, used to provide a stop condition
 * @param conceptOrderFinder an action able to retrieve a disappeared concept in previously computed concept orders
 * @return
 */
	private static String build(RCAFamily family, String attrName, MODE mode, int currentConcept, ISet visited,
			ConceptOrderFinder conceptOrderFinder) {
		// parse concept name
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
		// detect ghost concepts
		boolean ghostConcepts = !(fc.getOrder().getConcepts().contains(concept)) && concept >= 0
				&& conceptOrderFinder != null;
		// verify stop criteria: already visited or disappeared (ghost)
		if (visited.contains(concept) || ghostConcepts) {
			ConceptOrder conceptOrder;
			if (ghostConcepts)
				conceptOrder = conceptOrderFinder.findConceptOrder(fcName, concept);
			else
				conceptOrder = fc.getOrder();
			return buildConceptNameWithExtent(attrName, fc, conceptOrder, concept);
		} else {
			if (concept >= 0)
				visited.add(concept);
		}
		// build attribute name
		if (mode != MODE.SIMPLE) {
			String conceptName = buildConceptNameWithIntent(family, fc, concept, mode,
					currentConcept < 0 ? concept : currentConcept, visited, conceptOrderFinder);
			attrName = attrName.replace("C_" + fcName + "_" + concept, conceptName);
			visited.remove(concept);
		}
		return attrName;
	}

/**
 * 
 * @param family
 * @param fc
 * @param concept
 * @param mode renaming mode
 * @param currentConcept
 * @param visited a collection of already visited concepts in construction, used to provide a stop condition
 * @param conceptOrderFinder an action able to retrieve a disappeared concept in previously computed concept orders
 * @return
 */
	private static String buildConceptNameWithIntent(RCAFamily family, FormalContext fc, int concept, MODE mode,
			int currentConcept, ISet visited, ConceptOrderFinder conceptOrderFinder) {
		ISet rIntent = fc.getOrder().getConceptReducedIntent(concept);
		int rIntentNativeCount = 0;
		for (Iterator<Integer> it = rIntent.iterator(); it.hasNext();) {
			if (!fc.isRelationalAttribute(it.next()))
				rIntentNativeCount++;
		}
		String conceptName;
		if ((rIntent.isEmpty() && mode == MODE.REDUCED_INTENT)
				|| (rIntentNativeCount == 0 && mode == MODE.REDUCED_INTENT_NA)) {
			conceptName = fc.getConceptName(concept);
		} else {
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
					if(remainingIntent.cardinality()==fc.getContext().getAttributeCount())
					{
						conceptName="_ALL_ATTRIBUTES_";
						remainingIntent=fc.getContext().getFactory().createSet();
					}
					else conceptName += "/_INH_/";
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
		return conceptName;
	}
	/**
	 * 
	 * @param attrName
	 * @param fc
	 * @param conceptOrder
	 * @param concept
	 * @return
	 */

	private static String buildConceptNameWithExtent(String attrName, FormalContext fc, ConceptOrder conceptOrder,
			int concept) {
		String conceptName = null;
		ISet extentToDisplay;
		extentToDisplay = conceptOrder.getConceptReducedExtent(concept);
		if (extentToDisplay.isEmpty()) {
			extentToDisplay = conceptOrder.getConceptExtent(concept);
			if(extentToDisplay.isEmpty())
				conceptName="_ALL_ATTRIBUTES_";
			else {
				// keyword when all objects are inherited
				if(extentToDisplay.cardinality()==fc.getContext().getObjectCount())
				{
					conceptName="_ALL_OBJECTS_";
					extentToDisplay=fc.getContext().getFactory().createSet(); // clear extension to display
				}
				else conceptName = "/_OBJ_/_INH_/";
			}
		} else {
			conceptName = "/_OBJ_/";
		}
		// build extent
		boolean first = true;
		for (Iterator<Integer> it = extentToDisplay.iterator(); it.hasNext();) {
			int numObj = it.next();
			if (!first)
				conceptName += "&";
			else
				first = false;
			conceptName += fc.getContext().getObjectName(numObj);
		}
		attrName = attrName.replace("C_" + fc.getName() + "_" + concept, conceptName);
		return attrName;

	}
}
