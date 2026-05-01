/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.cli.io;

import java.util.Iterator;

import org.json.simple.JSONArray;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;

public final class RuleExporterUtils {

    private RuleExporterUtils() {}

    public static String formatAttributes(
            ISet set,
            IBinaryContext context) {

        StringBuilder sb = new StringBuilder();
        boolean first = true;

        for (Iterator<Integer> it=set.iterator();it.hasNext();) {
        	int a=it.next();
            if (!first) sb.append(", ");
            sb.append(context.getAttributeName(a));
            first = false;
        }
        return sb.toString();
    }

    public static JSONArray attributesAsJsonArray(
            ISet set,
            IBinaryContext context) {

        JSONArray array = new JSONArray();
        for (Iterator<Integer> it=set.iterator();it.hasNext();) {
        	int a=it.next();
            array.add(context.getAttributeName(a));
        }
        return array;
    }

}
