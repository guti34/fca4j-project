/*
 * lincbo_pruning.c — voir lincbo_pruning.h pour l'intention générale.
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 */
#include "lincbo_pruning.h"
#include "../core/fca4j_common.h"
#include "../core/bitset.h"
#include "../core/bitset_roaring.h"
#include "../core/strbuf.h"
#include <limits.h>

/* ── petits utilitaires manquants de bitset.h ────────────────────────────
 * Laissés ici (static) plutôt que dans bitset.h pour ne pas toucher un
 * fichier partagé par ares.c/ceres.c sans revue ; à remonter si utiles
 * ailleurs. */

/* Premier bit positionné, ou INT_MAX si le bitset est vide. */
static inline int bs_find_first_lt(const aword *a, int w) {
    for (int k = 0; k < w; k++) if (a[k]) return (k << 6) + AW_CTZ(a[k]);
    return INT_MAX;
}
/* a := {0, 1, ..., n-1} (tous les bits jusqu'à n, aucun au-delà — respecte
 * la convention bitset.h comme quoi rien n'est jamais positionné après n). */
static inline void bs_fill_universe(aword *a, int w, int n) {
    for (int k = 0; k < w; k++) a[k] = ~(aword)0;
    int rem = n & 63;
    if (rem && w > 0) a[w - 1] &= (((aword)1 << rem) - 1);
}

/* ── implication interne : bitsets denses, contrairement à CImplication
 * (implication.h) qui est roaring. On ne convertit qu'à l'export final,
 * voir export_implications(). `closure` inclut les bits de `premise`
 * (contrairement au CImplication exporté, qui les retranche par
 * convention — cf. impl_create_with_support). `closure_min` est mis en
 * cache car imp->closure ne change plus une fois l'implication ajoutée,
 * alors qu'il est relu à chaque déclenchement dans la boucle chaude. ─── */
typedef struct {
    aword *premise;
    aword *closure;
    int    premise_card;
    int    closure_min;
    roaring_bitmap_t *support;   /* transféré à l'export, voir dimplvec_free */
} DenseImpl;

typedef struct { DenseImpl *data; int len, cap; } DenseImplVec;

static DenseImplVec dimplvec_new(void) {
    DenseImplVec v; v.len = 0; v.cap = 64;
    v.data = (DenseImpl*)malloc(sizeof(DenseImpl) * v.cap);
    return v;
}
static void dimplvec_free(DenseImplVec *v) {
    for (int i = 0; i < v->len; i++) {
        free(v->data[i].premise);
        free(v->data[i].closure);
        if (v->data[i].support) roaring_bitmap_free(v->data[i].support);
    }
    free(v->data); v->data = NULL; v->len = v->cap = 0;
}

/* ── état complet du moteur pour une exécution ──────────────────────── */
typedef struct {
    BinaryContext  *ctx;
    int n;                 /* nb_attributes */
    int w;                  /* AW_N(n) mots par bitset dense */
    LinCboPruneMode mode;

    /* Cache dense de ctx->rows[o] (intent de chaque objet, roaring dans
     * BinaryContext), construit une seule fois à la création de l'état.
     * Les lignes du contexte ne changent jamais pendant un run : convertir
     * une fois ici, plutôt qu'à chaque appel de fermeture, transforme
     * l'étape extent/intent — jusque-là un aller-retour dense<->roaring
     * par fermeture réelle — en une poignée de AND denses sur des mots
     * déjà en mémoire. Coût : nb_objects * w mots une fois, amorti sur
     * tout le run (voir real_closure_dense). */
    aword **dense_rows;

    /* Tentative abandonnée : un cache dense_cols (miroir de dense_rows, côté
     * objets) avait été essayé pour éviter roaring_bitmap_and_inplace dans
     * dense_compute_extent. Un bench à un seul facteur (extent_bench.c,
     * densité uniforme, 43500 objets façon ord10shuttle) montrait dense 2x à
     * 5x plus rapide — mais ne mesurait que le AND, pas la reconversion
     * dense->roaring en sortie. Sur données réelles (attributs corrélés,
     * contrairement au hasard uniforme du bench), les extents restent
     * souvent larges bien plus profondément dans la récursion ; reconstruire
     * un roaring_bitmap_t bit à bit (roaring_bitmap_add un par un, potentiel-
     * lement des dizaines de milliers de fois par fermeture, répété à
     * chaque fermeture réelle) s'est révélé catastrophique — 25x plus LENT
     * sur ord10shuttle (43500x88) en conditions réelles, malgré le gain
     * mesuré en synthétique. roaring_bitmap_add_range + and_inplace (code
     * ci-dessous) reste, lui, en opérations de niveau conteneur du début à
     * la fin — jamais O(cardinalité de l'extent) en nombre d'appels. Note
     * pour un futur essai : re-mesurer avec la reconversion incluse, sur un
     * jeu de données aux attributs corrélés (pas indépendants), avant de
     * retenter — voir aussi roaring_bitmap_add_bulk (contexte, valeurs
     * triées) comme piste si on rouvre le sujet. */

    DenseImplVec impls;
    IntVec  *list;          /* par attribut : indices d'implications dont il est prémisse */
    IntVec   counts_template;/* [k] = cardinalité de la prémisse de l'implication k (compte relatif à ∅) */
    aword   *default_conclusion;
    bool     has_default_conclusion;

    /* élagage LIFO (LinCbOWithPruning.java / cboMemPruning.cpp) */
    int *rules;
    int *pruning_stack;
    int  pruning_sp;
    int  pruning_stack_cap;

    /* élagage LCM (cboMemLCMPruning.cpp), pile triée par insertion */
    int *pruning_data;
    int *pruning_stack_lcm;
    int  pruning_sp_lcm;
    int  pruning_stack_lcm_cap;
} LinCboState;

/* Les deux piles d'élagage n'ont PAS une taille bornée par n : une même
 * frame « for i » peut pousser jusqu'à n entrées avant son propre
 * dépilement, et jusqu'à n frames peuvent être imbriquées simultanément
 * (une par valeur de y), d'où un pire cas en O(n²) d'entrées vivantes en
 * même temps — pas O(n). Piles Java (Stack<Integer>) et C++
 * (std::stack<int>) n'ont pas ce problème car elles croissent librement ;
 * on reproduit ça avec un realloc doublant, plutôt qu'une capacité fixe
 * (trouvé par AddressSanitizer lors de la validation, cf. message
 * accompagnant ce fichier).
 */
static void pruning_stack_ensure(int **stack, int *cap, int needed) {
    if (needed <= *cap) return;
    int newcap = *cap > 0 ? *cap * 2 : 16;
    while (newcap < needed) newcap *= 2;
    *stack = (int*)realloc(*stack, (size_t)newcap * sizeof(int));
    *cap = newcap;
}

/* Une seule frame par appel « boucle for i » (branche fermée) ; les
 * appels « extension » (Bo != fermeture) tournent en place sans empiler,
 * voir la note dans run_engine(). */
typedef struct {
    int y;
    aword *closed;      /* Bo == fermeture ici : un intent réel */
    int   *count;
    int    count_len;
    int    i;
    int    stack_snapshot;
} Frame;

/* ───────────────────────── création / libération ──────────────────── */

static LinCboState *lincbo_state_create(BinaryContext *ctx, LinCboPruneMode mode) {
    LinCboState *st = (LinCboState*)calloc(1, sizeof(LinCboState));
    st->ctx = ctx;
    st->n = ctx->nb_attributes;
    st->w = AW_N(st->n);
    st->mode = mode;
    st->impls = dimplvec_new();
    st->list = (IntVec*)malloc((size_t)(st->n > 0 ? st->n : 1) * sizeof(IntVec));
    for (int a = 0; a < st->n; a++) st->list[a] = IntVec_new();
    st->counts_template = IntVec_new();

    st->dense_rows = (aword**)malloc((size_t)(ctx->nb_objects > 0 ? ctx->nb_objects : 1) * sizeof(aword*));
    for (int o = 0; o < ctx->nb_objects; o++) {
        st->dense_rows[o] = (aword*)malloc((size_t)st->w * sizeof(aword));
        bs_from_roaring_into(ctx->rows[o], st->dense_rows[o], st->w);
    }

    if (mode == LINCBO_PRUNE_LIFO) {
        st->rules = (int*)malloc((size_t)(st->n > 0 ? st->n : 1) * sizeof(int));
        for (int i = 0; i < st->n; i++) st->rules[i] = -1;
        st->pruning_stack_cap = st->n > 16 ? st->n : 16;
        st->pruning_stack = (int*)malloc((size_t)st->pruning_stack_cap * sizeof(int));
    } else if (mode == LINCBO_PRUNE_LCM) {
        st->pruning_data = (int*)malloc((size_t)(st->n > 0 ? st->n : 1) * sizeof(int));
        for (int i = 0; i < st->n; i++) st->pruning_data[i] = -1;
        st->pruning_stack_lcm_cap = st->n > 16 ? st->n : 16;
        st->pruning_stack_lcm = (int*)malloc((size_t)st->pruning_stack_lcm_cap * sizeof(int));
    }
    return st;
}

static void lincbo_state_free(LinCboState *st) {
    dimplvec_free(&st->impls);
    for (int a = 0; a < st->n; a++) IntVec_free(&st->list[a]);
    free(st->list);
    for (int o = 0; o < st->ctx->nb_objects; o++) free(st->dense_rows[o]);
    free(st->dense_rows);
    IntVec_free(&st->counts_template);
    free(st->default_conclusion);
    free(st->rules);
    free(st->pruning_stack);
    free(st->pruning_data);
    free(st->pruning_stack_lcm);
    free(st);
}

static int add_implication(LinCboState *st, const aword *premise, const aword *bclosure,
                            roaring_bitmap_t *support) {
    int w = st->w;
    aword *p = (aword*)malloc((size_t)w * sizeof(aword));
    aword *c = (aword*)malloc((size_t)w * sizeof(aword));
    bs_copy(p, premise, w);
    bs_copy(c, bclosure, w);
    int card = bs_card(p, w);

    if (st->impls.len >= st->impls.cap) {
        st->impls.cap *= 2;
        st->impls.data = (DenseImpl*)realloc(st->impls.data, sizeof(DenseImpl) * st->impls.cap);
    }
    int idx = st->impls.len++;
    st->impls.data[idx].premise      = p;
    st->impls.data[idx].closure      = c;
    st->impls.data[idx].premise_card = card;
    st->impls.data[idx].closure_min  = bs_find_first_lt(c, w);
    st->impls.data[idx].support      = support;

    IntVec_push(&st->counts_template, card);

    if (card == 0) {
        if (!st->has_default_conclusion) {
            st->default_conclusion = (aword*)calloc((size_t)w, sizeof(aword));
            st->has_default_conclusion = true;
        }
        bs_or(st->default_conclusion, c, w);
    }

    for (BS_FOREACH(a, premise, w)) {
        IntVec_push(&st->list[a], idx);
    }
    return idx;
}

/* ───────────────────────── élagage : dispatch par mode ────────────── */

static int pruning_snapshot(LinCboState *st) {
    return st->mode == LINCBO_PRUNE_LCM ? st->pruning_sp_lcm : st->pruning_sp;
}

static void lcm_pruning_del(LinCboState *st, int att, int prev_stack_size) {
    while (st->pruning_sp_lcm > prev_stack_size) {
        int top = st->pruning_stack_lcm[st->pruning_sp_lcm - 1];
        if (st->pruning_data[top] >= att) {
            st->pruning_sp_lcm--;
            st->pruning_data[top] = -1;
        } else break;
    }
}
static void lcm_pruning_set(LinCboState *st, int att, int adds, int prev_stack_size) {
    pruning_stack_ensure(&st->pruning_stack_lcm, &st->pruning_stack_lcm_cap, st->pruning_sp_lcm + 1);
    int cur = st->pruning_sp_lcm;
    while (cur > prev_stack_size) {
        if (st->pruning_data[st->pruning_stack_lcm[cur - 1]] <= adds) break;
        st->pruning_stack_lcm[cur] = st->pruning_stack_lcm[cur - 1];
        cur--;
    }
    st->pruning_stack_lcm[cur] = att;
    st->pruning_sp_lcm++;
    st->pruning_data[att] = adds;
}

static void pruning_unwind(LinCboState *st, int snapshot) {
    if (st->mode == LINCBO_PRUNE_LIFO) {
        while (st->pruning_sp > snapshot) {
            int val = st->pruning_stack[--st->pruning_sp];
            st->rules[val] = -1;
        }
    } else if (st->mode == LINCBO_PRUNE_LCM) {
        lcm_pruning_del(st, -1, snapshot); /* att=-1 : purge inconditionnelle, cf. cboMemLCMPruning.cpp */
    }
}

/* Appelé quand un enfant (attribut i) vient de retourner `result`. */
static void pruning_after_child(LinCboState *st, int i, int result, int frame_snapshot) {
    if (st->mode == LINCBO_PRUNE_LIFO) {
        if (result >= 0 && result < i) {
            st->rules[i] = result;
            pruning_stack_ensure(&st->pruning_stack, &st->pruning_stack_cap, st->pruning_sp + 1);
            st->pruning_stack[st->pruning_sp++] = i;
        }
    } else if (st->mode == LINCBO_PRUNE_LCM) {
        if (result != -1) lcm_pruning_set(st, i, result, frame_snapshot);
    }
}

/* true si l'attribut i doit être sauté sans appel récursif. */
static bool pruning_should_skip(LinCboState *st, int i, const aword *Bp, int frame_snapshot) {
    switch (st->mode) {
    case LINCBO_PRUNE_LIFO: {
        int r = st->rules[i];
        return r != -1 && !bs_test(Bp, r);
    }
    case LINCBO_PRUNE_LCM:
        lcm_pruning_del(st, i, frame_snapshot);
        return st->pruning_data[i] != -1;
    default:
        return false;
    }
}

/* ───────────────────────── fermeture réelle (extent/intent) ─────────
 *
 * Équivalent dense-natif de compute_extent()/compute_intent() (closure.c),
 * qui n'existait qu'en roaring. La différence n'est pas cosmétique : un
 * bench à un seul facteur (algo/backend identiques, cf. bench_sets.c)
 * montrait ce chemin roaring<->dense<->roaring perdant face à
 * lincbo.c (100% roaring) dès qu'un même petit univers d'attributs (peu
 * de mots) enchaînait beaucoup de fermetures réelles — le coût fixe par
 * conversion (allocation + itération bit-à-bit d'un roaring_bitmap_t) ne
 * s'amortissait plus. Ici, on ne convertit jamais Bo ni le résultat :
 *   - l'extent s'obtient en parcourant les bits de Bo directement
 *     (BS_FOREACH) pour AND-er les colonnes (roaring, domaine objets —
 *     ça reste roaring, c'est déjà ce que veut le support exporté ; voir
 *     la note sur LinCboState pour pourquoi un passage en dense ici a été
 *     essayé puis abandonné) ;
 *   - l'intent s'obtient en AND-ant les lignes des objets de l'extent,
 *     mais depuis le cache dense_rows (voir LinCboState) plutôt qu'en
 *     convertissant chaque roaring_bitmap_t ctx->rows[o] à la volée.
 * Résultat : zéro conversion dense<->roaring dans le chemin chaud, quelle
 * que soit la taille de l'univers d'attributs ou la densité du contexte.
 */
static roaring_bitmap_t *dense_compute_extent(LinCboState *st, const aword *Bo) {
    BinaryContext *ctx = st->ctx;
    int w = st->w;
    roaring_bitmap_t *extent = roaring_bitmap_create();
    if (ctx->nb_attributes < ctx->nb_objects) {
        roaring_bitmap_add_range(extent, 0, (uint32_t)ctx->nb_objects);
        for (BS_FOREACH(a, Bo, w)) {
            roaring_bitmap_and_inplace(extent, ctx->cols[a]);
        }
    } else {
        /* objet o dans l'extent ⟺ toutes les attributs de Bo sont dans
         * l'intent de o, i.e. Bo ⊆ dense_rows[o] — pas l'inverse (miroir
         * exact de compute_extent : roaring_bitmap_is_subset(attributes,
         * ctx->rows[o]) teste attributes ⊆ rows[o]). */
        for (int o = 0; o < ctx->nb_objects; o++)
            if (bs_subset(Bo, st->dense_rows[o], w))
                roaring_bitmap_add(extent, (uint32_t)o);
    }
    return extent;
}

static void dense_compute_intent_into(LinCboState *st, roaring_bitmap_t *extent, aword *intent_out) {
    BinaryContext *ctx = st->ctx;
    int w = st->w, n = st->n;
    uint32_t card = (uint32_t)roaring_bitmap_get_cardinality(extent);
    if (card == 0) { bs_fill_universe(intent_out, w, n); return; }
    if (card < (uint32_t)n) {
        roaring_uint32_iterator_t it;
        roaring_iterator_init(extent, &it);
        int o0 = (int)it.current_value;
        bs_copy(intent_out, st->dense_rows[o0], w);
        roaring_uint32_iterator_advance(&it);
        while (it.has_value) {
            if (bs_empty(intent_out, w)) break; /* sortie anticipée : plus rien à retirer */
            int o = (int)it.current_value;
            bs_and(intent_out, st->dense_rows[o], w);
            roaring_uint32_iterator_advance(&it);
        }
    } else {
        bs_zero(intent_out, w);
        for (int a = 0; a < n; a++)
            if (roaring_bitmap_is_subset(extent, ctx->cols[a]))
                bs_set(intent_out, a);
    }
}

static void real_closure_dense(LinCboState *st, const aword *Bo,
                                aword *bclosure_out, roaring_bitmap_t **support_out) {
    roaring_bitmap_t *ext = dense_compute_extent(st, Bo);
    dense_compute_intent_into(st, ext, bclosure_out);
    bs_or(bclosure_out, Bo, st->w); /* toujours vrai mathématiquement, gardé par robustesse */
    *support_out = ext;
}

/* ───────────────────────── LinClosureRC (cœur de LinCbO) ──────────── */

/* Z est consommé (modifié en place). B n'est pas modifié. prev_count est
 * emprunté (ni modifié ni libéré ici). Retourne false sur coupe précoce
 * (compatibilité de canonicité), auquel cas *fail_out porte l'attribut en
 * cause. */
static bool lin_closure_rc(LinCboState *st, const aword *B, int y, aword *Z,
                            const int *prev_count, int prev_count_len,
                            aword **D_out, int **count_out, int *count_len_out,
                            int *fail_out) {
    int w = st->w, n = st->n;
    aword *D = (aword*)malloc((size_t)w * sizeof(aword));
    bs_copy(D, B, w);
    if (st->has_default_conclusion) bs_or(D, st->default_conclusion, w);

    int nimp = st->impls.len;
    int *count = (int*)malloc((size_t)(nimp > 0 ? nimp : 1) * sizeof(int));
    for (int i = 0; i < prev_count_len && i < nimp; i++) count[i] = prev_count[i];
    for (int i = prev_count_len; i < nimp; i++) {
        const aword *pr = st->impls.data[i].premise;
        int c = 0;
        for (int k = 0; k < w; k++) c += AW_POPCOUNT(pr[k] & ~B[k]);
        count[i] = c;
    }

    /* Parité avec lincbo.c / LinCbO.java (variante sans élagage) : un test
     * supplémentaire absent de la référence académique des variantes avec
     * élagage (cboMemPruning.cpp / cboMemLCMPruning.cpp) et de
     * LinCbOWithPruning.java. Sa valeur de retour n'est jamais consommée
     * hors du mode NONE (aucune pile d'élagage à mettre à jour). */
    bool extra_check = (st->mode == LINCBO_PRUNE_NONE);

    for (;;) {
        int m = bs_find_first_lt(Z, w);
        if (m >= n) break;
        bs_clear(Z, m);
        IntVec *lm = &st->list[m];
        for (int li = 0; li < lm->len; li++) {
            int impId = lm->data[li];
            if (impId >= nimp) continue;
            int c = count[impId];
            count[impId] = c - 1;
            if (c == 1) {
                DenseImpl *imp = &st->impls.data[impId];
                if (extra_check && imp->closure_min < y) {
                    int minD = bs_find_first_lt(D, w);
                    if (minD >= y) {
                        free(D); free(count);
                        *fail_out = imp->closure_min;
                        return false;
                    }
                }
                int minBit = INT_MAX;
                for (int k = 0; k < w; k++) {
                    aword d = imp->closure[k] & ~D[k];
                    if (d) {
                        if (minBit == INT_MAX) minBit = (k << 6) + AW_CTZ(d);
                        D[k] |= d;
                        Z[k] |= d;
                    }
                }
                if (minBit < y) {
                    free(D); free(count);
                    *fail_out = minBit;
                    return false;
                }
            }
        }
        if (bs_card(D, w) == n) {
            *D_out = D; *count_out = NULL; *count_len_out = 0; *fail_out = -1;
            free(count);
            return true;
        }
    }
    *D_out = D; *count_out = count; *count_len_out = nimp; *fail_out = -1;
    return true;
}

/* ───────────────────────── moteur itératif ─────────────────────────
 *
 * Traduction de la récursion Step()/_LinCbOStep en boucle + pile
 * explicite, MAIS : la branche « extension » (Bo != fermeture, un seul
 * appel terminal) ne pousse jamais de frame — elle boucle en place. Seule
 * la branche « for i » (Bo == fermeture, plusieurs appels) empile,
 * puisque c'est la seule où il faut se souvenir d'une position de
 * boucle entre deux appels enfants. Le raisonnement (la pile d'élagage
 * globale, elle, revient toujours à sa taille d'entrée avant qu'un appel
 * ne retourne, y compris pour les maillons d'extension, donc leur
 * dépilement est un no-op qu'on peut omettre entièrement) est détaillé
 * dans le message accompagnant ce fichier.
 */
static void run_engine(LinCboState *st) {
    int n = st->n, w = st->w;
    Frame *stack = (Frame*)malloc((size_t)(n + 2) * sizeof(Frame));
    int sp = -1;

    aword *cur_B = (aword*)calloc((size_t)w, sizeof(aword));
    int    cur_y = -1;
    aword *cur_Z = (aword*)calloc((size_t)w, sizeof(aword));
    int   *cur_prev_count = NULL;
    int    cur_prev_count_len = 0;
    /* cur_prev_count vient de deux sources : soit un tableau frais renvoyé
     * par lin_closure_rc (possédé, à libérer après usage — cas "extend" en
     * boucle), soit st->counts_template.data / f->count, empruntés à une
     * structure qui vit plus longtemps que cet appel (le frame, ou l'état
     * global) — pas besoin d'en faire une copie juste pour la jeter juste
     * après : lin_closure_rc prend prev_count en `const` et construit lui-
     * même son propre tableau de sortie. Avant ce drapeau, chaque descente
     * malloc+memcpy-ait ce tableau (taille = nb d'implications, donc
     * potentiellement des milliers d'entiers) rien que pour le refaire une
     * seconde fois à l'identique à l'intérieur de lin_closure_rc : mesuré au
     * profileur (callgrind), ce doublon représentait ~40% des instructions
     * exécutées sur un contexte à base large — l'écart qui faisait perdre ce
     * moteur face à lincbo.c (qui ne fait qu'une seule copie, comme ici
     * après correction) sur les contextes à beaucoup d'implications. */
    bool   cur_prev_count_owned = false;

    bool entering = true;
    int  resume_result = INT_MIN; /* sentinelle "pas de résultat enfant à appliquer" */

    for (;;) {
        if (entering) {
            bool pushed_frame = false;
            for (;;) { /* boucle "extension" : tant que Bo != fermeture et qu'on reste dans les clous */
                aword *D; int *count; int count_len; int fail;
                bool ok = lin_closure_rc(st, cur_B, cur_y, cur_Z,
                                          cur_prev_count, cur_prev_count_len,
                                          &D, &count, &count_len, &fail);
                free(cur_Z); cur_Z = NULL;
                if (cur_prev_count_owned) free(cur_prev_count);
                cur_prev_count = NULL;
                cur_prev_count_owned = false;

                if (!ok) {
                    free(cur_B); cur_B = NULL;
                    resume_result = fail;
                    entering = false;
                    break;
                }

                aword *bclosure = (aword*)malloc((size_t)w * sizeof(aword));
                roaring_bitmap_t *support;
                real_closure_dense(st, D, bclosure, &support);

                if (!bs_equal(D, bclosure, w)) {
                    add_implication(st, D, bclosure, support); /* prend possession de support */
                    aword *Zp = (aword*)malloc((size_t)w * sizeof(aword));
                    int minZp = INT_MAX;
                    for (int k = 0; k < w; k++) {
                        aword d = bclosure[k] & ~D[k];
                        Zp[k] = d;
                        if (d && minZp == INT_MAX) minZp = (k << 6) + AW_CTZ(d);
                    }
                    free(D);
                    if (minZp != INT_MAX && minZp > cur_y) {
                        free(cur_B);
                        cur_B = bclosure;
                        cur_Z = Zp;
                        cur_prev_count = count;
                        cur_prev_count_len = count_len;
                        cur_prev_count_owned = true;
                        continue; /* tourne en place, pas d'empilement */
                    } else {
                        free(bclosure); free(Zp); free(count);
                        free(cur_B); cur_B = NULL;
                        resume_result = -1;
                        entering = false;
                        break;
                    }
                } else {
                    free(cur_B); cur_B = NULL;
                    roaring_bitmap_free(support); /* extent d'un nœud fermé : pas de fermeture-historique ici */
                    free(D);
                    sp++;
                    Frame *f = &stack[sp];
                    f->y = cur_y;
                    f->closed = bclosure;
                    f->count = count;
                    f->count_len = count_len;
                    f->i = n;
                    f->stack_snapshot = pruning_snapshot(st);
                    pushed_frame = true;
                    break;
                }
            }
            if (pushed_frame) {
                entering = false;
                resume_result = INT_MIN;
            }
        }

        if (sp < 0) break; /* calcul terminé */

        Frame *f = &stack[sp];
        if (resume_result != INT_MIN) {
            pruning_after_child(st, f->i, resume_result, f->stack_snapshot);
        }

        bool descended = false;
        while (f->i > f->y + 1) {
            f->i--;
            int i = f->i;
            if (bs_test(f->closed, i)) continue;

            aword *Bp = (aword*)malloc((size_t)w * sizeof(aword));
            bs_copy(Bp, f->closed, w);
            bs_set(Bp, i);

            if (pruning_should_skip(st, i, Bp, f->stack_snapshot)) {
                free(Bp);
                continue;
            }

            aword *Zp = (aword*)calloc((size_t)w, sizeof(aword));
            bs_set(Zp, i);
            int card = bs_card(Bp, w);

            cur_B = Bp;
            cur_y = i;
            cur_Z = Zp;
            /* Emprunt, pas de copie : consommé tout de suite par le premier
             * lin_closure_rc de la prochaine itération « entering », avant
             * qu'aucune implication ne soit ajoutée (donc avant que
             * counts_template puisse être réalloué) — voir la note sur
             * cur_prev_count_owned plus haut. */
            if (card == 1) {
                cur_prev_count_len = st->counts_template.len;
                cur_prev_count = st->counts_template.data;
            } else {
                cur_prev_count_len = f->count_len;
                cur_prev_count = f->count;
            }
            cur_prev_count_owned = false;

            descended = true;
            break;
        }

        if (descended) {
            entering = true;
            continue;
        }

        pruning_unwind(st, f->stack_snapshot);
        free(f->closed);
        free(f->count);
        sp--;
        resume_result = -1;
        entering = false;
    }

    free(stack);
    free(cur_B);
    free(cur_Z);
    if (cur_prev_count_owned) free(cur_prev_count);
}

/* ───────────────────────── export / API publique ───────────────────── */

static ImplVec export_implications(LinCboState *st) {
    ImplVec out = ImplVec_new();
    int w = st->w;
    for (int i = 0; i < st->impls.len; i++) {
        DenseImpl *di = &st->impls.data[i];
        roaring_bitmap_t *premise_r = bs_to_roaring(di->premise, w);
        roaring_bitmap_t *closure_r = bs_to_roaring(di->closure, w);
        CImplication *imp = impl_create_with_support(premise_r, closure_r, di->support);
        di->support = NULL; /* possession transférée, cf. dimplvec_free */
        ImplVec_push(&out, imp);
    }
    return out;
}

ImplVec run_lincbo_pruning(BinaryContext *ctx, LinCboPruneMode mode) {
    LinCboState *st = lincbo_state_create(ctx, mode);
    run_engine(st);
    ImplVec result = export_implications(st);
    lincbo_state_free(st);
    return result;
}

static const char *mode_name(LinCboPruneMode mode) {
    switch (mode) {
    case LINCBO_PRUNE_LIFO: return "LinCbOWithPruning";
    case LINCBO_PRUNE_LCM:  return "LinCbOWithLCMPruning";
    default:                return "LinCbO";
    }
}

char *run_lincbo_pruning_json(BinaryContext *ctx, LinCboPruneMode mode) {
    ImplVec result = run_lincbo_pruning(ctx, mode);

    StrBuf sb = sb_new();
    sb_printf(&sb, "{\"algorithm\":\"%s\",\"implications\":%d,\"context\":", mode_name(mode), result.len);
    sb_append_json_str(&sb, ctx->name);
    sb_printf(&sb, ",\"objects\":%d,\"attributes\":%d,\"basis\":[", ctx->nb_objects, ctx->nb_attributes);
    for (int i = 0; i < result.len; i++) {
        if (i > 0) sb_append(&sb, ",");
        CImplication *imp = result.data[i];
        sb_append(&sb, "{\"premise\":[");
        roaring_uint32_iterator_t it; roaring_iterator_init(imp->premise, &it);
        int first = 1;
        while (it.has_value) {
            if (!first) sb_append(&sb, ",");
            int a = (int)it.current_value;
            if (a < ctx->attr_names.len) sb_append_json_str(&sb, ctx->attr_names.data[a]);
            else sb_printf(&sb, "\"%d\"", a);
            first = 0; roaring_uint32_iterator_advance(&it);
        }
        sb_append(&sb, "],\"conclusion\":[");
        roaring_iterator_init(imp->conclusion, &it); first = 1;
        while (it.has_value) {
            if (!first) sb_append(&sb, ",");
            int a = (int)it.current_value;
            if (a < ctx->attr_names.len) sb_append_json_str(&sb, ctx->attr_names.data[a]);
            else sb_printf(&sb, "\"%d\"", a);
            first = 0; roaring_uint32_iterator_advance(&it);
        }
        sb_printf(&sb, "],\"support\":%d}", imp->support_size);
    }
    sb_append(&sb, "]}");

    implvec_free_all(&result);
    return sb.buf;
}

/* Miroir exact de run_dbasis_flat (algo/dbasis.c) : même disposition, même
 * convention (conclusion déjà privée de la prémisse, support réduit à sa
 * cardinalité — pas ses objets, recalculés côté Java comme le fait déjà
 * NativeDBaseV24). Voir lincbo_pruning.h pour le format. */
int *run_lincbo_pruning_flat(BinaryContext *ctx, LinCboPruneMode mode, int *out_len) {
    ImplVec result = run_lincbo_pruning(ctx, mode);

    long total = 1; /* [0] = M */
    for (int i = 0; i < result.len; i++) {
        CImplication *imp = result.data[i];
        total += (long)roaring_bitmap_get_cardinality(imp->premise)
               + (long)roaring_bitmap_get_cardinality(imp->conclusion)
               + 3; /* cardP + cardC + support */
    }

    int *flat = (int*)malloc((size_t)total * sizeof(int));
    int p = 0;
    flat[p++] = result.len;

    for (int i = 0; i < result.len; i++) {
        CImplication *imp = result.data[i];
        roaring_uint32_iterator_t it;

        flat[p++] = (int)roaring_bitmap_get_cardinality(imp->premise);
        roaring_iterator_init(imp->premise, &it);
        while (it.has_value) { flat[p++] = (int)it.current_value; roaring_uint32_iterator_advance(&it); }

        flat[p++] = (int)roaring_bitmap_get_cardinality(imp->conclusion);
        roaring_iterator_init(imp->conclusion, &it);
        while (it.has_value) { flat[p++] = (int)it.current_value; roaring_uint32_iterator_advance(&it); }

        flat[p++] = imp->support_size;
    }

    implvec_free_all(&result);
    *out_len = p;
    return flat;
}
