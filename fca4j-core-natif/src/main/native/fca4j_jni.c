/*
 * fca4j_jni.c — Points d'entrée JNI uniquement
 *
 * Ce fichier ne contient AUCUNE logique algorithmique.
 * Chaque fonction JNI :
 *   1. Construit un BinaryContext depuis la matrice Java
 *   2. Appelle l'impl C correspondante
 *   3. Retourne le résultat JSON à Java
 *   4. Libère les ressources C
 *
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 */

#include <jni.h>
#include <stdlib.h>
#include "core/context.h"
#include "algo/dbasis.h"
#include "algo/hermes.h"
#include "algo/lincbo.h"
#include "algo/lincbo_pruning.h"
#include "algo/addextent.h"
#include "algo/latticecbo.h"
#include "algo/pluton.h"
#include "algo/ares.h"
#include "algo/ceres.h"

/* ── Instrumentation de la frontière JNI ─────────────────────────────────
 *
 * La comparaison C/Java de Ceres a montré un profil inattendu : le C gagne 1,7x
 * sur chess (0,24 M de cellules) mais PERD sur ord10shuttle (3,83 M de
 * cellules), alors que ce dernier ne produit que 239 concepts. Le classement
 * suit la taille de la matrice, pas la difficulté du problème — ce qui désigne
 * la préparation des données plutôt que l'algorithme.
 *
 * Trois postes sont donc chronométrés séparément :
 *
 *   ctx     construction du BinaryContext depuis la matrice d'octets, soit
 *           |G| + |A| bitmaps roaring créés puis remplis cellule par cellule ;
 *   algo    l'algorithme lui-même ;
 *   total   la traversée complète, pour voir ce qui reste ailleurs.
 *
 * Actif seulement si FCA4J_PROFILE vaut 1, comme le reste des instrumentations
 * du projet. Désactivé, il n'y a qu'une lecture de variable statique par appel.
 *
 * Cette instrumentation sert aussi Ares et Pluton, qui empruntent le même
 * chemin et ont donc la même dette éventuelle.
 */

#include <stdio.h>
#include <time.h>
#ifdef _WIN32
  #include <windows.h>   /* QueryPerformanceCounter */
#endif

static int jni_profile_state = -1;   /* -1 = pas encore consulté */

static int jni_profile(void) {
    if (jni_profile_state < 0) {
        const char *e = getenv("FCA4J_PROFILE");
        jni_profile_state = (e && e[0] == '1' && e[1] == '\0') ? 1 : 0;
    }
    return jni_profile_state;
}

/* Horloge murale, en millisecondes. Pas clock() : il mesure le temps CPU cumulé
 * de tous les threads, ce qui n'a aucun sens pour les algorithmes parallèles.
 *
 * Hors Windows, timespec_get plutôt que clock_gettime(CLOCK_MONOTONIC) : ce
 * dernier exige une macro de test POSIX que -std=c11 désactive, et les en-têtes
 * libc sont déjà figés quand fca4j_common.h définit _GNU_SOURCE. timespec_get
 * est du C11 standard, donc disponible sans condition. Il suit l'horloge murale
 * et non une horloge monotone, ce qui est sans importance ici : on mesure des
 * intervalles de quelques millisecondes au sein d'un même appel. */
static double jni_now_ms(void) {
#ifdef _WIN32
    LARGE_INTEGER f, t;
    QueryPerformanceFrequency(&f);
    QueryPerformanceCounter(&t);
    return (double)t.QuadPart * 1000.0 / (double)f.QuadPart;
#else
    struct timespec ts;
    timespec_get(&ts, TIME_UTC);
    return (double)ts.tv_sec * 1000.0 + (double)ts.tv_nsec / 1e6;
#endif
}

static void jni_report(const char *algo, int nb_obj, int nb_attr,
                       double t_ctx, double t_algo, double t_total) {
    fprintf(stderr,
            "[jni] %-8s %6d x %-5d cellules %8.2f M | ctx %8.2f ms  algo %8.2f ms"
            "  reste %6.2f ms  total %8.2f ms\n",
            algo, nb_obj, nb_attr,
            (double)nb_obj * (double)nb_attr / 1e6,
            t_ctx, t_algo, t_total - t_ctx - t_algo, t_total);
    fflush(stderr);
}

/* ── Utilitaire : construction BinaryContext depuis paramètres JNI ── */

static BinaryContext *ctx_from_jni(JNIEnv *env,
                                    jint nObjects, jint nAttributes,
                                    jbyteArray jmatrix,
                                    jobjectArray jattrNames) {
    int nb_obj  = (int)nObjects;
    int nb_attr = (int)nAttributes;

    jbyte *matrix = (*env)->GetByteArrayElements(env, jmatrix, NULL);
    BinaryContext *ctx = ctx_from_matrix(nb_obj, nb_attr, matrix, "");
    (*env)->ReleaseByteArrayElements(env, jmatrix, matrix, JNI_ABORT);

    /* Noms d'attributs (optionnels) */
    if (jattrNames != NULL) {
        int nnames = (int)(*env)->GetArrayLength(env, jattrNames);
        for (int a = 0; a < nnames && a < nb_attr; a++) {
            jstring jname = (jstring)(*env)->GetObjectArrayElement(env, jattrNames, a);
            if (jname) {
                const char *name = (*env)->GetStringUTFChars(env, jname, NULL);
                ctx_add_attr_name(ctx, name);
                (*env)->ReleaseStringUTFChars(env, jname, name);
                (*env)->DeleteLocalRef(env, jname);
            }
        }
    }
    return ctx;
}

/* ── DBasis ─────────────────────────────────────────────────────────── */

JNIEXPORT jstring JNICALL
Java_fr_lirmm_fca4j_core_natif_NativeBridge_runDbasis(
        JNIEnv *env, jclass clazz,
        jint nObjects, jint nAttributes,
        jbyteArray jmatrix,
        jobjectArray jattrNames,
        jint minSupport,
        jint maxThreads) {

    BinaryContext *ctx = ctx_from_jni(env, nObjects, nAttributes, jmatrix, jattrNames);
    char *json = run_dbasis_impl(ctx, (int)minSupport, (int)maxThreads);
    ctx_free(ctx);

    jstring result = (*env)->NewStringUTF(env, json);
    free(json);
    return result;
}
/*
 * runDbasisFlat — variante rapide renvoyant un int[] plat (indices, aucun nom).
 */
JNIEXPORT jintArray JNICALL
Java_fr_lirmm_fca4j_core_natif_NativeBridge_runDbasisFlat(
        JNIEnv *env, jclass clazz,
        jint nObjects, jint nAttributes,
        jbyteArray jmatrix,
        jint minSupport,
        jint maxThreads) {

    BinaryContext *ctx = ctx_from_jni(env, nObjects, nAttributes, jmatrix, NULL);

    int len = 0;
    int *flat = run_dbasis_flat(ctx, (int)minSupport, (int)maxThreads, &len);
    ctx_free(ctx);

    if (flat == NULL || len == 0) {
        if (flat) free(flat);
        return (*env)->NewIntArray(env, 0);
    }

    jintArray result = (*env)->NewIntArray(env, len);
    if (result != NULL)
        (*env)->SetIntArrayRegion(env, result, 0, len, (jint*)flat);
    free(flat);
    return result;
}
/* ── Hermes ──────────────────────────────────────────────────────────── */

JNIEXPORT jstring JNICALL
Java_fr_lirmm_fca4j_core_natif_NativeBridge_runHermes(
        JNIEnv *env, jclass clazz,
        jint nObjects, jint nAttributes,
        jbyteArray jmatrix,
        jobjectArray jattrNames) {

    BinaryContext *ctx = ctx_from_jni(env, nObjects, nAttributes, jmatrix, jattrNames);
    char *json = run_hermes_impl(ctx);
    ctx_free(ctx);

    jstring result = (*env)->NewStringUTF(env, json);
    free(json);
    return result;
}

/*
 * runHermesFlat — variante rapide renvoyant un int[] plat (même format que
 * runAddExtentFlat). Pas de noms d'attributs : le tableau ne contient que
 * des indices.
 */
JNIEXPORT jintArray JNICALL
Java_fr_lirmm_fca4j_core_natif_NativeBridge_runHermesFlat(
        JNIEnv *env, jclass clazz,
        jint nObjects, jint nAttributes,
        jbyteArray jmatrix) {

    /* Profil de frontiere. Sur ord6magic04, l'algorithme mesure 155 ms alors que
     * l'appel complet en prend 293 : il faut savoir ou passent les 137 ms
     * restantes avant de corriger quoi que ce soit. Meme instrumentation que
     * pour Ceres et Ares, inerte sans FCA4J_PROFILE=1. */
    const int prof = jni_profile();
    const double t0 = prof ? jni_now_ms() : 0.0;
    BinaryContext *ctx = ctx_from_jni(env, nObjects, nAttributes, jmatrix, NULL);
    const double t1 = prof ? jni_now_ms() : 0.0;

    int len = 0;
    int *flat = run_hermes_flat(ctx, &len);
    const double t2 = prof ? jni_now_ms() : 0.0;
    ctx_free(ctx);
    if (prof) {
        jni_report("hermes", (int)nObjects, (int)nAttributes,
                   t1 - t0, t2 - t1, jni_now_ms() - t0);
    }

    if (flat == NULL || len == 0) {
        if (flat) free(flat);
        return (*env)->NewIntArray(env, 0);
    }

    jintArray result = (*env)->NewIntArray(env, len);
    if (result != NULL)
        (*env)->SetIntArrayRegion(env, result, 0, len, (jint*)flat);
    free(flat);
    return result;
}
/* ── Pluton ──────────────────────────────────────────────────────────── */

JNIEXPORT jstring JNICALL
Java_fr_lirmm_fca4j_core_natif_NativeBridge_runPluton(
        JNIEnv *env, jclass clazz,
        jint nObjects, jint nAttributes,
        jbyteArray jmatrix,
        jobjectArray jattrNames) {

    BinaryContext *ctx = ctx_from_jni(env, nObjects, nAttributes, jmatrix, jattrNames);
    char *json = run_pluton_impl(ctx);
    ctx_free(ctx);

    jstring result = (*env)->NewStringUTF(env, json);
    free(json);
    return result;
}

/*
 * runPlutonFlat — variante rapide renvoyant un int[] plat (même format que
 * runHermesFlat / runAddExtentFlat). Indices uniquement, aucun nom.
 */
JNIEXPORT jintArray JNICALL
Java_fr_lirmm_fca4j_core_natif_NativeBridge_runPlutonFlat(
        JNIEnv *env, jclass clazz,
        jint nObjects, jint nAttributes,
        jbyteArray jmatrix) {

    BinaryContext *ctx = ctx_from_jni(env, nObjects, nAttributes, jmatrix, NULL);

    int len = 0;
    int *flat = run_pluton_flat(ctx, &len);
    ctx_free(ctx);

    if (flat == NULL || len == 0) {
        if (flat) free(flat);
        return (*env)->NewIntArray(env, 0);
    }

    jintArray result = (*env)->NewIntArray(env, len);
    if (result != NULL)
        (*env)->SetIntArrayRegion(env, result, 0, len, (jint*)flat);
    free(flat);
    return result;
}

/* ── LinCbO ──────────────────────────────────────────────────────────── */

JNIEXPORT jstring JNICALL
Java_fr_lirmm_fca4j_core_natif_NativeBridge_runLincbo(
        JNIEnv *env, jclass clazz,
        jint nObjects, jint nAttributes,
        jbyteArray jmatrix,
        jobjectArray jattrNames) {

    BinaryContext *ctx = ctx_from_jni(env, nObjects, nAttributes, jmatrix, jattrNames);
    char *json = run_lincbo_impl(ctx);
    ctx_free(ctx);

    jstring result = (*env)->NewStringUTF(env, json);
    free(json);
    return result;
}

/*
 * runLincboPruningFlat — moteur unifié LinCbO avec élagage (lincbo_pruning.c),
 * variante rapide renvoyant un int[] plat (indices, aucun nom), même format
 * que runDbasisFlat. mode : 0=NONE, 1=LIFO (LinCbOWithPruning), 2=LCM.
 */
JNIEXPORT jintArray JNICALL
Java_fr_lirmm_fca4j_core_natif_NativeBridge_runLincboPruningFlat(
        JNIEnv *env, jclass clazz,
        jint nObjects, jint nAttributes,
        jbyteArray jmatrix,
        jint mode) {

    BinaryContext *ctx = ctx_from_jni(env, nObjects, nAttributes, jmatrix, NULL);

    int len = 0;
    int *flat = run_lincbo_pruning_flat(ctx, (LinCboPruneMode)mode, &len);
    ctx_free(ctx);

    if (flat == NULL || len == 0) {
        if (flat) free(flat);
        return (*env)->NewIntArray(env, 0);
    }

    jintArray result = (*env)->NewIntArray(env, len);
    if (result != NULL)
        (*env)->SetIntArrayRegion(env, result, 0, len, (jint*)flat);
    free(flat);
    return result;
}

/* ── AddExtent ───────────────────────────────────────────────────────── */

JNIEXPORT jstring JNICALL
Java_fr_lirmm_fca4j_core_natif_NativeBridge_runAddExtent(
        JNIEnv *env, jclass clazz,
        jint nObjects, jint nAttributes,
        jbyteArray jmatrix,
        jobjectArray jattrNames) {

    BinaryContext *ctx = ctx_from_jni(env, nObjects, nAttributes, jmatrix, jattrNames);
    char *json = run_addextent_impl(ctx);
    ctx_free(ctx);

    jstring result = (*env)->NewStringUTF(env, json);
    free(json);
    return result;
}

/*
 * runAddExtentFlat — variante rapide renvoyant un int[] plat
 * (voir co_to_flat_array pour le format). Les noms d'attributs ne sont pas
 * nécessaires ici puisque le tableau ne contient que des indices.
 */
JNIEXPORT jintArray JNICALL
Java_fr_lirmm_fca4j_core_natif_NativeBridge_runAddExtentFlat(
        JNIEnv *env, jclass clazz,
        jint nObjects, jint nAttributes,
        jbyteArray jmatrix) {

    BinaryContext *ctx = ctx_from_jni(env, nObjects, nAttributes, jmatrix, NULL);

    int len = 0;
    int *flat = run_addextent_flat(ctx, &len);
    ctx_free(ctx);

    if (flat == NULL || len == 0) {
        if (flat) free(flat);
        return (*env)->NewIntArray(env, 0);
    }

    jintArray result = (*env)->NewIntArray(env, len);
    if (result != NULL)
        (*env)->SetIntArrayRegion(env, result, 0, len, (jint*)flat);
    free(flat);
    return result;
}
/* ── LatticeCbO (Lattice_ParallelCbO) ────────────────────────────────── */

JNIEXPORT jstring JNICALL
Java_fr_lirmm_fca4j_core_natif_NativeBridge_runLatticeCbO(
        JNIEnv *env, jclass clazz,
        jint nObjects, jint nAttributes,
        jbyteArray jmatrix,
        jobjectArray jattrNames) {

    BinaryContext *ctx = ctx_from_jni(env, nObjects, nAttributes, jmatrix, jattrNames);
    char *json = run_latticecbo_impl(ctx);
    ctx_free(ctx);

    jstring result = (*env)->NewStringUTF(env, json);
    free(json);
    return result;
}

/* Variante rapide : int[] plat (même format que runAddExtentFlat). */
JNIEXPORT jintArray JNICALL
Java_fr_lirmm_fca4j_core_natif_NativeBridge_runLatticeCbOFlat(
        JNIEnv *env, jclass clazz,
        jint nObjects, jint nAttributes,
        jbyteArray jmatrix) {

    BinaryContext *ctx = ctx_from_jni(env, nObjects, nAttributes, jmatrix, NULL);

    int len = 0;
    int *flat = run_latticecbo_flat(ctx, &len);
    ctx_free(ctx);

    if (flat == NULL || len == 0) {
        if (flat) free(flat);
        return (*env)->NewIntArray(env, 0);
    }

    jintArray result = (*env)->NewIntArray(env, len);
    if (result != NULL)
        (*env)->SetIntArrayRegion(env, result, 0, len, (jint*)flat);
    free(flat);
    return result;
}
/* ── Types et comparateur pour computeMinimalGenerators ─────────────────
 * Définis au niveau fichier pour compatibilité Clang/MSVC
 * (les fonctions imbriquées sont une extension GCC non portable).        */
typedef struct { int *attrs; int size; } Edge;
static int cmp_e(const void *a, const void *b) {
    return ((Edge*)a)->size - ((Edge*)b)->size;
}

/* ── computeMinimalGenerators (utilisé en mode MULTITHREAD) ──────────── */

JNIEXPORT jobjectArray JNICALL
Java_fr_lirmm_fca4j_core_natif_NativeBridge_computeMinimalGenerators(
        JNIEnv *env, jclass clazz,
        jint nObjects, jint nAttributes,
        jbyteArray jmatrix,
        jint target,
        jintArray jbinaryPremises) {

    int nb_obj  = (int)nObjects;
    int nb_attr = (int)nAttributes;
    jclass intArrayClass = (*env)->FindClass(env, "[I");

    jbyte *matrix = (*env)->GetByteArrayElements(env, jmatrix, NULL);

    /* Prémisses binaires */
    bool *is_bin_prem = (bool*)calloc(nb_attr, sizeof(bool));
    if (jbinaryPremises != NULL) {
        jint *bp    = (*env)->GetIntArrayElements(env, jbinaryPremises, NULL);
        int   bplen = (int)(*env)->GetArrayLength(env, jbinaryPremises);
        for (int i = 0; i < bplen; i++) is_bin_prem[(int)bp[i]] = true;
        (*env)->ReleaseIntArrayElements(env, jbinaryPremises, bp, JNI_ABORT);
    }

    /* Construction hypergraphe directement */
    Edge *edges  = (Edge*)malloc(nb_obj * sizeof(Edge));
    int   nedges = 0;
    bool  trivial = false;

    for (int o = 0; o < nb_obj && !trivial; o++) {
        if (matrix[o * nb_attr + (int)target]) continue;
        int *arr = (int*)malloc(nb_attr * sizeof(int));
        int sz = 0;
        for (int a = 0; a < nb_attr; a++) {
            if (a == (int)target || is_bin_prem[a] || matrix[o * nb_attr + a]) continue;
            arr[sz++] = a;
        }
        if (sz == 0) { free(arr); trivial = true; }
        else { edges[nedges].attrs = arr; edges[nedges].size = sz; nedges++; }
    }
    (*env)->ReleaseByteArrayElements(env, jmatrix, matrix, JNI_ABORT);
    free(is_bin_prem);

    if (trivial || nedges == 0) {
        for (int e = 0; e < nedges; e++) free(edges[e].attrs);
        free(edges);
        return (*env)->NewObjectArray(env, 0, intArrayClass, NULL);
    }

    /* Tri par cardinalité */
    qsort(edges, nedges, sizeof(Edge), cmp_e);

    /* Index inversé CSR */
    int *counts = (int*)calloc(nb_attr, sizeof(int));
    int total_ae = 0;
    for (int e = 0; e < nedges; e++)
        for (int i = 0; i < edges[e].size; i++) { counts[edges[e].attrs[i]]++; total_ae++; }
    int *aeo = (int*)malloc((nb_attr+1) * sizeof(int)); aeo[0] = 0;
    for (int a = 0; a < nb_attr; a++) aeo[a+1] = aeo[a] + counts[a];
    int *aed = (int*)malloc(total_ae * sizeof(int));
    memset(counts, 0, nb_attr * sizeof(int));
    for (int e = 0; e < nedges; e++)
        for (int i = 0; i < edges[e].size; i++) { int a = edges[e].attrs[i]; aed[aeo[a]+counts[a]++] = e; }
    free(counts);

    /* Listes attributs par arête */
    int total_ea = 0;
    for (int e = 0; e < nedges; e++) total_ea += edges[e].size;
    int *eao = (int*)malloc((nedges+1)*sizeof(int)); eao[0] = 0;
    int *ead = (int*)malloc(total_ea *sizeof(int));
    for (int e = 0; e < nedges; e++) {
        int idx = eao[e];
        for (int i = 0; i < edges[e].size; i++) ead[idx++] = edges[e].attrs[i];
        eao[e+1] = idx;
    }

    /* GenState inline */
    typedef struct { int nedges, max_attr; int *edge_hit, *aeo, *aed, *eao, *ead; bool *in_current; int current_size; int **results; int *result_sizes; int result_count, result_cap; } GS;
    GS gs;
    gs.nedges = nedges; gs.max_attr = nb_attr;
    gs.aeo = aeo; gs.aed = aed; gs.eao = eao; gs.ead = ead;
    gs.edge_hit   = (int*) calloc(nedges,  sizeof(int));
    gs.in_current = (bool*)calloc(nb_attr, sizeof(bool));
    gs.current_size = 0;
    gs.result_cap = 64; gs.result_count = 0;
    gs.results      = (int**)malloc(64*sizeof(int*));
    gs.result_sizes = (int*) malloc(64*sizeof(int));

    /* generate_covers inline (même logique que dbasis.c) */
    typedef struct { int index, ai, attr; } GF;
    GF *stack = (GF*)malloc((nedges+1)*sizeof(GF));
    int sp = 0;
    stack[0].index = 0; stack[0].ai = -1; stack[0].attr = -1;
    while (sp >= 0) {
        GF *f = &stack[sp];
        if (f->attr >= 0) {
            gs.in_current[f->attr] = false; gs.current_size--;
            int s = gs.aeo[f->attr], en = gs.aeo[f->attr+1];
            for (int i = s; i < en; i++) gs.edge_hit[gs.aed[i]]--;
            f->attr = -1;
        }
        while (f->index < nedges && gs.edge_hit[f->index] > 0) f->index++;
        if (f->index >= nedges) {
            if (gs.result_count == gs.result_cap) {
                gs.result_cap *= 2;
                gs.results      = (int**)realloc(gs.results,      gs.result_cap*sizeof(int*));
                gs.result_sizes = (int*) realloc(gs.result_sizes, gs.result_cap*sizeof(int));
            }
            int *arr = (int*)malloc(gs.current_size*sizeof(int)); int k = 0;
            for (int a = 0; a < nb_attr; a++) if (gs.in_current[a]) arr[k++] = a;
            gs.results[gs.result_count] = arr; gs.result_sizes[gs.result_count] = gs.current_size;
            gs.result_count++; sp--; continue;
        }
        if (f->ai < 0) f->ai = gs.eao[f->index];
        int ea_end = gs.eao[f->index+1]; bool pushed = false;
        while (f->ai < ea_end) {
            int attr = gs.ead[f->ai++];
            if (gs.in_current[attr]) continue;
            gs.in_current[attr] = true; gs.current_size++;
            { int s = gs.aeo[attr], en = gs.aeo[attr+1]; for (int i = s; i < en; i++) gs.edge_hit[gs.aed[i]]++; }
            bool cov = false;
            for (int a2 = 0; a2 < nb_attr && !cov; a2++) {
                if (!gs.in_current[a2]) continue;
                bool all = true;
                int s = gs.aeo[a2], en = gs.aeo[a2+1];
                for (int i = s; i < en; i++) if (gs.edge_hit[gs.aed[i]] <= 1) { all = false; break; }
                if (all) cov = true;
            }
            if (!cov) {
                f->attr = attr; sp++;
                stack[sp].index = f->index+1; stack[sp].ai = -1; stack[sp].attr = -1;
                pushed = true; break;
            }
            gs.in_current[attr] = false; gs.current_size--;
            { int s = gs.aeo[attr], en = gs.aeo[attr+1]; for (int i = s; i < en; i++) gs.edge_hit[gs.aed[i]]--; }
        }
        if (!pushed) sp--;
    }
    free(stack);

    /* Résultat */
    jobjectArray jresult = (*env)->NewObjectArray(env, gs.result_count, intArrayClass, NULL);
    for (int i = 0; i < gs.result_count; i++) {
        jintArray jcover = (*env)->NewIntArray(env, gs.result_sizes[i]);
        (*env)->SetIntArrayRegion(env, jcover, 0, gs.result_sizes[i], (jint*)gs.results[i]);
        (*env)->SetObjectArrayElement(env, jresult, i, jcover);
        (*env)->DeleteLocalRef(env, jcover);
        free(gs.results[i]);
    }
    free(gs.results); free(gs.result_sizes);
    free(gs.edge_hit); free(gs.in_current);
    free(aeo); free(aed); free(eao); free(ead);
    for (int e = 0; e < nedges; e++) free(edges[e].attrs);
    free(edges);
    return jresult;
}
JNIEXPORT jintArray JNICALL
Java_fr_lirmm_fca4j_core_natif_NativeBridge_runLatticeCbOCsrFlat(
        JNIEnv *env, jclass clazz,
        jint nObjects, jint nAttributes,
        jbyteArray jmatrix) {

    BinaryContext *ctx = ctx_from_jni(env, nObjects, nAttributes, jmatrix, NULL);
    int len = 0;
    int *flat = run_latticecbo_csr_flat(ctx, &len);
    ctx_free(ctx);

    if (flat == NULL || len == 0) {
        if (flat) free(flat);
        return (*env)->NewIntArray(env, 0);
    }
    jintArray result = (*env)->NewIntArray(env, len);
    if (result != NULL)
        (*env)->SetIntArrayRegion(env, result, 0, len, (jint*)flat);
    free(flat);
    return result;
}
/* ── Ares ───────────────────────────────────────────────────────────── */

JNIEXPORT jintArray JNICALL
Java_fr_lirmm_fca4j_core_natif_NativeBridge_runAresFlat(
        JNIEnv *env, jclass clazz,
        jint nObjects, jint nAttributes,
        jbyteArray jmatrix) {

    const int prof = jni_profile();
    const double t0 = prof ? jni_now_ms() : 0.0;
    BinaryContext *ctx = ctx_from_jni(env, nObjects, nAttributes, jmatrix, NULL);
    const double t1 = prof ? jni_now_ms() : 0.0;
    int len = 0;
    int *flat = run_ares_flat(ctx, &len);
    const double t2 = prof ? jni_now_ms() : 0.0;
    ctx_free(ctx);
    if (prof) {
        jni_report("ares", (int)nObjects, (int)nAttributes,
                   t1 - t0, t2 - t1, jni_now_ms() - t0);
    }

    if (flat == NULL || len == 0) {
        if (flat) free(flat);
        return (*env)->NewIntArray(env, 0);
    }
    jintArray result = (*env)->NewIntArray(env, len);
    if (result != NULL)
        (*env)->SetIntArrayRegion(env, result, 0, len, (jint*)flat);
    free(flat);
    return result;
}

/* ── Ceres ───────────────────────────────────────────────────────────── */

JNIEXPORT jintArray JNICALL
Java_fr_lirmm_fca4j_core_natif_NativeBridge_runCeresFlat(
        JNIEnv *env, jclass clazz,
        jint nObjects, jint nAttributes,
        jbyteArray jmatrix) {

    /* Pas de BinaryContext ici : la matrice alimente directement la forme dense
     * de Ceres. Le détour par roaring coûtait 64 ms sur ord10shuttle pour 51 ms
     * d'algorithme — |G| + |A| bitmaps créés puis remplis cellule par cellule,
     * aussitôt reconvertis en dense.
     *
     * Le tableau Java est rendu dès la construction terminée, sans attendre la
     * fin du calcul. GetByteArrayElements plutôt que sa variante critique : la
     * copie coûte moins d'une milliseconde même sur 3,8 Mo, alors qu'une section
     * critique tenue pendant toute la construction gênerait le ramasse-miettes. */
    const int prof = jni_profile();
    const double t0 = prof ? jni_now_ms() : 0.0;
    jbyte *matrix = (*env)->GetByteArrayElements(env, jmatrix, NULL);
    CeresContext *cx = ceres_ctx_from_matrix((int)nObjects, (int)nAttributes,
                                             (const signed char*)matrix);
    (*env)->ReleaseByteArrayElements(env, jmatrix, matrix, JNI_ABORT);
    const double t1 = prof ? jni_now_ms() : 0.0;
    int len = 0;
    int *flat = run_ceres_dense(cx, &len);
    const double t2 = prof ? jni_now_ms() : 0.0;
    ceres_ctx_free(cx);
    if (prof) {
        jni_report("ceres", (int)nObjects, (int)nAttributes,
                   t1 - t0, t2 - t1, jni_now_ms() - t0);
    }

    if (flat == NULL || len == 0) {
        if (flat) free(flat);
        return (*env)->NewIntArray(env, 0);
    }
    jintArray result = (*env)->NewIntArray(env, len);
    if (result != NULL)
        (*env)->SetIntArrayRegion(env, result, 0, len, (jint*)flat);
    free(flat);
    return result;
}

