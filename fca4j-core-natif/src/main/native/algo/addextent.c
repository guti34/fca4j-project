/*
 * addextent.c — AddExtent : construction du treillis de concepts
 * Portage C de fr.lirmm.fca4j.algo.Lattice_AddExtent
 *
 * Référence validée : fca4j-wasm/fca4j.c (lattice_add_extent / add_extent)
 * Différences par rapport à la version précédente :
 *   - add_extent ne copie PAS newExtent en entrée (c'est l'appelant qui passe
 *     ctx->cols[a] directement ; la copie est faite uniquement à la création
 *     du nouveau concept, à l'intérieur de add_extent)
 *   - Suppression de la liste toDelete : remplacement swap-with-last O(1)
 *     au lieu du décalage O(n²)
 *   - Libération systématique de inter après l'appel récursif (pas de test
 *     de pointeur)
 *   - Signature simplifiée : pas de paramètre BinaryContext* inutile
 *
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 */

#include <stdio.h>
#include <stdlib.h>
#include "addextent.h"
#include "../core/conceptorder.h"
#include "../core/fca4j_common.h"

/* croaring_hardware_support() : 0 = scalaire, 1 = AVX2, 3 = AVX2+AVX512.
 * Déclaré ici pour éviter un warning si non exposé par roaring.h. */
extern int croaring_hardware_support(void);

/* Timing portable (Windows QueryPerformanceCounter / POSIX clock).
 * Activé uniquement si -DFCA4J_TIMING est passé à la compilation. */
#ifdef FCA4J_TIMING
  #ifdef _WIN32
    #include <windows.h>
    static double _ms_now(void) {
        LARGE_INTEGER f, c;
        QueryPerformanceFrequency(&f);
        QueryPerformanceCounter(&c);
        return (double)c.QuadPart * 1000.0 / (double)f.QuadPart;
    }
  #else
    #include <time.h>
    static double _ms_now(void) {
        struct timespec t; clock_gettime(CLOCK_MONOTONIC, &t);
        return t.tv_sec * 1000.0 + t.tv_nsec / 1e6;
    }
  #endif
  #define T_DECL() double _t0 = 0.0
  #define T_START() (_t0 = _ms_now())
  #define T_END(label) \
        fprintf(stderr, "[addextent] %-18s : %8.2f ms\n", label, _ms_now() - _t0)
  #define T_SIMD_REPORT() \
        fprintf(stderr, "[addextent] croaring SIMD   : %d (0=scalaire 1=AVX2 3=AVX512)\n", \
                croaring_hardware_support())
#else
  #define T_DECL()
  #define T_START()
  #define T_END(label)
  #define T_SIMD_REPORT()
#endif

/* ── get_smallest_containing ────────────────────────────────────────────────
 * Descend depuis generator tant qu'un enfant direct contient entièrement
 * extent. Retourne le nœud le plus bas dont l'extent contient encore extent.
 * (= getSmallestContainingConcept en Java)
 * ─────────────────────────────────────────────────────────────────────────── */
static int get_smallest_containing(ConceptOrder *co,
                                    roaring_bitmap_t *extent,
                                    int generator) {
    int found = 1;
    while (found) {
        found = 0;
        roaring_uint32_iterator_t it;
        roaring_iterator_init(co->graph->children[generator], &it);
        while (it.has_value) {
            int child = (int)it.current_value;
            if (roaring_bitmap_is_subset(extent, co->extents[child])) {
                generator = child;
                found = 1;
                break;
            }
            roaring_uint32_iterator_advance(&it);
        }
    }
    return generator;
}

/* ── add_extent (récursif) ──────────────────────────────────────────────────
 * Insère newExtent dans le treillis à partir de generator.
 * Retourne l'id du concept dont l'extent == newExtent après insertion.
 *
 * PROPRIÉTÉ : newExtent est un pointeur emprunté — add_extent n'en prend pas
 * ownership. La copie est faite uniquement si un nouveau concept est créé.
 * ─────────────────────────────────────────────────────────────────────────── */
static int add_extent(ConceptOrder *co,
                      roaring_bitmap_t *extent,
                      int generator) {
    generator = get_smallest_containing(co, extent, generator);

    /* Cas fusion : extent existe déjà */
    if (roaring_bitmap_equals(extent, co->extents[generator]))
        return generator;

    /* Snapshot des enfants avant toute modification du graphe */
    uint32_t card = (uint32_t)roaring_bitmap_get_cardinality(
                        co->graph->children[generator]);
    uint32_t *children_arr = (uint32_t*)malloc(card * sizeof(uint32_t));
    roaring_bitmap_to_uint32_array(co->graph->children[generator], children_arr);

    /* Construction de newChildren */
    IntVec newChildren = IntVec_new();

    for (uint32_t ci = 0; ci < card; ci++) {
        int candidate = (int)children_arr[ci];

        if (!roaring_bitmap_is_subset(extent, co->extents[candidate])) {
            /* Intersection et appel récursif */
            roaring_bitmap_t *inter = roaring_bitmap_and(extent,
                                                          co->extents[candidate]);
            candidate = add_extent(co, inter, candidate);
            roaring_bitmap_free(inter);   /* toujours libéré ici */
        }

        /* Vérifier la dominance dans newChildren — swap-with-last O(1) */
        int addChild = 1;
        for (int j = newChildren.len - 1; j >= 0; j--) {
            int ch = newChildren.data[j];
            if (roaring_bitmap_is_subset(co->extents[candidate], co->extents[ch])) {
                /* ch domine candidate → ne pas ajouter */
                addChild = 0;
                break;
            } else if (roaring_bitmap_is_subset(co->extents[ch],
                                                 co->extents[candidate])) {
                /* candidate domine ch → retirer ch par swap-with-last */
                newChildren.data[j] = newChildren.data[newChildren.len - 1];
                newChildren.len--;
            }
        }

        if (addChild)
            IntVec_push(&newChildren, candidate);
    }
    free(children_arr);

    /* Créer le nouveau concept */
    roaring_bitmap_t *newExt    = roaring_bitmap_copy(extent);
    roaring_bitmap_t *newIntent = roaring_bitmap_copy(co->intents[generator]);
    int nc = co_add_concept(co, newExt, newIntent);

    /* Rewire les arêtes */
    for (int i = 0; i < newChildren.len; i++) {
        co_remove_edge(co, newChildren.data[i], generator);
        co_add_edge(co,    newChildren.data[i], nc);
    }
    co_add_edge(co, nc, generator);

    IntVec_free(&newChildren);
    return nc;
}

/* ── build_lattice — construction commune du ConceptOrder ───────────────────
 * Exécute AddExtent et calcule les rextents réduits. N'appelle PAS
 * co_compute_intents : selon le consommateur, les intents complets sont soit
 * calculés ici (chemin JSON), soit reconstruits côté Java (chemin flat).
 * ─────────────────────────────────────────────────────────────────────────── */
static ConceptOrder *build_lattice(BinaryContext *ctx) {
    T_DECL();
    T_SIMD_REPORT();
    ConceptOrder *co = co_create(ctx);

    /* Concept top : extent = {0..nb_objects-1}, intent = {} */
    roaring_bitmap_t *allObjects = roaring_bitmap_create();
    for (int o = 0; o < ctx->nb_objects; o++)
        roaring_bitmap_add(allObjects, (uint32_t)o);
    roaring_bitmap_t *emptyIntent = roaring_bitmap_create();
    int top = co_add_concept(co, allObjects, emptyIntent);

    /* Insérer chaque attribut par son extent (pointeur direct, pas de copie) */
    T_START();
    for (int a = 0; a < ctx->nb_attributes; a++) {
        int concept = add_extent(co, ctx->cols[a], top);
        roaring_bitmap_add(co->rintents[concept], (uint32_t)a);
    }
    T_END("add_extent loop");

    /* rextents réduits (nécessaires aux deux chemins) */
    T_START();
    co_compute_rextents(co);
    T_END("compute rextents");

#ifdef FCA4J_TIMING
    fprintf(stderr, "[addextent] concepts=%d\n", co->counter);
#endif
    return co;
}

/* ── run_addextent_impl — point d'entrée JSON (compat / debug) ───────────────
 * ─────────────────────────────────────────────────────────────────────────── */
char *run_addextent_impl(BinaryContext *ctx) {
    T_DECL();
    ConceptOrder *co = build_lattice(ctx);

    /* Le JSON nécessite les intents complets */
    T_START();
    co_compute_intents(co);
    T_END("compute intents");

    T_START();
    char *json = co_to_json(co);
    T_END("co_to_json");

    co_free(co);
    return json;
}

/* ── run_addextent_flat — point d'entrée tableau plat (rapide) ───────────────
 * Pas de co_compute_intents : ConceptOrder.populate() reconstruit les intents
 * et extents complets côté Java à partir des réduits.
 * ─────────────────────────────────────────────────────────────────────────── */
int *run_addextent_flat(BinaryContext *ctx, int *out_len) {
    T_DECL();
    ConceptOrder *co = build_lattice(ctx);

    T_START();
    int *flat = co_to_flat_array(co, out_len);
    T_END("co_to_flat_array");

    co_free(co);
    return flat;
}
