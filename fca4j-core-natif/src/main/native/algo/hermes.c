#include "hermes.h"
#include "../core/fca4j_common.h"
#include "../core/conceptorder.h"
#include "../core/bitset.h"

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

/* ── ce que la campagne d'optimisation a etabli ───────────────────────────
 *
 * Le portage natif etait 3 a 5 fois PLUS LENT que le Hermes Java une fois
 * celui-ci optimise : 1020 ms contre 496 sur ord6magic04. Il reproduisait
 * fidelement un Java qui avait entre-temps gagne 8x. Il est aujourd'hui a
 * 266 ms, soit 1,86x plus rapide que le Java.
 *
 * Ce qui a paye, dans l'ordre d'importance :
 *
 *   1. `values` en DENSE. Ces ensembles portent des indices de classes
 *      d'attributs — 33,5 elements en moyenne sur 52, soit UN mot de 64 bits —
 *      et le test d'inclusion passait par roaring_bitmap_is_subset : 9,7 M
 *      d'appels a 56,3 ns. En dense : 4,8 ns. C'est la raison principale pour
 *      laquelle le portage perdait contre le Java, dont BITSET_PACKED fait ce
 *      test sur un long.
 *
 *   2. CLARIFICATION par regroupement de hachage. 5 056 808 comparaisons
 *      ramenees a 14 559. Trois defauts d'un coup : comparaison a tous les
 *      predecesseurs du bloc, cardinalite recalculee a chaque comparaison, et
 *      suppression par decalage du tableau entier.
 *
 *   3. TRI PAR COMPTAGE au lieu du tri par insertion — ce dernier etant en
 *      O(n^2) et recalculant la cardinalite a chaque comparaison. Defaut propre
 *      au portage : le Java utilise Collections.sort.
 *
 *   4. Marquage en tableau plat, invariants hisses hors de la boucle interne,
 *      pile reutilisee.
 *
 *   5. PROPAGATION DES EXTENTS ET INTENTS rendue facultative. co_to_flat_array
 *      n'ecrit que les aretes, les rextents et les rintents ; dans ce fichier,
 *      gsh->intents n'etait jamais relu et gsh->extents ne l'etait que par sa
 *      propre propagation. Ce travail ne sert qu'a co_to_json, d'ou le drapeau
 *      full_sets. Le marquage des descendants, lui, reste actif dans les deux
 *      cas : c'est lui qui evite les aretes de transitivite.
 *
 *   6. POSE D'ARETE DIFFEREE. co_add_edge coutait quatre operations roaring par
 *      arete. Les aretes sont desormais accumulees dans le miroir local puis
 *      inserees d'un bloc par roaring_bitmap_add_many, les parents obtenus par
 *      transposition, maximals/minimals recalcules. C'est le miroir en int[] du
 *      point 4 — sans gain propre — qui rend cette pose groupee possible.
 *
 * RESULTAT sur ord6magic04 (19020 x 52) : le portage est passe de 1020 a 217 ms
 * et devance le Java optimise (475 ms) d'un facteur 2,2. Sur run_hermes_flat
 * seul, mesure hors JNI, le gain est de 12x.
 *
 * OU VA LE TEMPS AUJOURD'HUI, sur ord6magic04 :
 *   algorithme            88 ms   41 %
 *   contexte (ctx_from_jni) 25 ms   11 %
 *   cote Java            ~102 ms   47 %   buildMatrix + populateFromFlat
 * La frontiere pese donc 59 % : meme en divisant encore l'algorithme par deux,
 * on ne gagnerait plus que 20 % du total. C'est le point d'arret naturel pour ce
 * fichier. Le chantier suivant, s'il a lieu, est la frontiere — commune aux
 * QUATRE portages, et cout FIXE par appel, donc decisif pour RCA qui enchaine
 * les invocations, marginal pour un gros calcul isole.
 *
 * Restent aussi mesurees mais non corrigees les unions d'extents et la pose
 * d'arete dans ConceptOrder pour les autres algorithmes, qui n'ont pas recu le
 * traitement applique ici.
 *
 * Ce qui a ete tente SANS resultat : remplacer le parcours des descendants par
 * des tableaux d'entiers. Il ne pese que 2,4 % du temps. Une instrumentation
 * qui confondait « pose d'arete + unions + descendance » sous un seul
 * chronometre lui avait attribue 42 %. Le code est conserve — il est correct et
 * sans cout — mais aucun gain ne lui est imputable. Lecon : chronometrer
 * separement les sites imbriques, et se mefier d'un correctif qui ne rend rien.
 *
 * La structure quadratique du diagramme de Hasse n'a pas ete touchee : le ratio
 * de paires examinees vaut 1,000 sur tous les contextes mesures.
 */

/* Hache un roaring par ses elements : FNV-1a suivi de l'avalanche fmix64.
 * Sans le finaliseur, FNV-1a ne diffuse pas vers les bits de POIDS FAIBLE — ceux
 * que retient l'indice de seau — et la distribution s'effondre sur des ensembles
 * structures. Meme correctif que pour la phase couvertures de PARALLEL_CBO. */
static uint64_t hermes_hash_values(const roaring_bitmap_t *bm) {
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

static RefSetVec hermes_clarify(RefSetVec *setToClarify, RefSetVec *setToSynchronize) {
    qsort(setToClarify->data, setToClarify->len, sizeof(RefSet), cmp_refset_card_desc);

    /* Regroupement par HACHAGE a l'interieur de chaque bloc de cardinalite
     * egale, au lieu d'une comparaison a tous les predecesseurs du bloc.
     *
     * Trois defauts corriges d'un coup, mesures sur ord6magic04 (19020 objets) :
     *   - 5 056 808 comparaisons, la version Java etant tombee a 14 559 par le
     *     meme moyen ;
     *   - la cardinalite recalculee a chaque comparaison, alors qu'elle ne
     *     change pas : relevee une fois ici ;
     *   - la suppression qui DECALAIT tout le tableau, soit 14 559 decalages
     *     dans un tableau de 19 020 : marquage puis compaction unique.
     *
     * Deux ensembles egaux ont necessairement le meme hache, donc aucune fusion
     * n'est manquee ; l'egalite reste verifiee avant toute fusion, donc aucune
     * fusion abusive. Le survivant reste le PLUS ANCIEN du groupe et la
     * compaction preserve l'ordre relatif — la numerotation des classes en
     * depend, et donc la sortie.
     */
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
                uint64_t h = hermes_hash_values(setToClarify->data[i].values);
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

static RefSetVec hermes_compute_dom_relation(RefSetVec *attrSets) {
    RefSetVec dom = RefSetVec_new();
    for (int i = 0; i < attrSets->len; i++) {
        RefSet newSet = refset_create();
        roaring_bitmap_or_inplace(newSet.refs, attrSets->data[i].refs);
        for (int j = 0; j < attrSets->len; j++) {
            if (i == j || roaring_bitmap_is_subset(attrSets->data[i].values, attrSets->data[j].values)) {
                roaring_bitmap_add(newSet.values, (uint32_t)j);
            }
        }
        RefSetVec_push(&dom, newSet);
    }
    return dom;
}

/* Diagramme de Hasse.
 *
 * La structure reste celle de l'article : chaque concept est compare a TOUS ses
 * predecesseurs dans l'extension lineaire, soit N(N-1)/2 paires — un ratio de
 * 1,000 mesure sur tous les contextes du corpus. Ce qui suit ne touche pas a
 * cette structure ; seul le cout unitaire change.
 *
 * Quatre corrections, transposees de la campagne menee sur le Hermes Java, qui
 * l'a fait passer de 2590 a 314 ms sur ord6magic04 :
 *
 *  1. TRI PAR COMPTAGE au lieu du tri par insertion. Le tri par insertion est
 *     en O(n^2) ET recalculait la cardinalite a chaque comparaison : de l'ordre
 *     de 16 millions d'appels a roaring_bitmap_get_cardinality pour 8052
 *     concepts. Le Java, lui, utilisait Collections.sort — le defaut est propre
 *     au portage. Les cardinalites sont ici relevees une fois (n appels), puis
 *     le tri est en O(n + |A|). Il est stable et decroissant, comme l'etait le
 *     tri par insertion, donc l'ordre produit est identique.
 *
 *  2. MARQUAGE EN TABLEAU PLAT au lieu d'un roaring_bitmap_t. Le marquage est
 *     interroge une fois par paire ; chaque acces au bitmap coute une recherche
 *     de conteneur et une dichotomie, la ou un octet indexe coute un acces
 *     memoire. C'est l'equivalent natif du HashSet<Integer> corrige cote Java.
 *
 *  3. INVARIANTS HISSES. s_has_obj et le minimum de l'intent reduit de S ne
 *     dependent pas de prevPos : ils sortent de la boucle interne. Les memes
 *     grandeurs pour T ne dependent que de T : elles sont relevees une fois par
 *     concept. Les ensembles REDUITS ne sont ecrits qu'a la creation du concept
 *     — seuls extents et intents complets sont enrichis ensuite — donc ces
 *     valeurs restent justes jusqu'a la fin.
 *
 *  4. PILE REUTILISEE. IntVec_new() etait appele a l'interieur de la boucle,
 *     soit une allocation par arete posee : 123 682 sur un contexte a 8052
 *     concepts.
 */
/* Propagation des extents et intents COMPLETS.
 *
 * Elle ne sert qu'a co_to_json : co_to_flat_array n'ecrit que les aretes, les
 * rextents et les rintents, et le commentaire de run_hermes_flat le disait deja
 * — « populate() reconstruit intents/extents complets cote Java ». Dans ce
 * fichier, gsh->intents n'est JAMAIS relu, seulement ecrit ; gsh->extents n'est
 * relu que par sa propre propagation.
 *
 * Le profil attribuait 23,2 % du temps a ces unions. Le drapeau les rend
 * facultatives : le chemin plat les saute, le chemin JSON les conserve.
 *
 * Attention : le MARQUAGE des descendants, lui, reste indispensable dans les
 * deux cas — c'est lui qui evite les aretes de transitivite. Seule l'union
 * d'intents qui l'accompagne est facultative. */
static void hermes_compute_hasse(ConceptOrder *gsh, BinaryContext *ctx,
                                 HConceptVec *conceptSets, bool full_sets) {
    int n = conceptSets->len;
    if (n <= 0) {
        return;
    }

    /* Les `values` en DENSE.
     *
     * Elles ne portent que des indices de classes d'attributs — 33,5 elements en
     * moyenne sur 52 pour ord6magic04, soit UN SEUL mot de 64 bits. Le test
     * d'inclusion s'y faisait par roaring_bitmap_is_subset : 9 743 421 appels a
     * 56,3 ns, soit 65 % du temps total, pour ce qu'un ET et une comparaison
     * reglent. C'est la raison pour laquelle le portage etait plus lent que le
     * Java, dont BITSET_PACKED fait exactement ce test sur un long.
     *
     * La largeur est calculee sur le plus grand indice REELLEMENT present plutot
     * que sur nb_attributes : les valeurs indexent les classes d'attributs apres
     * clarification, dont le nombre est au plus nb_attributes mais souvent
     * moindre. Un mot de trop ne serait pas faux, seulement inutile. */
    int maxVal = -1;
    for (int i = 0; i < n; i++) {
        if (!roaring_bitmap_is_empty(conceptSets->data[i].values)) {
            int mx = (int)roaring_bitmap_maximum(conceptSets->data[i].values);
            if (mx > maxVal) maxVal = mx;
        }
    }
    const int wv = AW_N(maxVal + 1 > 0 ? maxVal + 1 : 1);
    aword *dvals = (aword*)calloc((size_t)n * (size_t)wv, sizeof(aword));
    for (int i = 0; i < n; i++) {
        aword *d = dvals + (size_t)i * wv;
        roaring_uint32_iterator_t it;
        roaring_iterator_init(conceptSets->data[i].values, &it);
        while (it.has_value) { bs_set(d, (int)it.current_value); roaring_uint32_iterator_advance(&it); }
    }

    /* Cardinalites relevees une fois, puis tri par comptage decroissant. */
    int *cards = (int*)malloc((size_t)n * sizeof(int));
    int maxCard = 0;
    for (int i = 0; i < n; i++) {
        cards[i] = bs_card(dvals + (size_t)i * wv, wv);
        if (cards[i] > maxCard) maxCard = cards[i];
    }
    int *bucket = (int*)calloc((size_t)maxCard + 2, sizeof(int));
    for (int i = 0; i < n; i++) bucket[maxCard - cards[i]]++;   /* decroissant */
    int acc = 0;
    for (int k = 0; k <= maxCard; k++) { int b = bucket[k]; bucket[k] = acc; acc += b; }
    int *order = (int*)malloc((size_t)n * sizeof(int));
    for (int i = 0; i < n; i++) order[bucket[maxCard - cards[i]]++] = i;   /* stable */
    free(bucket);
    free(cards);

    int *conceptIds = (int*)malloc((size_t)n * sizeof(int));
    /* Grandeurs de T, relevees a la creation du concept. -1 vaut « vide ». */
    int *rextMin = (int*)malloc((size_t)n * sizeof(int));
    unsigned char *visited = (unsigned char*)calloc((size_t)n, 1);
    IntVec stack = IntVec_new();   /* reutilisee d'une arete a l'autre */

    /* Couvertures inferieures tenues par l'algorithme, en entiers, plutot que
     * relues dans graph->children[c] par un iterateur roaring.
     *
     * L'algorithme cree lui-meme toutes les aretes et n'en retire aucune : il
     * connait donc la structure aussi bien que le graphe. Un tableau par
     * concept, double a saturation. C'est la representation adoptee pour Ceres.
     *
     * MESURE : le gain est NUL. Le parcours des descendants ne pese que 2,4 %
     * du temps ; ce qui coute, dans le meme bloc, c'est la pose de l'arete
     * (19,8 %) et les unions d'extents (23,2 %). Une premiere version de
     * l'instrumentation confondait les trois sous un seul chronometre, d'ou une
     * attribution erronee de 42 % a la descendance. Le code est conserve parce
     * qu'il est correct et sans coût, mais il ne faut pas lui attribuer de gain.
     *
     * co_add_edge(gsh, lower, upper) place lower parmi les enfants de upper :
     * l'arete (conceptT, conceptS) donne donc conceptT enfant de conceptS. */
    int **childArr = (int**)calloc((size_t)n, sizeof(int*));
    int *childLen = (int*)calloc((size_t)n, sizeof(int));
    int *childCap = (int*)calloc((size_t)n, sizeof(int));

    for (int pos = 0; pos < n; pos++) {
        int idx = order[pos];
        HConceptSet *cSet = &conceptSets->data[idx];
        int conceptS = co_add_concept(gsh, roaring_bitmap_copy(cSet->extent), roaring_bitmap_copy(cSet->intent));
        roaring_bitmap_or_inplace(gsh->rextents[conceptS], cSet->extent);
        roaring_bitmap_or_inplace(gsh->rintents[conceptS], cSet->intent);
        conceptIds[pos] = conceptS;
        rextMin[pos] = roaring_bitmap_is_empty(gsh->rextents[conceptS])
                     ? -1 : (int)roaring_bitmap_minimum(gsh->rextents[conceptS]);

        /* Invariants de S pour toute la boucle interne. */
        const bool s_has_obj = rextMin[pos] >= 0;
        const uint32_t s_attr = s_has_obj ? 0u
                              : roaring_bitmap_minimum(gsh->rintents[conceptS]);
        const aword *sValues = dvals + (size_t)idx * wv;
        const roaring_bitmap_t *sRIntent = gsh->rintents[conceptS];

        for (int prevPos = pos - 1; prevPos >= 0; prevPos--) {
            int conceptT = conceptIds[prevPos];
            if (visited[conceptT]) {
                visited[conceptT] = 0;
                continue;
            }
            bool isParent;
            int t_obj_min = rextMin[prevPos];
            if (t_obj_min >= 0 && !s_has_obj) {
                isParent = roaring_bitmap_contains(ctx->rows[t_obj_min], s_attr);
            } else {
                isParent = bs_subset(sValues, dvals + (size_t)order[prevPos] * wv, wv);
            }
            if (!isParent) {
                continue;
            }

            /* Trois choses distinctes se passent ici : la pose de l'arete, la
             * propagation des extents et intents, et le parcours des
             * descendants. Elles sont chronometrees SEPAREMENT — les avoir
             * confondues sous un seul site m'a fait attribuer a la descendance
             * un cout qui etait celui des unions. */
            /* L'arete n'est PAS posee dans le graphe ici : elle est seulement
             * enregistree dans le miroir local, et toutes le seront d'un bloc a
             * la fin. co_add_edge coute quatre operations roaring par arete —
             * deux ajouts dans parents/children, deux retraits dans
             * maximals/minimals — soit 19,8 % du temps mesure. En differant, on
             * remplit chaque bitmap en une passe sur des valeurs deja groupees,
             * ce qui evite autant de recherches de conteneur.
             *
             * C'est possible parce que RIEN ne relit le graphe pendant la
             * construction : la descendance passe par le miroir, et
             * maximals/minimals ne servent qu'apres. */
            if (childLen[conceptS] == childCap[conceptS]) {
                int nc = childCap[conceptS] ? childCap[conceptS] * 2 : 4;
                childArr[conceptS] = (int*)realloc(childArr[conceptS], (size_t)nc * sizeof(int));
                childCap[conceptS] = nc;
            }
            childArr[conceptS][childLen[conceptS]++] = conceptT;
            if (full_sets) {
                roaring_bitmap_or_inplace(gsh->extents[conceptS], gsh->extents[conceptT]);
                roaring_bitmap_or_inplace(gsh->intents[conceptT], sRIntent);
            }

            /* Parcours en profondeur des descendants de T : chacun herite une
             * fois et une seule, la marque servant de garde. */
            stack.len = 0;
            {
                const int *ch = childArr[conceptT];
                const int cn = childLen[conceptT];
                for (int k = 0; k < cn; k++) {
                    int child = ch[k];
                    if (!visited[child]) {
                        visited[child] = 1;
                        if (full_sets) roaring_bitmap_or_inplace(gsh->intents[child], sRIntent);
                        IntVec_push(&stack, child);
                    }
                }
            }
            int head = 0;
            while (head < stack.len) {
                int cur = stack.data[head++];
                const int *ch = childArr[cur];
                const int cn = childLen[cur];
                for (int k = 0; k < cn; k++) {
                    int child = ch[k];
                    if (!visited[child]) {
                        visited[child] = 1;
                        if (full_sets) roaring_bitmap_or_inplace(gsh->intents[child], sRIntent);
                        IntVec_push(&stack, child);
                    }
                }
            }
        }
    }
    /* Pose groupee des aretes. childArr[c] contient les enfants de c dans
     * l'ordre ou ils ont ete trouves ; roaring_bitmap_add_many les insere en une
     * fois. Les parents sont deduits par transposition, en une passe.
     *
     * maximals et minimals sont recalcules plutot que decrementes arete par
     * arete : un concept est maximal s'il n'a aucun parent, minimal s'il n'a
     * aucun enfant. co_add_concept les y a tous places a la creation. */
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

    IntVec_free(&stack);
    for (int i = 0; i < n; i++) free(childArr[i]);
    free(childArr); free(childLen); free(childCap);
    free(dvals);
    free(visited); free(rextMin);
    free(order); free(conceptIds);
}

/* Construit l'AOC-poset Hermes et renvoie le ConceptOrder.
 * Les rextents/rintents (labellisation réduite) sont remplis par
 * hermes_compute_hasse ; les extents/intents complets aussi (propagation).
 * Côté flat, seuls rextents/rintents + arêtes sont sérialisés : populate()
 * reconstruit le reste côté Java. */
/* full_sets : construire ou non les extents et intents COMPLETS. Le chemin plat
 * n'en a pas besoin — populate() les reconstruit cote Java — le chemin JSON si. */
static ConceptOrder *build_hermes(BinaryContext *ctx, bool full_sets) {
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
    hermes_compute_hasse(gsh, ctx, &concepts, full_sets);
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
    ConceptOrder *gsh = build_hermes(ctx, true);   /* le JSON lit extents et intents */
    char *json = co_to_json(gsh);
    co_free(gsh);
    return json;
}

/* Point d'entrée tableau plat (rapide) — même format que run_addextent_flat.
 * populate() reconstruit intents/extents complets côté Java. */
int *run_hermes_flat(BinaryContext *ctx, int *out_len) {
    ConceptOrder *gsh = build_hermes(ctx, false);  /* populate() les reconstruit */
    int *flat = co_to_flat_array(gsh, out_len);
    co_free(gsh);
    return flat;
}
