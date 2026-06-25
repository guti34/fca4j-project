/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.CsrConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.IConceptOrder;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

/**
 * The Class AOC_poset_Hermes.
 */
public class AOC_poset_Hermes implements AbstractAlgo<IConceptOrder> {

	protected IBinaryContext matrix; //ressource de depart
    protected IConceptOrder gsh = null; //ressource d'arrivee
    protected Chrono chrono = null; // eventually a chrono to store execution time 
    protected HashSet<Integer> visited = new HashSet<>();
    protected ISetFactory factory;
    protected int minSetSize;

    /**
     * Instantiates a new AO C poset hermes.
     *
     * @param bc the bc
     * @param chrono the chrono
     */
    public AOC_poset_Hermes(IBinaryContext bc, Chrono chrono) {
        super();
        this.chrono = chrono;
        matrix = bc;
        factory = matrix.getFactory();
        minSetSize=Integer.max(matrix.getAttributeCount(), matrix.getObjectCount());
    }

    /**
     * Instantiates a new AO C poset hermes.
     *
     * @param bc the bc
     */
    public AOC_poset_Hermes(IBinaryContext bc) {
        this(bc, null);
    }

    /**
     * Clarify.
     *
     * @param setToClarify the set to clarify
     * @param setToSynchronize the set to synchronize
     * @return the array list
     */
    protected ArrayList<RefSet> clarify(ArrayList<RefSet> setToClarify, ArrayList<RefSet> setToSynchronize) {
        Comparator<RefSet> comparator = new Comparator<RefSet>() {

            @Override
            public int compare(RefSet o1, RefSet o2) {
                int card1 = o1.values.cardinality();
                int card2 = o2.values.cardinality();
                if (card1 < card2) {
                    return 1;
                }
                if (card1 == card2) {
                    return 0;
                }
                return -1;

            }
        };
        // sort RefSets depending on the cardinality
        Collections.sort(setToClarify, comparator);
        for (int i = setToClarify.size() - 1; i > 0; i--) {
            RefSet setToCompare = setToClarify.get(i);
            for (int j = i - 1; j >= 0; j--) {
                RefSet iSet = setToClarify.get(j);
                int comparison = comparator.compare(setToCompare, iSet);
                if (comparison == 0) {
                    if (setToCompare.values.equals(iSet.values)) {
                        iSet.addRef(setToCompare.refs);
                        setToClarify.remove(i);
                        break;
                    }
                } else {
                    break;
                }
            }
        }
        ArrayList<RefSet> attrSets = new ArrayList<RefSet>(setToSynchronize.size());
        for (int i = 0; i < setToSynchronize.size(); i++) {
            attrSets.add(new RefSet(setToSynchronize.get(i).refs));
        }
        for (int i = 0; i < setToClarify.size(); i++) {
            ISet ms = setToClarify.get(i).values;
            for (Iterator<Integer> it = ms.iterator(); it.hasNext(); attrSets.get(it.next()).values.add(i));
        }
        return attrSets;
    }

    /**
     * Compute attribute dom relation.
     *
     * @param attrSets the attr sets
     * @return the array list
     */
    protected ArrayList<RefSet> computeAttributeDomRelation(ArrayList<RefSet> attrSets) {
        ArrayList<RefSet> domRelation = new ArrayList<RefSet>();
        for (int i = 0; i < attrSets.size(); i++) {
            RefSet attrSet = attrSets.get(i);
            RefSet newSet = new RefSet();
            newSet.addRef(attrSet.refs);
            for (int j = 0; j < attrSets.size(); j++) {
                RefSet attr2Set = attrSets.get(j);
                boolean b = (i == j || attrSet.isInclude(attr2Set));
                if (b) {
                    newSet.values.add(j);
                }
            }
            domRelation.add(newSet);
        }
        return domRelation;
    }

    private boolean isVisited(int concept) {
        return visited.contains(concept);
    }

    private void setVisited(int concept, boolean b) {
        if (b) {
            visited.add(concept);
        } else {
            visited.remove(concept);
        }
    }

    /**
     * Compute hasse diagram.
     *
     * @param conceptSets the concept sets
     * @throws Exception the exception
     */
    protected void computeHasseDiagram(ArrayList<ConceptSet> conceptSets) throws Exception {
        // sort concept sets depending on the cardinality
        Collections.sort(conceptSets, new Comparator<ConceptSet>() {

            @Override
            public int compare(ConceptSet o1, ConceptSet o2) {
                int card1 = o1.values.cardinality();
                int card2 = o2.values.cardinality();
                return -Integer.compare(card1, card2);
            }
        });
        ArrayList<Integer> concepts = new ArrayList<>();
        ArrayList<ConceptSet> conceptSetArray = new ArrayList<>();
        for (int i = 0; i < conceptSets.size(); i++) {
            ConceptSet cSet = conceptSets.get(i);
            int conceptS = gsh.addConcept(cSet.extent, cSet.intent, factory.clone(cSet.extent), factory.clone(cSet.intent));
            concepts.add(conceptS);
            conceptSetArray.add(cSet);
            for (int j = i - 1; j >= 0; j--) {
                //on compare chaque noeud dans l'extension lineaire e ces precedents
                //sauf ceux qui sont marques, afin d'eviter les arcs de transitivite
                int conceptT = concepts.get(j);
                if (isVisited(conceptT)) {
                    setVisited(conceptT, false); //on demarque pour le prochain tour de la boucle principale
                } else if (isParentOf(conceptS, cSet, conceptT, conceptSetArray.get(j))) {

                    //si S est le pere de T, on rajoute l'arc et on marque les descendants de T afin d'eviter les arcs de transitivite
                    gsh.addPrecedenceConnection(conceptT, conceptS);
                    Iterator<Integer> iteratorTargetExtent = gsh.getConceptExtent(conceptT).iterator();
                    while (iteratorTargetExtent.hasNext()) {
                        gsh.getConceptExtent(conceptS).add(iteratorTargetExtent.next());
                    }
                    gsh.getConceptIntent(conceptT).addAll(gsh.getConceptReducedIntent(conceptS));
                    completeDescendance(conceptT, gsh.getConceptReducedIntent(conceptS));
                }
            }
//		setPercentageOfWork((i*100)/linext.size());
        }

    }
//pour determiner si S est le pere de T

    private boolean isParentOf(int conceptS, ConceptSet sCS, int conceptT, ConceptSet tCS) {
        boolean s_has_object = !gsh.getConceptReducedExtent(conceptS).isEmpty();
        int t_object = gsh.getConceptReducedExtent(conceptT).first();

        if (t_object >= 0 && !s_has_object) {
            int s_attr = gsh.getConceptReducedIntent(conceptS).first();
            return matrix.get(t_object, s_attr);
        } else {
            return tCS.values.containsAll(sCS.values);
        }
    }

//marquer tous les descendants d'un concept et heriter des attributs des parents (pour eviter les arcs de transitivite)

    private void completeDescendance(int concept, ISet intent) {
        for (Iterator<Integer> it = gsh.getLowerCoverIterator(concept); it.hasNext();) {
            int child = it.next();
            if (!isVisited(child)) {
                setVisited(child, true);
                gsh.getConceptIntent(child).addAll(intent);
                completeDescendance(child, intent);
            }
        }
    }

    /**
     * Compute GSH.
     *
     * @return the concept order
     * @throws Exception the exception
     */
    public IConceptOrder computeGSH() throws Exception {
        gsh = new ConceptOrder("AOCposetWithHermes", matrix, getDescription());
        ArrayList<RefSet> attrSets = new ArrayList<>();
        ArrayList<RefSet> objSets = new ArrayList<>();
        if (chrono != null) {
            chrono.start("clarify");
        }
        if (matrix.getAttributeCount() > matrix.getObjectCount()) {
            for (int numAttr = 0; numAttr < matrix.getAttributeCount(); numAttr++) {
                attrSets.add(new RefSet(numAttr, matrix.getExtent(numAttr)));
            }
            for (int numObj = 0; numObj < matrix.getObjectCount(); numObj++) {
                objSets.add(new RefSet(numObj));
            }
            objSets = clarify(attrSets, objSets);
            attrSets = clarify(objSets, attrSets);
        } else {
            for (int numObj = 0; numObj < matrix.getObjectCount(); numObj++) {
                objSets.add(new RefSet(numObj, matrix.getIntent(numObj)));
            }
            for (int numAttr = 0; numAttr < matrix.getAttributeCount(); numAttr++) {
                attrSets.add(new RefSet(numAttr));
            }
            attrSets = clarify(objSets, attrSets);
            objSets = clarify(attrSets, objSets);
        }
        if (chrono != null) {
            chrono.stop("clarify");
            chrono.start("concept");
        }
        // find attribute Domination relation
        ArrayList<RefSet> domSets = computeAttributeDomRelation(attrSets);
        ArrayList<ConceptSet> concepts = new ArrayList<ConceptSet>();
        // merge domSets to object Sets to build Concept matrix
        for (RefSet objSet : objSets) {
            concepts.add(new ConceptSet(null, objSet.refs, objSet.values));
        }
//	int firstSetSize=concepts.size();
        for (RefSet domSet : domSets) {
            boolean done = false;
            for (ConceptSet concept : concepts) //		for(int i=0;i<firstSetSize;i++)
            {
                if (domSet.values.equals(concept.values)) {
                    concept.intent.addAll(domSet.refs);
                    done = true;
                    break;
                }
            }
            if (!done) {
                concepts.add(new ConceptSet(domSet.refs, null, domSet.values));
            }
        }
        if (chrono != null) {
            chrono.stop("concept");
            chrono.start("order");
        }
        computeHasseDiagram(concepts);
        if (chrono != null) {
            chrono.stop("order");
        }
        return gsh;
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    @Override
    public String getDescription() {
        return "Hermes";
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
            computeGSH();
        } catch (Exception ex) {
            Logger.getLogger(AOC_poset_Hermes.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * The Class RefSet.
     */
    class RefSet {

        ISet refs;
        ISet values;

        /**
         * Instantiates a new ref set.
         */
        RefSet() {
            this.refs = factory.createSet(minSetSize);
            this.values = factory.createSet(minSetSize);
        }

        /**
         * Checks if is include.
         *
         * @param anotherRefSet the another ref set
         * @return true, if is include
         */
        public boolean isInclude(RefSet anotherRefSet) {
            return anotherRefSet.values.containsAll(values);
        }

        /**
         * Instantiates a new ref set.
         *
         * @param ref the ref
         * @param values the values
         */
        RefSet(int[] ref, int[] values) {
            this.refs = factory.createSet(ref.length);
            for (int i : ref) {
                this.refs.add(i);
            }
            int max = 0;
            for (int i : values) {
                if (i > max) {
                    max = i;
                }
            }
            this.values = factory.createSet(max + 1);
            for (int i : values) {
                this.values.add(i);
            }
        }

        /**
         * Instantiates a new ref set.
         *
         * @param ref the ref
         * @param values the values
         */
        RefSet(int ref, ISet values) {
            this.refs = factory.createSet(minSetSize);
            this.refs.add(ref);
            this.values = factory.clone(values);
        }

        /**
         * Instantiates a new ref set.
         *
         * @param ref the ref
         */
        RefSet(int ref) {
            this.refs = factory.createSet(minSetSize);
            this.refs.add(ref);
            this.values = factory.createSet(minSetSize);
        }

        /**
         * Instantiates a new ref set.
         *
         * @param refs the refs
         */
        RefSet(ISet refs) {
            this.values = factory.createSet(minSetSize);
            this.refs = factory.clone(refs);
        }

        /**
         * Adds the ref.
         *
         * @param ref the ref
         */
        void addRef(int ref) {
            this.refs.add(ref);
        }

        /**
         * Adds the ref.
         *
         * @param refsToAdd the refs to add
         */
        void addRef(ISet refsToAdd) {
            this.refs.addAll(refsToAdd);
        }
    }

    /**
     * The Class ConceptSet.
     */
    class ConceptSet {

        ISet intent;
        ISet extent;
        ISet values;

        /**
         * Instantiates a new concept set.
         *
         * @param intent the intent
         * @param extent the extent
         * @param values the values
         */
        ConceptSet(ISet intent, ISet extent, ISet values) {
            if (intent == null) {
                this.intent = factory.createSet(matrix.getAttributeCount());
            } else {
                this.intent = intent;
            }
            if (extent == null) {
                this.extent = factory.createSet(matrix.getObjectCount());
            } else {
                this.extent = extent;
            }
            this.values = values;
        }
    }
}
