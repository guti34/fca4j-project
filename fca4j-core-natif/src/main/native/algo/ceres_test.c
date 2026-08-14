/*
 * ceres_test.c — validation différentielle du portage C de CERES
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 *
 * Compare la sortie de run_ceres_flat à celle de run_hermes_flat, qui construit
 * le même AOC-poset par un tout autre chemin. La comparaison se fait modulo
 * numérotation : les deux algorithmes attribuent les identifiants dans un ordre
 * qui leur est propre.
 *
 * Le témoin est la signature (rextent, rintent) de chaque concept. Elle est
 * discriminante : dans un AOC-poset, un objet est introduit par un seul concept
 * et un attribut aussi, donc deux concepts distincts ne peuvent pas partager la
 * même paire — sauf s'ils ont l'un et l'autre les deux ensembles vides, cas que
 * le test signale explicitement au lieu de l'ignorer.
 *
 * Trois phases : balayage exhaustif des petits contextes, contextes aléatoires,
 * puis contextes plus larges dont le seul but est de faire dépasser les
 * capacités initiales et donc de déclencher les réallocations. Cette dernière
 * phase compte : un pointeur d'extent conservé au travers d'une création de
 * concept est invalide après realloc, et le bug ne se manifeste pas sur les
 * petits contextes.
 *
 * Compilation (depuis src/main/native) :
 *   gcc -std=c11 -O2 -Wall -Wextra -o ceres_test \
 *       algo/ceres_test.c algo/ceres.c core/dynorder.c algo/hermes.c \
 *       core/context.c core/strbuf.c core/conceptorder.c core/clarification.c \
 *       core/graph.c core/tarjan.c core/closure.c core/implication.c \
 *       croaring/roaring.c -lm
 */
#include "ceres.h"
#include "hermes.h"
#include "../core/context.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* ── flux pseudo-aléatoire reproductible, indépendant de la libc ──────── */
static uint64_t rng_state;
static void rng_seed(uint64_t s) { rng_state = s ? s : 0x9E3779B97F4A7C15ULL; }
static uint64_t rng_next(void) {
    uint64_t x = rng_state;
    x ^= x << 13; x ^= x >> 7; x ^= x << 17;
    rng_state = x;
    return x;
}
static int rng_below(int n) { return (int)(rng_next() % (uint64_t)n); }

/* ── lecture du format plat ───────────────────────────────────────────── */

typedef struct {
    int N, E;
    int *edges;      /* 2E entiers */
    int **re, *re_n; /* rextent par concept */
    int **ri, *ri_n; /* rintent par concept */
} Flat;

static Flat *flat_parse(const int *buf) {
    Flat *f = (Flat*)calloc(1, sizeof(Flat));
    long p = 0;
    f->N = buf[p++];
    f->E = buf[p++];
    f->edges = (int*)malloc((size_t)(2 * f->E > 0 ? 2 * f->E : 1) * sizeof(int));
    for (int i = 0; i < 2 * f->E; i++) f->edges[i] = buf[p++];
    f->re = (int**)calloc((size_t)(f->N > 0 ? f->N : 1), sizeof(int*));
    f->ri = (int**)calloc((size_t)(f->N > 0 ? f->N : 1), sizeof(int*));
    f->re_n = (int*)calloc((size_t)(f->N > 0 ? f->N : 1), sizeof(int));
    f->ri_n = (int*)calloc((size_t)(f->N > 0 ? f->N : 1), sizeof(int));
    for (int c = 0; c < f->N; c++) {
        int k = buf[p++];
        f->re_n[c] = k;
        f->re[c] = (int*)malloc((size_t)(k > 0 ? k : 1) * sizeof(int));
        for (int i = 0; i < k; i++) f->re[c][i] = buf[p++];
        k = buf[p++];
        f->ri_n[c] = k;
        f->ri[c] = (int*)malloc((size_t)(k > 0 ? k : 1) * sizeof(int));
        for (int i = 0; i < k; i++) f->ri[c][i] = buf[p++];
    }
    return f;
}

static void flat_free(Flat *f) {
    for (int c = 0; c < f->N; c++) { free(f->re[c]); free(f->ri[c]); }
    free(f->re); free(f->ri); free(f->re_n); free(f->ri_n);
    free(f->edges); free(f);
}

/* Les éléments sortent déjà croissants du sérialiseur ; on écrit la signature
 * telle quelle. */
static void flat_signature(const Flat *f, int c, char *out, size_t cap) {
    size_t p = 0;
    p += (size_t)snprintf(out + p, cap - p, "R");
    for (int i = 0; i < f->re_n[c] && p < cap; i++)
        p += (size_t)snprintf(out + p, cap - p, ".%d", f->re[c][i]);
    if (p < cap) p += (size_t)snprintf(out + p, cap - p, "|I");
    for (int i = 0; i < f->ri_n[c] && p < cap; i++)
        p += (size_t)snprintf(out + p, cap - p, ".%d", f->ri[c][i]);
}

static int cmp_int_pair(const void *a, const void *b) {
    const int *x = (const int*)a, *y = (const int*)b;
    if (x[0] != y[0]) return x[0] - y[0];
    return x[1] - y[1];
}

#define SIGCAP 4096

/*
 * Renvoie NULL si les deux AOC-posets coïncident, sinon un message décrivant
 * le premier écart trouvé (mémoire statique, valide jusqu'au prochain appel).
 */
static const char *compare_flat(const Flat *fa, const Flat *fh) {
    static char msg[512];

    if (fa->N != fh->N) {
        snprintf(msg, sizeof(msg), "nombre de concepts : ceres=%d hermes=%d", fa->N, fh->N);
        return msg;
    }
    if (fa->E != fh->E) {
        snprintf(msg, sizeof(msg), "nombre d'aretes : ceres=%d hermes=%d", fa->E, fh->E);
        return msg;
    }
    int N = fa->N;
    if (N == 0) return NULL;

    char *sa = (char*)malloc((size_t)N * SIGCAP);
    char *sh = (char*)malloc((size_t)N * SIGCAP);
    for (int c = 0; c < N; c++) {
        flat_signature(fa, c, sa + (size_t)c * SIGCAP, SIGCAP);
        flat_signature(fh, c, sh + (size_t)c * SIGCAP, SIGCAP);
    }

    /* signatures dupliquees : le temoin ne serait plus discriminant */
    for (int i = 0; i < N; i++)
        for (int j = i + 1; j < N; j++)
            if (strcmp(sa + (size_t)i * SIGCAP, sa + (size_t)j * SIGCAP) == 0) {
                snprintf(msg, sizeof(msg),
                         "signature dupliquee cote ceres (concepts %d et %d) : %s",
                         i, j, sa + (size_t)i * SIGCAP);
                free(sa); free(sh);
                return msg;
            }

    /* correspondance ares -> hermes */
    int *map = (int*)malloc((size_t)N * sizeof(int));
    for (int i = 0; i < N; i++) {
        map[i] = -1;
        for (int j = 0; j < N; j++)
            if (strcmp(sa + (size_t)i * SIGCAP, sh + (size_t)j * SIGCAP) == 0) { map[i] = j; break; }
        if (map[i] < 0) {
            snprintf(msg, sizeof(msg), "concept sans equivalent chez hermes : %s",
                     sa + (size_t)i * SIGCAP);
            free(sa); free(sh); free(map);
            return msg;
        }
    }

    /* aretes, traduites puis triees */
    int E = fa->E;
    int *ea = (int*)malloc((size_t)(2 * E > 0 ? 2 * E : 1) * sizeof(int));
    int *eh = (int*)malloc((size_t)(2 * E > 0 ? 2 * E : 1) * sizeof(int));
    for (int i = 0; i < E; i++) {
        ea[2 * i]     = map[fa->edges[2 * i]];
        ea[2 * i + 1] = map[fa->edges[2 * i + 1]];
        eh[2 * i]     = fh->edges[2 * i];
        eh[2 * i + 1] = fh->edges[2 * i + 1];
    }
    qsort(ea, (size_t)E, 2 * sizeof(int), cmp_int_pair);
    qsort(eh, (size_t)E, 2 * sizeof(int), cmp_int_pair);
    const char *res = NULL;
    for (int i = 0; i < E; i++) {
        if (ea[2 * i] != eh[2 * i] || ea[2 * i + 1] != eh[2 * i + 1]) {
            snprintf(msg, sizeof(msg),
                     "arete divergente au rang %d : ceres (%d,%d) hermes (%d,%d)",
                     i, ea[2 * i], ea[2 * i + 1], eh[2 * i], eh[2 * i + 1]);
            res = msg;
            break;
        }
    }
    free(ea); free(eh); free(map); free(sa); free(sh);
    return res;
}

/* ── exécution sur un contexte ────────────────────────────────────────── */

static void print_matrix(const signed char *m, int n, int p) {
    printf("    contexte %dx%d :\n", n, p);
    for (int i = 0; i < n; i++) {
        printf("      ");
        for (int j = 0; j < p; j++) printf("%d", m[i * p + j]);
        printf("\n");
    }
}

/* Renvoie 1 si le contexte passe. */
static int run_one(const signed char *m, int n, int p, int verbose) {
    BinaryContext *c1 = ctx_from_matrix(n, p, m, "t");
    BinaryContext *c2 = ctx_from_matrix(n, p, m, "t");
    int la = 0, lh = 0;
    int *ba = run_ceres_flat(c1, &la);
    int *bh = run_hermes_flat(c2, &lh);
    int ok = 1;
    if (!ba || !bh) {
        printf("  ECHEC : sortie nulle (ceres=%p hermes=%p)\n", (void*)ba, (void*)bh);
        ok = 0;
    } else {
        Flat *fa = flat_parse(ba), *fh = flat_parse(bh);
        const char *err = compare_flat(fa, fh);
        if (err) {
            printf("  ECHEC : %s\n", err);
            if (verbose) print_matrix(m, n, p);
            ok = 0;
        }
        flat_free(fa); flat_free(fh);
    }
    free(ba); free(bh);
    ctx_free(c1); ctx_free(c2);
    return ok;
}

int main(int argc, char **argv) {
    unsigned long long seed = (argc > 1) ? strtoull(argv[1], NULL, 10) : 20260709ULL;
    int rounds = (argc > 2) ? atoi(argv[2]) : 20000;
    int failures = 0, total = 0;

    printf("=== phase 1 : balayage exhaustif (au plus 12 cellules) ===\n");
    for (int n = 1; n <= 12; n++) {
        for (int p = 1; p <= 12; p++) {
            if (n * p > 12) continue;
            int cells = n * p;
            long combos = 1L << cells;
            signed char *m = (signed char*)malloc((size_t)cells);
            for (long mask = 0; mask < combos; mask++) {
                for (int k = 0; k < cells; k++) m[k] = (mask >> k) & 1;
                total++;
                if (!run_one(m, n, p, 1)) {
                    failures++;
                    if (failures > 5) { free(m); goto done; }
                }
            }
            free(m);
        }
    }
    printf("  %d contextes balayes\n", total);

    printf("=== phase 2 : contextes aleatoires (graine %llu, %d tirages) ===\n",
           seed, rounds);
    rng_seed(seed);
    for (int r = 0; r < rounds; r++) {
        int n = 1 + rng_below(8);
        int p = 1 + rng_below(8);
        int density = 1 + rng_below(9);   /* 10 % a 90 % */
        signed char *m = (signed char*)malloc((size_t)(n * p));
        for (int k = 0; k < n * p; k++) m[k] = (rng_below(10) < density) ? 1 : 0;
        total++;
        if (!run_one(m, n, p, 1)) {
            printf("    (tirage %d)\n", r);
            failures++;
            if (failures > 5) { free(m); goto done; }
        }
        free(m);
    }
    printf("  %d tirages\n", rounds);

    printf("=== phase 3 : contextes larges (declenchement des reallocations) ===\n");
    for (int r = 0; r < 200; r++) {
        int n = 40 + rng_below(120);
        int p = 20 + rng_below(40);
        int density = 2 + rng_below(7);
        signed char *m = (signed char*)malloc((size_t)(n * p));
        for (int k = 0; k < n * p; k++) m[k] = (rng_below(10) < density) ? 1 : 0;
        total++;
        if (!run_one(m, n, p, 0)) {
            printf("    (contexte large %d : %dx%d densite %d0%%)\n", r, n, p, density);
            failures++;
            if (failures > 5) { free(m); goto done; }
        }
        free(m);
    }
    printf("  200 contextes larges\n");

done:
    printf("\n%d contextes testes, %d echecs\n", total, failures);
    printf("%s\n", failures == 0 ? "ceres : conforme a hermes" : "ceres : DIVERGENCES");
    return failures == 0 ? 0 : 1;
}
