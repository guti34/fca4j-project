/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.core;

import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;

/**
 * The Interface IConceptOrder.
 *
 * Implemented by {@link ConceptOrder} (JGraphT-backed) and by
 * {@link CsrConceptOrder} (memory-lean, dense arrays + CSR adjacency).
 *
 * @author agutierr
 */
public interface IConceptOrder {

    // ===== identity / context ==============================================

    public String getId();

    public void setId(String name);

    public IBinaryContext getContext();

    public String getAlgoName();

    // ===== concepts ========================================================

    public int addConcept(ISet extent, ISet intent);

    public int addConcept(ISet extent, ISet intent, ISet rextent, ISet rintent);

    public void removeConcept(int numConcept);

    public ISet getConceptExtent(int numConcept);

    public ISet getConceptIntent(int numConcept);

    public ISet getConceptReducedExtent(int numConcept);

    public void setReducedExtent(int numConcept, ISet rextent);

    public ISet getConceptReducedIntent(int numConcept);

    public void setReducedIntent(int numConcept, ISet rintent);

    public Set<Integer> getConcepts();

    public int getConceptCount();

    /** True if the concept is a fusion (reduced extent cardinality &gt; 1). */
    public boolean isFusion(int concept);

    /** True if the concept is new (reduced extent cardinality == 0). */
    public boolean isNewConcept(int concept);

    /** True if the concept is dummy (empty full extent or empty full intent). */
    public boolean isDummy(int concept);

    // ===== precedence (Hasse) edges ========================================

    public void addPrecedenceConnection(int lower, int greater);

    public void addPrecedenceConnections(int[] lowers, int[] greaters);

    public void removePrecedenceConnection(int lower, int greater);

    public int getEdgeCount();

    /** Bulk load of concepts (reduced sets as bitsets) and edges. */
    public void populate(int[] concepts, int[] edges, BitSet[] bitsets);

    // ===== extrema =========================================================

    public int getTop();

    public int getBottom();

    public ISet getMaximals();

    public ISet getMinimals();

    // ===== covers / degrees ================================================

    public ISet getLowerCover(int concept);

    public ISet getUpperCover(int concept);

    public Set<Integer> getLowerCoverSet(int concept);

    public Set<Integer> getUpperCoverSet(int concept);

    public Iterator<Integer> getLowerCoverIterator(int concept);

    public Iterator<Integer> getUpperCoverIterator(int concept);

    public int inDegreeOf(int concept);

    public int outDegreeOf(int concept);

    /**
     * All the (transitive) children of the concept. Reflexive: the concept is
     * included in its children.
     */
    public ISet getAllChildren(int concept);

    /**
     * All the (transitive) parents of the concept. Reflexive: the concept is
     * included in its parents.
     */
    public ISet getAllParents(int concept);

    // ===== iterators =======================================================

    public Iterator<Integer> getBasicIterator();

    public Iterator<Integer> getBottomUpIterator();

    public Iterator<Integer> getTopDownIterator();

    public Iterator<Integer> getDepthFirstIterator();

    public Iterator<Integer> getBreadthFirstIterator();

    public Iterator<Integer> getTopologicalIterator();

    public ArrayList<Integer> sortByExtent(boolean increasing);

    public ArrayList<Integer> sortByIntent(boolean increasing);

    // ===== implications (require full intents/extents) =====================

    public List<Implication> getDepthFirstImplications();

    public List<Implication> getBreadthFirstImplications();

    public List<Implication> getTopologicalImplications();

    // ===== full extents / intents ==========================================

    public void computeIntents();

    public void computeExtents();

    public void buildExtentIntent();

    // ===== order transforms ================================================

    /** Transitive reduction (keep only cover edges). */
    public void reduce();

    /** Transitive closure (add all reachable pairs). */
    public void closure();

    /**
     * Remaps reduced and full sets (and the context) from a clarified context
     * back to the original (un-clarified) one.
     */
    public void substitution(IBinaryContext notClarifiedContext, List<ISet> attrClasses, List<ISet> objClasses);

    /**
     * Like {@link #substitution} but remaps only the reduced extents/intents (and
     * the context); the full sets are left untouched. Use when only the reduced
     * sets and the Hasse diagram are consumed downstream (e.g. JSON output).
     */
    public void substitutionReduced(IBinaryContext notClarifiedContext, List<ISet> attrClasses, List<ISet> objClasses);

    /**
     * Turns the order of the transposed context into that of the original:
     * swaps extents/intents, reverses the Hasse diagram, swaps extrema.
     */
    public void dual(IBinaryContext originalContext);

    // ===== paths / clones / listeners ======================================

    /** Shortest directed path between two concepts, or null if none exists. */
    public List<Integer> getShortestPath(int vertex1, int vertex2);

    public ConceptOrder clone();

    public ConceptOrder clone(ISetFactory newFactory);

    public void addPropertyChangeListener(PropertyChangeListener l);

    public void removePropertyChangeListener(PropertyChangeListener l);
}
