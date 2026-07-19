/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.core.natif;

import fr.lirmm.fca4j.algo.AOC_poset_Pluton;
import fr.lirmm.fca4j.algo.AbstractAlgo;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.natif.impl.NativeAOCPosetPluton;

/**
 * Point d'entrée public pour l'AOC-poset Pluton.
 *
 * <ul>
 *   <li><b>Natif disponible</b> → {@link NativeAOCPosetPluton} : pipeline
 *       complet en C (clarification + ordre + substitution).</li>
 *   <li><b>Fallback</b> → {@link AOC_poset_Pluton} standard Java.</li>
 * </ul>
 */
public final class FastAOCPosetPluton {

    static {
        NativeBridge.isAvailable();
    }

    private FastAOCPosetPluton() {}

    /**
     * Crée une instance optimisée de Pluton.
     *
     * @param context contexte binaire d'entrée
     * @return instance prête à exécuter (appeler {@code run()} puis {@code getResult()})
     */
    public static AbstractAlgo create(IBinaryContext context) {
        if (NativeBridge.isAvailable()) {
            return new NativeAOCPosetPluton(context);
        } else {
            return new AOC_poset_Pluton(context);
        }
    }

    /**
     * Indique quel backend est actif.
     */
    public static String activeBackend() {
        return NativeBridge.isAvailable()
                ? "native (C)"
                : "java (AOC_poset_Pluton standard)";
    }
}
