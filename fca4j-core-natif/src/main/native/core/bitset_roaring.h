/*
 * bitset_roaring.h — passerelle entre bitsets denses et roaring_bitmap_t
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 *
 * Séparé de bitset.h à dessein : un algorithme entièrement dense (Ares, Ceres)
 * n'a aucune raison d'entraîner croaring dans son unité de compilation. Seuls
 * les algorithmes qui produisent un ConceptOrder — dont les ensembles sont des
 * roaring — ont besoin de ces conversions.
 *
 * Extrait de latticecbo.c, où ces fonctions vivaient en `static`.
 */
#ifndef FCA4J_BITSET_ROARING_H
#define FCA4J_BITSET_ROARING_H

#include "bitset.h"
#include "../croaring/roaring.h"
#include <stdlib.h>

/* dst (w mots, déjà alloué) reçoit les bits de bm. */
static inline void bs_from_roaring_into(roaring_bitmap_t *bm, aword *dst, int w) {
    bs_zero(dst, w);
    roaring_uint32_iterator_t it;
    roaring_iterator_init(bm, &it);
    while (it.has_value) {
        uint32_t o = it.current_value;
        dst[o >> 6] |= ((aword)1 << (o & 63));
        roaring_uint32_iterator_advance(&it);
    }
}

static inline aword *bs_from_roaring(roaring_bitmap_t *bm, int w) {
    aword *a = (aword *)calloc((size_t)w, sizeof(aword));
    bs_from_roaring_into(bm, a, w);
    return a;
}

/* Ajoute les bits positionnés de a dans un roaring existant. Réutiliser un
 * bitmap déjà créé (le placeholder vide de co_add_concept, par exemple) évite
 * un couple create/free par concept. */
static inline void bs_add_to_roaring(const aword *a, roaring_bitmap_t *bm, int w) {
    for (int wi = 0; wi < w; wi++) {
        aword x = a[wi];
        while (x) {
            int b = AW_CTZ(x);
            roaring_bitmap_add(bm, (uint32_t)(((uint32_t)wi << 6) + (uint32_t)b));
            x &= x - 1;
        }
    }
}

static inline roaring_bitmap_t *bs_to_roaring(const aword *a, int w) {
    roaring_bitmap_t *bm = roaring_bitmap_create();
    bs_add_to_roaring(a, bm, w);
    return bm;
}

#endif /* FCA4J_BITSET_ROARING_H */
