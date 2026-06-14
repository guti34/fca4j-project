cmake_minimum_required(VERSION 3.16)
project(fca4j_dbasis C)
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# Type de build par défaut : Release (doit être fixé AVANT toute évaluation
# des generator expressions ci-dessous).
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release CACHE STRING "Build type" FORCE)
endif()

find_package(JNI REQUIRED)
include_directories(${JNI_INCLUDE_DIRS})
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

set(CROARING_DIR "${CMAKE_CURRENT_SOURCE_DIR}/croaring")
include_directories(${CROARING_DIR})

set(CORE_SOURCES
    core/context.c
    core/strbuf.c
    core/implication.c
    core/closure.c
    core/clarification.c
    core/graph.c
    core/tarjan.c
    core/conceptorder.c
)

set(ALGO_SOURCES
    algo/dbasis.c
    algo/hermes.c
    algo/lincbo.c
    algo/addextent.c
    algo/latticecbo.c
)

add_library(fca4j_dbasis SHARED
    ${CROARING_DIR}/roaring.c
    ${CORE_SOURCES}
    ${ALGO_SOURCES}
    fca4j_jni.c
)

target_include_directories(fca4j_dbasis PRIVATE
    ${CROARING_DIR}
    ${JNI_INCLUDE_DIRS}
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# ── Flags d'optimisation ──────────────────────────────────────────────────
# IMPORTANT : flags séparés (pas "-O3 -march=native" en un seul token), sinon
# certains générateurs passent toute la chaîne comme UN argument compilateur,
# ce qui désactive silencieusement -march=native et fait tomber CRoaring en
# mode SCALAIRE (≈ 3× plus lent sur les opérations d'ensemble).
target_compile_options(fca4j_dbasis PRIVATE
    $<$<CONFIG:Release>:-O3>
    $<$<CONFIG:Release>:-march=native>
    $<$<CONFIG:Release>:-funroll-loops>
)

# Message de configuration : rend les flags visibles à la configuration.
message(STATUS "fca4j_dbasis: CMAKE_BUILD_TYPE = ${CMAKE_BUILD_TYPE}")
message(STATUS "fca4j_dbasis: flags Release    = -O3 -march=native -funroll-loops")
message(STATUS "fca4j_dbasis: CMAKE_C_FLAGS    = ${CMAKE_C_FLAGS}")

# Lier pthread sur Linux/macOS
if(NOT WIN32)
    target_link_libraries(fca4j_dbasis pthread)
endif()

# Sur Windows avec MinGW, pthread est disponible via -lpthread
if(WIN32 AND MINGW)
    target_link_libraries(fca4j_dbasis pthread)
endif()

set_target_properties(fca4j_dbasis PROPERTIES OUTPUT_NAME "fca4j_dbasis")
if(WIN32)
    set_target_properties(fca4j_dbasis PROPERTIES PREFIX "")
endif()
