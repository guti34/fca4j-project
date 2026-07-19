/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.core.natif;

import fr.lirmm.fca4j.algo.AbstractAlgo;
import fr.lirmm.fca4j.algo.Lattice_AddExtent;
import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.natif.impl.NativeLatticeAddExtent;

/**
 * Point d'entrée public pour l'algorithme AddExtent.
 *
 * <ul>
 *   <li><b>Natif disponible</b> → {@link NativeLatticeAddExtent} :
 *       pipeline complet en C.</li>
 *   <li><b>Fallback</b> → {@link Lattice_AddExtent} standard Java.</li>
 * </ul>
 */
public final class FastLatticeAddExtent {

    static {
        NativeBridge.isAvailable();
    }

    private FastLatticeAddExtent() {}

    /**
     * Crée une instance optimisée de AddExtent.
     *
     * @param context contexte binaire d'entrée
     * @return instance prête à exécuter (appeler {@code run()} puis {@code getResult()})
     */
    public static AbstractAlgo create(IBinaryContext context) {
        return create(context, false);
    }

    /**
     * @param needFullSets true si l'appelant lit les extents/intents complets
     *                     (RCA, implications, DOT, descripteurs). Faux pour la
     *                     sortie JSON, qui n'utilise que les sets réduits.
     */
    public static AbstractAlgo create(IBinaryContext context, boolean needFullSets) {
        if (NativeBridge.isAvailable()) {
            NativeLatticeAddExtent algo = new NativeLatticeAddExtent(context);
            algo.setNeedFullSets(needFullSets);
            return algo;
        } else {
            return new Lattice_AddExtent(context);   // produit déjà les sets complets
        }
    }
    /**
     * Indique quel backend est actif.
     */
    public static String activeBackend() {
        return NativeBridge.isAvailable()
                ? "native (C)"
                : "java (Lattice_AddExtent standard)";
    }
}
