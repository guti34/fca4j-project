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
*/package fr.lirmm.fca4j.util;

import java.util.Iterator;

import fr.lirmm.fca4j.core.RCAFamily;
import fr.lirmm.fca4j.core.RCAFamily.FormalContext;
import fr.lirmm.fca4j.iset.ISet;

public class AttributeRenamer {

	public static enum MODE {
		SIMPLE, FULL_INTENT, FULL_INTENT_NA, REDUCED_INTENT, REDUCED_INTENT_NA, REDUCED_INTENT_FULL_WHEN_EMPTY,
		REDUCED_INTENT_FULL_WHEN_EMPTY_NA
	};

	public static String build(RCAFamily family, String attrName, MODE mode, int currentConcept) {
		ISet visited=family.getFactory().createSet();
		if(currentConcept>=0)visited.add(currentConcept);
		return build(family,attrName,mode,currentConcept,visited);
	}
		private static String build(RCAFamily family, String attrName, MODE mode, int currentConcept,ISet visited) {
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
		if (visited.contains(concept)) {
			return attrName;
		}else visited.add(concept);
		FormalContext fc = family.getFormalContext(fcName);
		// build attribute name
//		if(currentConcept<0)
//			throw new NullPointerException("concept="+concept);
		if (concept != currentConcept && mode != MODE.SIMPLE && fc.getOrder().getConcepts().contains(concept)) {
//			System.out.println("concept="+concept+" currentConcept="+currentConcept);
			String conceptName = buildConceptName(family, fc, concept, mode,
					currentConcept < 0 ? concept : currentConcept,visited);
			attrName = attrName.replace("C_" + fcName + "_" + concept, conceptName);
		}
		return attrName;
	}
	private static String buildConceptName(RCAFamily family, FormalContext fc, int concept, MODE mode,
			int currentConcept,ISet visited) {
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
			intent.removeAll(rIntent);
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
				conceptName += build(family, attrName, mode, currentConcept,visited);
			}
			// build inherited intent
			if (mode == MODE.FULL_INTENT || mode == MODE.FULL_INTENT_NA
					|| (mode == MODE.REDUCED_INTENT_FULL_WHEN_EMPTY && rIntent.isEmpty())
					|| (mode == MODE.REDUCED_INTENT_FULL_WHEN_EMPTY_NA && rIntent.isEmpty())) {
				if (!intent.isEmpty()) { 
					conceptName += "/I/";
				for (Iterator<Integer> it = intent.iterator(); it.hasNext();) {
					int numAttr = it.next();
					if ((mode == MODE.FULL_INTENT_NA || mode == MODE.REDUCED_INTENT_FULL_WHEN_EMPTY_NA)
							&& fc.isRelationalAttribute(numAttr))
						continue;
					if (!(conceptName.length() == 0 || conceptName.endsWith("/")))
						conceptName += "&";
					String attrName = fc.getContext().getAttributeName(numAttr);
					conceptName += build(family, attrName, mode, -1,visited);
				}
				}
			}
		}
		return conceptName;
	}
}
