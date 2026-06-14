/*
 * latticecbo.h — Lattice_ParallelCbO : treillis complet par Close-by-One
 * sur les extents. Portage C de fr.lirmm.fca4j.algo.Lattice_ParallelCbO
 * (énumération séquentielle, contexte brut).
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 */
#ifndef FCA4J_LATTICECBO_H
#define FCA4J_LATTICECBO_H

#include <pthread.h>
#ifdef _WIN32
  #include <windows.h>
#else
  #include <unistd.h>
#endif
#include "../core/context.h"

/* JSON ConceptOrder (co_to_json), pour debug/compat. À libérer par l'appelant. */
char *run_latticecbo_impl(BinaryContext *ctx);

/* Tableau d'entiers plat (format co_to_flat_array). Buffer alloué à libérer,
 * longueur écrite dans *out_len ; NULL si échec. co_compute_intents() n'est PAS
 * appelé : ConceptOrder.populate() reconstruit les sets complets côté Java. */
int *run_latticecbo_flat(BinaryContext *ctx, int *out_len);

#endif /* FCA4J_LATTICECBO_H */