#include "implication.h"

CImplication *impl_create(roaring_bitmap_t *premise,
                           roaring_bitmap_t *conclusion,
                           int support_size) {
    CImplication *imp = (CImplication*)malloc(sizeof(CImplication));
    imp->premise      = premise;
    imp->conclusion   = roaring_bitmap_andnot(conclusion, premise);
    roaring_bitmap_free(conclusion);
    imp->support      = NULL;
    imp->support_size = support_size;
    return imp;
}

CImplication *impl_create_with_support(roaring_bitmap_t *premise,
                                        roaring_bitmap_t *conclusion,
                                        roaring_bitmap_t *support) {
    CImplication *imp = (CImplication*)malloc(sizeof(CImplication));
    imp->premise      = premise;
    imp->conclusion   = roaring_bitmap_andnot(conclusion, premise);
    roaring_bitmap_free(conclusion);
    imp->support      = support;
    imp->support_size = support ? (int)roaring_bitmap_get_cardinality(support) : 0;
    return imp;
}

void impl_free(CImplication *imp) {
    if (!imp) return;
    roaring_bitmap_free(imp->premise);
    roaring_bitmap_free(imp->conclusion);
    if (imp->support) roaring_bitmap_free(imp->support);
    free(imp);
}

void implvec_free_all(ImplVec *v) {
    for (int i = 0; i < v->len; i++) impl_free(v->data[i]);
    ImplVec_free(v);
}
