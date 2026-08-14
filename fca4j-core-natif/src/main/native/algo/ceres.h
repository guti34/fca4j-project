/*
 * ceres.h — AOC-poset par l'algorithme Ceres, portage natif
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 */
#ifndef FCA4J_CERES_H
#define FCA4J_CERES_H

#include "../core/context.h"

/*
 * Représentation dense du contexte, propre à Ceres : lignes f(o), leurs
 * cardinalités, colonnes g(a). Elle est exposée parce que sa CONSTRUCTION s'est
 * révélée dominer le temps d'exécution sur les contextes à beaucoup d'objets.
 *
 * Mesuré sur ord10shuttle (43500 x 88, 3,83 M de cellules) : passer par un
 * BinaryContext coûtait 64 ms de préparation pour 51 ms d'algorithme — il faut
 * dire que ctx_from_matrix y crée 43588 bitmaps roaring, les remplit cellule par
 * cellule, et que Ceres les reconvertit aussitôt en dense. Deux conversions pour
 * rien. La matrice d'octets alimente directement la forme dense, en une passe.
 *
 * Séparer la construction de l'exécution permet en outre à l'appelant JNI de
 * rendre le tableau Java dès la fin de la construction, sans le retenir pendant
 * tout le calcul.
 */
typedef struct CeresContext CeresContext;

/* Depuis la matrice binaire aplatie row-major (matrix[o * nb_attr + a]).
 * Le tableau n'est lu que pendant l'appel : l'appelant peut le libérer après. */
CeresContext *ceres_ctx_from_matrix(int nb_obj, int nb_attr, const signed char *matrix);

/* Depuis un BinaryContext. Chemin des harnais de vérification, qui doivent
 * partir de la même entrée que les autres algorithmes. */
CeresContext *ceres_ctx_from_binary(const BinaryContext *ctx);

void ceres_ctx_free(CeresContext *cx);

/* Construit l'AOC-poset et le sérialise au format plat de co_to_flat_array.
 * À libérer avec free(). Renvoie NULL et *out_len = 0 en cas d'échec.
 * Ne libère pas cx. */
int *run_ceres_dense(const CeresContext *cx, int *out_len);

/* Convenance : ceres_ctx_from_binary + run_ceres_dense + libération. */
int *run_ceres_flat(BinaryContext *ctx, int *out_len);

#endif /* FCA4J_CERES_H */
