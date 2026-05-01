/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.util;

import fr.lirmm.fca4j.core.ConceptOrder;

public interface ConceptOrderFinder {
	    ConceptOrder findConceptOrder(String formalContext, int numConcept);
	}

