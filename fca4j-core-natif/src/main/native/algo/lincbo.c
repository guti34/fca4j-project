#include "lincbo.h"
#include "../core/fca4j_common.h"
#include "../core/implication.h"
#include "../core/closure.h"
#include "../core/strbuf.h"

typedef struct {
    BinaryContext *ctx;
    ImplVec implications;
    roaring_bitmap_t *defaultConclusion;
    IntVec *list;
} LinCbOState;

static void lincbo_init(LinCbOState *st, BinaryContext *ctx) {
    st->ctx = ctx;
    st->implications = ImplVec_new();
    st->defaultConclusion = NULL;
    st->list = (IntVec*)malloc(ctx->nb_attributes * sizeof(IntVec));
    for (int a = 0; a < ctx->nb_attributes; a++) st->list[a] = IntVec_new();
}

static int lincbo_add_implication(LinCbOState *st, roaring_bitmap_t *premise,
                                   roaring_bitmap_t *closureSet, roaring_bitmap_t *support) {
    int idx = st->implications.len;
    ImplVec_push(&st->implications, impl_create_with_support(
        roaring_bitmap_copy(premise), roaring_bitmap_copy(closureSet), roaring_bitmap_copy(support)));
    if (roaring_bitmap_is_empty(premise)) {
        if (!st->defaultConclusion) st->defaultConclusion = roaring_bitmap_create();
        roaring_bitmap_or_inplace(st->defaultConclusion, st->implications.data[idx]->conclusion);
    }
    roaring_uint32_iterator_t it;
    roaring_iterator_init(premise, &it);
    while (it.has_value) { IntVec_push(&st->list[(int)it.current_value], idx); roaring_uint32_iterator_advance(&it); }
    return idx;
}

static bool lincbo_equals_until(roaring_bitmap_t *s1, roaring_bitmap_t *s2, int y) {
    roaring_uint32_iterator_t i1, i2;
    roaring_iterator_init(s1, &i1); roaring_iterator_init(s2, &i2);
    while (true) {
        if (!i1.has_value) return !i2.has_value || (int)i2.current_value > y;
        if (!i2.has_value) return (int)i1.current_value > y;
        int v1 = (int)i1.current_value, v2 = (int)i2.current_value;
        if (v1 != v2) return v1 > y && v2 > y;
        roaring_uint32_iterator_advance(&i1); roaring_uint32_iterator_advance(&i2);
    }
}

static int bm_min(roaring_bitmap_t *bm) {
    return roaring_bitmap_is_empty(bm) ? 0x7FFFFFFF : (int)roaring_bitmap_minimum(bm);
}

static bool lincbo_lin_closure_rc(LinCbOState *st, roaring_bitmap_t *B, int y,
                                   roaring_bitmap_t *Z, int *prev_count, int prev_count_len,
                                   roaring_bitmap_t **D_out, int **count_out, int *count_len_out) {
    roaring_bitmap_t *D = roaring_bitmap_copy(B);
    if (st->defaultConclusion) roaring_bitmap_or_inplace(D, st->defaultConclusion);
    int nimp = st->implications.len;
    int *count = (int*)malloc(nimp * sizeof(int));
    for (int i = 0; i < prev_count_len && i < nimp; i++) count[i] = prev_count[i];
    for (int i = prev_count_len; i < nimp; i++) {
        roaring_bitmap_t *diff = roaring_bitmap_andnot(st->implications.data[i]->premise, B);
        count[i] = (int)roaring_bitmap_get_cardinality(diff);
        roaring_bitmap_free(diff);
    }
    roaring_bitmap_t *Zwork = roaring_bitmap_copy(Z);
    while (!roaring_bitmap_is_empty(Zwork)) {
        int m = (int)roaring_bitmap_minimum(Zwork);
        roaring_bitmap_remove(Zwork, (uint32_t)m);
        for (int li = 0; li < st->list[m].len; li++) {
            int num_imp = st->list[m].data[li];
            if (num_imp >= nimp) continue;
            if (--count[num_imp] == 0) {
                CImplication *imp = st->implications.data[num_imp];
                if (bm_min(imp->conclusion) < y && bm_min(D) >= y) {
                    roaring_bitmap_free(D); roaring_bitmap_free(Zwork); free(count); return false;
                }
                roaring_bitmap_t *add = roaring_bitmap_andnot(imp->conclusion, D);
                if (bm_min(add) < y) {
                    roaring_bitmap_free(add); roaring_bitmap_free(D); roaring_bitmap_free(Zwork); free(count); return false;
                }
                roaring_bitmap_or_inplace(D, add); roaring_bitmap_or_inplace(Zwork, add);
                roaring_bitmap_free(add);
            }
        }
    }
    roaring_bitmap_free(Zwork);
    *D_out = D; *count_out = count; *count_len_out = nimp;
    return true;
}

static void lincbo_step(LinCbOState *st, roaring_bitmap_t *B, int y, roaring_bitmap_t *Z,
                         int *prev_count, int prev_count_len,
                         roaring_bitmap_t *lastAttrSet, roaring_bitmap_t *lastExtent) {
    roaring_bitmap_t *Bo = NULL; int *count = NULL; int count_len = 0;
    if (!lincbo_lin_closure_rc(st, B, y, Z, prev_count, prev_count_len, &Bo, &count, &count_len)) return;
    roaring_bitmap_t *ext      = compute_extent(st->ctx, Bo);
    roaring_bitmap_t *intent   = compute_intent(st->ctx, ext);
    roaring_bitmap_t *bclosure = roaring_bitmap_copy(Bo);
    roaring_bitmap_or_inplace(bclosure, intent);
    roaring_bitmap_free(intent);
    if (!roaring_bitmap_equals(Bo, bclosure)) {
        lincbo_add_implication(st, Bo, bclosure, ext);
        if (lincbo_equals_until(Bo, bclosure, y)) {
            roaring_bitmap_t *Zp = roaring_bitmap_andnot(bclosure, Bo);
            lincbo_step(st, bclosure, y, Zp, count, count_len, lastAttrSet, lastExtent);
            roaring_bitmap_free(Zp);
        }
    } else {
        for (int i = st->ctx->nb_attributes - 1; i > y; i--) {
            if (!roaring_bitmap_contains(Bo, (uint32_t)i)) {
                roaring_bitmap_t *Bp = roaring_bitmap_copy(Bo); roaring_bitmap_add(Bp, (uint32_t)i);
                roaring_bitmap_t *Zp = roaring_bitmap_create(); roaring_bitmap_add(Zp, (uint32_t)i);
                lincbo_step(st, Bp, i, Zp, count, count_len, bclosure, ext);
                roaring_bitmap_free(Bp); roaring_bitmap_free(Zp);
            }
        }
    }
    roaring_bitmap_free(Bo); roaring_bitmap_free(bclosure); roaring_bitmap_free(ext); free(count);
}

char *run_lincbo_impl(BinaryContext *ctx) {
    LinCbOState st;
    lincbo_init(&st, ctx);
    roaring_bitmap_t *B0 = roaring_bitmap_create();
    roaring_bitmap_t *Z0 = roaring_bitmap_create();
    lincbo_step(&st, B0, -1, Z0, NULL, 0, NULL, NULL);
    roaring_bitmap_free(B0); roaring_bitmap_free(Z0);

    StrBuf sb = sb_new();
    sb_printf(&sb, "{\"algorithm\":\"LinCbO\",\"implications\":%d,\"context\":", st.implications.len);
    sb_append_json_str(&sb, ctx->name);
    sb_printf(&sb, ",\"objects\":%d,\"attributes\":%d,\"basis\":[", ctx->nb_objects, ctx->nb_attributes);
    for (int i = 0; i < st.implications.len; i++) {
        if (i > 0) sb_append(&sb, ",");
        CImplication *imp = st.implications.data[i];
        sb_append(&sb, "{\"premise\":[");
        roaring_uint32_iterator_t it; roaring_iterator_init(imp->premise, &it);
        int first = 1;
        while (it.has_value) {
            if (!first) sb_append(&sb, ",");
            int a = (int)it.current_value;
            if (a < ctx->attr_names.len) sb_append_json_str(&sb, ctx->attr_names.data[a]);
            else sb_printf(&sb, "\"%d\"", a);
            first = 0; roaring_uint32_iterator_advance(&it);
        }
        sb_append(&sb, "],\"conclusion\":[");
        roaring_iterator_init(imp->conclusion, &it); first = 1;
        while (it.has_value) {
            if (!first) sb_append(&sb, ",");
            int a = (int)it.current_value;
            if (a < ctx->attr_names.len) sb_append_json_str(&sb, ctx->attr_names.data[a]);
            else sb_printf(&sb, "\"%d\"", a);
            first = 0; roaring_uint32_iterator_advance(&it);
        }
        sb_printf(&sb, "],\"support\":%d}", imp->support_size);
    }
    sb_append(&sb, "]}");
    for (int i = 0; i < st.implications.len; i++) impl_free(st.implications.data[i]);
    ImplVec_free(&st.implications);
    for (int a = 0; a < ctx->nb_attributes; a++) IntVec_free(&st.list[a]);
    free(st.list);
    if (st.defaultConclusion) roaring_bitmap_free(st.defaultConclusion);
    return sb.buf;
}
