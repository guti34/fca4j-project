/*
 * latticecbo.c — Lattice_ParallelCbO : treillis complet par Close-by-One
 * sur les extents (contexte brut, séquentiel).
 *
 * Énumération : depuis l'extent top (tous les objets), on étend E par E ∩ col_i
 * pour i hors de l'intent de E, validé par canonicité (aucun k<i, k hors intent,
 * avec E_i ⊆ col_k). L'intersection d'extents fermés étant fermée, pas de
 * clôture ; la canonicité garantit l'unicité. Les colonnes dupliquées sont
 * gérées par la canonicité (le plus petit indice gagne).
 *
 * Couvertures inférieures = A ∩ col_m maximaux propres, m parcourant les
 * attributs MEET-IRRÉDUCTIBLES (précalculés). L'id du concept n'est résolu (via
 * la table extent->id) que pour les couvertures retenues.
 *
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 */

#include <stdio.h>
#include <stdlib.h>
#include "latticecbo.h"
#include "../core/conceptorder.h"
#include "../core/closure.h"
#include "../core/fca4j_common.h"

extern int croaring_hardware_support(void);

/* ── Timing portable, activé par -DFCA4J_TIMING ──────────────────────────── */
#ifdef FCA4J_TIMING
  #ifdef _WIN32
    #include <windows.h>
    static double _ms_now(void) {
        LARGE_INTEGER f, c;
        QueryPerformanceFrequency(&f); QueryPerformanceCounter(&c);
        return (double)c.QuadPart * 1000.0 / (double)f.QuadPart;
    }
  #else
    #include <time.h>
    static double _ms_now(void) {
        struct timespec t; clock_gettime(CLOCK_MONOTONIC, &t);
        return t.tv_sec * 1000.0 + t.tv_nsec / 1e6;
    }
  #endif
  #define T_DECL() double _t0 = 0.0
  #define T_START() (_t0 = _ms_now())
  #define T_END(label) \
        fprintf(stderr, "[latticecbo] %-18s : %8.2f ms\n", label, _ms_now() - _t0)
  #define T_SIMD_REPORT() \
        fprintf(stderr, "[latticecbo] croaring SIMD   : %d (0=scalaire 1=AVX2 3=AVX512)\n", \
                croaring_hardware_support())
#else
  #define T_DECL()
  #define T_START()
  #define T_END(label)
  #define T_SIMD_REPORT()
#endif

/* ── Table de hachage extent (roaring) -> id de concept ──────────────────────
 * Clés EMPRUNTÉES (possédées par le ConceptOrder). Hash fort mémorisé.
 * ─────────────────────────────────────────────────────────────────────────── */
typedef struct CMapNode {
    roaring_bitmap_t *key;
    uint64_t          hash;
    int               id;
    struct CMapNode  *next;
} CMapNode;

typedef struct {
    CMapNode **buckets;
    int nbuckets;
    int size;
} CMap;

/* FNV-1a sur tous les éléments : distribue correctement les extents. */
static uint64_t bm_hash(roaring_bitmap_t *bm) {
    uint64_t h = 1469598103934665603ULL;
    roaring_uint32_iterator_t it;
    roaring_iterator_init(bm, &it);
    while (it.has_value) {
        h = (h ^ (uint64_t)it.current_value) * 1099511628211ULL;
        roaring_uint32_iterator_advance(&it);
    }
    return h;
}

static CMap *cmap_create(int expected) {
    int nb = 1024;
    while (nb < expected) nb <<= 1;
    CMap *m = (CMap*)malloc(sizeof(CMap));
    m->nbuckets = nb; m->size = 0;
    m->buckets = (CMapNode**)calloc(nb, sizeof(CMapNode*));
    return m;
}

static void cmap_resize(CMap *m) {
    int newnb = m->nbuckets * 2;
    CMapNode **nb = (CMapNode**)calloc(newnb, sizeof(CMapNode*));
    for (int b = 0; b < m->nbuckets; b++) {
        CMapNode *n = m->buckets[b];
        while (n) {
            CMapNode *nx = n->next;
            int idx = (int)(n->hash & (uint64_t)(newnb - 1));   /* hash réutilisé */
            n->next = nb[idx]; nb[idx] = n;
            n = nx;
        }
    }
    free(m->buckets);
    m->buckets = nb; m->nbuckets = newnb;
}

static void cmap_put(CMap *m, roaring_bitmap_t *key, int id) {
    if (m->size > m->nbuckets * 2) cmap_resize(m);
    uint64_t h = bm_hash(key);
    int b = (int)(h & (uint64_t)(m->nbuckets - 1));
    CMapNode *n = (CMapNode*)malloc(sizeof(CMapNode));
    n->key = key; n->hash = h; n->id = id; n->next = m->buckets[b];
    m->buckets[b] = n; m->size++;
}

static int cmap_get(CMap *m, roaring_bitmap_t *key) {
    uint64_t h = bm_hash(key);
    int b = (int)(h & (uint64_t)(m->nbuckets - 1));
    for (CMapNode *n = m->buckets[b]; n; n = n->next)
        if (n->hash == h && roaring_bitmap_equals(n->key, key)) return n->id;
    return -1;
}

static void cmap_free(CMap *m) {
    for (int b = 0; b < m->nbuckets; b++) {
        CMapNode *n = m->buckets[b];
        while (n) { CMapNode *nx = n->next; free(n); n = nx; }
    }
    free(m->buckets); free(m);
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

/* ── Énumération Close-by-One sur les extents ───────────────────────────────
 * E,B = extent et intent complet (empruntés, possédés par co). y = dernier
 * attribut ajouté. compute_intent n'est appelé que pour les concepts canoniques.
 * ─────────────────────────────────────────────────────────────────────────── */
 /* Exploration récursive d'un sous-arbre, stockage LOCAL (pas de co/map). */
 static void cbo_enum_local(BinaryContext *ctx, BitmapVec *exts, BitmapVec *ints,
                            roaring_bitmap_t *E, roaring_bitmap_t *B, int y) {
     int m = ctx->nb_attributes;
     for (int i = y + 1; i < m; i++) {
         if (roaring_bitmap_contains(B, (uint32_t)i)) continue;
         roaring_bitmap_t *Ei = roaring_bitmap_and(E, ctx->cols[i]);
         int canonical = 1;
         for (int k = 0; k < i; k++) {
             if (roaring_bitmap_contains(B, (uint32_t)k)) continue;
             if (roaring_bitmap_is_subset(Ei, ctx->cols[k])) { canonical = 0; break; }
         }
         if (canonical) {
             roaring_bitmap_t *Bi = compute_intent(ctx, Ei);
             BitmapVec_push(exts, Ei);
             BitmapVec_push(ints, Bi);
             cbo_enum_local(ctx, exts, ints, Ei, Bi, i);
         } else {
             roaring_bitmap_free(Ei);
         }
     }
 }

 typedef struct {
     BinaryContext *ctx;
     roaring_bitmap_t *E0, *B0;   /* extent/intent du top (lecture seule) */
     int m;
     int *next_i;                 /* compteur partagé sur les attributs de 1er niveau */
     pthread_mutex_t *lock;
     BitmapVec exts, ints;        /* concepts produits par ce thread (locaux) */
 } EnumTask;

 static void *enum_thread(void *arg) {
     EnumTask *t = (EnumTask*)arg;
     BinaryContext *ctx = t->ctx;
     for (;;) {
         pthread_mutex_lock(t->lock);
         int i = (*t->next_i)++;
         pthread_mutex_unlock(t->lock);
         if (i >= t->m) break;
         if (roaring_bitmap_contains(t->B0, (uint32_t)i)) continue;

         roaring_bitmap_t *Ei = roaring_bitmap_and(t->E0, ctx->cols[i]);
         int canonical = 1;
         for (int k = 0; k < i; k++) {
             if (roaring_bitmap_contains(t->B0, (uint32_t)k)) continue;
             if (roaring_bitmap_is_subset(Ei, ctx->cols[k])) { canonical = 0; break; }
         }
         if (canonical) {
             roaring_bitmap_t *Bi = compute_intent(ctx, Ei);
             BitmapVec_push(&t->exts, Ei);
             BitmapVec_push(&t->ints, Bi);
             cbo_enum_local(ctx, &t->exts, &t->ints, Ei, Bi, i);
         } else {
             roaring_bitmap_free(Ei);
         }
     }
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

 typedef struct {
     ConceptOrder *co; CMap *map; BinaryContext *ctx;
     const int *irr; int nIrr;
     int c_start, c_end;
     IntVec child, parent;   /* arêtes (enfant, parent) produites par ce thread */
 } CoverTask;

 /* Couvertures pour la tranche [c_start, c_end). Lecture seule sur co et map. */
 static void cover_range(CoverTask *t) {
     ConceptOrder *co = t->co; CMap *map = t->map; BinaryContext *ctx = t->ctx;
     int nIrr = t->nIrr; const int *irr = t->irr;
     int cap = nIrr ? nIrr : 1;

     roaring_bitmap_t **cand = (roaring_bitmap_t**)malloc((size_t)cap * sizeof(*cand));
     int *cand_card = (int*)malloc((size_t)cap * sizeof(int));
     int *order     = (int*)malloc((size_t)cap * sizeof(int));   /* indices triés par card décroissante */
     int *conf      = (int*)malloc((size_t)cap * sizeof(int));   /* indices maximaux confirmés */

     for (int c = t->c_start; c < t->c_end; c++) {
         roaring_bitmap_t *A = co->extents[c];
         roaring_bitmap_t *B = co->intents[c];

         int kc = 0;
         for (int s = 0; s < nIrr; s++) {
             int mm = irr[s];
             if (roaring_bitmap_contains(B, (uint32_t)mm)) continue;
             cand[kc] = roaring_bitmap_and(A, ctx->cols[mm]);
             cand_card[kc] = (int)roaring_bitmap_get_cardinality(cand[kc]);
             order[kc] = kc;
             kc++;
         }

         /* tri insertion des indices par cardinalité décroissante (kc petit) */
         for (int a = 1; a < kc; a++) {
             int v = order[a], cv = cand_card[v], b = a - 1;
             while (b >= 0 && cand_card[order[b]] < cv) { order[b+1] = order[b]; b--; }
             order[b+1] = v;
         }

         /* maximal ssi non dominé par un maximal déjà confirmé (de card >=) */
         int nconf = 0;
         for (int a = 0; a < kc; a++) {
             int xi = order[a];
             int dominated = 0;
             for (int j = 0; j < nconf; j++) {
                 int cj = conf[j];
                 if (cand_card[cj] == cand_card[xi]) {
                     if (roaring_bitmap_equals(cand[cj], cand[xi])) { dominated = 1; break; } /* doublon */
                 } else { /* cand_card[cj] > cand_card[xi] */
                     if (roaring_bitmap_is_subset(cand[xi], cand[cj])) { dominated = 1; break; }
                 }
             }
             if (!dominated) {
                 conf[nconf++] = xi;
                 int id = cmap_get(map, cand[xi]);
                 if (id >= 0 && id != c) {
                     IntVec_push(&t->child, id);
                     IntVec_push(&t->parent, c);
                 }
             }
         }
         for (int x = 0; x < kc; x++) roaring_bitmap_free(cand[x]);
     }

     free(cand); free(cand_card); free(order); free(conf);
 }

 static void *cover_thread(void *arg) { cover_range((CoverTask*)arg); return NULL; }

 /* ── build_lattice_cbo ──────────────────────────────────────────────────────
 * Pas de co_compute_intents : ConceptOrder.populate() reconstruit les sets
 * complets côté Java. Les intents complets stockés ne servent qu'en interne.
 * ─────────────────────────────────────────────────────────────────────────── */
static ConceptOrder *build_lattice_cbo(BinaryContext *ctx) {
    T_DECL();
    T_SIMD_REPORT();
    ConceptOrder *co = co_create(ctx);
    CMap *map = cmap_create(1 << 16);

    /* Concept top : extent = tous les objets, intent = attributs communs */
    roaring_bitmap_t *allObjects = roaring_bitmap_create();
    if (ctx->nb_objects > 0)
        roaring_bitmap_add_range(allObjects, 0, (uint32_t)ctx->nb_objects);
    roaring_bitmap_t *topIntent = compute_intent(ctx, allObjects);
    int top = co_add_concept(co, allObjects, topIntent);
    cmap_put(map, allObjects, top);

	/* 1) Énumération CbO — parallèle (sous-arbres de premier niveau, dynamique) */
	    T_START();
	    int en_threads = detect_nthreads();
	    if (en_threads < 1) en_threads = 1;
	    if (ctx->nb_attributes > 0 && en_threads > ctx->nb_attributes)
	        en_threads = ctx->nb_attributes;
	    if (en_threads < 1) en_threads = 1;

	    int next_i = 0;
	    pthread_mutex_t emutex;
	    pthread_mutex_init(&emutex, NULL);
	    EnumTask *etasks = (EnumTask*)malloc((size_t)en_threads * sizeof(EnumTask));
	    pthread_t *etids = (pthread_t*)malloc((size_t)en_threads * sizeof(pthread_t));
	    for (int i = 0; i < en_threads; i++) {
	        etasks[i].ctx = ctx;
	        etasks[i].E0 = allObjects;
	        etasks[i].B0 = topIntent;
	        etasks[i].m = ctx->nb_attributes;
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
	    T_END("cbo enum (par)");

	    /* fusion séquentielle : IDs globaux + table extent->id */
	    T_START();
	    for (int i = 0; i < en_threads; i++) {
	        for (int j = 0; j < etasks[i].exts.len; j++) {
	            roaring_bitmap_t *E = etasks[i].exts.data[j];
	            roaring_bitmap_t *B = etasks[i].ints.data[j];
	            int id = co_add_concept(co, E, B);   /* co prend possession de E et B */
	            cmap_put(map, E, id);
	        }
	        BitmapVec_free(&etasks[i].exts);   /* libère le tableau, pas les bitmaps */
	        BitmapVec_free(&etasks[i].ints);
	    }
	    free(etasks); free(etids);
	    T_END("merge concepts");
	#ifdef FCA4J_TIMING
	    fprintf(stderr, "[latticecbo] concepts=%d  enum threads=%d\n", co->counter, en_threads);
	#endif
    /* 2) Intents réduits : pour chaque attribut a, concept dont l'extent == col_a */
    T_START();
    for (int a = 0; a < ctx->nb_attributes; a++) {
        int id = cmap_get(map, ctx->cols[a]);
        if (id >= 0) roaring_bitmap_add(co->rintents[id], (uint32_t)a);
    }
    T_END("reduced intents");

    /* 3) Attributs meet-irréductibles (une fois) */
    T_START();
    int nIrr = 0;
    int *irr = compute_irreducibles(ctx, &nIrr);
    T_END("irreducibles");
#ifdef FCA4J_TIMING
    fprintf(stderr, "[latticecbo] irreducibles=%d / %d\n", nIrr, ctx->nb_attributes);
#endif

/* 4) Couvertures inférieures directes (Hasse) — parallèle par concept */
    T_START();
    int nthreads = detect_nthreads();
    if (nthreads < 1) nthreads = 1;
    if (co->counter > 0 && nthreads > co->counter) nthreads = co->counter;

    CoverTask *tasks = (CoverTask*)malloc((size_t)nthreads * sizeof(CoverTask));
    pthread_t *tids  = (pthread_t*)malloc((size_t)nthreads * sizeof(pthread_t));
    int chunk = (co->counter + nthreads - 1) / nthreads;
    for (int i = 0; i < nthreads; i++) {
        tasks[i].co = co; tasks[i].map = map; tasks[i].ctx = ctx;
        tasks[i].irr = irr; tasks[i].nIrr = nIrr;
        tasks[i].c_start = i * chunk;
        tasks[i].c_end   = (i + 1) * chunk;
        if (tasks[i].c_end > co->counter) tasks[i].c_end = co->counter;
        if (tasks[i].c_start > co->counter) tasks[i].c_start = co->counter;
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
    free(tasks); free(tids); free(irr);
    T_END("covers");
#ifdef FCA4J_TIMING
    fprintf(stderr, "[latticecbo] cover threads   : %d\n", nthreads);
#endif
		
    /* 5) Extents réduits */
    T_START();
    co_compute_rextents(co);
    T_END("compute rextents");

    cmap_free(map);
    return co;
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