/* bench_sets.c — comparaison directe des deux représentations de set
 * disponibles côté C pour le domaine ATTRIBUTS de LinCbO :
 *
 *   - roaring_bitmap_t  : algo/lincbo.c (l'oracle existant, non modifié)
 *   - bitset.h dense    : algo/lincbo_pruning.c, mode LINCBO_PRUNE_NONE
 *
 * Les deux calculent EXACTEMENT le même algorithme (LinCbO sans élagage,
 * même ordre d'énumération B/Z/y) — seule la structure de données qui
 * représente B, Z, D, premise/conclusion pour les ATTRIBUTS change. C'est
 * donc une comparaison à un seul facteur, pas un bench qui mélange
 * plusieurs différences.
 *
 * But : trancher s'il faut exposer un choix de backend dans la commande
 * DG_BASIS, ou si l'un des deux domine assez largement sur la plage de
 * tailles réaliste (attributs = dizaines à quelques milliers) pour ne
 * garder qu'un seul chemin de code.
 *
 * Note taille des instances : avec un contexte aléatoire dense (densité
 * fixe) le nombre de concepts/implications croît très vite avec le nombre
 * d'attributs et peut devenir intraitable pour les DEUX moteurs (ce n'est
 * pas un artefact roaring-vs-bitset, juste la combinatoire de la base
 * canonique). Pour explorer de grands univers d'attributs sans tomber dans
 * ce mur, on fait décroître la densité avec n pour garder le nombre moyen
 * d'attributs actifs par objet à peu près constant — plus représentatif
 * d'un contexte réel (texte, panier d'achat) à grand nombre d'attributs.
 *
 * Compilation (optimisée, sans sanitizer — c'est un bench de temps) :
 *   gcc -O2 -DNDEBUG -I. -o bench_sets bench_sets.c \
 *       algo/lincbo.c algo/lincbo_pruning.c core/*.c croaring/roaring.c -lm -lpthread
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#ifdef _WIN32
#include <windows.h>
#endif
#include "core/context.h"
#include "core/implication.h"
#include "algo/lincbo.h"
#include "algo/lincbo_pruning.h"

/* clock() vaut CLOCKS_PER_SEC=1000 sous MinGW mais sa résolution réelle
 * reste calée sur le timer système (~15.6 ms par défaut sous Windows) : les
 * cas de quelques ms se lisaient "0.000 ms", et un ratio roaring/dense avec
 * un dénominateur à 0 produisait des speedups à 10 chiffres. timespec_get
 * (C11) aurait dû régler ça mais n'est pas déclaré sur toutes les
 * distributions mingw-w64 malgré -std=c11 — pas assez universel. On repart
 * sur les API natives, sous-milliseconde des deux côtés : QPC sous Windows
 * (disponible depuis XP, aucune dépendance runtime C11),
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

#define WALL_BUDGET_SEC 90.0

static double g_wall_start;
static int wall_exceeded(void) {
    return (now_ms() - g_wall_start) > (WALL_BUDGET_SEC * 1000.0);
}

static int count_basis(const char *json) {
    const char *p = strstr(json, "\"implications\":");
    return p ? atoi(p + strlen("\"implications\":")) : -1;
}

/* rand_r() est POSIX, absent de MinGW (échoue à la compilation sous
 * Windows). xorshift32 est un PRNG minimal à état explicite, même usage,
 * sans dépendre de l'OS. */
static unsigned int xorshift32(unsigned int *state) {
    unsigned int x = *state;
    x ^= x << 13; x ^= x >> 17; x ^= x << 5;
    return *state = x;
}

static BinaryContext *make_random_ctx(int nobj, int nattr, double density, unsigned int seed) {
    BinaryContext *ctx = ctx_create(nobj, nattr, "bench");
    char buf[16];
    for (int a = 0; a < nattr; a++) { snprintf(buf, 16, "a%d", a); ctx_add_attr_name(ctx, buf); }
    unsigned int s = seed ? seed : 1; /* xorshift32 reste bloqué à 0 si la graine est 0 */
    for (int o = 0; o < nobj; o++)
        for (int a = 0; a < nattr; a++)
            if ((double)xorshift32(&s) / 4294967295.0 < density) ctx_set(ctx, o, a, true);
    return ctx;
}

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

typedef struct { double roaring_ms, dense_ms; int impl_roaring, impl_dense; } Result;

static Result bench_once(BinaryContext *ctx, int reps) {
    Result r = {0, 0, -1, -1};
    double best_roaring = 1e18, best_dense = 1e18;
    for (int k = 0; k < reps; k++) {
        double t0 = now_ms();
        char *jr = run_lincbo_impl(ctx);
        double ms = now_ms() - t0;
        if (ms < best_roaring) best_roaring = ms;
        r.impl_roaring = count_basis(jr);
        free(jr);
        if (ms > 5000.0) break; /* cas deja tres couteux : un seul echantillon suffit */
    }
    for (int k = 0; k < reps; k++) {
        double t0 = now_ms();
        char *jd = run_lincbo_pruning_json(ctx, LINCBO_PRUNE_NONE);
        double ms = now_ms() - t0;
        if (ms < best_dense) best_dense = ms;
        r.impl_dense = count_basis(jd);
        free(jd);
        if (ms > 5000.0) break;
    }
    r.roaring_ms = best_roaring;
    r.dense_ms = best_dense;
    return r;
}

static void report(const char *label, int nobj, int nattr, double density, Result r) {
    const char *check = (r.impl_roaring == r.impl_dense) ? "ok" : "MISMATCH";
    /* En dessous de ~0.05 ms les deux mesures sont dominées par le bruit de
     * l'horloge (et du malloc/free du JSON), pas par un vrai écart : un
     * ratio y serait un artefact, pas une mesure — mieux vaut l'annoncer. */
    char speedup_str[16];
    if (r.roaring_ms < 0.05 && r.dense_ms < 0.05) snprintf(speedup_str, sizeof(speedup_str), "   n/a");
    else snprintf(speedup_str, sizeof(speedup_str), "%5.2fx", r.roaring_ms / (r.dense_ms > 0.001 ? r.dense_ms : 0.001));
    printf("%-22s %5dx%-5d d=%.3f  roaring=%10.3fms  dense=%10.3fms  speedup=%s  impl=%d/%d [%s]\n",
           label, nobj, nattr, density, r.roaring_ms, r.dense_ms, speedup_str, r.impl_roaring, r.impl_dense, check);
    fflush(stdout);
}

int main(void) {
#ifdef _WIN32
    SetConsoleOutputCP(CP_UTF8); /* évite le "—" en mojibake dans cmd.exe */
#endif
    g_wall_start = now_ms();

    printf("=== attributs : petit -> grand, ~8 attributs actifs/objet en moyenne (densite decroissante) ===\n");
    {
        int nattrs[] = {10, 20, 40, 80, 150, 300, 600, 1200, 2500, 5000};
        for (size_t i = 0; i < sizeof(nattrs)/sizeof(nattrs[0]); i++) {
            if (wall_exceeded()) { printf("  (arret : budget de temps depasse, cas restants sautes)\n"); break; }
            int n = nattrs[i];
            int nobj = 30;
            double density = n <= 40 ? 0.30 : (8.0 / n);
            if (density > 0.30) density = 0.30;
            BinaryContext *ctx = make_random_ctx(nobj, n, density, 1000 + (unsigned)n);
            Result r = bench_once(ctx, n > 600 ? 1 : 3);
            char label[32]; snprintf(label, 32, "rand nattr=%d", n);
            report(label, nobj, n, density, r);
            ctx_free(ctx);
        }
    }

    printf("\n=== densite variable, taille fixe (30x60) — zoom sur la zone de croisement ===\n");
    {
        /* on s'arrete a 0.50 : au-dela le nombre de concepts explose de
         * façon combinatoire (comportement connu de LinCbO sur contexte
         * quasi-plein, pas specifique a un backend) et rend le cas
         * intraitable pour les deux moteurs en un temps de bench raisonnable. */
        double densities[] = {0.05, 0.15, 0.30, 0.35, 0.40, 0.45, 0.50};
        for (size_t i = 0; i < sizeof(densities)/sizeof(densities[0]); i++) {
            if (wall_exceeded()) { printf("  (arret : budget de temps depasse, cas restants sautes)\n"); break; }
            BinaryContext *ctx = make_random_ctx(30, 60, densities[i], 42);
            Result r = bench_once(ctx, densities[i] >= 0.40 ? 2 : 5);
            char label[32]; snprintf(label, 32, "rand d=%.2f", densities[i]);
            report(label, 30, 60, densities[i], r);
            ctx_free(ctx);
        }
    }

    printf("\n=== objets : petit -> grand, attributs fixes (40), densite 0.3 ===\n");
    {
        int nobjs[] = {10, 30, 60, 120, 250};
        for (size_t i = 0; i < sizeof(nobjs)/sizeof(nobjs[0]); i++) {
            if (wall_exceeded()) { printf("  (arret : budget de temps depasse, cas restants sautes)\n"); break; }
            int no = nobjs[i];
            BinaryContext *ctx = make_random_ctx(no, 40, 0.30, 7000 + (unsigned)no);
            Result r = bench_once(ctx, 2);
            char label[32]; snprintf(label, 32, "rand nobj=%d", no);
            report(label, no, 40, 0.30, r);
            ctx_free(ctx);
        }
    }

    printf("\n=== contexte grille (structure, plage de tailles) ===\n");
    {
        int sizes[][2] = {{4,4},{6,6},{8,8},{10,10},{14,14}};
        for (size_t i = 0; i < sizeof(sizes)/sizeof(sizes[0]); i++) {
            if (wall_exceeded()) { printf("  (arret : budget de temps depasse, cas restants sautes)\n"); break; }
            int R = sizes[i][0], C = sizes[i][1];
            BinaryContext *ctx = make_grid_ctx(R, C);
            Result r = bench_once(ctx, 3);
            char label[32]; snprintf(label, 32, "grid %dx%d", R, C);
            report(label, R*C, R+C, -1.0, r);
            ctx_free(ctx);
        }
    }

    printf("\n=== sparse (attributs tres nombreux, ~3 actifs/objet) ===\n");
    {
        int nattrs[] = {200, 500, 1000, 2000, 5000, 10000};
        for (size_t i = 0; i < sizeof(nattrs)/sizeof(nattrs[0]); i++) {
            if (wall_exceeded()) { printf("  (arret : budget de temps depasse, cas restants sautes)\n"); break; }
            int n = nattrs[i];
            double density = 3.0 / n;
            BinaryContext *ctx = make_random_ctx(20, n, density, 55555 + (unsigned)n);
            Result r = bench_once(ctx, 1);
            char label[32]; snprintf(label, 32, "sparse nattr=%d", n);
            report(label, 20, n, density, r);
            ctx_free(ctx);
        }
    }

    return 0;
}
