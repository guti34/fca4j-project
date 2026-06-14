/*
 * tarjan.h — Détection de D-cycles (Tarjan SCC)
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 */
#ifndef FCA4J_TARJAN_H
#define FCA4J_TARJAN_H

#include "fca4j_common.h"
#include "implication.h"

/* Retourne les SCCs de taille > 1 du graphe de dépendances des implications */
BitmapVec find_dcycles(ImplVec *basis, int nb_attributes);

#endif /* FCA4J_TARJAN_H */
