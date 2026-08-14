/*
 * ceres.c — AOC-poset par l'algorithme Ceres, portage natif
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 *
 * Portage de AOC_poset_Ceres.java, à l'issue de la campagne d'optimisation qui
 * a fait passer le corpus de 5961 à 639 ms. Les structures adoptées côté Java
 * pendant cette campagne — plages de parents contiguës, listes d'enfants par
 * concept, marquage horodaté sur tableaux plats — sont celles de DynOrder : le
 * portage écrit dans une structure déjà éprouvée plutôt que d'en inventer une.
 *
 * Ce que le profilage Java a établi, et qui a dicté ce fichier :
 *
 *  - le parcours de Classify domine (94 % sur chess, 77 M d'enfants visités),
 *    et son coût est celui du déplacement en mémoire, pas du calcul ;
 *  - l'extent à insérer est fixe pendant tout un appel à Classify, donc ses
 *    mots occupés se relèvent une fois (bs_subset_sparse) ;
 *  - WorkOnLeftPart2 pèse ensuite, dominé par un tri qu'un tri par comptage
 *    règle en O(n + |A|).
 *
 * DynOrder ne stocke pas les intents complets : le format plat ne transmet que
 * rextent et rintent, et Java reconstruit le reste. Ceres, lui, a besoin de
 * l'intent complet en cours de route (accumulation sur les ancêtres, test
 * f(o) == intent). Ces intents vivent donc ici, dans PreConcept, et ne sont
 * jamais écrits dans l'ordre.
 *
 * Le contexte est converti une fois en représentation dense : les lignes f(o),
 * leurs cardinalités, et les colonnes g(a). Toutes les opérations d'ensemble de
 * l'algorithme portent ensuite sur des mots de 64 bits contigus.
 */

#include "ceres.h"
#include "../core/dynorder.h"
#include "../core/bitset.h"
#include <stdlib.h>
#include <string.h>

/* Un pré-concept manipulé avant insertion. Les quatre pointeurs désignent soit
 * des tampons possédés par l'algorithme, soit — dans le cas particulier du
 * sommet, voir plus bas — des zones appartenant à DynOrder. `owned` distingue
 * les deux pour la libération. */
typedef struct {
    aword *ext, *intent, *rext, *rint;
    int extCard;
    int owned;
} PreConcept;

/* ── contexte dense ────────────────────────────────────────────────────── */

/* Les largeurs DOIVENT coïncider avec celles que dyn_create calcule, faute de
 * quoi les extents écrits ici déborderaient des tampons de l'ordre — sans que
 * rien ne le signale. D'où ces deux fonctions plutôt que des AW_N dispersés. */
void ceres_ctx_free(CeresContext *cx);

static inline int ceres_wo(int nb_obj)  { return AW_N(nb_obj  > 0 ? nb_obj  : 1); }
static inline int ceres_wa(int nb_attr) { return AW_N(nb_attr > 0 ? nb_attr : 1); }

struct CeresContext {
    int nb_obj, nb_attr;
    int wo, wa;
    aword *objIntent;      /* nb_obj * wa : f(o) */
    int   *objIntentCard;  /* |f(o)| */
    aword *attrExtent;     /* nb_attr * wo : g(a) */
};

static CeresContext *ceres_ctx_alloc(int nb_obj, int nb_attr) {
    CeresContext *cx = (CeresContext*)calloc(1, sizeof(CeresContext));
    if (!cx) {
        return NULL;
    }
    cx->nb_obj = nb_obj;
    cx->nb_attr = nb_attr;
    cx->wo = ceres_wo(nb_obj);
    cx->wa = ceres_wa(nb_attr);
    cx->objIntent = (aword*)calloc((size_t)(nb_obj > 0 ? nb_obj : 1) * cx->wa, sizeof(aword));
    cx->objIntentCard = (int*)calloc((size_t)(nb_obj > 0 ? nb_obj : 1), sizeof(int));
    cx->attrExtent = (aword*)calloc((size_t)(nb_attr > 0 ? nb_attr : 1) * cx->wo, sizeof(aword));
    if (!cx->objIntent || !cx->objIntentCard || !cx->attrExtent) {
        ceres_ctx_free(cx);
        return NULL;
    }
    return cx;
}

void ceres_ctx_free(CeresContext *cx) {
    if (!cx) {
        return;
    }
    free(cx->objIntent);
    free(cx->objIntentCard);
    free(cx->attrExtent);
    free(cx);
}

CeresContext *ceres_ctx_from_matrix(int nb_obj, int nb_attr, const signed char *matrix) {
    CeresContext *cx = ceres_ctx_alloc(nb_obj, nb_attr);
    if (!cx) {
        return NULL;
    }
    /* Une seule passe sur la matrice, ligne par ligne : l'accès est séquentiel
     * des deux côtés pour f(o), dispersé seulement pour g(a). Aucune allocation
     * dans la boucle, à comparer aux |G| + |A| bitmaps que créait le chemin
     * précédent. */
    for (int o = 0; o < nb_obj; o++) {
        const signed char *row = matrix + (size_t)o * nb_attr;
        aword *fo = cx->objIntent + (size_t)o * cx->wa;
        int card = 0;
        for (int a = 0; a < nb_attr; a++) {
            if (row[a]) {
                bs_set(fo, a);
                bs_set(cx->attrExtent + (size_t)a * cx->wo, o);
                card++;
            }
        }
        cx->objIntentCard[o] = card;
    }
    return cx;
}

CeresContext *ceres_ctx_from_binary(const BinaryContext *ctx) {
    CeresContext *cx = ceres_ctx_alloc(ctx->nb_objects, ctx->nb_attributes);
    if (!cx) {
        return NULL;
    }
    for (int o = 0; o < cx->nb_obj; o++) {
        aword *row = cx->objIntent + (size_t)o * cx->wa;
        roaring_uint32_iterator_t it;
        roaring_iterator_init(ctx->rows[o], &it);
        while (it.has_value) {
            bs_set(row, (int)it.current_value);
            roaring_uint32_iterator_advance(&it);
        }
        cx->objIntentCard[o] = bs_card(row, cx->wa);
    }
    for (int a = 0; a < cx->nb_attr; a++) {
        aword *col = cx->attrExtent + (size_t)a * cx->wo;
        roaring_uint32_iterator_t it;
        roaring_iterator_init(ctx->cols[a], &it);
        while (it.has_value) {
            bs_set(col, (int)it.current_value);
            roaring_uint32_iterator_advance(&it);
        }
    }
    return cx;
}

/* ── état de l'algorithme ─────────────────────────────────────────────── */

typedef struct {
    DynOrder *ord;
    int nb_obj, nb_attr;
    int wo, wa;

    /* contexte dense, emprunté au CeresContext : jamais libéré ici */
    const aword *objIntent;
    const int   *objIntentCard;
    const aword *attrExtent;

    /* marquage de maturité de Classify : compteur de parents restant à voir,
     * horodaté pour éviter toute remise à zéro entre appels. */
    int *marks, *markEpoch;
    int epoch;

    /* couverture supérieure en construction. puc[c] == epoch signifie présent ;
     * un retrait remet à zéro. Interrogé seulement sur les nœuds défilés, donc
     * jamais balayé en entier. */
    int *puc;

    int *queue;            /* file du parcours, un nœud au plus une fois */
    int *activeWords;      /* mots occupés de l'extent en cours d'insertion */

    /* tampons de WorkOnLeftPart2, dimensionnés une fois sur nb_obj */
    int *wolpObjs, *wolpRaw, *wolpCard, *wolpBucket;
    unsigned char *wolpConsumed;
    aword *wolpAssoc;

    aword *topIntent;      /* intent complet du sommet, absent de DynOrder */
    int top;               /* identifiant du sommet : le premier concept créé */
} Ceres;

/* ── Classify ─────────────────────────────────────────────────────────────
 * Parcours descendant depuis le sommet. Un concept n'est examiné qu'une fois
 * tous ses parents traités (compteur de maturité), ce qui garantit un ordre
 * topologique ; c'est ce qui rend correct le calcul de la couverture supérieure
 * par « ajouter chaque nœud visité, retirer ses parents ».
 */
/* Renvoie l'identifiant du concept créé. L'appelant en a besoin : côté Java,
 * addConcept range les RÉFÉRENCES des ensembles, si bien que la mise à jour de
 * l'extent réduit qui suit l'appel modifie le concept déjà inséré. Ici les
 * bitsets sont copiés, donc cette mise à jour doit être reportée explicitement. */
static int classify(Ceres *ce, PreConcept *p, int isAttributeCpt) {
    DynOrder *ord = ce->ord;
    const int wo = ce->wo;

    /* Mots occupés de l'extent à insérer : relevés une fois, l'extent ne
     * bougeant pas pendant le parcours (seul l'intent est enrichi, et seulement
     * pour les concepts attribut). */
    const int nActive = bs_nonzero_words(p->ext, wo, ce->activeWords);

    ce->epoch++;
    const int epoch = ce->epoch;

    int qHead = 0, qTail = 0;
    ce->queue[qTail++] = ce->top;

    while (qHead < qTail) {
        int cur = ce->queue[qHead++];
        ce->puc[cur] = epoch;
        IntVec *up = &ord->upper[cur];
        for (int i = 0; i < up->len; i++) {
            ce->puc[up->data[i]] = 0;
        }

        if (isAttributeCpt) {
            bs_or(p->intent, DYN_RINT(ord, cur), ce->wa);
        }

        IntVec *lo = &ord->lower[cur];
        for (int i = 0; i < lo->len; i++) {
            int P = lo->data[i];
            if (ce->markEpoch[P] != epoch) {
                ce->marks[P] = ord->upper[P].len;
                ce->markEpoch[P] = epoch;
            }
            if (--ce->marks[P] == 0) {
                if (bs_subset_sparse(p->ext, DYN_EXT(ord, P),
                                     ce->activeWords, nActive)) {
                    ce->queue[qTail++] = P;
                }
            }
        }
    }

    int c = dyn_new_concept(ord);
    bs_copy(DYN_EXT(ord, c),  p->ext,  wo);
    bs_copy(DYN_REXT(ord, c), p->rext, wo);
    bs_copy(DYN_RINT(ord, c), p->rint, ce->wa);
    /* Les parents sont exactement les nœuds défilés encore marqués. Parcourir
     * la file plutôt que l'ensemble des concepts : le premier est borné par le
     * nombre d'ancêtres, le second par le nombre total de concepts. */
    for (int k = 0; k < qTail; k++) {
        int v = ce->queue[k];
        if (ce->puc[v] == epoch) {
            dyn_add_edge(ord, c, v);
            ce->puc[v] = 0;
        }
    }
    return c;
}

/* ── WorkOnLeftPart2 ──────────────────────────────────────────────────────
 * Les objets de l'extent qui ne sont pas dans l'extent réduit engendrent
 * éventuellement des concepts objet. Le balayage par cardinalité d'intension
 * croissante permet de regrouper d'un seul passage les objets de même intension.
 */
static void wolp(Ceres *ce, PreConcept *p, const aword *allCoveredIntent) {
    const int wa = ce->wa, wo = ce->wo;

    int n = 0;
    for (int o = 0; o < ce->nb_obj; o++) {
        if (bs_test(p->ext, o) && !bs_test(p->rext, o)) {
            ce->wolpRaw[n++] = o;
        }
    }
    if (n <= 0) {
        return;   /* n <= 0 plutôt que == 0 : borne le domaine pour le compilateur */
    }

    /* Tri par comptage sur |f(o)|, borné par |A| : O(n + |A|). Stable, donc les
     * objets de même cardinalité gardent l'ordre croissant des indices — c'est
     * l'ordre que produisait le tri Java, et la sortie en dépend. */
    const int m = ce->nb_attr;
    memset(ce->wolpBucket, 0, (size_t)(m + 2) * sizeof(int));
    for (int i = 0; i < n; i++) {
        ce->wolpCard[i] = ce->objIntentCard[ce->wolpRaw[i]];
        ce->wolpBucket[ce->wolpCard[i] + 1]++;
    }
    for (int k = 0; k <= m; k++) {
        ce->wolpBucket[k + 1] += ce->wolpBucket[k];
    }
    for (int i = 0; i < n; i++) {
        ce->wolpObjs[ce->wolpBucket[ce->wolpCard[i]]++] = ce->wolpRaw[i];
    }

    memset(ce->wolpConsumed, 0, (size_t)n);

    for (int i = 0; i < n; i++) {
        if (ce->wolpConsumed[i]) {
            continue;
        }
        const int oi = ce->wolpObjs[i];
        aword *assoc = ce->wolpAssoc;
        bs_copy(assoc, ce->objIntent + (size_t)oi * wa, wa);
        if (!bs_subset(assoc, allCoveredIntent, wa)) {
            continue;
        }

        /* Concept objet : intension réduite vide par construction. */
        PreConcept np;
        np.ext    = (aword*)calloc((size_t)wo, sizeof(aword));
        np.rext   = (aword*)calloc((size_t)wo, sizeof(aword));
        np.intent = (aword*)malloc((size_t)wa * sizeof(aword));
        np.rint   = (aword*)calloc((size_t)wa, sizeof(aword));
        np.owned  = 1;
        bs_copy(np.intent, assoc, wa);
        bs_set(np.ext, oi);
        bs_set(np.rext, oi);

        const int card = ce->objIntentCard[oi];
        for (int j = i + 1; j < n; j++) {
            if (ce->wolpConsumed[j]) {
                continue;
            }
            const int oj = ce->wolpObjs[j];
            const aword *fj = ce->objIntent + (size_t)oj * wa;
            if (ce->objIntentCard[oj] == card) {
                if (bs_equal(fj, assoc, wa)) {
                    bs_set(np.ext, oj);
                    bs_set(np.rext, oj);
                    ce->wolpConsumed[j] = 1;
                }
            } else if (bs_subset(assoc, fj, wa)) {
                bs_set(np.ext, oj);
            }
        }

        classify(ce, &np, 0);
        free(np.ext); free(np.rext); free(np.intent); free(np.rint);
    }
}

/* ── tri décroissant des pré-concepts attribut ────────────────────────────
 * Tri par comptage sur la cardinalité d'extent, parcouru à l'envers pour
 * obtenir l'ordre décroissant. Stable dans cet ordre, comme le tri Java.
 */
static void sort_preconcepts_desc(PreConcept *tab, int k, int nb_obj, int *bucket) {
    memset(bucket, 0, (size_t)(nb_obj + 2) * sizeof(int));
    for (int i = 0; i < k; i++) {
        bucket[tab[i].extCard + 1]++;
    }
    for (int v = 0; v <= nb_obj; v++) {
        bucket[v + 1] += bucket[v];
    }
    PreConcept *tmp = (PreConcept*)malloc((size_t)k * sizeof(PreConcept));
    for (int i = 0; i < k; i++) {
        tmp[bucket[tab[i].extCard]++] = tab[i];
    }
    /* croissant dans tmp ; on recopie à l'envers */
    for (int i = 0; i < k; i++) {
        tab[i] = tmp[k - 1 - i];
    }
    free(tmp);
}

/* ── point d'entrée ───────────────────────────────────────────────────── */

int *run_ceres_dense(const CeresContext *cx, int *out_len) {
    *out_len = 0;
    if (cx == NULL || cx->nb_obj == 0) {
        return NULL;
    }

    Ceres ce;
    memset(&ce, 0, sizeof(ce));
    ce.nb_obj = cx->nb_obj;
    ce.nb_attr = cx->nb_attr;
    ce.ord = dyn_create(ce.nb_obj, ce.nb_attr);
    ce.wo = ce.ord->wo;
    ce.wa = ce.ord->wa;
    ce.objIntent = cx->objIntent;
    ce.objIntentCard = cx->objIntentCard;
    ce.attrExtent = cx->attrExtent;

    const int wo = ce.wo, wa = ce.wa;
    const int maxConcepts = ce.nb_obj + ce.nb_attr + 2;

    ce.marks = (int*)calloc((size_t)maxConcepts, sizeof(int));
    ce.markEpoch = (int*)calloc((size_t)maxConcepts, sizeof(int));
    ce.puc = (int*)calloc((size_t)maxConcepts, sizeof(int));
    ce.queue = (int*)malloc((size_t)maxConcepts * sizeof(int));
    ce.activeWords = (int*)malloc((size_t)(wo + 1) * sizeof(int));
    ce.epoch = 0;

    ce.wolpObjs = (int*)malloc((size_t)ce.nb_obj * sizeof(int));
    ce.wolpRaw = (int*)malloc((size_t)ce.nb_obj * sizeof(int));
    ce.wolpCard = (int*)malloc((size_t)ce.nb_obj * sizeof(int));
    ce.wolpBucket = (int*)malloc((size_t)(ce.nb_attr + 2) * sizeof(int));
    ce.wolpConsumed = (unsigned char*)malloc((size_t)ce.nb_obj);
    ce.wolpAssoc = (aword*)malloc((size_t)wa * sizeof(aword));
    ce.topIntent = (aword*)calloc((size_t)wa, sizeof(aword));

    /* Sommet : tous les objets, extent réduit = objets d'intension vide. */
    const int top = dyn_new_concept(ce.ord);
    ce.top = top;
    {
        aword *e = DYN_EXT(ce.ord, top);
        for (int o = 0; o < ce.nb_obj; o++) {
            bs_set(e, o);
        }
        aword *re = DYN_REXT(ce.ord, top);
        for (int o = 0; o < ce.nb_obj; o++) {
            if (ce.objIntentCard[o] == 0) {
                bs_set(re, o);
            }
        }
    }

    /* Un pré-concept par attribut. */
    const int K = ce.nb_attr;
    PreConcept *tab = (PreConcept*)malloc((size_t)(K > 0 ? K : 1) * sizeof(PreConcept));
    for (int a = 0; a < K; a++) {
        tab[a].ext = (aword*)malloc((size_t)wo * sizeof(aword));
        bs_copy(tab[a].ext, ce.attrExtent + (size_t)a * wo, wo);
        tab[a].intent = (aword*)calloc((size_t)wa, sizeof(aword));
        tab[a].rext = (aword*)calloc((size_t)wo, sizeof(aword));
        tab[a].rint = (aword*)calloc((size_t)wa, sizeof(aword));
        bs_set(tab[a].intent, a);
        bs_set(tab[a].rint, a);
        tab[a].extCard = bs_card(tab[a].ext, wo);
        tab[a].owned = 1;
    }

    int *sortBucket = (int*)malloc((size_t)(ce.nb_obj + 2) * sizeof(int));
    sort_preconcepts_desc(tab, K, ce.nb_obj, sortBucket);
    free(sortBucket);

    unsigned char *done = (unsigned char*)calloc((size_t)(K > 0 ? K : 1), 1);
    aword *allCoveredIntent = (aword*)calloc((size_t)wa, sizeof(aword));

    int startIndex = 0, endIndex = 1;
    while (startIndex < K) {
        const int sizeToDo = tab[startIndex].extCard;

        /* Pré-concepts de même cardinalité d'extent : ceux dont l'extent est
         * identique fusionnent dans le premier. */
        while (endIndex < K && tab[endIndex].extCard == sizeToDo) {
            for (int i = startIndex; i < endIndex; i++) {
                if (!done[i] && bs_equal(tab[i].ext, tab[endIndex].ext, wo)) {
                    bs_or(tab[i].intent, tab[endIndex].intent, wa);
                    /* Java accumule ici l'INTENT du fusionné dans le RINTENT du
                     * survivant, pas son rintent. Pour un pré-concept attribut
                     * les deux coïncident, mais la fusion peut avoir déjà enrichi
                     * l'intent : on reproduit le code d'origine. */
                    bs_or(tab[i].rint, tab[endIndex].intent, wa);
                    done[endIndex] = 1;
                }
            }
            endIndex++;
        }

        for (int i = startIndex; i < endIndex; i++) {
            if (done[i]) {
                continue;
            }
            int doWOLP = 0;
            int cid = -1;

            if (sizeToDo < ce.nb_obj) {
                cid = classify(&ce, &tab[i], 1);
                doWOLP = 1;
            } else {
                /* L'extent couvre tous les objets : le pré-concept se confond
                 * avec le sommet. Java remplace alors l'entrée par une VUE sur
                 * les ensembles du sommet — la mise à jour de l'extent réduit qui
                 * suit écrit donc directement dans le sommet. On reproduit en
                 * pointant sur les zones de DynOrder ; d'où le drapeau owned,
                 * qui empêche de les libérer avec les tampons de l'algorithme.
                 *
                 * Ces pointeurs seraient invalidés par une réallocation de
                 * l'ordre. C'est sans danger ici : le tri décroissant place ce
                 * bloc en tête, et aucun concept n'est créé pendant son
                 * traitement. Invariant, non garantie syntaxique. */
                bs_or(ce.topIntent, tab[i].intent, wa);
                bs_or(DYN_RINT(ce.ord, top), tab[i].rint, wa);
                if (tab[i].owned) {
                    free(tab[i].ext); free(tab[i].intent);
                    free(tab[i].rext); free(tab[i].rint);
                }
                tab[i].ext = DYN_EXT(ce.ord, top);
                tab[i].intent = ce.topIntent;
                tab[i].rext = DYN_REXT(ce.ord, top);
                tab[i].rint = DYN_RINT(ce.ord, top);
                tab[i].owned = 0;
                doWOLP = 0;
            }

            bs_or(allCoveredIntent, tab[i].rint, wa);

            /* Extent réduit : un objet y entre si son intension est exactement
             * celle du concept. */
            for (int o = 0; o < ce.nb_obj; o++) {
                if (bs_test(tab[i].ext, o)
                    && bs_equal(ce.objIntent + (size_t)o * wa, tab[i].intent, wa)) {
                    bs_set(tab[i].rext, o);
                }
            }
            /* Report dans le concept inséré. Dans la branche « vue sur le
             * sommet » (cid == -1), tab[i].rext EST déjà la zone du sommet et
             * la boucle ci-dessus y a écrit directement. */
            if (cid >= 0) {
                bs_copy(DYN_REXT(ce.ord, cid), tab[i].rext, wo);
            }

            if (doWOLP) {
                wolp(&ce, &tab[i], allCoveredIntent);
            }
            done[i] = 1;
        }

        startIndex = endIndex;
        endIndex++;
    }

    /* Le sommet disparaît s'il ne réduit rien. Ses enfants perdent alors un
     * parent, comme le fait removeConcept côté Java, qui retire les arêtes
     * incidentes avant de supprimer le sommet. */
    if (bs_card(DYN_REXT(ce.ord, top), wo) == 0
        && bs_card(DYN_RINT(ce.ord, top), wa) == 0) {
        IntVec *lo = &ce.ord->lower[top];
        while (lo->len > 0) {
            dyn_remove_edge(ce.ord, lo->data[0], top);
        }
        dyn_remove_concept(ce.ord, top);
    }

    int *flat = dyn_to_flat(ce.ord, out_len);

    for (int i = 0; i < K; i++) {
        if (tab[i].owned) {
            free(tab[i].ext); free(tab[i].intent);
            free(tab[i].rext); free(tab[i].rint);
        }
    }
    free(tab);
    free(done);
    free(allCoveredIntent);
    free(ce.marks); free(ce.markEpoch); free(ce.puc);
    free(ce.queue); free(ce.activeWords);
    free(ce.wolpObjs); free(ce.wolpRaw); free(ce.wolpCard);
    free(ce.wolpBucket); free(ce.wolpConsumed); free(ce.wolpAssoc);
    free(ce.topIntent);
    dyn_free(ce.ord);
    return flat;
}

int *run_ceres_flat(BinaryContext *ctx, int *out_len) {
    *out_len = 0;
    if (ctx->nb_objects == 0) {
        return NULL;
    }
    CeresContext *cx = ceres_ctx_from_binary(ctx);
    int *flat = run_ceres_dense(cx, out_len);
    ceres_ctx_free(cx);
    return flat;
}
