/*
 * ares.h — AOC-poset Ares (portage C)
 * Portage de fr.lirmm.fca4j.algo.AOC_poset_Ares
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 */
#ifndef FCA4J_ARES_H
#define FCA4J_ARES_H

#include "../core/context.h"

/*
 * Point d'entrée tableau plat — même format que run_hermes_flat et
 * run_pluton_flat, décrit dans co_to_flat_array : ConceptOrder.populate()
 * reconstruit intents et extents complets côté Java.
 *
 * Pas de variante JSON pour l'instant : le JNI n'utilise que le format plat, et
 * un run_ares_impl() ne servirait qu'au débogage. À ajouter si le besoin
 * apparaît, sur le modèle de run_pluton_impl.
 *
 * L'implémentation suit le découpage du Java : run_ares_flat boucle sur les
 * attributs et appelle ares_step pour chacun. Ce découpage est délibéré — si un
 * usage incrémental apparaît un jour (ARES est le seul algorithme du lot qui
 * sache reprendre un AOC-poset existant), une API à handle persistant se pose
 * par-dessus sans réécrire l'étape.
 */
int *run_ares_flat(BinaryContext *ctx, int *out_len);

#endif /* FCA4J_ARES_H */
