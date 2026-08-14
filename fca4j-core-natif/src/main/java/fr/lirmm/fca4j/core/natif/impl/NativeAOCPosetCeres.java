/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.core.natif.impl;

import java.util.BitSet;
import java.util.Iterator;

import fr.lirmm.fca4j.algo.AbstractAlgo;
import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.natif.NativeBridge;

/**
 * Implémentation native de l'AOC-poset Ceres.
 *
 * <p>Délègue l'intégralité du calcul à {@code run_ceres_flat} via
 * {@link NativeBridge#runCeresFlat}. Le C construit l'ordre dans la structure
 * {@code DynOrder} — extents denses, couvertures en listes d'entiers, marquages
 * horodatés — puis sérialise au format plat commun, consommé tel quel par
 * {@link ConceptOrder#populate}. Aucun JSON, aucune résolution de noms.
 *
 * <p>Comme Ares, le portage ne clarifie pas : Ceres travaille sur le contexte
 * original, donc les indices du tableau plat sont déjà ceux du contexte
 * d'entrée, sans substitution.
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
public class NativeAOCPosetCeres implements AbstractAlgo {

    private final IBinaryContext matrix;
    private ConceptOrder order;

    public NativeAOCPosetCeres(IBinaryContext matrix) {
        this.matrix = matrix;
    }

    @Override
    public String getDescription() {
        return "Ceres";
    }

    @Override
    public ConceptOrder getResult() {
        return order;
    }

    @Override
    public void run() {
        int nObj = matrix.getObjectCount();
        int nAttr = matrix.getAttributeCount();

        byte[] mat = NativeAOCPosetAres.buildMatrix(matrix);
        int[] flat = NativeBridge.runCeresFlat(nObj, nAttr, mat);

        order = new ConceptOrder("AOCposetWithCeres", matrix, getDescription());
        populateFromFlat(order, flat);
    }

    // ── Désérialisation tableau plat → ConceptOrder.populate() ───────────────

    private void populateFromFlat(ConceptOrder co, int[] flat) {
        if (flat == null || flat.length < 2) {
            return;
        }

        int p = 0;
        int N = flat[p++];
        int E = flat[p++];
        if (N <= 0) {
            return;
        }

        int[] concepts = new int[N];
        for (int i = 0; i < N; i++) {
            concepts[i] = i;
        }

        int[] edges = new int[2 * E];
        for (int i = 0; i < 2 * E; i++) {
            edges[i] = flat[p++];
        }

        BitSet[] bitsets = new BitSet[N * 2];
        for (int c = 0; c < N; c++) {
            BitSet rext = new BitSet();
            int cardRe = flat[p++];
            for (int k = 0; k < cardRe; k++) {
                rext.set(flat[p++]);
            }
            bitsets[c * 2] = rext;
            BitSet rint = new BitSet();
            int cardRi = flat[p++];
            for (int k = 0; k < cardRi; k++) {
                rint.set(flat[p++]);
            }
            bitsets[c * 2 + 1] = rint;
        }

        co.populate(concepts, edges, bitsets);
        co.buildExtentIntent();
    }
}
