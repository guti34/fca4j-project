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

# core/ : structures et briques partagées par plusieurs algorithmes.
# bitset.h et bitset_roaring.h sont des en-têtes seuls (tout est inline).
# dynorder.c y a été remonté depuis algo/aresorder.c le jour où un deuxième
# algorithme en a eu besoin.
set(CORE_SOURCES
    core/context.c
    core/strbuf.c
    core/implication.c
    core/closure.c
    core/clarification.c
    core/graph.c
    core/tarjan.c
    core/conceptorder.c
    core/dynorder.c
)

set(ALGO_SOURCES
    algo/ares.c
    algo/ceres.c
    algo/dbasis.c
    algo/hermes.c
    algo/lincbo.c
    algo/lincbo_pruning.c
    algo/addextent.c
    algo/latticecbo.c
    algo/pluton.c
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

# ── Harnais de vérification, hors JNI ─────────────────────────────────────
# EXCLUDE_FROM_ALL : ils ne ralentissent pas la compilation ordinaire de la DLL.
# Construction explicite :
#   cmake --build build --target dynorder_test ares_test latticecbo_regress
# Ils tournent sans JVM, ce qui est le point : une régression de portage se
# constate sans rien démarrer.

# Socle : DynOrder seul, sans croaring ni algorithme.
add_executable(dynorder_test EXCLUDE_FROM_ALL
    algo/dynorder_test.c core/dynorder.c)
target_include_directories(dynorder_test PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

# Ares contre Hermes, sur contextes balayés puis aléatoires.
add_executable(ares_test EXCLUDE_FROM_ALL
    algo/ares_test.c algo/ares.c core/dynorder.c algo/hermes.c
    core/context.c core/strbuf.c core/conceptorder.c core/clarification.c
    core/graph.c core/tarjan.c core/closure.c core/implication.c
    ${CROARING_DIR}/roaring.c)
target_include_directories(ares_test PRIVATE ${CROARING_DIR} ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_options(ares_test PRIVATE -O2)
if(NOT WIN32 OR MINGW)
    target_link_libraries(ares_test m)
endif()

# Signature canonique du treillis, invariante par renumérotation, balayée sur
# 1, 4 et 16 threads. Usage : latticecbo_regress > ref.txt puis
# latticecbo_regress ref.txt. La référence doit être produite AVANT le
# changement que l'on cherche à valider.
add_executable(latticecbo_regress EXCLUDE_FROM_ALL
    algo/latticecbo_regress.c algo/latticecbo.c
    core/context.c core/strbuf.c core/conceptorder.c core/closure.c
    core/graph.c core/tarjan.c core/implication.c core/clarification.c
    ${CROARING_DIR}/roaring.c)
target_include_directories(latticecbo_regress PRIVATE ${CROARING_DIR} ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_options(latticecbo_regress PRIVATE -O2)
target_link_libraries(latticecbo_regress pthread)
if(NOT WIN32 OR MINGW)
    target_link_libraries(latticecbo_regress m)
endif()

# Ceres contre Hermes, mêmes trois phases qu'ares_test.
add_executable(ceres_test EXCLUDE_FROM_ALL
    algo/ceres_test.c algo/ceres.c core/dynorder.c algo/hermes.c
    core/context.c core/strbuf.c core/conceptorder.c core/clarification.c
    core/graph.c core/tarjan.c core/closure.c core/implication.c
    ${CROARING_DIR}/roaring.c)
target_include_directories(ceres_test PRIVATE ${CROARING_DIR} ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_options(ceres_test PRIVATE -O2)
if(NOT WIN32 OR MINGW)
    target_link_libraries(ceres_test m)
endif()

# Signature canonique des quatre AOC-posets, invariante par renumerotation.
# Indispensable des lors qu'on modifie hermes.c : ares_test et ceres_test le
# prennent pour oracle, donc ils ne peuvent plus le valider. Usage :
#   aoc_regress > aoc_ref.txt        AVANT le premier changement
#   aoc_regress aoc_ref.txt          apres
add_executable(aoc_regress EXCLUDE_FROM_ALL
    algo/aoc_regress.c algo/hermes.c algo/ares.c algo/ceres.c algo/pluton.c
    core/dynorder.c core/context.c core/strbuf.c core/conceptorder.c
    core/clarification.c core/graph.c core/tarjan.c core/closure.c
    core/implication.c ${CROARING_DIR}/roaring.c)
target_include_directories(aoc_regress PRIVATE ${CROARING_DIR} ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_options(aoc_regress PRIVATE -O2)
target_link_libraries(aoc_regress pthread)
if(NOT WIN32 OR MINGW)
    target_link_libraries(aoc_regress m)
endif()

# LinCbO avec élagage (lincbo_pruning.c) : trois harnais qui partagent tous
# le même socle de sources (lincbo.c comme oracle, lincbo_pruning.c comme
# moteur testé/mesuré, plus le strict nécessaire de core/).
set(LINCBO_PRUNING_SOURCES
    algo/lincbo.c algo/lincbo_pruning.c
    core/context.c core/strbuf.c core/implication.c core/closure.c
    ${CROARING_DIR}/roaring.c)

# Correction : les 3 modes de lincbo_pruning.c (NONE/LIFO/LCM) contre
# lincbo.c, sur 8 cas nommés + 20 fuzz. Échoue (exit 1) si une base diverge
# de l'oracle. À lancer sous ASan/UBSan avant tout changement dans
# lincbo_pruning.c (voir le commentaire en tête du fichier) :
#   cmake --build build --target lincbo_pruning_test --config Debug
add_executable(lincbo_pruning_test EXCLUDE_FROM_ALL
    algo/lincbo_pruning_test.c ${LINCBO_PRUNING_SOURCES})
target_include_directories(lincbo_pruning_test PRIVATE ${CROARING_DIR} ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_options(lincbo_pruning_test PRIVATE -O1 -g)
if(NOT WIN32 OR MINGW)
    target_link_libraries(lincbo_pruning_test m)
endif()

# Sanity perf (pas un vrai benchmark) : cohérence des 3 modes sur contexte
# aléatoire + contexte "grille" structuré, et cas limites (0 attribut, 1x1,
# colonne toujours vraie).
add_executable(lincbo_pruning_perf EXCLUDE_FROM_ALL
    algo/lincbo_pruning_perf.c ${LINCBO_PRUNING_SOURCES})
target_include_directories(lincbo_pruning_perf PRIVATE ${CROARING_DIR} ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_options(lincbo_pruning_perf PRIVATE -O2)
if(NOT WIN32 OR MINGW)
    target_link_libraries(lincbo_pruning_perf m)
endif()

# Bench à un seul facteur : bitset.h dense (lincbo_pruning.c, mode NONE)
# contre roaring_bitmap_t (lincbo.c) pour le domaine ATTRIBUTS, à algorithme
# identique — sert à décider s'il faut exposer un choix de backend dans
# RULEBASIS. Voir les commentaires en tête de lincbo_pruning_bench_sets.c
# pour les résultats déjà obtenus et leur interprétation. Plusieurs minutes
# à l'exécution (balaie jusqu'à 10 000 attributs) : pas dans la cible
# groupée `tests` ci-dessous, à lancer explicitement.
add_executable(lincbo_pruning_bench_sets EXCLUDE_FROM_ALL
    algo/lincbo_pruning_bench_sets.c ${LINCBO_PRUNING_SOURCES})
target_include_directories(lincbo_pruning_bench_sets PRIVATE ${CROARING_DIR} ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_options(lincbo_pruning_bench_sets PRIVATE -O2)
if(NOT WIN32 OR MINGW)
    target_link_libraries(lincbo_pruning_bench_sets m)
endif()

# Cible groupée, pour n'avoir qu'un nom à retenir. lincbo_pruning_perf et
# lincbo_pruning_bench_sets restent hors groupe (bench, pas correction ;
# le second est lent) — se construisent/lancent explicitement par leur nom.
add_custom_target(tests DEPENDS dynorder_test ares_test ceres_test
                              aoc_regress latticecbo_regress
                              lincbo_pruning_test)