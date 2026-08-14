/*
 * dynorder_test.c — vérification du socle, indépendante d'ARES
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 *
 * Compilation (depuis src/main/native) :
 *   gcc -std=c99 -O2 -o dynorder_test algo/dynorder_test.c core/dynorder.c \
 *       core/context.c croaring/roaring.c -lm
 *
 * Ce test ne couvre que le socle : bitsets, couvertures, tri par comptage,
 * marquages, compaction. Il ne dit rien de la correction d'ARES lui-même, qui
 * reste du ressort d'AocAudit une fois le portage terminé.
 */
#include "../core/dynorder.h"
#include <assert.h>

static int failures = 0;

static void check(int cond, const char *what) {
    if (!cond) { printf("ECHEC : %s\n", what); failures++; }
    else       { printf("  ok   : %s\n", what); }
}

/* Diagramme jouet, 6 objets :
 *        top(0)              extent {0..5}
 *       /      \
 *     a(1)     b(2)          {0,1,2}   {2,3,4}
 *       \      /
 *        c(3)                {2}
 */
static DynOrder *build_toy(void) {
    DynOrder *dyn = dyn_create(6, 4);
    int top = dyn_new_concept(dyn);
    for (int i = 0; i < 6; i++) bs_set(DYN_EXT(dyn, top), i);
    int a = dyn_new_concept(dyn);
    bs_set(DYN_EXT(dyn, a), 0); bs_set(DYN_EXT(dyn, a), 1); bs_set(DYN_EXT(dyn, a), 2);
    int b = dyn_new_concept(dyn);
    bs_set(DYN_EXT(dyn, b), 2); bs_set(DYN_EXT(dyn, b), 3); bs_set(DYN_EXT(dyn, b), 4);
    int c = dyn_new_concept(dyn);
    bs_set(DYN_EXT(dyn, c), 2);
    dyn_add_edge(dyn, a, top);
    dyn_add_edge(dyn, b, top);
    dyn_add_edge(dyn, c, a);
    dyn_add_edge(dyn, c, b);
    return dyn;
}

int main(void) {
    printf("--- bitsets ---\n");
    {
        int w = AW_N(200);
        aword *x = (aword*)calloc(w, sizeof(aword));
        aword *y = (aword*)calloc(w, sizeof(aword));
        bs_set(x, 0); bs_set(x, 63); bs_set(x, 64); bs_set(x, 199);
        check(bs_card(x, w) == 4, "cardinalite a cheval sur les mots");
        check(bs_test(x, 63) && bs_test(x, 64) && !bs_test(x, 65), "bits aux frontieres");
        bs_set(y, 64); bs_set(y, 100);
        check(bs_intersects(x, y, w), "intersection non vide detectee");
        check(bs_card_and(x, y, w) == 1, "cardinalite de l'intersection");
        bs_andnot(x, y, w);
        check(bs_card(x, w) == 3 && !bs_test(x, 64), "soustraction");
        bs_zero(y, w);
        check(bs_empty(y, w) && !bs_intersects(x, y, w), "ensemble vide");
        free(x); free(y);
    }

    printf("--- couvertures ---\n");
    {
        DynOrder *dyn = build_toy();
        check(dyn->lower[0].len == 2, "top a deux enfants");
        check(dyn->upper[3].len == 2, "c a deux parents");
        check(dyn_remove_edge(dyn, 3, 1), "retrait d'une arete existante");
        check(!dyn_remove_edge(dyn, 3, 1), "retrait d'une arete absente signale");
        check(dyn->lower[1].len == 0 && dyn->upper[3].len == 1, "couvertures a jour");
        dyn_free(dyn);
    }

    printf("--- extent reduit ---\n");
    {
        DynOrder *dyn = build_toy();
        int w = dyn->wo;
        aword *out = (aword*)calloc(w, sizeof(aword));
        int nch = dyn_reduced_extent(dyn, 0, out);
        /* top moins (a union b) = {5} */
        check(nch == 2, "nombre d'enfants parcourus");
        check(bs_card(out, w) == 1 && bs_test(out, 5), "rextent du sommet");
        nch = dyn_reduced_extent(dyn, 1, out);
        /* a moins c = {0,1} */
        check(bs_card(out, w) == 2 && bs_test(out, 0) && bs_test(out, 1), "rextent de a");
        free(out);
        dyn_free(dyn);
    }

    printf("--- tri par comptage ---\n");
    {
        DynOrder *dyn = build_toy();
        int cnt = 0;
        const int *ord = dyn_sort_by_extent(dyn, &cnt);
        check(cnt == 4, "quatre concepts vivants");
        int prev = -1, croissant = 1;
        for (int i = 0; i < cnt; i++) {
            int card = bs_card(DYN_EXT(dyn, ord[i]), dyn->wo);
            if (card < prev) croissant = 0;
            prev = card;
        }
        check(croissant, "cardinalites croissantes");
        check(ord[0] == 3, "le plus specifique en tete");
        check(ord[cnt - 1] == 0, "le plus general en queue");
        dyn_free(dyn);
    }

    printf("--- accessibilite ciblee ---\n");
    {
        DynOrder *dyn = build_toy();
        int targets[2] = {0, 2};          /* top et b */
        dyn_mark_reachable_up(dyn, 3, targets, 2);
        check(dyn_reached(dyn, 0) && dyn_reached(dyn, 2), "top et b atteints depuis c");
        int t2[1] = {2};
        dyn_mark_reachable_up(dyn, 1, t2, 1);
        check(!dyn_reached(dyn, 2), "b non atteint depuis a");
        check(dyn_reached(dyn, 0), "top atteint depuis a");
        dyn_free(dyn);
    }

    printf("--- maximaux ---\n");
    {
        DynOrder *dyn = build_toy();
        unsigned char flags[4] = {0, 1, 1, 1};   /* a, b, c */
        int list[3] = {1, 2, 3};
        dyn_maximal_of(dyn, flags, list, 3);
        check(dyn->sel_count == 2, "c est domine par a et b");
        int has_a = 0, has_b = 0;
        for (int i = 0; i < dyn->sel_count; i++) {
            if (dyn->sel[i] == 1) has_a = 1;
            if (dyn->sel[i] == 2) has_b = 1;
        }
        check(has_a && has_b, "a et b retenus");

        /* max des selectionnes sous top : idem */
        dyn_maximal_selected_descendants(dyn, 0, flags);
        int kept = 0;
        for (int i = 0; i < dyn->sel_count; i++)
            if (!dyn_dominated(dyn, dyn->sel[i])) kept++;
        check(dyn->sel_count == 3, "trois selectionnes sous le sommet");
        check(kept == 2, "c ecarte comme domine");

        /* sous a : seuls a et c sont descendants, a domine c */
        dyn_maximal_selected_descendants(dyn, 1, flags);
        kept = 0;
        for (int i = 0; i < dyn->sel_count; i++)
            if (!dyn_dominated(dyn, dyn->sel[i])) kept++;
        check(dyn->sel_count == 2 && kept == 1, "sous a : seul a est maximal");
        dyn_free(dyn);
    }

    printf("--- suppression et compaction ---\n");
    {
        DynOrder *dyn = build_toy();
        bs_set(DYN_RINT(dyn, 1), 0);
        bs_set(DYN_RINT(dyn, 2), 1);
        bs_set(DYN_REXT(dyn, 3), 2);
        /* retirer b : isoler puis supprimer */
        dyn_remove_edge(dyn, 2, 0);
        dyn_remove_edge(dyn, 3, 2);
        dyn_remove_concept(dyn, 2);
        check(dyn->nb_alive == 3, "trois concepts vivants");

        int len = 0;
        int *flat = dyn_to_flat(dyn, &len);
        check(flat != NULL, "serialisation produite");
        check(flat[0] == 3, "trois concepts dans le tableau plat");
        /* aretes restantes : a->top, c->a  */
        check(flat[1] == 2, "deux aretes");
        int maxid = -1;
        for (int i = 0; i < 2 * flat[1]; i++) {
            int v = flat[2 + i];
            if (v > maxid) maxid = v;
            if (v < 0) { printf("ECHEC : identifiant negatif apres compaction\n"); failures++; break; }
        }
        check(maxid < flat[0], "identifiants renumerotes sans trou");
        free(flat);
        dyn_free(dyn);
    }

    printf("\n%s\n", failures == 0 ? "socle : tout est vert" : "socle : des tests ont echoue");
    return failures == 0 ? 0 : 1;
}
