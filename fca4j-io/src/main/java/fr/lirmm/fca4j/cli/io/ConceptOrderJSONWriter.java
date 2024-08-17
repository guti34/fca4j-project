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
package fr.lirmm.fca4j.cli.io;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Iterator;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.IConceptOrder;
import fr.lirmm.fca4j.core.operator.AbstractScalingOperator;
import fr.lirmm.fca4j.core.operator.MyScalingOperatorFactory;

/**
 * The Class ConceptOrderJSONWriter.
 */
public class ConceptOrderJSONWriter {

	/**
	 * Builds the.
	 *
	 * @param json the json
	 * @param conceptOrder the concept order
	 * @param matrix the matrix
	 */
/*	public static JSONArray build(ConceptOrder conceptOrder) {
		JSONArray conceptArray = new JSONArray();
		IBinaryContext matrix = conceptOrder.getContext();
		for (Iterator<Integer> it = conceptOrder.getTopDownIterator(); it.hasNext();) {
			int concept = it.next();
			JSONObject conceptJson = new JSONObject();
			conceptJson.put("id", concept);
			conceptArray.add(conceptJson);
			// populate intent
			JSONArray attributesArray = new JSONArray();
			conceptJson.put("attributes", attributesArray);
			for (Iterator<Integer> itIntent = conceptOrder.getConceptReducedIntent(concept).iterator(); itIntent
					.hasNext();) {
				String attrName = matrix.getObjectName(itIntent.next());
				attributesArray.add(attrName);
			}

			// populate extent
			JSONArray objectArray = new JSONArray();
			conceptJson.put("objects", objectArray);
			for (Iterator<Integer> itExtent = conceptOrder.getConceptReducedExtent(concept).iterator(); itExtent
					.hasNext();) {
				String objName = matrix.getObjectName(itExtent.next());
				objectArray.add(objName);
			}
			// populate children
			JSONArray children = new JSONArray();
			for (Iterator<Integer> itChildren = conceptOrder.getLowerCover(concept).iterator(); itChildren.hasNext();) {
				children.add(itChildren.next());
			}
			conceptJson.put("children", children);
		}
		return conceptArray;
	}
*/
	/**
	 * Generate JSON.
	 *
	 * @param family           the rca family
	 * @param conceptOrder     the concept order
	 */
	public static JSONArray build(IConceptOrder conceptOrder,
			boolean fullIntents, boolean fullExtents) {
		JSONArray conceptJsonArray=new JSONArray();
		IBinaryContext matrix = conceptOrder.getContext();
		for (Iterator<Integer> it = conceptOrder.getTopDownIterator(); it.hasNext();) {
			int concept = it.next();
			JSONObject conceptJson = new JSONObject();
			conceptJson.put("id", concept);
			conceptJson.put("context", matrix.getName());
			conceptJsonArray.add(conceptJson);
			// populate reduced intent
			JSONObject attributesJson = new JSONObject();
			conceptJson.put("attributes", attributesJson);
			for (Iterator<Integer> itIntent = conceptOrder.getConceptReducedIntent(concept).iterator(); itIntent
					.hasNext();) {
				String attrName = matrix.getAttributeName(itIntent.next());
				generateAttribute(attributesJson,attrName );
			}
			if (fullIntents) {
				// populate full intent
				JSONObject fullIntentJson = new JSONObject();
				conceptJson.put("intent", fullIntentJson);
				for (Iterator<Integer> itIntent = conceptOrder.getConceptIntent(concept).iterator(); itIntent
						.hasNext();) {
					String attrName = matrix.getAttributeName(itIntent.next());
					generateAttribute(fullIntentJson,attrName );
				}
			}
			// populate extent
			JSONArray objectJsonArray = new JSONArray();
			conceptJson.put("objects", objectJsonArray);
			for (Iterator<Integer> itExtent = conceptOrder.getConceptReducedExtent(concept).iterator(); itExtent
					.hasNext();) {
				String objName = matrix.getObjectName(itExtent.next());
				objectJsonArray.add(objName);
			}
			if (fullExtents) {
				// populate full extent
				JSONArray fullExtentJsonArray = new JSONArray();
				conceptJson.put("extent", fullExtentJsonArray);
				for (Iterator<Integer> itExtent = conceptOrder.getConceptExtent(concept).iterator(); itExtent
						.hasNext();) {
					String objName = matrix.getObjectName(itExtent.next());
					fullExtentJsonArray.add(objName);
				}
			}
			// populate children
			JSONArray children = new JSONArray();
			for (Iterator<Integer> itChildren = conceptOrder.getLowerCover(concept).iterator(); itChildren.hasNext();) {
				children.add(itChildren.next());
			}
			conceptJson.put("children", children);
			// populate parents
			JSONArray parents = new JSONArray();
			for (Iterator<Integer> itParents = conceptOrder.getUpperCover(concept).iterator(); itParents.hasNext();) {
				parents.add(itParents.next());
			}
			conceptJson.put("parents", parents);
		}
		return conceptJsonArray;

	}
	private static void generateAttribute(JSONObject attributesJson,String attrName ) {
		int index = attrName.indexOf("(C_");
		if (index > 0) {
			String attrRelName = attrName.substring(0, index);
			JSONObject relAttrJson = (JSONObject) attributesJson.get(attrRelName);
			if (relAttrJson == null) {
				relAttrJson = new JSONObject();
				relAttrJson.put("concepts", new JSONArray());
				attributesJson.put(attrRelName, relAttrJson);
				// parse relation name
				int beg = attrRelName.indexOf("_");
				String relationName = attrRelName.substring(beg + 1);
				relAttrJson.put("relation", relationName);
				// parse operator
				String sOperator = attrRelName.substring(0, beg);
				AbstractScalingOperator operator = MyScalingOperatorFactory.createScalingOperator(sOperator, null);
				if (operator == null) {
					relAttrJson.put("operator", "?");
				} else {
					if (MyScalingOperatorFactory.hasParameter(operator.getName())) {
						relAttrJson.put("percent", MyScalingOperatorFactory.getParameter(operator.getName()));
						int idx = operator.getName().lastIndexOf('N');
						relAttrJson.put("operator", operator.getName().substring(0, idx + 1));
					} else {
						relAttrJson.put("operator", operator.getName());
					}
				}
				// parse target
				String s = attrName.substring(index + 3);
				int underscore = s.indexOf("_");
				String target = s.substring(0, underscore);
				relAttrJson.put("target", target);

			}
			// parse concepts
			String s = attrName.substring(index + 3);
			int parenthesis = s.indexOf(")");
			int underscore = s.indexOf("_");
			int numConcept = Integer.parseInt(s.substring(underscore + 1, parenthesis));
			JSONArray attrConceptJsonArray = (JSONArray) relAttrJson.get("concepts");
			attrConceptJsonArray.add(numConcept);
		} else {
			try {
				attributesJson.put(attrName, attrName);
/*				int underscore = attrName.indexOf("_");
				String key = attrName.substring(0, underscore);
				String value = attrName.substring(underscore + 1);
				attributesJson.put(key, value);
*/			} catch (Exception e) {
				attributesJson.put(attrName, attrName);
			}
		}

	}
	/**
	 * Write.
	 *
	 * @param buff the buff
	 * @param conceptOrder the concept order
	 * @param matrix the matrix
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static void write(BufferedWriter buff, ConceptOrder conceptOrder, IBinaryContext matrix) throws IOException {
		JSONObject mainJson = new JSONObject();
		mainJson.put("source", matrix.getName());
		mainJson.put("concepts",build( conceptOrder, false, false));
		mainJson.writeJSONString(buff);
		buff.flush();
		buff.close();
	}
}
