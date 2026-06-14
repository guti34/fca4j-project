#include "hermes.h"
#include "../core/fca4j_common.h"
#include "../core/conceptorder.h"

typedef struct { roaring_bitmap_t *refs; roaring_bitmap_t *values; } RefSet;
VEC_DEF(RefSet, RefSetVec)
typedef struct { roaring_bitmap_t *intent; roaring_bitmap_t *extent; roaring_bitmap_t *values; } HConceptSet;
VEC_DEF(HConceptSet, HConceptVec)

static RefSet refset_create(void) {
    RefSet rs; rs.refs = roaring_bitmap_create(); rs.values = roaring_bitmap_create(); return rs;
}
static RefSet refset_create_with_ref(int ref) {
    RefSet rs = refset_create(); roaring_bitmap_add(rs.refs, (uint32_t)ref); return rs;
}
static RefSet refset_create_with_ref_and_values(int ref, roaring_bitmap_t *values) {
    RefSet rs; rs.refs = roaring_bitmap_create(); roaring_bitmap_add(rs.refs, (uint32_t)ref);
    rs.values = roaring_bitmap_copy(values); return rs;
}
static RefSet refset_create_from_refs(roaring_bitmap_t *refs) {
    RefSet rs; rs.refs = roaring_bitmap_copy(refs); rs.values = roaring_bitmap_create(); return rs;
}
static void refset_free(RefSet *rs) { roaring_bitmap_free(rs->refs); roaring_bitmap_free(rs->values); }

static int cmp_refset_card_desc(const void *a, const void *b) {
    return (int)roaring_bitmap_get_cardinality(((RefSet*)b)->values)
         - (int)roaring_bitmap_get_cardinality(((RefSet*)a)->values);
}

static RefSetVec hermes_clarify(RefSetVec *setToClarify, RefSetVec *setToSynchronize) {
    qsort(setToClarify->data, setToClarify->len, sizeof(RefSet), cmp_refset_card_desc);
    for (int i = setToClarify->len - 1; i > 0; i--) {
        RefSet *tc = &setToClarify->data[i];
        for (int j = i - 1; j >= 0; j--) {
            RefSet *ot = &setToClarify->data[j];
            int cd = (int)roaring_bitmap_get_cardinality(tc->values) - (int)roaring_bitmap_get_cardinality(ot->values);
            if (cd != 0) break;
            if (roaring_bitmap_equals(tc->values, ot->values)) {
                roaring_bitmap_or_inplace(ot->refs, tc->refs);
                refset_free(&setToClarify->data[i]);
                for (int k = i; k < setToClarify->len - 1; k++) setToClarify->data[k] = setToClarify->data[k+1];
                setToClarify->len--;
                break;
            }
        }
    }
    RefSetVec result = RefSetVec_new();
    for (int i = 0; i < setToSynchronize->len; i++)
        RefSetVec_push(&result, refset_create_from_refs(setToSynchronize->data[i].refs));
    for (int i = 0; i < setToClarify->len; i++) {
        roaring_uint32_iterator_t it;
        roaring_iterator_init(setToClarify->data[i].values, &it);
        while (it.has_value) {
            int idx = (int)it.current_value;
            if (idx < result.len) roaring_bitmap_add(result.data[idx].values, (uint32_t)i);
            roaring_uint32_iterator_advance(&it);
        }
    }
    return result;
}

static RefSetVec hermes_compute_dom_relation(RefSetVec *attrSets) {
    RefSetVec dom = RefSetVec_new();
    for (int i = 0; i < attrSets->len; i++) {
        RefSet newSet = refset_create();
        roaring_bitmap_or_inplace(newSet.refs, attrSets->data[i].refs);
        for (int j = 0; j < attrSets->len; j++)
            if (i == j || roaring_bitmap_is_subset(attrSets->data[i].values, attrSets->data[j].values))
                roaring_bitmap_add(newSet.values, (uint32_t)j);
        RefSetVec_push(&dom, newSet);
    }
    return dom;
}

static void hermes_compute_hasse(ConceptOrder *gsh, BinaryContext *ctx, HConceptVec *conceptSets) {
    int n = conceptSets->len;
    int *order = (int*)malloc(n * sizeof(int));
    for (int i = 0; i < n; i++) order[i] = i;
    for (int i = 1; i < n; i++) {
        int key = order[i];
        int keyCard = (int)roaring_bitmap_get_cardinality(conceptSets->data[key].values);
        int j = i - 1;
        while (j >= 0 && (int)roaring_bitmap_get_cardinality(conceptSets->data[order[j]].values) < keyCard)
            { order[j+1] = order[j]; j--; }
        order[j+1] = key;
    }
    int *conceptIds = (int*)malloc(n * sizeof(int));
    roaring_bitmap_t *visited_set = roaring_bitmap_create();
    for (int pos = 0; pos < n; pos++) {
        int idx = order[pos];
        HConceptSet *cSet = &conceptSets->data[idx];
        int conceptS = co_add_concept(gsh, roaring_bitmap_copy(cSet->extent), roaring_bitmap_copy(cSet->intent));
        roaring_bitmap_or_inplace(gsh->rextents[conceptS], cSet->extent);
        roaring_bitmap_or_inplace(gsh->rintents[conceptS], cSet->intent);
        conceptIds[pos] = conceptS;
        for (int prevPos = pos - 1; prevPos >= 0; prevPos--) {
            int conceptT = conceptIds[prevPos];
            if (roaring_bitmap_contains(visited_set, (uint32_t)conceptT)) {
                roaring_bitmap_remove(visited_set, (uint32_t)conceptT);
            } else {
                int prevIdx = order[prevPos];
                HConceptSet *tCS = &conceptSets->data[prevIdx];
                bool isParent = false;
                bool s_has_obj = !roaring_bitmap_is_empty(gsh->rextents[conceptS]);
                bool t_has_obj = !roaring_bitmap_is_empty(gsh->rextents[conceptT]);
                uint32_t t_obj_min = t_has_obj ? roaring_bitmap_minimum(gsh->rextents[conceptT]) : 0;
                if (t_has_obj && !s_has_obj) {
                    uint32_t s_attr = roaring_bitmap_minimum(gsh->rintents[conceptS]);
                    isParent = roaring_bitmap_contains(ctx->rows[t_obj_min], s_attr);
                } else {
                    isParent = roaring_bitmap_is_subset(cSet->values, tCS->values);
                }
                if (isParent) {
                    co_add_edge(gsh, conceptT, conceptS);
                    roaring_bitmap_or_inplace(gsh->extents[conceptS], gsh->extents[conceptT]);
                    roaring_bitmap_or_inplace(gsh->intents[conceptT], gsh->rintents[conceptS]);
                    IntVec stack = IntVec_new();
                    roaring_uint32_iterator_t cit;
                    roaring_iterator_init(gsh->graph->children[conceptT], &cit);
                    while (cit.has_value) {
                        int child = (int)cit.current_value;
                        if (!roaring_bitmap_contains(visited_set, (uint32_t)child)) {
                            roaring_bitmap_add(visited_set, (uint32_t)child);
                            roaring_bitmap_or_inplace(gsh->intents[child], gsh->rintents[conceptS]);
                            IntVec_push(&stack, child);
                        }
                        roaring_uint32_iterator_advance(&cit);
                    }
                    int head = 0;
                    while (head < stack.len) {
                        int cur = stack.data[head++];
                        roaring_iterator_init(gsh->graph->children[cur], &cit);
                        while (cit.has_value) {
                            int child = (int)cit.current_value;
                            if (!roaring_bitmap_contains(visited_set, (uint32_t)child)) {
                                roaring_bitmap_add(visited_set, (uint32_t)child);
                                roaring_bitmap_or_inplace(gsh->intents[child], gsh->rintents[conceptS]);
                                IntVec_push(&stack, child);
                            }
                            roaring_uint32_iterator_advance(&cit);
                        }
                    }
                    IntVec_free(&stack);
                }
            }
        }
    }
    roaring_bitmap_free(visited_set);
    free(order); free(conceptIds);
}

/* Construit l'AOC-poset Hermes et renvoie le ConceptOrder.
 * Les rextents/rintents (labellisation réduite) sont remplis par
 * hermes_compute_hasse ; les extents/intents complets aussi (propagation).
 * Côté flat, seuls rextents/rintents + arêtes sont sérialisés : populate()
 * reconstruit le reste côté Java. */
static ConceptOrder *build_hermes(BinaryContext *ctx) {
    ConceptOrder *gsh = co_create(ctx);
    RefSetVec attrSets = RefSetVec_new();
    RefSetVec objSets  = RefSetVec_new();

    if (ctx->nb_attributes > ctx->nb_objects) {
        for (int a = 0; a < ctx->nb_attributes; a++)
            RefSetVec_push(&attrSets, refset_create_with_ref_and_values(a, ctx->cols[a]));
        for (int o = 0; o < ctx->nb_objects; o++)
            RefSetVec_push(&objSets, refset_create_with_ref(o));
        RefSetVec newObj = hermes_clarify(&attrSets, &objSets);
        for (int i = 0; i < objSets.len; i++) refset_free(&objSets.data[i]); RefSetVec_free(&objSets);
        objSets = newObj;
        RefSetVec newAttr = hermes_clarify(&objSets, &attrSets);
        for (int i = 0; i < attrSets.len; i++) refset_free(&attrSets.data[i]); RefSetVec_free(&attrSets);
        attrSets = newAttr;
    } else {
        for (int o = 0; o < ctx->nb_objects; o++)
            RefSetVec_push(&objSets, refset_create_with_ref_and_values(o, ctx->rows[o]));
        for (int a = 0; a < ctx->nb_attributes; a++)
            RefSetVec_push(&attrSets, refset_create_with_ref(a));
        RefSetVec newAttr = hermes_clarify(&objSets, &attrSets);
        for (int i = 0; i < attrSets.len; i++) refset_free(&attrSets.data[i]); RefSetVec_free(&attrSets);
        attrSets = newAttr;
        RefSetVec newObj = hermes_clarify(&attrSets, &objSets);
        for (int i = 0; i < objSets.len; i++) refset_free(&objSets.data[i]); RefSetVec_free(&objSets);
        objSets = newObj;
    }

    RefSetVec domSets = hermes_compute_dom_relation(&attrSets);
    HConceptVec concepts = HConceptVec_new();
    for (int i = 0; i < objSets.len; i++) {
        HConceptSet cs;
        cs.intent = roaring_bitmap_create();
        cs.extent = roaring_bitmap_copy(objSets.data[i].refs);
        cs.values = roaring_bitmap_copy(objSets.data[i].values);
        HConceptVec_push(&concepts, cs);
    }
    for (int i = 0; i < domSets.len; i++) {
        bool done = false;
        for (int j = 0; j < concepts.len; j++) {
            if (roaring_bitmap_equals(domSets.data[i].values, concepts.data[j].values)) {
                roaring_bitmap_or_inplace(concepts.data[j].intent, domSets.data[i].refs);
                done = true; break;
            }
        }
        if (!done) {
            HConceptSet cs;
            cs.intent = roaring_bitmap_copy(domSets.data[i].refs);
            cs.extent = roaring_bitmap_create();
            cs.values = roaring_bitmap_copy(domSets.data[i].values);
            HConceptVec_push(&concepts, cs);
        }
    }
    hermes_compute_hasse(gsh, ctx, &concepts);
    for (int i = 0; i < attrSets.len; i++) refset_free(&attrSets.data[i]); RefSetVec_free(&attrSets);
    for (int i = 0; i < objSets.len;  i++) refset_free(&objSets.data[i]);  RefSetVec_free(&objSets);
    for (int i = 0; i < domSets.len;  i++) refset_free(&domSets.data[i]);  RefSetVec_free(&domSets);
    for (int i = 0; i < concepts.len; i++) {
        roaring_bitmap_free(concepts.data[i].intent);
        roaring_bitmap_free(concepts.data[i].extent);
        roaring_bitmap_free(concepts.data[i].values);
    }
    HConceptVec_free(&concepts);
    return gsh;
}

/* Point d'entrée JSON (compat / debug). */
char *run_hermes_impl(BinaryContext *ctx) {
    ConceptOrder *gsh = build_hermes(ctx);
    char *json = co_to_json(gsh);
    co_free(gsh);
    return json;
}

/* Point d'entrée tableau plat (rapide) — même format que run_addextent_flat.
 * populate() reconstruit intents/extents complets côté Java. */
int *run_hermes_flat(BinaryContext *ctx, int *out_len) {
    ConceptOrder *gsh = build_hermes(ctx);
    int *flat = co_to_flat_array(gsh, out_len);
    co_free(gsh);
    return flat;
}
