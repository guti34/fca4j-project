/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.core;

import java.util.Iterator;
import java.util.List;

import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;

/**
 * The Interface IConceptOrder.
 *
 * @author agutierr
 */
public interface IConceptOrder {

    /**
     * Gets the id.
     *
     * @return the id
     */
    public String getId();

    /**
     * Gets the context.
     *
     * @return the context
     */
    public IBinaryContext getContext();

    /**
     * Adds the concept.
     *
     * @param extent the extent
     * @param intent the intent
     * @return the int
     */
    public int addConcept(ISet extent, ISet intent);

    /**
     * Adds the concept.
     *
     * @param extent the extent
     * @param intent the intent
     * @param rextent the rextent
     * @param rintent the rintent
     * @return the int
     */
    public int addConcept(ISet extent, ISet intent, ISet rextent, ISet rintent);

    /**
     * Gets the concept extent.
     *
     * @param numConcept the num concept
     * @return the concept extent
     */
    public ISet getConceptExtent(int numConcept);

    /**
     * Gets the concept intent.
     *
     * @param numConcept the num concept
     * @return the concept intent
     */
    public ISet getConceptIntent(int numConcept);

    /**
     * Gets the concept reduced extent.
     *
     * @param numConcept the num concept
     * @return the concept reduced extent
     */
    public ISet getConceptReducedExtent(int numConcept);

    /**
     * Sets the reduced extent.
     *
     * @param numConcept the num concept
     * @param rextent the rextent
     */
    public void setReducedExtent(int numConcept, ISet rextent);

    /**
     * Gets the concept reduced intent.
     *
     * @param numConcept the num concept
     * @return the concept reduced intent
     */
    public ISet getConceptReducedIntent(int numConcept);

    /**
     * Sets the reduced intent.
     *
     * @param numConcept the num concept
     * @param rintent the rintent
     */
    public void setReducedIntent(int numConcept, ISet rintent);

    /**
     * Removes the concept.
     *
     * @param numConcept the num concept
     */
    public void removeConcept(int numConcept);

    /**
     * Adds the precedence connection.
     *
     * @param lower the lower
     * @param greater the greater
     */
    public void addPrecedenceConnection(int lower, int greater);

    public void addPrecedenceConnections(int[] lowers, int[] greaters);
    /**
     * Removes the precedence connection.
     *
     * @param lower the lower
     * @param greater the greater
     */
    public void removePrecedenceConnection(int lower, int greater);

    /**
     * Gets the top.
     *
     * @return the top
     */
    public int getTop();

    /**
     * Gets the maximals.
     *
     * @return the maximals
     */
    public ISet getMaximals();

    /**
     * Gets the minimals.
     *
     * @return the minimals
     */
    public ISet getMinimals();

    /**
     * Gets the bottom.
     *
     * @return the bottom
     */
    public int getBottom();

    /**
     * returns all the children of the current concept. This relation is
     * reflexive so the current concept is included in its children
     *
     * @param concept the concept
     * @return the all children
     */

    public ISet getAllChildren(int concept);

    /**
     * Computes and returns all the parents of the current concept.This
     * relation is reflexive so the current concept is included in its parents
     *
     * @param concept the concept
     * @return the all parents
     */
    public ISet getAllParents(int concept);

    /**
     * In degree of.
     *
     * @param concept the concept
     * @return the int
     */
    public int inDegreeOf(int concept);

    /**
     * Out degree of.
     *
     * @param concept the concept
     * @return the int
     */
    public int outDegreeOf(int concept);

    /**
     * Gets the lower cover.
     *
     * @param concept the concept
     * @return the lower cover
     */
    public ISet getLowerCover(int concept);

    /**
     * Gets the upper cover.
     *
     * @param concept the concept
     * @return the upper cover
     */
    public ISet getUpperCover(int concept);

    /**
     * Gets the basic iterator.
     *
     * @return the basic iterator
     */
    public Iterator<Integer> getBasicIterator();

    /**
     * Gets the bottom up iterator.
     *
     * @return the bottom up iterator
     */
    public Iterator<Integer> getBottomUpIterator();

    /**
     * Gets the top down iterator.
     *
     * @return the top down iterator
     */
    public Iterator<Integer> getTopDownIterator();

    /**
     * Gets the concept count.
     *
     * @return the concept count
     */
    public int getConceptCount();

    /**
     * Gets the edge count.
     *
     * @return the edge count
     */
    public int getEdgeCount();

    /**  
     * 
     * @return the name of the algorithm which have built this order
     * 
     */
    public String getAlgoName();
    
    /**
     * Clone.
     *
     * @param newFactory the new factory
     * @return the concept order
     */
    public ConceptOrder clone(ISetFactory newFactory);
    /**
     * Get the shortest path between deux concept
     * @param vertex1
     * @param vertex2
     * @return the path or null if no path exist
     */
    public List<Integer> getShortestPath(int vertex1,int vertex2);

}
