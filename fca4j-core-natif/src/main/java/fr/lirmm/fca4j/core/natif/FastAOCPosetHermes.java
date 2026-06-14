/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.core.natif;

import fr.lirmm.fca4j.algo.AOC_poset_Hermes;
import fr.lirmm.fca4j.algo.AbstractAlgo;
import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.natif.impl.NativeAOCPosetHermes;

/**
 * Point d'entrée public pour l'AOC-poset Hermes.
 *
 * <ul>
 *   <li><b>Natif disponible</b> → {@link NativeAOCPosetHermes} : pipeline
 *       complet en C.</li>
 *   <li><b>Fallback</b> → {@link AOC_poset_Hermes} standard Java.</li>
 * </ul>
 */
public final class FastAOCPosetHermes {

    static {
        NativeBridge.isAvailable();
    }

    private FastAOCPosetHermes() {}

    /**
     * Crée une instance optimisée de Hermes.
     *
     * @param context contexte binaire d'entrée
     * @return instance prête à exécuter (appeler {@code run()} puis {@code getResult()})
     */
    public static AbstractAlgo create(IBinaryContext context) {
        if (NativeBridge.isAvailable()) {
            return new NativeAOCPosetHermes(context);
        } else {
            return new AOC_poset_Hermes(context);
        }
    }

    /**
     * Indique quel backend est actif.
     */
    public static String activeBackend() {
        return NativeBridge.isAvailable()
                ? "native (C)"
                : "java (AOC_poset_Hermes standard)";
    }
}
