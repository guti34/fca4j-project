/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import fr.lirmm.fca4j.core.CsrConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.IConceptOrder;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

// TODO: Auto-generated Javadoc
/**
 * The Class AOC_poset_Ares.
 */
public class AOC_poset_Ares implements AbstractAlgo<IConceptOrder> {

    private IBinaryContext matrix; //ressource d'entree
    private IConceptOrder gsh; //ressource de sortie
    private Chrono chrono = null; // eventually a chrono to store execution time 
    private boolean acposet;
    private boolean ocposet;
    protected ISetFactory factory;

    /**
     * Instantiates a new AOC poset algorithm: ares.
     *
     * @param matrix the context
     * @param chrono the chrono
     */
    public AOC_poset_Ares(IBinaryContext matrix, Chrono chrono) {
        this(matrix, chrono, null, true, true);
    }

    /**
     * Instantiates a new AOC poset algorithm: ares.
     *
     * @param matrix the context
     * @param chrono the chrono
     * @param gsh the gsh
     * @param ocposet build ocposet
     * @param acposet build acposet
     */
    public AOC_poset_Ares(IBinaryContext matrix, Chrono chrono, IConceptOrder gsh, boolean ocposet, boolean acposet) {
        super();
        this.gsh = gsh;
        this.matrix = matrix;
        this.factory=matrix.getFactory();
        this.chrono = chrono;
        this.ocposet = ocposet;
        this.acposet = acposet;
    }

    /**
     * Instantiates a new AOC poset algorithm: ares.
     *
     * @param matrix the context
     */
    public AOC_poset_Ares(IBinaryContext matrix) {
        this(matrix, null);
    }

    private boolean hasAllAConcepts() {
        return acposet;
    }

    private boolean hasAllOConcepts() {
        return ocposet;
    }


    /**
     * Compute.
     *
     * @param newAttributes the new attributes
     * @return the concept order
     * @throws CloneNotSupportedException the clone not supported exception
     */
    public IConceptOrder compute(ISet newAttributes) throws CloneNotSupportedException {
        //for each attribute add it
        for (Iterator<Integer> attrIterator = newAttributes.iterator(); attrIterator.hasNext();) {
            AAresStep aaresStep = new AAresStep(attrIterator.next());
            aaresStep.compute();
        }
        // fix bug forgetting transitive edges
        gsh.reduce();
        return gsh;
    }

    private ISet computeReducedExtent(int c) throws CloneNotSupportedException {
        ISet extsC = factory.clone(gsh.getConceptExtent(c));
        for (Iterator<Integer> it = gsh.getLowerCoverIterator(c); it.hasNext();) {
            int child = it.next();
            extsC.removeAll(gsh.getConceptExtent(child));
        }
        return extsC;
    }

    private ISet max(ISet concepts) {
        ISet max = concepts.clone();
        for (Iterator<Integer> it = concepts.iterator(); it.hasNext();) {
            int concept = it.next();
            ISet children = gsh.getAllChildren(concept);
            children.remove(concept);
            max.removeAll(children);
        }
        return max;
    }

    private int pickOne(ISet concepts) {
        if (!concepts.iterator().hasNext()) {
            return -1;
        } else {
            int next = concepts.first();
            concepts.remove(next);
            return next;
        }
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    public String getDescription() {
        return "Ares";
    }

    /**
     * Gets the result.
     *
     * @return the result
     */
    @Override
    public IConceptOrder getResult() {
        return gsh;
    }

    /**
     * Run.
     */
    @Override
    public void run() {
        try {
            if (gsh == null) {
                gsh = new CsrConceptOrder("AOCposetWithAres", matrix, getDescription());
                ISet extent = factory.createSet(matrix.getObjectCount());
                extent.fill(matrix.getObjectCount());
                ISet rextent = factory.createSet(matrix.getObjectCount());
                rextent.addAll(extent);
                gsh.addConcept(extent, factory.createSet(matrix.getAttributeCount()), rextent, factory.createSet(matrix.getAttributeCount()));
                ISet intent = factory.createSet(matrix.getAttributeCount());
                intent.fill(matrix.getAttributeCount());
                gsh = compute(intent);
            } else {
                int max_attr = -1;
                ISet intent = factory.createSet(matrix.getAttributeCount());
                for (Iterator<Integer> it = gsh.getMinimals().iterator(); it.hasNext();) {
                    intent.addAll(gsh.getConceptIntent(it.next()));
                }
                for (Iterator<Integer> it = intent.iterator(); it.hasNext();) {
                    max_attr = it.next();
                }
                ISet intentToCompute = factory.createSet(matrix.getAttributeCount());
                for (int numattr = max_attr + 1; numattr < matrix.getAttributeCount(); numattr++) {
                    intentToCompute.add(numattr);
                }
                gsh = compute(intentToCompute);
            }
        } catch (CloneNotSupportedException ex) {
            ex.printStackTrace();
        }
    }

    private class AAresStep {
    	int maxNbConcepts=matrix.getAttributeCount()+matrix.getObjectCount();
        ISet subConceptsOfA =factory.createSet(maxNbConcepts);
        ISet nonIntroducingConcepts = factory.createSet(maxNbConcepts);
        ISet doNotCheck = factory.createSet(maxNbConcepts);
        ISet extentOfA;
        boolean isCADefined = false;
        int ca = -1;
        int a;
        HashSet<Integer> conceptsToAdd = new HashSet<>();

        AAresStep(int a) {
                this.a = a;
                /* properEntitiesOfA aims at being the list of proper Entities of the attribute a
                * it starts with all the entities having a as attributes
                * */
                this.extentOfA = factory.clone(matrix.getExtent(a));
        }

        void compute() throws CloneNotSupportedException {
            /* a linear extension of the already created concepts from more specific to more general*/
//            ArrayList<Integer> sortedConcepts = new ArrayList<>();
//            sortedConcepts.addAll(gsh.getConcepts());
//            sortedConcepts.sort((Integer c1, Integer c2) -> Integer.compare(gsh.getConceptExtent(c1).cardinality(), gsh.getConceptExtent(c2).cardinality()));
            ArrayList<Integer> sortedConcepts = gsh.sortByExtent(true);
            for (int concept:sortedConcepts) {
                if (!visit(concept)) {
                    return;
                }

            }
            if (!isCADefined
                    && (hasAllAConcepts() || extentOfA.cardinality() != 0)//if oc poset then the concept must have proper entities
                    ) {
                ISet caExtent = matrix.getExtent(a);
                ISet caRIntent = factory.createSet();
                caRIntent.add(a);
                ca = gsh.addConcept(caExtent, factory.createSet(), factory.createSet(), caRIntent);
                conceptsToAdd.add(ca);
                for (Iterator<Integer> it = max(subConceptsOfA).iterator(); it.hasNext();) {
                    int maxSub = it.next();
                    gsh.addPrecedenceConnection(maxSub, ca);
                }
// bug rextent fix
//            gsh.getConceptReducedExtent(ca).addAll(computeReducedExtent(ca));
// bug rextent fix
            } else if (isCADefined && !hasAllAConcepts() && extentOfA.cardinality() == 0) {
                nonIntroducingConcepts.add(ca);
            }
            for (Iterator<Integer> it = nonIntroducingConcepts.iterator(); it.hasNext();) {
                int toRemove = it.next();
                if (!gsh.getConceptReducedIntent(toRemove).isEmpty()) {
                    for (Iterator<Integer> it2 = gsh.getLowerCoverIterator(toRemove); it2.hasNext();) {
                        int child = it2.next();
                        gsh.getConceptReducedIntent(child).addAll(gsh.getConceptReducedIntent(toRemove));
                    }
                }
                Set<Integer> parents = gsh.getUpperCoverSet(toRemove);
                Set<Integer> children = gsh.getLowerCoverSet(toRemove);
                for (int p : parents) {
                    gsh.removePrecedenceConnection(toRemove, p);
                }
                for (int c : children) {
                    gsh.removePrecedenceConnection(c, toRemove);
                }
                for (int p : parents) {
                    for (int c : children) {
                        if (!gsh.getLowerCoverSet(p).contains(c)) {
                            gsh.addPrecedenceConnection(c, p);
                        }
                    }
                }
                gsh.removeConcept(toRemove);
                sortedConcepts.remove((Integer) toRemove);
                conceptsToAdd.remove((Integer) toRemove);
            }
            sortedConcepts.addAll(conceptsToAdd);

        }

        private boolean visit(int c) throws CloneNotSupportedException {
            if (!nonIntroducingConcepts.contains(c)
                    && !doNotCheck.contains(c)
                    && (gsh.getConceptExtent(c).newIntersect(matrix.getExtent(a)).cardinality() > 0
                    || (gsh.getConceptExtent(c).cardinality() == 0 || matrix.getExtent(a).cardinality() == 0))) {
                ISet cReducedExtent = computeReducedExtent(c);
                ISet ec = cReducedExtent.newIntersect(matrix.getExtent(a));
                ISet rc = gsh.getConceptExtent(c).newDifference(matrix.getExtent(a));
                ISet ra = matrix.getExtent(a).newDifference(gsh.getConceptExtent(c));
                ISet cc = gsh.getConceptExtent(c).newIntersect(matrix.getExtent(a));

                if (rc.cardinality() == 0 && rc.equals(ra)) {//case 1 extent of c is g(a)
                    //c.getBitIntent().set(context.getAttributes().indexOf(a));
                    gsh.getConceptIntent(c).add(a);
                    gsh.getConceptReducedIntent(c).add(a);
                    return false;
                } else if (rc.cardinality() == 0) {//case 2 c subconcept of g(a)
                    subConceptsOfA.add(c);
                    //c.getBitIntent().set(context.getAttributes().indexOf(a));
                    gsh.getConceptIntent(c).add(a);
                    extentOfA.removeAll(cReducedExtent);
                } else if (ra.cardinality() == 0) {//case 3 c superconcept of g(a) 
                    if (!isCADefined) {
                        ca = gsh.addConcept(factory.createSet(matrix.getObjectCount()), factory.createSet(matrix.getAttributeCount()));
                        conceptsToAdd.add(ca);
                        isCADefined = true;
                        //ca.getBitIntent().set(context.getAttributes().indexOf(a));
                        gsh.getConceptIntent(ca).add(a);
                        gsh.getConceptReducedIntent(ca).add(a);
                        gsh.getConceptExtent(ca).addAll(matrix.getExtent(a));
//                        ISet extsC = new MySetWrapper(0);
                        ISet extsC = gsh.getConceptReducedExtent(c);
                        extsC.addAll(cReducedExtent);
                        if (ec.cardinality() != 0) {
                            extsC.removeAll(matrix.getExtent(a));
                            if ((gsh.getConceptReducedIntent(c).cardinality() == 0 || !hasAllAConcepts())
                                    && (extsC.cardinality() == 0 || !hasAllOConcepts())) {
                                nonIntroducingConcepts.add(c);
                            }
                        }
                        for (Iterator<Integer> it = max(subConceptsOfA).iterator(); it.hasNext();) {
                            int maxSub = it.next();
                            gsh.addPrecedenceConnection(maxSub, ca);
                            gsh.removePrecedenceConnection(maxSub, c);
                        }

                    }
                    gsh.addPrecedenceConnection(ca, c);
                    gsh.setReducedExtent(ca, computeReducedExtent(ca));
                    //ca.getBitIntent().or(c.getBitIntent());
                    gsh.getConceptIntent(ca).addAll(gsh.getConceptIntent(c));
                    for (Iterator<Integer> it = gsh.getLowerCoverIterator(ca); it.hasNext();) {
                        int child = it.next();
                        gsh.removePrecedenceConnection(child, c);
                    }
                    doNotCheck.addAll(gsh.getAllParents(c));
                } else if (ec.cardinality() != 0 && hasAllOConcepts()) {//case 4 c and g(a) not comparable
                    ISet extsC2 = factory.createSet(matrix.getAttributeCount()+matrix.getObjectCount());
                    extsC2.addAll(ec);//TODO is a copy necessary ?
                    ISet extsC = gsh.getConceptReducedExtent(c);
//                    extsC.addAll(c.getReducedExtent());
                    extsC.addAll(cReducedExtent);
// bug rextent fix
                    int c2 = gsh.addConcept(factory.clone(cc), factory.createSet(matrix.getObjectCount()), extsC2, factory.createSet(matrix.getAttributeCount()));
                    conceptsToAdd.add(c2);
                    // bug rextent fix
                    //TODO
                    //c2.getBitIntent().or(c.getBitIntent());
                    //c2.getBitIntent().set(context.getAttributes().indexOf(a));
                    gsh.getConceptIntent(c2).addAll(gsh.getConceptIntent(c));
                    gsh.getConceptIntent(c2).add(a);
                    gsh.addPrecedenceConnection(c2, c);
                    ISet interC_SCA = subConceptsOfA.newIntersect(gsh.getAllChildren(c));
                    for (Iterator<Integer> it = max(interC_SCA).iterator(); it.hasNext();) {
                        int c3 = it.next();
                        gsh.addPrecedenceConnection(c3, c2);
                        gsh.removePrecedenceConnection(c3, c);
                    }
                    gsh.addPrecedenceConnection(c2, c);
                    subConceptsOfA.add(c2);
                    for (Iterator<Integer> it = gsh.getLowerCoverIterator(c2); it.hasNext();) {
                        int c2Child = it.next();
                        gsh.removePrecedenceConnection(c2Child, c);
                    }
                    extsC.removeAll(extsC2);
                    if ((gsh.getConceptReducedIntent(c).cardinality() == 0 || !hasAllAConcepts())
                            && extsC.cardinality() == 0) {
                        nonIntroducingConcepts.add(c);
                    }
// bug rextent fix
                    extentOfA.removeAll(gsh.getConceptReducedExtent(c2));
//                    properEntitiesOfA.clearAll(computeReducedExtent(c2));
                }
            }
            return true;
        }
    }
}
