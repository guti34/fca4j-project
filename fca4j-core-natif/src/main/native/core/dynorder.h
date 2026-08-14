/*
 * dynorder.h — ordre de concepts mutable à couvertures incrémentales
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 *
 * Anciennement algo/aresorder.h. Le module a été remonté dans core/ et
 * débarrassé de sa couche bitset (désormais core/bitset.h) le jour où un
 * deuxième algorithme en a eu besoin : Ceres construit lui aussi son ordre
 * incrémentalement, en interrogeant les couvertures pendant la construction.
 *
 * Pourquoi une structure distincte de ConceptOrder :
 *
 *  - ConceptOrder est append-only et ne matérialise ses couvertures qu'à la fin.
 *    Ares *supprime* des concepts et retire des arêtes en cours de route ; Ceres
 *    lit |couverture supérieure(c)| et parcourt la couverture inférieure de
 *    chaque concept déjà inséré, à chaque insertion.
 *  - Le profilage Java a montré que le coût de ces algorithmes se concentre sur
 *    le parcours des couvertures et sur des opérations d'extents dont le coût
 *    suit (1 + nb_enfants) x largeur_en_mots. C'est le régime où CRoaring est le
 *    plus défavorable : indirection par conteneur, itérateurs lourds, et un coût
 *    par opération sans rapport avec la densité réelle des extents.
 *
 * D'où : extents denses en uint64, couvertures en listes d'entiers, marquages
 * par tableaux horodatés. ConceptOrder n'est pas touché, donc Hermes, AddExtent
 * et Pluton gardent exactement le comportement mesuré aujourd'hui.
 *
 * Les intents complets ne sont pas stockés : le format plat ne transmet que
 * rextent, rintent et les arêtes, et ni Ares (mode non incrémental) ni Ceres
 * n'utilisent l'intent complet pour décider quoi que ce soit. C'est |A| bits par
 * concept et toutes les unions associées en moins.
 */
#ifndef FCA4J_DYNORDER_H
#define FCA4J_DYNORDER_H

#include "context.h"
#include "bitset.h"

/* ── ordre de concepts mutable ────────────────────────────────────────── */

typedef struct {
    int nb_obj, nb_attr;
    int wo, wa;              /* largeurs en mots : objets, attributs */

    int n;                   /* nombre d'identifiants attribués */
    int cap;                 /* capacité allouée */

    aword *ext;              /* cap * wo : extents */
    aword *rext;             /* cap * wo : extents réduits */
    aword *rint;             /* cap * wa : intents réduits */

    IntVec *lower, *upper;   /* couvertures, cap entrées */
    unsigned char *alive;    /* suppression logique */
    int nb_alive;

    /* marquages horodatés : pas de remise à zéro entre appels. Les époques
     * démarrent à 1 et les tableaux à 0, donc une case fraîchement allouée ne
     * peut pas se faire passer pour marquée. */
    int *markA, *markB;
    int epochA, epochB;

    int *stack, stack_cap;   /* pile DFS explicite */
    int *sel,   sel_cap, sel_count;  /* sortie des sélections */

    int *sort_buf;           /* sortie du tri, cap entrées */
    int *bucket;             /* comptage, nb_obj + 2 entrées */
} DynOrder;

#define DYN_EXT(dyn, c)  ((dyn)->ext  + (size_t)(c) * (dyn)->wo)
#define DYN_REXT(dyn, c) ((dyn)->rext + (size_t)(c) * (dyn)->wo)
#define DYN_RINT(dyn, c) ((dyn)->rint + (size_t)(c) * (dyn)->wa)

DynOrder *dyn_create(int nb_obj, int nb_attr);
void       dyn_free(DynOrder *dyn);

/* Crée un concept vide (extent, rextent, rintent à zéro) et renvoie son id. */
int  dyn_new_concept(DynOrder *dyn);

void dyn_add_edge(DynOrder *dyn, int lower, int upper);
/* Renvoie true si l'arête existait réellement. Le portage garde ce retour
 * parce que les mesures Java ont montré que 55 % des retraits portaient sur
 * des arêtes inexistantes : le chiffre est à confirmer côté C. */
bool dyn_remove_edge(DynOrder *dyn, int lower, int upper);
/* Suppression logique : le concept doit déjà être isolé (couvertures vides). */
void dyn_remove_concept(DynOrder *dyn, int c);

/* Extension linéaire des concepts vivants, du plus spécifique au plus général.
 * Tri par comptage sur la cardinalité de l'extent : O(n + |O|) au lieu du
 * O(n log n) du sort Java, refait à chaque attribut. Le résultat pointe sur un
 * tampon interne, valide jusqu'au prochain appel. */
const int *dyn_sort_by_extent(DynOrder *dyn, int *out_count);

/* rext_out = extent(c) privé des extents des enfants directs.
 * Renvoie le nombre d'enfants parcourus (le facteur qui domine ce coût). */
int dyn_reduced_extent(DynOrder *dyn, int c, aword *rext_out);

/* Marque les ancêtres de src (réflexif) jusqu'à avoir rencontré toutes les
 * cibles, puis s'arrête. Après l'appel, dyn_reached(dyn, v) répond pour tout v
 * de targets. */
void dyn_mark_reachable_up(DynOrder *dyn, int src, const int *targets, int ntargets);
static inline bool dyn_reached(const DynOrder *dyn, int v) {
    return dyn->markA[v] == dyn->epochA;
}

/* Éléments maximaux de l'ensemble décrit par sel_flags parmi les concepts
 * listés dans sel_list. Résultat dans dyn->sel / dyn->sel_count. */
void dyn_maximal_of(DynOrder *dyn, const unsigned char *sel_flags,
                   const int *sel_list, int list_count);

/* Éléments maximaux de sel_flags inter descendants(c), descendants pris
 * réflexivement. Résultat dans dyn->sel / dyn->sel_count ; l'entrée i est
 * maximale ssi dyn_dominated(dyn, dyn->sel[i]) est faux. */
void dyn_maximal_selected_descendants(DynOrder *dyn, int c,
                                     const unsigned char *sel_flags);
static inline bool dyn_dominated(const DynOrder *dyn, int v) {
    return dyn->markB[v] == dyn->epochB;
}

/* Sérialisation au format plat de co_to_flat_array, avec compaction des
 * concepts supprimés. À libérer avec free(). */
int *dyn_to_flat(DynOrder *dyn, int *out_len);

#endif /* FCA4J_DYNORDER_H */
