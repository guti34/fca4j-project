/*
 * latticecbo.c — Lattice_ParallelCbO : treillis complet par Close-by-One
 * sur les extents (contexte brut, parallèle).
 *
 * Énumération : depuis l'extent top (tous les objets), on étend E par E ∩ col_i
 * pour i hors de l'intent de E, validé par canonicité (aucun k<i, k hors intent,
 * avec E_i ⊆ col_k). L'intersection d'extents fermés étant fermée, pas de
 * clôture ; la canonicité garantit l'unicité. Les colonnes dupliquées sont
 * gérées par la canonicité (le plus petit indice gagne).
 *
 * v2 — boucle chaude DÉ-CROARINGÉE sur mots 64 bits packés + héritage incrémental
 * de l'intent (esprit In-Close4) :
 *   - chaque extent est un tableau de W = ⌈nObj/64⌉ mots ; E ∩ col_i = AND mot à
 *     mot ; Ei ⊆ col_k = (Ei[w] & ~col_k[w]) == 0 (sortie anticipée), sans
 *     dispatch de conteneur roaring dans la boucle ;
 *   - l'intent de l'enfant (inB[]) est hérité du parent sous l'indice i (garanti
 *     par la canonicité) et recalculé seulement au-dessus → plus aucun
 *     compute_intent complet par concept ;
 *   - les colonnes packées sont en lecture seule : partage direct entre threads,
 *     pas de copie profonde.
 * Les roaring extent/intent ne sont matérialisés qu'une fois par concept
 * CANONIQUE (frontière vers ConceptOrder / CMap / phase covers, inchangées).
 *
 * Couvertures inférieures = A ∩ col_m maximaux propres, m parcourant les
 * attributs MEET-IRRÉDUCTIBLES (précalculés). L'id du concept n'est résolu (via
 * la table extent->id) que pour les couvertures retenues.
 *
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 */

/* Macros de feature-test avant tout en-tête système.
 * - macOS  : _DARWIN_C_SOURCE expose POSIX + extensions Darwin
 *            (clock_gettime, sysconf/_SC_NPROCESSORS_ONLN, u_int…)
 * - Linux  : _POSIX_C_SOURCE 199309L suffit pour clock_gettime + sysconf
 * - Windows: rien (timing via QueryPerformanceCounter, CPU via GetSystemInfo) */
 
#ifdef __APPLE__
#  ifndef _DARWIN_C_SOURCE
#    define _DARWIN_C_SOURCE
#  endif
#elif !defined(_WIN32)
#  ifndef _POSIX_C_SOURCE
#    define _POSIX_C_SOURCE 199309L
#  endif
#endif

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include "latticecbo.h"
#include "../core/conceptorder.h"
#include "../core/closure.h"
#include "../core/fca4j_common.h"
#ifndef _WIN32
  #include <unistd.h>   /* sysconf(_SC_NPROCESSORS_ONLN), clock_gettime */
#endif

extern int croaring_hardware_support(void);

/* ── Timing (toujours actif) ─────────────────────────────────────────────────
 * Chaque mesure est écrite sur stderr ET en append dans un fichier, car stderr
 * issu du JNI n'apparaît pas dans la console Eclipse. Pour ne plus mesurer, il
 * suffit de supprimer (ou commenter) les lignes T_START()/T_END(...) dans
 * build_lattice_cbo. Mets un chemin absolu dans TLOG_PATH si le répertoire
 * courant du process n'est pas pratique (ex. "C:\\platform\\fca4j_timing.log").
 * ─────────────────────────────────────────────────────────────────────────── */
#define TLOG_PATH "c:\\platform\\fca4j_timing.log"
#ifdef _WIN32
  #include <windows.h>
  static double _ms_now(void) {
      LARGE_INTEGER f, c;
      QueryPerformanceFrequency(&f); QueryPerformanceCounter(&c);
      return (double)c.QuadPart * 1000.0 / (double)f.QuadPart;
  }
#else
  static double _ms_now(void) {
      struct timespec t; clock_gettime(CLOCK_MONOTONIC, &t);
      return t.tv_sec * 1000.0 + t.tv_nsec / 1e6;
  }
#endif
static void _tlog(const char *fmt, ...) {
    va_list ap;
    FILE *f = fopen(TLOG_PATH, "a");
    if (f) { va_start(ap, fmt); vfprintf(f, fmt, ap); va_end(ap); fclose(f); }
    va_start(ap, fmt); vfprintf(stderr, fmt, ap); va_end(ap); fflush(stderr);
}
#define T_DECL()  double _t0 = 0.0
#define T_START() (_t0 = _ms_now())
#define T_END(label)    _tlog("[latticecbo] %-18s : %8.2f ms\n", (label), _ms_now() - _t0)
#define T_SIMD_REPORT() _tlog("[latticecbo] croaring SIMD   : %d (0=scalaire 1=AVX2 3=AVX512)\n", \
                              croaring_hardware_support())

/* count-trailing-zeros 64 bits portable */
#if defined(_MSC_VER)
  #include <intrin.h>
  static inline int ctz64(uint64_t x){ unsigned long i; _BitScanForward64(&i, x); return (int)i; }
#else
  #define ctz64(x) __builtin_ctzll(x)
#endif

/* ── Table de hachage extent (mots packés) -> id de concept ──────────────────
 * Clé = W mots empruntés (vivent dans all_words, possédé par build_lattice_cbo).
 * Hash FNV-1a sur W mots (pas par élément). Pool de nœuds : aucune allocation
 * pendant les put/get, donc aucune contention d'allocateur en phase covers.
 * ─────────────────────────────────────────────────────────────────────────── */
typedef struct WNode {
    const uint64_t *key;       /* pointe dans all_words, non possédé */
    uint64_t        hash;
    int             id;
    struct WNode   *next;
} WNode;

typedef struct {
    WNode **buckets;
    int     nbuckets;
    int     W;
    WNode  *pool;              /* tous les nœuds en une allocation */
    int     pool_used;
} WMap;

static uint64_t fnv_words(const uint64_t *w, int W) {
    uint64_t h = 1469598103934665603ULL;
    for (int i = 0; i < W; i++) h = (h ^ w[i]) * 1099511628211ULL;
    return h;
}

/* expected = nb de concepts (taille exacte du pool). Facteur de charge ~0.5. */
static WMap *wmap_create(int expected, int W) {
    int nb = 1024;
    while (nb < (expected > 0 ? expected : 1) * 2) nb <<= 1;
    WMap *m = (WMap*)malloc(sizeof(WMap));
    m->nbuckets = nb; m->W = W; m->pool_used = 0;
    m->buckets = (WNode**)calloc((size_t)nb, sizeof(WNode*));
    m->pool = (WNode*)malloc((size_t)(expected > 0 ? expected : 1) * sizeof(WNode));
    return m;
}

static void wmap_put(WMap *m, const uint64_t *key, int id) {
    uint64_t h = fnv_words(key, m->W);
    int b = (int)(h & (uint64_t)(m->nbuckets - 1));
    WNode *n = &m->pool[m->pool_used++];
    n->key = key; n->hash = h; n->id = id; n->next = m->buckets[b];
    m->buckets[b] = n;
}

static int wmap_get(WMap *m, const uint64_t *key) {
    int W = m->W;
    uint64_t h = fnv_words(key, W);
    int b = (int)(h & (uint64_t)(m->nbuckets - 1));
    for (WNode *n = m->buckets[b]; n; n = n->next) {
        if (n->hash != h) continue;
        if (memcmp(n->key, key, (size_t)W * sizeof(uint64_t)) == 0) return n->id;
    }
    return -1;
}

static void wmap_free(WMap *m) { free(m->buckets); free(m->pool); free(m); }

/* ── Attributs meet-irréductibles ────────────────────────────────────────────
 * m irréductible ssi col_m != ∩{col_k : col_k ⊋ col_m}. Calcul O(nb_attr²).
 * ─────────────────────────────────────────────────────────────────────────── */
static int *compute_irreducibles(BinaryContext *ctx, int *out_n) {
    int m = ctx->nb_attributes;
    int *irr = (int*)malloc((size_t)(m > 0 ? m : 1) * sizeof(int));
    int n = 0;
    for (int a = 0; a < m; a++) {
        roaring_bitmap_t *ca = ctx->cols[a];
        uint64_t card_a = roaring_bitmap_get_cardinality(ca);
        roaring_bitmap_t *Im = NULL;
        for (int k = 0; k < m; k++) {
            if (k == a) continue;
            roaring_bitmap_t *ck = ctx->cols[k];
            if (roaring_bitmap_get_cardinality(ck) > card_a
                && roaring_bitmap_is_subset(ca, ck)) {        /* col_k ⊋ col_a */
                if (Im == NULL) Im = roaring_bitmap_copy(ck);
                else            roaring_bitmap_and_inplace(Im, ck);
            }
        }
        int irreducible;
        if (Im == NULL) {
            irreducible = (card_a != (uint64_t)ctx->nb_objects);
        } else {
            irreducible = !roaring_bitmap_equals(Im, ca);     /* Im ⊋ col_a */
            roaring_bitmap_free(Im);
        }
        if (irreducible) irr[n++] = a;
    }
    *out_n = n;
    return irr;
}

/* ── Représentation packée (mots 64 bits) ───────────────────────────────────
 * Univers d'objets fixe → W = ⌈nObj/64⌉ mots. Aucun bit de padding au-delà de
 * nObj n'est jamais positionné, donc AND/inclusion ne nécessitent pas de masque.
 * ─────────────────────────────────────────────────────────────────────────── */
static inline void words_and(uint64_t *dst, const uint64_t *a, const uint64_t *b, int W) {
    for (int w = 0; w < W; w++) dst[w] = a[w] & b[w];
}
/* re ← re \ x  (retire de re les bits présents dans x) */
static inline void words_andnot(uint64_t *re, const uint64_t *x, int W) {
    for (int w = 0; w < W; w++) re[w] &= ~x[w];
}
/* X ⊆ Y  ⟺  aucun bit de X hors de Y  ⟺  (X & ~Y) == 0 partout. */
static inline int words_subset(const uint64_t *x, const uint64_t *y, int W) {
    for (int w = 0; w < W; w++) if (x[w] & ~y[w]) return 0;
    return 1;
}
#if defined(_MSC_VER)
  #include <intrin.h>
  static inline int popcnt64(uint64_t x){ return (int)__popcnt64(x); }
#else
  #define popcnt64(x) __builtin_popcountll(x)
#endif
static inline int words_popcount(const uint64_t *x, int W) {
    int c = 0; for (int w = 0; w < W; w++) c += popcnt64(x[w]); return c;
}
static inline int words_equal(const uint64_t *a, const uint64_t *b, int W) {
    for (int w = 0; w < W; w++) if (a[w] != b[w]) return 0;
    return 1;
}
static void words_from_roaring_into(roaring_bitmap_t *bm, uint64_t *dst, int W) {
    memset(dst, 0, (size_t)W * sizeof(uint64_t));
    roaring_uint32_iterator_t it;
    roaring_iterator_init(bm, &it);
    while (it.has_value) {
        uint32_t o = it.current_value;
        dst[o >> 6] |= (1ULL << (o & 63));
        roaring_uint32_iterator_advance(&it);
    }
}
static uint64_t *words_from_roaring(roaring_bitmap_t *bm, int W) {
    uint64_t *w = (uint64_t*)calloc((size_t)W, sizeof(uint64_t));
    words_from_roaring_into(bm, w, W);
    return w;
}
/* Matérialisation roaring (une fois par concept canonique). */
/* Ajoute les bits positionnés de w[] dans un roaring existant (réutilise le
 * placeholder vide créé par co_add_concept : pas de create/free). */
static void add_words_to_roaring(const uint64_t *w, roaring_bitmap_t *bm, int W) {
    for (int wi = 0; wi < W; wi++) {
        uint64_t x = w[wi];
        while (x) {
            int b = ctz64(x);
            roaring_bitmap_add(bm, (uint32_t)(((uint32_t)wi << 6) + (uint32_t)b));
            x &= x - 1;
        }
    }
}
static roaring_bitmap_t *roaring_from_words(const uint64_t *w, int W) {
    roaring_bitmap_t *bm = roaring_bitmap_create();
    for (int wi = 0; wi < W; wi++) {
        uint64_t x = w[wi];
        while (x) {
            int b = ctz64(x);
            roaring_bitmap_add(bm, (uint32_t)(((uint32_t)wi << 6) + (uint32_t)b));
            x &= x - 1;
        }
    }
    return bm;
}
static roaring_bitmap_t *roaring_from_bools(const uint8_t *inB, int m) {
    roaring_bitmap_t *bm = roaring_bitmap_create();
    for (int k = 0; k < m; k++) if (inB[k]) roaring_bitmap_add(bm, (uint32_t)k);
    return bm;
}

/* ── Énumération Close-by-One sur les extents (packée) ───────────────────────
 * E = extent packé (W mots, possédé par l'appelant). inB[k] = (k ∈ intent(E)),
 * O(1). y = dernier attribut ajouté. Pour chaque enfant CANONIQUE on hérite
 * inB sous i (canonicité), on pose i, on recalcule au-dessus, et on matérialise
 * les roaring extent/intent une seule fois.
 * ─────────────────────────────────────────────────────────────────────────── */
static void cbo_enum_local(int m, int W, uint64_t **cw,
                           BitmapVec *exts, BitmapVec *ints,
                           uint64_t *E, const uint8_t *inB, int y) {
    uint64_t *Ei = (uint64_t*)malloc((size_t)W * sizeof(uint64_t));
    for (int i = y + 1; i < m; i++) {
        if (inB[i]) continue;                       /* i déjà dans l'intent */
        words_and(Ei, E, cw[i], W);                 /* extent fermé, ⊊ E */

        /* canonicité : aucun k<i hors intent(E) tel que Ei ⊆ col_k */
        int canonical = 1;
        for (int k = 0; k < i; k++) {
            if (inB[k]) continue;
            if (words_subset(Ei, cw[k], W)) { canonical = 0; break; }
        }
        if (!canonical) continue;

        /* intent(Ei) : hérité < i (canonique), i ajouté, recalculé > i */
        uint8_t *inBi = (uint8_t*)malloc((size_t)m);
        memcpy(inBi, inB, (size_t)i);
        inBi[i] = 1;
        for (int k = i + 1; k < m; k++)
            inBi[k] = inB[k] ? 1 : (words_subset(Ei, cw[k], W) ? 1 : 0);

        BitmapVec_push(exts, roaring_from_words(Ei, W));
        BitmapVec_push(ints, roaring_from_bools(inBi, m));

        uint64_t *Eic = (uint64_t*)malloc((size_t)W * sizeof(uint64_t));
        memcpy(Eic, Ei, (size_t)W * sizeof(uint64_t));
        cbo_enum_local(m, W, cw, exts, ints, Eic, inBi, i);
        free(Eic);
        free(inBi);
    }
    free(Ei);
}

typedef struct {
    int m, W;
    uint64_t **cw;               /* colonnes packées (lecture seule, partagées) */
    uint64_t *E0;                /* extent top packé (lecture seule) */
    const uint8_t *inB0;         /* intent top (lecture seule) */
    int *next_i;                 /* compteur partagé sur les attributs de 1er niveau */
    pthread_mutex_t *lock;
    BitmapVec exts, ints;        /* concepts produits par ce thread (locaux) */
} EnumTask;

static void *enum_thread(void *arg) {
    EnumTask *t = (EnumTask*)arg;
    int m = t->m, W = t->W;
    uint64_t **cw = t->cw;
    uint64_t *Ei = (uint64_t*)malloc((size_t)W * sizeof(uint64_t));
    for (;;) {
        pthread_mutex_lock(t->lock);
        int i = (*t->next_i)++;
        pthread_mutex_unlock(t->lock);
        if (i >= m) break;
        if (t->inB0[i]) continue;

        words_and(Ei, t->E0, cw[i], W);
        int canonical = 1;
        for (int k = 0; k < i; k++) {
            if (t->inB0[k]) continue;
            if (words_subset(Ei, cw[k], W)) { canonical = 0; break; }
        }
        if (!canonical) continue;

        uint8_t *inBi = (uint8_t*)malloc((size_t)m);
        memcpy(inBi, t->inB0, (size_t)i);
        inBi[i] = 1;
        for (int k = i + 1; k < m; k++)
            inBi[k] = t->inB0[k] ? 1 : (words_subset(Ei, cw[k], W) ? 1 : 0);

        BitmapVec_push(&t->exts, roaring_from_words(Ei, W));
        BitmapVec_push(&t->ints, roaring_from_bools(inBi, m));

        uint64_t *Eic = (uint64_t*)malloc((size_t)W * sizeof(uint64_t));
        memcpy(Eic, Ei, (size_t)W * sizeof(uint64_t));
        cbo_enum_local(m, W, cw, &t->exts, &t->ints, Eic, inBi, i);
        free(Eic);
        free(inBi);
    }
    free(Ei);
    return NULL;
}

static int detect_nthreads(void) {
#ifdef _WIN32
    SYSTEM_INFO si; GetSystemInfo(&si);
    int n = (int)si.dwNumberOfProcessors; return n > 0 ? n : 1;
#else
    long n = sysconf(_SC_NPROCESSORS_ONLN); return n > 0 ? (int)n : 1;
#endif
}

/* fetch-add atomique (sans verrou) pour la distribution dynamique du travail */
#if defined(_MSC_VER)
  #include <intrin.h>
  static inline int atomic_fetch_add_int(int *p, int v) {
      return (int)_InterlockedExchangeAdd((long volatile*)p, (long)v);
  }
#else
  static inline int atomic_fetch_add_int(int *p, int v) {
      return __atomic_fetch_add(p, v, __ATOMIC_RELAXED);
  }
#endif
#define COVER_BATCH 64   /* concepts grappillés par atomic : compromis contention/équilibrage */

/* Construction parallèle du store packé all_words : chaque thread convertit une
 * tranche disjointe de concepts (extents roaring -> mots), sans verrou. */
typedef struct {
    ConceptOrder *co; uint64_t *all_words; int *all_card; int W; int c_start, c_end;
} ConvTask;

static void *conv_thread(void *arg) {
    ConvTask *t = (ConvTask*)arg;
    for (int c = t->c_start; c < t->c_end; c++) {
        uint64_t *dst = t->all_words + (size_t)c * t->W;
        words_from_roaring_into(t->co->extents[c], dst, t->W);
        t->all_card[c] = words_popcount(dst, t->W);
    }
    return NULL;
}

typedef struct {
    ConceptOrder *co; int ncpt; WMap *wmap;
    const uint64_t *all_words;   /* extents packés de tous les concepts (lecture seule) */
    const int *all_card;         /* cardinalité de chaque extent (lecture seule) */
    const int *irr; int nIrr; int W;
    uint64_t **irr_words;        /* colonnes packées des irréductibles (lecture seule) */
    int *next_c;                 /* curseur atomique partagé (fetch-add par lots) */
    IntVec child, parent;        /* arêtes (enfant, parent) produites par ce thread */
} CoverTask;

/* Couvertures inférieures directes (Hasse), part dynamique de concepts par thread.
 * Tout en mots packés : extent lu directement dans all_words, A∩col_mm = AND,
 * cardinalité = popcount, domination = comparaison de mots, résolution de l'id par
 * wmap_get (hash sur W mots). Aucun roaring, aucune allocation dans la boucle.
 * Lecture seule sur co / wmap / all_words / irr_words. */
static void *cover_thread(void *arg) {
    CoverTask *t = (CoverTask*)arg;
    WMap *wmap = t->wmap; int ncpt = t->ncpt;
    const uint64_t *all_words = t->all_words;
    const int *all_card = t->all_card;
    int nIrr = t->nIrr, W = t->W;
    uint64_t **irw = t->irr_words;
    int cap = nIrr ? nIrr : 1;

    uint64_t *cw    = (uint64_t*)malloc((size_t)cap * (size_t)W * sizeof(uint64_t)); /* candidats packés */
    int *cand_card  = (int*)malloc((size_t)cap * sizeof(int));
    int *order      = (int*)malloc((size_t)cap * sizeof(int));   /* indices triés par card décroissante */
    int *conf       = (int*)malloc((size_t)cap * sizeof(int));   /* indices maximaux confirmés */
    /* tri par comptage : têtes de listes chaînées par cardinalité (≤ W*64), maillons */
    int hsize = W * 64 + 1;
    int *head = (int*)malloc((size_t)hsize * sizeof(int));
    int *nxt  = (int*)malloc((size_t)cap * sizeof(int));
    for (int i = 0; i < hsize; i++) head[i] = -1;

    for (;;) {
        int start = atomic_fetch_add_int(t->next_c, COVER_BATCH);
        if (start >= ncpt) break;
        int stop = start + COVER_BATCH;
        if (stop > ncpt) stop = ncpt;
        for (int c = start; c < stop; c++) {

        const uint64_t *A = all_words + (size_t)c * W;
        int cardA = all_card[c];

        /* candidats = A ∩ col_mm STRICTEMENT inclus dans A (sinon mm ∈ intent(A)).
         * card == cardA ⟺ A ⊆ col_mm ⟺ mm dans l'intent → on saute, sans toucher
         * aux intents roaring : tout reste en mots packés contigus. */
        int kc = 0;
        for (int s = 0; s < nIrr; s++) {
            uint64_t *dst = cw + (size_t)kc * W;
            words_and(dst, A, irw[s], W);
            int card = words_popcount(dst, W);
            if (card == cardA) continue;
            cand_card[kc] = card;
            kc++;
        }

        /* tri par comptage des indices, cardinalité décroissante : O(kc), sans
         * branchements imprévisibles (remplace l'insertion O(kc²)). */
        int maxc = -1, minc = hsize;
        for (int i = 0; i < kc; i++) {
            int card = cand_card[i];
            nxt[i] = head[card]; head[card] = i;
            if (card > maxc) maxc = card;
            if (card < minc) minc = card;
        }
        int pos = 0;
        for (int card = maxc; card >= minc; card--) {
            for (int i = head[card]; i >= 0; i = nxt[i]) order[pos++] = i;
            head[card] = -1;   /* réinitialise pour le concept suivant */
        }

        /* maximal ssi non dominé par un maximal déjà confirmé (de card >=) */
        int nconf = 0;
        for (int a = 0; a < kc; a++) {
            int xi = order[a];
            const uint64_t *cxi = cw + (size_t)xi * W;
            int dominated = 0;
            for (int j = 0; j < nconf; j++) {
                int cj = conf[j];
                const uint64_t *ccj = cw + (size_t)cj * W;
                if (cand_card[cj] == cand_card[xi]) {
                    if (words_equal(ccj, cxi, W)) { dominated = 1; break; }   /* doublon */
                } else { /* cand_card[cj] > cand_card[xi] */
                    if (words_subset(cxi, ccj, W)) { dominated = 1; break; }
                }
            }
            if (!dominated) {
                conf[nconf++] = xi;
                int id = wmap_get(wmap, cxi);
                if (id >= 0 && id != c) {
                    IntVec_push(&t->child, id);
                    IntVec_push(&t->parent, c);
                }
            }
        }
        }   /* for c dans le lot */
    }

    free(cw); free(cand_card); free(order); free(conf); free(head); free(nxt);
    return NULL;
}

/* ── Extents réduits, en parallèle et en mots packés ─────────────────────────
 * rextent(c) = extent(c) privé de l'union des extents des enfants directs.
 * On lit les extents packés (all_words), on retranche par andnot mot à mot, puis
 * on remplit le placeholder roaring vide de co->rextents[c] (créé par
 * co_add_concept) — pas de create/free par concept. Chaque thread traite des
 * concepts disjoints : écriture sur son propre rextents[c], lecture seule
 * ailleurs. */
typedef struct {
    ConceptOrder *co; const uint64_t *all_words; int W; int *next_c;
} RexTask;

static void *rex_thread(void *arg) {
    RexTask *t = (RexTask*)arg;
    ConceptOrder *co = t->co;
    const uint64_t *all_words = t->all_words;
    int W = t->W;
    uint64_t *re = (uint64_t*)malloc((size_t)W * sizeof(uint64_t));
    for (;;) {
        int start = atomic_fetch_add_int(t->next_c, COVER_BATCH);
        if (start >= co->counter) break;
        int stop = start + COVER_BATCH;
        if (stop > co->counter) stop = co->counter;
        for (int c = start; c < stop; c++) {
            memcpy(re, all_words + (size_t)c * W, (size_t)W * sizeof(uint64_t));
            roaring_uint32_iterator_t it;
            roaring_iterator_init(co->graph->children[c], &it);
            while (it.has_value) {
                int child = (int)it.current_value;
                words_andnot(re, all_words + (size_t)child * W, W);
                roaring_uint32_iterator_advance(&it);
            }
            add_words_to_roaring(re, co->rextents[c], W);
        }
    }
    free(re);
    return NULL;
}

/* ── build_lattice_cbo ──────────────────────────────────────────────────────
 * Pas de co_compute_intents : ConceptOrder.populate() reconstruit les sets
 * complets côté Java. Les intents complets stockés ne servent qu'en interne.
 * ─────────────────────────────────────────────────────────────────────────── */
static ConceptOrder *build_lattice_cbo(BinaryContext *ctx) {
    T_DECL();
/*    T_SIMD_REPORT(); */
    ConceptOrder *co = co_create(ctx);

    /* Concept top : extent = tous les objets, intent = attributs communs */
    roaring_bitmap_t *allObjects = roaring_bitmap_create();
    if (ctx->nb_objects > 0)
        roaring_bitmap_add_range(allObjects, 0, (uint32_t)ctx->nb_objects);
    roaring_bitmap_t *topIntent = compute_intent(ctx, allObjects);
    co_add_concept(co, allObjects, topIntent);   /* id 0 ; indexé plus bas dans all_words/wmap */

    /* 0) Précalcul des colonnes packées + top packé (lecture seule, partagés) */
    int m = ctx->nb_attributes;
    int W = (ctx->nb_objects + 63) >> 6; if (W < 1) W = 1;

    uint64_t **cw = (uint64_t**)malloc((size_t)(m > 0 ? m : 1) * sizeof(uint64_t*));
    for (int a = 0; a < m; a++) cw[a] = words_from_roaring(ctx->cols[a], W);

    uint64_t *E0 = (uint64_t*)calloc((size_t)W, sizeof(uint64_t));
    int full = ctx->nb_objects >> 6;
    int rem  = ctx->nb_objects & 63;
    for (int w = 0; w < full; w++) E0[w] = ~0ULL;
    if (rem) E0[full] = (1ULL << rem) - 1ULL;

    uint8_t *inB0 = (uint8_t*)malloc((size_t)(m > 0 ? m : 1));
    for (int a = 0; a < m; a++) inB0[a] = words_subset(E0, cw[a], W) ? 1 : 0;

    /* 1) Énumération CbO — parallèle (sous-arbres de premier niveau, dynamique) */
    T_START();
    int en_threads = detect_nthreads();
    if (en_threads < 1) en_threads = 1;
    if (m > 0 && en_threads > m) en_threads = m;
    if (en_threads < 1) en_threads = 1;

    int next_i = 0;
    pthread_mutex_t emutex;
    pthread_mutex_init(&emutex, NULL);
    EnumTask *etasks = (EnumTask*)malloc((size_t)en_threads * sizeof(EnumTask));
    pthread_t *etids = (pthread_t*)malloc((size_t)en_threads * sizeof(pthread_t));
    for (int i = 0; i < en_threads; i++) {
        etasks[i].m = m;
        etasks[i].W = W;
        etasks[i].cw = cw;
        etasks[i].E0 = E0;
        etasks[i].inB0 = inB0;
        etasks[i].next_i = &next_i;
        etasks[i].lock = &emutex;
        etasks[i].exts = BitmapVec_new();
        etasks[i].ints = BitmapVec_new();
    }
    for (int i = 0; i < en_threads; i++)
        pthread_create(&etids[i], NULL, enum_thread, &etasks[i]);
    for (int i = 0; i < en_threads; i++)
        pthread_join(etids[i], NULL);
    pthread_mutex_destroy(&emutex);
/*    T_END("cbo enum (par)"); */

    /* fusion séquentielle : attribution des IDs globaux */
    T_START();
    for (int i = 0; i < en_threads; i++) {
        for (int j = 0; j < etasks[i].exts.len; j++) {
            roaring_bitmap_t *E = etasks[i].exts.data[j];
            roaring_bitmap_t *B = etasks[i].ints.data[j];
            co_add_concept(co, E, B);   /* co prend possession de E et B */
        }
        BitmapVec_free(&etasks[i].exts);   /* libère le tableau, pas les bitmaps */
        BitmapVec_free(&etasks[i].ints);
    }
    free(etasks); free(etids);
/*    T_END("merge concepts"); */

    /* libération de la représentation packée d'énumération */
    for (int a = 0; a < m; a++) free(cw[a]);
    free(cw); free(E0); free(inB0);
/*    _tlog("[latticecbo] concepts=%d  enum threads=%d  W=%d\n", co->counter, en_threads, W); */

    /* 1bis) Store packé de TOUS les extents (parallèle) + map clé-mots.
     * all_words sert à la fois aux intents réduits et à la phase covers ;
     * la wmap (clé = W mots) évite tout roaring/hachage par élément ensuite. */
    T_START();
    uint64_t *all_words = (uint64_t*)malloc((size_t)co->counter * (size_t)W * sizeof(uint64_t));
    int *all_card = (int*)malloc((size_t)(co->counter > 0 ? co->counter : 1) * sizeof(int));
    {
        int cv_threads = detect_nthreads();
        if (cv_threads < 1) cv_threads = 1;
        if (co->counter > 0 && cv_threads > co->counter) cv_threads = co->counter;
        if (cv_threads < 1) cv_threads = 1;
        ConvTask *cv = (ConvTask*)malloc((size_t)cv_threads * sizeof(ConvTask));
        pthread_t *cvt = (pthread_t*)malloc((size_t)cv_threads * sizeof(pthread_t));
        int cchunk = (co->counter + cv_threads - 1) / cv_threads;
        for (int i = 0; i < cv_threads; i++) {
            cv[i].co = co; cv[i].all_words = all_words; cv[i].all_card = all_card; cv[i].W = W;
            cv[i].c_start = i * cchunk;
            cv[i].c_end   = (i + 1) * cchunk;
            if (cv[i].c_end > co->counter)   cv[i].c_end = co->counter;
            if (cv[i].c_start > co->counter) cv[i].c_start = co->counter;
        }
        for (int i = 0; i < cv_threads; i++)
            pthread_create(&cvt[i], NULL, conv_thread, &cv[i]);
        for (int i = 0; i < cv_threads; i++)
            pthread_join(cvt[i], NULL);
        free(cv); free(cvt);
    }
    WMap *wmap = wmap_create(co->counter, W);
    for (int c = 0; c < co->counter; c++)
        wmap_put(wmap, all_words + (size_t)c * W, c);
 /*   T_END("pack store + wmap"); */

    /* 2) Intents réduits : pour chaque attribut a, concept dont l'extent == col_a */
    T_START();
    {
        uint64_t *scratch = (uint64_t*)malloc((size_t)W * sizeof(uint64_t));
        for (int a = 0; a < ctx->nb_attributes; a++) {
            words_from_roaring_into(ctx->cols[a], scratch, W);
            int id = wmap_get(wmap, scratch);
            if (id >= 0) roaring_bitmap_add(co->rintents[id], (uint32_t)a);
        }
        free(scratch);
    }
/*    T_END("reduced intents"); */

    /* 3) Attributs meet-irréductibles (une fois) */
    T_START();
    int nIrr = 0;
    int *irr = compute_irreducibles(ctx, &nIrr);
/*    T_END("irreducibles"); */
/*    _tlog("[latticecbo] irreducibles=%d / %d\n", nIrr, ctx->nb_attributes); */

/* 4) Couvertures inférieures directes (Hasse) — parallèle, part dynamique par concept */
    T_START();
    /* colonnes packées des irréductibles (lecture seule, partagées entre threads) */
    uint64_t **irr_words = (uint64_t**)malloc((size_t)(nIrr > 0 ? nIrr : 1) * sizeof(uint64_t*));
    for (int s = 0; s < nIrr; s++)
        irr_words[s] = words_from_roaring(ctx->cols[irr[s]], W);

    int nthreads = detect_nthreads();
    if (nthreads < 1) nthreads = 1;
    if (co->counter > 0 && nthreads > co->counter) nthreads = co->counter;
    if (nthreads < 1) nthreads = 1;

    int next_c = 0;
    CoverTask *tasks = (CoverTask*)malloc((size_t)nthreads * sizeof(CoverTask));
    pthread_t *tids  = (pthread_t*)malloc((size_t)nthreads * sizeof(pthread_t));
    for (int i = 0; i < nthreads; i++) {
        tasks[i].co = co; tasks[i].ncpt = co->counter; tasks[i].wmap = wmap; tasks[i].all_words = all_words;
        tasks[i].all_card = all_card;
        tasks[i].irr = irr; tasks[i].nIrr = nIrr; tasks[i].W = W;
        tasks[i].irr_words = irr_words;
        tasks[i].next_c = &next_c;
        tasks[i].child  = IntVec_new();
        tasks[i].parent = IntVec_new();
    }
    for (int i = 0; i < nthreads; i++)
        pthread_create(&tids[i], NULL, cover_thread, &tasks[i]);
    for (int i = 0; i < nthreads; i++)
        pthread_join(tids[i], NULL);

    /* application séquentielle des arêtes (le graphe partagé est muté ici) */
    for (int i = 0; i < nthreads; i++) {
        for (int k = 0; k < tasks[i].child.len; k++)
            co_add_edge(co, tasks[i].child.data[k], tasks[i].parent.data[k]);
        IntVec_free(&tasks[i].child);
        IntVec_free(&tasks[i].parent);
    }
    for (int s = 0; s < nIrr; s++) free(irr_words[s]);
    free(irr_words);
    free(tasks); free(tids); free(irr);
/*    T_END("covers"); */
/*    _tlog("[latticecbo] cover threads   : %d\n", nthreads); */

    /* 5) Extents réduits — parallèle, packé (réutilise all_words avant libération) */
    T_START();
    {
        int rx_threads = detect_nthreads();
        if (rx_threads < 1) rx_threads = 1;
        if (co->counter > 0 && rx_threads > co->counter) rx_threads = co->counter;
        if (rx_threads < 1) rx_threads = 1;
        int rx_next = 0;
        RexTask *rx = (RexTask*)malloc((size_t)rx_threads * sizeof(RexTask));
        pthread_t *rxt = (pthread_t*)malloc((size_t)rx_threads * sizeof(pthread_t));
        for (int i = 0; i < rx_threads; i++) {
            rx[i].co = co; rx[i].all_words = all_words; rx[i].W = W; rx[i].next_c = &rx_next;
        }
        for (int i = 0; i < rx_threads; i++)
            pthread_create(&rxt[i], NULL, rex_thread, &rx[i]);
        for (int i = 0; i < rx_threads; i++)
            pthread_join(rxt[i], NULL);
        free(rx); free(rxt);
    }
/*    T_END("compute rextents"); */

    wmap_free(wmap);
    free(all_words);
    free(all_card);
    return co;
}


/* ════════════════════════════════════════════════════════════════════════════
 * VARIANTE CSR / packée du Lattice_ParallelCbO.
 *
 * Même énumération et même phase covers que build_lattice_cbo, mais l'ordre
 * n'est JAMAIS matérialisé en roaring : on supprime l'aller-retour
 * packé→roaring→packé par concept (roaring_from_words + conv_thread) ET les
 * intents complets (roaring_from_bools) qui ne sont jamais lus dans le chemin
 * flat. Les extents restent packés (all_words), l'adjacence et les sets réduits
 * sortent directement au format plat de co_to_flat_array.
 * ════════════════════════════════════════════════════════════════════════════ */

/* Vecteur d'extents packés (W mots/concept), contigu et croissant. */
typedef struct { uint64_t *data; int count, cap, W; } PackVec;
static PackVec PackVec_new(int W) {
    PackVec v; v.W = W; v.count = 0; v.cap = 64;
    v.data = (uint64_t*)malloc((size_t)v.cap * (size_t)W * sizeof(uint64_t));
    return v;
}
static inline void PackVec_push(PackVec *v, const uint64_t *words) {
    if (v->count >= v->cap) {
        v->cap *= 2;
        v->data = (uint64_t*)realloc(v->data, (size_t)v->cap * (size_t)v->W * sizeof(uint64_t));
    }
    memcpy(v->data + (size_t)v->count * v->W, words, (size_t)v->W * sizeof(uint64_t));
    v->count++;
}
static void PackVec_free(PackVec *v) { free(v->data); v->data=NULL; v->count=v->cap=0; }

/* Énumération CbO packée : pousse l'extent packé, ne matérialise AUCUN intent
 * (inB est utilisé pour la canonicité puis jeté). */
static void cbo_enum_local_csr(int m, int W, uint64_t **cw, PackVec *out,
                               uint64_t *E, const uint8_t *inB, int y) {
    uint64_t *Ei = (uint64_t*)malloc((size_t)W * sizeof(uint64_t));
    for (int i = y + 1; i < m; i++) {
        if (inB[i]) continue;
        words_and(Ei, E, cw[i], W);
        int canonical = 1;
        for (int k = 0; k < i; k++) {
            if (inB[k]) continue;
            if (words_subset(Ei, cw[k], W)) { canonical = 0; break; }
        }
        if (!canonical) continue;

        uint8_t *inBi = (uint8_t*)malloc((size_t)m);
        memcpy(inBi, inB, (size_t)i);
        inBi[i] = 1;
        for (int k = i + 1; k < m; k++)
            inBi[k] = inB[k] ? 1 : (words_subset(Ei, cw[k], W) ? 1 : 0);

        PackVec_push(out, Ei);

        uint64_t *Eic = (uint64_t*)malloc((size_t)W * sizeof(uint64_t));
        memcpy(Eic, Ei, (size_t)W * sizeof(uint64_t));
        cbo_enum_local_csr(m, W, cw, out, Eic, inBi, i);
        free(Eic);
        free(inBi);
    }
    free(Ei);
}

typedef struct {
    int m, W;
    uint64_t **cw;
    uint64_t *E0;
    const uint8_t *inB0;
    int *next_i;
    pthread_mutex_t *lock;
    PackVec out;
} EnumCsrTask;

static void *enum_csr_thread(void *arg) {
    EnumCsrTask *t = (EnumCsrTask*)arg;
    int m = t->m, W = t->W;
    uint64_t **cw = t->cw;
    uint64_t *Ei = (uint64_t*)malloc((size_t)W * sizeof(uint64_t));
    for (;;) {
        pthread_mutex_lock(t->lock);
        int i = (*t->next_i)++;
        pthread_mutex_unlock(t->lock);
        if (i >= m) break;
        if (t->inB0[i]) continue;
        words_and(Ei, t->E0, cw[i], W);
        int canonical = 1;
        for (int k = 0; k < i; k++) {
            if (t->inB0[k]) continue;
            if (words_subset(Ei, cw[k], W)) { canonical = 0; break; }
        }
        if (!canonical) continue;
        uint8_t *inBi = (uint8_t*)malloc((size_t)m);
        memcpy(inBi, t->inB0, (size_t)i);
        inBi[i] = 1;
        for (int k = i + 1; k < m; k++)
            inBi[k] = t->inB0[k] ? 1 : (words_subset(Ei, cw[k], W) ? 1 : 0);
        PackVec_push(&t->out, Ei);
        uint64_t *Eic = (uint64_t*)malloc((size_t)W * sizeof(uint64_t));
        memcpy(Eic, Ei, (size_t)W * sizeof(uint64_t));
        cbo_enum_local_csr(m, W, cw, &t->out, Eic, inBi, i);
        free(Eic); free(inBi);
    }
    free(Ei);
    return NULL;
}

/* Extents réduits packés : re = extent(c) − union des extents des enfants. */
typedef struct {
    const uint64_t *all_words; uint64_t *rex_words; int W;
    const int *childrenPtr, *childrenAdj; int N; int *next_c;
} RexCsrTask;

static void *rex_csr_thread(void *arg) {
    RexCsrTask *t = (RexCsrTask*)arg;
    int W = t->W;
    uint64_t *re = (uint64_t*)malloc((size_t)W * sizeof(uint64_t));
    for (;;) {
        int start = atomic_fetch_add_int(t->next_c, COVER_BATCH);
        if (start >= t->N) break;
        int stop = start + COVER_BATCH; if (stop > t->N) stop = t->N;
        for (int c = start; c < stop; c++) {
            memcpy(re, t->all_words + (size_t)c * W, (size_t)W * sizeof(uint64_t));
            for (int k = t->childrenPtr[c]; k < t->childrenPtr[c+1]; k++)
                words_andnot(re, t->all_words + (size_t)t->childrenAdj[k] * W, W);
            memcpy(t->rex_words + (size_t)c * W, re, (size_t)W * sizeof(uint64_t));
        }
    }
    free(re);
    return NULL;
}

typedef struct {
    int N, W, E;
    int nb_objects, nb_attributes;
    int *edge_child, *edge_parent;     /* E */
    uint64_t *rex_words;               /* N*W */
    int *rintPtr;                      /* N+1 */
    int *rintAdj;                      /* rintPtr[N] (<= nb_attributes) */
} CsrLattice;

static void csr_free(CsrLattice *L) {
    if (!L) return;
    free(L->edge_child); free(L->edge_parent);
    free(L->rex_words); free(L->rintPtr); free(L->rintAdj);
    free(L);
}

static CsrLattice *build_lattice_cbo_csr(BinaryContext *ctx) {
    int m = ctx->nb_attributes;
    int W = (ctx->nb_objects + 63) >> 6; if (W < 1) W = 1;

    /* colonnes packées + top packé (lecture seule) */
    uint64_t **cw = (uint64_t**)malloc((size_t)(m>0?m:1)*sizeof(uint64_t*));
    for (int a = 0; a < m; a++) cw[a] = words_from_roaring(ctx->cols[a], W);
    uint64_t *E0 = (uint64_t*)calloc((size_t)W, sizeof(uint64_t));
    int full = ctx->nb_objects >> 6, rem = ctx->nb_objects & 63;
    for (int w = 0; w < full; w++) E0[w] = ~0ULL;
    if (rem) E0[full] = (1ULL << rem) - 1ULL;
    uint8_t *inB0 = (uint8_t*)malloc((size_t)(m>0?m:1));
    for (int a = 0; a < m; a++) inB0[a] = words_subset(E0, cw[a], W) ? 1 : 0;

    /* 1) énumération parallèle → extents packés par thread (pas d'intents) */
    int en_threads = detect_nthreads();
    if (en_threads < 1) en_threads = 1;
    if (m > 0 && en_threads > m) en_threads = m;
    if (en_threads < 1) en_threads = 1;
    int next_i = 0;
    pthread_mutex_t emutex; pthread_mutex_init(&emutex, NULL);
    EnumCsrTask *etasks = (EnumCsrTask*)malloc((size_t)en_threads*sizeof(EnumCsrTask));
    pthread_t *etids = (pthread_t*)malloc((size_t)en_threads*sizeof(pthread_t));
    for (int i = 0; i < en_threads; i++) {
        etasks[i].m=m; etasks[i].W=W; etasks[i].cw=cw; etasks[i].E0=E0; etasks[i].inB0=inB0;
        etasks[i].next_i=&next_i; etasks[i].lock=&emutex; etasks[i].out=PackVec_new(W);
    }
    for (int i=0;i<en_threads;i++) pthread_create(&etids[i],NULL,enum_csr_thread,&etasks[i]);
    for (int i=0;i<en_threads;i++) pthread_join(etids[i],NULL);
    pthread_mutex_destroy(&emutex);

    /* 2) all_words : top (id 0) puis concaténation des extents par thread */
    int N = 1;
    for (int i = 0; i < en_threads; i++) N += etasks[i].out.count;
    uint64_t *all_words = (uint64_t*)malloc((size_t)N*(size_t)W*sizeof(uint64_t));
    memcpy(all_words, E0, (size_t)W*sizeof(uint64_t));
    int id = 1;
    for (int i = 0; i < en_threads; i++) {
        memcpy(all_words + (size_t)id*W, etasks[i].out.data,
               (size_t)etasks[i].out.count*(size_t)W*sizeof(uint64_t));
        id += etasks[i].out.count;
        PackVec_free(&etasks[i].out);
    }
    free(etasks); free(etids);
    for (int a = 0; a < m; a++) free(cw[a]);
    free(cw); free(E0); free(inB0);

    int *all_card = (int*)malloc((size_t)(N>0?N:1)*sizeof(int));
    for (int c = 0; c < N; c++) all_card[c] = words_popcount(all_words + (size_t)c*W, W);

    WMap *wmap = wmap_create(N, W);
    for (int c = 0; c < N; c++) wmap_put(wmap, all_words + (size_t)c*W, c);

    /* 3) intents réduits en CSR (total <= m : un attribut = 1 concept au plus) */
    int *rintPtr = (int*)calloc((size_t)N+1, sizeof(int));
    int *attr_concept = (int*)malloc((size_t)(m>0?m:1)*sizeof(int));
    {
        uint64_t *scratch = (uint64_t*)malloc((size_t)W*sizeof(uint64_t));
        for (int a = 0; a < m; a++) {
            words_from_roaring_into(ctx->cols[a], scratch, W);
            int ida = wmap_get(wmap, scratch);
            attr_concept[a] = ida;
            if (ida >= 0) rintPtr[ida+1]++;
        }
        free(scratch);
    }
    for (int c = 0; c < N; c++) rintPtr[c+1] += rintPtr[c];
    int rintTotal = rintPtr[N];
    int *rintAdj = (int*)malloc((size_t)(rintTotal>0?rintTotal:1)*sizeof(int));
    {
        int *cur = (int*)malloc((size_t)(N+1)*sizeof(int));
        memcpy(cur, rintPtr, (size_t)(N+1)*sizeof(int));
        for (int a = 0; a < m; a++)
            if (attr_concept[a] >= 0) rintAdj[cur[attr_concept[a]]++] = a;
        free(cur);
    }
    free(attr_concept);

    /* 4) irréductibles + colonnes packées */
    int nIrr = 0;
    int *irr = compute_irreducibles(ctx, &nIrr);
    uint64_t **irr_words = (uint64_t**)malloc((size_t)(nIrr>0?nIrr:1)*sizeof(uint64_t*));
    for (int s = 0; s < nIrr; s++) irr_words[s] = words_from_roaring(ctx->cols[irr[s]], W);

    /* 5) couvertures — réutilise cover_thread (ncpt=N, co=NULL) */
    int nthreads = detect_nthreads();
    if (nthreads < 1) nthreads = 1;
    if (N > 0 && nthreads > N) nthreads = N;
    if (nthreads < 1) nthreads = 1;
    int next_c = 0;
    CoverTask *tasks = (CoverTask*)malloc((size_t)nthreads*sizeof(CoverTask));
    pthread_t *tids = (pthread_t*)malloc((size_t)nthreads*sizeof(pthread_t));
    for (int i = 0; i < nthreads; i++) {
        tasks[i].co = NULL; tasks[i].ncpt = N; tasks[i].wmap = wmap;
        tasks[i].all_words = all_words; tasks[i].all_card = all_card;
        tasks[i].irr = irr; tasks[i].nIrr = nIrr; tasks[i].W = W;
        tasks[i].irr_words = irr_words; tasks[i].next_c = &next_c;
        tasks[i].child = IntVec_new(); tasks[i].parent = IntVec_new();
    }
    for (int i=0;i<nthreads;i++) pthread_create(&tids[i],NULL,cover_thread,&tasks[i]);
    for (int i=0;i<nthreads;i++) pthread_join(tids[i],NULL);

    int E = 0; for (int i=0;i<nthreads;i++) E += tasks[i].child.len;
    int *edge_child  = (int*)malloc((size_t)(E>0?E:1)*sizeof(int));
    int *edge_parent = (int*)malloc((size_t)(E>0?E:1)*sizeof(int));
    {
        int ep = 0;
        for (int i = 0; i < nthreads; i++) {
            for (int k = 0; k < tasks[i].child.len; k++) {
                edge_child[ep]  = tasks[i].child.data[k];
                edge_parent[ep] = tasks[i].parent.data[k];
                ep++;
            }
            IntVec_free(&tasks[i].child); IntVec_free(&tasks[i].parent);
        }
    }
    free(tasks); free(tids);
    for (int s=0;s<nIrr;s++) free(irr_words[s]);
    free(irr_words); free(irr);

    /* 6) CSR enfants-par-parent (pour les rextents) */
    int *childrenPtr = (int*)calloc((size_t)N+1, sizeof(int));
    for (int e = 0; e < E; e++) childrenPtr[edge_parent[e]+1]++;
    for (int c = 0; c < N; c++) childrenPtr[c+1] += childrenPtr[c];
    int *childrenAdj = (int*)malloc((size_t)(E>0?E:1)*sizeof(int));
    {
        int *cur = (int*)malloc((size_t)(N+1)*sizeof(int));
        memcpy(cur, childrenPtr, (size_t)(N+1)*sizeof(int));
        for (int e = 0; e < E; e++) childrenAdj[cur[edge_parent[e]]++] = edge_child[e];
        free(cur);
    }

    /* 7) extents réduits packés (parallèle) */
    uint64_t *rex_words = (uint64_t*)malloc((size_t)N*(size_t)W*sizeof(uint64_t));
    {
        int rx_threads = detect_nthreads();
        if (rx_threads < 1) rx_threads = 1;
        if (N > 0 && rx_threads > N) rx_threads = N;
        if (rx_threads < 1) rx_threads = 1;
        int rx_next = 0;
        RexCsrTask *rx = (RexCsrTask*)malloc((size_t)rx_threads*sizeof(RexCsrTask));
        pthread_t *rxt = (pthread_t*)malloc((size_t)rx_threads*sizeof(pthread_t));
        for (int i = 0; i < rx_threads; i++) {
            rx[i].all_words = all_words; rx[i].rex_words = rex_words; rx[i].W = W;
            rx[i].childrenPtr = childrenPtr; rx[i].childrenAdj = childrenAdj;
            rx[i].N = N; rx[i].next_c = &rx_next;
        }
        for (int i=0;i<rx_threads;i++) pthread_create(&rxt[i],NULL,rex_csr_thread,&rx[i]);
        for (int i=0;i<rx_threads;i++) pthread_join(rxt[i],NULL);
        free(rx); free(rxt);
    }

    free(childrenPtr); free(childrenAdj);
    wmap_free(wmap); free(all_words); free(all_card);

    CsrLattice *L = (CsrLattice*)malloc(sizeof(CsrLattice));
    L->N = N; L->W = W; L->E = E;
    L->nb_objects = ctx->nb_objects; L->nb_attributes = ctx->nb_attributes;
    L->edge_child = edge_child; L->edge_parent = edge_parent;
    L->rex_words = rex_words; L->rintPtr = rintPtr; L->rintAdj = rintAdj;
    return L;
}

/* Même format que co_to_flat_array. */
static int *csr_to_flat_array(CsrLattice *L, int *out_len) {
    int N = L->N, W = L->W, E = L->E;
    long total = 2 + 2L*E;
    for (int c = 0; c < N; c++) {
        total += 1 + words_popcount(L->rex_words + (size_t)c*W, W);
        total += 1 + (L->rintPtr[c+1] - L->rintPtr[c]);
    }
    int *buf = (int*)malloc((size_t)total*sizeof(int));
    if (!buf) { *out_len = 0; return NULL; }
    long p = 0;
    buf[p++] = N; buf[p++] = E;
    for (int e = 0; e < E; e++) { buf[p++] = L->edge_child[e]; buf[p++] = L->edge_parent[e]; }
    for (int c = 0; c < N; c++) {
        const uint64_t *re = L->rex_words + (size_t)c*W;
        buf[p++] = words_popcount(re, W);
        for (int wi = 0; wi < W; wi++) {
            uint64_t x = re[wi];
            while (x) { int b = ctz64(x); buf[p++] = (wi<<6) + b; x &= x-1; }
        }
        int s = L->rintPtr[c], e2 = L->rintPtr[c+1];
        buf[p++] = e2 - s;
        for (int k = s; k < e2; k++) buf[p++] = L->rintAdj[k];
    }
    *out_len = (int)p;
    return buf;
}


/* ── Points d'entrée ─────────────────────────────────────────────────────── */
char *run_latticecbo_impl(BinaryContext *ctx) {
    ConceptOrder *co = build_lattice_cbo(ctx);
    char *json = co_to_json(co);
    co_free(co);
    return json;
}

int *run_latticecbo_flat(BinaryContext *ctx, int *out_len) {
    T_DECL();
    ConceptOrder *co = build_lattice_cbo(ctx);
    T_START();
    int *flat = co_to_flat_array(co, out_len);
    T_END("co_to_flat_array");
    co_free(co);
    return flat;
}

int *run_latticecbo_csr_flat(BinaryContext *ctx, int *out_len) {
    CsrLattice *L = build_lattice_cbo_csr(ctx);
    int *flat = csr_to_flat_array(L, out_len);
    csr_free(L);
    return flat;
}
