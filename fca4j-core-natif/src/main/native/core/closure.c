#include "closure.h"

roaring_bitmap_t *compute_extent(BinaryContext *ctx, roaring_bitmap_t *attributes) {
    roaring_bitmap_t *extent = roaring_bitmap_create();
    if (ctx->nb_attributes < ctx->nb_objects) {
        roaring_bitmap_add_range(extent, 0, (uint32_t)ctx->nb_objects);
        roaring_uint32_iterator_t it;
        roaring_iterator_init(attributes, &it);
        while (it.has_value) {
            roaring_bitmap_and_inplace(extent, ctx->cols[(int)it.current_value]);
            roaring_uint32_iterator_advance(&it);
        }
    } else {
        for (int o = 0; o < ctx->nb_objects; o++)
            if (roaring_bitmap_is_subset(attributes, ctx->rows[o]))
                roaring_bitmap_add(extent, (uint32_t)o);
    }
    return extent;
}

roaring_bitmap_t *compute_intent(BinaryContext *ctx, roaring_bitmap_t *extent) {
    roaring_bitmap_t *intent = roaring_bitmap_create();
    uint32_t card = (uint32_t)roaring_bitmap_get_cardinality(extent);
    if (card < (uint32_t)ctx->nb_attributes) {
        roaring_bitmap_add_range(intent, 0, (uint32_t)ctx->nb_attributes);
        roaring_uint32_iterator_t it;
        roaring_iterator_init(extent, &it);
        while (it.has_value) {
            roaring_bitmap_and_inplace(intent, ctx->rows[(int)it.current_value]);
            roaring_uint32_iterator_advance(&it);
        }
    } else {
        for (int a = 0; a < ctx->nb_attributes; a++)
            if (roaring_bitmap_is_subset(extent, ctx->cols[a]))
                roaring_bitmap_add(intent, (uint32_t)a);
    }
    return intent;
}

roaring_bitmap_t *closure(BinaryContext *ctx, roaring_bitmap_t *attrSet) {
    roaring_bitmap_t *ext = compute_extent(ctx, attrSet);
    roaring_bitmap_t *cl  = compute_intent(ctx, ext);
    roaring_bitmap_or_inplace(cl, attrSet);
    roaring_bitmap_free(ext);
    return cl;
}
