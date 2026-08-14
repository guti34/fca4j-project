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
 * v3 — INSTRUMENTATION (voir bloc « Profilage » ci-dessous). Aucune modification
 * algorithmique : uniquement des compteurs par thread et des chronos par phase,
 * inactifs tant que la variable d'environnement FCA4J_PROFILE n'est pas posée.
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
#include <stdarg.h>
#include "latticecbo.h"
#include "../core/conceptorder.h"
#include "../core/closure.h"
#include "../core/fca4j_common.h"
#include "../core/bitset.h"
#include "../core/bitset_roaring.h"
#ifndef _WIN32
  #include <unistd.h>   /* sysconf(_SC_NPROCESSORS_ONLN), clock_gettime */
#endif

/* croaring_hardware_support() est declare inconditionnellement dans roaring.h
 * mais n'est DEFINI que sous la garde x86_64/AMD64 de isadetection.c. Sur ARM
 * (Apple Silicon, aarch64) le symbole n'existe pas : y faire reference casse
 * l'edition de liens. On ne l'appelle donc que sur x86, ailleurs on rapporte
 * -1 (« sans objet »). */
#if defined(__x86_64__) || defined(_M_AMD64) || defined(_M_X64)
extern int croaring_hardware_support(void);
#  define FCA4J_SIMD_SUPPORT() croaring_hardware_support()
#else
#  define FCA4J_SIMD_SUPPORT() (-1)
#endif

/* ══════════════════════════════════════════════════════════════════════════
 * PROFILAGE
 *
 * Activation : variable d'environnement FCA4J_PROFILE=1 (absente ou "0" → tout
 * est silencieux et le surcoût se réduit à quelques appels d'horloge par run,
 * plus des compteurs entiers locaux dans l'énumération).
 *
 * Sortie : stdout (visible dans la console quand on lance `java -jar`), et en
 * append dans le fichier désigné par FCA4J_TLOG si cette variable est posée
 * (utile depuis Eclipse, où la sortie native n'apparaît pas).
 *
 *   Windows PowerShell :
 *     $env:FCA4J_PROFILE=1
 *     $env:FCA4J_TLOG="C:\platform\fca4j_timing.log"
 *   bash :
 *     FCA4J_PROFILE=1 FCA4J_TLOG=/tmp/fca4j.log java -jar …
 * ══════════════════════════════════════════════════════════════════════════ */

static int   g_prof = 0;          /* posé une fois par prof_init(), lu ensuite */
static double g_report_ms = 0.0;  /* temps passé dans les fonctions de rapport */
static char  g_prof_path[512];    /* chemin de log, vide si aucun */

static void prof_init(void) {
    const char *e  = getenv("FCA4J_PROFILE");
    g_prof       = (e  && *e  && *e  != '0') ? 1 : 0;
    g_report_ms  = 0.0;
    g_prof_path[0] = '\0';
    if (g_prof) {
        const char *p = getenv("FCA4J_TLOG");
        if (p && *p) {
            size_t n = strlen(p);
            if (n >= sizeof(g_prof_path)) n = sizeof(g_prof_path) - 1;
            memcpy(g_prof_path, p, n);
            g_prof_path[n] = '\0';
        }
    }
}

static void prof_log(const char *fmt, ...) {
    va_list ap;
    if (!g_prof) return;
    va_start(ap, fmt); vprintf(fmt, ap); va_end(ap);
    fflush(stdout);
    if (g_prof_path[0]) {
        FILE *f = fopen(g_prof_path, "a");
        if (f) { va_start(ap, fmt); vfprintf(f, fmt, ap); va_end(ap); fclose(f); }
    }
}

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

/* Chronométrage de phase : PH_DECL une fois, puis PH(v)/PH_END(v,"label"). */
#define PH_DECL()          double _ph = 0.0
#define PH(v)              ((v) = _ms_now())
#define PH_END(v, label)   prof_log("  %-24s %9.2f ms\n", (label), _ms_now() - (v))



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
    uint64_t h = bs_hash(key, m->W);
    int b = (int)(h & (uint64_t)(m->nbuckets - 1));
    WNode *n = &m->pool[m->pool_used++];
    n->key = key; n->hash = h; n->id = id; n->next = m->buckets[b];
    m->buckets[b] = n;
}

/* Insertion avec hash déjà calculé. Le hachage lit W mots par concept, soit
 * tout le store d'extents (505 Mo sur ord10shuttle) : c'est ce qui coûte, et
 * ça se calcule en parallèle. L'insertion elle-même n'est que du chaînage. */
static void wmap_put_h(WMap *m, const uint64_t *key, int id, uint64_t h) {
    int b = (int)(h & (uint64_t)(m->nbuckets - 1));
    WNode *n = &m->pool[m->pool_used++];
    n->key = key; n->hash = h; n->id = id; n->next = m->buckets[b];
    m->buckets[b] = n;
}

static int wmap_get(WMap *m, const uint64_t *key) {
    int W = m->W;
    uint64_t h = bs_hash(key, W);
    int b = (int)(h & (uint64_t)(m->nbuckets - 1));
    for (WNode *n = m->buckets[b]; n; n = n->next) {
        if (n->hash != h) continue;
        if (memcmp(n->key, key, (size_t)W * sizeof(uint64_t)) == 0) return n->id;
    }
    return -1;
}

static void wmap_free(WMap *m) { free(m->buckets); free(m->pool); free(m); }

/* Diagnostic de la table : occupation des buckets et longueur maximale de
 * chaîne. Une longueur maximale élevée signalerait un hachage dégradé (c'était
 * le défaut de la toute première version, à 326 s). */
static void wmap_report(WMap *m) {
    if (g_prof < 2) return;   /* parcourt tous les buckets : coûteux */
    int used = 0, maxlen = 0;
    long long total = 0;
    for (int b = 0; b < m->nbuckets; b++) {
        int len = 0;
        for (WNode *n = m->buckets[b]; n; n = n->next) len++;
        if (len) { used++; total += len; if (len > maxlen) maxlen = len; }
    }
    prof_log("  wmap: %d entrees / %d buckets, occupes=%d (%.1f%%), chaine max=%d, moy=%.2f\n",
             m->pool_used, m->nbuckets, used,
             100.0 * (double)used / (double)m->nbuckets, maxlen,
             used ? (double)total / (double)used : 0.0);
}

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
static roaring_bitmap_t *roaring_from_bools(const uint8_t *inB, int m) {
    roaring_bitmap_t *bm = roaring_bitmap_create();
    for (int k = 0; k < m; k++) if (inB[k]) roaring_bitmap_add(bm, (uint32_t)k);
    return bm;
}

/* ── Compteurs d'énumération (un exemplaire par thread, jamais partagé) ──────
 * attempts     : candidats i évalués (une intersection E ∩ col_i chacun)
 * failures     : rejets de canonicité
 * canon_iters  : itérations de la boucle de canonicité qui appellent réellement
 *                bs_subset (k < i, k hors intent)
 * intent_iters : idem pour le recalcul d'intent (k > i, k hors intent hérité)
 * (canon_iters + intent_iters) × W = volume d'opérations « mot » du test de
 * canonicité et du recalcul d'intent : c'est le chiffre qui arbitre le passage
 * éventuel à une représentation ligne/tableau d'objets.
 * ─────────────────────────────────────────────────────────────────────────── */
typedef struct {
    long long attempts;
    long long failures;
    long long canon_iters;
    long long intent_iters;
} EnumStats;

static inline void enum_stats_zero(EnumStats *s) {
    s->attempts = s->failures = s->canon_iters = s->intent_iters = 0;
}
static inline void enum_stats_add(EnumStats *d, const EnumStats *s) {
    d->attempts += s->attempts; d->failures += s->failures;
    d->canon_iters += s->canon_iters; d->intent_iters += s->intent_iters;
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
        bs_and_to(Ei, E, cw[i], W);                 /* extent fermé, ⊊ E */

        /* canonicité : aucun k<i hors intent(E) tel que Ei ⊆ col_k */
        int canonical = 1;
        for (int k = 0; k < i; k++) {
            if (inB[k]) continue;
            if (bs_subset(Ei, cw[k], W)) { canonical = 0; break; }
        }
        if (!canonical) continue;

        /* intent(Ei) : hérité < i (canonique), i ajouté, recalculé > i */
        uint8_t *inBi = (uint8_t*)malloc((size_t)m);
        memcpy(inBi, inB, (size_t)i);
        inBi[i] = 1;
        for (int k = i + 1; k < m; k++)
            inBi[k] = inB[k] ? 1 : (bs_subset(Ei, cw[k], W) ? 1 : 0);

        BitmapVec_push(exts, bs_to_roaring(Ei, W));
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

        bs_and_to(Ei, t->E0, cw[i], W);
        int canonical = 1;
        for (int k = 0; k < i; k++) {
            if (t->inB0[k]) continue;
            if (bs_subset(Ei, cw[k], W)) { canonical = 0; break; }
        }
        if (!canonical) continue;

        uint8_t *inBi = (uint8_t*)malloc((size_t)m);
        memcpy(inBi, t->inB0, (size_t)i);
        inBi[i] = 1;
        for (int k = i + 1; k < m; k++)
            inBi[k] = t->inB0[k] ? 1 : (bs_subset(Ei, cw[k], W) ? 1 : 0);

        BitmapVec_push(&t->exts, bs_to_roaring(Ei, W));
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
    /* FCA4J_THREADS force le nombre de threads : indispensable pour les tests
     * de reproductibilité et les courbes de passage à l'échelle. */
    const char *e = getenv("FCA4J_THREADS");
    if (e && *e) {
        int n = atoi(e);
        if (n > 0) return n;
    }
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
  static inline int atomic_load_int(const int *p) {
      return (int)_InterlockedCompareExchange((long volatile*)(size_t)p, 0, 0);
  }
  static inline void atomic_store_int(int *p, int v) {
      _InterlockedExchange((long volatile*)p, (long)v);
  }
#else
  static inline int atomic_fetch_add_int(int *p, int v) {
      return __atomic_fetch_add(p, v, __ATOMIC_RELAXED);
  }
  /* Lecture/écriture relâchées : la valeur n'est qu'une heuristique de
   * publication, mais l'accès concurrent doit rester défini. */
  static inline int atomic_load_int(const int *p) {
      return __atomic_load_n(p, __ATOMIC_RELAXED);
  }
  static inline void atomic_store_int(int *p, int v) {
      __atomic_store_n(p, v, __ATOMIC_RELAXED);
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
        bs_from_roaring_into(t->co->extents[c], dst, t->W);
        t->all_card[c] = bs_card(dst, t->W);
    }
    return NULL;
}

typedef struct {
    ConceptOrder *co; int ncpt; WMap *wmap;
    const uint64_t *all_words;   /* extents packés de tous les concepts (lecture seule) */
    const int *all_card;         /* cardinalité de chaque extent (lecture seule) */
    const int *irr; int nIrr; int W;
    uint64_t **irr_words;        /* colonnes packées des irréductibles (lecture seule) */
    const uint64_t *all_intents; /* intents packés (N*Wa), NULL = pas de saut possible */
    int Wa;
    int *next_c;                 /* curseur atomique partagé (fetch-add par lots) */
    IntVec child, parent;        /* arêtes (enfant, parent) produites par ce thread */
    /* ── instrumentation (par thread) ── */
    double    wall_ms;           /* durée de vie du thread */
    int       concepts_done;     /* concepts traités */
    long long cand_built;        /* candidats A ∩ col_mm effectivement construits */
    long long cand_kept;         /* candidats retenus (⊊ A) */
    long long cand_skipped;      /* candidats écartés sans calcul (attribut dans l'intent) */
    long long dom_iters;         /* itérations du test de domination */
} CoverTask;

static inline void cover_stats_zero(CoverTask *t) {
    t->wall_ms = 0.0; t->concepts_done = 0;
    t->cand_built = 0; t->cand_kept = 0; t->cand_skipped = 0; t->dom_iters = 0;
}

/* Couvertures inférieures directes (Hasse), part dynamique de concepts par thread.
 * Tout en mots packés : extent lu directement dans all_words, A∩col_mm = AND,
 * cardinalité = popcount, domination = comparaison de mots, résolution de l'id par
 * wmap_get (hash sur W mots). Aucun roaring, aucune allocation dans la boucle.
 * Lecture seule sur co / wmap / all_words / irr_words. */
static void *cover_thread(void *arg) {
    CoverTask *t = (CoverTask*)arg;
    double t_enter = _ms_now();
    WMap *wmap = t->wmap; int ncpt = t->ncpt;
    const uint64_t *all_words = t->all_words;
    const int *all_card = t->all_card;
    int nIrr = t->nIrr, W = t->W, Wa = t->Wa;
    uint64_t **irw = t->irr_words;
    const int *irr = t->irr;
    const uint64_t *all_intents = t->all_intents;
    const int use_intents = (all_intents != NULL);
    int cap = nIrr ? nIrr : 1;

    long long s_built = 0, s_kept = 0, s_dom = 0, s_skipped = 0;
    int s_done = 0;

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
        s_done++;

        /* candidats = A ∩ col_mm STRICTEMENT inclus dans A (sinon mm ∈ intent(A)).
         *
         * mm ∈ intent(A) ⟺ A ⊆ col_mm ⟺ |A ∩ col_mm| = |A|. Quand l'intent packé
         * est disponible, un test de bit (Wa mots en cache) remplace le AND et le
         * popcount sur W mots — soit ~28 % des candidats écartés sans toucher à
         * col_mm, ce qui allège aussi d'autant le trafic mémoire de la phase.
         * Le test card == cardA est conservé : il est gratuit (card est calculé de
         * toute façon) et couvre le cas all_intents == NULL. */
        const uint64_t *Bc = (use_intents) ? all_intents + (size_t)c * Wa : NULL;
        int kc = 0;
        for (int s = 0; s < nIrr; s++) {
            if (Bc && bs_test(Bc, irr[s])) { s_skipped++; continue; }
            uint64_t *dst = cw + (size_t)kc * W;
            bs_and_to(dst, A, irw[s], W);
            int card = bs_card(dst, W);
            s_built++;
            if (card == cardA) continue;
            cand_card[kc] = card;
            kc++;
        }
        s_kept += kc;

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
                s_dom++;
                if (cand_card[cj] == cand_card[xi]) {
                    if (bs_equal(ccj, cxi, W)) { dominated = 1; break; }   /* doublon */
                } else { /* cand_card[cj] > cand_card[xi] */
                    if (bs_subset(cxi, ccj, W)) { dominated = 1; break; }
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

    t->concepts_done = s_done;
    t->cand_built = s_built;
    t->cand_kept  = s_kept;
    t->cand_skipped = s_skipped;
    t->dom_iters  = s_dom;
    t->wall_ms    = _ms_now() - t_enter;
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
                bs_andnot(re, all_words + (size_t)child * W, W);
                roaring_uint32_iterator_advance(&it);
            }
            bs_add_to_roaring(re, co->rextents[c], W);
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
    PH_DECL();
    prof_init();
    prof_log("\n=== [latticecbo/roaring] contexte %d objets x %d attributs ===\n",
             ctx->nb_objects, ctx->nb_attributes);
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
    for (int a = 0; a < m; a++) cw[a] = bs_from_roaring(ctx->cols[a], W);

    uint64_t *E0 = (uint64_t*)calloc((size_t)W, sizeof(uint64_t));
    int full = ctx->nb_objects >> 6;
    int rem  = ctx->nb_objects & 63;
    for (int w = 0; w < full; w++) E0[w] = ~0ULL;
    if (rem) E0[full] = (1ULL << rem) - 1ULL;

    uint8_t *inB0 = (uint8_t*)malloc((size_t)(m > 0 ? m : 1));
    for (int a = 0; a < m; a++) inB0[a] = bs_subset(E0, cw[a], W) ? 1 : 0;

    /* 1) Énumération CbO — parallèle (sous-arbres de premier niveau, dynamique) */
    PH(_ph);
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
    PH_END(_ph, "1. enumeration");

    /* fusion séquentielle : attribution des IDs globaux */
    PH(_ph);
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
    PH_END(_ph, "2. fusion concepts");

    /* libération de la représentation packée d'énumération */
    for (int a = 0; a < m; a++) free(cw[a]);
    free(cw); free(E0); free(inB0);

    /* 1bis) Store packé de TOUS les extents (parallèle) + map clé-mots.
     * all_words sert à la fois aux intents réduits et à la phase covers ;
     * la wmap (clé = W mots) évite tout roaring/hachage par élément ensuite. */
    PH(_ph);
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
    PH_END(_ph, "3. store packe + wmap");

    /* 2) Intents réduits : pour chaque attribut a, concept dont l'extent == col_a */
    PH(_ph);
    {
        uint64_t *scratch = (uint64_t*)malloc((size_t)W * sizeof(uint64_t));
        for (int a = 0; a < ctx->nb_attributes; a++) {
            bs_from_roaring_into(ctx->cols[a], scratch, W);
            int id = wmap_get(wmap, scratch);
            if (id >= 0) roaring_bitmap_add(co->rintents[id], (uint32_t)a);
        }
        free(scratch);
    }
    PH_END(_ph, "4. intents reduits");

    /* 3) Attributs meet-irréductibles (une fois) */
    PH(_ph);
    int nIrr = 0;
    int *irr = compute_irreducibles(ctx, &nIrr);
    PH_END(_ph, "5. irreductibles");

/* 4) Couvertures inférieures directes (Hasse) — parallèle, part dynamique par concept */
    PH(_ph);
    /* colonnes packées des irréductibles (lecture seule, partagées entre threads) */
    uint64_t **irr_words = (uint64_t**)malloc((size_t)(nIrr > 0 ? nIrr : 1) * sizeof(uint64_t*));
    for (int s = 0; s < nIrr; s++)
        irr_words[s] = bs_from_roaring(ctx->cols[irr[s]], W);

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
        tasks[i].all_intents = NULL;   /* chemin roaring : pas d'intents packés */
        tasks[i].Wa = 0;
        tasks[i].next_c = &next_c;
        tasks[i].child  = IntVec_new();
        tasks[i].parent = IntVec_new();
        cover_stats_zero(&tasks[i]);
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
    PH_END(_ph, "6. couvertures");

    /* 5) Extents réduits — parallèle, packé (réutilise all_words avant libération) */
    PH(_ph);
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
    PH_END(_ph, "7. extents reduits");

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
 * packé→roaring→packé par concept (bs_to_roaring + conv_thread) ET les
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

/* ── File de tâches partagée pour l'énumération ──────────────────────────────
 *
 * L'ancienne distribution ne découpait qu'au PREMIER niveau : un thread prenait
 * un attribut i et faisait tout son sous-arbre en DFS local, sans partage
 * possible ensuite. Or les sous-arbres de premier niveau sont extrêmement
 * déséquilibrés — sur inter3magic04, l'attribut 1 porte 47 % du treillis et
 * imposait à lui seul 385 des 386 ms de la phase (efficacité 0,143).
 *
 * Ici, un thread qui trouve un enfant canonique décide : soit il descend
 * localement, soit il PUBLIE l'enfant comme tâche globale. Le critère est
 * l'état de la file — si elle contient moins de `spawn_target` tâches, c'est
 * que des threads risquent de manquer de travail, donc on publie. C'est le
 * pendant du getSurplusQueuedTaskCount() de Lattice_ParallelCbO côté Java.
 *
 * Le découpage est ainsi ADAPTATIF en profondeur : un gros sous-arbre se
 * décompose tout seul aussi longtemps que nécessaire, un petit est traité
 * localement sans surcoût de synchronisation.
 *
 * Terminaison : une file vide ne signifie pas la fin, un thread encore actif
 * pouvant publier. On compte les threads occupés (`busy`) et on ne sort que
 * lorsque file vide ET busy == 0, les dormeurs étant réveillés par diffusion.
 * ─────────────────────────────────────────────────────────────────────────── */
typedef struct {
    uint64_t *E;      /* extent packé (W mots), possédé par la tâche */
    uint8_t  *inB;    /* intent en octets (m), possédé par la tâche */
    int       y;      /* dernier attribut ajouté */
    int       depth;  /* profondeur dans l'arbre CbO */
} EnumJob;

typedef struct {
    EnumJob        *jobs;
    int             len, cap;
    int             len_hint;   /* lu en atomique relachee : heuristique de publication */
    int             published;  /* tâches publiées depuis le début (budget) */
    int             busy;
    pthread_mutex_t lock;
    pthread_cond_t  cv;
} JobQueue;

static void jq_init(JobQueue *q) {
    q->cap = 256; q->len = 0; q->busy = 0;
    atomic_store_int(&q->len_hint, 0);
    atomic_store_int(&q->published, 0);
    q->jobs = (EnumJob*)malloc((size_t)q->cap * sizeof(EnumJob));
    pthread_mutex_init(&q->lock, NULL);
    pthread_cond_init(&q->cv, NULL);
}

static void jq_destroy(JobQueue *q) {
    free(q->jobs);
    pthread_mutex_destroy(&q->lock);
    pthread_cond_destroy(&q->cv);
}

/* La file prend possession de E et inB. */
static void jq_push(JobQueue *q, uint64_t *E, uint8_t *inB, int y, int depth) {
    pthread_mutex_lock(&q->lock);
    if (q->len >= q->cap) {
        q->cap *= 2;
        q->jobs = (EnumJob*)realloc(q->jobs, (size_t)q->cap * sizeof(EnumJob));
    }
    q->jobs[q->len].E = E; q->jobs[q->len].inB = inB; q->jobs[q->len].y = y;
    q->jobs[q->len].depth = depth;
    q->len++; atomic_store_int(&q->len_hint, q->len);
    atomic_fetch_add_int(&q->published, 1);
    pthread_cond_signal(&q->cv);
    pthread_mutex_unlock(&q->lock);
}

/* Retourne 0 quand tout est fini. LIFO : préserve la localité du parcours. */
static int jq_pop(JobQueue *q, EnumJob *out) {
    pthread_mutex_lock(&q->lock);
    for (;;) {
        if (q->len > 0) {
            q->len--; atomic_store_int(&q->len_hint, q->len);
            *out = q->jobs[q->len];
            q->busy++;
            pthread_mutex_unlock(&q->lock);
            return 1;
        }
        if (q->busy == 0) {                 /* file vide et personne au travail */
            pthread_cond_broadcast(&q->cv);
            pthread_mutex_unlock(&q->lock);
            return 0;
        }
        pthread_cond_wait(&q->cv, &q->lock);
    }
}

static void jq_done(JobQueue *q) {
    pthread_mutex_lock(&q->lock);
    q->busy--;
    if (q->busy == 0 && q->len == 0) pthread_cond_broadcast(&q->cv);
    pthread_mutex_unlock(&q->lock);
}

typedef struct {
    int m, W, Wa;
    uint64_t **cw;
    JobQueue *jq;
    int spawn_target;   /* on publie tant que la file est plus courte que ça */
    int spawn_depth;    /* on ne publie que les nœuds peu profonds */
    int max_tasks;      /* budget global : filet de sécurité */
} EnumCtx;

/* Publier ? Deux conditions.
 *
 * (1) La file doit être courte — sinon il y a déjà de quoi occuper tout le
 *     monde et une descente locale est préférable (pas de verrou, meilleure
 *     localité de cache).
 *
 * (2) Un BUDGET global doit rester. Sans lui le mécanisme dégénère : quand N
 *     threads vident la file aussi vite qu'elle se remplit, la condition (1)
 *     est vraie en permanence et CHAQUE concept devient une tâche — mesuré,
 *     104 597 tâches pour 104 597 concepts, soit un parcours en largeur avec
 *     un verrou et deux allocations par concept. Le budget borne le surcoût
 *     de synchronisation tout en laissant le découpage s'adapter là où il
 *     sert : ce sont les gros sous-arbres qui sont en cours de traitement
 *     quand la file se vide, donc ce sont eux qui se font découper. */
static inline int jq_should_publish(const EnumCtx *ec, int depth) {
    return depth < ec->spawn_depth
        && atomic_load_int(&ec->jq->len_hint) < ec->spawn_target
        && atomic_load_int(&ec->jq->published) < ec->max_tasks;
}

/* Énumération CbO packée. Publie ou descend selon l'état de la file. */
static void cbo_enum_job(const EnumCtx *ec, PackVec *out, PackVec *outB,
                         EnumStats *st, const uint64_t *E, const uint8_t *inB,
                         int y, int depth) {
    int m = ec->m, W = ec->W, Wa = ec->Wa;
    uint64_t **cw = ec->cw;
    long long att = 0, fail = 0, ci = 0, ii = 0;
    uint64_t *Ei = (uint64_t*)malloc((size_t)W * sizeof(uint64_t));
    uint64_t *Bi = (uint64_t*)malloc((size_t)Wa * sizeof(uint64_t));

    for (int i = y + 1; i < m; i++) {
        if (inB[i]) continue;
        att++;
        bs_and_to(Ei, E, cw[i], W);
        int canonical = 1;
        for (int k = 0; k < i; k++) {
            if (inB[k]) continue;
            ci++;
            if (bs_subset(Ei, cw[k], W)) { canonical = 0; break; }
        }
        if (!canonical) { fail++; continue; }

        uint8_t *inBi = (uint8_t*)malloc((size_t)m);
        memcpy(inBi, inB, (size_t)i);
        inBi[i] = 1;
        for (int k = i + 1; k < m; k++) {
            if (inB[k]) { inBi[k] = 1; continue; }
            ii++;
            inBi[k] = bs_subset(Ei, cw[k], W) ? 1 : 0;
        }
        memset(Bi, 0, (size_t)Wa * sizeof(uint64_t));
        for (int k = 0; k < m; k++)
            if (inBi[k]) Bi[k >> 6] |= (1ULL << (k & 63));

        PackVec_push(out, Ei);
        PackVec_push(outB, Bi);

        uint64_t *Ec = (uint64_t*)malloc((size_t)W * sizeof(uint64_t));
        memcpy(Ec, Ei, (size_t)W * sizeof(uint64_t));

        if (jq_should_publish(ec, depth)) {
            jq_push(ec->jq, Ec, inBi, i, depth + 1);   /* la file prend possession */
        } else {
            cbo_enum_job(ec, out, outB, st, Ec, inBi, i, depth + 1);
            free(Ec); free(inBi);
        }
    }
    free(Ei); free(Bi);
    st->attempts += att; st->failures += fail;
    st->canon_iters += ci; st->intent_iters += ii;
}

typedef struct {
    const EnumCtx *ec;
    PackVec out;              /* extents packés (W mots) */
    PackVec outB;             /* intents packés (Wa mots), même ordre */
    EnumStats st;
    double    wall_ms;
    double    busy_ms;
    int       jobs_taken;
    int       max_job_nodes;  /* plus grosse tâche traitée par ce thread */
} EnumCsrTask;

static void *enum_csr_thread(void *arg) {
    EnumCsrTask *t = (EnumCsrTask*)arg;
    double t_enter = _ms_now();
    double busy = 0.0;
    JobQueue *q = t->ec->jq;
    EnumJob job;
    enum_stats_zero(&t->st);
    t->jobs_taken = 0; t->max_job_nodes = 0;

    while (jq_pop(q, &job)) {
        double r0 = _ms_now();
        int before = t->out.count;
        cbo_enum_job(t->ec, &t->out, &t->outB, &t->st, job.E, job.inB, job.y, job.depth);
        free(job.E); free(job.inB);
        busy += _ms_now() - r0;
        t->jobs_taken++;
        int produced = t->out.count - before;
        if (produced > t->max_job_nodes) t->max_job_nodes = produced;
        jq_done(q);
    }
    t->busy_ms = busy;
    t->wall_ms = _ms_now() - t_enter;
    return NULL;
}

/* ── Extents réduits ─────────────────────────────────────────────────────────
 * rextent(c) = extent(c) privé de l'union des extents des enfants directs.
 *
 * La version précédente matérialisait ça dans un bitmap N*W (505 Mo sur
 * ord10shuttle) — pour n'y stocker au total que nb_objects bits. En effet un
 * objet o appartient au rextent d'EXACTEMENT un concept : son concept-objet,
 * le plus petit contenant o. La somme des cardinalités vaut donc nb_objects,
 * soit 43 500 entiers logés dans 505 Mo. Le bitmap était alloué, écrit, relu
 * par popcount dans csr_to_flat_array, puis libéré.
 *
 * On produit désormais directement des paires (concept, objet) dans un vecteur
 * par thread. Le calcul (les andnot sur W mots) est inchangé ; seul le stockage
 * du résultat devient proportionnel au résultat. */
typedef struct {
    const uint64_t *all_words; int W;
    const int *childrenPtr, *childrenAdj; int N; int *next_c;
    int *rex_card;               /* écriture disjointe : rex_card[c] */
    IntVec pair_c, pair_o;       /* paires (concept, objet) locales au thread */
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
                bs_andnot(re, t->all_words + (size_t)t->childrenAdj[k] * W, W);
            int card = 0;
            for (int wi = 0; wi < W; wi++) {
                uint64_t x = re[wi];
                while (x) {
                    int b = AW_CTZ(x);
                    IntVec_push(&t->pair_c, c);
                    IntVec_push(&t->pair_o, (wi << 6) + b);
                    card++;
                    x &= x - 1;
                }
            }
            t->rex_card[c] = card;
        }
    }
    free(re);
    return NULL;
}

/* ── Passe post-énumération, parallèle ───────────────────────────────────────
 * Remplace trois parcours séquentiels du store d'extents (concaténation,
 * popcount, hachage) qui pesaient 332 ms sur ord10shuttle pour 505 Mo.
 *
 * CopyTask   : chaque thread recopie SON bloc de PackVec vers all_words /
 *              all_intents, à un offset connu après la jointure.
 * DigestTask : parcours équilibré par lots atomiques sur les concepts, qui
 *              calcule cardinalité ET hash en touchant les données une fois.
 *              Le hash est ensuite consommé par wmap_put_h en séquentiel, où
 *              il ne reste que du chaînage de pointeurs. */
typedef struct {
    const uint64_t *src_e, *src_b;
    uint64_t *dst_e, *dst_b;
    int count, W, Wa;
} CopyTask;

static void *copy_thread(void *arg) {
    CopyTask *t = (CopyTask*)arg;
    if (t->count > 0) {
        memcpy(t->dst_e, t->src_e, (size_t)t->count * (size_t)t->W  * sizeof(uint64_t));
        memcpy(t->dst_b, t->src_b, (size_t)t->count * (size_t)t->Wa * sizeof(uint64_t));
    }
    return NULL;
}

typedef struct {
    const uint64_t *all_words; int W; int N; int *next_c;
    int *all_card; uint64_t *hashes;
} DigestTask;

static void *digest_thread(void *arg) {
    DigestTask *t = (DigestTask*)arg;
    int W = t->W;
    for (;;) {
        int start = atomic_fetch_add_int(t->next_c, COVER_BATCH);
        if (start >= t->N) break;
        int stop = start + COVER_BATCH; if (stop > t->N) stop = t->N;
        for (int c = start; c < stop; c++) {
            const uint64_t *p = t->all_words + (size_t)c * W;
            t->all_card[c] = bs_card(p, W);
            t->hashes[c]   = bs_hash(p, W);
        }
    }
    return NULL;
}

typedef struct {
    int N, W, E;
    int nb_objects, nb_attributes;
    int *edge_pairs;                   /* 2E, déjà entrelacé (child, parent) */
    int *rexPtr, *rexAdj;              /* N+1, rexPtr[N] */
    int *rintPtr;                      /* N+1 */
    int *rintAdj;                      /* rintPtr[N] (<= nb_attributes) */
} CsrLattice;

static void csr_free(CsrLattice *L) {
    if (!L) return;
    free(L->edge_pairs);
    free(L->rexPtr); free(L->rexAdj);
    free(L->rintPtr); free(L->rintAdj);
    free(L);
}

/* ── Rapports d'instrumentation ─────────────────────────────────────────── */

/* Statistiques de cardinalité des extents.
 *
 * Enjeu : à W mots, chaque AND ou test d'inclusion coûte W opérations quelle que
 * soit la taille RÉELLE de l'extent. Une représentation par liste triée d'objets
 * coûterait O(|A|) tests de bit. Le point d'équilibre est |A| ≈ W (2W opérations
 * mot contre ~2|A| tests). La colonne « <= W » donne donc directement la part des
 * concepts pour lesquels le changement de représentation serait gagnant. */
static void report_extent_cards(const int *all_card, int N, int W, int nObj) {
    if (!g_prof || N <= 0) return;
    double t0 = _ms_now();
    long long sum = 0;
    int mx = 0, mn = 0x7fffffff, below = 0;
    long long hist[24];
    for (int i = 0; i < 24; i++) hist[i] = 0;
    for (int c = 0; c < N; c++) {
        int k = all_card[c];
        sum += k;
        if (k > mx) mx = k;
        if (k < mn) mn = k;
        if (k <= W) below++;
        int b = 0; while ((1 << (b + 1)) <= k && b < 23) b++;
        hist[k > 0 ? b : 0]++;
    }
    double mean = (double)sum / (double)N;
    prof_log("  cardinalite extents : moy=%.1f  min=%d  max=%d  (sur %d objets, W=%d)\n",
             mean, mn == 0x7fffffff ? 0 : mn, mx, nObj, W);
    prof_log("     concepts avec |A| <= W : %d / %d (%.1f%%)  -> part gagnante pour"
             " une representation par liste d'objets\n",
             below, N, 100.0 * (double)below / (double)N);
    prof_log("     volume theorique : mots packes = %.3f G,  liste d'objets = %.3f G"
             "  (ratio %.1fx)\n",
             (double)N * (double)W / 1e9, (double)sum / 1e9,
             sum ? (double)N * (double)W / (double)sum : 0.0);
    if (g_prof >= 2) {
        prof_log("     histogramme |A| :");
        for (int b = 0; b < 24; b++)
            if (hist[b]) prof_log(" [%d..%d)=%lld", 1 << b, 1 << (b + 1), hist[b]);
        prof_log("\n");
    }
    g_report_ms += _ms_now() - t0;
}

static void report_enum(EnumCsrTask *et, int nt, int N, int W, int m,
                        int spawn_target, int spawn_depth, int max_tasks, double wall_ms) {
    if (!g_prof) return;
    double t_rep = _ms_now();
    EnumStats tot; enum_stats_zero(&tot);
    double busy_sum = 0.0, wall_max = 0.0;
    int biggest_job = 0, jobs_total = 0;
    int per_thread_min = 0x7fffffff, per_thread_max = 0;

    for (int i = 0; i < nt; i++) {
        enum_stats_add(&tot, &et[i].st);
        busy_sum += et[i].busy_ms;
        jobs_total += et[i].jobs_taken;
        if (et[i].wall_ms > wall_max) wall_max = et[i].wall_ms;
        if (et[i].out.count < per_thread_min) per_thread_min = et[i].out.count;
        if (et[i].out.count > per_thread_max) per_thread_max = et[i].out.count;
        if (et[i].max_job_nodes > biggest_job) biggest_job = et[i].max_job_nodes;
    }
    if (per_thread_min == 0x7fffffff) per_thread_min = 0;

    prof_log("\n  --- enumeration : %d threads, %.2f ms (mur), seuil=%d prof=%d budget=%d ---\n",
             nt, wall_ms, spawn_target, spawn_depth, max_tasks);
    prof_log("  %3s %10s %8s %10s %10s %10s\n",
             "th", "concepts", "taches", "busy(ms)", "wall(ms)", "maxTache");
    for (int i = 0; i < nt; i++)
        prof_log("  %3d %10d %8d %10.2f %10.2f %10d\n",
                 i, et[i].out.count, et[i].jobs_taken,
                 et[i].busy_ms, et[i].wall_ms, et[i].max_job_nodes);

    double ideal = busy_sum / (double)nt;
    prof_log("  equilibrage      : efficacite = %.3f  (1.0 = parfait ; busy_moyen %.2f ms"
             " vs thread le plus long %.2f ms)\n",
             (wall_max > 0.0) ? ideal / wall_max : 0.0, ideal, wall_max);
    prof_log("  concepts/thread  : min=%d max=%d  ratio=%.2f  (ideal = %d)\n",
             per_thread_min, per_thread_max,
             per_thread_min ? (double)per_thread_max / (double)per_thread_min : 0.0,
             nt ? (N - 1) / nt : 0);
    prof_log("  taches           : %d publiees, plus grosse = %d concepts (%.1f%% du treillis)\n",
             jobs_total, biggest_job,
             N > 0 ? 100.0 * (double)biggest_job / (double)N : 0.0);
    prof_log("  candidats testes : %lld   rejets canonicite : %lld  (taux = %.3f)\n",
             tot.attempts, tot.failures,
             tot.attempts ? (double)tot.failures / (double)tot.attempts : 0.0);
    prof_log("  bs_subset     : canonicite=%lld  intent=%lld  total=%lld\n",
             tot.canon_iters, tot.intent_iters, tot.canon_iters + tot.intent_iters);
    prof_log("  volume mots 64b  : %.3f G operations  (W=%d, m=%d)\n",
             (double)(tot.canon_iters + tot.intent_iters) * (double)W / 1e9, W, m);
    prof_log("  cout moyen par candidat : %.1f mots (borne theorique m*W = %d)\n",
             tot.attempts ? (double)(tot.canon_iters + tot.intent_iters) * (double)W
                            / (double)tot.attempts : 0.0,
             m * W);
    g_report_ms += _ms_now() - t_rep;
}

static void report_covers(CoverTask *ct, int nt, int N, int W, int nIrr, double wall_ms) {
    if (!g_prof) return;
    double t_rep = _ms_now();
    double busy_sum = 0.0, wall_max = 0.0;
    long long built = 0, kept = 0, dom = 0, edges = 0, skipped = 0;
    int done_min = 0x7fffffff, done_max = 0;
    for (int i = 0; i < nt; i++) {
        busy_sum += ct[i].wall_ms;
        if (ct[i].wall_ms > wall_max) wall_max = ct[i].wall_ms;
        built += ct[i].cand_built; kept += ct[i].cand_kept; dom += ct[i].dom_iters;
        skipped += ct[i].cand_skipped;
        edges += ct[i].child.len;
        if (ct[i].concepts_done < done_min) done_min = ct[i].concepts_done;
        if (ct[i].concepts_done > done_max) done_max = ct[i].concepts_done;
    }
    if (done_min == 0x7fffffff) done_min = 0;
    double ideal = busy_sum / (double)nt;
    long long total_cand = built + skipped;
    /* trafic : chaque candidat construit lit une colonne (W mots) et écrit puis
     * relit son propre bloc (2W mots si non fusionné) */
    double traffic_go = (double)built * (double)W * 8.0 * 3.0 / 1073741824.0;
    prof_log("\n  --- couvertures : %d threads, %.2f ms (mur) ---\n", nt, wall_ms);
    prof_log("  equilibrage      : efficacite = %.3f  (concepts/thread min=%d max=%d)\n",
             wall_max > 0.0 ? ideal / wall_max : 0.0, done_min, done_max);
    prof_log("  candidats        : %lld au total, %lld evites par l'intent (%.1f%%),"
             " %lld construits, %lld retenus\n",
             total_cand, skipped,
             total_cand ? 100.0 * (double)skipped / (double)total_cand : 0.0,
             built, kept);
    prof_log("  volume mots 64b  : AND+popcount = %.3f G,  domination = %.3f G\n",
             (double)built * (double)W * 2.0 / 1e9, (double)dom * (double)W / 1e9);
    prof_log("  trafic memoire   : ~%.2f Go en %.2f ms = %.1f Go/s\n",
             traffic_go, wall_ms, wall_ms > 0.0 ? traffic_go * 1000.0 / wall_ms : 0.0);
    prof_log("  aretes produites : %lld  (%.2f par concept, nIrr=%d)\n",
             edges, N ? (double)edges / (double)N : 0.0, nIrr);
    g_report_ms += _ms_now() - t_rep;
}

static CsrLattice *build_lattice_cbo_csr(BinaryContext *ctx) {
    PH_DECL();
    double t_all = _ms_now();
    prof_init();

    int m = ctx->nb_attributes;
    int W = (ctx->nb_objects + 63) >> 6; if (W < 1) W = 1;
    int Wa = (m + 63) >> 6; if (Wa < 1) Wa = 1;

    prof_log("\n=== [latticecbo/CSR] contexte %d objets x %d attributs"
             "  (W=%d mots/extent, Wa=%d mots/intent) ===\n",
             ctx->nb_objects, m, W, Wa);
    prof_log("  SIMD croaring    : %d (-1=sans objet hors x86, 0=scalaire,"
             " 1=AVX2, 3=AVX512)\n", FCA4J_SIMD_SUPPORT());
    prof_log("  profilage        : niveau %d  (2 = rapports detailles, plus couteux)\n",
             g_prof);

    /* 0) colonnes packées + top packé (lecture seule) */
    PH(_ph);
    uint64_t **cw = (uint64_t**)malloc((size_t)(m>0?m:1)*sizeof(uint64_t*));
    for (int a = 0; a < m; a++) cw[a] = bs_from_roaring(ctx->cols[a], W);
    uint64_t *E0 = (uint64_t*)calloc((size_t)W, sizeof(uint64_t));
    int full = ctx->nb_objects >> 6, rem = ctx->nb_objects & 63;
    for (int w = 0; w < full; w++) E0[w] = ~0ULL;
    if (rem) E0[full] = (1ULL << rem) - 1ULL;
    uint8_t *inB0 = (uint8_t*)malloc((size_t)(m>0?m:1));
    for (int a = 0; a < m; a++) inB0[a] = bs_subset(E0, cw[a], W) ? 1 : 0;
    PH_END(_ph, "0. colonnes packees");

    /* 1) énumération parallèle : file de tâches partagée, publication adaptative */
    double t_enum = _ms_now();
    int en_threads = detect_nthreads();
    if (en_threads < 1) en_threads = 1;

    int spawn_target = 2 * en_threads;
    int spawn_depth  = 4;
    int max_tasks    = 1 << 20;   /* filet de sécurité, ne devrait jamais mordre */
    {
        const char *sp = getenv("FCA4J_SPAWN");
        if (sp && *sp) { int v = atoi(sp); if (v > 0) spawn_target = v; }
        const char *sd = getenv("FCA4J_SPAWNDEPTH");
        if (sd && *sd) { int v = atoi(sd); if (v > 0) spawn_depth = v; }
        const char *mt = getenv("FCA4J_MAXTASKS");
        if (mt && *mt) { int v = atoi(mt); if (v > 0) max_tasks = v; }
    }

    JobQueue jq; jq_init(&jq);
    EnumCtx ectx;
    ectx.m = m; ectx.W = W; ectx.Wa = Wa; ectx.cw = cw;
    ectx.jq = &jq; ectx.spawn_target = spawn_target; ectx.spawn_depth = spawn_depth;
    ectx.max_tasks = max_tasks;

    /* tâche initiale : le concept top, développé à partir de y = -1 */
    {
        uint64_t *Er = (uint64_t*)malloc((size_t)W * sizeof(uint64_t));
        memcpy(Er, E0, (size_t)W * sizeof(uint64_t));
        uint8_t *Br = (uint8_t*)malloc((size_t)(m > 0 ? m : 1));
        memcpy(Br, inB0, (size_t)(m > 0 ? m : 1));
        jq_push(&jq, Er, Br, -1, 0);
    }

    EnumCsrTask *etasks = (EnumCsrTask*)malloc((size_t)en_threads*sizeof(EnumCsrTask));
    pthread_t *etids = (pthread_t*)malloc((size_t)en_threads*sizeof(pthread_t));
    for (int i = 0; i < en_threads; i++) {
        etasks[i].ec = &ectx;
        etasks[i].out = PackVec_new(W); etasks[i].outB = PackVec_new(Wa);
        enum_stats_zero(&etasks[i].st);
        etasks[i].wall_ms = etasks[i].busy_ms = 0.0;
        etasks[i].jobs_taken = 0; etasks[i].max_job_nodes = 0;
    }
    for (int i=0;i<en_threads;i++) pthread_create(&etids[i],NULL,enum_csr_thread,&etasks[i]);
    for (int i=0;i<en_threads;i++) pthread_join(etids[i],NULL);
    jq_destroy(&jq);
    t_enum = _ms_now() - t_enum;

    int N = 1;
    for (int i = 0; i < en_threads; i++) N += etasks[i].out.count;
    report_enum(etasks, en_threads, N, W, m, spawn_target, spawn_depth, max_tasks, t_enum);
    prof_log("  1. enumeration           %9.2f ms  -> %d concepts\n", t_enum, N);

    /* 2) all_words / all_intents : top (id 0) puis recopie PARALLÈLE par thread */
    PH(_ph);
    uint64_t *all_words = (uint64_t*)malloc((size_t)N*(size_t)W*sizeof(uint64_t));
    uint64_t *all_intents = (uint64_t*)malloc((size_t)N*(size_t)Wa*sizeof(uint64_t));
    memcpy(all_words, E0, (size_t)W*sizeof(uint64_t));
    memset(all_intents, 0, (size_t)Wa*sizeof(uint64_t));
    for (int a = 0; a < m; a++)
        if (inB0[a]) all_intents[a >> 6] |= (1ULL << (a & 63));
    {
        CopyTask *cp = (CopyTask*)malloc((size_t)en_threads*sizeof(CopyTask));
        pthread_t *cpt = (pthread_t*)malloc((size_t)en_threads*sizeof(pthread_t));
        int id = 1;
        for (int i = 0; i < en_threads; i++) {
            cp[i].src_e = etasks[i].out.data;  cp[i].src_b = etasks[i].outB.data;
            cp[i].dst_e = all_words + (size_t)id*W;
            cp[i].dst_b = all_intents + (size_t)id*Wa;
            cp[i].count = etasks[i].out.count; cp[i].W = W; cp[i].Wa = Wa;
            id += etasks[i].out.count;
        }
        for (int i=0;i<en_threads;i++) pthread_create(&cpt[i],NULL,copy_thread,&cp[i]);
        for (int i=0;i<en_threads;i++) pthread_join(cpt[i],NULL);
        free(cp); free(cpt);
    }
    for (int i = 0; i < en_threads; i++) {
        PackVec_free(&etasks[i].out);
        PackVec_free(&etasks[i].outB);
    }
    free(etasks); free(etids);
    for (int a = 0; a < m; a++) free(cw[a]);
    free(cw); free(E0); free(inB0);
    PH_END(_ph, "2. concat (parallele)");
    prof_log("     (extents = %.1f Mo, intents = %.1f Mo)\n",
             (double)N * (double)W * 8.0 / 1048576.0,
             (double)N * (double)Wa * 8.0 / 1048576.0);

    /* 3) cardinalités + hachages, en une passe parallèle équilibrée */
    PH(_ph);
    int *all_card = (int*)malloc((size_t)(N>0?N:1)*sizeof(int));
    uint64_t *hashes = (uint64_t*)malloc((size_t)(N>0?N:1)*sizeof(uint64_t));
    {
        int dg_threads = detect_nthreads();
        if (dg_threads < 1) dg_threads = 1;
        if (N > 0 && dg_threads > N) dg_threads = N;
        int dg_next = 0;
        DigestTask *dg = (DigestTask*)malloc((size_t)dg_threads*sizeof(DigestTask));
        pthread_t *dgt = (pthread_t*)malloc((size_t)dg_threads*sizeof(pthread_t));
        for (int i = 0; i < dg_threads; i++) {
            dg[i].all_words = all_words; dg[i].W = W; dg[i].N = N;
            dg[i].next_c = &dg_next; dg[i].all_card = all_card; dg[i].hashes = hashes;
        }
        for (int i=0;i<dg_threads;i++) pthread_create(&dgt[i],NULL,digest_thread,&dg[i]);
        for (int i=0;i<dg_threads;i++) pthread_join(dgt[i],NULL);
        free(dg); free(dgt);
    }
    PH_END(_ph, "3. cards+hash (parallele)");
    report_extent_cards(all_card, N, W, ctx->nb_objects);

    PH(_ph);
    WMap *wmap = wmap_create(N, W);
    for (int c = 0; c < N; c++) wmap_put_h(wmap, all_words + (size_t)c*W, c, hashes[c]);
    free(hashes);
    PH_END(_ph, "4. wmap (insertion)");
    wmap_report(wmap);

    /* 3) intents réduits en CSR (total <= m : un attribut = 1 concept au plus) */
    PH(_ph);
    int *rintPtr = (int*)calloc((size_t)N+1, sizeof(int));
    int *attr_concept = (int*)malloc((size_t)(m>0?m:1)*sizeof(int));
    {
        uint64_t *scratch = (uint64_t*)malloc((size_t)W*sizeof(uint64_t));
        for (int a = 0; a < m; a++) {
            bs_from_roaring_into(ctx->cols[a], scratch, W);
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
    PH_END(_ph, "5. intents reduits");

    /* 4) irréductibles + colonnes packées */
    PH(_ph);
    int nIrr = 0;
    int *irr = compute_irreducibles(ctx, &nIrr);
    uint64_t **irr_words = (uint64_t**)malloc((size_t)(nIrr>0?nIrr:1)*sizeof(uint64_t*));
    for (int s = 0; s < nIrr; s++) irr_words[s] = bs_from_roaring(ctx->cols[irr[s]], W);
    PH_END(_ph, "6. irreductibles");
    prof_log("     (%d irreductibles / %d attributs)\n", nIrr, m);

    /* 5) couvertures — réutilise cover_thread (ncpt=N, co=NULL) */
    double t_cov = _ms_now();
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
        tasks[i].all_intents = all_intents; tasks[i].Wa = Wa;
        tasks[i].child = IntVec_new(); tasks[i].parent = IntVec_new();
        cover_stats_zero(&tasks[i]);
    }
    for (int i=0;i<nthreads;i++) pthread_create(&tids[i],NULL,cover_thread,&tasks[i]);
    for (int i=0;i<nthreads;i++) pthread_join(tids[i],NULL);
    t_cov = _ms_now() - t_cov;

    int E = 0; for (int i=0;i<nthreads;i++) E += tasks[i].child.len;
    report_covers(tasks, nthreads, N, W, nIrr, t_cov);
    prof_log("  7. couvertures           %9.2f ms  -> %d aretes\n", t_cov, E);

    PH(_ph);
    int *edge_pairs = (int*)malloc((size_t)(E>0?2*E:1)*sizeof(int));
    {
        int ep = 0;
        for (int i = 0; i < nthreads; i++) {
            for (int k = 0; k < tasks[i].child.len; k++) {
                edge_pairs[ep++] = tasks[i].child.data[k];
                edge_pairs[ep++] = tasks[i].parent.data[k];
            }
            IntVec_free(&tasks[i].child); IntVec_free(&tasks[i].parent);
        }
    }
    free(tasks); free(tids);
    for (int s=0;s<nIrr;s++) free(irr_words[s]);
    free(irr_words); free(irr);
    PH_END(_ph, "8. concat aretes");

    /* 6) CSR enfants-par-parent (pour les rextents) */
    PH(_ph);
    int *childrenPtr = (int*)calloc((size_t)N+1, sizeof(int));
    for (int e = 0; e < E; e++) childrenPtr[edge_pairs[2*e+1]+1]++;
    for (int c = 0; c < N; c++) childrenPtr[c+1] += childrenPtr[c];
    int *childrenAdj = (int*)malloc((size_t)(E>0?E:1)*sizeof(int));
    {
        int *cur = (int*)malloc((size_t)(N+1)*sizeof(int));
        memcpy(cur, childrenPtr, (size_t)(N+1)*sizeof(int));
        for (int e = 0; e < E; e++)
            childrenAdj[cur[edge_pairs[2*e+1]]++] = edge_pairs[2*e];
        free(cur);
    }
    PH_END(_ph, "9. CSR enfants");

    /* 7) extents réduits : calcul parallèle, stockage proportionnel au résultat */
    PH(_ph);
    int *rex_card = (int*)calloc((size_t)(N>0?N:1), sizeof(int));
    int *rexPtr = (int*)calloc((size_t)N+1, sizeof(int));
    int *rexAdj = NULL;
    int rexTotal = 0;
    {
        int rx_threads = detect_nthreads();
        if (rx_threads < 1) rx_threads = 1;
        if (N > 0 && rx_threads > N) rx_threads = N;
        int rx_next = 0;
        RexCsrTask *rx = (RexCsrTask*)malloc((size_t)rx_threads*sizeof(RexCsrTask));
        pthread_t *rxt = (pthread_t*)malloc((size_t)rx_threads*sizeof(pthread_t));
        for (int i = 0; i < rx_threads; i++) {
            rx[i].all_words = all_words; rx[i].W = W;
            rx[i].childrenPtr = childrenPtr; rx[i].childrenAdj = childrenAdj;
            rx[i].N = N; rx[i].next_c = &rx_next; rx[i].rex_card = rex_card;
            rx[i].pair_c = IntVec_new(); rx[i].pair_o = IntVec_new();
        }
        for (int i=0;i<rx_threads;i++) pthread_create(&rxt[i],NULL,rex_csr_thread,&rx[i]);
        for (int i=0;i<rx_threads;i++) pthread_join(rxt[i],NULL);

        for (int c = 0; c < N; c++) rexPtr[c+1] = rexPtr[c] + rex_card[c];
        rexTotal = rexPtr[N];
        rexAdj = (int*)malloc((size_t)(rexTotal>0?rexTotal:1)*sizeof(int));
        int *cur = (int*)malloc((size_t)(N+1)*sizeof(int));
        memcpy(cur, rexPtr, (size_t)(N+1)*sizeof(int));
        for (int i = 0; i < rx_threads; i++) {
            for (int k = 0; k < rx[i].pair_c.len; k++)
                rexAdj[cur[rx[i].pair_c.data[k]]++] = rx[i].pair_o.data[k];
            IntVec_free(&rx[i].pair_c); IntVec_free(&rx[i].pair_o);
        }
        free(cur); free(rx); free(rxt); free(rex_card);
    }
    PH_END(_ph, "10. extents reduits");
    prof_log("     (%d entrees au total, soit %.2f Mo au lieu de %.1f Mo en bitmap)\n",
             rexTotal, (double)rexTotal*4.0/1048576.0,
             (double)N*(double)W*8.0/1048576.0);

    PH(_ph);
    free(childrenPtr); free(childrenAdj);
    wmap_free(wmap); free(all_words); free(all_card); free(all_intents);
    PH_END(_ph, "11. liberations");

    CsrLattice *L = (CsrLattice*)malloc(sizeof(CsrLattice));
    L->N = N; L->W = W; L->E = E;
    L->nb_objects = ctx->nb_objects; L->nb_attributes = ctx->nb_attributes;
    L->edge_pairs = edge_pairs;
    L->rexPtr = rexPtr; L->rexAdj = rexAdj;
    L->rintPtr = rintPtr; L->rintAdj = rintAdj;

    prof_log("  ---------------------------------------------\n");
    {
        double tot_ms = _ms_now() - t_all;
        prof_log("  TOTAL noyau CSR          %9.2f ms   (enum %.1f%%, covers %.1f%%)\n",
                 tot_ms - g_report_ms,
                 100.0 * t_enum / tot_ms, 100.0 * t_cov / tot_ms);
        prof_log("  (dont %.2f ms de code de rapport, exclus du total ci-dessus)\n",
                 g_report_ms);
    }
    return L;
}

/* Même format que co_to_flat_array.
 * Le calcul de la longueur ne parcourt plus N*W mots par popcount : les
 * cardinalités sont déjà portées par rexPtr / rintPtr. Les arêtes sont
 * entrelacées en amont, donc recopiables d'un seul memcpy. */
static int *csr_to_flat_array(CsrLattice *L, int *out_len) {
    int N = L->N, E = L->E;
    long total = 2 + 2L*E + 2L*N + (long)L->rexPtr[N] + (long)L->rintPtr[N];
    int *buf = (int*)malloc((size_t)total*sizeof(int));
    if (!buf) { *out_len = 0; return NULL; }
    long p = 0;
    buf[p++] = N; buf[p++] = E;
    if (E > 0) { memcpy(buf + p, L->edge_pairs, (size_t)(2*E)*sizeof(int)); p += 2L*E; }
    for (int c = 0; c < N; c++) {
        int s = L->rexPtr[c], e = L->rexPtr[c+1];
        buf[p++] = e - s;
        for (int k = s; k < e; k++) buf[p++] = L->rexAdj[k];
        int s2 = L->rintPtr[c], e2 = L->rintPtr[c+1];
        buf[p++] = e2 - s2;
        for (int k = s2; k < e2; k++) buf[p++] = L->rintAdj[k];
    }
    *out_len = (int)p;
    return buf;
}


/* ── Points d'entrée ─────────────────────────────────────────────────────── */
char *run_latticecbo_impl(BinaryContext *ctx) {
    prof_init();
    ConceptOrder *co = build_lattice_cbo(ctx);
    char *json = co_to_json(co);
    co_free(co);
    return json;
}

int *run_latticecbo_flat(BinaryContext *ctx, int *out_len) {
    PH_DECL();
    prof_init();
    ConceptOrder *co = build_lattice_cbo(ctx);
    PH(_ph);
    int *flat = co_to_flat_array(co, out_len);
    PH_END(_ph, "8. co_to_flat_array");
    co_free(co);
    return flat;
}

int *run_latticecbo_csr_flat(BinaryContext *ctx, int *out_len) {
    CsrLattice *L = build_lattice_cbo_csr(ctx);   /* prof_init() y est appelé */
    double t = _ms_now();
    int *flat = csr_to_flat_array(L, out_len);
    prof_log("  12. csr_to_flat_array    %9.2f ms  -> %d entiers (%.1f Mo, sequentiel)\n",
             _ms_now() - t, *out_len, (double)(*out_len) * 4.0 / 1048576.0);
    prof_log("=== [latticecbo/CSR] fin ===\n\n");
    csr_free(L);
    return flat;
}
