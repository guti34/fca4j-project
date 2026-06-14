/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.core.natif;

import fr.lirmm.fca4j.algo.DBaseV24;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.natif.impl.NativeDBaseV24;

/**
 * Point d'entrée public du module {@code fca4j-core-natif}.
 *
 * <ul>
 *   <li><b>Natif disponible</b> → {@link NativeDBaseV24} :
 *       pipeline complet en C avec pthread natif.</li>
 *   <li><b>Fallback</b> → {@link DBaseV24} standard Java.</li>
 * </ul>
 */
public final class FastDBaseV24 {

    static {
        NativeBridge.isAvailable();
    }

    private FastDBaseV24() {}

    /**
     * Crée une instance optimisée.
     *
     * @param context    contexte binaire d'entrée
     * @param minSupport seuil de support (0 = pas de filtre)
     * @param maxThreads 1 = mono, 0 = auto, >1 = fixé
     */
    public static DBaseV24 create(IBinaryContext context, int minSupport, int maxThreads) {
        if (NativeBridge.isAvailable()) {
            return new NativeDBaseV24(context, minSupport, maxThreads);
        } else {
            return new DBaseV24(context, minSupport, maxThreads);
        }
    }

    /**
     * Indique quel backend est actif.
     */
    public static String activeBackend() {
        return NativeBridge.isAvailable()
                ? "native (C + pthread)"
                : "java (DBaseV24 standard)";
    }
}
