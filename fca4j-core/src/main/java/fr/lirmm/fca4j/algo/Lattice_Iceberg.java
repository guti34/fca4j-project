/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.Iterator;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.IConceptOrder;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;


/**
 * The Class Lattice_Iceberg.
 *
 * <p>Same incremental construction as {@link Lattice_AddExtent} (every attribute
 * extent is inserted in turn, splitting the concepts it meets), restricted to
 * an iceberg: extents whose cardinality falls below the threshold are collapsed
 * into a single BOTTOM concept instead of being materialised individually.
 *
 * <p>Follows the same {@code needFullSets} contract as {@link Lattice_AddExtent}
 * and {@link Lattice_ParallelCbO}: full intents are no longer computed
 * unconditionally, only on demand (rule extraction, DOT output, datalog
 * descriptors or RCA).
 */
public class Lattice_Iceberg implements AbstractAlgo<IConceptOrder> {

    private IBinaryContext matrix;
    protected ISetFactory factory;
    private IConceptOrder order;
    private Chrono chrono = null; // eventually a chrono to store execution time
    private int icebergThreshold;
    private int percentage;
    private Integer bottom;

    /**
     * Whether the caller needs the FULL intents. Off by default: the reduced sets
     * and the Hasse diagram are enough for the JSON and XML outputs. The full
     * EXTENTS are always available: the algorithm maintains them itself.
     */
    private boolean needFullSets = false;

    /**
     * Instantiates a new lattice iceberg.
     *
     * @param matrix the matrix
     * @param percentage the percentage
     * @param chrono the chrono
     */
    public Lattice_Iceberg(IBinaryContext matrix, int percentage, Chrono chrono) {
        super();
        this.matrix = matrix;
        this.factory = matrix.getFactory();
        this.chrono = chrono;
        icebergThreshold = matrix.getObjectCount() * percentage / 100;
        this.percentage=percentage;
    }

    /**
     * Instantiates a new lattice iceberg.
     *
     * @param matrix the matrix
     * @param percentage the percentage
     */
    public Lattice_Iceberg(IBinaryContext matrix, int percentage) {
        this(matrix, percentage,null);
    }
    
    /**
     * Gets the percentage.
     *
     * @return the percentage
     */
    public int getPercentage(){
    	return percentage;
    }

    /**
     * Enables/disables materialising the full intents (off by default).
     *
     * @param needFullSets whether the full intents are needed downstream
     */
    public void setNeedFullSets(boolean needFullSets) {
        this.needFullSets = needFullSets;
    }

    public boolean isNeedFullSets() {
        return needFullSets;
    }

    /**
     * Adds the extent.
     *
     * @param extent the extent
     * @param generatorParam the generator param
     * @return the int
     * @throws Exception the exception
     */
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
     * <p>Walks the lower covers with the iterator rather than
     * {@code getLowerCoverSet}, which builds a fresh {@code HashSet} of boxed
     * integers on every call — in the innermost loop of the whole algorithm.
     */
    private int getSmallestContainingConcept(ISet extent, int generator) {
        boolean isMaximal = true;
        while (isMaximal) {
            isMaximal = false;
            for (Iterator<Integer> it = order.getLowerCoverIterator(generator); it.hasNext();) {
                int child = it.next();
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
        return "Iceberg";
    }

    /**
     * Gets the result.
     *
     * @return the result
     */
    @Override
    public IConceptOrder getResult() {
        return order;
    }

    /**
     * Run.
     */
    @Override
    public void run() {
        try {
            if (chrono != null) {
                chrono.start("enum");
            }
            order = new ConceptOrder("LatticeWithAddExtent",matrix,getDescription());
            ISet allObjects = factory.createSet(matrix.getObjectCount());
            allObjects.fill(matrix.getObjectCount());
            int top=order.addConcept(allObjects, factory.createSet(0));
            for (int numAttr = 0; numAttr < matrix.getAttributeCount(); numAttr++) {
                int concept = addExtent(matrix.getExtent(numAttr), top);
                order.getConceptReducedIntent(concept).add(numAttr);
            }
            if (chrono != null) {
                chrono.stop("enum");
            }

            // Reduced extents: an object is introduced at the smallest concept still
            // containing it. Same rule as Lattice_AddExtent, the parallel CbO builder
            // and the C kernel.
            if (chrono != null) {
                chrono.start("rextents");
            }
            for (Iterator<Integer> it=order.getBasicIterator();it.hasNext();  ) {
                int concept=it.next();
                ISet rExtent = factory.clone(order.getConceptExtent(concept));
                for (Iterator<Integer> itChild = order.getLowerCoverIterator(concept); itChild.hasNext();) {
                    rExtent.removeAll(order.getConceptExtent(itChild.next()));
                }
                order.getConceptReducedExtent(concept).addAll(rExtent);
            }
            if (chrono != null) {
                chrono.stop("rextents");
            }

            // Full intents only on demand: the algorithm never reads them, and the
            // JSON/XML writers use the reduced sets only.
            if (needFullSets) {
                if (chrono != null) {
                    chrono.start("intents");
                }
                order.computeIntents();
                if (chrono != null) {
                    chrono.stop("intents");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
