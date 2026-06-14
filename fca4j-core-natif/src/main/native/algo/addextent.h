/*
 * addextent.h — AddExtent : construction du treillis de concepts
 * Portage C de fr.lirmm.fca4j.algo.Lattice_AddExtent
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 */
#ifndef FCA4J_ADDEXTENT_H
#define FCA4J_ADDEXTENT_H

#include "../core/context.h"

/*
 * run_addextent_impl — construit le treillis complet par l'algorithme AddExtent
 * et retourne un JSON alloué sur le tas (à libérer par l'appelant).
 * Format JSON identique à co_to_json() / run_hermes_impl().
 */
char *run_addextent_impl(BinaryContext *ctx);

/*
 * run_addextent_flat — variante renvoyant un tableau d'entiers plat
 * (voir co_to_flat_array pour le format) au lieu d'un JSON.
 * Bien plus rapide à consommer côté Java : aucun parsing de chaîne ni
 * résolution nom→index.
 *
 * Retourne un buffer alloué (à libérer par l'appelant via free) et écrit
 * sa longueur (nombre d'int) dans *out_len. NULL si échec.
 *
 * Note : co_compute_intents() n'est pas appelé ici — ConceptOrder.populate()
 * reconstruit les intents/extents complets côté Java à partir des réduits.
 */
int *run_addextent_flat(BinaryContext *ctx, int *out_len);

#endif /* FCA4J_ADDEXTENT_H */
