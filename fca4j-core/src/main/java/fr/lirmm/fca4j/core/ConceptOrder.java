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

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.jgrapht.alg.TransitiveClosure;
import org.jgrapht.alg.TransitiveReduction;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;

import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;

/**
 *
 * @author agutierr
 */
public class ConceptOrder implements IConceptOrder, Cloneable {

    private int counter = 0;
    protected String id;
    protected String algoName;
    protected IBinaryContext context;
    ISetFactory factory;
    /**
     * The graph.
     */
    protected SimpleDirectedGraph<Integer, DefaultEdge> hierarchy = new SimpleDirectedGraph<>(DefaultEdge.class);
    /**
     * maximal and minimal nodes
     */
    protected ISet minimals;
    protected ISet maximals;
    /**
     * the concepts
     */
//    protected HashMap<Integer, IConcept> concepts=new HashMap<>();
    protected HashMap<Integer, ISet> extents = new HashMap<>();
    protected HashMap<Integer, ISet> rextents = new HashMap<>();
    protected HashMap<Integer, ISet> intents = new HashMap<>();
    protected HashMap<Integer, ISet> rintents = new HashMap<>();
    /**
     * support for listeners
     */
    private final transient PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    public ConceptOrder(String id, IBinaryContext context, String algoName) {
        this.id = id;
        this.context = context;
        this.algoName = algoName;
        this.factory = context.getFactory();
        maximals = factory.createSet(context.getAttributeCount()+context.getObjectCount());
        minimals = factory.createSet(context.getAttributeCount()+context.getObjectCount());
/*            JSONImporter importer=new JSONImporter();
            importer.setVertexFactory(new Function<String,Integer>(){
            @Override
            public Integer apply(String t) {
                return Integer.parseInt(t);
            }
        });
            importer.importGraph(hierarchy, new StringReader(jsonGraph));            
         */
    }

    public void populate(int[] concepts, int[] edges, BitSet[] bitsets, boolean buildExtentIntent) {
        // add vertices to graph, rintent and rextent
        int ex = 0, in = 1;
        for (int i = 0; i < concepts.length; i++, ex += 2, in += 2) {
            int numConcept = concepts[i];
            if (numConcept >= counter) {
                counter = numConcept + 1;
            }
            hierarchy.addVertex(numConcept);
            rextents.put(numConcept, factory.createSet(bitsets[ex],context.getObjectCount()));
            rintents.put(numConcept, factory.createSet(bitsets[in],context.getAttributeCount()));
            extents.put(numConcept, factory.createSet(context.getObjectCount()));
            intents.put(numConcept, factory.createSet(context.getAttributeCount()));
        }
        // add edges to graph
        for (int edgeCounter = 0; edgeCounter < edges.length; edgeCounter += 2) {
            hierarchy.addEdge(edges[edgeCounter], edges[edgeCounter + 1]);
        }
        // populate extents and intents
        for (Iterator<Integer> it = getTopDownIterator(); it.hasNext();) {
            int concept = it.next();
            // retrieve maximals and minimals
            if (hierarchy.outDegreeOf(concept) == 0) {
                maximals.add(concept);
            }
            if (hierarchy.inDegreeOf(concept) == 0) {
                minimals.add(concept);
            }
            // build intent
            getConceptIntent(concept).addAll(getConceptReducedIntent(concept));
            for (Iterator<Integer> itChild = getLowerCoverIterator(concept); itChild.hasNext();) {
                int sub = itChild.next();
                getConceptIntent(sub).addAll(getConceptIntent(concept));
            }
        }
        for (Iterator<Integer> it = getBottomUpIterator(); it.hasNext();) {
            int concept = it.next();
            // build extent
            getConceptExtent(concept).addAll(getConceptReducedExtent(concept));
            for (Iterator<Integer> itParent = getUpperCoverIterator(concept); itParent.hasNext();) {
                int parent = itParent.next();
                getConceptExtent(parent).addAll(getConceptExtent(concept));
            }
        }
        // populate context
        for (Iterator<Integer> it = getBasicIterator(); it.hasNext();) {
            int concept = it.next();
            for (Iterator<Integer> itAttr = getConceptReducedIntent(concept).iterator(); itAttr.hasNext();) {
                int numAttr = itAttr.next();
                context.setExtent(numAttr, getConceptExtent(concept));
            }
            for (Iterator<Integer> itObj = getConceptReducedExtent(concept).iterator(); itObj.hasNext();) {
                int numObj = itObj.next();
                context.setIntent(numObj, getConceptIntent(concept));
            }
        }

    }

    public String getId() {
        return id;
    }

    public IBinaryContext getContext() {
        return context;
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }

    public int addConcept(ISet extent, ISet intent) {
        return addConcept(extent, intent, factory.createSet(context.getObjectCount()), factory.createSet(context.getAttributeCount()));
    }

    public int addConcept(ISet extent, ISet intent, ISet rextent, ISet rintent) {
        int numConcept = counter++;
        hierarchy.addVertex(numConcept);
        extents.put(numConcept, extent);
        rextents.put(numConcept, rextent);
        intents.put(numConcept, intent);
        rintents.put(numConcept, rintent);
        maximals.add(numConcept);
        minimals.add(numConcept);
        pcs.firePropertyChange("ADD_CONCEPT", numConcept, numConcept);
        return numConcept;

    }

    public ISet getConceptExtent(int numConcept) {
        return extents.get(numConcept);
    }

    public ISet getConceptIntent(int numConcept) {
        return intents.get(numConcept);
    }

    public ISet getConceptReducedExtent(int numConcept) {
        return rextents.get(numConcept);
    }

    public void setReducedExtent(int numConcept, ISet rextent) {
        rextents.put(numConcept, rextent);
    }

    public ISet getConceptReducedIntent(int numConcept) {
        return rintents.get(numConcept);
    }

    public void setReducedIntent(int numConcept, ISet rintent) {
        rintents.put(numConcept, rintent);
    }

    public void removeConcept(int numConcept) {
        extents.remove(numConcept);
        intents.remove(numConcept);
        rextents.remove(numConcept);
        rintents.remove(numConcept);
        maximals.remove(numConcept);
        minimals.remove(numConcept);
        int[] sources = new int[hierarchy.incomingEdgesOf(numConcept).size()];
        int i = 0;
        for (DefaultEdge edge : hierarchy.incomingEdgesOf(numConcept)) {
            sources[i++] = hierarchy.getEdgeSource(edge);
        }
        for (int source : sources) {
            removePrecedenceConnection(source, numConcept);
            if(outDegreeOf(source)==0)
                maximals.add(source);
        }
        int[] targets = new int[hierarchy.outgoingEdgesOf(numConcept).size()];
        i = 0;
        for (DefaultEdge edge : hierarchy.outgoingEdgesOf(numConcept)) {
            targets[i++] = hierarchy.getEdgeTarget(edge);
        }
        for (int target : targets) {
            removePrecedenceConnection(numConcept, target);
            if(inDegreeOf(target)==0)
                minimals.add(target);
        }
        hierarchy.removeVertex(numConcept);
        pcs.firePropertyChange("REMOVE_CONCEPT", numConcept, numConcept);
    }

    public void addPrecedenceConnection(int lower, int greater) {
        hierarchy.addEdge(lower, greater);
        maximals.remove(lower);
        minimals.remove(greater);
        pcs.firePropertyChange("ADD_EDGE", lower, greater);
    }

    public void removePrecedenceConnection(int lower, int greater) {
        if (hierarchy.removeEdge(lower, greater) != null) {
            if (hierarchy.outDegreeOf(lower) == 0 && !maximals.contains(lower)) {
                maximals.add(lower);
            }
            if (hierarchy.inDegreeOf(greater) == 0 && !minimals.contains(greater)) {
                minimals.add(greater);
            }
            pcs.firePropertyChange("REMOVE_EDGE", lower, greater);
        }
//        else  Exceptions.printStackTrace(new Exception("remove edge failed"));
    }

    /**
     *
     */
    public int getTop() {
        return maximals.iterator().next();
    }

    public ISet getMaximals() {
        return maximals;
    }

    public ISet getMinimals() {
        return minimals;
    }

    public int getBottom() {
        return minimals.iterator().next();
    }

    public Set<Integer> getConcepts() {
        return hierarchy.vertexSet();
    }

    /**
     * returns all the children of the current concept. This relation is
     * reflexive so the current concept is included in its children
     */

    public ISet getAllChildren(int concept) {
        ISet children = factory.createSet(context.getObjectCount()+context.getAttributeCount());
        populateChildren(concept, children);
        return children;
    }

    private void populateChildren(int concept, ISet children) {
        children.add(concept);
        for (Iterator<Integer> it = getLowerCoverIterator(concept); it.hasNext();) {
            populateChildren(it.next(), children);
        }
    }

    /**
     * Computes and returns all the parents of the current concept. This
     * relation is reflexive so the current concept is included in its parents
     */
    public ISet getAllParents(int concept) {
        ISet parents = factory.createSet(context.getObjectCount()+context.getAttributeCount());
        populateParents(concept, parents);
        return parents;
    }

    private void populateParents(int concept, ISet parents) {
        parents.add(concept);
        for (Iterator<Integer> it = getUpperCoverIterator(concept); it.hasNext();) {
            populateParents(it.next(), parents);
        }
    }

    public int inDegreeOf(int concept) {
        return hierarchy.inDegreeOf(concept);
    }

    public int outDegreeOf(int concept) {
        return hierarchy.outDegreeOf(concept);
    }

    public Set<Integer> getLowerCoverSet(int concept) {
        Set<DefaultEdge> itEdges = hierarchy.incomingEdgesOf(concept);
        Set<Integer> set = new HashSet<>();
        for (DefaultEdge edge : itEdges) {
            set.add(hierarchy.getEdgeSource(edge));
        }
        return set;
    }

    public Set<Integer> getUpperCoverSet(int concept) {
        Set<DefaultEdge> itEdges = hierarchy.outgoingEdgesOf(concept);
        Set<Integer> set = new HashSet<>();
        for (DefaultEdge edge : itEdges) {
            set.add(hierarchy.getEdgeTarget(edge));
        }
        return set;
    }
    public ISet getLowerCover(int concept) {
        Set<DefaultEdge> itEdges = hierarchy.incomingEdgesOf(concept);
        ISet set = factory.createSet();
        for (DefaultEdge edge : itEdges) {
            set.add(hierarchy.getEdgeSource(edge));
        }
        return set;
    }

    public ISet getUpperCover(int concept) {
        Set<DefaultEdge> itEdges = hierarchy.outgoingEdgesOf(concept);
        ISet set = factory.createSet(context.getAttributeCount()+context.getObjectCount());
        for (DefaultEdge edge : itEdges) {
            set.add(hierarchy.getEdgeTarget(edge));
        }
        return set;
    }

    public Iterator<Integer> getLowerCoverIterator(int concept) {
        Iterator<DefaultEdge> itEdges = hierarchy.incomingEdgesOf(concept).iterator();
        Iterator<Integer> it = new Iterator<Integer>() {
            @Override
            public boolean hasNext() {
                return itEdges.hasNext();
            }

            @Override
            public Integer next() {
                DefaultEdge edge = itEdges.next();
                return hierarchy.getEdgeSource(edge);
            }
        };
        return it;
    }

    public Iterator<Integer> getUpperCoverIterator(int concept) {
        Iterator<DefaultEdge> itEdges = hierarchy.outgoingEdgesOf(concept).iterator();
        Iterator<Integer> it = new Iterator<Integer>() {
            @Override
            public boolean hasNext() {
                return itEdges.hasNext();
            }

            @Override
            public Integer next() {
                DefaultEdge edge = itEdges.next();
                return hierarchy.getEdgeTarget(edge);
            }
        };
        return it;
    }

    public boolean isFusion(int concept) {
        return (getConceptReducedExtent(concept).cardinality() > 1);
    }

    public boolean isNewConcept(int concept) {
        return (getConceptReducedExtent(concept).cardinality() == 0);
    }

    public boolean isDummy(int concept) {
        return (getConceptExtent(concept).cardinality() == 0 || getConceptIntent(concept).cardinality() == 0);
    }

    public ArrayList<Integer> sortByExtent(boolean increasing) {
        ArrayList<Integer> list = new ArrayList<>(hierarchy.vertexSet());
        if (increasing) {
            list.sort((Integer c1, Integer c2) -> Integer.compare(getConceptExtent(c1).cardinality(), getConceptExtent(c2).cardinality()));
        } else {
            list.sort((Integer c1, Integer c2) -> -Integer.compare(getConceptExtent(c2).cardinality(), getConceptExtent(c1).cardinality()));
        }
        return list;
    }

    public ArrayList<Integer> sortByIntent(boolean increasing) {
        ArrayList<Integer> list = new ArrayList<>(hierarchy.vertexSet());
        if (increasing) {
            list.sort((Integer c1, Integer c2) -> Integer.compare(getConceptIntent(c1).cardinality(), getConceptIntent(c2).cardinality()));
        } else {
            list.sort((Integer c1, Integer c2) -> -Integer.compare(getConceptIntent(c2).cardinality(), getConceptIntent(c1).cardinality()));
        }
        return list;
    }

    public Iterator<Integer> getBasicIterator() {
        return hierarchy.vertexSet().iterator();
    }

    public Iterator<Integer> getBottomUpIterator() {
        return new TopologicalOrderIterator(hierarchy);
    }

    public Iterator<Integer> getTopDownIterator() {
        ArrayList<Integer> vertices = new ArrayList<>();
        for (Iterator<Integer> it = new TopologicalOrderIterator(hierarchy); it.hasNext();) {
            vertices.add(0, it.next());
        }
        return vertices.iterator();
    }

    public int getConceptCount() {
        return hierarchy.vertexSet().size();
    }

    /*
public void exportJSON(Writer writer){
             JSONExporter exporter=new JSONExporter();
             exporter.setVertexIdProvider(new Function<Integer, String>(){
                 @Override
                 public String apply(Integer t) {
                     return t.toString();
                 }
             });
             exporter.exportGraph(hierarchy, writer);
    }
     */
    public int getEdgeCount() {
        return hierarchy.edgeSet().size();
    }

    public String getAlgoName() {
        return algoName;
    }

    /* introduce to fix a bug with ares
    Attention: This transformation remove edges without notification to listeners
     */
    public void reduce() {
        TransitiveReduction.INSTANCE.reduce(hierarchy);
        minimals.clear(minimals.capacity());
        maximals.clear(maximals.capacity());
        for(int concept:hierarchy.vertexSet())
        {
            if(inDegreeOf(concept)==0)
                minimals.add(concept);
            if(outDegreeOf(concept)==0)
                maximals.add(concept);
        }
    }

    public void closure() {
        TransitiveClosure.INSTANCE.closeSimpleDirectedGraph(hierarchy);
    }

    public void computeIntents(){
        // populate extents and intents
        for (Iterator<Integer> it = getTopDownIterator(); it.hasNext();) {
            int concept = it.next();
            // build intent
            getConceptIntent(concept).addAll(getConceptReducedIntent(concept));
            for (Iterator<Integer> itChild = getLowerCoverIterator(concept); itChild.hasNext();) {
                int sub = itChild.next();
                getConceptIntent(sub).addAll(getConceptIntent(concept));
            }
        }
    }
    public void computeExtents(){    
        for (Iterator<Integer> it = getBottomUpIterator(); it.hasNext();) {
            int concept = it.next();
            // build extent
            getConceptExtent(concept).addAll(getConceptReducedExtent(concept));
            for (Iterator<Integer> itParent = getUpperCoverIterator(concept); itParent.hasNext();) {
                int parent = itParent.next();
                getConceptExtent(parent).addAll(getConceptExtent(concept));
            }
        }    	
    }
    public void setId(String name) {
        this.id = name;
    }

    @Override
    public ConceptOrder clone() {
        ConceptOrder newOrder = new ConceptOrder(id, context, algoName);
        newOrder.counter = counter;
        newOrder.extents = (HashMap<Integer, ISet>) extents.clone();
        newOrder.rextents = (HashMap<Integer, ISet>) rextents.clone();
        newOrder.intents = (HashMap<Integer, ISet>) intents.clone();
        newOrder.rintents = (HashMap<Integer, ISet>) rintents.clone();
        newOrder.factory = factory;
        newOrder.hierarchy = (SimpleDirectedGraph<Integer, DefaultEdge>) hierarchy.clone();
        newOrder.maximals = maximals.clone();
        newOrder.minimals = minimals.clone();
        return newOrder;
    }
    @Override
    public ConceptOrder clone(ISetFactory newFactory) {
        ConceptOrder newOrder = new ConceptOrder(id, context, algoName);
        newOrder.counter = counter;
        for(int numattr:extents.keySet())
        {
            ISet set=extents.get(numattr);
            ISet newSet= newFactory.createSet(set.toBitSet(), set.capacity());
            newOrder.extents.put(numattr, newSet);
        }
        for(int numattr:rextents.keySet())
        {
            ISet set=rextents.get(numattr);
            ISet newSet= newFactory.createSet(set.toBitSet(), set.capacity());
            newOrder.rextents.put(numattr, newSet);
        }
        for(int numobj:intents.keySet())
        {
            ISet set=intents.get(numobj);
            ISet newSet= newFactory.createSet(set.toBitSet(), set.capacity());
            newOrder.intents.put(numobj, newSet);
        }
        for(int numobj:rintents.keySet())
        {
            ISet set=rintents.get(numobj);
            ISet newSet= newFactory.createSet(set.toBitSet(), set.capacity());
            newOrder.rintents.put(numobj, newSet);
        }
       newOrder.factory = factory;
        newOrder.hierarchy = (SimpleDirectedGraph<Integer, DefaultEdge>) hierarchy.clone();
        for(int max:maximals.toList())
        {
            newOrder.maximals.add(max);
        }
        for(int min:minimals.toList())
        {
            newOrder.minimals.add(min);
        }
        return newOrder;
    }
	private ISet substitution(ISet set,List<ISet> classes) {
		ISet result=factory.createSet();
		for(Iterator<Integer> it=set.iterator();it.hasNext();)
				result.addAll(classes.get(it.next()));
		return result;
	}
    public void substitution(IBinaryContext notClarifiedContext,List<ISet> attrClasses, List<ISet> objClasses) {
    	this.context=notClarifiedContext;
    	HashMap<Integer,ISet> newExtents=new HashMap<>();
    	for(int concept:extents.keySet()) {
    		ISet newExtent=substitution(extents.get(concept),objClasses);
    		newExtents.put(concept, newExtent);
    	}
    	extents=newExtents;
    	HashMap<Integer,ISet> newIntents=new HashMap<>();
    	for(int concept:intents.keySet()) {
    		ISet newIntent=substitution(intents.get(concept),attrClasses);
    		newIntents.put(concept, newIntent);
    	}
    	intents=newIntents;
    	HashMap<Integer,ISet> newRExtents=new HashMap<>();
    	for(int concept:rextents.keySet()) {
    		ISet newRExtent=substitution(rextents.get(concept),objClasses);
    		newRExtents.put(concept, newRExtent);
    	}
    	rextents=newRExtents;
    	HashMap<Integer,ISet> newRIntents=new HashMap<>();
    	for(int concept:rintents.keySet()) {
    		ISet newRIntent=substitution(rintents.get(concept),attrClasses);
    		newRIntents.put(concept, newRIntent);
    	}
    	rintents=newRIntents;
}
}
