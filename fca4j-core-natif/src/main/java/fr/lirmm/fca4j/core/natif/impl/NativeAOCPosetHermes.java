/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.core.natif.impl;

import java.util.BitSet;
import java.util.Iterator;

import fr.lirmm.fca4j.algo.AbstractAlgo;
import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.natif.NativeBridge;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;

/**
 * Implémentation native de l'AOC-poset Hermes.
 *
 * <p>Délègue l'intégralité du calcul à {@code run_hermes_flat} via
 * {@link NativeBridge#runHermesFlat}. Le résultat est un tableau d'entiers
 * plat (indices uniquement) consommé directement par
 * {@link ConceptOrder#populate} — aucun JSON, aucune résolution de noms.
 *
 * <p>Format du tableau plat (voir {@code co_to_flat_array} côté C) :
 * <pre>
 *   [0]            N      = nombre de concepts
 *   [1]            E      = nombre d'arêtes
 *   [2 .. 2+2E-1]  edges  = E paires (child, parent)
 *   puis pour chaque concept 0..N-1 :
 *       [card_rextent, o0, o1, ...]
 *       [card_rintent, a0, a1, ...]
 * </pre>
 */
public class NativeAOCPosetHermes implements AbstractAlgo {

    private final IBinaryContext matrix;
    private final ISetFactory    factory;
    private ConceptOrder         order;

    public NativeAOCPosetHermes(IBinaryContext matrix) {
        this.matrix  = matrix;
        this.factory = matrix.getFactory();
    }

    @Override
    public String getDescription() {
        return "Hermes";
    }

    @Override
    public ConceptOrder getResult() {
        return order;
    }

    @Override
    public void run() {
        int nObj  = matrix.getObjectCount();
        int nAttr = matrix.getAttributeCount();

        byte[] mat  = buildMatrix(matrix);
        int[]  flat = NativeBridge.runHermesFlat(nObj, nAttr, mat);

        order = new ConceptOrder("AOCposetWithHermes", matrix, getDescription());
        populateFromFlat(order, flat);
    }

    // ── Désérialisation tableau plat → ConceptOrder.populate() ───────────────

    private void populateFromFlat(ConceptOrder co, int[] flat) {
        if (flat == null || flat.length < 2) return;

        int p = 0;
        int N = flat[p++];
        int E = flat[p++];
        if (N <= 0) return;

        /* Concepts implicitement numérotés 0..N-1 */
        int[] concepts = new int[N];
        for (int i = 0; i < N; i++) concepts[i] = i;

        /* Arêtes : E paires (child, parent) déjà à plat */
        int[] edges = new int[2 * E];
        for (int i = 0; i < 2 * E; i++) edges[i] = flat[p++];

        /* bitsets : [2i] = rextent, [2i+1] = rintent */
        BitSet[] bitsets = new BitSet[N * 2];
        for (int c = 0; c < N; c++) {
            BitSet rext = new BitSet();
            int cardRe = flat[p++];
            for (int k = 0; k < cardRe; k++) rext.set(flat[p++]);
            bitsets[c * 2] = rext;
            BitSet rint = new BitSet();
            int cardRi = flat[p++];
            for (int k = 0; k < cardRi; k++) rint.set(flat[p++]);
            bitsets[c * 2 + 1] = rint;
        }

        co.populate(concepts, edges, bitsets);
        co.buildExtentIntent();
    }

    // ── Utilitaires ──────────────────────────────────────────────────────────

    static byte[] buildMatrix(IBinaryContext ctx) {
        int nObj  = ctx.getObjectCount();
        int nAttr = ctx.getAttributeCount();
        byte[] m  = new byte[nObj * nAttr];
        for (int o = 0; o < nObj; o++) {
            ISet intent = ctx.getIntent(o);
            for (Iterator it = intent.iterator(); it.hasNext();) {
                int a = ((Integer) it.next()).intValue();
                m[o * nAttr + a] = 1;
            }
        }
        return m;
    }
}
