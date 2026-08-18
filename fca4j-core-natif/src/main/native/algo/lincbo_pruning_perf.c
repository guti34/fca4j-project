/* perf_main.c — sanity check (pas un vrai benchmark) : les trois modes
 * donnent-ils un nombre d'implications cohérent, et l'élagage réduit-il
 * bien le temps/la récursion sur un contexte plus structuré (pas juste du
 * bruit aléatoire, où l'élagage a peu de prise) ? */
#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#ifdef _WIN32
#include <windows.h>
#endif
#include "core/context.h"
#include "core/implication.h"
#include "algo/lincbo.h"
#include "algo/lincbo_pruning.h"

/* clock() a une résolution réelle calée sur le timer système (~15.6 ms par
 * défaut sous MinGW/Windows) : des cas de quelques ms se lisaient "0.00 ms".
 * timespec_get (C11) aurait dû régler ça mais n'est pas déclaré sur toutes
 * les distributions mingw-w64 malgré -std=c11 — pas assez universel. On
 * repart sur les API natives, sous-milliseconde des deux côtés : QPC sous
 * Windows (disponible depuis XP, aucune dépendance runtime C11),
 * clock_gettime(CLOCK_MONOTONIC) ailleurs (POSIX, glibc/macOS). */
#if defined(_WIN32)
static double now_ms(void) {
    static LARGE_INTEGER freq;
    static int init = 0;
    if (!init) { QueryPerformanceFrequency(&freq); init = 1; }
    LARGE_INTEGER t;
    QueryPerformanceCounter(&t);
    return (double)t.QuadPart * 1000.0 / (double)freq.QuadPart;
}
#else
static double now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (double)ts.tv_sec * 1000.0 + (double)ts.tv_nsec / 1e6;
}
#endif

/* Contexte "grille" : objets = paires (i,j) sur une grille RxC, attribut
 * "row_i" vrai si l'objet est sur la ligne i, "col_j" vrai si sur la
 * colonne j. Beaucoup de structure (dépendances entre attributs), ce qui
 * donne plus de prise à l'élagage qu'un contexte purement aléatoire. */
static BinaryContext *make_grid_ctx(int R, int C) {
    int nobj = R * C, nattr = R + C;
    BinaryContext *ctx = ctx_create(nobj, nattr, "grid");
    char buf[16];
    for (int i = 0; i < R; i++) { snprintf(buf, 16, "row_%d", i); ctx_add_attr_name(ctx, buf); }
    for (int j = 0; j < C; j++) { snprintf(buf, 16, "col_%d", j); ctx_add_attr_name(ctx, buf); }
    for (int i = 0; i < R; i++)
        for (int j = 0; j < C; j++) {
            int o = i * C + j;
            ctx_set(ctx, o, i, true);
            ctx_set(ctx, o, R + j, true);
        }
    return ctx;
}

static void bench_one(const char *label, BinaryContext *ctx, LinCboPruneMode mode) {
    double t0 = now_ms();
    ImplVec r = run_lincbo_pruning(ctx, mode);
    double ms = now_ms() - t0;
    printf("  %-24s: %5d implications, %7.2f ms\n", label, r.len, ms);
    implvec_free_all(&r);
}

int main(void) {
#ifdef _WIN32
    SetConsoleOutputCP(CP_UTF8);
#endif
    printf("=== contexte aleatoire 40x24, densite 0.35 ===\n");
    {
        BinaryContext *ctx = ctx_create(40, 24, "rand");
        char buf[8];
        for (int a = 0; a < 24; a++) { snprintf(buf, 8, "a%d", a); ctx_add_attr_name(ctx, buf); }
        srand(42);
        for (int o = 0; o < 40; o++)
            for (int a = 0; a < 24; a++)
                if ((double)rand() / RAND_MAX < 0.35) ctx_set(ctx, o, a, true);
        bench_one("NONE (sans elagage)", ctx, LINCBO_PRUNE_NONE);
        bench_one("LIFO (elagage simple)", ctx, LINCBO_PRUNE_LIFO);
        bench_one("LCM  (elagage trie)", ctx, LINCBO_PRUNE_LCM);
        ctx_free(ctx);
    }

    printf("\n=== contexte grille 6x6 (structure, 12 attributs, 36 objets) ===\n");
    {
        BinaryContext *ctx = make_grid_ctx(6, 6);
        bench_one("NONE (sans elagage)", ctx, LINCBO_PRUNE_NONE);
        bench_one("LIFO (elagage simple)", ctx, LINCBO_PRUNE_LIFO);
        bench_one("LCM  (elagage trie)", ctx, LINCBO_PRUNE_LCM);
        ctx_free(ctx);
    }

    printf("\n=== cas limites ===\n");
    {
        /* attribut toujours vrai + attribut toujours faux + objet vide */
        BinaryContext *ctx = ctx_create(4, 3, "edge");
        ctx_add_attr_name(ctx, "always"); ctx_add_attr_name(ctx, "never"); ctx_add_attr_name(ctx, "mixed");
        for (int o = 0; o < 4; o++) ctx_set(ctx, o, 0, true); /* "always" vrai partout */
        ctx_set(ctx, 0, 2, true); ctx_set(ctx, 1, 2, true);   /* "mixed" vrai sur 2 objets */
        for (LinCboPruneMode m = LINCBO_PRUNE_NONE; m <= LINCBO_PRUNE_LCM; m++) {
            ImplVec r = run_lincbo_pruning(ctx, m);
            printf("  mode %d: %d implications\n", m, r.len);
            implvec_free_all(&r);
        }
        ctx_free(ctx);

        /* 1 objet, 1 attribut */
        BinaryContext *ctx2 = ctx_create(1, 1, "tiny1x1");
        ctx_add_attr_name(ctx2, "x");
        ctx_set(ctx2, 0, 0, true);
        for (LinCboPruneMode m = LINCBO_PRUNE_NONE; m <= LINCBO_PRUNE_LCM; m++) {
            ImplVec r = run_lincbo_pruning(ctx2, m);
            printf("  1x1 mode %d: %d implications\n", m, r.len);
            implvec_free_all(&r);
        }
        ctx_free(ctx2);

        /* 0 attribut */
        BinaryContext *ctx3 = ctx_create(3, 0, "noattrs");
        for (LinCboPruneMode m = LINCBO_PRUNE_NONE; m <= LINCBO_PRUNE_LCM; m++) {
            ImplVec r = run_lincbo_pruning(ctx3, m);
            printf("  0-attr mode %d: %d implications\n", m, r.len);
            implvec_free_all(&r);
        }
        ctx_free(ctx3);
    }
    return 0;
}
