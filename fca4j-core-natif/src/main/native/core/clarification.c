#include "clarification.h"

ClarificationResult clarify_context(BinaryContext *ctx) {
    ClarificationResult res;

    int *class_of = (int*)malloc(ctx->nb_attributes * sizeof(int));
    memset(class_of, -1, ctx->nb_attributes * sizeof(int));

    BitmapVec classes   = BitmapVec_new();
    IntVec    class_rep = IntVec_new();

    for (int a = 0; a < ctx->nb_attributes; a++) {
        if (class_of[a] >= 0) continue;
        int cls = classes.len;
        roaring_bitmap_t *cls_set = roaring_bitmap_create();
        roaring_bitmap_add(cls_set, (uint32_t)a);
        class_of[a] = cls;
        for (int b = a + 1; b < ctx->nb_attributes; b++) {
            if (class_of[b] >= 0) continue;
            if (roaring_bitmap_equals(ctx->cols[a], ctx->cols[b])) {
                roaring_bitmap_add(cls_set, (uint32_t)b);
                class_of[b] = cls;
            }
        }
        BitmapVec_push(&classes, cls_set);
        IntVec_push(&class_rep, a);
    }

    int nb_clar_attrs = classes.len;
    BinaryContext *clar = ctx_create(ctx->nb_objects, nb_clar_attrs, ctx->name);

    for (int ca = 0; ca < nb_clar_attrs; ca++) {
        int rep = class_rep.data[ca];
        for (int o = 0; o < ctx->nb_objects; o++)
            if (roaring_bitmap_contains(ctx->cols[rep], (uint32_t)o))
                ctx_set(clar, o, ca, true);
        if (rep < ctx->attr_names.len)
            ctx_add_attr_name(clar, ctx->attr_names.data[rep]);
    }
    for (int o = 0; o < ctx->nb_objects; o++)
        if (o < ctx->obj_names.len)
            ctx_add_obj_name(clar, ctx->obj_names.data[o]);

    res.clarified   = clar;
    res.attr_classes = classes.data; /* transféré */
    res.nb_classes  = nb_clar_attrs;

    free(class_of);
    IntVec_free(&class_rep);
    return res;
}

void clarification_free(ClarificationResult *res) {
    for (int i = 0; i < res->nb_classes; i++)
        roaring_bitmap_free(res->attr_classes[i]);
    free(res->attr_classes);
    ctx_free(res->clarified);
}

roaring_bitmap_t *convert_to_original(roaring_bitmap_t *set,
                                       roaring_bitmap_t **attr_classes) {
    roaring_bitmap_t *result = roaring_bitmap_create();
    roaring_uint32_iterator_t it;
    roaring_iterator_init(set, &it);
    while (it.has_value) {
        int clar_attr = (int)it.current_value;
        uint32_t orig = roaring_bitmap_minimum(attr_classes[clar_attr]);
        roaring_bitmap_add(result, orig);
        roaring_uint32_iterator_advance(&it);
    }
    return result;
}
