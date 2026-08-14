/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.algo;

import fr.lirmm.fca4j.iset.ISet;

/**
 * Garde-fou de l'invariant sur lequel {@link AOC_poset_Ares} repose au site A6.
 *
 * <p>Contexte. La boucle de suppression appelait {@code max(children)} et
 * {@code min(parents)} avant de rebrancher les enfants d'un concept retiré sous
 * ses parents. Ces deux appels sont des identités : les couvertures d'un même
 * élément sont des antichaînes dans un diagramme transitivement réduit, donc
 * ni l'un ni l'autre ne peut retirer quoi que ce soit. Mesuré sur ord6magic04,
 * ils parcouraient 264 et 192 sommets en moyenne, 6932 fois chacun, pour
 * renvoyer leur propre entrée — de l'ordre de 15 % du temps total passé à
 * prouver qu'il n'y avait rien à écarter. Ils ont été supprimés après
 * vérification sur le balayage exhaustif jusqu'à 12 cellules, 20000 contextes
 * aléatoires et les cinq contextes du banc, sans un seul contre-exemple.
 *
 * <p>Le code dépend donc désormais d'un invariant qu'il n'énonce nulle part.
 * Poser {@code FCA4J_ARES_ASSERT_A6=1} (ou {@code -Dfca4j.ares.assertA6=true})
 * restaure les deux appels et fait échouer l'exécution s'ils retirent quoi que
 * ce soit. À passer sur {@code AocAudit} après toute modification de la boucle
 * de suppression.
 *
 * <p>C'est volontairement une {@link RuntimeException} et non une
 * {@code AssertionError} : {@code AocAudit} n'intercepte que les
 * {@code RuntimeException}, si bien qu'une violation devient une panne
 * ordinaire, que son réducteur minimise automatiquement en un contexte témoin.
 */
public final class AresGuard {

    /** Vrai si la vérification de l'invariant A6 est active. */
    public static final boolean ASSERT_A6 = init();

    private AresGuard() {
    }

    private static boolean init() {
        String env = System.getenv("FCA4J_ARES_ASSERT_A6");
        if (env != null && (env.equals("1") || env.equalsIgnoreCase("true"))) {
            return true;
        }
        return Boolean.getBoolean("fca4j.ares.assertA6");
    }

    /** Levée quand une couverture n'est pas une antichaîne au moment d'une suppression. */
    public static class A6InvariantViolation extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public A6InvariantViolation(String message) {
            super(message);
        }
    }

    /**
     * Vérifie que max et min n'ont rien retiré des deux couvertures.
     *
     * @param children    couverture inférieure du concept supprimé
     * @param maxChildren ses éléments maximaux
     * @param parents     couverture supérieure du concept supprimé
     * @param minParents  ses éléments minimaux
     */
    public static void checkA6(ISet children, ISet maxChildren,
            ISet parents, ISet minParents) {
        if (maxChildren.cardinality() != children.cardinality()) {
            throw new A6InvariantViolation(
                    "max(children) a retire des elements au site A6 : entree=" + children
                    + " sortie=" + maxChildren
                    + " (la couverture inferieure d'un concept supprime n'est pas une antichaine)");
        }
        if (minParents.cardinality() != parents.cardinality()) {
            throw new A6InvariantViolation(
                    "min(parents) a retire des elements au site A6 : entree=" + parents
                    + " sortie=" + minParents
                    + " (la couverture superieure d'un concept supprime n'est pas une antichaine)");
        }
    }
}
