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
    protected List<Implication> computeDBasis() {
        int nObj  = context.getObjectCount();
        int nAttr = context.getAttributeCount();

        byte[] matrix = buildMatrix(context);

        int[] flat = NativeBridge.runDbasisFlat(
                nObj, nAttr, matrix, minSupport, nativeMaxThreads);

        return parseDbasisFlat(flat);
    }

    /**
     * Désérialise le tableau plat produit par {@code run_dbasis_flat} en
     * construisant directement les {@link Implication} à partir des indices :
     * aucun JSON, aucune résolution de noms, aucune allocation de String.
     */
    private List<Implication> parseDbasisFlat(int[] flat) {
        List<Implication> result = new ArrayList<>();
        if (flat == null || flat.length < 1) return result;

        int p = 0;
        int m = flat[p++];
        for (int i = 0; i < m; i++) {
            ISet premise = createEmptySet();
            int cardP = flat[p++];
            for (int k = 0; k < cardP; k++) premise.add(flat[p++]);

            ISet conclusion = createEmptySet();
            int cardC = flat[p++];
            for (int k = 0; k < cardC; k++) conclusion.add(flat[p++]);

            int support = flat[p++];

            // Mêmes conventions que l'ancien parseImplication :
            // le constructeur Implication(premise, conclusion, support) fait conclusion \ premise.
            conclusion.addAll(premise);
            result.add(new Implication(premise, conclusion, support));
        }
        return result;
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
}
