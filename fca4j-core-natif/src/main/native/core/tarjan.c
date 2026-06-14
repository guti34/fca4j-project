#include "tarjan.h"

typedef struct {
    int n;
    roaring_bitmap_t **adj;
    int  *index_;
    int  *lowlink;
    bool *on_stack;
    IntVec stack;
    int current_index;
    BitmapVec sccs;
} TarjanState;

static void tarjan_strongconnect(TarjanState *st, int v) {
    st->index_[v] = st->lowlink[v] = st->current_index++;
    IntVec_push(&st->stack, v);
    st->on_stack[v] = true;
    roaring_uint32_iterator_t it;
    roaring_iterator_init(st->adj[v], &it);
    while (it.has_value) {
        int w = (int)it.current_value;
        if (st->index_[w] < 0) {
            tarjan_strongconnect(st, w);
            if (st->lowlink[w] < st->lowlink[v]) st->lowlink[v] = st->lowlink[w];
        } else if (st->on_stack[w]) {
            if (st->index_[w] < st->lowlink[v]) st->lowlink[v] = st->index_[w];
        }
        roaring_uint32_iterator_advance(&it);
    }
    if (st->lowlink[v] == st->index_[v]) {
        roaring_bitmap_t *scc = roaring_bitmap_create();
        int w;
        do {
            w = st->stack.data[st->stack.len - 1];
            st->stack.len--;
            st->on_stack[w] = false;
            roaring_bitmap_add(scc, (uint32_t)w);
        } while (w != v);
        if (roaring_bitmap_get_cardinality(scc) > 1)
            BitmapVec_push(&st->sccs, scc);
        else
            roaring_bitmap_free(scc);
    }
}

BitmapVec find_dcycles(ImplVec *basis, int nb_attributes) {
    roaring_bitmap_t **adj = (roaring_bitmap_t**)calloc(nb_attributes, sizeof(roaring_bitmap_t*));
    for (int i = 0; i < nb_attributes; i++) adj[i] = roaring_bitmap_create();
    for (int i = 0; i < basis->len; i++) {
        CImplication *imp = basis->data[i];
        if (roaring_bitmap_get_cardinality(imp->premise) <= 1) continue;
        uint32_t x = roaring_bitmap_minimum(imp->conclusion);
        roaring_uint32_iterator_t it;
        roaring_iterator_init(imp->premise, &it);
        while (it.has_value) {
            roaring_bitmap_add(adj[x], (uint32_t)it.current_value);
            roaring_uint32_iterator_advance(&it);
        }
    }
    TarjanState st;
    st.n = nb_attributes; st.adj = adj;
    st.index_  = (int*)malloc(nb_attributes * sizeof(int));
    st.lowlink = (int*)malloc(nb_attributes * sizeof(int));
    st.on_stack = (bool*)calloc(nb_attributes, sizeof(bool));
    st.stack = IntVec_new();
    st.current_index = 0;
    st.sccs = BitmapVec_new();
    memset(st.index_, -1, nb_attributes * sizeof(int));
    for (int v = 0; v < nb_attributes; v++)
        if (st.index_[v] < 0) tarjan_strongconnect(&st, v);
    free(st.index_); free(st.lowlink); free(st.on_stack);
    IntVec_free(&st.stack);
    for (int i = 0; i < nb_attributes; i++) roaring_bitmap_free(adj[i]);
    free(adj);
    return st.sccs;
}
