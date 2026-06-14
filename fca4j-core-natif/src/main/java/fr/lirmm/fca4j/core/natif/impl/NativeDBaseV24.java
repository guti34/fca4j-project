/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.core.natif.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import fr.lirmm.fca4j.algo.DBaseV24;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.core.natif.NativeBridge;
import fr.lirmm.fca4j.iset.ISet;

/**
 * Sous-classe de {@link DBaseV24} déléguant l'intégralité du pipeline DBasis
 * au C via {@link NativeBridge#runDbasis} — threading inclus.
 *
 * <p>Le paramètre {@code maxThreads} est transmis directement au C :
 * <ul>
 *   <li>{@code 1}  → séquentiel (mono-thread C)</li>
 *   <li>{@code 0}  → auto-détection des cœurs (pthread C)</li>
 *   <li>{@code >1} → nombre fixé de threads (pthread C)</li>
 * </ul>
 *
 * <p>Le pool de threads Java n'est plus utilisé — tout le parallélisme
 * est géré en C avec pthread, sans overhead JNI par attribut.
 */
public class NativeDBaseV24 extends DBaseV24 {

    /** Conservé localement car private dans DBaseV24 */
    private final int nativeMaxThreads;

    public NativeDBaseV24(IBinaryContext context, int minSupport, int maxThreads) {
        super(context, minSupport, maxThreads);
        this.nativeMaxThreads = maxThreads;
    }

    /**
     * Remplace le pipeline Java complet par un unique appel C.
     * {@inheritDoc}
     */
    @Override
    protected List computeDBasis() {
        int nObj  = context.getObjectCount();
        int nAttr = context.getAttributeCount();

        byte[]   matrix    = buildMatrix(context);
        String[] attrNames = buildAttrNames(context);

        String json = NativeBridge.runDbasis(
                nObj, nAttr, matrix, attrNames,
                minSupport, nativeMaxThreads);

        return parseDbasisJson(json);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Utilitaires de construction
    // ══════════════════════════════════════════════════════════════════════

    private static byte[] buildMatrix(IBinaryContext ctx) {
        int nObj  = ctx.getObjectCount();
        int nAttr = ctx.getAttributeCount();
        byte[] m  = new byte[nObj * nAttr];
        for (int o = 0; o < nObj; o++) {
            ISet intent = ctx.getIntent(o);
            for (Iterator it = intent.iterator(); it.hasNext();) {
                int a = (Integer) it.next();
                m[o * nAttr + a] = 1;
            }
        }
        return m;
    }

    private static String[] buildAttrNames(IBinaryContext ctx) {
        int nAttr = ctx.getAttributeCount();
        String[] names = new String[nAttr];
        for (int a = 0; a < nAttr; a++) {
            names[a] = ctx.getAttributeName(a);
        }
        return names;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Désérialisation JSON → List<Implication>
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Désérialise le JSON produit par {@code run_dbasis_impl}.
     *
     * <p>Format :
     * <pre>
     * {"algorithm":"DBase","basis":[
     *   {"premise":["a","b"],"conclusion":["c"],"support":42},
     *   ...
     * ]}
     * </pre>
     */
    private List parseDbasisJson(String json) {
        List result = new ArrayList();
        if (json == null || json.isEmpty()) return result;

        int basisStart = json.indexOf("\"basis\":[");
        if (basisStart < 0) return result;
        basisStart += "\"basis\":[".length();
        int basisEnd = json.lastIndexOf("]}");
        if (basisEnd < 0) return result;
        String basisStr = json.substring(basisStart, basisEnd);

        int depth = 0, start = -1;
        for (int i = 0; i < basisStr.length(); i++) {
            char c = basisStr.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    Implication imp = parseImplication(basisStr.substring(start, i + 1));
                    if (imp != null) result.add(imp);
                    start = -1;
                }
            }
        }
        return result;
    }

    private Implication parseImplication(String impl) {
        try {
            ISet premise    = parseAttrArray(impl, "premise");
            ISet conclusion = parseAttrArray(impl, "conclusion");
            int  support    = parseIntField(impl,  "support");
            // Implication(premise, conclusion, supportSize) fait conclusion \ premise
            conclusion.addAll(premise);
            return new Implication(premise, conclusion, support);
        } catch (Exception e) {
            return null;
        }
    }

    private ISet parseAttrArray(String impl, String fieldName) {
        ISet set = createEmptySet();
        String marker = "\"" + fieldName + "\":[";
        int idx = impl.indexOf(marker);
        if (idx < 0) return set;
        idx += marker.length();
        int end = impl.indexOf(']', idx);
        if (end < 0) return set;
        String arr = impl.substring(idx, end).trim();
        if (arr.isEmpty()) return set;
        int pos = 0;
        while (pos < arr.length()) {
            int q1 = arr.indexOf('"', pos);
            if (q1 < 0) break;
            int q2 = arr.indexOf('"', q1 + 1);
            if (q2 < 0) break;
            String name = arr.substring(q1 + 1, q2);
            int attrIdx = context.getAttributeIndex(name);
            if (attrIdx >= 0) set.add(attrIdx);
            pos = q2 + 1;
        }
        return set;
    }

    private static int parseIntField(String impl, String fieldName) {
        String marker = "\"" + fieldName + "\":";
        int idx = impl.indexOf(marker);
        if (idx < 0) return 0;
        idx += marker.length();
        int end = idx;
        while (end < impl.length() &&
               (Character.isDigit(impl.charAt(end)) || impl.charAt(end) == '-'))
            end++;
        try { return Integer.parseInt(impl.substring(idx, end)); }
        catch (NumberFormatException e) { return 0; }
    }
}
