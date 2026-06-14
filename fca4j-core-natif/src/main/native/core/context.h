/*
 * context.h — BinaryContext : structure et opérations de base
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 */
#ifndef FCA4J_CONTEXT_H
#define FCA4J_CONTEXT_H

#include "fca4j_common.h"

typedef struct {
    int nb_objects;
    int nb_attributes;
    roaring_bitmap_t **rows;   /* intent de chaque objet */
    roaring_bitmap_t **cols;   /* extent de chaque attribut */
    StrVec obj_names;
    StrVec attr_names;
    char name[256];
} BinaryContext;

BinaryContext *ctx_create(int nb_obj, int nb_attr, const char *name);
void           ctx_set(BinaryContext *ctx, int obj, int attr, bool val);
void           ctx_add_obj_name(BinaryContext *ctx, const char *n);
void           ctx_add_attr_name(BinaryContext *ctx, const char *n);
void           ctx_free(BinaryContext *ctx);

/* Construit un BinaryContext depuis une matrice byte[] Java (row-major) */
BinaryContext *ctx_from_matrix(int nb_obj, int nb_attr,
                                const signed char *matrix,
                                const char *name);

#endif /* FCA4J_CONTEXT_H */
