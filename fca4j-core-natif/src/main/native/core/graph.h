/*
 * graph.h — AttrGraph : réduction transitive et ordre topologique
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 */
#ifndef FCA4J_GRAPH_H
#define FCA4J_GRAPH_H

#include "fca4j_common.h"

typedef struct {
    int               n;
    roaring_bitmap_t **adj;
} AttrGraph;

AttrGraph *attrgraph_create(int n);
void       attrgraph_free(AttrGraph *g);
void       transitive_reduction(AttrGraph *g);
IntVec     topological_order(AttrGraph *g, roaring_bitmap_t *vertices);

#endif /* FCA4J_GRAPH_H */
