/*
 * dbasis.h — DBase : pipeline complet (clarification → E-basis)
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 */
#ifndef FCA4J_DBASIS_H
#define FCA4J_DBASIS_H

#include "../core/context.h"
#include "../core/implication.h"

/*
 * Calcule la D-basis / E-basis complète depuis un BinaryContext.
 * Retourne un JSON alloué (à libérer avec free()).
 * max_threads : 1 = mono, 0 = auto, >1 = fixé.
 */
char *run_dbasis_impl(BinaryContext *orig_ctx, int min_support, int max_threads);

#endif /* FCA4J_DBASIS_H */
