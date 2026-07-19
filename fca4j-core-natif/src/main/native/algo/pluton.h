/*
 * pluton.h — AOC-poset Pluton (portage C)
 * Portage de fr.lirmm.fca4j.algo.AOC_poset_Pluton
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 */
#ifndef FCA4J_PLUTON_H
#define FCA4J_PLUTON_H

#include "../core/context.h"
#include "../core/conceptorder.h"

/* Point d'entrée JSON (compat / debug). */
char *run_pluton_impl(BinaryContext *ctx);

/* Point d'entrée tableau plat (rapide) — même format que run_hermes_flat :
 * ConceptOrder.populate() reconstruit intents/extents complets côté Java. */
int  *run_pluton_flat(BinaryContext *ctx, int *out_len);

#endif /* FCA4J_PLUTON_H */
