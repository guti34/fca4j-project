/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.core.natif;

import fr.lirmm.fca4j.algo.AbstractAlgo;
import fr.lirmm.fca4j.algo.Lattice_ParallelCbO;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.natif.impl.NativeLatticeCbO;

/**
 * Point d'entrée public pour l'algorithme Lattice_ParallelCbO.
 *
 * <ul>
 *   <li><b>Natif disponible</b> → {@link NativeLatticeCbO} : pipeline C.</li>
 *   <li><b>Fallback</b> → {@link Lattice_ParallelCbO} standard Java.</li>
 * </ul>
 */
public final class FastLatticeCbO {

    static { NativeBridge.isAvailable(); }

    private FastLatticeCbO() {}

    public static AbstractAlgo create(IBinaryContext context) {
        if (NativeBridge.isAvailable()) {
            return new NativeLatticeCbO(context);
        } else {
            return new Lattice_ParallelCbO(context);
        }
    }

    public static String activeBackend() {
        return NativeBridge.isAvailable()
                ? "native (C)"
                : "java (Lattice_ParallelCbO)";
    }
}