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
package fr.lirmm.fca4j.core;

import java.util.Iterator;

import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;

/**
 *
 * @author agutierr
 */
public interface IConceptOrder {

    public String getId();

    public IBinaryContext getContext();

    public int addConcept(ISet extent, ISet intent);

    public int addConcept(ISet extent, ISet intent, ISet rextent, ISet rintent);

    public ISet getConceptExtent(int numConcept);

    public ISet getConceptIntent(int numConcept);

    public ISet getConceptReducedExtent(int numConcept);

    public void setReducedExtent(int numConcept, ISet rextent);

    public ISet getConceptReducedIntent(int numConcept);

    public void setReducedIntent(int numConcept, ISet rintent);

    public void removeConcept(int numConcept);

    public void addPrecedenceConnection(int lower, int greater);

    public void removePrecedenceConnection(int lower, int greater);

    public int getTop();

    public ISet getMaximals();

    public ISet getMinimals();

    public int getBottom();

    /**
     * returns all the children of the current concept. This relation is
     * reflexive so the current concept is included in its children
     */

    public ISet getAllChildren(int concept);

    /**
     * Computes and returns all the parents of the current concept.This
     * relation is reflexive so the current concept is included in its parents
     * @param concept
     * @return 
     */
    public ISet getAllParents(int concept);

    public int inDegreeOf(int concept);

    public int outDegreeOf(int concept);

    public ISet getLowerCover(int concept);

    public ISet getUpperCover(int concept);

    public Iterator<Integer> getBasicIterator();

    public Iterator<Integer> getBottomUpIterator();

    public Iterator<Integer> getTopDownIterator();

    public int getConceptCount();

    public int getEdgeCount();

    /**  
     * 
     * @return the name of the algorithm which have built this order
     * 
     */
    public String getAlgoName();
    
    public ConceptOrder clone(ISetFactory newFactory);
}
