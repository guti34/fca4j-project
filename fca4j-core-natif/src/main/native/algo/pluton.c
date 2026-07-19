/*
 * pluton.c — AOC-poset Pluton (portage C)
 * Portage de fr.lirmm.fca4j.algo.AOC_poset_Pluton
 *
 * Pipeline identique à la version Java (option "tout en C", comme Hermes) :
 *   1. clarification bidirectionnelle du contexte (objets ET attributs), les
 *      classes d'équivalence portant leurs membres d'origine (RefSet.refs) ;
 *   2. calcul de l'AOC-poset sur le contexte clarifié :
 *        - partitions maxmod OA puis AO,
 *        - extension linéaire par l'algorithme du Petit Poucet (tomThumb),
 *        - concepts simplifiés (computeLinext),
 *        - ordre par balayage arrière avec marquage anti-transitivité
 *          (identique à hermes_compute_hasse) ;
 *   3. réexpression des rextents/rintents dans les indices d'origine via les
 *      classes d'équivalence (substitution), avant sérialisation flat.
 *
 * Les rextents/rintents finaux sont donc exprimés dans le contexte ORIGINAL,
 * exactement comme Hermes ; co_to_flat_array les sérialise tels quels et
 * ConceptOrder.populate reconstruit le reste côté Java.
 *
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 */

#include <stdio.h>
#include <stdlib.h>
#include "pluton.h"
#include "../core/fca4j_common.h"
#include "../core/conceptorder.h"

/* ════════════════════════════════════════════════════════════════════════
 *  Clarification (reprise de hermes.c : RefSet + clarify bidirectionnel)
 * ════════════════════════════════════════════════════════════════════════ */

typedef struct { roaring_bitmap_t *refs; roaring_bitmap_t *values; } RefSet;
VEC_DEF(RefSet, RefSetVec)

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

/* Identique à hermes_clarify : fusionne les RefSet de mêmes valeurs (en
 * accumulant leurs refs), puis resynchronise setToSynchronize sur les indices
 * clarifiés. */
static RefSetVec pluton_clarify(RefSetVec *setToClarify, RefSetVec *setToSynchronize) {
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

/* Résultat de la clarification : contexte clarifié + classes d'équivalence. */
typedef struct {
    BinaryContext    *clarified;
    roaring_bitmap_t **obj_classes;   /* obj_classes[i]  = objets originaux de la classe i  */
    roaring_bitmap_t **attr_classes;  /* attr_classes[j] = attrs originaux de la classe j   */
    int               nb_obj_classes;
    int               nb_attr_classes;
} PlutonClarif;

/* Clarifie objets ET attributs, dans le même ordre que le Java
 * (Clarification.run avec clarifyAttributes=clarifyObjects=true). */
static PlutonClarif pluton_build_clarified(BinaryContext *ctx) {
    RefSetVec attrSets = RefSetVec_new();
    RefSetVec objSets  = RefSetVec_new();

    if (ctx->nb_attributes > ctx->nb_objects) {
        for (int a = 0; a < ctx->nb_attributes; a++)
            RefSetVec_push(&attrSets, refset_create_with_ref_and_values(a, ctx->cols[a]));
        for (int o = 0; o < ctx->nb_objects; o++)
            RefSetVec_push(&objSets, refset_create_with_ref(o));
        RefSetVec newObj = pluton_clarify(&attrSets, &objSets);
        for (int i = 0; i < objSets.len; i++) refset_free(&objSets.data[i]); RefSetVec_free(&objSets);
        objSets = newObj;
        RefSetVec newAttr = pluton_clarify(&objSets, &attrSets);
        for (int i = 0; i < attrSets.len; i++) refset_free(&attrSets.data[i]); RefSetVec_free(&attrSets);
        attrSets = newAttr;
    } else {
        for (int o = 0; o < ctx->nb_objects; o++)
            RefSetVec_push(&objSets, refset_create_with_ref_and_values(o, ctx->rows[o]));
        for (int a = 0; a < ctx->nb_attributes; a++)
            RefSetVec_push(&attrSets, refset_create_with_ref(a));
        RefSetVec newAttr = pluton_clarify(&objSets, &attrSets);
        for (int i = 0; i < attrSets.len; i++) refset_free(&attrSets.data[i]); RefSetVec_free(&attrSets);
        attrSets = newAttr;
        RefSetVec newObj = pluton_clarify(&attrSets, &objSets);
        for (int i = 0; i < objSets.len; i++) refset_free(&objSets.data[i]); RefSetVec_free(&objSets);
        objSets = newObj;
    }

    /* À ce stade : objSets[i].values = attributs clarifiés de l'objet clarifié i,
     *              attrSets[j].values = objets clarifiés de l'attribut clarifié j.
     * On construit le contexte clarifié à partir des colonnes attrSets.values. */
    int nO = objSets.len;
    int nA = attrSets.len;
    BinaryContext *cl = ctx_create(nO, nA, ctx->name);
    for (int j = 0; j < nA; j++) {
        roaring_uint32_iterator_t it;
        roaring_iterator_init(attrSets.data[j].values, &it);
        while (it.has_value) {
            ctx_set(cl, (int)it.current_value, j, true);
            roaring_uint32_iterator_advance(&it);
        }
    }

    PlutonClarif pc;
    pc.clarified = cl;
    pc.nb_obj_classes  = nO;
    pc.nb_attr_classes = nA;
    pc.obj_classes  = (roaring_bitmap_t**)malloc(nO * sizeof(roaring_bitmap_t*));
    pc.attr_classes = (roaring_bitmap_t**)malloc(nA * sizeof(roaring_bitmap_t*));
    for (int i = 0; i < nO; i++) pc.obj_classes[i]  = roaring_bitmap_copy(objSets.data[i].refs);
    for (int j = 0; j < nA; j++) pc.attr_classes[j] = roaring_bitmap_copy(attrSets.data[j].refs);

    for (int i = 0; i < objSets.len;  i++) refset_free(&objSets.data[i]);  RefSetVec_free(&objSets);
    for (int i = 0; i < attrSets.len; i++) refset_free(&attrSets.data[i]); RefSetVec_free(&attrSets);
    return pc;
}

static void pluton_clarif_free(PlutonClarif *pc) {
    for (int i = 0; i < pc->nb_obj_classes;  i++) roaring_bitmap_free(pc->obj_classes[i]);
    for (int j = 0; j < pc->nb_attr_classes; j++) roaring_bitmap_free(pc->attr_classes[j]);
    free(pc->obj_classes);
    free(pc->attr_classes);
    ctx_free(pc->clarified);
}

/* ════════════════════════════════════════════════════════════════════════
 *  Partitions maxmod
 * ════════════════════════════════════════════════════════════════════════ */

/* maxmodPartitionOA : partitionne les objets par rapport aux attributs.
 * Renvoie une BitmapVec (chaque bloc est un ensemble d'objets, à libérer). */
static BitmapVec maxmod_partition_oa(BinaryContext *ctx) {
    BitmapVec part = BitmapVec_new();
    roaring_bitmap_t *objects = roaring_bitmap_create();
    for (int o = 0; o < ctx->nb_objects; o++) roaring_bitmap_add(objects, (uint32_t)o);
    BitmapVec_push(&part, objects);

    for (int a = 0; a < ctx->nb_attributes; a++) {
        roaring_bitmap_t *r = ctx->cols[a];
        BitmapVec newPart = BitmapVec_new();
        for (int j = 0; j < part.len; j++) {
            roaring_bitmap_t *k = part.data[j];
            if (roaring_bitmap_get_cardinality(k) > 1) {
                roaring_bitmap_t *k1 = roaring_bitmap_and(k, r);
                roaring_bitmap_t *k2 = roaring_bitmap_andnot(k, r);
                if (!roaring_bitmap_is_empty(k1)) BitmapVec_push(&newPart, k1); else roaring_bitmap_free(k1);
                if (!roaring_bitmap_is_empty(k2)) BitmapVec_push(&newPart, k2); else roaring_bitmap_free(k2);
                roaring_bitmap_free(k);
            } else {
                BitmapVec_push(&newPart, k);
            }
        }
        BitmapVec_free(&part);
        part = newPart;
    }
    return part;
}

/* maxmodPartitionAO : version duale, à partir de la partition d'objets.
 * partObjects n'est pas consommée (on lit seulement le premier élément de
 * chaque bloc). */
static BitmapVec maxmod_partition_ao(BinaryContext *ctx, BitmapVec *partObjects) {
    BitmapVec PART = BitmapVec_new();
    roaring_bitmap_t *attributes = roaring_bitmap_create();
    for (int a = 0; a < ctx->nb_attributes; a++) roaring_bitmap_add(attributes, (uint32_t)a);
    BitmapVec_push(&PART, attributes);

    for (int i = partObjects->len - 1; i >= 0; i--) {
        int x = (int)roaring_bitmap_minimum(partObjects->data[i]); /* .first() */
        roaring_bitmap_t *R = ctx->rows[x];
        BitmapVec newPART = BitmapVec_new();
        for (int j = 0; j < PART.len; j++) {
            roaring_bitmap_t *K = PART.data[j];
            if (roaring_bitmap_get_cardinality(K) > 1) {
                roaring_bitmap_t *K1 = roaring_bitmap_and(K, R);
                roaring_bitmap_t *K2 = roaring_bitmap_andnot(K, R);
                if (!roaring_bitmap_is_empty(K1)) BitmapVec_push(&newPART, K1); else roaring_bitmap_free(K1);
                if (!roaring_bitmap_is_empty(K2)) BitmapVec_push(&newPART, K2); else roaring_bitmap_free(K2);
                roaring_bitmap_free(K);
            } else {
                BitmapVec_push(&newPART, K);
            }
        }
        BitmapVec_free(&PART);
        PART = newPART;
    }
    return PART;
}

/* ════════════════════════════════════════════════════════════════════════
 *  Petit Poucet (tomThumb) + extension linéaire (computeLinext)
 * ════════════════════════════════════════════════════════════════════════ */

/* Fusionne les deux partitions en une liste alternée de maxmods, en marquant
 * lesquels sont des extensions (objets). Consomme Y et X (transfère la
 * propriété des bitmaps dans `list` ; les blocs non transférés sont libérés).
 *
 * `list`      : BitmapVec de sortie (maxmods dans l'ordre du Petit Poucet)
 * `isExtent`  : IntVec parallèle (1 = le maxmod de même index est un extent) */
static void tom_thumb(BinaryContext *ctx, BitmapVec *Y, BitmapVec *X,
                      BitmapVec *list, IntVec *isExtent) {
    int q = Y->len;
    int r = X->len;
    int j = q - 1;
    int i = 0;
    while (j >= 0 && i < r) {
        roaring_bitmap_t *Yj = Y->data[j];
        int y = (int)roaring_bitmap_minimum(Yj);
        roaring_bitmap_t *Xi = X->data[i];
        int x = (int)roaring_bitmap_minimum(Xi);
        if (roaring_bitmap_contains(ctx->rows[y], (uint32_t)x)) { /* matrix.get(y,x) */
            BitmapVec_push(list, Xi);
            IntVec_push(isExtent, 0);
            i++;
        } else {
            BitmapVec_push(list, Yj);
            IntVec_push(isExtent, 1);
            j--;
        }
    }
    while (j >= 0) {
        BitmapVec_push(list, Y->data[j]);
        IntVec_push(isExtent, 1);
        j--;
    }
    while (i < r) {
        BitmapVec_push(list, X->data[i]);
        IntVec_push(isExtent, 0);
        i++;
    }
    /* Les bitmaps de Y et X sont soit transférés dans `list`, soit jamais
     * atteints par les curseurs — mais dans ce merge chaque bloc est transféré
     * exactement une fois (les deux boucles de queue épuisent les restes), donc
     * on ne libère rien ici. On vide seulement les conteneurs. */
    BitmapVec_free(Y);
    BitmapVec_free(X);
}

/* computeLinext : crée les concepts simplifiés dans l'ordre du Petit Poucet et
 * remplit leur extension/intension réduite (dans l'espace clarifié). Renvoie la
 * liste des ids de concepts (extension linéaire), et remplit rextent/rintent. */
static IntVec pluton_compute_linext(ConceptOrder *gsh, BinaryContext *ctx) {
    IntVec linext = IntVec_new();

    if (ctx->nb_attributes == 0) {
        roaring_bitmap_t *extent = roaring_bitmap_create();
        for (int o = 0; o < ctx->nb_objects; o++) roaring_bitmap_add(extent, (uint32_t)o);
        roaring_bitmap_t *intent = roaring_bitmap_create();
        int c = co_add_concept(gsh, extent, intent);
        roaring_bitmap_or_inplace(gsh->rextents[c], extent);
        IntVec_push(&linext, c);
        return linext;
    }

    BitmapVec Y = maxmod_partition_oa(ctx);
    BitmapVec X = maxmod_partition_ao(ctx, &Y);
    BitmapVec maxmods = BitmapVec_new();
    IntVec    isExtent = IntVec_new();
    tom_thumb(ctx, &Y, &X, &maxmods, &isExtent);

    /* Parcours des maxmods du plus haut au plus bas (comme le Java, i de fin à 1),
     * en couplant un extent suivi d'un intent en un même concept quand c'est
     * possible ; sinon concept simplifié seul. */
    int lastAlone = 1;
    for (int idx = maxmods.len - 1; idx > 0; idx--) {
        roaring_bitmap_t *set1 = maxmods.data[idx];
        roaring_bitmap_t *set2 = maxmods.data[idx - 1];

        if (isExtent.data[idx]) {                 /* set1 est un extent */
            int couple = 0;
            roaring_bitmap_t *objects = set1;
            if (!isExtent.data[idx - 1]) {        /* set2 est un intent */
                int o = (int)roaring_bitmap_minimum(objects);
                int a0 = (int)roaring_bitmap_minimum(set2);
                roaring_bitmap_t *ga = ctx->cols[a0];
                if (roaring_bitmap_contains(ga, (uint32_t)o)) {  /* 1er test */
                    couple = 1;
                    roaring_bitmap_t *fo = ctx->rows[o];
                    roaring_uint32_iterator_t jt;
                    roaring_iterator_init(ga, &jt);
                    while (couple && jt.has_value) {
                        roaring_bitmap_t *fj = ctx->rows[(int)jt.current_value];
                        if (!roaring_bitmap_is_subset(fo, fj)) couple = 0; /* fj.containsAll(fo) */
                        roaring_uint32_iterator_advance(&jt);
                    }
                }
            }
            roaring_bitmap_t *extent = roaring_bitmap_copy(objects);
            roaring_bitmap_t *intent = roaring_bitmap_create();
            if (couple) {
                roaring_bitmap_or_inplace(intent, set2);       /* attributs de set2 */
                if (idx == 1) lastAlone = 0;
                idx--;
            }
            int c = co_add_concept(gsh, extent, intent);
            roaring_bitmap_or_inplace(gsh->rextents[c], extent);
            roaring_bitmap_or_inplace(gsh->rintents[c], intent);
            IntVec_push(&linext, c);
        } else {                                   /* concept avec extension réduite vide */
            roaring_bitmap_t *attributes = set1;
            roaring_bitmap_t *extent = roaring_bitmap_create();
            roaring_bitmap_t *intent = roaring_bitmap_copy(attributes);
            int c = co_add_concept(gsh, extent, intent);
            roaring_bitmap_or_inplace(gsh->rintents[c], intent);
            IntVec_push(&linext, c);
        }
    }
    if (lastAlone) {
        roaring_bitmap_t *maxmod = maxmods.data[0];
        roaring_bitmap_t *extent = roaring_bitmap_create();
        roaring_bitmap_t *intent = roaring_bitmap_create();
        if (isExtent.data[0]) roaring_bitmap_or_inplace(extent, maxmod);
        else                  roaring_bitmap_or_inplace(intent, maxmod);
        int c = co_add_concept(gsh, extent, intent);
        roaring_bitmap_or_inplace(gsh->rextents[c], extent);
        roaring_bitmap_or_inplace(gsh->rintents[c], intent);
        IntVec_push(&linext, c);
    }

    for (int k = 0; k < maxmods.len; k++) roaring_bitmap_free(maxmods.data[k]);
    BitmapVec_free(&maxmods);
    IntVec_free(&isExtent);
    return linext;
}

/* ════════════════════════════════════════════════════════════════════════
 *  Ordre (identique à hermes_compute_hasse, adapté aux données Pluton)
 * ════════════════════════════════════════════════════════════════════════ */

/* Partie T du test de parenté, les données de S ayant été préparées une fois
 * par la boucle externe (hissage identique au niveau 0.5 de la version Java :
 * fs/gs/s_attr ne dépendent que de S et sont invariants sur toute la boucle j). */
static bool pluton_is_parent_t(ConceptOrder *gsh, BinaryContext *ctx,
                               bool s_has_obj, roaring_bitmap_t *fs,
                               roaring_bitmap_t *gs, int s_attr, int conceptT) {
    if (s_has_obj) {
        if (!roaring_bitmap_is_empty(gsh->rextents[conceptT])) {
            roaring_bitmap_t *ft = ctx->rows[roaring_bitmap_minimum(gsh->rextents[conceptT])];
            return roaring_bitmap_is_subset(fs, ft);          /* ft.containsAll(fs) */
        } else {
            roaring_bitmap_t *ft = ctx->cols[roaring_bitmap_minimum(gsh->rintents[conceptT])];
            roaring_uint32_iterator_t it;
            roaring_iterator_init(fs, &it);
            while (it.has_value) {
                roaring_bitmap_t *ge = ctx->cols[(int)it.current_value];
                if (!roaring_bitmap_is_subset(ft, ge)) return false;
                roaring_uint32_iterator_advance(&it);
            }
            return true;
        }
    } else {
        if (!roaring_bitmap_is_empty(gsh->rintents[conceptT])) {
            roaring_bitmap_t *gt = ctx->cols[roaring_bitmap_minimum(gsh->rintents[conceptT])];
            return roaring_bitmap_is_subset(gt, gs);          /* gs.containsAll(gt) */
        } else {
            int t_obj = (int)roaring_bitmap_minimum(gsh->rextents[conceptT]);
            return roaring_bitmap_contains(ctx->rows[t_obj], (uint32_t)s_attr);
        }
    }
}

/* Boucle d'ordre : pour chaque concept de la linext, comparer aux précédents en
 * sautant les descendants déjà marqués (anti-transitivité), avec héritage des
 * intents réduits de S dans les descendants de T. Structure calquée sur
 * hermes_compute_hasse. */
static void pluton_compute_order(ConceptOrder *gsh, BinaryContext *ctx, IntVec *linext) {
    int n = gsh->counter;
    /* visited plat : accès O(1), au lieu d'un roaring_bitmap martelé O(K^2) fois
     * (c'est exactement le gain HashSet -> boolean[] mesuré côté Java). */
    char *visited = (char*)calloc(n, sizeof(char));
    /* pile de descendance réutilisée sur toute la boucle, pas réallouée par arête */
    IntVec stack = IntVec_new();

    for (int i = 0; i < linext->len; i++) {
        int conceptS = linext->data[i];
        /* invariants de S, calculés une fois (hissage niveau 0.5) */
        bool s_has_obj = !roaring_bitmap_is_empty(gsh->rextents[conceptS]);
        roaring_bitmap_t *fs = NULL, *gs = NULL;
        int s_attr = -1;
        if (s_has_obj) {
            fs = ctx->rows[roaring_bitmap_minimum(gsh->rextents[conceptS])];
        } else {
            s_attr = (int)roaring_bitmap_minimum(gsh->rintents[conceptS]);
            gs = ctx->cols[s_attr];
        }

        for (int j = i - 1; j >= 0; j--) {
            int conceptT = linext->data[j];
            if (visited[conceptT]) {
                visited[conceptT] = 0;
            } else if (pluton_is_parent_t(gsh, ctx, s_has_obj, fs, gs, s_attr, conceptT)) {
                co_add_edge(gsh, conceptT, conceptS);
                /* Le calcul de l'ordre ne lit que rextents/rintents ; les
                 * extents/intents COMPLETS ne servent qu'au JSON et sont
                 * reconstruits par propagation (pluton_rebuild_full_sets). On
                 * n'accumule donc rien ici : cela évite O(K^2) unions de grands
                 * bitsets dans la boucle chaude. Seul le marquage des descendants
                 * (anti-transitivité) est nécessaire. */
                stack.len = 0;
                roaring_uint32_iterator_t cit;
                roaring_iterator_init(gsh->graph->children[conceptT], &cit);
                while (cit.has_value) {
                    int child = (int)cit.current_value;
                    if (!visited[child]) {
                        visited[child] = 1;
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
                        if (!visited[child]) {
                            visited[child] = 1;
                            IntVec_push(&stack, child);
                        }
                        roaring_uint32_iterator_advance(&cit);
                    }
                }
            }
        }
    }
    IntVec_free(&stack);
    free(visited);
}

/* ════════════════════════════════════════════════════════════════════════
 *  Substitution : rextents/rintents clarifiés → indices d'origine
 * ════════════════════════════════════════════════════════════════════════ */

static roaring_bitmap_t *substitute(roaring_bitmap_t *set, roaring_bitmap_t **classes) {
    roaring_bitmap_t *res = roaring_bitmap_create();
    roaring_uint32_iterator_t it;
    roaring_iterator_init(set, &it);
    while (it.has_value) {
        roaring_bitmap_or_inplace(res, classes[(int)it.current_value]);
        roaring_uint32_iterator_advance(&it);
    }
    return res;
}

static void pluton_substitute_reduced(ConceptOrder *gsh, PlutonClarif *pc) {
    for (int c = 0; c < gsh->counter; c++) {
        roaring_bitmap_t *re = substitute(gsh->rextents[c], pc->obj_classes);
        roaring_bitmap_free(gsh->rextents[c]);
        gsh->rextents[c] = re;
        roaring_bitmap_t *ri = substitute(gsh->rintents[c], pc->attr_classes);
        roaring_bitmap_free(gsh->rintents[c]);
        gsh->rintents[c] = ri;
    }
}

/* ════════════════════════════════════════════════════════════════════════
 *  Assemblage
 * ════════════════════════════════════════════════════════════════════════ */

/* Construit l'AOC-poset Pluton et renvoie le ConceptOrder, dont les
 * rextents/rintents sont exprimés dans le contexte ORIGINAL (ctx). Le graphe
 * est déjà transitivement réduit (propriété de l'algorithme). */
static ConceptOrder *build_pluton(BinaryContext *ctx) {
    PlutonClarif pc = pluton_build_clarified(ctx);
    /* Le ConceptOrder pointe le contexte original : c'est l'espace dans lequel
     * les rextents/rintents seront exprimés après substitution, et celui que
     * co_to_flat_array / le writer utilisent. Pendant le calcul, isParentOf et
     * les partitions lisent le contexte CLARIFIÉ (pc.clarified) via les
     * fonctions ci-dessus, jamais gsh->ctx. */
    ConceptOrder *gsh = co_create(ctx);

    IntVec linext = pluton_compute_linext(gsh, pc.clarified);
    pluton_compute_order(gsh, pc.clarified, &linext);
    IntVec_free(&linext);

    pluton_substitute_reduced(gsh, &pc);
    pluton_clarif_free(&pc);
    return gsh;
}

/* Reconstruit extents/intents complets à partir des réduits, par propagation
 * dans le DAG (bottom-up pour les extents, top-down pour les intents). Utilisé
 * uniquement par le chemin JSON de debug ; le chemin flat n'en a pas besoin
 * (ConceptOrder.populate le fait côté Java). */
static void pluton_rebuild_full_sets(ConceptOrder *gsh) {
    int n = gsh->counter;
    for (int c = 0; c < n; c++) {
        roaring_bitmap_free(gsh->extents[c]);
        roaring_bitmap_free(gsh->intents[c]);
        gsh->extents[c] = roaring_bitmap_copy(gsh->rextents[c]);
        gsh->intents[c] = roaring_bitmap_copy(gsh->rintents[c]);
    }
    /* intents top-down : file BFS depuis les racines (sans parents), chaque
     * enfant reçoit l'intent complet de ses parents. */
    int *indeg = (int*)calloc(n, sizeof(int));
    for (int c = 0; c < n; c++)
        indeg[c] = (int)roaring_bitmap_get_cardinality(gsh->graph->parents[c]);
    int *queue = (int*)malloc(n * sizeof(int));
    int head = 0, tail = 0;
    for (int c = 0; c < n; c++) if (indeg[c] == 0) queue[tail++] = c;
    while (head < tail) {
        int c = queue[head++];
        roaring_uint32_iterator_t it;
        roaring_iterator_init(gsh->graph->children[c], &it);
        while (it.has_value) {
            int child = (int)it.current_value;
            roaring_bitmap_or_inplace(gsh->intents[child], gsh->intents[c]);
            if (--indeg[child] == 0) queue[tail++] = child;
            roaring_uint32_iterator_advance(&it);
        }
    }
    /* extents bottom-up : file BFS depuis les feuilles (sans enfants), chaque
     * parent reçoit l'extent complet de ses enfants. */
    int *outdeg = (int*)calloc(n, sizeof(int));
    for (int c = 0; c < n; c++)
        outdeg[c] = (int)roaring_bitmap_get_cardinality(gsh->graph->children[c]);
    head = tail = 0;
    for (int c = 0; c < n; c++) if (outdeg[c] == 0) queue[tail++] = c;
    while (head < tail) {
        int c = queue[head++];
        roaring_uint32_iterator_t it;
        roaring_iterator_init(gsh->graph->parents[c], &it);
        while (it.has_value) {
            int parent = (int)it.current_value;
            roaring_bitmap_or_inplace(gsh->extents[parent], gsh->extents[c]);
            if (--outdeg[parent] == 0) queue[tail++] = parent;
            roaring_uint32_iterator_advance(&it);
        }
    }
    free(indeg); free(outdeg); free(queue);
}

char *run_pluton_impl(BinaryContext *ctx) {
    /* Chemin JSON de compat/debug. Pluton ne remappe que les ensembles réduits
     * vers l'espace original ; on reconstruit les complets par propagation. */
    ConceptOrder *gsh = build_pluton(ctx);
    pluton_rebuild_full_sets(gsh);
    char *json = co_to_json(gsh);
    co_free(gsh);
    return json;
}

int *run_pluton_flat(BinaryContext *ctx, int *out_len) {
    ConceptOrder *gsh = build_pluton(ctx);
    int *flat = co_to_flat_array(gsh, out_len);
    co_free(gsh);
    return flat;
}
