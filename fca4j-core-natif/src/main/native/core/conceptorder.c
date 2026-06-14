#include "conceptorder.h"
#include "strbuf.h"

/* ── DGraph ── */

DGraph *dgraph_create(int cap) {
    DGraph *g = (DGraph*)calloc(1, sizeof(DGraph));
    g->capacity = cap;
    g->children = (roaring_bitmap_t**)calloc(cap, sizeof(roaring_bitmap_t*));
    g->parents  = (roaring_bitmap_t**)calloc(cap, sizeof(roaring_bitmap_t*));
    g->vertices = roaring_bitmap_create();
    return g;
}

static void dgraph_ensure_cap(DGraph *g, int needed) {
    if (needed <= g->capacity) return;
    int newcap = g->capacity * 2;
    if (newcap < needed) newcap = needed;
    g->children = (roaring_bitmap_t**)realloc(g->children, newcap * sizeof(roaring_bitmap_t*));
    g->parents  = (roaring_bitmap_t**)realloc(g->parents,  newcap * sizeof(roaring_bitmap_t*));
    for (int i = g->capacity; i < newcap; i++) { g->children[i] = NULL; g->parents[i] = NULL; }
    g->capacity = newcap;
}

void dgraph_add_vertex(DGraph *g, int v) {
    dgraph_ensure_cap(g, v + 1);
    if (!roaring_bitmap_contains(g->vertices, (uint32_t)v)) {
        roaring_bitmap_add(g->vertices, (uint32_t)v);
        g->children[v] = roaring_bitmap_create();
        g->parents[v]  = roaring_bitmap_create();
    }
}

void dgraph_add_edge(DGraph *g, int child, int parent) {
    roaring_bitmap_add(g->parents[child],  (uint32_t)parent);
    roaring_bitmap_add(g->children[parent], (uint32_t)child);
}

void dgraph_remove_edge(DGraph *g, int child, int parent) {
    roaring_bitmap_remove(g->parents[child],   (uint32_t)parent);
    roaring_bitmap_remove(g->children[parent], (uint32_t)child);
}

void dgraph_free(DGraph *g) {
    roaring_uint32_iterator_t it;
    roaring_iterator_init(g->vertices, &it);
    while (it.has_value) {
        int v = (int)it.current_value;
        roaring_bitmap_free(g->children[v]);
        roaring_bitmap_free(g->parents[v]);
        roaring_uint32_iterator_advance(&it);
    }
    free(g->children); free(g->parents);
    roaring_bitmap_free(g->vertices);
    free(g);
}

/* ── ConceptOrder ── */

ConceptOrder *co_create(BinaryContext *ctx) {
    int cap = 256;
    ConceptOrder *co = (ConceptOrder*)calloc(1, sizeof(ConceptOrder));
    co->counter = 0; co->ctx = ctx;
    co->graph   = dgraph_create(cap);
    co->capacity = cap;
    co->extents  = (roaring_bitmap_t**)calloc(cap, sizeof(roaring_bitmap_t*));
    co->intents  = (roaring_bitmap_t**)calloc(cap, sizeof(roaring_bitmap_t*));
    co->rextents = (roaring_bitmap_t**)calloc(cap, sizeof(roaring_bitmap_t*));
    co->rintents = (roaring_bitmap_t**)calloc(cap, sizeof(roaring_bitmap_t*));
    co->maximals = roaring_bitmap_create();
    co->minimals = roaring_bitmap_create();
    return co;
}

static void co_ensure_cap(ConceptOrder *co, int needed) {
    if (needed <= co->capacity) return;
    int newcap = co->capacity * 2;
    if (newcap < needed) newcap = needed;
    co->extents  = (roaring_bitmap_t**)realloc(co->extents,  newcap * sizeof(roaring_bitmap_t*));
    co->intents  = (roaring_bitmap_t**)realloc(co->intents,  newcap * sizeof(roaring_bitmap_t*));
    co->rextents = (roaring_bitmap_t**)realloc(co->rextents, newcap * sizeof(roaring_bitmap_t*));
    co->rintents = (roaring_bitmap_t**)realloc(co->rintents, newcap * sizeof(roaring_bitmap_t*));
    for (int i = co->capacity; i < newcap; i++)
        co->extents[i] = co->intents[i] = co->rextents[i] = co->rintents[i] = NULL;
    co->capacity = newcap;
}

int co_add_concept(ConceptOrder *co, roaring_bitmap_t *extent, roaring_bitmap_t *intent) {
    int n = co->counter++;
    co_ensure_cap(co, n + 1);
    dgraph_add_vertex(co->graph, n);
    co->extents[n]  = extent;
    co->intents[n]  = intent;
    co->rextents[n] = roaring_bitmap_create();
    co->rintents[n] = roaring_bitmap_create();
    roaring_bitmap_add(co->maximals, (uint32_t)n);
    roaring_bitmap_add(co->minimals, (uint32_t)n);
    return n;
}

void co_add_edge(ConceptOrder *co, int lower, int upper) {
    dgraph_add_edge(co->graph, lower, upper);
    roaring_bitmap_remove(co->maximals, (uint32_t)lower);
    roaring_bitmap_remove(co->minimals, (uint32_t)upper);
}

void co_remove_edge(ConceptOrder *co, int lower, int upper) {
    dgraph_remove_edge(co->graph, lower, upper);
    /* Restaurer maximals/minimals si le nœud n'a plus de voisins de ce côté */
    if (roaring_bitmap_is_empty(co->graph->parents[lower]))
        roaring_bitmap_add(co->maximals, (uint32_t)lower);
    if (roaring_bitmap_is_empty(co->graph->children[upper]))
        roaring_bitmap_add(co->minimals, (uint32_t)upper);
}

/*
 * co_compute_rextents — calcule les reduced extents de tous les concepts.
 * rextent(c) = extent(c) − union(extent(enfant)) pour chaque enfant direct.
 * À appeler après que tous les concepts et arêtes ont été insérés.
 */
void co_compute_rextents(ConceptOrder *co) {
    for (int c = 0; c < co->counter; c++) {
        /* Partir d'une copie de l'extent complet */
        roaring_bitmap_t *re = roaring_bitmap_copy(co->extents[c]);
        /* Soustraire les extents des enfants directs */
        roaring_uint32_iterator_t it;
        roaring_iterator_init(co->graph->children[c], &it);
        while (it.has_value) {
            int child = (int)it.current_value;
            roaring_bitmap_andnot_inplace(re, co->extents[child]);
            roaring_uint32_iterator_advance(&it);
        }
        /* Remplacer le rextent existant (créé vide par co_add_concept) */
        roaring_bitmap_free(co->rextents[c]);
        co->rextents[c] = re;
    }
}

/*
 * co_compute_intents — propage les intents top-down depuis les reduced intents.
 * intent(c) = rintent(c) ∪ intent(parent) pour chaque parent direct.
 * Parcours topologique : on visite les maximals en premier (ordre décroissant
 * par extent — approx. suffisante ici via tri sur counter en ordre inverse,
 * car AddExtent insère les concepts de haut en bas).
 *
 * Implémentation : parcours BFS depuis les maximals (comme computeIntents() Java).
 */
void co_compute_intents(ConceptOrder *co) {
    int n = co->counter;
    if (n == 0) return;

    /* Calculer in-degree de chaque nœud (nombre de parents) */
    int *indegree = (int*)calloc(n, sizeof(int));
    for (int c = 0; c < n; c++)
        indegree[c] = (int)roaring_bitmap_get_cardinality(co->graph->parents[c]);

    /* File BFS initialisée avec les maximals (indegree == 0 = pas de parents) */
    int *queue = (int*)malloc(n * sizeof(int));
    int head = 0, tail = 0;
    for (int c = 0; c < n; c++)
        if (indegree[c] == 0)
            queue[tail++] = c;

    while (head < tail) {
        int c = queue[head++];
        /* intent(c) reçoit son propre rintent */
        roaring_bitmap_or_inplace(co->intents[c], co->rintents[c]);
        /* Propager intent(c) à chaque enfant direct */
        roaring_uint32_iterator_t it;
        roaring_iterator_init(co->graph->children[c], &it);
        while (it.has_value) {
            int child = (int)it.current_value;
            roaring_bitmap_or_inplace(co->intents[child], co->intents[c]);
            indegree[child]--;
            if (indegree[child] == 0)
                queue[tail++] = child;
            roaring_uint32_iterator_advance(&it);
        }
    }
    free(queue);
    free(indegree);
}

void co_free(ConceptOrder *co) {
    for (int i = 0; i < co->counter; i++) {
        if (co->extents[i])  roaring_bitmap_free(co->extents[i]);
        if (co->intents[i])  roaring_bitmap_free(co->intents[i]);
        if (co->rextents[i]) roaring_bitmap_free(co->rextents[i]);
        if (co->rintents[i]) roaring_bitmap_free(co->rintents[i]);
    }
    free(co->extents); free(co->intents);
    free(co->rextents); free(co->rintents);
    roaring_bitmap_free(co->maximals);
    roaring_bitmap_free(co->minimals);
    dgraph_free(co->graph);
    free(co);
}

char *co_to_json(ConceptOrder *co) {
    BinaryContext *ctx = co->ctx;
    StrBuf sb = sb_new();
    sb_printf(&sb, "{\"concepts\":%d,\"objects\":%d,\"attributes\":%d,",
              co->counter, ctx->nb_objects, ctx->nb_attributes);
    int edge_count = 0;
    for (int c = 0; c < co->counter; c++)
        edge_count += (int)roaring_bitmap_get_cardinality(co->graph->parents[c]);
    sb_printf(&sb, "\"edge_count\":%d,\"lattice\":[", edge_count);
    for (int c = 0; c < co->counter; c++) {
        if (c > 0) sb_append(&sb, ",");
        sb_printf(&sb, "{\"id\":%d,\"rintent\":[", c);
        roaring_uint32_iterator_t it;
        roaring_iterator_init(co->rintents[c], &it);
        int first = 1;
        while (it.has_value) {
            if (!first) sb_append(&sb, ",");
            int a = (int)it.current_value;
            if (a < ctx->attr_names.len) sb_append_json_str(&sb, ctx->attr_names.data[a]);
            else sb_printf(&sb, "\"%d\"", a);
            first = 0; roaring_uint32_iterator_advance(&it);
        }
        sb_append(&sb, "],\"rextent\":[");
        roaring_iterator_init(co->rextents[c], &it); first = 1;
        while (it.has_value) {
            if (!first) sb_append(&sb, ",");
            int o = (int)it.current_value;
            if (o < ctx->obj_names.len) sb_append_json_str(&sb, ctx->obj_names.data[o]);
            else sb_printf(&sb, "\"%d\"", o);
            first = 0; roaring_uint32_iterator_advance(&it);
        }
        sb_printf(&sb, "],\"ext_card\":%d,\"int_card\":%d,\"children\":[",
                  (int)roaring_bitmap_get_cardinality(co->extents[c]),
                  (int)roaring_bitmap_get_cardinality(co->intents[c]));
        roaring_iterator_init(co->graph->children[c], &it); first = 1;
        while (it.has_value) {
            if (!first) sb_append(&sb, ",");
            sb_printf(&sb, "%d", (int)it.current_value);
            first = 0; roaring_uint32_iterator_advance(&it);
        }
        sb_append(&sb, "]}");
    }
    sb_append(&sb, "]}");
    return sb.buf;
}

/*
 * co_to_flat_array — sérialise le ConceptOrder en un tableau d'entiers plat,
 * auto-descriptif, destiné à être transmis tel quel à Java via JNI (zéro JSON,
 * zéro résolution de noms).
 *
 * Format (tous les éléments sont des int32) :
 *   [0]                 N      = nombre de concepts
 *   [1]                 E      = nombre d'arêtes (paires child→parent)
 *   [2 .. 2+2E-1]       edges  = E paires (child, parent) à plat
 *   ensuite, pour chaque concept c de 0..N-1, dans l'ordre des ids :
 *       [card_rextent, o0, o1, ...]    (1 + card entiers, indices d'objets)
 *       [card_rintent, a0, a1, ...]    (1 + card entiers, indices d'attributs)
 *
 * Les ids de concepts sont implicitement 0..N-1 (co_add_concept les attribue
 * séquentiellement), donc on ne les transmet pas.
 *
 * Retourne un buffer alloué (à libérer par l'appelant via free) et écrit la
 * longueur totale (nombre d'int) dans *out_len.
 */
int *co_to_flat_array(ConceptOrder *co, int *out_len) {
    int N = co->counter;

    /* 1) Compter les arêtes et la taille totale nécessaire */
    int E = 0;
    for (int c = 0; c < N; c++)
        E += (int)roaring_bitmap_get_cardinality(co->graph->parents[c]);

    long total = 2;            /* header N, E */
    total += (long)2 * E;      /* edges */
    for (int c = 0; c < N; c++) {
        total += 1 + (long)roaring_bitmap_get_cardinality(co->rextents[c]);
        total += 1 + (long)roaring_bitmap_get_cardinality(co->rintents[c]);
    }

    int *buf = (int*)malloc((size_t)total * sizeof(int));
    if (!buf) { *out_len = 0; return NULL; }

    long p = 0;
    buf[p++] = N;
    buf[p++] = E;

    /* 2) Arêtes : on parcourt les parents de chaque concept → (child=c, parent) */
    for (int c = 0; c < N; c++) {
        roaring_uint32_iterator_t it;
        roaring_iterator_init(co->graph->parents[c], &it);
        while (it.has_value) {
            buf[p++] = c;                       /* child  */
            buf[p++] = (int)it.current_value;   /* parent */
            roaring_uint32_iterator_advance(&it);
        }
    }

    /* 3) Pour chaque concept : rextent puis rintent (card + éléments) */
    for (int c = 0; c < N; c++) {
        /* rextent */
        int card_re = (int)roaring_bitmap_get_cardinality(co->rextents[c]);
        buf[p++] = card_re;
        roaring_uint32_iterator_t it;
        roaring_iterator_init(co->rextents[c], &it);
        while (it.has_value) {
            buf[p++] = (int)it.current_value;
            roaring_uint32_iterator_advance(&it);
        }
        /* rintent */
        int card_ri = (int)roaring_bitmap_get_cardinality(co->rintents[c]);
        buf[p++] = card_ri;
        roaring_iterator_init(co->rintents[c], &it);
        while (it.has_value) {
            buf[p++] = (int)it.current_value;
            roaring_uint32_iterator_advance(&it);
        }
    }

    *out_len = (int)p;   /* == total */
    return buf;
}
