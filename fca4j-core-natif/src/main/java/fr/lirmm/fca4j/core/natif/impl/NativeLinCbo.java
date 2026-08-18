/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.core.natif.impl;

import java.util.ArrayList;
import java.util.Iterator;

import fr.lirmm.fca4j.algo.AbstractLinCbo;
import fr.lirmm.fca4j.algo.ClosureStrategy;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.core.natif.NativeBridge;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.util.Chrono;

/**
 * Sous-classe de {@link AbstractLinCbo} déléguant le calcul de la base de
 * Duquenne-Guigues au moteur unifié C {@code lincbo_pruning.c} via
 * {@link NativeBridge#runLincboPruningFlat} — trois modes d'élagage
 * disponibles côté C (voir {@link #MODE_NONE}, {@link #MODE_LIFO},
 * {@link #MODE_LCM}).
 *
 * <p>Ne remplace que {@link #init()} et {@link #_LinCbO()} : la gestion de la
 * clarification (option {@code -clarify}) reste entièrement assurée par
 * {@link AbstractLinCbo#run()}, qui l'applique de façon transparente autour
 * de ces deux méthodes — aucun code spécifique n'est nécessaire ici pour en
 * bénéficier.</p>
 *
 * <p>Comme {@code NativeDBaseV24}, le C ne renvoie que la <i>cardinalité</i>
 * du support de chaque implication, pas les objets qui le composent : le
 * support réel ({@link ISet}) est recalculé côté Java.
 *
 * <p><b>Important :</b> ce recalcul se fait via {@link #computeExtentDirect},
 * une intersection directe contre le contexte — <i>pas</i> via
 * {@link AbstractLinCbo#closure} / {@code computeIntExt.closure(...)}. Un
 * appel {@code closure(fermeture, premise, null, null)} a été essayé en
 * premier lieu, sur la conviction que passer {@code null} pour
 * {@code lastAttrSet}/{@code lastExtent} était une façon sûre et générique de
 * demander un calcul « à froid » — cette convention est bien celle utilisée
 * par {@code LinCbOWithPruning._LinCbO()} pour son tout premier appel
 * récursif. Mais elle n'est valide que pour les {@link ClosureStrategy} qui
 * recalculent réellement l'extent à partir de {@code attrSet} dans ce cas
 * ({@link fr.lirmm.fca4j.algo.ClosureDirect} le fait). {@code
 * ClosureWithHistory}, purement incrémentale, ne s'appuie QUE sur {@code
 * lastAttrSet}/{@code lastExtent} pour restreindre l'extent : avec {@code
 * lastAttrSet == null} elle renvoie tout le contexte sans jamais regarder
 * {@code attrSet}, un cas qui, dans l'algorithme Java réel, ne se produit
 * qu'au tout premier appel où la prémisse est réellement vide. Appelée ici
 * avec une prémisse non vide, elle renvoyait donc l'extent complet pour
 * chaque implication (bug constaté : support = nombre total d'objets pour
 * toutes les règles avec {@code -c WITH_HISTORY}). {@link #computeExtentDirect}
 * contourne complètement la stratégie branchée par l'utilisateur — elle n'a
 * jamais été conçue pour ce calcul ponctuel, hors boucle de récursion — et
 * reste donc correcte quel que soit {@code -c}.</p>
 */
public class NativeLinCbo extends AbstractLinCbo {

    /** Miroir de LinCboPruneMode (algo/lincbo_pruning.h) : sans élagage. */
    public static final int MODE_NONE = 0;
    /** Élagage LIFO — LinCbOWithPruning.java / cboMemPruning.cpp. */
    public static final int MODE_LIFO = 1;
    /** Élagage « façon LCM » — cboMemLCMPruning.cpp, plus agressif. */
    public static final int MODE_LCM = 2;

    private final int nativeMode;

    public NativeLinCbo(IBinaryContext binCtx, Chrono chrono, ClosureStrategy computeIntExtStrategy,
            boolean clarify, int mode) {
        super(binCtx, chrono, computeIntExtStrategy, clarify);
        this.nativeMode = mode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void init() {
        implications = new ArrayList<>();
    }

    /**
     * Remplace le pipeline Java complet par un unique appel C.
     * {@inheritDoc}
     */
    @Override
    protected void _LinCbO() throws InterruptedException {
        int nObj = matrix.getObjectCount();
        int nAttr = matrix.getAttributeCount();
        byte[] mtx = buildMatrix(matrix);

        int[] flat = NativeBridge.runLincboPruningFlat(nObj, nAttr, mtx, nativeMode);
        parseFlat(flat);
    }

    /**
     * Désérialise le tableau plat produit par {@code run_lincbo_pruning_flat}
     * (même format auto-descriptif que {@code run_dbasis_flat}) en
     * construisant directement les {@link Implication} à partir des indices :
     * aucun JSON, aucune résolution de noms, aucune allocation de String.
     */
    private void parseFlat(int[] flat) {
        if (flat == null || flat.length < 1) {
            return;
        }
        int nAttr = matrix.getAttributeCount();
        int p = 0;
        int m = flat[p++];
        for (int i = 0; i < m; i++) {
            ISet premise = factory.createSet(nAttr);
            int cardP = flat[p++];
            for (int k = 0; k < cardP; k++) {
                premise.add(flat[p++]);
            }

            ISet conclusion = factory.createSet(nAttr);
            int cardC = flat[p++];
            for (int k = 0; k < cardC; k++) {
                conclusion.add(flat[p++]);
            }

            // Cardinalité renvoyée par le C — non utilisée directement : on
            // recalcule le vrai support (ISet) ci-dessous.
            p++; // consomme la valeur sans la garder

            // Même convention que NativeDBaseV24 : le constructeur
            // Implication(premise, conclusion, support) fait conclusion \ premise
            // (mathématiquement un no-op ici, la conclusion venant du C étant
            // déjà privée de la prémisse, mais conservé pour la cohérence).
            conclusion.addAll(premise);

            ISet support = computeExtentDirect(premise);

            implications.add(new Implication(premise, conclusion, support));
        }
    }

    /**
     * Calcule l'extent (support) d'un ensemble d'attributs directement contre
     * {@link #matrix}, sans passer par {@link #computeIntExt} — voir la note
     * en tête de classe sur pourquoi la {@link ClosureStrategy} branchée par
     * l'utilisateur n'est pas fiable pour cet usage ponctuel. Même logique
     * que {@code ClosureDirect.computeExtent} (les deux branches, selon que
     * les attributs ou les objets sont moins nombreux).
     */
    private ISet computeExtentDirect(ISet attrSet) {
        int nObj = matrix.getObjectCount();
        int nAttr = matrix.getAttributeCount();
        ISet extent = factory.createSet(nObj);
        if (nAttr < nObj) {
            extent.fill(nObj);
            for (Iterator<Integer> it = attrSet.iterator(); it.hasNext();) {
                extent.retainAll(matrix.getExtent(it.next()));
            }
        } else {
            for (int o = 0; o < nObj; o++) {
                if (matrix.getIntent(o).containsAll(attrSet)) {
                    extent.add(o);
                }
            }
        }
        return extent;
    }

    private static byte[] buildMatrix(IBinaryContext ctx) {
        int nObj = ctx.getObjectCount();
        int nAttr = ctx.getAttributeCount();
        byte[] m = new byte[nObj * nAttr];
        for (int o = 0; o < nObj; o++) {
            ISet intent = ctx.getIntent(o);
            for (Iterator<Integer> it = intent.iterator(); it.hasNext();) {
                int a = it.next();
                m[o * nAttr + a] = 1;
            }
        }
        return m;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        switch (nativeMode) {
        case MODE_LIFO:
            return "NativeLinCbo(LIFO)";
        case MODE_LCM:
            return "NativeLinCbo(LCM)";
        default:
            return "NativeLinCbo(NONE)";
        }
    }
}
