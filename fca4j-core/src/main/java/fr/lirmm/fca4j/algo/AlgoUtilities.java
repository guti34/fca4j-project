/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.List;

import fr.lirmm.fca4j.core.ConceptOrder;

/**
 * The Class AlgoUtilities.
 *
 * @author agutierr
 */
public class AlgoUtilities {

    /**
     * build new concept order with only attribute concepts
     *
     * @param co the concept order
     * @return the new concept order
     */
    public static final ConceptOrder toAttributeConceptOrder(ConceptOrder co) {
        ConceptOrder co2 = co.clone();
        List<Integer> toRemove = new ArrayList<>();
        for (int concept : co2.getConcepts()) {
            if (co2.getConceptReducedIntent(concept).isEmpty()) {
                toRemove.add(concept);
            }
        }
        co2.closure();
        for (int concept : toRemove) {
            co2.removeConcept(concept);
        }
        co2.reduce();
        
        return co2;
    }
    
    /**
     * build new concept order with only object concepts
     *
     * @param co the concept order
     * @return the new concept order
     */
    public static final ConceptOrder toObjectConceptOrder(ConceptOrder co) {
        ConceptOrder co2 = co.clone();
        List<Integer> toRemove = new ArrayList<>();
        for (int concept : co2.getConcepts()) {
            if (co2.getConceptReducedExtent(concept).isEmpty()) {
                toRemove.add(concept);
            }
        }
        co2.closure();
        for (int concept : toRemove) {
            co2.removeConcept(concept);
        }
        co2.reduce();
        return co2;
    }
}
