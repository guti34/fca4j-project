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

import java.util.*;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

/**
 * Optimized version of ADD_EXTENT using direct indexing.
 */
public class Lattice_AddExtent_Indexed implements AbstractAlgo<ConceptOrder> {

    private IBinaryContext matrix;
    private ConceptOrder order;
    private ISetFactory factory;
    private Chrono chrono;

    public Lattice_AddExtent_Indexed(IBinaryContext matrix, Chrono chrono) {
        this.matrix = matrix;
        this.factory = matrix.getFactory();
        this.chrono = chrono;
    }

    public Lattice_AddExtent_Indexed(IBinaryContext matrix) {
        this(matrix, null);
    }

    @Override
    public String getDescription() {
        return "AddExtent";
    }

    @Override
    public ConceptOrder getResult() {
        return order;
    }

    @Override
    public void run() {
        try {
            order = new ConceptOrder("LatticeWithAddExtentIndexed", matrix, getDescription());

            // Top concept: all objects, empty intent
            ISet allObjects = factory.createSet(matrix.getObjectCount());
            allObjects.fill(matrix.getObjectCount());

            int top = order.addConcept(allObjects, factory.createSet());

            // Process each attribute incrementally
            for (int attr = 0; attr < matrix.getAttributeCount(); attr++) {
                int concept = addExtent(matrix.getExtent(attr), top);
                order.getConceptReducedIntent(concept).add(attr);
            }

            // Compute reduced extents
            for (Iterator<Integer> it = order.getBasicIterator(); it.hasNext(); ) {
                int concept = it.next();
                ISet rExtent = factory.clone(order.getConceptExtent(concept));

                for (int child : order.getLowerCoverSet(concept)) {
                    rExtent.removeAll(order.getConceptExtent(child));
                }

                order.getConceptReducedExtent(concept).addAll(rExtent);
            }

            // Compute intents fully
            order.computeIntents();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =====================================================
    // Core: add an extent under a given generator concept
    // =====================================================
    private int addExtent(ISet extent, int generator) throws Exception {
        generator = findSmallestContainingConcept(extent, generator);

        ISet generatorExtent = order.getConceptExtent(generator);
        if (extent.equals(generatorExtent)) return generator;

        ArrayList<Integer> newChildren = new ArrayList<>();

        for (int child : order.getLowerCoverSet(generator)) {
            ISet childExtent = order.getConceptExtent(child);

            if (!childExtent.containsAll(extent)) {
                ISet intersection = extent.newIntersect(childExtent);
                child = addExtent(intersection, child);
            }

            boolean keep = true;

            Iterator<Integer> it = newChildren.iterator();
            while (it.hasNext()) {
                int existing = it.next();
                ISet existingExtent = order.getConceptExtent(existing);
                ISet candidateExtent = order.getConceptExtent(child);

                if (existingExtent.containsAll(candidateExtent)) {
                    keep = false;
                    break;
                }

                if (candidateExtent.containsAll(existingExtent)) {
                    it.remove();
                }
            }

            if (keep) newChildren.add(child);
        }

        int newConcept = order.addConcept(factory.clone(extent),
                                         factory.clone(order.getConceptIntent(generator)));

        for (int child : newChildren) {
            order.removePrecedenceConnection(child, generator);
            order.addPrecedenceConnection(child, newConcept);
        }

        order.addPrecedenceConnection(newConcept, generator);

        return newConcept;
    }

    // =====================================================
    // Find smallest ancestor concept containing the extent
    // =====================================================
    private int findSmallestContainingConcept(ISet extent, int generator) {
        boolean done = false;
        while (!done) {
            done = true;
            for (int child : order.getLowerCoverSet(generator)) {
                if (order.getConceptExtent(child).containsAll(extent)) {
                    generator = child;
                    done = false;
                    break;
                }
            }
        }
        return generator;
    }
}
