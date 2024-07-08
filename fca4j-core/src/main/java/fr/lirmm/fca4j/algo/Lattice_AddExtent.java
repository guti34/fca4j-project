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
import java.util.Iterator;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

/**
 * The Class Lattice_AddExtent.
 */
public class Lattice_AddExtent implements AbstractAlgo<ConceptOrder> {

    private IBinaryContext matrix;    
    private ConceptOrder order;    
    protected ISetFactory factory;
    private Chrono chrono = null; // eventually a chrono to store execution time 
    
    /**
     * Instantiates a new lattice add extent.
     *
     * @param matrix the matrix
     * @param chrono the chrono
     */
    public Lattice_AddExtent(IBinaryContext matrix, Chrono chrono) {
        super();
        this.matrix = matrix;
        this.factory = matrix.getFactory();
        this.chrono = chrono;
    }

    /**
     * Instantiates a new lattice add extent.
     *
     * @param matrix the matrix
     */
    public Lattice_AddExtent(IBinaryContext matrix) {
        this(matrix, null);
    }

    /**
     * Adds the extent.
     *
     * @param extent the extent
     * @param generator the generator
     * @return the int
     * @throws Exception the exception
     */
    protected int addExtent(ISet extent, int generator) throws Exception{
        generator = getSmallestContainingConcept(extent, generator);
        if (extent.equals(order.getConceptExtent(generator))) {//containsAll(generator.getExtent()) && generator.getExtent().containsAll(extent) ) {
            return generator;
        }
        ArrayList<Integer> newChildren = new ArrayList<>();
        Iterator<Integer> it = order.getLowerCoverIterator(generator);
        while (it.hasNext()) {
            int candidate = it.next();
            if (!order.getConceptExtent(candidate).containsAll(extent)) {
                ISet intersection = extent.newIntersect(order.getConceptExtent(candidate));                
                candidate = addExtent(intersection, candidate);
            }
            boolean addChild = true;
            ArrayList<Integer> conceptsToDelete = new ArrayList<>();
            for (int child:newChildren) {
                if (order.getConceptExtent(child).containsAll(order.getConceptExtent(candidate))) {
                    addChild = false;
                    break;
                } else if (order.getConceptExtent(candidate).containsAll(order.getConceptExtent(child))) {
                    conceptsToDelete.add(child);
                }
            }
            newChildren.removeAll(conceptsToDelete);
            if (addChild) {
                newChildren.add(candidate);
            }
        }       
        int newConcept = order.addConcept(factory.clone(extent), factory.clone(order.getConceptIntent(generator)));
        for (int child:newChildren) 
        {
            order.removePrecedenceConnection(child, generator);
            order.addPrecedenceConnection(child, newConcept);
        }
        order.addPrecedenceConnection(newConcept, generator);
        return newConcept;
    }

    /**
     * get a concept whose extent contains the extent passed in parameter but
     * whose children don't
	 *
     */
    private int getSmallestContainingConcept(ISet extent, int generator) {
        boolean isMaximal = true;
        while (isMaximal) {
            isMaximal = false;
            for (int child : order.getLowerCoverSet(generator)) {
                if (order.getConceptExtent(child).containsAll(extent)) {
                    generator = child;
                    isMaximal = true;
                    break;
                }
            }
        }
        return generator;
    }
    
    /**
     * Gets the description.
     *
     * @return the description
     */
    @Override
    public String getDescription() {
        return "AddExtent";
    }

    /**
     * Gets the result.
     *
     * @return the result
     */
    @Override
    public ConceptOrder getResult() {
        return order;
    }

    /**
     * Run.
     */
    @Override
    public void run() {
        try {
            order = new ConceptOrder("LatticeWithAddExtent",matrix,getDescription());
            if (chrono != null) {
                chrono.start("concept/order");
            }
            ISet allObjects = factory.createSet(matrix.getObjectCount());
            allObjects.fill(matrix.getObjectCount());
            int top=order.addConcept(allObjects, factory.createSet());
            for (int numAttr = 0; numAttr < matrix.getAttributeCount(); numAttr++) {
                int concept = addExtent(matrix.getExtent(numAttr), top);
                order.getConceptReducedIntent(concept).add(numAttr);
            }
            
            for (Iterator<Integer> it=order.getBasicIterator();it.hasNext();  ) {
            int concept=it.next();
                ISet rExtent = factory.clone(order.getConceptExtent(concept));
                for (int child : order.getLowerCoverSet(concept)) {
                    rExtent.removeAll(order.getConceptExtent(child));
//                    rExtent = rExtent.newDifference(order.getConceptExtent(child));
                }
                order.getConceptReducedExtent(concept).addAll(rExtent);
            }
            order.computeIntents();
            if (chrono != null) {
                chrono.stop("concept/order");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
}
