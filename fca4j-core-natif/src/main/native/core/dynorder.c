/*
 * dynorder.c — ordre de concepts mutable à couvertures incrémentales (anciennement aresorder.c)
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 */
#include "dynorder.h"

#define DYN_INIT_CAP 256

static void dyn_ensure_cap(DynOrder *dyn, int needed) {
    if (needed <= dyn->cap) return;
    int newcap = dyn->cap * 2;
    if (newcap < needed) newcap = needed;

    dyn->ext  = (aword*)realloc(dyn->ext,  (size_t)newcap * dyn->wo * sizeof(aword));
    dyn->rext = (aword*)realloc(dyn->rext, (size_t)newcap * dyn->wo * sizeof(aword));
    dyn->rint = (aword*)realloc(dyn->rint, (size_t)newcap * dyn->wa * sizeof(aword));
    dyn->lower = (IntVec*)realloc(dyn->lower, (size_t)newcap * sizeof(IntVec));
    dyn->upper = (IntVec*)realloc(dyn->upper, (size_t)newcap * sizeof(IntVec));
    dyn->alive = (unsigned char*)realloc(dyn->alive, (size_t)newcap);
    dyn->markA = (int*)realloc(dyn->markA, (size_t)newcap * sizeof(int));
    dyn->markB = (int*)realloc(dyn->markB, (size_t)newcap * sizeof(int));
    dyn->sort_buf = (int*)realloc(dyn->sort_buf, (size_t)newcap * sizeof(int));

    /* Les marques doivent naître à 0 : les époques valent au moins 1. */
    memset(dyn->markA + dyn->cap, 0, (size_t)(newcap - dyn->cap) * sizeof(int));
    memset(dyn->markB + dyn->cap, 0, (size_t)(newcap - dyn->cap) * sizeof(int));
    memset(dyn->alive + dyn->cap, 0, (size_t)(newcap - dyn->cap));

    dyn->cap = newcap;
}

static void dyn_ensure_stack(DynOrder *dyn, int needed) {
    if (needed <= dyn->stack_cap) return;
    int newcap = dyn->stack_cap * 2;
    if (newcap < needed) newcap = needed;
    dyn->stack = (int*)realloc(dyn->stack, (size_t)newcap * sizeof(int));
    dyn->stack_cap = newcap;
}

static void dyn_ensure_sel(DynOrder *dyn, int needed) {
    if (needed <= dyn->sel_cap) return;
    int newcap = dyn->sel_cap * 2;
    if (newcap < needed) newcap = needed;
    dyn->sel = (int*)realloc(dyn->sel, (size_t)newcap * sizeof(int));
    dyn->sel_cap = newcap;
}

DynOrder *dyn_create(int nb_obj, int nb_attr) {
    DynOrder *dyn = (DynOrder*)calloc(1, sizeof(DynOrder));
    dyn->nb_obj = nb_obj;
    dyn->nb_attr = nb_attr;
    dyn->wo = AW_N(nb_obj  > 0 ? nb_obj  : 1);
    dyn->wa = AW_N(nb_attr > 0 ? nb_attr : 1);
    dyn->cap = DYN_INIT_CAP;
    dyn->n = 0;
    dyn->nb_alive = 0;

    dyn->ext  = (aword*)calloc((size_t)dyn->cap * dyn->wo, sizeof(aword));
    dyn->rext = (aword*)calloc((size_t)dyn->cap * dyn->wo, sizeof(aword));
    dyn->rint = (aword*)calloc((size_t)dyn->cap * dyn->wa, sizeof(aword));
    dyn->lower = (IntVec*)malloc((size_t)dyn->cap * sizeof(IntVec));
    dyn->upper = (IntVec*)malloc((size_t)dyn->cap * sizeof(IntVec));
    dyn->alive = (unsigned char*)calloc((size_t)dyn->cap, 1);
    dyn->markA = (int*)calloc((size_t)dyn->cap, sizeof(int));
    dyn->markB = (int*)calloc((size_t)dyn->cap, sizeof(int));
    dyn->sort_buf = (int*)malloc((size_t)dyn->cap * sizeof(int));
    dyn->epochA = 0;
    dyn->epochB = 0;

    dyn->stack_cap = 256;
    dyn->stack = (int*)malloc((size_t)dyn->stack_cap * sizeof(int));
    dyn->sel_cap = 64;
    dyn->sel = (int*)malloc((size_t)dyn->sel_cap * sizeof(int));
    dyn->sel_count = 0;

    dyn->bucket = (int*)malloc((size_t)(nb_obj + 2) * sizeof(int));
    return dyn;
}

void dyn_free(DynOrder *dyn) {
    if (!dyn) return;
    for (int c = 0; c < dyn->n; c++) {
        IntVec_free(&dyn->lower[c]);
        IntVec_free(&dyn->upper[c]);
    }
    free(dyn->ext); free(dyn->rext); free(dyn->rint);
    free(dyn->lower); free(dyn->upper);
    free(dyn->alive); free(dyn->markA); free(dyn->markB);
    free(dyn->stack); free(dyn->sel);
    free(dyn->sort_buf); free(dyn->bucket);
    free(dyn);
}

int dyn_new_concept(DynOrder *dyn) {
    dyn_ensure_cap(dyn, dyn->n + 1);
    int c = dyn->n++;
    bs_zero(DYN_EXT(dyn, c),  dyn->wo);
    bs_zero(DYN_REXT(dyn, c), dyn->wo);
    bs_zero(DYN_RINT(dyn, c), dyn->wa);
    dyn->lower[c] = IntVec_new();
    dyn->upper[c] = IntVec_new();
    dyn->alive[c] = 1;
    dyn->nb_alive++;
    return c;
}

void dyn_add_edge(DynOrder *dyn, int lower, int upper) {
    IntVec_push(&dyn->upper[lower], upper);
    IntVec_push(&dyn->lower[upper], lower);
}

/* Retrait par échange avec le dernier : l'ordre des couvertures n'a aucune
 * signification, donc O(degré) sans décalage. */
static bool intvec_remove(IntVec *v, int val) {
    for (int i = 0; i < v->len; i++) {
        if (v->data[i] == val) {
            v->data[i] = v->data[v->len - 1];
            v->len--;
            return true;
        }
    }
    return false;
}

bool dyn_remove_edge(DynOrder *dyn, int lower, int upper) {
    bool a = intvec_remove(&dyn->upper[lower], upper);
    bool b = intvec_remove(&dyn->lower[upper], lower);
    return a || b;
}

void dyn_remove_concept(DynOrder *dyn, int c) {
    if (!dyn->alive[c]) return;
    dyn->alive[c] = 0;
    dyn->nb_alive--;
    dyn->lower[c].len = 0;
    dyn->upper[c].len = 0;
}

const int *dyn_sort_by_extent(DynOrder *dyn, int *out_count) {
    int maxcard = dyn->nb_obj;
    memset(dyn->bucket, 0, (size_t)(maxcard + 2) * sizeof(int));

    /* Passe 1 : compter les cardinalités des concepts vivants. */
    for (int c = 0; c < dyn->n; c++) {
        if (!dyn->alive[c]) continue;
        int card = bs_card(DYN_EXT(dyn, c), dyn->wo);
        dyn->bucket[card + 1]++;
    }
    /* Passe 2 : préfixes. */
    for (int k = 1; k <= maxcard + 1; k++) dyn->bucket[k] += dyn->bucket[k - 1];
    /* Passe 3 : placement, stable et croissant. */
    int total = 0;
    for (int c = 0; c < dyn->n; c++) {
        if (!dyn->alive[c]) continue;
        int card = bs_card(DYN_EXT(dyn, c), dyn->wo);
        dyn->sort_buf[dyn->bucket[card]++] = c;
        total++;
    }
    *out_count = total;
    return dyn->sort_buf;
}

int dyn_reduced_extent(DynOrder *dyn, int c, aword *rext_out) {
    bs_copy(rext_out, DYN_EXT(dyn, c), dyn->wo);
    IntVec *lo = &dyn->lower[c];
    for (int i = 0; i < lo->len; i++) {
        bs_andnot(rext_out, DYN_EXT(dyn, lo->data[i]), dyn->wo);
    }
    return lo->len;
}

void dyn_mark_reachable_up(DynOrder *dyn, int src, const int *targets, int ntargets) {
    dyn->epochB++;               /* markB sert ici de marque « est une cible » */
    int remaining = 0;
    for (int i = 0; i < ntargets; i++) {
        int t = targets[i];
        if (dyn->markB[t] != dyn->epochB) {
            dyn->markB[t] = dyn->epochB;
            remaining++;
        }
    }
    dyn->epochA++;
    dyn_ensure_stack(dyn, 1);
    int sp = 0;
    dyn->stack[sp++] = src;
    dyn->markA[src] = dyn->epochA;
    if (dyn->markB[src] == dyn->epochB) remaining--;

    while (sp > 0 && remaining > 0) {
        int v = dyn->stack[--sp];
        IntVec *up = &dyn->upper[v];
        for (int i = 0; i < up->len; i++) {
            int p = up->data[i];
            if (dyn->markA[p] == dyn->epochA) continue;
            dyn->markA[p] = dyn->epochA;
            if (dyn->markB[p] == dyn->epochB) remaining--;
            dyn_ensure_stack(dyn, sp + 1);
            dyn->stack[sp++] = p;
        }
    }
}

void dyn_maximal_of(DynOrder *dyn, const unsigned char *sel_flags,
                   const int *sel_list, int list_count) {
    dyn->sel_count = 0;
    if (list_count == 0) return;
    if (list_count == 1) {
        dyn_ensure_sel(dyn, 1);
        dyn->sel[dyn->sel_count++] = sel_list[0];
        dyn->epochB++;           /* aucun dominé */
        return;
    }
    /* Marquer les descendants stricts de l'ensemble, en une seule descente
     * partagée : O(V + E) au lieu d'un parcours par élément. */
    dyn->epochB++;
    int sp = 0;
    for (int i = 0; i < list_count; i++) {
        IntVec *lo = &dyn->lower[sel_list[i]];
        for (int j = 0; j < lo->len; j++) {
            int w = lo->data[j];
            if (dyn->markB[w] == dyn->epochB) continue;
            dyn->markB[w] = dyn->epochB;
            dyn_ensure_stack(dyn, sp + 1);
            dyn->stack[sp++] = w;
        }
    }
    while (sp > 0) {
        int v = dyn->stack[--sp];
        IntVec *lo = &dyn->lower[v];
        for (int j = 0; j < lo->len; j++) {
            int w = lo->data[j];
            if (dyn->markB[w] == dyn->epochB) continue;
            dyn->markB[w] = dyn->epochB;
            dyn_ensure_stack(dyn, sp + 1);
            dyn->stack[sp++] = w;
        }
    }
    dyn_ensure_sel(dyn, list_count);
    for (int i = 0; i < list_count; i++) {
        int v = sel_list[i];
        if (dyn->markB[v] != dyn->epochB) dyn->sel[dyn->sel_count++] = v;
    }
    (void)sel_flags;
}

void dyn_maximal_selected_descendants(DynOrder *dyn, int c,
                                     const unsigned char *sel_flags) {
    /* Descente 1 : collecter les sélectionnés parmi les descendants de c.
     * On ne peut pas s'arrêter en rencontrant un sélectionné : un autre
     * sélectionné situé plus bas peut être atteignable par un chemin qui
     * l'évite, et serait alors manqué. */
    dyn->epochA++;
    dyn->sel_count = 0;
    int sp = 0;
    dyn_ensure_stack(dyn, 1);
    dyn->stack[sp++] = c;
    dyn->markA[c] = dyn->epochA;
    while (sp > 0) {
        int v = dyn->stack[--sp];
        if (sel_flags[v]) {
            dyn_ensure_sel(dyn, dyn->sel_count + 1);
            dyn->sel[dyn->sel_count++] = v;
        }
        IntVec *lo = &dyn->lower[v];
        for (int i = 0; i < lo->len; i++) {
            int w = lo->data[i];
            if (dyn->markA[w] == dyn->epochA) continue;
            dyn->markA[w] = dyn->epochA;
            dyn_ensure_stack(dyn, sp + 1);
            dyn->stack[sp++] = w;
        }
    }
    /* Descente 2 : marquer les descendants stricts des sélectionnés, pour
     * écarter ceux qui en dominent un autre. */
    dyn->epochB++;
    sp = 0;
    for (int i = 0; i < dyn->sel_count; i++) {
        IntVec *lo = &dyn->lower[dyn->sel[i]];
        for (int j = 0; j < lo->len; j++) {
            int w = lo->data[j];
            if (dyn->markB[w] == dyn->epochB) continue;
            dyn->markB[w] = dyn->epochB;
            dyn_ensure_stack(dyn, sp + 1);
            dyn->stack[sp++] = w;
        }
    }
    while (sp > 0) {
        int v = dyn->stack[--sp];
        IntVec *lo = &dyn->lower[v];
        for (int j = 0; j < lo->len; j++) {
            int w = lo->data[j];
            if (dyn->markB[w] == dyn->epochB) continue;
            dyn->markB[w] = dyn->epochB;
            dyn_ensure_stack(dyn, sp + 1);
            dyn->stack[sp++] = w;
        }
    }
}

int *dyn_to_flat(DynOrder *dyn, int *out_len) {
    /* Compaction : les identifiants transmis à Java doivent être 0..N-1 sans
     * trou, alors que les concepts supprimés laissent des vides. */
    int *newid = (int*)malloc((size_t)dyn->n * sizeof(int));
    int N = 0;
    for (int c = 0; c < dyn->n; c++) {
        newid[c] = dyn->alive[c] ? N++ : -1;
    }

    int E = 0;
    for (int c = 0; c < dyn->n; c++) {
        if (dyn->alive[c]) E += dyn->upper[c].len;
    }

    long total = 2 + (long)2 * E;
    for (int c = 0; c < dyn->n; c++) {
        if (!dyn->alive[c]) continue;
        total += 1 + bs_card(DYN_REXT(dyn, c), dyn->wo);
        total += 1 + bs_card(DYN_RINT(dyn, c), dyn->wa);
    }

    int *buf = (int*)malloc((size_t)total * sizeof(int));
    if (!buf) { free(newid); *out_len = 0; return NULL; }

    long p = 0;
    buf[p++] = N;
    buf[p++] = E;
    for (int c = 0; c < dyn->n; c++) {
        if (!dyn->alive[c]) continue;
        IntVec *up = &dyn->upper[c];
        for (int i = 0; i < up->len; i++) {
            buf[p++] = newid[c];
            buf[p++] = newid[up->data[i]];
        }
    }
    for (int c = 0; c < dyn->n; c++) {
        if (!dyn->alive[c]) continue;
        /* Parcours PAR MOTS, pas bit à bit. Tester chaque indice de 0 à |G| pour
         * chaque concept coûte N x |G| : 85,8 M de tests sur ord6magic04, pour
         * au plus 19 020 bits réellement positionnés — un objet n'appartient
         * qu'à un seul extent réduit. Le profil natif attribuait 29,5 % du temps
         * total à cette boucle, plus que WorkOnLeftPart2. Ici le coût devient
         * N x |G|/64 lectures de mots plus une extraction par bit présent.
         * Les indices sortent croissants, comme avant : le format plat et les
         * harnais qui le comparent en dépendent. */
        const aword *re = DYN_REXT(dyn, c);
        buf[p++] = bs_card(re, dyn->wo);
        for (int w = 0; w < dyn->wo; w++) {
            aword x = re[w];
            while (x) {
                buf[p++] = (w << 6) + AW_CTZ(x);
                x &= x - 1;
            }
        }
        const aword *ri = DYN_RINT(dyn, c);
        buf[p++] = bs_card(ri, dyn->wa);
        for (int w = 0; w < dyn->wa; w++) {
            aword x = ri[w];
            while (x) {
                buf[p++] = (w << 6) + AW_CTZ(x);
                x &= x - 1;
            }
        }
    }
    free(newid);
    *out_len = (int)p;
    return buf;
}
