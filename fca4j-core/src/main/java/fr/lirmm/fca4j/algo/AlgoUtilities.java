/*
BSD 3-Clause License

Copyright (c) 2022 LIRMM
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

   * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
   * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
   * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
