/*
 * dbasis.c — Pipeline DBase complet (mono ou multi-thread)
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 */
#include "dbasis.h"
#include "../core/fca4j_common.h"
#include "../core/closure.h"
#include "../core/clarification.h"
#include "../core/graph.h"
#include "../core/tarjan.h"
#include "../core/strbuf.h"
#include <time.h>
#include <stdarg.h>

/* ── Détection portable du nombre de cœurs ───────────────────────────── */
#ifdef _WIN32
#include <windows.h>
static int get_cpu_count(void) {
    SYSTEM_INFO si;
    GetSystemInfo(&si);
    return (int)si.dwNumberOfProcessors;
}
#else
#include <unistd.h>
static int get_cpu_count(void) {
    int c = (int)sysconf(_SC_NPROCESSORS_ONLN);
    return c > 0 ? c : 4;
}
#endif

#ifndef __EMSCRIPTEN__
#include <pthread.h>
#endif

/* ── Structures internes ─────────────────────────────────────────────── */

typedef struct { int *attrs; int size; } Edge;

static int cmp_edge_size(const void *a, const void *b) {
    return ((Edge*)a)->size - ((Edge*)b)->size;
}
typedef struct { int *arr; int size; } GenResult;
static int cmp_gen_result(const void *a, const void *b) {
    const GenResult *x = (const GenResult*)a;
    const GenResult *y = (const GenResult*)b;
    if (x->size != y->size) return x->size - y->size;
    for (int i = 0; i < x->size; i++)
        if (x->arr[i] != y->arr[i]) return x->arr[i] - y->arr[i];
    return 0;
}
typedef struct {
    int nedges, max_attr;
    int  *edge_hit;
    int  *attr_edge_offset, *attr_edge_data;
    int  *edge_attr_offset, *edge_attr_data;
    bool *in_current;
	int  *current_list;       /* pile des attributs du transversal courant */
    int   current_size;
    int **results; int *result_sizes; int result_count, result_cap;
} GenState;

static void gs_add_result(GenState *gs) {
    if (gs->result_count == gs->result_cap) {
        gs->result_cap *= 2;
        gs->results      = (int**)realloc(gs->results,      gs->result_cap * sizeof(int*));
        gs->result_sizes = (int*) realloc(gs->result_sizes, gs->result_cap * sizeof(int));
    }
    int *arr = (int*)malloc(gs->current_size * sizeof(int));
    int k = 0;
    for (int a = 0; a < gs->max_attr; a++)
        if (gs->in_current[a]) arr[k++] = a;
    gs->results[gs->result_count]      = arr;
    gs->result_sizes[gs->result_count] = gs->current_size;
    gs->result_count++;
}

static bool is_already_covered_fast(GenState *gs) {
    for (int idx = 0; idx < gs->current_size; idx++) {
        int attr = gs->current_list[idx];
        bool covers_all = true;
        int start = gs->attr_edge_offset[attr], end = gs->attr_edge_offset[attr+1];
        for (int i = start; i < end; i++)
            if (gs->edge_hit[gs->attr_edge_data[i]] <= 1) { covers_all = false; break; }
        if (covers_all) return true;
    }
    return false;
}

static inline void gen_add_attr(GenState *gs, int attr) {
    gs->in_current[attr] = true;
    gs->current_list[gs->current_size++] = attr;
    int start = gs->attr_edge_offset[attr], end = gs->attr_edge_offset[attr+1];
    for (int i = start; i < end; i++) gs->edge_hit[gs->attr_edge_data[i]]++;
}

static inline void gen_remove_attr(GenState *gs, int attr) {
    gs->in_current[attr] = false;
    gs->current_size--;       /* LIFO : on dépile attr (sommet) */
    int start = gs->attr_edge_offset[attr], end = gs->attr_edge_offset[attr+1];
    for (int i = start; i < end; i++) gs->edge_hit[gs->attr_edge_data[i]]--;
}
typedef struct { int index, ai, attr; } GenFrame;

static void generate_covers(GenState *gs) {
    GenFrame *stack = (GenFrame*)malloc((gs->nedges + 1) * sizeof(GenFrame));
    int sp = 0;
    stack[0].index = 0; stack[0].ai = -1; stack[0].attr = -1;
    while (sp >= 0) {
        GenFrame *f = &stack[sp];
        if (f->attr >= 0) { gen_remove_attr(gs, f->attr); f->attr = -1; }
        while (f->index < gs->nedges && gs->edge_hit[f->index] > 0) f->index++;
        if (f->index >= gs->nedges) { gs_add_result(gs); sp--; continue; }
        if (f->ai < 0) f->ai = gs->edge_attr_offset[f->index];
        int ea_end = gs->edge_attr_offset[f->index + 1];
        bool pushed = false;
        while (f->ai < ea_end) {
            int attr = gs->edge_attr_data[f->ai++];
            if (gs->in_current[attr]) continue;
            gen_add_attr(gs, attr);
            if (!is_already_covered_fast(gs)) {
                f->attr = attr; sp++;
                stack[sp].index = f->index + 1; stack[sp].ai = -1; stack[sp].attr = -1;
                pushed = true; break;
            }
            gen_remove_attr(gs, attr);
        }
        if (!pushed) sp--;
    }
    free(stack);
}

/* ── Générateurs minimaux ────────────────────────────────────────────── */

static BitmapVec compute_minimal_generators(BinaryContext *ctx, int target,
                                             roaring_bitmap_t *binary_premises,
                                             int min_support) {
    GenState gs;
    gs.max_attr = ctx->nb_attributes;
    gs.result_cap = 64; gs.result_count = 0;
    gs.results      = (int**)malloc(64 * sizeof(int*));
    gs.result_sizes = (int*) malloc(64 * sizeof(int));

    /* Construction hypergraphe */
    Edge *edges = (Edge*)malloc(ctx->nb_objects * sizeof(Edge));
    int nedges = 0; bool trivial = false;

    for (int o = 0; o < ctx->nb_objects && !trivial; o++) {
        if (roaring_bitmap_contains(ctx->cols[target], (uint32_t)o)) continue;
        int *arr = (int*)malloc(ctx->nb_attributes * sizeof(int));
        int sz = 0;
        roaring_bitmap_t *all = roaring_bitmap_create();
        roaring_bitmap_add_range(all, 0, (uint32_t)ctx->nb_attributes);
        roaring_bitmap_andnot_inplace(all, ctx->rows[o]);
        roaring_bitmap_remove(all, (uint32_t)target);
        if (binary_premises) roaring_bitmap_andnot_inplace(all, binary_premises);
        roaring_uint32_iterator_t it;
        roaring_iterator_init(all, &it);
        while (it.has_value) { arr[sz++] = (int)it.current_value; roaring_uint32_iterator_advance(&it); }
        roaring_bitmap_free(all);
        if (sz == 0) { free(arr); trivial = true; }
        else { edges[nedges].attrs = arr; edges[nedges].size = sz; nedges++; }
    }

    BitmapVec result = BitmapVec_new();
    if (trivial || nedges == 0) {
        for (int e = 0; e < nedges; e++) free(edges[e].attrs);
        free(edges); free(gs.results); free(gs.result_sizes);
        return result;
    }

    qsort(edges, nedges, sizeof(Edge), cmp_edge_size); 
    gs.nedges = nedges;

    /* Index inversé CSR */
    int total_ae = 0;
    int *counts = (int*)calloc(ctx->nb_attributes, sizeof(int));
    for (int e = 0; e < nedges; e++)
        for (int i = 0; i < edges[e].size; i++) { counts[edges[e].attrs[i]]++; total_ae++; }
    gs.attr_edge_offset = (int*)malloc((ctx->nb_attributes + 1) * sizeof(int));
    gs.attr_edge_offset[0] = 0;
    for (int a = 0; a < ctx->nb_attributes; a++)
        gs.attr_edge_offset[a+1] = gs.attr_edge_offset[a] + counts[a];
    gs.attr_edge_data = (int*)malloc(total_ae * sizeof(int));
    memset(counts, 0, ctx->nb_attributes * sizeof(int));
    for (int e = 0; e < nedges; e++)
        for (int i = 0; i < edges[e].size; i++) {
            int a = edges[e].attrs[i];
            gs.attr_edge_data[gs.attr_edge_offset[a] + counts[a]++] = e;
        }
    free(counts);

    /* Listes attributs par arête */
    int total_ea = 0;
    for (int e = 0; e < nedges; e++) total_ea += edges[e].size;
    gs.edge_attr_offset = (int*)malloc((nedges + 1) * sizeof(int));
    gs.edge_attr_data   = (int*)malloc(total_ea * sizeof(int));
    gs.edge_attr_offset[0] = 0;
    for (int e = 0; e < nedges; e++) {
        int idx = gs.edge_attr_offset[e];
        for (int i = 0; i < edges[e].size; i++) gs.edge_attr_data[idx++] = edges[e].attrs[i];
        gs.edge_attr_offset[e+1] = idx;
    }

    gs.edge_hit   = (int*) calloc(nedges,            sizeof(int));
    gs.in_current = (bool*)calloc(ctx->nb_attributes, sizeof(bool));
	gs.current_list = (int*) malloc(ctx->nb_attributes * sizeof(int));
    gs.current_size = 0;

	generate_covers(&gs);

	    /* Déduplication des transversaux avant la minimisation O(n²)
	       (équivalent du HashSet<ISet> côté Java). */
	    GenResult *gr = (GenResult*)malloc(gs.result_count * sizeof(GenResult));
	    for (int i = 0; i < gs.result_count; i++) {
	        gr[i].arr  = gs.results[i];
	        gr[i].size = gs.result_sizes[i];
	    }
	    qsort(gr, gs.result_count, sizeof(GenResult), cmp_gen_result);

	    int distinct = 0;
		    for (int i = 0; i < gs.result_count; i++) {
		        if (i > 0 && cmp_gen_result(&gr[i-1], &gr[i]) == 0)
		            continue;                       /* doublon : ignoré, NE PAS libérer ici */
		        roaring_bitmap_t *bm = roaring_bitmap_create();
		        for (int k = 0; k < gr[i].size; k++)
		            roaring_bitmap_add(bm, (uint32_t)gr[i].arr[k]);
		        BitmapVec_push(&result, bm);
		        distinct++;
		    }
		    /* libération différée : chaque arr exactement une fois, après les comparaisons */
		    for (int i = 0; i < gs.result_count; i++)
		        free(gr[i].arr);
		    free(gr);
		    free(gs.results); free(gs.result_sizes);
		free(gs.edge_hit); free(gs.in_current); free(gs.current_list);
    free(gs.attr_edge_offset); free(gs.attr_edge_data);
    free(gs.edge_attr_offset); free(gs.edge_attr_data);
    for (int e = 0; e < nedges; e++) free(edges[e].attrs);
    free(edges);
    return result;
}

/* ── Couvertures minimales ───────────────────────────────────────────── */

static BitmapVec compute_minimal_covers(BitmapVec *covers, roaring_bitmap_t **closures_arr) {
    int n = covers->len;
    bool *removed = (bool*)calloc(n, sizeof(bool));
    for (int i = 0; i < n; i++) {
        if (removed[i]) continue;
        roaring_bitmap_t *g = covers->data[i];
        roaring_bitmap_t *g_union = NULL;
        for (int j = 0; j < n; j++) {
            if (i == j || removed[j]) continue;
            roaring_bitmap_t *h = covers->data[j];
            if (roaring_bitmap_is_subset(h, g)) { removed[i] = true; break; }
            if (roaring_bitmap_get_cardinality(g) > 1) {
                if (!g_union) {
                    g_union = roaring_bitmap_create();
                    roaring_uint32_iterator_t it;
                    roaring_iterator_init(g, &it);
                    while (it.has_value) {
                        roaring_bitmap_or_inplace(g_union, closures_arr[(int)it.current_value]);
                        roaring_uint32_iterator_advance(&it);
                    }
                }
                if (roaring_bitmap_is_subset(h, g_union)) { removed[i] = true; break; }
            }
        }
        if (g_union) roaring_bitmap_free(g_union);
    }
    BitmapVec result = BitmapVec_new();
    for (int i = 0; i < n; i++)
        if (!removed[i]) BitmapVec_push(&result, roaring_bitmap_copy(covers->data[i]));
    free(removed);
    return result;
}

/* ── Base non-binaire pour un attribut ──────────────────────────────── */

static ImplVec compute_non_binary_basis_for_attr(BinaryContext *ctx, int target,
                                                  roaring_bitmap_t *binary_premises,
                                                  roaring_bitmap_t **closures,
                                                  int min_support) {
    ImplVec basis = ImplVec_new();

    BitmapVec min_gens   = compute_minimal_generators(ctx, target, binary_premises, min_support);
    BitmapVec min_covers = compute_minimal_covers(&min_gens, closures);

    for (int i = 0; i < min_covers.len; i++) {
        roaring_bitmap_t *premise = min_covers.data[i];
        if (roaring_bitmap_get_cardinality(premise) > 1) {
            roaring_bitmap_t *concl = roaring_bitmap_create();
            roaring_bitmap_add(concl, (uint32_t)target);
            ImplVec_push(&basis, impl_create(roaring_bitmap_copy(premise), concl, 0));
        }
    }
    for (int i = 0; i < min_gens.len;   i++) roaring_bitmap_free(min_gens.data[i]);
    BitmapVec_free(&min_gens);
    for (int i = 0; i < min_covers.len; i++) roaring_bitmap_free(min_covers.data[i]);
    BitmapVec_free(&min_covers);
    return basis;
}

/* ── E-basis ─────────────────────────────────────────────────────────── */

static ImplVec build_ebasis(ImplVec *non_binary, BinaryContext *ctx) {
    BitmapVec cycles = find_dcycles(non_binary, ctx->nb_attributes);
    if (cycles.len > 0) {
        for (int i = 0; i < cycles.len; i++) roaring_bitmap_free(cycles.data[i]);
        BitmapVec_free(&cycles);
        ImplVec result = ImplVec_new();
        for (int i = 0; i < non_binary->len; i++) {
            CImplication *src = non_binary->data[i];
            ImplVec_push(&result, impl_create_with_support(
                roaring_bitmap_copy(src->premise),
                roaring_bitmap_copy(src->conclusion),
                src->support ? roaring_bitmap_copy(src->support) : NULL));
        }
        return result;
    }
    BitmapVec_free(&cycles);

    IntVec *by_concl = (IntVec*)calloc(ctx->nb_attributes, sizeof(IntVec));
    for (int a = 0; a < ctx->nb_attributes; a++) by_concl[a] = IntVec_new();
    for (int i = 0; i < non_binary->len; i++) {
        CImplication *imp = non_binary->data[i];
        if (roaring_bitmap_get_cardinality(imp->conclusion) > 0) {
            uint32_t x = roaring_bitmap_minimum(imp->conclusion);
            if ((int)x < ctx->nb_attributes) IntVec_push(&by_concl[x], i);
        }
    }

    ImplVec result = ImplVec_new();
    for (int a = 0; a < ctx->nb_attributes; a++) {
        IntVec *group = &by_concl[a];
        if (group->len == 0) continue;
        IntVec nb_indices = IntVec_new();
        for (int gi = 0; gi < group->len; gi++) {
            CImplication *imp = non_binary->data[group->data[gi]];
            if (roaring_bitmap_get_cardinality(imp->premise) <= 1) {
                ImplVec_push(&result, impl_create_with_support(
                    roaring_bitmap_copy(imp->premise), roaring_bitmap_copy(imp->conclusion),
                    imp->support ? roaring_bitmap_copy(imp->support) : NULL));
            } else {
                IntVec_push(&nb_indices, group->data[gi]);
            }
        }
        if (nb_indices.len <= 1) {
            for (int gi = 0; gi < nb_indices.len; gi++) {
                CImplication *imp = non_binary->data[nb_indices.data[gi]];
                ImplVec_push(&result, impl_create_with_support(
                    roaring_bitmap_copy(imp->premise), roaring_bitmap_copy(imp->conclusion),
                    imp->support ? roaring_bitmap_copy(imp->support) : NULL));
            }
        } else {
            roaring_bitmap_t **cls = (roaring_bitmap_t**)malloc(nb_indices.len * sizeof(roaring_bitmap_t*));
            for (int gi = 0; gi < nb_indices.len; gi++)
                cls[gi] = closure(ctx, non_binary->data[nb_indices.data[gi]]->premise);
            for (int i = 0; i < nb_indices.len; i++) {
                bool dominated = false;
                for (int j = 0; j < nb_indices.len; j++) {
                    if (i == j) continue;
                    if (!roaring_bitmap_is_subset(cls[i], cls[j]) &&
                         roaring_bitmap_is_subset(cls[j], cls[i])) { dominated = true; break; }
                }
                if (!dominated) {
                    CImplication *imp = non_binary->data[nb_indices.data[i]];
                    ImplVec_push(&result, impl_create_with_support(
                        roaring_bitmap_copy(imp->premise), roaring_bitmap_copy(imp->conclusion),
                        imp->support ? roaring_bitmap_copy(imp->support) : NULL));
                }
            }
            for (int gi = 0; gi < nb_indices.len; gi++) roaring_bitmap_free(cls[gi]);
            free(cls);
        }
        IntVec_free(&nb_indices);
    }
    for (int a = 0; a < ctx->nb_attributes; a++) IntVec_free(&by_concl[a]);
    free(by_concl);
    return result;
}

/* ── Worker pthread ──────────────────────────────────────────────────── */

#ifndef __EMSCRIPTEN__
typedef struct {
    BinaryContext    *ctx;
    roaring_bitmap_t **closures;
    roaring_bitmap_t **binary_premises;
    int min_support, nb_attributes;
    int *queue; volatile int queue_head, queue_tail;
    pthread_mutex_t queue_mutex;
    ImplVec *results;
} DBaseWorkerData;

/* Copies profondes pour isoler chaque thread (zéro bitmap partagé en lecture) */
static roaring_bitmap_t **copy_bitmap_array(roaring_bitmap_t **src, int n) {
    roaring_bitmap_t **dst = (roaring_bitmap_t**)malloc(n * sizeof(roaring_bitmap_t*));
    for (int i = 0; i < n; i++) dst[i] = roaring_bitmap_copy(src[i]);
    return dst;
}
static void free_bitmap_array(roaring_bitmap_t **a, int n) {
    for (int i = 0; i < n; i++) roaring_bitmap_free(a[i]);
    free(a);
}
static void *dbasis_worker(void *arg) {
    DBaseWorkerData *wd = (DBaseWorkerData*)arg;

    /* Copies privées : ce thread ne lit plus aucun bitmap partagé. */
    BinaryContext pctx = *wd->ctx;          /* copie superficielle (scalaires + noms) */
    pctx.rows = copy_bitmap_array(wd->ctx->rows, wd->ctx->nb_objects);
    pctx.cols = copy_bitmap_array(wd->ctx->cols, wd->ctx->nb_attributes);
    roaring_bitmap_t **pclosures = copy_bitmap_array(wd->closures, wd->nb_attributes);

    ImplVec local = ImplVec_new();
    while (1) {
        int attr = -1;
        pthread_mutex_lock(&wd->queue_mutex);
        if (wd->queue_head < wd->queue_tail) attr = wd->queue[wd->queue_head++];
        pthread_mutex_unlock(&wd->queue_mutex);
        if (attr < 0) break;

        ImplVec part = compute_non_binary_basis_for_attr(
            &pctx, attr, wd->binary_premises[attr], pclosures, wd->min_support);

        for (int i = 0; i < part.len; i++) ImplVec_push(&local, part.data[i]);
        ImplVec_free(&part);
    }

    pthread_mutex_lock(&wd->queue_mutex);
    for (int i = 0; i < local.len; i++) ImplVec_push(&wd->results[0], local.data[i]);
    pthread_mutex_unlock(&wd->queue_mutex);
    ImplVec_free(&local);

    free_bitmap_array(pctx.rows, wd->ctx->nb_objects);
    free_bitmap_array(pctx.cols, wd->ctx->nb_attributes);
    free_bitmap_array(pclosures, wd->nb_attributes);
    return NULL;
}
#endif

/* ── Pipeline principal ──────────────────────────────────────────────── */

static ImplVec compute_dbasis_impls(BinaryContext *orig_ctx, int min_support, int max_threads) {
	
    /* 1. Clarification */
    ClarificationResult clar = clarify_context(orig_ctx);
    BinaryContext *ctx = clar.clarified;

    ImplVec eq_basis = ImplVec_new();
    for (int ca = 0; ca < clar.nb_classes; ca++) {
        if (roaring_bitmap_get_cardinality(clar.attr_classes[ca]) > 1) {
            uint32_t first = roaring_bitmap_minimum(clar.attr_classes[ca]);
            roaring_uint32_iterator_t it;
            roaring_iterator_init(clar.attr_classes[ca], &it);
            while (it.has_value) {
                int a = (int)it.current_value;
                if (a != (int)first) {
                    roaring_bitmap_t *p = roaring_bitmap_create(); roaring_bitmap_add(p, (uint32_t)a);
                    roaring_bitmap_t *c = roaring_bitmap_create(); roaring_bitmap_add(c, first);
                    ImplVec_push(&eq_basis, impl_create(p, c, 0));
                    roaring_bitmap_t *p2 = roaring_bitmap_create(); roaring_bitmap_add(p2, first);
                    roaring_bitmap_t *c2 = roaring_bitmap_create(); roaring_bitmap_add(c2, (uint32_t)a);
                    ImplVec_push(&eq_basis, impl_create(p2, c2, 0));
                }
                roaring_uint32_iterator_advance(&it);
            }
        }
    }

    /* 2. Base binaire */
    roaring_bitmap_t **binary_premises = (roaring_bitmap_t**)calloc(ctx->nb_attributes, sizeof(roaring_bitmap_t*));
    AttrGraph *graph = attrgraph_create(ctx->nb_attributes);
    roaring_bitmap_t *vertices_in_graph = roaring_bitmap_create();
    int always_true_attr = -1;

    for (int a1 = 0; a1 < ctx->nb_attributes; a1++) {
        if ((int)roaring_bitmap_get_cardinality(ctx->cols[a1]) == ctx->nb_objects) {
            always_true_attr = a1;
        } else {
            for (int a2 = 0; a2 < ctx->nb_attributes; a2++) {
                if (a1 == a2) continue;
                if (roaring_bitmap_is_subset(ctx->cols[a1], ctx->cols[a2])) {
                    roaring_bitmap_add(graph->adj[a1], (uint32_t)a2);
                    roaring_bitmap_add(vertices_in_graph, (uint32_t)a1);
                    roaring_bitmap_add(vertices_in_graph, (uint32_t)a2);
                    if (!binary_premises[a2]) binary_premises[a2] = roaring_bitmap_create();
                    roaring_bitmap_add(binary_premises[a2], (uint32_t)a1);
                }
            }
        }
    }

    ImplVec binary_basis = ImplVec_new();
    if (always_true_attr >= 0) {
        roaring_bitmap_t *p = roaring_bitmap_create();
        roaring_bitmap_t *c = roaring_bitmap_create();
        roaring_bitmap_add(c, (uint32_t)always_true_attr);
        ImplVec_push(&binary_basis, impl_create(p, c, ctx->nb_objects));
        roaring_bitmap_remove(vertices_in_graph, (uint32_t)always_true_attr);
        for (int v = 0; v < ctx->nb_attributes; v++) {
            roaring_bitmap_remove(graph->adj[v], (uint32_t)always_true_attr);
            roaring_bitmap_remove(graph->adj[always_true_attr], (uint32_t)v);
        }
    }
    transitive_reduction(graph);
    IntVec topo = topological_order(graph, vertices_in_graph);
    for (int ti = 0; ti < topo.len; ti++) {
        int u = topo.data[ti];
        roaring_uint32_iterator_t it;
        roaring_iterator_init(graph->adj[u], &it);
        while (it.has_value) {
            int v = (int)it.current_value;
            roaring_bitmap_t *p = roaring_bitmap_create(); roaring_bitmap_add(p, (uint32_t)u);
            roaring_bitmap_t *c = roaring_bitmap_create(); roaring_bitmap_add(c, (uint32_t)v);
            roaring_bitmap_t *sup = roaring_bitmap_copy(ctx->cols[u]);
            ImplVec_push(&binary_basis, impl_create_with_support(p, c, sup));
            roaring_uint32_iterator_advance(&it);
        }
    }
    IntVec_free(&topo);
    roaring_bitmap_free(vertices_in_graph);
    attrgraph_free(graph);

    /* 3. Fermetures */
    roaring_bitmap_t **closures = (roaring_bitmap_t**)malloc(ctx->nb_attributes * sizeof(roaring_bitmap_t*));
    for (int a = 0; a < ctx->nb_attributes; a++) {
        roaring_bitmap_t *attr_set = roaring_bitmap_create();
        roaring_bitmap_add(attr_set, (uint32_t)a);
        closures[a] = closure(ctx, attr_set);
        roaring_bitmap_free(attr_set);
    }

    /* 4. Base non-binaire */
    ImplVec non_binary = ImplVec_new();
#ifndef __EMSCRIPTEN__
    if (max_threads != 1 && ctx->nb_attributes > 1) {
        int nb_threads = (max_threads < 1) ? get_cpu_count() : max_threads;
        if (nb_threads > ctx->nb_attributes) nb_threads = ctx->nb_attributes;
        fprintf(stderr, "[dbasis] threads=%d attrs=%d\n", nb_threads, ctx->nb_attributes);
        DBaseWorkerData wd;
        wd.ctx = ctx; wd.closures = closures; wd.binary_premises = binary_premises;
        wd.min_support = min_support; wd.nb_attributes = ctx->nb_attributes;
        wd.queue = (int*)malloc(ctx->nb_attributes * sizeof(int));
        for (int a = 0; a < ctx->nb_attributes; a++) wd.queue[a] = a;
        wd.queue_head = 0; wd.queue_tail = ctx->nb_attributes;
        pthread_mutex_init(&wd.queue_mutex, NULL);
        wd.results = &non_binary;
        pthread_t *threads = (pthread_t*)malloc(nb_threads * sizeof(pthread_t));
        for (int t = 0; t < nb_threads; t++) pthread_create(&threads[t], NULL, dbasis_worker, &wd);
        for (int t = 0; t < nb_threads; t++) pthread_join(threads[t], NULL);
        free(threads); free(wd.queue);
        pthread_mutex_destroy(&wd.queue_mutex);
    } else
#endif
    {
        for (int a = 0; a < ctx->nb_attributes; a++) {
            ImplVec part = compute_non_binary_basis_for_attr(ctx, a, binary_premises[a], closures, min_support);
            for (int i = 0; i < part.len; i++) ImplVec_push(&non_binary, part.data[i]);
            ImplVec_free(&part);
        }
    }

    /* 5. Fusion binary + non-binary */
    ImplVec temp_basis = ImplVec_new();
    for (int i = 0; i < binary_basis.len; i++) ImplVec_push(&temp_basis, binary_basis.data[i]);
    for (int i = 0; i < non_binary.len;   i++) ImplVec_push(&temp_basis, non_binary.data[i]);

    /* 6. Réécriture vers contexte original + supports */
    ImplVec dbasis = ImplVec_new();
    for (int i = 0; i < eq_basis.len; i++) {
        CImplication *imp = eq_basis.data[i];
        roaring_bitmap_t *sup = compute_extent(orig_ctx, imp->premise);
        imp->support = sup;
        imp->support_size = (int)roaring_bitmap_get_cardinality(sup);
        ImplVec_push(&dbasis, imp);
    }
    ImplVec non_binary_with_support = ImplVec_new();
    for (int i = 0; i < temp_basis.len; i++) {
        CImplication *imp = temp_basis.data[i];
        roaring_bitmap_t *orig_p = convert_to_original(imp->premise,     clar.attr_classes);
        roaring_bitmap_t *orig_c = convert_to_original(imp->conclusion,   clar.attr_classes);
        roaring_bitmap_t *sup    = compute_extent(orig_ctx, orig_p);
        CImplication *new_imp = impl_create_with_support(orig_p, roaring_bitmap_copy(orig_c), sup);
        roaring_bitmap_free(orig_c);
        if (roaring_bitmap_get_cardinality(imp->premise) < 2)
            ImplVec_push(&dbasis, new_imp);
        else if ((int)roaring_bitmap_get_cardinality(sup) >= min_support)
            ImplVec_push(&non_binary_with_support, new_imp);
        else
            impl_free(new_imp);
    }

    /* 7. E-basis */
    ImplVec ebasis = build_ebasis(&non_binary_with_support, orig_ctx);
    for (int i = 0; i < ebasis.len; i++) ImplVec_push(&dbasis, ebasis.data[i]);
    ImplVec_free(&ebasis);
    for (int i = 0; i < non_binary_with_support.len; i++) impl_free(non_binary_with_support.data[i]);
    ImplVec_free(&non_binary_with_support);
    for (int i = 0; i < temp_basis.len; i++) impl_free(temp_basis.data[i]);
    ImplVec_free(&temp_basis);
    ImplVec_free(&binary_basis);
    ImplVec_free(&non_binary);

    /* Nettoyage */
    for (int a = 0; a < ctx->nb_attributes; a++) roaring_bitmap_free(closures[a]);
    free(closures);
    for (int a = 0; a < ctx->nb_attributes; a++) if (binary_premises[a]) roaring_bitmap_free(binary_premises[a]);
    free(binary_premises);
    clarification_free(&clar);

	ImplVec_free(&eq_basis);   /* éléments partagés dans dbasis : on ne libère que le tableau */
	    return dbasis;
	}
	/* ── Sérialisation JSON (canal historique, conservé) ─────────────────── */
	char *run_dbasis_impl(BinaryContext *orig_ctx, int min_support, int max_threads) {
	    ImplVec dbasis = compute_dbasis_impls(orig_ctx, min_support, max_threads);

	    StrBuf sb = sb_new();
	    sb_printf(&sb, "{\"algorithm\":\"DBase\",\"implications\":%d,\"context\":", dbasis.len);
	    sb_append_json_str(&sb, orig_ctx->name);
	    sb_printf(&sb, ",\"objects\":%d,\"attributes\":%d", orig_ctx->nb_objects, orig_ctx->nb_attributes);
	    sb_append(&sb, ",\"basis\":[");
	    for (int i = 0; i < dbasis.len; i++) {
	        if (i > 0) sb_append(&sb, ",");
	        CImplication *imp = dbasis.data[i];
	        sb_append(&sb, "{\"premise\":[");
	        roaring_uint32_iterator_t it;
	        roaring_iterator_init(imp->premise, &it);
	        int first = 1;
	        while (it.has_value) {
	            if (!first) sb_append(&sb, ",");
	            int a = (int)it.current_value;
	            if (a < orig_ctx->attr_names.len) sb_append_json_str(&sb, orig_ctx->attr_names.data[a]);
	            else sb_printf(&sb, "\"%d\"", a);
	            first = 0; roaring_uint32_iterator_advance(&it);
	        }
	        sb_append(&sb, "],\"conclusion\":[");
	        roaring_iterator_init(imp->conclusion, &it); first = 1;
	        while (it.has_value) {
	            if (!first) sb_append(&sb, ",");
	            int a = (int)it.current_value;
	            if (a < orig_ctx->attr_names.len) sb_append_json_str(&sb, orig_ctx->attr_names.data[a]);
	            else sb_printf(&sb, "\"%d\"", a);
	            first = 0; roaring_uint32_iterator_advance(&it);
	        }
	        sb_printf(&sb, "],\"support\":%d}", imp->support_size);
	    }
	    sb_append(&sb, "]}");

	    for (int i = 0; i < dbasis.len; i++) impl_free(dbasis.data[i]);
	    ImplVec_free(&dbasis);
	    return sb.buf;
	}

	/* ── Sérialisation plate int[] (canal rapide, indices uniquement) ─────────
	 * Format auto-descriptif :
	 *   [0]                      M = nombre d'implications
	 *   puis pour chaque implication :
	 *       [cardP] p0 p1 ... p(cardP-1)
	 *       [cardC] c0 c1 ... c(cardC-1)
	 *       [support]
	 * Indices exprimés dans le contexte ORIGINAL (déjà réécrits par le pipeline).
	 */
	int *run_dbasis_flat(BinaryContext *orig_ctx, int min_support, int max_threads, int *out_len) {
	    ImplVec dbasis = compute_dbasis_impls(orig_ctx, min_support, max_threads);

	    /* 1. Taille totale */
	    long total = 1;  /* [0] = M */
	    for (int i = 0; i < dbasis.len; i++) {
	        CImplication *imp = dbasis.data[i];
	        total += (long)roaring_bitmap_get_cardinality(imp->premise)
	               + (long)roaring_bitmap_get_cardinality(imp->conclusion)
	               + 3;  /* cardP + cardC + support */
	    }

	    int *flat = (int*)malloc((size_t)total * sizeof(int));
	    int p = 0;
	    flat[p++] = dbasis.len;

	    for (int i = 0; i < dbasis.len; i++) {
	        CImplication *imp = dbasis.data[i];
	        roaring_uint32_iterator_t it;

	        flat[p++] = (int)roaring_bitmap_get_cardinality(imp->premise);
	        roaring_iterator_init(imp->premise, &it);
	        while (it.has_value) { flat[p++] = (int)it.current_value; roaring_uint32_iterator_advance(&it); }

	        flat[p++] = (int)roaring_bitmap_get_cardinality(imp->conclusion);
	        roaring_iterator_init(imp->conclusion, &it);
	        while (it.has_value) { flat[p++] = (int)it.current_value; roaring_uint32_iterator_advance(&it); }

	        flat[p++] = imp->support_size;
	    }

	    for (int i = 0; i < dbasis.len; i++) impl_free(dbasis.data[i]);
	    ImplVec_free(&dbasis);

	    *out_len = p;
	    return flat;
	}

