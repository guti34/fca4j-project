#include "graph.h"

AttrGraph *attrgraph_create(int n) {
    AttrGraph *g = (AttrGraph*)malloc(sizeof(AttrGraph));
    g->n   = n;
    g->adj = (roaring_bitmap_t**)calloc(n, sizeof(roaring_bitmap_t*));
    for (int i = 0; i < n; i++) g->adj[i] = roaring_bitmap_create();
    return g;
}

void attrgraph_free(AttrGraph *g) {
    for (int i = 0; i < g->n; i++) roaring_bitmap_free(g->adj[i]);
    free(g->adj);
    free(g);
}

void transitive_reduction(AttrGraph *g) {
    for (int u = 0; u < g->n; u++) {
        uint32_t card = (uint32_t)roaring_bitmap_get_cardinality(g->adj[u]);
        if (card <= 1) continue;
        roaring_bitmap_t *reachable = roaring_bitmap_create();
        uint32_t *succs = (uint32_t*)malloc(card * sizeof(uint32_t));
        roaring_bitmap_to_uint32_array(g->adj[u], succs);
        IntVec queue = IntVec_new();
        for (uint32_t i = 0; i < card; i++) {
            int s = (int)succs[i];
            roaring_uint32_iterator_t it;
            roaring_iterator_init(g->adj[s], &it);
            while (it.has_value) {
                int t = (int)it.current_value;
                if (!roaring_bitmap_contains(reachable, (uint32_t)t)) {
                    roaring_bitmap_add(reachable, (uint32_t)t);
                    IntVec_push(&queue, t);
                }
                roaring_uint32_iterator_advance(&it);
            }
        }
        int head = 0;
        while (head < queue.len) {
            int cur = queue.data[head++];
            roaring_uint32_iterator_t it;
            roaring_iterator_init(g->adj[cur], &it);
            while (it.has_value) {
                int t = (int)it.current_value;
                if (!roaring_bitmap_contains(reachable, (uint32_t)t)) {
                    roaring_bitmap_add(reachable, (uint32_t)t);
                    IntVec_push(&queue, t);
                }
                roaring_uint32_iterator_advance(&it);
            }
        }
        IntVec_free(&queue);
        roaring_bitmap_andnot_inplace(g->adj[u], reachable);
        roaring_bitmap_free(reachable);
        free(succs);
    }
}

IntVec topological_order(AttrGraph *g, roaring_bitmap_t *vertices) {
    int *in_deg = (int*)calloc(g->n, sizeof(int));
    roaring_uint32_iterator_t it;
    roaring_iterator_init(vertices, &it);
    while (it.has_value) {
        int u = (int)it.current_value;
        roaring_uint32_iterator_t it2;
        roaring_iterator_init(g->adj[u], &it2);
        while (it2.has_value) { in_deg[(int)it2.current_value]++; roaring_uint32_iterator_advance(&it2); }
        roaring_uint32_iterator_advance(&it);
    }
    IntVec queue = IntVec_new();
    roaring_iterator_init(vertices, &it);
    while (it.has_value) {
        if (in_deg[(int)it.current_value] == 0) IntVec_push(&queue, (int)it.current_value);
        roaring_uint32_iterator_advance(&it);
    }
    IntVec result = IntVec_new();
    int head = 0;
    while (head < queue.len) {
        int u = queue.data[head++];
        IntVec_push(&result, u);
        roaring_uint32_iterator_t it2;
        roaring_iterator_init(g->adj[u], &it2);
        while (it2.has_value) {
            int v = (int)it2.current_value;
            if (--in_deg[v] == 0) IntVec_push(&queue, v);
            roaring_uint32_iterator_advance(&it2);
        }
    }
    free(in_deg);
    IntVec_free(&queue);
    return result;
}
