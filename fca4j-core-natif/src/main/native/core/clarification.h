/*
 * clarification.h — Clarification du contexte
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 */
#ifndef FCA4J_CLARIFICATION_H
#define FCA4J_CLARIFICATION_H

#include "context.h"

typedef struct {
    BinaryContext    *clarified;
    roaring_bitmap_t **attr_classes; /* attr_classes[i] = ensemble des attrs originaux pour l'attr clarifié i */
    int               nb_classes;
} ClarificationResult;

ClarificationResult clarify_context(BinaryContext *ctx);
void                clarification_free(ClarificationResult *res);

/* Convertit un ensemble d'indices clarifié vers les indices originaux */
roaring_bitmap_t *convert_to_original(roaring_bitmap_t *set,
                                       roaring_bitmap_t **attr_classes);

#endif /* FCA4J_CLARIFICATION_H */
