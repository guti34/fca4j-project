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
	public static void build(JSONObject json, ConceptOrder conceptOrder, IBinaryContext matrix) {
		JSONArray conceptArray = new JSONArray();
		json.put("concepts", conceptArray);
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
			// populate parents
			/*
			 * JSONArray parents = new JSONArray(); for (Iterator<Integer>
			 * itParents = conceptOrder.getUpperCover(concept).iterator();
			 * itParents.hasNext();) { parents.add(itParents.next()); }
			 * conceptJson.put("parents", parents);
			 */
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
		build(mainJson, conceptOrder, matrix);
		mainJson.writeJSONString(buff);
		buff.flush();
		buff.close();
	}
}
