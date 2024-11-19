package fr.lirmm.fca4j.util;

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
		// build attribute name
		if (concept != currentConcept && mode != MODE.SIMPLE && fc.getOrder().getConcepts().contains(concept)) {
			String conceptName = buildConceptName(family, fc, concept, mode,
					currentConcept < 0 ? concept : currentConcept);
			attrName = attrName.replace("C_" + fcName + "_" + concept, conceptName);
		}
		return attrName;
	}

	private static String buildConceptName(RCAFamily family, FormalContext fc, int concept, MODE mode,
			int currentConcept) {
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
				conceptName += build(family, attrName, mode, currentConcept);
			}
			// build inherited intent
			if (mode == MODE.FULL_INTENT || mode == MODE.FULL_INTENT_NA
					|| (mode == MODE.REDUCED_INTENT_FULL_WHEN_EMPTY && rIntent.isEmpty())
					|| (mode == MODE.REDUCED_INTENT_FULL_WHEN_EMPTY_NA && rIntent.isEmpty())) {
				if (!intent.isEmpty())
					conceptName += "/I/";
				for (Iterator<Integer> it = intent.iterator(); it.hasNext();) {
					int numAttr = it.next();
					if ((mode == MODE.FULL_INTENT_NA || mode == MODE.REDUCED_INTENT_FULL_WHEN_EMPTY_NA)
							&& fc.isRelationalAttribute(numAttr))
						continue;
					if (!(conceptName.length() == 0 || conceptName.endsWith("/")))
						conceptName += "&";
					String attrName = fc.getContext().getAttributeName(numAttr);
					conceptName += build(family, attrName, mode, -1);
				}
			}
		}
		return conceptName;
	}
}
