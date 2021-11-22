/*
 * Copyright (c) 2013-2016, ENGEES. All rights reserved.
 * This file is part of RCAExplore.
 * 
 *  RCAExplore is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  RCAExplore is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with RCAExplore.  If not, see <http://www.gnu.org/licenses/>.
 *  
 *  Authors : 
 *  - Jean-Rémy Falleri
 *  - Xavier Dolques
 *  
 *  this file contains code covered by the following terms:
 *  Copyright 2009 Jean-Rémy Falleri
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.Iterator;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;


public class Lattice_Iceberg implements AbstractAlgo<ConceptOrder> {

    private IBinaryContext matrix;    
    protected ISetFactory factory;
    private ConceptOrder order;    
    private Chrono chrono = null; // eventually a chrono to store execution time 
    private int icebergThreshold;
    private int percentage;
    private Integer bottom;
    
    public Lattice_Iceberg(IBinaryContext matrix, int percentage, Chrono chrono) {
        super();
        this.matrix = matrix;
        this.factory = matrix.getFactory();
        this.chrono = chrono;
        icebergThreshold = matrix.getObjectCount() * percentage / 100;
        this.percentage=percentage;
    }

    public Lattice_Iceberg(IBinaryContext matrix, int percentage) {
        this(matrix, percentage,null);
    }
    public int getPercentage(){
    	return percentage;
    }
    protected int addExtent(ISet extent, int generatorParam) throws Exception{
        int extentCardinality = extent.cardinality();
        if (extentCardinality < icebergThreshold) {
            extent.removeAll(extent);
        }
        int generator;

        if (extentCardinality < icebergThreshold && bottom != null) {
            generator = bottom;
        } else {
            generator = getSmallestContainingConcept(extent, generatorParam);
        }

        if (extent.equals(order.getConceptExtent(generator))) {
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
            for (int child : newChildren) {
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
        for (int child : newChildren) {
            order.removePrecedenceConnection(child, generator);
            order.addPrecedenceConnection(child, newConcept);
        }
        order.addPrecedenceConnection(newConcept, generator);
        if (order.inDegreeOf(newConcept)==0) {
            bottom = newConcept;
        }
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
    
    @Override
    public String getDescription() {
        return "Iceberg";
    }

    @Override
    public ConceptOrder getResult() {
        return order;
    }

    @Override
    public void run() {
        try {
            order = new ConceptOrder("LatticeWithAddExtent",matrix,getDescription());
            if (chrono != null) {
                chrono.start("concept/order");
            }
            ISet allObjects = factory.createSet(matrix.getObjectCount());
            allObjects.fill(matrix.getObjectCount());
            int top=order.addConcept(allObjects, factory.createSet(0));
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
