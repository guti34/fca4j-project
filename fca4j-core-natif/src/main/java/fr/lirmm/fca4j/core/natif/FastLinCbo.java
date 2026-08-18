/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.core.natif;

import fr.lirmm.fca4j.algo.AbstractLinCbo;
import fr.lirmm.fca4j.algo.ClosureStrategy;
import fr.lirmm.fca4j.algo.LinCbO;
import fr.lirmm.fca4j.algo.LinCbOWithPruning;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.natif.impl.NativeLinCbo;
import fr.lirmm.fca4j.util.Chrono;

/**
 * Point d'entrée public du module {@code fca4j-core-natif} pour le calcul de
 * la base de Duquenne-Guigues via LinCbO.
 *
 * <ul>
 *   <li><b>Natif disponible</b> → {@link NativeLinCbo} : moteur unifié C
 *       {@code lincbo_pruning.c}, trois modes d'élagage (voir
 *       {@link NativeLinCbo#MODE_NONE}, {@link NativeLinCbo#MODE_LIFO},
 *       {@link NativeLinCbo#MODE_LCM}).</li>
 *   <li><b>Fallback</b> → implémentation Java standard : {@link LinCbO}
 *       (mode {@code NONE}) ou {@link LinCbOWithPruning} (modes
 *       {@code LIFO} et {@code LCM} — pas d'équivalent Java de l'élagage
 *       LCM, on retombe sur l'élagage LIFO ; la base canonique produite est
 *       identique dans tous les cas, seule la performance en pâtit).</li>
 * </ul>
 */
public final class FastLinCbo {

    static {
        NativeBridge.isAvailable();
    }

    private FastLinCbo() {}

    /**
     * Crée une instance optimisée.
     *
     * @param context               contexte binaire d'entrée
     * @param chrono                chrono optionnel (peut être {@code null})
     * @param closureStrategy       stratégie de calcul intent/extent
     * @param clarify                clarifier le contexte avant calcul
     * @param mode                   {@link NativeLinCbo#MODE_NONE},
     *                                {@link NativeLinCbo#MODE_LIFO} ou
     *                                {@link NativeLinCbo#MODE_LCM}
     */
    public static AbstractLinCbo create(IBinaryContext context, Chrono chrono, ClosureStrategy closureStrategy,
            boolean clarify, int mode) {
        if (NativeBridge.isAvailable()) {
            return new NativeLinCbo(context, chrono, closureStrategy, clarify, mode);
        }
        if (mode == NativeLinCbo.MODE_NONE) {
            return new LinCbO(context, chrono, closureStrategy, clarify);
        }
        // LIFO et LCM retombent tous deux sur LinCbOWithPruning (LIFO) en
        // l'absence de natif : aucune implémentation Java de l'élagage LCM
        // n'existe dans fca4j, et le choix de stratégie d'élagage ne change
        // jamais la base canonique produite, seulement le temps de calcul.
        return new LinCbOWithPruning(context, chrono, closureStrategy, clarify);
    }

    /**
     * Indique quel backend est actif.
     */
    public static String activeBackend() {
        return NativeBridge.isAvailable()
                ? "native (C, lincbo_pruning)"
                : "java (LinCbO / LinCbOWithPruning)";
    }
}
