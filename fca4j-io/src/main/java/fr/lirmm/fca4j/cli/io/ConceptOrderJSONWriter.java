/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.cli.io;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.IConceptOrder;
import fr.lirmm.fca4j.core.operator.AbstractScalingOperator;
import fr.lirmm.fca4j.core.operator.MyScalingOperatorFactory;

/**
 * The Class ConceptOrderJSONWriter.
 */
public class ConceptOrderJSONWriter {

	public static JSONArray build(IConceptOrder conceptOrder, boolean fullIntents, boolean fullExtents) {
		return build(conceptOrder, fullIntents, fullExtents, false);
	}

	public static JSONArray build(IConceptOrder conceptOrder, boolean fullIntents, boolean fullExtents,
			boolean distances) {
		JSONArray conceptJsonArray = new JSONArray();
		IBinaryContext matrix = conceptOrder.getContext();
		for (Iterator<Integer> it = conceptOrder.getTopDownIterator(); it.hasNext();) {
			conceptJsonArray.add(buildConcept(conceptOrder, matrix, it.next(), fullIntents, fullExtents, distances));
		}
		return conceptJsonArray;
	}

	private static int distanceToTop(IConceptOrder order, int concept) {
		int dist = Integer.MAX_VALUE;
		for (Iterator<Integer> it = order.getMaximals().iterator(); it.hasNext();) {
			int top = it.next();
			List<Integer> path = order.getShortestPath(concept, top);
			if (path != null && path.size() < dist)
				dist = path.size();
		}
		return dist - 1;
	}

	private static int distanceToBottom(IConceptOrder order, int concept) {
		int dist = Integer.MAX_VALUE;
		for (Iterator<Integer> it = order.getMinimals().iterator(); it.hasNext();) {
			int bottom = it.next();
			List<Integer> path = order.getShortestPath(bottom, concept);
			if (path != null && path.size() < dist)
				dist = path.size();
		}
		return dist - 1;
	}

	private static void generateAttribute(JSONObject attributesJson, String attrName) {
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
			} catch (Exception e) {
				attributesJson.put(attrName, attrName);
			}
		}
	}

	private static JSONObject buildConcept(IConceptOrder conceptOrder, IBinaryContext matrix, int concept,
			boolean fullIntents, boolean fullExtents, boolean distances) {
		JSONObject conceptJson = new JSONObject();
		conceptJson.put("id", concept);
		conceptJson.put("name", matrix.getName() + "_" + concept);
		conceptJson.put("context", matrix.getName());
		if (distances) {
			conceptJson.put("distanceToTop", distanceToTop(conceptOrder, concept));
			conceptJson.put("distanceToBottom", distanceToBottom(conceptOrder, concept));
		}
		// intent réduit
		JSONObject attributesJson = new JSONObject();
		conceptJson.put("attributes", attributesJson);
		for (Iterator<Integer> itIntent = conceptOrder.getConceptReducedIntent(concept).iterator(); itIntent
				.hasNext();) {
			generateAttribute(attributesJson, matrix.getAttributeName(itIntent.next()));
		}
		if (fullIntents) {
			JSONObject fullIntentJson = new JSONObject();
			conceptJson.put("intent", fullIntentJson);
			for (Iterator<Integer> itIntent = conceptOrder.getConceptIntent(concept).iterator(); itIntent
					.hasNext();) {
				generateAttribute(fullIntentJson, matrix.getAttributeName(itIntent.next()));
			}
		}
		// extent réduit
		JSONArray objectJsonArray = new JSONArray();
		conceptJson.put("objects", objectJsonArray);
		for (Iterator<Integer> itExtent = conceptOrder.getConceptReducedExtent(concept).iterator(); itExtent
				.hasNext();) {
			objectJsonArray.add(matrix.getObjectName(itExtent.next()));
		}
		if (fullExtents) {
			JSONArray fullExtentJsonArray = new JSONArray();
			conceptJson.put("extent", fullExtentJsonArray);
			for (Iterator<Integer> itExtent = conceptOrder.getConceptExtent(concept).iterator(); itExtent
					.hasNext();) {
				fullExtentJsonArray.add(matrix.getObjectName(itExtent.next()));
			}
		}
		// children (lower cover) — itérateur de tranche CSR, aucune allocation de set
		JSONArray children = new JSONArray();
		for (Iterator<Integer> itChildren = conceptOrder.getLowerCoverIterator(concept); itChildren.hasNext();) {
			children.add(itChildren.next());
		}
		conceptJson.put("children", children);
		// parents (upper cover) — idem
		JSONArray parents = new JSONArray();
		for (Iterator<Integer> itParents = conceptOrder.getUpperCoverIterator(concept); itParents.hasNext();) {
			parents.add(itParents.next());
		}
		conceptJson.put("parents", parents);
		return conceptJson;
	}

	/**
	 * Write.
	 *
	 * @param buff         the buff
	 * @param conceptOrder the concept order
	 * @param matrix       the matrix
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static void write(BufferedWriter buff, IConceptOrder conceptOrder, IBinaryContext matrix) throws IOException {
		JSONObject mainJson = new JSONObject();
		mainJson.put("source", matrix.getName());
		mainJson.put("concepts", build(conceptOrder, false, false));
		mainJson.writeJSONString(buff);
		buff.flush();
		buff.close();
	}

	/**
	 * Écrit en streaming le document JSON complet via json-simple par concept
	 * (sortie équivalente à mainJson{source,algo,concepts:build(...)} ; seul
	 * l'ordre des 3 clés de premier niveau peut différer). Conservée pour
	 * compatibilité ; préférer {@link #writeStreamingFast} pour les gros treillis.
	 */
	public static void writeStreaming(BufferedWriter buff, IConceptOrder conceptOrder, String source, String algo,
			boolean fullIntents, boolean fullExtents, boolean distances) throws IOException {
		IBinaryContext matrix = conceptOrder.getContext();
		buff.write("{\"source\":");
		JSONValue.writeJSONString(source, buff);
		buff.write(",\"algo\":");
		JSONValue.writeJSONString(algo, buff);
		buff.write(",\"concepts\":[");
		boolean first = true;
		for (Iterator<Integer> it = conceptOrder.getTopDownIterator(); it.hasNext();) {
			int concept = it.next();
			if (!first) {
				buff.write(",");
			}
			first = false;
			buildConcept(conceptOrder, matrix, concept, fullIntents, fullExtents, distances).writeJSONString(buff);
		}
		buff.write("]}");
		buff.flush();
	}

	/**
	 * Variante rapide : écrit le JSON à la main (sans construire de JSONObject par
	 * concept), avec un vocabulaire d'objets pré-échappé une seule fois et des
	 * entiers (id, covers) écrits directement. Le sous-objet {@code attributes}
	 * reste produit par {@link #generateAttribute} pour préserver la logique RCA.
	 *
	 * <p>Sortie sémantiquement identique au chemin {@code build(order,false,false)}
	 * ; l'ordre des clés par concept est fixé (id, name, context, attributes,
	 * objects, children, parents) au lieu de l'ordre de hachage de json-simple.
	 *
	 * @param buff   the buff
	 * @param order  the concept order
	 * @param source value of the top-level "source" field
	 * @param algo   value of the top-level "algo" field
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static void writeStreamingFast(BufferedWriter buff, IConceptOrder order, String source, String algo)
			throws IOException {
		IBinaryContext matrix = order.getContext();
		// pré-échappe le petit vocabulaire d'objets une seule fois
		int nbObj = matrix.getObjectCount();
		String[] qObj = new String[nbObj];
		for (int o = 0; o < nbObj; o++) {
			qObj[o] = quote(matrix.getObjectName(o));
		}
		String ctxName = matrix.getName();
		String qCtx = quote(ctxName);

		buff.write("{\"source\":");
		buff.write(quote(source));
		buff.write(",\"algo\":");
		buff.write(quote(algo));
		buff.write(",\"concepts\":[");
		boolean firstConcept = true;
		for (Iterator<Integer> it = order.getTopDownIterator(); it.hasNext();) {
			int c = it.next();
			if (!firstConcept) {
				buff.write(',');
			}
			firstConcept = false;
			buff.write("{\"id\":");
			buff.write(Integer.toString(c));
			buff.write(",\"name\":");
			buff.write(quote(ctxName + "_" + c));
			buff.write(",\"context\":");
			buff.write(qCtx);
			// attributes (réduit) : generateAttribute conservé pour le cas RCA
			JSONObject attributesJson = new JSONObject();
			for (Iterator<Integer> itI = order.getConceptReducedIntent(c).iterator(); itI.hasNext();) {
				generateAttribute(attributesJson, matrix.getAttributeName(itI.next()));
			}
			buff.write(",\"attributes\":");
			attributesJson.writeJSONString(buff);
			// objects (réduit) : noms pré-échappés
			buff.write(",\"objects\":[");
			boolean f = true;
			for (Iterator<Integer> itE = order.getConceptReducedExtent(c).iterator(); itE.hasNext();) {
				if (!f) {
					buff.write(',');
				}
				f = false;
				buff.write(qObj[itE.next()]);
			}
			buff.write(']');
			// children (lower cover) : entiers directs via l'itérateur CSR
			buff.write(",\"children\":[");
			f = true;
			for (Iterator<Integer> itC = order.getLowerCoverIterator(c); itC.hasNext();) {
				if (!f) {
					buff.write(',');
				}
				f = false;
				buff.write(Integer.toString(itC.next()));
			}
			buff.write(']');
			// parents (upper cover)
			buff.write(",\"parents\":[");
			f = true;
			for (Iterator<Integer> itP = order.getUpperCoverIterator(c); itP.hasNext();) {
				if (!f) {
					buff.write(',');
				}
				f = false;
				buff.write(Integer.toString(itP.next()));
			}
			buff.write("]}");
		}
		buff.write("]}");
		buff.flush();
	}

	private static String quote(String s) {
		return "\"" + JSONValue.escape(s) + "\"";
	}
}