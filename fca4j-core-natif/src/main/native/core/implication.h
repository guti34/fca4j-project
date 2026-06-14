/*
 * implication.h — CImplication et ImplVec
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 */
#ifndef FCA4J_IMPLICATION_H
#define FCA4J_IMPLICATION_H

#include "fca4j_common.h"

typedef struct {
    roaring_bitmap_t *premise;
    roaring_bitmap_t *conclusion;
    roaring_bitmap_t *support;   /* peut être NULL */
    int support_size;
} CImplication;

VEC_DEF(CImplication*, ImplVec)

CImplication *impl_create(roaring_bitmap_t *premise,
                           roaring_bitmap_t *conclusion,
                           int support_size);

CImplication *impl_create_with_support(roaring_bitmap_t *premise,
                                        roaring_bitmap_t *conclusion,
                                        roaring_bitmap_t *support);

void impl_free(CImplication *imp);
void implvec_free_all(ImplVec *v);

#endif /* FCA4J_IMPLICATION_H */
