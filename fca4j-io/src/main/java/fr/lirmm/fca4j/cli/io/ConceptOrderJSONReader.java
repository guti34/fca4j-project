/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.cli.io;

import java.io.FileReader;
import java.util.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.IConceptOrder;
import fr.lirmm.fca4j.iset.ISet;

public class ConceptOrderJSONReader {

    public static IConceptOrder[] read(String file, Map<String, IBinaryContext> contexts) throws Exception {

        JSONParser parser = new JSONParser();
        Object root = parser.parse(new FileReader(file));

        Map<String, List<JSONObject>> groupedConcepts = new HashMap<>();

        JSONArray conceptArray;

        // -------- detect format --------

        if (root instanceof JSONObject) {

            JSONObject main = (JSONObject) root;
            conceptArray = (JSONArray) main.get("concepts");

        } else {

            conceptArray = (JSONArray) root;

        }

        // -------- group by context --------

        for (Object o : conceptArray) {

            JSONObject concept = (JSONObject) o;

            String ctxName = (String) concept.get("context");

            groupedConcepts
                    .computeIfAbsent(ctxName, k -> new ArrayList<>())
                    .add(concept);
        }

        // -------- build orders --------

        List<IConceptOrder> result = new ArrayList<>();

        for (String ctxName : groupedConcepts.keySet()) {

            IBinaryContext ctx = contexts.get(ctxName);

            if (ctx == null)
                throw new RuntimeException("Unknown context: " + ctxName);

            ConceptOrder order = buildConceptOrder(groupedConcepts.get(ctxName), ctx);

            result.add(order);
        }

        return result.toArray(new IConceptOrder[0]);
    }

    private static ConceptOrder buildConceptOrder(List<JSONObject> concepts, IBinaryContext ctx) {

        ConceptOrder order = new ConceptOrder(ctx.getName(), ctx, "JSONImport");

        Map<Integer,Integer> idMap = new HashMap<>();

        // ---------- create concepts ----------

        for (JSONObject cJson : concepts) {

            int id = ((Long) cJson.get("id")).intValue();

            ISet extent = ctx.getFactory().createSet(ctx.getObjectCount());
            ISet intent = ctx.getFactory().createSet(ctx.getAttributeCount());
            ISet rextent = ctx.getFactory().createSet(ctx.getObjectCount());
            ISet rintent = ctx.getFactory().createSet(ctx.getAttributeCount());

            // reduced extent
            JSONArray objects = (JSONArray) cJson.get("objects");
            if (objects != null)
                for (Object o : objects) {
                    String name = (String) o;
                    int idx = ctx.getObjectIndex(name);
                    if (idx >= 0)
                        rextent.add(idx);
                }

            // reduced intent
            JSONObject attrs = (JSONObject) cJson.get("attributes");
            if (attrs != null)
                for (Object key : attrs.keySet()) {

                    String attrName = key.toString();

                    int idx = ctx.getAttributeIndex(attrName);
                    if (idx >= 0)
                        rintent.add(idx);
                }

            int newId = order.addConcept(extent, intent, rextent, rintent);

            idMap.put(id, newId);
        }

        // ---------- edges ----------

        for (JSONObject cJson : concepts) {

            int id = ((Long) cJson.get("id")).intValue();
            int parent = idMap.get(id);

            JSONArray children = (JSONArray) cJson.get("children");

            if (children == null)
                continue;

            for (Object c : children) {

                int child = idMap.get(((Long) c).intValue());

                order.addPrecedenceConnection(child, parent);
            }
        }

        // ---------- rebuild extents/intents ----------

        order.computeIntents();
        order.computeExtents();

        return order;
    }
}