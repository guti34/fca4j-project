/*
 * lincbo.h — LinCbO : base de Duquenne-Guigues
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 */
#ifndef FCA4J_LINCBO_H
#define FCA4J_LINCBO_H

#include "../core/context.h"

/* Retourne un JSON alloué (à libérer avec free()) */
char *run_lincbo_impl(BinaryContext *ctx);

#endif /* FCA4J_LINCBO_H */
