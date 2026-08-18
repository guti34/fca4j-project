/*
 * lincbo_pruning.h — base de Duquenne-Guigues, moteur unifié LinCbO
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 *
 * Remplace/complète lincbo.c : un seul moteur itératif (pile explicite,
 * pas de récursion C) paramétré par la stratégie d'élagage, au lieu de
 * dupliquer LinClosureRC/Step une fois par variante. Les ensembles
 * d'attributs (B, Z, D, Bo, la fermeture) sont des bitsets denses
 * (core/bitset.h), pas des roaring_bitmap_t : l'univers est celui des
 * attributs, typiquement petit et dense — voir la note dans
 * core/bitset_roaring.h sur la séparation dense/roaring déjà pratiquée
 * par ares.c/ceres.c. croaring n'intervient qu'au moment de calculer
 * l'extent (le support, univers des objets), comme le fait déjà closure.c.
 *
 * Trois modes, un seul code source :
 *   - LINCBO_PRUNE_NONE : LinCbO tel quel (équivalent à lincbo.c existant,
 *     utile comme oracle de non-régression).
 *   - LINCBO_PRUNE_LIFO : élagage tel que LinCbOWithPruning.java /
 *     cboMemPruning.cpp (pile LIFO, condition de coupe result<i).
 *   - LINCBO_PRUNE_LCM  : élagage « façon LCM » de cboMemLCMPruning.cpp
 *     (pile triée par insertion, condition de coupe result!=-1, plus
 *     agressive). Sans équivalent dans fca4j avant ce fichier.
 */
#ifndef FCA4J_LINCBO_PRUNING_H
#define FCA4J_LINCBO_PRUNING_H

#include "../core/context.h"
#include "../core/implication.h"

typedef enum {
    LINCBO_PRUNE_NONE = 0,
    LINCBO_PRUNE_LIFO = 1,
    LINCBO_PRUNE_LCM  = 2
} LinCboPruneMode;

/* Calcule la base de Duquenne-Guigues. ImplVec de CImplication* (voir
 * implication.h) ; l'appelant libère avec implvec_free_all(). */
ImplVec run_lincbo_pruning(BinaryContext *ctx, LinCboPruneMode mode);

/* Variante pratique : JSON alloué (à libérer avec free()), même format que
 * run_lincbo_impl (lincbo.c) pour rester un remplacement direct côté JNI. */
char *run_lincbo_pruning_json(BinaryContext *ctx, LinCboPruneMode mode);

/* Variante rapide pour le pont JNI : tableau d'entiers plat, indices
 * uniquement (aucun nom, aucune String côté Java), même convention que
 * run_dbasis_flat (algo/dbasis.h) :
 *   [0] = M (nombre d'implications)
 *   puis pour chaque implication :
 *     [cardP] p0 p1 ... p(cardP-1)   (prémisse)
 *     [cardC] c0 c1 ... c(cardC-1)   (conclusion, déjà privée de la prémisse)
 *     [support]                     (cardinalité du support, pas ses objets)
 * *out_len reçoit la taille totale du tableau retourné (alloué, à libérer
 * avec free()). */
int *run_lincbo_pruning_flat(BinaryContext *ctx, LinCboPruneMode mode, int *out_len);

#endif /* FCA4J_LINCBO_PRUNING_H */
