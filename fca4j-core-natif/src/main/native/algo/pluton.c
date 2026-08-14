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
#include "../core/bitset.h"
#include "../core/bitset_roaring.h"

/* ════════════════════════════════════════════════════════════════════════
 *  Clarification (reprise de hermes.c : RefSet + clarify bidirectionnel)
 * ════════════════════════════════════════════════════════════════════════ */

/* owns_values : faux quand .values EMPRUNTE un bitmap plutôt que d'en détenir
 * une copie — cas des RefSet produites par pluton_clarify_initial, dont les
 * .values pointent directement sur ctx->rows[o] / ctx->cols[a] du contexte
 * ORIGINAL (voir son commentaire de tête pour la raison d'être : éviter une
 * roaring_bitmap_copy par élément quand l'écrasante majorité sera fusionnée
 * puis jetée dans la minute qui suit). refset_free ne libère .values que si
 * owns_values — indispensable ici, sous peine de double-free sur les bitmaps
 * du contexte original. */
typedef struct { roaring_bitmap_t *refs; roaring_bitmap_t *values; bool owns_values; } RefSet;
VEC_DEF(RefSet, RefSetVec)

static RefSet refset_create(void) {
    RefSet rs; rs.refs = roaring_bitmap_create(); rs.values = roaring_bitmap_create();
    rs.owns_values = true; return rs;
}
static RefSet refset_create_with_ref(int ref) {
    RefSet rs = refset_create(); roaring_bitmap_add(rs.refs, (uint32_t)ref); return rs;
}
static RefSet refset_create_from_refs(roaring_bitmap_t *refs) {
    RefSet rs; rs.refs = roaring_bitmap_copy(refs); rs.values = roaring_bitmap_create();
    rs.owns_values = true; return rs;
}
static void refset_free(RefSet *rs) {
    roaring_bitmap_free(rs->refs);
    if (rs->owns_values) roaring_bitmap_free(rs->values);
}

static int cmp_refset_card_desc(const void *a, const void *b) {
    return (int)roaring_bitmap_get_cardinality(((RefSet*)b)->values)
         - (int)roaring_bitmap_get_cardinality(((RefSet*)a)->values);
}

/* Hache un roaring par ses elements : FNV-1a suivi de l'avalanche fmix64.
 * Meme construction que hermes_hash_values (hermes.c) : sans le finaliseur,
 * FNV-1a ne diffuse pas vers les bits de poids faible que retient l'indice
 * de seau, et la distribution s'effondre sur des ensembles structures. */
static uint64_t pluton_hash_values(const roaring_bitmap_t *bm) {
    uint64_t h = 1469598103934665603ULL;
    roaring_uint32_iterator_t it;
    roaring_iterator_init((roaring_bitmap_t*)bm, &it);
    while (it.has_value) {
        h = (h ^ (uint64_t)it.current_value) * 1099511628211ULL;
        roaring_uint32_iterator_advance(&it);
    }
    h ^= h >> 33; h *= 0xff51afd7ed558ccdULL;
    h ^= h >> 33; h *= 0xc4ceb9fe1a85ec53ULL;
    h ^= h >> 33;
    return h;
}

/* Identique à hermes_clarify : fusionne les RefSet de mêmes valeurs (en
 * accumulant leurs refs), puis resynchronise setToSynchronize sur les indices
 * clarifiés.
 *
 * Le regroupement se fait par HACHAGE a l'interieur de chaque bloc de
 * cardinalite egale (le tableau est trie decroissant juste avant), au lieu de
 * comparer chaque element a tous ses predecesseurs du bloc puis decaler tout
 * le tableau d'une case a chaque fusion : c'est le meme changement, transpose
 * a l'identique, que celui deja applique a hermes_clarify. Deux defauts
 * corriges d'un coup : comparaison en O(bloc^2), et suppression en O(n) par
 * fusion (jusqu'a O(n^2) au total sur un contexte tres redondant). Deux
 * ensembles egaux ont necessairement le meme hache (aucune fusion manquee) ;
 * l'egalite reste verifiee avant toute fusion (aucune fusion abusive).
 *
 * pluton_build_clarified n'appelle plus cette fonction que sur le second tour
 * de chaque branche (le cote deja petit) : le premier tour, sur le cote
 * LARGE, passe par pluton_clarify_initial ci-dessous, qui evite en plus de
 * materialiser un RefSet par element avant de savoir s'il survit. */
static RefSetVec pluton_clarify(RefSetVec *setToClarify, RefSetVec *setToSynchronize) {
    qsort(setToClarify->data, setToClarify->len, sizeof(RefSet), cmp_refset_card_desc);

    int n = setToClarify->len;
    int *card = (int*)malloc((size_t)(n > 0 ? n : 1) * sizeof(int));
    for (int i = 0; i < n; i++)
        card[i] = (int)roaring_bitmap_get_cardinality(setToClarify->data[i].values);
    unsigned char *dead = (unsigned char*)calloc((size_t)(n > 0 ? n : 1), 1);

    int blockStart = 0;
    while (blockStart < n) {
        int blockEnd = blockStart + 1;
        while (blockEnd < n && card[blockEnd] == card[blockStart]) blockEnd++;
        int m = blockEnd - blockStart;
        if (m > 1) {
            /* Table ouverte a sondage lineaire, taille puissance de deux >= 2m. */
            int cap = 4;
            while (cap < 2 * m) cap <<= 1;
            int *slot = (int*)malloc((size_t)cap * sizeof(int));
            uint64_t *hsl = (uint64_t*)malloc((size_t)cap * sizeof(uint64_t));
            for (int k = 0; k < cap; k++) slot[k] = -1;
            for (int i = blockStart; i < blockEnd; i++) {
                uint64_t h = pluton_hash_values(setToClarify->data[i].values);
                int k = (int)(h & (uint64_t)(cap - 1));
                int merged = 0;
                while (slot[k] >= 0) {
                    if (hsl[k] == h) {
                        if (roaring_bitmap_equals(setToClarify->data[slot[k]].values,
                                                  setToClarify->data[i].values)) {
                            roaring_bitmap_or_inplace(setToClarify->data[slot[k]].refs,
                                                      setToClarify->data[i].refs);
                            refset_free(&setToClarify->data[i]);
                            dead[i] = 1;
                            merged = 1;
                            break;
                        }
                    }
                    k = (k + 1) & (cap - 1);
                }
                if (!merged) { slot[k] = i; hsl[k] = h; }
            }
            free(slot); free(hsl);
        }
        blockStart = blockEnd;
    }

    /* Compaction unique, ordre relatif preserve. */
    int w = 0;
    for (int i = 0; i < n; i++)
        if (!dead[i]) setToClarify->data[w++] = setToClarify->data[i];
    setToClarify->len = w;
    free(card); free(dead);

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

/* ── ce que la mesure du 2026-08-14 (ord10shuttle.slf) a etabli ───────────
 *
 * PLUTON_PROFILE=1 sur ord10shuttle.slf (43500 objets x 88 attributs, 175
 * classes clarifiees, 239 concepts finaux) a montre clarify a ~65 ms sur un
 * total de ~67 ms — la boucle O(K^2) de pluton_compute_order (optimisee plus
 * haut) n'y pesait jamais rien, K=239 etant petit. Emprunter .values au lieu
 * de le copier (RefSet.owns_values ci-dessus) n'a gagne qu'une fraction de ce
 * temps (~65 -> ~55 ms sur un banc synthetique reproduisant la meme forme,
 * bench_pluton_dup.c). Le reste : la boucle qui construisait un RefSet PAR
 * OBJET (43500 fois) materialisait quand meme un roaring_bitmap_t pour .refs
 * — meme une fois .values emprunte — avant de savoir que 43325 de ces objets
 * seraient fusionnes puis liberes dans la minute qui suit par pluton_clarify.
 * Chaque objet jete payait quand meme un roaring_bitmap_create + add, puis un
 * roaring_bitmap_free.
 *
 * pluton_clarify_initial evite cela : elle part directement de `rawValues`
 * (EMPRUNTE au contexte original, jamais copie ni pour .values ni pour
 * .refs) et ne materialise un roaring_bitmap_t pour .refs qu'une fois les
 * groupes d'egalite connus — un par groupe SURVIVANT (175 dans le cas
 * mesure), pose d'un bloc par roaring_bitmap_add_many. Meme principe que la
 * pose d'arete differee de pluton_compute_order : materialiser seulement ce
 * qui doit survivre, une fois qu'on sait quoi.
 *
 * Reservee au premier appel de chaque branche de pluton_build_clarified (le
 * cote LARGE, construit directement depuis ctx->rows/ctx->cols) : c'est le
 * seul endroit ou l'indice ORIGINAL et la valeur de l'unique ref coincident
 * par construction (rawValues[i] vient de ctx->rows[i] ou ctx->cols[i]). Le
 * second appel de chaque branche opere sur un vecteur deja petit (issu du
 * premier) ; pluton_clarify (ci-dessus) y suffit.
 *
 * RESULTAT (meme banc synthetique, 43500x88 -> 175 classes, PLUTON_PROFILE) :
 * clarify passe de ~60 ms a ~15 ms, soit ~4x sur cette seule phase et environ
 * 3,7x sur le total (~63 -> ~16 ms). */
static RefSetVec pluton_clarify_initial(roaring_bitmap_t **rawValues, int n,
                                        RefSetVec *setToSynchronize,
                                        RefSetVec *outClarified) {
    if (n == 0) {
        *outClarified = RefSetVec_new();
        RefSetVec result = RefSetVec_new();
        for (int i = 0; i < setToSynchronize->len; i++)
            RefSetVec_push(&result, refset_create_from_refs(setToSynchronize->data[i].refs));
        return result;
    }

    int *card = (int*)malloc((size_t)n * sizeof(int));
    int maxCard = 0;
    for (int i = 0; i < n; i++) {
        card[i] = (int)roaring_bitmap_get_cardinality(rawValues[i]);
        if (card[i] > maxCard) maxCard = card[i];
    }
    /* Tri par comptage DECROISSANT des INDICES (pas des elements : on ne
     * deplace que des int, jamais de roaring_bitmap_t). Meme idiome que
     * hermes_compute_hasse (hermes.c). */
    int *bucket = (int*)calloc((size_t)maxCard + 2, sizeof(int));
    for (int i = 0; i < n; i++) bucket[maxCard - card[i]]++;
    int acc = 0;
    for (int k = 0; k <= maxCard; k++) { int b = bucket[k]; bucket[k] = acc; acc += b; }
    int *order = (int*)malloc((size_t)n * sizeof(int));
    for (int i = 0; i < n; i++) order[bucket[maxCard - card[i]]++] = i;
    free(bucket);

    /* Regroupement par hachage a l'interieur de chaque bloc de cardinalite
     * egale — meme principe que pluton_clarify, mais on assigne un GROUPE a
     * chaque indice plutot que de fusionner des RefSet deja materialisees. */
    int *groupOf = (int*)malloc((size_t)n * sizeof(int));
    IntVec groupFirst = IntVec_new();   /* groupFirst.data[g] = indice ORIGINAL representant du groupe g */
    int ngroups = 0;

    int blockStart = 0;
    while (blockStart < n) {
        int blockEnd = blockStart + 1;
        while (blockEnd < n && card[order[blockEnd]] == card[order[blockStart]]) blockEnd++;
        int m = blockEnd - blockStart;
        if (m == 1) {
            int idx = order[blockStart];
            groupOf[idx] = ngroups;
            IntVec_push(&groupFirst, idx);
            ngroups++;
        } else {
            int cap = 4;
            while (cap < 2 * m) cap <<= 1;
            int *slot = (int*)malloc((size_t)cap * sizeof(int));
            uint64_t *hsl = (uint64_t*)malloc((size_t)cap * sizeof(uint64_t));
            for (int k = 0; k < cap; k++) slot[k] = -1;
            for (int k = blockStart; k < blockEnd; k++) {
                int idx = order[k];
                uint64_t h = pluton_hash_values(rawValues[idx]);
                int s = (int)(h & (uint64_t)(cap - 1));
                int found = -1;
                while (slot[s] >= 0) {
                    if (hsl[s] == h && roaring_bitmap_equals(rawValues[slot[s]], rawValues[idx])) {
                        found = slot[s];
                        break;
                    }
                    s = (s + 1) & (cap - 1);
                }
                if (found >= 0) {
                    groupOf[idx] = groupOf[found];
                } else {
                    slot[s] = idx; hsl[s] = h;
                    groupOf[idx] = ngroups;
                    IntVec_push(&groupFirst, idx);
                    ngroups++;
                }
            }
            free(slot); free(hsl);
        }
        blockStart = blockEnd;
    }
    free(card); free(order);

    /* Materialisation groupee : compter chaque groupe, remplir un tableau de
     * membres, poser les refs d'un bloc. */
    int *groupLen = (int*)calloc((size_t)ngroups, sizeof(int));
    for (int i = 0; i < n; i++) groupLen[groupOf[i]]++;
    int *groupFillPos = (int*)calloc((size_t)ngroups, sizeof(int));
    uint32_t **groupMembers = (uint32_t**)malloc((size_t)ngroups * sizeof(uint32_t*));
    for (int g = 0; g < ngroups; g++)
        groupMembers[g] = (uint32_t*)malloc((size_t)groupLen[g] * sizeof(uint32_t));
    for (int i = 0; i < n; i++) {
        int g = groupOf[i];
        groupMembers[g][groupFillPos[g]++] = (uint32_t)i;
    }
    free(groupFillPos); free(groupOf);

    RefSetVec clarified = RefSetVec_new();
    for (int g = 0; g < ngroups; g++) {
        RefSet rs;
        rs.refs = roaring_bitmap_create();
        roaring_bitmap_add_many(rs.refs, (size_t)groupLen[g], groupMembers[g]);
        rs.values = rawValues[groupFirst.data[g]];   /* emprunte, jamais copie */
        rs.owns_values = false;
        RefSetVec_push(&clarified, rs);
        free(groupMembers[g]);
    }
    free(groupMembers); free(groupLen);
    IntVec_free(&groupFirst);

    RefSetVec result = RefSetVec_new();
    for (int i = 0; i < setToSynchronize->len; i++)
        RefSetVec_push(&result, refset_create_from_refs(setToSynchronize->data[i].refs));
    for (int i = 0; i < clarified.len; i++) {
        roaring_uint32_iterator_t it;
        roaring_iterator_init(clarified.data[i].values, &it);
        while (it.has_value) {
            int idx = (int)it.current_value;
            if (idx < result.len) roaring_bitmap_add(result.data[idx].values, (uint32_t)i);
            roaring_uint32_iterator_advance(&it);
        }
    }
    *outClarified = clarified;
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
        for (int o = 0; o < ctx->nb_objects; o++)
            RefSetVec_push(&objSets, refset_create_with_ref(o));
        /* côté LARGE (attributs) : pluton_clarify_initial évite de
         * matérialiser un RefSet par attribut avant de savoir s'il survit —
         * voir son commentaire de tête. */
        RefSetVec attrSetsClarified;
        RefSetVec newObj = pluton_clarify_initial(ctx->cols, ctx->nb_attributes, &objSets, &attrSetsClarified);
        for (int i = 0; i < objSets.len; i++) refset_free(&objSets.data[i]); RefSetVec_free(&objSets);
        objSets = newObj;
        RefSetVec newAttr = pluton_clarify(&objSets, &attrSetsClarified);
        for (int i = 0; i < attrSetsClarified.len; i++) refset_free(&attrSetsClarified.data[i]); RefSetVec_free(&attrSetsClarified);
        /* attrSets (déclaré vide en tête de fonction) n'a jamais servi dans
         * cette branche — le côté LARGE passe par pluton_clarify_initial, pas
         * par la boucle de construction habituelle. Sans ce free, son buffer
         * initial (VEC_INIT_CAP) fuit à la réaffectation qui suit. */
        RefSetVec_free(&attrSets);
        attrSets = newAttr;
    } else {
        for (int a = 0; a < ctx->nb_attributes; a++)
            RefSetVec_push(&attrSets, refset_create_with_ref(a));
        /* côté LARGE (objets) : idem, voir pluton_clarify_initial. C'est le
         * cas mesuré sur ord10shuttle.slf (43500 objets -> 175 classes). */
        RefSetVec objSetsClarified;
        RefSetVec newAttr = pluton_clarify_initial(ctx->rows, ctx->nb_objects, &attrSets, &objSetsClarified);
        for (int i = 0; i < attrSets.len; i++) refset_free(&attrSets.data[i]); RefSetVec_free(&attrSets);
        attrSets = newAttr;
        RefSetVec newObj = pluton_clarify(&attrSets, &objSetsClarified);
        for (int i = 0; i < objSetsClarified.len; i++) refset_free(&objSetsClarified.data[i]); RefSetVec_free(&objSetsClarified);
        /* Même raison que le RefSetVec_free(&attrSets) de l'autre branche :
         * objSets (vide, jamais servi ici) fuirait sinon à la réaffectation. */
        RefSetVec_free(&objSets);
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
 * ════════════════════════════════════════════════════════════════════════
 *
 * ── ce que la campagne d'optimisation établit ici ──────────────────────
 *
 * Le test de parenté comparait chaque paire (S,T) via des roaring_bitmap_t
 * directement sur le contexte CLARIFIÉ : roaring_bitmap_is_subset,
 * roaring_bitmap_contains, roaring_bitmap_is_empty, roaring_bitmap_minimum.
 * C'est exactement le défaut que la campagne précédente a corrigé dans
 * hermes_compute_hasse (point 1 de son bloc de commentaire, dans ce même
 * fichier) : le contexte clarifié est petit (nO classes d'objets, nA classes
 * d'attributs, souvent quelques dizaines à quelques centaines), et le test se
 * fait O(K^2) fois sur K concepts.
 *
 * Mesuré ici (bench_pluton.c, hors JNI, contexte synthétique 19020×52,
 * d=0.15, machine de dev — pas ord6magic04, qui n'est pas disponible dans cet
 * environnement, mais même régime que celui documenté pour Hermes) :
 * 10,7 s avant conversion. Deux changements, tous deux déjà validés
 * ailleurs dans ce projet :
 *
 *  1. DENSE. Les lignes/colonnes du contexte clarifié (ctx->rows, ctx->cols)
 *     sont converties une fois en aword[] (bitset.h) avant la boucle O(K^2),
 *     et le test d'inclusion passe de roaring_bitmap_is_subset (recherche de
 *     conteneur + dichotomie) à bs_subset (une boucle sur wa ou wo mots,
 *     souvent UN SEUL mot). Contrairement à Hermes, pluton_compute_linext a
 *     déjà fini de créer TOUS les concepts (et leurs rextents/rintents
 *     finaux) avant que pluton_compute_order ne démarre : les invariants par
 *     concept (obj_idx/attr_idx ci-dessous) se relèvent donc en une seule
 *     passe avant la boucle, plutôt qu'à la création de chacun comme le fait
 *     hermes_compute_hasse.
 *
 *  2. POSE D'ARÊTE DIFFÉRÉE. co_add_edge coûte quatre opérations roaring par
 *     arête (deux ajouts dans parents/children, deux retraits dans
 *     maximals/minimals). Les arêtes sont accumulées dans un miroir local
 *     (childArr), puis insérées d'un bloc par roaring_bitmap_add_many ; les
 *     parents sont obtenus par transposition, maximals/minimals recalculés.
 *     Le parcours des descendants (marquage anti-transitivité) lit désormais
 *     ce même miroir : nécessaire ici, puisque gsh->graph ne reçoit les
 *     arêtes qu'à la toute fin — à la différence de la version à pose
 *     immédiate, où gsh->graph->children[T] était déjà à jour (T est
 *     toujours traité à une itération extérieure strictement antérieure à
 *     celle de S). Le miroir local offre la même garantie, par construction :
 *     childArr[T] est intégralement rempli avant que S ne le relise. */

/* Invariants par concept, relevés une fois pour tous les concepts avant la
 * boucle O(K^2) : la valeur qui caractérise le concept — un objet si son
 * extension réduite est non vide, un attribut sinon, parfois les deux à la
 * fois pour un concept « couplé » (cf. pluton_compute_linext). -1 signale
 * l'absence. */
typedef struct { int obj_idx; int attr_idx; } PlutonConceptInv;

/* Test de parenté T→S, entièrement dense. Reprend exactement les quatre cas
 * de l'ancienne pluton_is_parent_t, mais lit les invariants précalculés
 * plutôt que de relire rextents/rintents et recalculer les minimums à chaque
 * appel. */
static bool pluton_is_parent_t_dense(const PlutonConceptInv *inv,
                                     aword **dense_rows, int wa,
                                     aword **dense_cols, int wo,
                                     int conceptS, int conceptT) {
    const PlutonConceptInv *s = &inv[conceptS];
    const PlutonConceptInv *t = &inv[conceptT];
    if (s->obj_idx >= 0) {
        const aword *fs = dense_rows[s->obj_idx];
        if (t->obj_idx >= 0) {
            return bs_subset(fs, dense_rows[t->obj_idx], wa);   /* ft.containsAll(fs) */
        } else {
            const aword *ft = dense_cols[t->attr_idx];
            for (BS_FOREACH(a, fs, wa)) {
                if (!bs_subset(ft, dense_cols[a], wo)) return false;
            }
            return true;
        }
    } else {
        const aword *gs = dense_cols[s->attr_idx];
        if (t->attr_idx >= 0) {
            return bs_subset(dense_cols[t->attr_idx], gs, wo);  /* gs.containsAll(gt) */
        } else {
            return bs_test(dense_rows[t->obj_idx], s->attr_idx);
        }
    }
}

/* Boucle d'ordre : pour chaque concept de la linext, comparer aux précédents en
 * sautant les descendants déjà marqués (anti-transitivité). Structure calquée
 * sur hermes_compute_hasse ; voir le commentaire ci-dessus pour ce qui a
 * changé. */
static void pluton_compute_order(ConceptOrder *gsh, BinaryContext *ctx, IntVec *linext) {
    int n = gsh->counter;
    if (n <= 0) return;

    /* invariants par concept, une seule passe */
    PlutonConceptInv *inv = (PlutonConceptInv*)malloc((size_t)n * sizeof(PlutonConceptInv));
    for (int c = 0; c < n; c++) {
        inv[c].obj_idx  = roaring_bitmap_is_empty(gsh->rextents[c]) ? -1
                        : (int)roaring_bitmap_minimum(gsh->rextents[c]);
        inv[c].attr_idx = roaring_bitmap_is_empty(gsh->rintents[c]) ? -1
                        : (int)roaring_bitmap_minimum(gsh->rintents[c]);
    }

    /* contexte clarifié en dense, une seule fois */
    int nO = ctx->nb_objects, nA = ctx->nb_attributes;
    int wa = AW_N(nA > 0 ? nA : 1);
    int wo = AW_N(nO > 0 ? nO : 1);
    aword *rowbuf = (aword*)calloc((size_t)(nO > 0 ? nO : 1) * (size_t)wa, sizeof(aword));
    aword *colbuf = (aword*)calloc((size_t)(nA > 0 ? nA : 1) * (size_t)wo, sizeof(aword));
    aword **dense_rows = (aword**)malloc((size_t)(nO > 0 ? nO : 1) * sizeof(aword*));
    aword **dense_cols = (aword**)malloc((size_t)(nA > 0 ? nA : 1) * sizeof(aword*));
    for (int o = 0; o < nO; o++) {
        dense_rows[o] = rowbuf + (size_t)o * wa;
        bs_from_roaring_into(ctx->rows[o], dense_rows[o], wa);
    }
    for (int a = 0; a < nA; a++) {
        dense_cols[a] = colbuf + (size_t)a * wo;
        bs_from_roaring_into(ctx->cols[a], dense_cols[a], wo);
    }

    /* visited plat : accès O(1), au lieu d'un roaring_bitmap martelé O(K^2) fois
     * (c'est exactement le gain HashSet -> boolean[] mesuré côté Java). */
    char *visited = (char*)calloc(n, sizeof(char));
    IntVec stack = IntVec_new();   /* pile de descendance réutilisée */

    /* Miroir local des arêtes enfant(T)->parent(S), cf. commentaire de tête :
     * co_add_edge coûte quatre opérations roaring par arête, différées ici et
     * posées d'un bloc à la fin. */
    int **childArr = (int**)calloc((size_t)n, sizeof(int*));
    int *childLen  = (int*)calloc((size_t)n, sizeof(int));
    int *childCap  = (int*)calloc((size_t)n, sizeof(int));

    for (int i = 0; i < linext->len; i++) {
        int conceptS = linext->data[i];

        for (int j = i - 1; j >= 0; j--) {
            int conceptT = linext->data[j];
            if (visited[conceptT]) {
                visited[conceptT] = 0;
            } else if (pluton_is_parent_t_dense(inv, dense_rows, wa, dense_cols, wo, conceptS, conceptT)) {
                /* L'arête n'est pas posée dans le graphe ici : seulement
                 * enregistrée dans le miroir local (voir commentaire de tête).
                 * Le calcul de l'ordre ne lit que rextents/rintents ; les
                 * extents/intents COMPLETS ne servent qu'au JSON et sont
                 * reconstruits par propagation (pluton_rebuild_full_sets). */
                if (childLen[conceptS] == childCap[conceptS]) {
                    int nc = childCap[conceptS] ? childCap[conceptS] * 2 : 4;
                    childArr[conceptS] = (int*)realloc(childArr[conceptS], (size_t)nc * sizeof(int));
                    childCap[conceptS] = nc;
                }
                childArr[conceptS][childLen[conceptS]++] = conceptT;

                /* Parcours en profondeur des descendants de T, via le miroir
                 * local (childArr[T] est déjà complet : T a fini son propre
                 * tour de boucle externe à une itération strictement
                 * antérieure à celle de S). */
                stack.len = 0;
                {
                    const int *ch = childArr[conceptT];
                    const int cn = childLen[conceptT];
                    for (int k = 0; k < cn; k++) {
                        int child = ch[k];
                        if (!visited[child]) { visited[child] = 1; IntVec_push(&stack, child); }
                    }
                }
                int head = 0;
                while (head < stack.len) {
                    int cur = stack.data[head++];
                    const int *ch = childArr[cur];
                    const int cn = childLen[cur];
                    for (int k = 0; k < cn; k++) {
                        int child = ch[k];
                        if (!visited[child]) { visited[child] = 1; IntVec_push(&stack, child); }
                    }
                }
            }
        }
    }

    /* Pose groupée des arêtes (même construction que hermes_compute_hasse).
     * maximals/minimals recalculés plutôt que décrémentés arête par arête :
     * co_add_concept les y a tous placés à la création. */
    {
        int *parentCount = (int*)calloc((size_t)n, sizeof(int));
        for (int c = 0; c < n; c++)
            for (int k = 0; k < childLen[c]; k++) parentCount[childArr[c][k]]++;
        int **parentArr = (int**)malloc((size_t)n * sizeof(int*));
        int *parentFill = (int*)calloc((size_t)n, sizeof(int));
        for (int c = 0; c < n; c++)
            parentArr[c] = parentCount[c] ? (int*)malloc((size_t)parentCount[c] * sizeof(int)) : NULL;
        for (int c = 0; c < n; c++)
            for (int k = 0; k < childLen[c]; k++) {
                int ch = childArr[c][k];
                parentArr[ch][parentFill[ch]++] = c;
            }
        for (int c = 0; c < n; c++) {
            if (childLen[c] > 0) {
                roaring_bitmap_add_many(gsh->graph->children[c], (size_t)childLen[c],
                                        (const uint32_t*)childArr[c]);
                roaring_bitmap_remove(gsh->minimals, (uint32_t)c);
            }
            if (parentCount[c] > 0) {
                roaring_bitmap_add_many(gsh->graph->parents[c], (size_t)parentCount[c],
                                        (const uint32_t*)parentArr[c]);
                roaring_bitmap_remove(gsh->maximals, (uint32_t)c);
            }
            free(parentArr[c]);
        }
        free(parentArr); free(parentCount); free(parentFill);
    }

    for (int i = 0; i < n; i++) free(childArr[i]);
    free(childArr); free(childLen); free(childCap);
    IntVec_free(&stack);
    free(visited);
    free(dense_rows); free(dense_cols); free(rowbuf); free(colbuf);
    free(inv);
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

/* Chronométrage par phase, INERTE par défaut (un seul getenv, un seul appel
 * horloge par phase — négligeable). N'imprime que si PLUTON_PROFILE est
 * positionnée dans l'environnement, et sur stderr : ne perturbe ni le calcul
 * ni la sortie standard qu'un banc externe pourrait lire (aoc_regress,
 * AocBench, ...).
 *
 * Raison d'être : la campagne d'optimisation du 2026-08-14 (dense + arêtes
 * différées) a ramené le calcul de l'ordre — O(K^2) sur K concepts — à un
 * coût négligeable. Sur un contexte qui clarifie/ordonne peu (K grand), le
 * gain est net. Sur un contexte qui clarifie BEAUCOUP en peu de concepts
 * (K petit) mais laisse un contexte clarifié encore volumineux, la partie
 * O(K^2) n'a jamais été le goulot — et la partie qui reste, elle, n'a pas
 * reçu le même traitement (maxmod_partition_oa/ao et tom_thumb sont encore
 * en roaring pur sur le contexte clarifié). Plutôt que de deviner laquelle
 * des trois phases domine, on la mesure — même leçon que celle tirée dans
 * hermes.c : « chronométrer séparément les sites imbriqués, et se méfier
 * d'un correctif qui ne rend rien ». */
#include <time.h>
static double pluton_now_s(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (double)ts.tv_sec + (double)ts.tv_nsec * 1e-9;
}

/* Construit l'AOC-poset Pluton et renvoie le ConceptOrder, dont les
 * rextents/rintents sont exprimés dans le contexte ORIGINAL (ctx). Le graphe
 * est déjà transitivement réduit (propriété de l'algorithme). */
static ConceptOrder *build_pluton(BinaryContext *ctx) {
    const char *prof = getenv("PLUTON_PROFILE");
    double t0 = prof ? pluton_now_s() : 0.0;

    PlutonClarif pc = pluton_build_clarified(ctx);
    double t1 = prof ? pluton_now_s() : 0.0;

    /* Le ConceptOrder pointe le contexte original : c'est l'espace dans lequel
     * les rextents/rintents seront exprimés après substitution, et celui que
     * co_to_flat_array / le writer utilisent. Pendant le calcul, isParentOf et
     * les partitions lisent le contexte CLARIFIÉ (pc.clarified) via les
     * fonctions ci-dessus, jamais gsh->ctx. */
    ConceptOrder *gsh = co_create(ctx);

    IntVec linext = pluton_compute_linext(gsh, pc.clarified);
    double t2 = prof ? pluton_now_s() : 0.0;

    pluton_compute_order(gsh, pc.clarified, &linext);
    IntVec_free(&linext);
    double t3 = prof ? pluton_now_s() : 0.0;

    /* Relevées avant free : pluton_clarif_free libère pc.clarified. */
    int clarNO = pc.clarified->nb_objects, clarNA = pc.clarified->nb_attributes;

    pluton_substitute_reduced(gsh, &pc);
    pluton_clarif_free(&pc);
    double t4 = prof ? pluton_now_s() : 0.0;

    if (prof) {
        fprintf(stderr,
                "[pluton] %dx%d -> clarifie %dx%d -> %d concepts : "
                "clarify=%.1fms linext(maxmod+tomThumb)=%.1fms order=%.1fms substitute=%.1fms total=%.1fms\n",
                ctx->nb_objects, ctx->nb_attributes,
                clarNO, clarNA, gsh->counter,
                (t1 - t0) * 1000.0, (t2 - t1) * 1000.0, (t3 - t2) * 1000.0, (t4 - t3) * 1000.0,
                (t4 - t0) * 1000.0);
        /* Sans ce flush : sous Windows/MinGW, stderr redirigé vers un fichier
         * (donc plus un terminal) passe souvent en mode pleinement bufferisé.
         * Le volume ici (une ligne courte par appel) ne remplit jamais le
         * tampon tout seul, et rien ne garantit qu'il soit vidé avant la fin
         * du processus — la DLL a son propre CRT, distinct de celui de
         * java.exe. Constaté : sans ce flush, pluton_profile.log restait vide
         * de toute ligne [pluton] malgre PLUTON_PROFILE positionnee. */
        fflush(stderr);
    }
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
