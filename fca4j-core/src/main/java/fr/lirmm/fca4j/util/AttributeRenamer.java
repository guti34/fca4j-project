package fr.lirmm.fca4j.util;

import java.util.HashSet;
import java.util.Iterator;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.RCAFamily;
import fr.lirmm.fca4j.core.RCAFamily.FormalContext;
import fr.lirmm.fca4j.core.RCAFamily.RelationalContext;
import fr.lirmm.fca4j.iset.ISet;

public class AttributeRenamer {

	public static enum MODE {
		SIMPLE, FULL_INTENT, REDUCED_INTENT, REDUCED_INTENT_FULL_WHEN_EMPTY
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
		if (concept == currentConcept) {
			return attrName;
		}
		// build attribute name
		else {
			if (mode != MODE.SIMPLE) {
				String conceptName = buildConceptName(family, fc, concept, mode, 
						currentConcept < 0 ? concept : currentConcept);
				attrName = attrName.replace("C_" + fcName + "_" + concept, conceptName);
			}
			return attrName;
		}
	}

	private static String buildConceptName(RCAFamily family, FormalContext fc, int concept, MODE mode,
			int currentConcept) {
		ISet rIntent = fc.getOrder().getConceptReducedIntent(concept);
		String conceptName;
		if (rIntent.isEmpty() && mode == MODE.REDUCED_INTENT) {
			conceptName = fc.getConceptName(concept);
		} else {
			ISet intent = fc.getOrder().getConceptIntent(concept).clone();
			intent.removeAll(rIntent);
			conceptName = "";

			// build reduced intent
			for (Iterator<Integer> it = rIntent.iterator(); it.hasNext();) {
				if (conceptName.length() > 0)
					conceptName += "&";
				int numAttr = it.next();
				String attrName = fc.getContext().getAttributeName(numAttr);
				conceptName += build(family, attrName, mode, currentConcept);
			}
			// build inherited intent
			if (mode == MODE.FULL_INTENT || (mode == MODE.REDUCED_INTENT_FULL_WHEN_EMPTY && rIntent.isEmpty())) {
				if (!intent.isEmpty())
					conceptName += "/I/";
				for (Iterator<Integer> it = intent.iterator(); it.hasNext();) {
					if (!(conceptName.length() == 0 || conceptName.endsWith("/")))
						conceptName += "&";
					int numAttr = it.next();
					String attrName = fc.getContext().getAttributeName(numAttr);
					conceptName += build(family, attrName, mode,-1);
				}
			}
		}
		return conceptName;
	}
}
