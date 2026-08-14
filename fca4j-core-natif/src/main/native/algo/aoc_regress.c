/* aoc_regress.c — signature canonique des AOC-posets natifs
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 *
 * POURQUOI CE HARNAIS EXISTE
 *
 * ares_test et ceres_test comparent leur algorithme A HERMES, pris pour oracle.
 * C'est valable tant qu'Hermes ne bouge pas. Or la campagne d'optimisation qui
 * vient de faire gagner 8,2x au Hermes Java doit maintenant etre transposee a
 * hermes.c — et des lors, Hermes ne peut plus se valider lui-meme, ni valider
 * les autres : une derive commune passerait inapercue.
 *
 * Ce harnais enregistre donc une signature INDEPENDANTE de tout oracle : il
 * decrit ce que l'algorithme produit aujourd'hui, et signale toute difference
 * demain. Meme principe que latticecbo_regress, applique aux quatre AOC-posets.
 *
 * LA REFERENCE DOIT ETRE PRODUITE AVANT LE PREMIER CHANGEMENT. Une reference
 * generee apres coup enregistrerait le comportement qu'on cherche a controler.
 * L'erreur a deja ete commise une fois sur latticecbo_ref.txt.
 *
 * CE QUE LA SIGNATURE CAPTURE
 *
 * Le tableau plat ne porte que les extents et intents REDUITS. L'extent complet
 * se reconstruit par extent(c) = rextent(c) U { extent(d) : d enfant de c }, en
 * ordre topologique. Les extents caracterisant les concepts d'un AOC-poset, la
 * signature est le hache du multiensemble des extents et du multiensemble des
 * couples d'extents relies par une arete. Elle est donc INVARIANTE PAR
 * RENUMEROTATION : deux executions produisant le meme AOC-poset donnent la meme
 * signature, quel que soit l'ordre d'attribution des identifiants.
 *
 * C'est essentiel ici : les optimisations envisagees changent l'ordre de
 * parcours (marquage en tableau plat, invariants hisses), donc potentiellement
 * la numerotation, sans changer le resultat. Une comparaison octet a octet du
 * tableau plat crierait au loup ; celle-ci ne le fera que s'il y a un loup.
 *
 * Les intents reduits sont egalement haches, separement : deux AOC-posets de
 * memes extents mais d'intents differents seraient distingues.
 *
 * Usage :
 *   aoc_regress                 genere les signatures et les affiche
 *   aoc_regress ref.txt         compare a une reference
 *
 * Creation de la reference :
 *   aoc_regress > aoc_ref.txt
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "algo/hermes.h"
#include "algo/ares.h"
#include "algo/ceres.h"
#include "algo/pluton.h"
#include "core/context.h"

#define NCASES 60

static unsigned long long rs;
static unsigned long long xs(void) {
    rs ^= rs << 13; rs ^= rs >> 7; rs ^= rs << 17; return rs;
}

static unsigned long long fnv(const unsigned char *p, size_t n) {
    unsigned long long h = 1469598103934665603ULL;
    for (size_t i = 0; i < n; i++) { h ^= p[i]; h *= 1099511628211ULL; }
    return h;
}
static int cmp_ull(const void *a, const void *b) {
    unsigned long long x = *(const unsigned long long*)a, y = *(const unsigned long long*)b;
    return x < y ? -1 : (x > y ? 1 : 0);
}

/* Signature invariante par renumerotation. Renvoie 0 et met *outN a -1 si le
 * graphe est incoherent (cycle ou arete pendante), ce qui est en soi un echec. */
static unsigned long long canon_signature(const int *flat, int nObj, int nAttr,
                                          int *outN, int *outE) {
    if (!flat) { *outN = 0; *outE = 0; return 0ULL; }
    int p = 0;
    int N = flat[p++], E = flat[p++];
    *outN = N; *outE = E;
    if (N <= 0) { return 0ULL; }

    int *ec = (int*)malloc((size_t)(E > 0 ? E : 1) * sizeof(int));
    int *ep = (int*)malloc((size_t)(E > 0 ? E : 1) * sizeof(int));
    for (int i = 0; i < E; i++) { ec[i] = flat[p++]; ep[i] = flat[p++]; }

    unsigned char *ext = (unsigned char*)calloc((size_t)N * (size_t)nObj, 1);
    unsigned char *rint = (unsigned char*)calloc((size_t)N * (size_t)(nAttr > 0 ? nAttr : 1), 1);

    /* CSR des enfants : cadj[cptr[c]..cptr[c+1]) = enfants de c */
    int *cptr = (int*)calloc((size_t)N + 1, sizeof(int));
    for (int i = 0; i < E; i++) cptr[ep[i] + 1]++;
    for (int c = 0; c < N; c++) cptr[c + 1] += cptr[c];
    int *cadj = (int*)malloc((size_t)(E > 0 ? E : 1) * sizeof(int));
    { int *cur = (int*)malloc((size_t)(N + 1) * sizeof(int));
      memcpy(cur, cptr, (size_t)(N + 1) * sizeof(int));
      for (int i = 0; i < E; i++) cadj[cur[ep[i]]++] = ec[i];
      free(cur); }

    for (int c = 0; c < N; c++) {
        int k = flat[p++];
        for (int j = 0; j < k; j++) { int o = flat[p++]; ext[(size_t)c * nObj + o] = 1; }
        int k2 = flat[p++];
        for (int j = 0; j < k2; j++) { int a = flat[p++]; rint[(size_t)c * (nAttr > 0 ? nAttr : 1) + a] = 1; }
    }

    /* Ordre topologique : un concept est pret quand tous ses enfants le sont. */
    int *pending = (int*)malloc((size_t)N * sizeof(int));
    for (int c = 0; c < N; c++) pending[c] = cptr[c + 1] - cptr[c];
    int *stack = (int*)malloc((size_t)N * sizeof(int));
    int sp = 0;
    for (int c = 0; c < N; c++) if (pending[c] == 0) stack[sp++] = c;

    int *pptr = (int*)calloc((size_t)N + 1, sizeof(int));
    for (int i = 0; i < E; i++) pptr[ec[i] + 1]++;
    for (int c = 0; c < N; c++) pptr[c + 1] += pptr[c];
    int *padj = (int*)malloc((size_t)(E > 0 ? E : 1) * sizeof(int));
    { int *cur = (int*)malloc((size_t)(N + 1) * sizeof(int));
      memcpy(cur, pptr, (size_t)(N + 1) * sizeof(int));
      for (int i = 0; i < E; i++) padj[cur[ec[i]]++] = ep[i];
      free(cur); }

    int done = 0;
    while (sp > 0) {
        int c = stack[--sp]; done++;
        unsigned char *dst = ext + (size_t)c * nObj;
        for (int k = cptr[c]; k < cptr[c + 1]; k++) {
            const unsigned char *src = ext + (size_t)cadj[k] * nObj;
            for (int o = 0; o < nObj; o++) dst[o] |= src[o];
        }
        for (int k = pptr[c]; k < pptr[c + 1]; k++)
            if (--pending[padj[k]] == 0) stack[sp++] = padj[k];
    }
    if (done != N) { *outN = -1; }   /* cycle : echec en soi */

    unsigned long long *h = (unsigned long long*)malloc((size_t)N * sizeof(unsigned long long));
    int wa = (nAttr > 0 ? nAttr : 1);
    for (int c = 0; c < N; c++) {
        unsigned long long he = fnv(ext + (size_t)c * nObj, (size_t)nObj);
        unsigned long long hi = fnv(rint + (size_t)c * wa, (size_t)wa);
        h[c] = he * 1000003ULL ^ (hi + 0x9e3779b97f4a7c15ULL);
    }

    unsigned long long *pairs = (unsigned long long*)malloc((size_t)(E > 0 ? E : 1) * sizeof(unsigned long long));
    for (int i = 0; i < E; i++) pairs[i] = h[ec[i]] * 1000003ULL ^ (h[ep[i]] + 0x9e3779b97f4a7c15ULL);

    unsigned long long *hs = (unsigned long long*)malloc((size_t)N * sizeof(unsigned long long));
    memcpy(hs, h, (size_t)N * sizeof(unsigned long long));
    qsort(hs, (size_t)N, sizeof(unsigned long long), cmp_ull);
    qsort(pairs, (size_t)(E > 0 ? E : 1), sizeof(unsigned long long), cmp_ull);

    unsigned long long sig = 1469598103934665603ULL;
    for (int c = 0; c < N; c++) { sig ^= hs[c]; sig *= 1099511628211ULL; }
    for (int i = 0; i < E; i++) { sig ^= pairs[i]; sig *= 1099511628211ULL; }

    free(ec); free(ep); free(ext); free(rint); free(cptr); free(cadj);
    free(pending); free(stack); free(pptr); free(padj); free(h); free(pairs); free(hs);
    return sig;
}

/* ── les quatre algorithmes ─────────────────────────────────────────────── */

typedef int *(*RunFlat)(BinaryContext *, int *);

typedef struct { const char *name; RunFlat run; } Algo;

static const Algo ALGOS[] = {
    { "hermes", run_hermes_flat },
    { "ares",   run_ares_flat   },
    { "ceres",  run_ceres_flat  },
    { "pluton", run_pluton_flat },
};

/* Les cas couvrent trois regimes, parce que la signature d'un defaut n'est pas
 * la meme selon la forme : beaucoup d'objets pour peu d'attributs, l'inverse,
 * et des contextes carres. Les densites vont de 10 a 55 %. */
static int gen_cases(FILE *out) {
    for (unsigned ai = 0; ai < sizeof(ALGOS) / sizeof(ALGOS[0]); ai++) {
        for (int t = 0; t < NCASES; t++) {
            rs = 1ULL + (unsigned long long)t * 2654435761ULL;
            int no, na;
            switch (t % 3) {
                case 0:  no = 60 + (int)(xs() % 240); na = 5  + (int)(xs() % 12); break;
                case 1:  no = 20 + (int)(xs() % 40);  na = 20 + (int)(xs() % 60); break;
                default: no = 30 + (int)(xs() % 80);  na = 30 + (int)(xs() % 80); break;
            }
            double d = 0.10 + (double)(xs() % 45) / 100.0;
            BinaryContext *c = ctx_create(no, na, "t");
            for (int o = 0; o < no; o++)
                for (int a = 0; a < na; a++)
                    if ((double)(xs() % 100000) / 100000.0 < d) ctx_set(c, o, a, true);
            int l = 0;
            int *f = ALGOS[ai].run(c, &l);
            int N = 0, E = 0;
            unsigned long long sig = canon_signature(f, no, na, &N, &E);
            fprintf(out, "%-6s t=%02d %dx%d d=%.2f N=%d E=%d canon=%016llx\n",
                    ALGOS[ai].name, t, no, na, d, N, E, sig);
            free(f);
            ctx_free(c);
        }
    }
    return 0;
}

int main(int argc, char **argv) {
    if (argc < 2) { gen_cases(stdout); return 0; }

    FILE *ref = fopen(argv[1], "r");
    if (!ref) { fprintf(stderr, "reference illisible : %s\n", argv[1]); return 2; }
    const char *tmpname = "aoc_regress_out.txt";
    FILE *cur = fopen(tmpname, "w+");
    if (!cur) { fprintf(stderr, "ecriture impossible\n"); fclose(ref); return 2; }
    gen_cases(cur);
    rewind(cur);

    char a[512], b[512];
    int line = 0, bad = 0;
    for (;;) {
        char *ra = fgets(a, sizeof(a), ref);
        char *rb = fgets(b, sizeof(b), cur);
        if (!ra && !rb) break;
        line++;
        if (!ra || !rb) { printf("ECART ligne %d : nombre de cas different\n", line); bad++; break; }
        if (strcmp(a, b) != 0) {
            printf("ECART ligne %d\n  attendu : %s  obtenu  : %s", line, a, b);
            bad++;
            if (bad > 15) { printf("  (arret apres 15 ecarts)\n"); break; }
        }
    }
    fclose(ref); fclose(cur); remove(tmpname);
    if (bad == 0) { printf("OK : %d cas, AOC-posets inchanges\n", line); return 0; }
    printf("ECHEC : %d ecart(s) sur %d cas\n", bad, line);
    return 1;
}
