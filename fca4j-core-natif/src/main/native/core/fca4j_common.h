/*
 * fca4j_common.h — macros et includes communs à tous les modules
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 */
#ifndef FCA4J_COMMON_H
#define FCA4J_COMMON_H

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>
#include <stdarg.h>
#include <time.h>
#include "../croaring/roaring.h"

#define VEC_INIT_CAP 64
#define VEC_DEF(T, Name)                                            \
    typedef struct { T *data; int len, cap; } Name;                 \
    static inline Name Name##_new(void) {                           \
        Name v; v.len = 0; v.cap = VEC_INIT_CAP;                   \
        v.data = (T*)malloc(sizeof(T) * v.cap); return v;           \
    }                                                               \
    static inline void Name##_push(Name *v, T val) {               \
        if (v->len >= v->cap) {                                     \
            v->cap *= 2;                                            \
            v->data = (T*)realloc(v->data, sizeof(T)*v->cap);      \
        }                                                           \
        v->data[v->len++] = val;                                    \
    }                                                               \
    static inline void Name##_free(Name *v) {                      \
        free(v->data); v->data=NULL; v->len=v->cap=0;              \
    }

VEC_DEF(int,               IntVec)
VEC_DEF(char*,             StrVec)
VEC_DEF(roaring_bitmap_t*, BitmapVec)

#endif /* FCA4J_COMMON_H */
