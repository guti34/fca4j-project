/*
 * bitset.h — couche de bitsets denses en mots de 64 bits, partagée par les algorithmes
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 *
 * Ce fichier rassemble la représentation dense qui existait en deux exemplaires :
 * les `bs_*` d'aresorder.h (Ares) et les `words_*` de latticecbo.c (Lattice CbO).
 * Les deux implémentaient la même chose — AND, ANDNOT, inclusion, popcount sur un
 * univers de taille fixe — avec des noms différents et des définitions de popcount
 * dupliquées. Un seul point de vérité évite qu'une optimisation appliquée d'un côté
 * (l'avalanche fmix64, par exemple) reste inconnue de l'autre.
 *
 * Convention d'univers : W = ceil(n/64) mots, et aucun bit au-delà de n n'est jamais
 * positionné. Les opérations n'ont donc pas besoin de masquer le dernier mot, et
 * bs_card / bs_subset / bs_equal restent exacts sans traitement particulier.
 *
 * Dépendances volontairement minimales : ni croaring, ni fca4j_common.h. Les
 * conversions vers roaring_bitmap_t vivent dans bitset_roaring.h, de sorte qu'un
 * algorithme entièrement dense (Ares, Ceres) n'entraîne pas croaring dans son
 * unité de compilation.
 */
#ifndef FCA4J_BITSET_H
#define FCA4J_BITSET_H

#include <stdint.h>
#include <stdbool.h>
#include <string.h>

typedef uint64_t aword;
#define AW_BITS 64
#define AW_N(nbits) (((nbits) + AW_BITS - 1) / AW_BITS)

/* ── intrinsèques ─────────────────────────────────────────────────────── */

#if defined(_MSC_VER)
#  include <intrin.h>
#  if defined(_M_X64) || defined(_M_ARM64)
#    define AW_POPCOUNT(x) ((int)__popcnt64(x))
#  else
static inline int aw_popcount_fallback(aword x) {
    x = x - ((x >> 1) & 0x5555555555555555ULL);
    x = (x & 0x3333333333333333ULL) + ((x >> 2) & 0x3333333333333333ULL);
    x = (x + (x >> 4)) & 0x0f0f0f0f0f0f0f0fULL;
    return (int)((x * 0x0101010101010101ULL) >> 56);
}
#    define AW_POPCOUNT(x) aw_popcount_fallback(x)
#  endif
static inline int aw_ctz(aword x) { unsigned long i; _BitScanForward64(&i, x); return (int)i; }
#  define AW_CTZ(x) aw_ctz(x)
#elif defined(__GNUC__) || defined(__clang__)
#  define AW_POPCOUNT(x) __builtin_popcountll(x)
#  define AW_CTZ(x)      __builtin_ctzll(x)
#else
static inline int aw_popcount_fallback(aword x) {
    x = x - ((x >> 1) & 0x5555555555555555ULL);
    x = (x & 0x3333333333333333ULL) + ((x >> 2) & 0x3333333333333333ULL);
    x = (x + (x >> 4)) & 0x0f0f0f0f0f0f0f0fULL;
    return (int)((x * 0x0101010101010101ULL) >> 56);
}
static inline int aw_ctz_fallback(aword x) {
    int n = 0;
    while (!(x & 1)) { x >>= 1; n++; }
    return n;
}
#  define AW_POPCOUNT(x) aw_popcount_fallback(x)
#  define AW_CTZ(x)      aw_ctz_fallback(x)
#endif

/* ── construction et accès élémentaire ────────────────────────────────── */

static inline void bs_zero(aword *a, int w) {
    memset(a, 0, (size_t)w * sizeof(aword));
}
static inline void bs_copy(aword *dst, const aword *src, int w) {
    memcpy(dst, src, (size_t)w * sizeof(aword));
}
static inline void bs_set(aword *a, int i) {
    a[i >> 6] |= (aword)1 << (i & 63);
}
static inline void bs_clear(aword *a, int i) {
    a[i >> 6] &= ~((aword)1 << (i & 63));
}
static inline bool bs_test(const aword *a, int i) {
    return (a[i >> 6] >> (i & 63)) & 1;
}

/* ── opérations ensemblistes ──────────────────────────────────────────── */

static inline void bs_or(aword *dst, const aword *src, int w) {
    for (int i = 0; i < w; i++) dst[i] |= src[i];
}
static inline void bs_and(aword *dst, const aword *src, int w) {
    for (int i = 0; i < w; i++) dst[i] &= src[i];
}
static inline void bs_andnot(aword *dst, const aword *src, int w) {
    for (int i = 0; i < w; i++) dst[i] &= ~src[i];
}
/* dst = a & b */
static inline void bs_and_to(aword *dst, const aword *a, const aword *b, int w) {
    for (int i = 0; i < w; i++) dst[i] = a[i] & b[i];
}
/* dst = a | b */
static inline void bs_or_to(aword *dst, const aword *a, const aword *b, int w) {
    for (int i = 0; i < w; i++) dst[i] = a[i] | b[i];
}

/* ── prédicats ────────────────────────────────────────────────────────── */

static inline int bs_card(const aword *a, int w) {
    int c = 0;
    for (int i = 0; i < w; i++) c += AW_POPCOUNT(a[i]);
    return c;
}
static inline bool bs_empty(const aword *a, int w) {
    for (int i = 0; i < w; i++) if (a[i]) return false;
    return true;
}
static inline bool bs_intersects(const aword *a, const aword *b, int w) {
    for (int i = 0; i < w; i++) if (a[i] & b[i]) return true;
    return false;
}
/* Cardinalité de a & b, sans matérialiser l'intersection. */
static inline int bs_card_and(const aword *a, const aword *b, int w) {
    int c = 0;
    for (int i = 0; i < w; i++) c += AW_POPCOUNT(a[i] & b[i]);
    return c;
}
/* x ⊆ y  ⟺  aucun bit de x hors de y  ⟺  (x & ~y) nul partout. */
static inline bool bs_subset(const aword *x, const aword *y, int w) {
    for (int i = 0; i < w; i++) if (x[i] & ~y[i]) return false;
    return true;
}
/* Boucle sur les mots plutôt que memcmp : à -O3, memcmp reste un appel de
 * bibliothèque là où la boucle s'intègre à l'appelant. Mesuré sur la phase
 * couvertures de Lattice CbO, le passage à memcmp faisait apparaître un appel
 * et réorganisait toute la fonction. Le gain éventuel de memcmp sur de longs
 * bitsets ne compense pas la perte d'intégration sur les courts, qui sont le
 * cas courant ici. */
static inline bool bs_equal(const aword *a, const aword *b, int w) {
    for (int i = 0; i < w; i++) if (a[i] != b[i]) return false;
    return true;
}

/* ── inclusion creuse ─────────────────────────────────────────────────────
 * bs_subset parcourt les w mots de x, y compris les mots nuls, qui ne peuvent
 * pourtant jamais mettre l'inclusion en défaut : il n'y a rien à y retrouver.
 * Quand un même x sert d'argument à de nombreux tests — le cas de Ceres, dont
 * l'extent à insérer est fixe pendant tout un parcours — on relève une fois les
 * indices de ses mots occupés, puis on ne teste que ceux-là.
 *
 * Mesuré côté Java sur ord6magic04 : 64 mots occupés sur 298, soit 119 M de mots
 * balayés dont 102 M sur du vide, et 340 -> 225 ms une fois la traversée réduite.
 *
 * idx doit pouvoir accueillir w entrées. Il décrit x tel qu'il est au moment du
 * relevé : un x modifié entre-temps rendrait la réponse fausse sans que rien ne
 * le signale.
 */
static inline int bs_nonzero_words(const aword *x, int w, int *idx) {
    int n = 0;
    for (int i = 0; i < w; i++) if (x[i]) idx[n++] = i;
    return n;
}
static inline bool bs_subset_sparse(const aword *x, const aword *y,
                                    const int *idx, int nidx) {
    for (int k = 0; k < nidx; k++) {
        int i = idx[k];
        if (x[i] & ~y[i]) return false;
    }
    return true;
}

/* ── parcours des bits positionnés ────────────────────────────────────────
 * Motif : for (BS_FOREACH(i, set, w)) { ... }  — i prend successivement chaque
 * indice présent. Le corps ne doit pas modifier `set`.
 *
 * Attention : la macro se développe en deux boucles imbriquées, donc un `break`
 * dans le corps ne quitte que le mot courant et le parcours reprend au mot
 * suivant. Pour une sortie anticipée, utiliser un drapeau et le tester dans la
 * condition, ou écrire la double boucle à la main.                            */
#define BS_FOREACH(var, a, w)                                                 \
    int _bs_wi = 0, var = -1; _bs_wi < (w); _bs_wi++)                         \
        for (aword _bs_x = (a)[_bs_wi];                                       \
             _bs_x && ((var = (_bs_wi << 6) + AW_CTZ(_bs_x)), 1);             \
             _bs_x &= _bs_x - 1

/* ── hachage d'un bitset ──────────────────────────────────────────────────
 * FNV-1a sur les MOTS, suivi de l'avalanche fmix64 de MurmurHash3.
 *
 * Le finaliseur n'est pas cosmétique. FNV-1a appliqué à des mots de 64 bits ne
 * diffuse pas vers les bits de POIDS FAIBLE : dans une multiplication modulo
 * 2^64, le bit j du produit ne dépend que des bits 0..j des opérandes. Or un
 * index de bucket vaut h & (nbuckets-1), donc précisément ces bits faibles. Sur
 * des extents de treillis — fortement structurés et emboîtés — la distribution
 * s'effondre : occupation ~5 %, chaînes de plusieurs milliers d'entrées.
 * fmix64 rabat les bits de poids fort sur les bits faibles en 3 décalages et
 * 2 multiplications, une fois par hash, négligeable devant la boucle sur W mots.
 *
 * Mesuré sur inter3magic04 (Lattice CbO) : phase couvertures 642 -> 266 ms,
 * chaîne maximale 11454 -> 15 entrées. Jamais perdant sur les contextes testés.
 */
static inline aword bs_fmix64(aword k) {
    k ^= k >> 33; k *= 0xff51afd7ed558ccdULL;
    k ^= k >> 33; k *= 0xc4ceb9fe1a85ec53ULL;
    k ^= k >> 33; return k;
}
static inline aword bs_hash(const aword *a, int w) {
    aword h = 1469598103934665603ULL;
    for (int i = 0; i < w; i++) h = (h ^ a[i]) * 1099511628211ULL;
    return bs_fmix64(h);
}

#endif /* FCA4J_BITSET_H */
