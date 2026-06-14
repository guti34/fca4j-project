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
#include "algo/addextent.h"
#include "algo/latticecbo.h"
#include "fr_lirmm_fca4j_core_natif_NativeBridge.h"

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

    BinaryContext *ctx = ctx_from_jni(env, nObjects, nAttributes, jmatrix, NULL);

    int len = 0;
    int *flat = run_hermes_flat(ctx, &len);
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
    typedef struct { int *attrs; int size; } Edge;
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
    int cmp_e(const void *a, const void *b) { return ((Edge*)a)->size - ((Edge*)b)->size; }
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
