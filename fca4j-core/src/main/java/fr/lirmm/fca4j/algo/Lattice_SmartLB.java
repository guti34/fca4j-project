package fr.lirmm.fca4j.algo;

import java.util.Iterator;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

public class Lattice_SmartLB implements AbstractAlgo<ConceptOrder> {
    private IBinaryContext matrix;    
    private ConceptOrder order;    
    protected ISetFactory factory;
    private Chrono chrono = null; // eventually a chrono to store execution time 
    /**
     * Instantiates a new lattice algo.
     *
     * @param matrix the matrix
     * @param chrono the chrono
     */
    public Lattice_SmartLB(IBinaryContext matrix, Chrono chrono) {
        super();
        this.matrix = matrix;
        this.factory = matrix.getFactory();
        this.chrono = chrono;
    }

    /**
     * Instantiates a new lattice algo.
     *
     * @param matrix the matrix
     */
    public Lattice_SmartLB(IBinaryContext matrix) {
        this(matrix, null);
    }


    /**
     * Run.
     */
    @Override
    public void run() {
        try {
            order = new ConceptOrder("LatticeWithSmartLB",matrix,getDescription());
            if (chrono != null) {
                chrono.start("concept/order");
            }
            ISet allObjects = factory.createSet(matrix.getObjectCount());
            allObjects.fill(matrix.getObjectCount());
            int top=order.addConcept(allObjects, factory.createSet());
/*            for (int numAttr = 0; numAttr < matrix.getAttributeCount(); numAttr++) {
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
*/            if (chrono != null) {
                chrono.stop("concept/order");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    @Override
    public String getDescription() {
        return "SmartLB";
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

}
