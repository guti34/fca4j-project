#include "context.h"

BinaryContext *ctx_create(int nb_obj, int nb_attr, const char *name) {
    BinaryContext *ctx = (BinaryContext*)calloc(1, sizeof(BinaryContext));
    ctx->nb_objects    = nb_obj;
    ctx->nb_attributes = nb_attr;
    strncpy(ctx->name, name ? name : "", 255);
    ctx->rows = (roaring_bitmap_t**)calloc(nb_obj,  sizeof(roaring_bitmap_t*));
    ctx->cols = (roaring_bitmap_t**)calloc(nb_attr, sizeof(roaring_bitmap_t*));
    for (int i = 0; i < nb_obj;  i++) ctx->rows[i] = roaring_bitmap_create();
    for (int i = 0; i < nb_attr; i++) ctx->cols[i] = roaring_bitmap_create();
    ctx->obj_names  = StrVec_new();
    ctx->attr_names = StrVec_new();
    return ctx;
}

void ctx_set(BinaryContext *ctx, int obj, int attr, bool val) {
    if (val) {
        roaring_bitmap_add(ctx->rows[obj],  (uint32_t)attr);
        roaring_bitmap_add(ctx->cols[attr], (uint32_t)obj);
    } else {
        roaring_bitmap_remove(ctx->rows[obj],  (uint32_t)attr);
        roaring_bitmap_remove(ctx->cols[attr], (uint32_t)obj);
    }
}

void ctx_add_obj_name(BinaryContext *ctx, const char *n) {
    StrVec_push(&ctx->obj_names, strdup(n));
}

void ctx_add_attr_name(BinaryContext *ctx, const char *n) {
    StrVec_push(&ctx->attr_names, strdup(n));
}

void ctx_free(BinaryContext *ctx) {
    if (!ctx) return;
    for (int i = 0; i < ctx->nb_objects;    i++) roaring_bitmap_free(ctx->rows[i]);
    for (int i = 0; i < ctx->nb_attributes; i++) roaring_bitmap_free(ctx->cols[i]);
    free(ctx->rows);
    free(ctx->cols);
    for (int i = 0; i < ctx->obj_names.len;  i++) free(ctx->obj_names.data[i]);
    for (int i = 0; i < ctx->attr_names.len; i++) free(ctx->attr_names.data[i]);
    StrVec_free(&ctx->obj_names);
    StrVec_free(&ctx->attr_names);
    free(ctx);
}

BinaryContext *ctx_from_matrix(int nb_obj, int nb_attr,
                                const signed char *matrix,
                                const char *name) {
    BinaryContext *ctx = ctx_create(nb_obj, nb_attr, name);
    for (int o = 0; o < nb_obj; o++)
        for (int a = 0; a < nb_attr; a++)
            if (matrix[o * nb_attr + a])
                ctx_set(ctx, o, a, true);
    return ctx;
}
