/*
 * closure.h — ClosureDirect : extent, intent, closure
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 */
#ifndef FCA4J_CLOSURE_H
#define FCA4J_CLOSURE_H

#include "context.h"

roaring_bitmap_t *compute_extent(BinaryContext *ctx, roaring_bitmap_t *attributes);
roaring_bitmap_t *compute_intent(BinaryContext *ctx, roaring_bitmap_t *extent);
roaring_bitmap_t *closure(BinaryContext *ctx, roaring_bitmap_t *attrSet);

#endif /* FCA4J_CLOSURE_H */
