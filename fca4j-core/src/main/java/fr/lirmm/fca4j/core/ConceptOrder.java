/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
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

import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.TransitiveClosure;
import org.jgrapht.alg.TransitiveReduction;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.EdgeReversedGraph;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jgrapht.traverse.DepthFirstIterator;
import org.jgrapht.traverse.TopologicalOrderIterator;

import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;

/**
 * The Class ConceptOrder.
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

    /**
     * Instantiates a new concept order.
     *
     * @param id the id
     * @param context the context
     * @param algoName the algo name
     */
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

    /**
     * Populate.
     *
     * @param concepts the concepts
     * @param edges the edges
     * @param bitsets the bitsets
     * @param buildExtentIntent the build extent intent
     */
    public void populate(int[] concepts, int[] edges, BitSet[] bitsets) {
        // add vertices to graph, rintent and rextent
        int ex = 0, in = 1;
        for (int i = 0; i < concepts.length; i++, ex += 2, in += 2) {
            int numConcept = concepts[i];
            if (numConcept >= counter) {
                counter = numConcept + 1;
            }
            hierarchy.addVertex(numConcept);
            rextents.put(numConcept, factory.createSet(bitsets[ex], context.getObjectCount()));
            rintents.put(numConcept, factory.createSet(bitsets[in], context.getAttributeCount()));
            extents.put(numConcept, factory.createSet(context.getObjectCount()));
            intents.put(numConcept, factory.createSet(context.getAttributeCount()));
        }
        // add edges to graph
        for (int edgeCounter = 0; edgeCounter < edges.length; edgeCounter += 2) {
            hierarchy.addEdge(edges[edgeCounter], edges[edgeCounter + 1]);
        }
        // maximals / minimals (toujours nécessaires, indépendants des sets complets)
        for (int i = 0; i < concepts.length; i++) {
            int concept = concepts[i];
            if (hierarchy.outDegreeOf(concept) == 0) {
                maximals.add(concept);
            }
            if (hierarchy.inDegreeOf(concept) == 0) {
                minimals.add(concept);
            }
        }
    }
        // intents/extents complets : nécessaires uniquement pour les consommateurs
        // qui les lisent (extraction de règles…). La sortie JSON n'utilise que les
        // sets réduits
    public void buildExtentIntent() {
            // build full intents (top-down)
            for (Iterator<Integer> it = getTopDownIterator(); it.hasNext();) {
                int concept = it.next();
                getConceptIntent(concept).addAll(getConceptReducedIntent(concept));
                for (Iterator<Integer> itChild = getLowerCoverIterator(concept); itChild.hasNext();) {
                    int sub = itChild.next();
                    getConceptIntent(sub).addAll(getConceptIntent(concept));
                }
            }
            // build full extents (bottom-up)
            for (Iterator<Integer> it = getBottomUpIterator(); it.hasNext();) {
                int concept = it.next();
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
    /**
     * Gets the id.
     *
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the context.
     *
     * @return the context
     */
    public IBinaryContext getContext() {
        return context;
    }

    /**
     * Adds the property change listener.
     *
     * @param l the l
     */
    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    /**
     * Removes the property change listener.
     *
     * @param l the l
     */
    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }

    /**
     * Adds the concept.
     *
     * @param extent the extent
     * @param intent the intent
     * @return the int
     */
    public int addConcept(ISet extent, ISet intent) {
        return addConcept(extent, intent, factory.createSet(context.getObjectCount()), factory.createSet(context.getAttributeCount()));
    }

    /**
     * Adds the concept.
     *
     * @param extent the extent
     * @param intent the intent
     * @param rextent the rextent
     * @param rintent the rintent
     * @return the int
     */
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

    /**
     * Gets the concept extent.
     *
     * @param numConcept the num concept
     * @return the concept extent
     */
    public ISet getConceptExtent(int numConcept) {
        return extents.get(numConcept);
    }

    /**
     * Gets the concept intent.
     *
     * @param numConcept the num concept
     * @return the concept intent
     */
    public ISet getConceptIntent(int numConcept) {
        return intents.get(numConcept);
    }

    /**
     * Gets the concept reduced extent.
     *
     * @param numConcept the num concept
     * @return the concept reduced extent
     */
    public ISet getConceptReducedExtent(int numConcept) {
        return rextents.get(numConcept);
    }

    /**
     * Sets the reduced extent.
     *
     * @param numConcept the num concept
     * @param rextent the rextent
     */
    public void setReducedExtent(int numConcept, ISet rextent) {
        rextents.put(numConcept, rextent);
    }

    /**
     * Gets the concept reduced intent.
     *
     * @param numConcept the num concept
     * @return the concept reduced intent
     */
    public ISet getConceptReducedIntent(int numConcept) {
        return rintents.get(numConcept);
    }

    /**
     * Sets the reduced intent.
     *
     * @param numConcept the num concept
     * @param rintent the rintent
     */
    public void setReducedIntent(int numConcept, ISet rintent) {
        rintents.put(numConcept, rintent);
    }

    /**
     * Removes the concept.
     *
     * @param numConcept the num concept
     */
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

    /**
     * Adds the precedence connection.
     *
     * @param lower the lower
     * @param greater the greater
     */
    public void addPrecedenceConnection(int lower, int greater) {
        hierarchy.addEdge(lower, greater);
        maximals.remove(lower);
        minimals.remove(greater);
        pcs.firePropertyChange("ADD_EDGE", lower, greater);
    }

    /**
     * Removes the precedence connection.
     *
     * @param lower the lower
     * @param greater the greater
     */
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
     * Gets the top.
     *
     * @return the top
     */
    public int getTop() {
        return maximals.iterator().next();
    }

    /**
     * Gets the maximals.
     *
     * @return the maximals
     */
    public ISet getMaximals() {
        return maximals;
    }

    /**
     * Gets the minimals.
     *
     * @return the minimals
     */
    public ISet getMinimals() {
        return minimals;
    }

    /**
     * Gets the bottom.
     *
     * @return the bottom
     */
    public int getBottom() {
        return minimals.iterator().next();
    }

    /**
     * Gets the concepts.
     *
     * @return the concepts
     */
    public Set<Integer> getConcepts() {
        return hierarchy.vertexSet();
    }

    /**
     * returns all the children of the current concept. This relation is
     * reflexive so the current concept is included in its children
     *
     * @param concept the concept
     * @return the all children
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
     *
     * @param concept the concept
     * @return the all parents
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

    /**
     * In degree of.
     *
     * @param concept the concept
     * @return the int
     */
    public int inDegreeOf(int concept) {
        return hierarchy.inDegreeOf(concept);
    }

    /**
     * Out degree of.
     *
     * @param concept the concept
     * @return the int
     */
    public int outDegreeOf(int concept) {
        return hierarchy.outDegreeOf(concept);
    }

    /**
     * Gets the lower cover set.
     *
     * @param concept the concept
     * @return the lower cover set
     */
    public Set<Integer> getLowerCoverSet(int concept) {
        Set<DefaultEdge> itEdges = hierarchy.incomingEdgesOf(concept);
        Set<Integer> set = new HashSet<>();
        for (DefaultEdge edge : itEdges) {
            set.add(hierarchy.getEdgeSource(edge));
        }
        return set;
    }

    /**
     * Gets the upper cover set.
     *
     * @param concept the concept
     * @return the upper cover set
     */
    public Set<Integer> getUpperCoverSet(int concept) {
        Set<DefaultEdge> itEdges = hierarchy.outgoingEdgesOf(concept);
        Set<Integer> set = new HashSet<>();
        for (DefaultEdge edge : itEdges) {
            set.add(hierarchy.getEdgeTarget(edge));
        }
        return set;
    }
    
    /**
     * Gets the lower cover.
     *
     * @param concept the concept
     * @return the lower cover
     */
    public ISet getLowerCover(int concept) {
        Set<DefaultEdge> itEdges = hierarchy.incomingEdgesOf(concept);
        ISet set = factory.createSet();
        for (DefaultEdge edge : itEdges) {
            set.add(hierarchy.getEdgeSource(edge));
        }
        return set;
    }

    /**
     * Gets the upper cover.
     *
     * @param concept the concept
     * @return the upper cover
     */
    public ISet getUpperCover(int concept) {
        Set<DefaultEdge> itEdges = hierarchy.outgoingEdgesOf(concept);
        ISet set = factory.createSet(context.getAttributeCount()+context.getObjectCount());
        for (DefaultEdge edge : itEdges) {
            set.add(hierarchy.getEdgeTarget(edge));
        }
        return set;
    }

    /**
     * Gets the lower cover iterator.
     *
     * @param concept the concept
     * @return the lower cover iterator
     */
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

    /**
     * Gets the upper cover iterator.
     *
     * @param concept the concept
     * @return the upper cover iterator
     */
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

    /**
     * Checks if is fusion.
     *
     * @param concept the concept
     * @return true, if is fusion
     */
    public boolean isFusion(int concept) {
        return (getConceptReducedExtent(concept).cardinality() > 1);
    }

    /**
     * Checks if is new concept.
     *
     * @param concept the concept
     * @return true, if is new concept
     */
    public boolean isNewConcept(int concept) {
        return (getConceptReducedExtent(concept).cardinality() == 0);
    }

    /**
     * Checks if is dummy.
     *
     * @param concept the concept
     * @return true, if is dummy
     */
    public boolean isDummy(int concept) {
        return (getConceptExtent(concept).cardinality() == 0 || getConceptIntent(concept).cardinality() == 0);
    }

    /**
     * Sort by extent.
     *
     * @param increasing the increasing
     * @return the array list
     */
    public ArrayList<Integer> sortByExtent(boolean increasing) {
        ArrayList<Integer> list = new ArrayList<>(hierarchy.vertexSet());
        if (increasing) {
            list.sort((Integer c1, Integer c2) -> Integer.compare(getConceptExtent(c1).cardinality(), getConceptExtent(c2).cardinality()));
        } else {
            list.sort((Integer c1, Integer c2) -> -Integer.compare(getConceptExtent(c2).cardinality(), getConceptExtent(c1).cardinality()));
        }
        return list;
    }

    /**
     * Sort by intent.
     *
     * @param increasing the increasing
     * @return the array list
     */
    public ArrayList<Integer> sortByIntent(boolean increasing) {
        ArrayList<Integer> list = new ArrayList<>(hierarchy.vertexSet());
        if (increasing) {
            list.sort((Integer c1, Integer c2) -> Integer.compare(getConceptIntent(c1).cardinality(), getConceptIntent(c2).cardinality()));
        } else {
            list.sort((Integer c1, Integer c2) -> -Integer.compare(getConceptIntent(c2).cardinality(), getConceptIntent(c1).cardinality()));
        }
        return list;
    }

    /**
     * Gets the basic iterator.
     *
     * @return the basic iterator
     */
    public Iterator<Integer> getBasicIterator() {
        return hierarchy.vertexSet().iterator();
    }

    /**
     * Gets the bottom up iterator.
     *
     * @return the bottom up iterator
     */
    public Iterator<Integer> getBottomUpIterator() {
        return new TopologicalOrderIterator<Integer, DefaultEdge>(hierarchy);
    }

    /**
     * Gets the top down iterator.
     *
     * @return the top down iterator
     */
    public Iterator<Integer> getTopDownIterator() {
        ArrayList<Integer> vertices = new ArrayList<>();
        for (Iterator<Integer> it = new TopologicalOrderIterator<>(hierarchy); it.hasNext();) {
            vertices.add(0, it.next());
        }
        return vertices.iterator();
    }
    /**
     * Gets the depth first concept iterator.
     *
     * @return the top down iterator
     */
    public Iterator<Integer> getDepthFirstIterator() {
        ArrayList<Integer> vertices = new ArrayList<>();
         Graph<Integer, DefaultEdge> reversedGraph = new EdgeReversedGraph<>(hierarchy);   
        
        for (Iterator<Integer> it = new DepthFirstIterator<>(reversedGraph); it.hasNext();) {
            vertices.add(it.next());
        }
/*        System.out.println("depth first:");
        for(int v:vertices) {
        	for(int attr:getConceptReducedIntent(v).toList())
        		System.out.print(getContext().getAttributeName(attr));
        	System.out.println();
        }
*/        
        return vertices.iterator();
    }
    /**
     * Gets the depth first concept iterator.
     *
     * @return the top down iterator
     */
    public Iterator<Integer> getBreadthFirstIterator() {
        ArrayList<Integer> vertices = new ArrayList<>();
        Graph<Integer, DefaultEdge> reversedGraph = new EdgeReversedGraph<>(hierarchy);        
        for (Iterator<Integer> it = new BreadthFirstIterator<>(reversedGraph,maximals.toList()); it.hasNext();) {
            vertices.add(it.next());
        }
        return vertices.iterator();
    }
    /**
     * Gets the topologic concept iterator.
     *
     * @return the top down iterator
     */
    public Iterator<Integer> getTopologicalIterator() {
        ArrayList<Integer> vertices = new ArrayList<>();
        Graph<Integer, DefaultEdge> reversedGraph = new EdgeReversedGraph<>(hierarchy);        
        for (Iterator<Integer> it = new TopologicalOrderIterator<>(reversedGraph); it.hasNext();) {
            vertices.add(it.next());
        }
        return vertices.iterator();
    }
    public List<Implication> getDepthFirstImplications(){
    	ArrayList<Implication> implications=new ArrayList<>();
    	for(Iterator<Integer> it=getDepthFirstIterator();it.hasNext();) {
    		int concept=it.next();
    		for(DefaultEdge edge:hierarchy.outgoingEdgesOf(concept))
    		{
    			ISet premise=getConceptReducedIntent(hierarchy.getEdgeSource(edge));
    			if(!premise.isEmpty())
    			{
    				ISet conclusion=getConceptIntent(hierarchy.getEdgeTarget(edge));    		
    				implications.add(new Implication(premise,conclusion,getConceptExtent(concept)));

    			}
    		}
    	}
    	return implications;
    }
    public List<Implication> getBreadthFirstImplications(){
    	ArrayList<Implication> implications=new ArrayList<>();
    	for(Iterator<Integer> it=getBreadthFirstIterator();it.hasNext();) {
    		int concept=it.next();
    		for(DefaultEdge edge:hierarchy.outgoingEdgesOf(concept))
    		{
    			ISet premise=getConceptReducedIntent(hierarchy.getEdgeSource(edge));
    			if(!premise.isEmpty())
    			{
    				ISet conclusion=getConceptIntent(hierarchy.getEdgeTarget(edge));    		
    				implications.add(new Implication(premise,conclusion,getConceptExtent(concept)));

    			}
    		}
    	}
    	return implications;
    }
    public List<Implication> getTopologicalImplications(){
    	ArrayList<Implication> implications=new ArrayList<>();
    	for(Iterator<Integer> it=getTopologicalIterator();it.hasNext();) {
    		int concept=it.next();
    		for(DefaultEdge edge:hierarchy.outgoingEdgesOf(concept))
    		{
    			ISet premise=getConceptReducedIntent(hierarchy.getEdgeSource(edge));
    			if(!(premise.isEmpty()))
    			{
    				ISet conclusion=getConceptIntent(hierarchy.getEdgeTarget(edge));  
//    				if(!conclusion.isEmpty())
    					implications.add(new Implication(premise,conclusion,getConceptExtent(concept)));

    			}
    		}
    	}
    	return implications;
    }
    /**
     * Gets the concept count.
     *
     * @return the concept count
     */
    public int getConceptCount() {
        return hierarchy.vertexSet().size();
    }

    /**
     * Gets the edge count.
     *
     * @return the edge count
     */
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

    /**
     * Gets the algo name.
     *
     * @return the algo name
     */
    public String getAlgoName() {
        return algoName;
    }

    /**
     * Reduce.
     */
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

    /**
     * Closure.
     */
    public void closure() {
        TransitiveClosure.INSTANCE.closeSimpleDirectedGraph(hierarchy);
    }

    /**
     * Compute intents.
     */
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
    
    /**
     * Compute extents.
     */
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
    
    /**
     * Sets the id.
     *
     * @param name the new id
     */
    public void setId(String name) {
        this.id = name;
    }

    /**
     * Clone.
     *
     * @return the concept order
     */
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
    
    /**
     * Clone.
     *
     * @param newFactory the new factory
     * @return the concept order
     */
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
    
    /**
     * Substitution.
     *
     * @param notClarifiedContext the not clarified context
     * @param attrClasses the attr classes
     * @param objClasses the obj classes
     */
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
    /**
     * Bulk insertion of cover edges (lower -> greater). Unlike
     * addPrecedenceConnection, skips per-edge property-change events and per-edge
     * extrema updates: maximal/minimal nodes are recomputed once at the end, and
     * boxed vertex keys are reused to avoid re-boxing 2*|E| times.
     */
    public void addPrecedenceConnections(int[] lowers, int[] greaters) {
        Integer[] box = new Integer[counter];
        for (int i = 0; i < lowers.length; i++) {
            int lo = lowers[i];
            int gr = greaters[i];
            Integer bl = box[lo];
            if (bl == null) { bl = Integer.valueOf(lo); box[lo] = bl; }
            Integer bg = box[gr];
            if (bg == null) { bg = Integer.valueOf(gr); box[gr] = bg; }
            hierarchy.addEdge(bl, bg);
        }
        maximals.clear(maximals.capacity());
        minimals.clear(minimals.capacity());
        for (int v : hierarchy.vertexSet()) {
            if (hierarchy.outDegreeOf(v) == 0) maximals.add(v);
            if (hierarchy.inDegreeOf(v) == 0) minimals.add(v);
        }
    }

    /**
     * Like substitution(...) but remaps only the reduced extents/intents (and the
     * context). The full extents/intents are left as-is (clarified space): use it
     * when only the reduced sets and the Hasse diagram are consumed downstream
     * (e.g. the JSON lattice output), to avoid remapping hundreds of thousands of
     * full extents needlessly.
     */
    public void substitutionReduced(IBinaryContext notClarifiedContext, List<ISet> attrClasses,
            List<ISet> objClasses) {
        this.context = notClarifiedContext;
        HashMap<Integer, ISet> newRExtents = new HashMap<>();
        for (int concept : rextents.keySet()) {
            newRExtents.put(concept, substitution(rextents.get(concept), objClasses));
        }
        rextents = newRExtents;
        HashMap<Integer, ISet> newRIntents = new HashMap<>();
        for (int concept : rintents.keySet()) {
            newRIntents.put(concept, substitution(rintents.get(concept), attrClasses));
        }
        rintents = newRIntents;
    }
    public List<Integer> getShortestPath(int vertex1,int vertex2){
        DijkstraShortestPath<Integer, DefaultEdge> dijkstraAlg = new DijkstraShortestPath<>(hierarchy);
        GraphPath path=dijkstraAlg.getPath(vertex1, vertex2);
        return path==null ? null : path.getVertexList();

    }

    /**
     * Turns this order (the lattice of the transposed context) into the lattice of
     * the original context: swaps extents/intents and their reduced counterparts,
     * reverses the Hasse diagram, swaps minimal/maximal nodes, and resets the
     * context. Used by the auto-orientation in the parallel CbO lattice builder.
     *
     * @param originalContext the (untransposed) context the order should describe
     */
    public void dual(IBinaryContext originalContext) {
        HashMap<Integer, ISet> swap = extents;
        extents = intents;
        intents = swap;
        swap = rextents;
        rextents = rintents;
        rintents = swap;

        // reverse every Hasse edge (the transposed lattice is the order-dual)
        SimpleDirectedGraph<Integer, DefaultEdge> reversed = new SimpleDirectedGraph<>(DefaultEdge.class);
        for (Integer v : hierarchy.vertexSet()) {
            reversed.addVertex(v);
        }
        for (DefaultEdge e : hierarchy.edgeSet()) {
            reversed.addEdge(hierarchy.getEdgeTarget(e), hierarchy.getEdgeSource(e));
        }
        hierarchy = reversed;

        ISet swapSet = maximals;
        maximals = minimals;
        minimals = swapSet;

        this.context = originalContext;
    }
    }
