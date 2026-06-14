/*
 * conceptorder.h — ConceptOrder et DGraph (utilisés par Hermes, AddExtent)
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 */
#ifndef FCA4J_CONCEPTORDER_H
#define FCA4J_CONCEPTORDER_H

#include "context.h"

/* ── DGraph ── */
typedef struct {
    int capacity;
    roaring_bitmap_t **children;
    roaring_bitmap_t **parents;
    roaring_bitmap_t *vertices;
} DGraph;

DGraph *dgraph_create(int cap);
void    dgraph_add_vertex(DGraph *g, int v);
void    dgraph_add_edge(DGraph *g, int child, int parent);
void    dgraph_remove_edge(DGraph *g, int child, int parent);
void    dgraph_free(DGraph *g);

/* ── ConceptOrder ── */
typedef struct {
    int counter;
    BinaryContext *ctx;
    DGraph *graph;
    int capacity;
    roaring_bitmap_t **extents, **intents, **rextents, **rintents;
    roaring_bitmap_t *maximals, *minimals;
} ConceptOrder;

ConceptOrder *co_create(BinaryContext *ctx);
int           co_add_concept(ConceptOrder *co, roaring_bitmap_t *extent, roaring_bitmap_t *intent);
void          co_add_edge(ConceptOrder *co, int child, int parent);
void          co_remove_edge(ConceptOrder *co, int child, int parent);
void          co_compute_rextents(ConceptOrder *co);
void          co_compute_intents(ConceptOrder *co);
void          co_free(ConceptOrder *co);
char         *co_to_json(ConceptOrder *co);
int          *co_to_flat_array(ConceptOrder *co, int *out_len);

#endif /* FCA4J_CONCEPTORDER_H */
