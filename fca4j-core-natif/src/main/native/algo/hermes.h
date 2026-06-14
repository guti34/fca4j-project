/*
 * hermes.h — AOC-poset Hermes algorithm
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 */
#ifndef FCA4J_HERMES_H
#define FCA4J_HERMES_H

#include "../core/context.h"

/* Retourne un JSON alloué (à libérer avec free()) */
char *run_hermes_impl(BinaryContext *ctx);

/* Variante rapide : tableau d'entiers plat (voir co_to_flat_array pour le
 * format). À libérer avec free(). Écrit la longueur dans *out_len. */
int *run_hermes_flat(BinaryContext *ctx, int *out_len);

#endif /* FCA4J_HERMES_H */
