/*
 * ares.c — AOC-poset Ares (portage C)
 * Portage de fr.lirmm.fca4j.algo.AOC_poset_Ares (version optimisée 2026)
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 *
 * Le portage suit le Java site par site, avec les mêmes noms de sites (A1 à A6,
 * cas 1 à 4), pour que les deux implémentations restent comparables arête par
 * arête. Les écarts sont volontaires et signalés en commentaire :
 *
 *  - pas d'intents complets : le format plat ne les transmet pas et le mode non
 *    incrémental ne les lit jamais pour décider ;
 *  - doNotCheck est un marquage et non un ensemble matérialisé ;
 *  - le tri de l'extension linéaire est un tri par comptage ;
 *  - les extents sont denses.
 *
 * Deux redondances du Java sont conservées telles quelles, parce qu'un portage
 * doit d'abord être fidèle et qu'elles se mesurent ensuite : le cas 4 retire les
 * arêtes (c3, c) puis rebalaye la couverture inférieure de c2 pour retirer les
 * mêmes, et une bonne moitié des retraits d'arêtes portent sur des arêtes
 * inexistantes (55 % mesurés côté Java).
 */
#include "ares.h"
#include "../core/dynorder.h"

typedef struct {
    DynOrder *dyn;
    BinaryContext *ctx;
    int wo, wa;
    bool has_all_a;          /* construire tous les concepts-attributs */
    bool has_all_o;          /* construire tous les concepts-objets */

    /* scratchs d'étape, alloués une fois pour toute l'exécution */
    aword *extA;             /* g(a), copie dense de la colonne */
    aword *extentOfA;        /* objets propres restants pour a */
    aword *inter;            /* extent(c) inter g(a) */
    aword *red;              /* extent réduit du concept visité */
    aword *ec;               /* red inter g(a) */
    aword *tmp;              /* extent réduit de ca */

    int a, cardA;

    unsigned char *in_sub;   /* appartenance à subConceptsOfA */
    IntVec sub_list;
    unsigned char *non_intro;
    IntVec non_intro_list;
    unsigned char *do_not_check;

    IntVec stack;            /* pile DFS pour le marquage des ancêtres */
    IntVec parents, children;/* copies des couvertures pendant la suppression */

    bool is_ca_defined;
    int ca;
    int cap_flags;           /* taille allouée des tableaux de drapeaux */
} AresState;

/* ── utilitaires ──────────────────────────────────────────────────────── */

static void st_ensure_flags(AresState *st, int needed) {
    if (needed <= st->cap_flags) return;
    int newcap = st->cap_flags * 2;
    if (newcap < needed) newcap = needed;
    st->in_sub       = (unsigned char*)realloc(st->in_sub, (size_t)newcap);
    st->non_intro    = (unsigned char*)realloc(st->non_intro, (size_t)newcap);
    st->do_not_check = (unsigned char*)realloc(st->do_not_check, (size_t)newcap);
    memset(st->in_sub + st->cap_flags, 0, (size_t)(newcap - st->cap_flags));
    memset(st->non_intro + st->cap_flags, 0, (size_t)(newcap - st->cap_flags));
    memset(st->do_not_check + st->cap_flags, 0, (size_t)(newcap - st->cap_flags));
    st->cap_flags = newcap;
}

static void st_add_sub(AresState *st, int c) {
    st_ensure_flags(st, c + 1);
    if (!st->in_sub[c]) {
        st->in_sub[c] = 1;
        IntVec_push(&st->sub_list, c);
    }
}

static void st_add_non_intro(AresState *st, int c) {
    st_ensure_flags(st, c + 1);
    if (!st->non_intro[c]) {
        st->non_intro[c] = 1;
        IntVec_push(&st->non_intro_list, c);
    }
}

/*
 * doNotCheck : en Java, getAllParents(c) matérialise l'ensemble des ancêtres
 * pour n'en faire qu'un test d'appartenance. Ici, un simple marquage montant.
 *
 * On ne coupe pas la descente sur un sommet déjà dans doNotCheck : le graphe est
 * modifié entre deux appels du cas 3, donc un ancêtre marqué lors d'un appel
 * précédent n'implique plus que tous ses ancêtres actuels le soient. Le
 * marquage de visite (markA, horodaté) empêche seulement de repasser deux fois
 * au sein du même appel.
 */
static void mark_ancestors_do_not_check(AresState *st, int c) {
    DynOrder *dyn = st->dyn;
    dyn->epochA++;
    st->stack.len = 0;
    st_ensure_flags(st, c + 1);
    dyn->markA[c] = dyn->epochA;
    st->do_not_check[c] = 1;
    IntVec_push(&st->stack, c);
    while (st->stack.len > 0) {
        int v = st->stack.data[--st->stack.len];
        IntVec *up = &dyn->upper[v];
        for (int i = 0; i < up->len; i++) {
            int p = up->data[i];
            if (dyn->markA[p] == dyn->epochA) continue;
            dyn->markA[p] = dyn->epochA;
            st_ensure_flags(st, p + 1);
            st->do_not_check[p] = 1;
            IntVec_push(&st->stack, p);
        }
    }
}

/* ── visite d'un concept ──────────────────────────────────────────────── */

/* Renvoie false pour interrompre l'étape (cas 1 : l'extent de c est exactement
 * g(a), plus rien à faire pour cet attribut — et, comme en Java, la boucle de
 * suppression est alors sautée elle aussi). */
static bool ares_visit(AresState *st, int c) {
    DynOrder *dyn = st->dyn;
    int wo = st->wo;

    st_ensure_flags(st, c + 1);
    if (st->non_intro[c] || st->do_not_check[c]) return true;

    const aword *extC = DYN_EXT(dyn, c);
    int cardC = bs_card(extC, wo);

    /* garde : les deux extents doivent se rencontrer, sauf si l'un est vide */
    if (cardC != 0 && st->cardA != 0 && !bs_intersects(extC, st->extA, wo))
        return true;

    /* une seule intersection pilote tout l'aiguillage */
    bs_and_to(st->inter, extC, st->extA, wo);
    int card_inter = bs_card(st->inter, wo);
    bool c_in_a = (card_inter == cardC);
    bool a_in_c = (card_inter == st->cardA);

    if (c_in_a && a_in_c) {                     /* cas 1 : extent(c) == g(a) */
        bs_set(DYN_RINT(dyn, c), st->a);
        return false;
    }

    if (c_in_a) {                               /* cas 2 : c sous g(a) */
        st_add_sub(st, c);
        dyn_reduced_extent(dyn, c, st->red);
        bs_andnot(st->extentOfA, st->red, wo);
        return true;
    }

    if (a_in_c) {                               /* cas 3 : c au-dessus de g(a) */
        if (!st->is_ca_defined) {
            st->ca = dyn_new_concept(dyn);
            st_ensure_flags(st, st->ca + 1);
            st->is_ca_defined = true;
            bs_set(DYN_RINT(dyn, st->ca), st->a);
            bs_or(DYN_EXT(dyn, st->ca), st->extA, wo);

            dyn_reduced_extent(dyn, c, st->red);
            aword *rextC = DYN_REXT(dyn, c);
            bs_or(rextC, st->red, wo);
            if (bs_intersects(st->red, st->extA, wo)) {   /* ec non vide */
                bs_andnot(rextC, st->extA, wo);
                bool no_rintent = bs_empty(DYN_RINT(dyn, c), st->wa) || !st->has_all_a;
                bool no_rextent = bs_empty(rextC, wo) || !st->has_all_o;
                if (no_rintent && no_rextent) st_add_non_intro(st, c);
            }
            /* A1 : rebrancher les maximaux des sous-concepts de a sous ca */
            dyn_maximal_of(dyn, st->in_sub, st->sub_list.data, st->sub_list.len);
            for (int i = 0; i < dyn->sel_count; i++) {
                int max_sub = dyn->sel[i];
                dyn_add_edge(dyn, max_sub, st->ca);
                dyn_remove_edge(dyn, max_sub, c);
            }
            /* extent(ca) et sa couverture inférieure sont figés jusqu'à la fin
             * de l'étape : son extent réduit est donc invariant et se calcule
             * une seule fois, comme dans le Java. */
            dyn_reduced_extent(dyn, st->ca, st->tmp);
            bs_copy(DYN_REXT(dyn, st->ca), st->tmp, wo);
        }
        /* A2 */
        dyn_add_edge(dyn, st->ca, c);
        IntVec *lo_ca = &dyn->lower[st->ca];
        for (int i = 0; i < lo_ca->len; i++)
            dyn_remove_edge(dyn, lo_ca->data[i], c);
        mark_ancestors_do_not_check(st, c);
        return true;
    }

    /* cas 4 : c et g(a) incomparables */
    if (!st->has_all_o) return true;
    dyn_reduced_extent(dyn, c, st->red);
    if (!bs_intersects(st->red, st->extA, wo)) return true;   /* ec vide */

    bs_and_to(st->ec, st->red, st->extA, wo);
    aword *rextC = DYN_REXT(dyn, c);
    bs_or(rextC, st->red, wo);

    int c2 = dyn_new_concept(dyn);
    st_ensure_flags(st, c2 + 1);
    rextC = DYN_REXT(dyn, c);          /* dyn_new_concept a pu réallouer */
    bs_copy(DYN_EXT(dyn, c2), st->inter, wo);
    bs_copy(DYN_REXT(dyn, c2), st->ec, wo);

    /* A3 */
    dyn_add_edge(dyn, c2, c);
    /* A4 : maximaux de subConceptsOfA parmi les descendants de c, en deux
     * marquages, sans matérialiser ni les descendants ni l'intersection. */
    dyn_maximal_selected_descendants(dyn, c, st->in_sub);
    for (int i = 0; i < dyn->sel_count; i++) {
        int c3 = dyn->sel[i];
        if (dyn_dominated(dyn, c3)) continue;
        dyn_add_edge(dyn, c3, c2);
        dyn_remove_edge(dyn, c3, c);
    }
    st_add_sub(st, c2);
    IntVec *lo_c2 = &dyn->lower[c2];
    for (int i = 0; i < lo_c2->len; i++)
        dyn_remove_edge(dyn, lo_c2->data[i], c);

    bs_andnot(rextC, st->ec, wo);
    {
        bool no_rintent = bs_empty(DYN_RINT(dyn, c), st->wa) || !st->has_all_a;
        if (no_rintent && bs_empty(rextC, wo)) st_add_non_intro(st, c);
    }
    bs_andnot(st->extentOfA, st->ec, wo);
    return true;
}

/* ── suppression des non-introducteurs (site A6) ──────────────────────── */

static void ares_remove_non_introducers(AresState *st) {
    DynOrder *dyn = st->dyn;
    for (int k = 0; k < st->non_intro_list.len; k++) {
        int to_remove = st->non_intro_list.data[k];
        if (!dyn->alive[to_remove]) continue;

        if (!bs_empty(DYN_RINT(dyn, to_remove), st->wa)) {
            IntVec *lo = &dyn->lower[to_remove];
            for (int i = 0; i < lo->len; i++)
                bs_or(DYN_RINT(dyn, lo->data[i]), DYN_RINT(dyn, to_remove), st->wa);
        }

        /* copier les couvertures : elles sont vidées juste après */
        st->parents.len = 0;
        st->children.len = 0;
        for (int i = 0; i < dyn->upper[to_remove].len; i++)
            IntVec_push(&st->parents, dyn->upper[to_remove].data[i]);
        for (int i = 0; i < dyn->lower[to_remove].len; i++)
            IntVec_push(&st->children, dyn->lower[to_remove].data[i]);

        for (int i = 0; i < st->parents.len; i++)
            dyn_remove_edge(dyn, to_remove, st->parents.data[i]);
        for (int i = 0; i < st->children.len; i++)
            dyn_remove_edge(dyn, st->children.data[i], to_remove);

        /*
         * Rebrancher chaque enfant sous chaque parent non déjà atteignable.
         * max(children) et min(parents) ont disparu : ce sont des couvertures
         * d'un même sommet, donc des antichaînes dans un diagramme réduit, et
         * les deux appels ne retiraient jamais rien. Vérifié côté Java sur le
         * balayage exhaustif jusqu'à 12 cellules, 20000 contextes aléatoires et
         * les cinq contextes du banc.
         *
         * L'accessibilité est calculée une fois par enfant, avant d'ajouter la
         * moindre arête pour cet enfant : une arête créée pour un parent ne rend
         * donc pas un autre parent atteignable au sein du même enfant. C'est le
         * comportement du Java, où l'ensemble des ancêtres était figé avant la
         * boucle interne.
         */
        for (int i = 0; i < st->children.len; i++) {
            int child = st->children.data[i];
            dyn_mark_reachable_up(dyn, child, st->parents.data, st->parents.len);
            for (int j = 0; j < st->parents.len; j++) {
                int p = st->parents.data[j];
                if (!dyn_reached(dyn, p)) dyn_add_edge(dyn, child, p);
            }
        }
        dyn_remove_concept(dyn, to_remove);
    }
}

/* ── une étape, un attribut ───────────────────────────────────────────── */

static void ares_step(AresState *st, int a) {
    DynOrder *dyn = st->dyn;
    int wo = st->wo;

    st->a = a;
    st->is_ca_defined = false;
    st->ca = -1;

    /* réinitialiser les drapeaux d'étape : seules les entrées touchées sont
     * remises à zéro, la liste les retient toutes. */
    for (int i = 0; i < st->sub_list.len; i++) st->in_sub[st->sub_list.data[i]] = 0;
    for (int i = 0; i < st->non_intro_list.len; i++) st->non_intro[st->non_intro_list.data[i]] = 0;
    st->sub_list.len = 0;
    st->non_intro_list.len = 0;
    memset(st->do_not_check, 0, (size_t)st->cap_flags);

    /* g(a) en dense */
    bs_zero(st->extA, wo);
    {
        roaring_uint32_iterator_t it;
        roaring_iterator_init(st->ctx->cols[a], &it);
        while (it.has_value) {
            bs_set(st->extA, (int)it.current_value);
            roaring_uint32_iterator_advance(&it);
        }
    }
    st->cardA = bs_card(st->extA, wo);
    bs_copy(st->extentOfA, st->extA, wo);

    /* extension linéaire, du plus spécifique au plus général */
    int count = 0;
    const int *order = dyn_sort_by_extent(dyn, &count);
    /* le tri renvoie un tampon interne, réutilisé par d'autres appels : on
     * travaille sur une copie, car des concepts sont créés pendant la boucle. */
    int *snapshot = (int*)malloc((size_t)(count > 0 ? count : 1) * sizeof(int));
    memcpy(snapshot, order, (size_t)count * sizeof(int));

    for (int i = 0; i < count; i++) {
        if (!dyn->alive[snapshot[i]]) continue;
        if (!ares_visit(st, snapshot[i])) { free(snapshot); return; }
    }
    free(snapshot);

    if (!st->is_ca_defined
        && (st->has_all_a || !bs_empty(st->extentOfA, wo))) {
        int ca = dyn_new_concept(dyn);
        st_ensure_flags(st, ca + 1);
        bs_copy(DYN_EXT(dyn, ca), st->extA, wo);
        bs_set(DYN_RINT(dyn, ca), a);
        st->ca = ca;
        /* A5 */
        dyn_maximal_of(dyn, st->in_sub, st->sub_list.data, st->sub_list.len);
        for (int i = 0; i < dyn->sel_count; i++)
            dyn_add_edge(dyn, dyn->sel[i], ca);
    } else if (st->is_ca_defined && !st->has_all_a
               && bs_empty(st->extentOfA, wo)) {
        st_add_non_intro(st, st->ca);
    }

    ares_remove_non_introducers(st);
}

/* ── point d'entrée ───────────────────────────────────────────────────── */

int *run_ares_flat(BinaryContext *ctx, int *out_len) {
    int nb_obj = ctx->nb_objects, nb_attr = ctx->nb_attributes;
    DynOrder *dyn = dyn_create(nb_obj, nb_attr);

    AresState st;
    memset(&st, 0, sizeof(st));
    st.dyn = dyn;
    st.ctx = ctx;
    st.wo = dyn->wo;
    st.wa = dyn->wa;
    st.has_all_a = true;
    st.has_all_o = true;
    st.extA      = (aword*)calloc((size_t)dyn->wo, sizeof(aword));
    st.extentOfA = (aword*)calloc((size_t)dyn->wo, sizeof(aword));
    st.inter     = (aword*)calloc((size_t)dyn->wo, sizeof(aword));
    st.red       = (aword*)calloc((size_t)dyn->wo, sizeof(aword));
    st.ec        = (aword*)calloc((size_t)dyn->wo, sizeof(aword));
    st.tmp       = (aword*)calloc((size_t)dyn->wo, sizeof(aword));
    st.cap_flags = 256;
    st.in_sub       = (unsigned char*)calloc((size_t)st.cap_flags, 1);
    st.non_intro    = (unsigned char*)calloc((size_t)st.cap_flags, 1);
    st.do_not_check = (unsigned char*)calloc((size_t)st.cap_flags, 1);
    st.sub_list       = IntVec_new();
    st.non_intro_list = IntVec_new();
    st.stack          = IntVec_new();
    st.parents        = IntVec_new();
    st.children       = IntVec_new();

    /* concept sommet : extent et extent réduit = tous les objets */
    int top = dyn_new_concept(dyn);
    st_ensure_flags(&st, top + 1);
    for (int o = 0; o < nb_obj; o++) {
        bs_set(DYN_EXT(dyn, top), o);
        bs_set(DYN_REXT(dyn, top), o);
    }

    for (int a = 0; a < nb_attr; a++) ares_step(&st, a);

    int *flat = dyn_to_flat(dyn, out_len);

    free(st.extA); free(st.extentOfA); free(st.inter);
    free(st.red); free(st.ec); free(st.tmp);
    free(st.in_sub); free(st.non_intro); free(st.do_not_check);
    IntVec_free(&st.sub_list); IntVec_free(&st.non_intro_list);
    IntVec_free(&st.stack); IntVec_free(&st.parents); IntVec_free(&st.children);
    dyn_free(dyn);
    return flat;
}
