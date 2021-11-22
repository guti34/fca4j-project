package fr.lirmm.fca4j.cli.io;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Iterator;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;

public class ConceptOrderJSONWriter {

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

	public static void write(BufferedWriter buff, ConceptOrder conceptOrder, IBinaryContext matrix) throws IOException {
		JSONObject mainJson = new JSONObject();
		mainJson.put("source", matrix.getName());
		build(mainJson, conceptOrder, matrix);
		mainJson.writeJSONString(buff);
		buff.flush();
		buff.close();
	}
}
