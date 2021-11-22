/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.List;

import fr.lirmm.fca4j.core.ConceptOrder;

/**
 *
 * @author agutierr
 */
public class AlgoUtilities {

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
