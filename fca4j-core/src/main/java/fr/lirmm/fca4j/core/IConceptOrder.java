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
