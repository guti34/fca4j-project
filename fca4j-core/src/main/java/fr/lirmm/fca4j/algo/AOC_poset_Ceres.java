/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.IConceptOrder;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

/**
 * The Class AOC_poset_Ceres.
 *
 * @author roume
 */
public class AOC_poset_Ceres implements AbstractAlgo<IConceptOrder> {

    private IBinaryContext binCtx = null;
    IConceptOrder theGSH = null;
    private Chrono chrono = null; // eventually a chrono to store execution time 
    protected ISetFactory factory;

    // Flat mark buffers indexed by concept id, with a monotonic epoch stamp so a
    // whole BFS is "reset" simply by bumping classifyIdentifier. Replaces the two
    // HashMap<Integer,Integer> (boxing + hashing on every mark update) and the
    // ran.nextInt() epoch, which could collide and make stale marks read as fresh.
    int[] marks;
    int[] cIdentifiers;
    int classifyIdentifier = 0;

    //	--------------------------------------
    // --------------------------------------
    //			StartUp
    // --------------------------------------
    /**
     * Instantiates a new AOC poset ceres.
     *
     * @param binCtx the bin ctx
     * @param chrono the chrono
     */
    // --------------------------------------
    public AOC_poset_Ceres(IBinaryContext binCtx, Chrono chrono) {
        super();
        this.binCtx = binCtx;
        this.factory=binCtx.getFactory();
        this.chrono = chrono;
    }

    /**
     * Instantiates a new AO C poset ceres.
     *
     * @param binCtx the bin ctx
     */
    public AOC_poset_Ceres(IBinaryContext binCtx) {
        this(binCtx, null);
    }


    private void Classify(PreConcept cptToAdd, ISet allCoveredIntent, boolean isAttributeCpt) {

        classifyIdentifier++;
        LinkedList<Integer> fifoQueue = new LinkedList<>(); // File accueillant des noeuds potentiellement parent de N
        ISet potentialUpperCover = factory.createSet(binCtx.getAttributeCount()+binCtx.getObjectCount()); // un ensemble de parents de N
        fifoQueue.add(theGSH.getTop()); // Q recoit top en initialisation
        int nextCpt;
        while (!fifoQueue.isEmpty()) {
            nextCpt = fifoQueue.remove(0);
            potentialUpperCover.add(nextCpt);
            potentialUpperCover.removeAll(theGSH.getUpperCover(nextCpt));
            if (isAttributeCpt) {
                cptToAdd.getIntent().addAll(theGSH.getConceptReducedIntent(nextCpt));
                // on peut modifier directement l'intension car la SHG est une EXTENT_LEVEL_INDEX
            }
            for (Iterator<Integer> it = theGSH.getLowerCoverIterator(nextCpt); it.hasNext();) {
                int P = it.next(); // P est un enfant du pere de N considere (CSC)
                changeMarkValue(P);
                if (isReady(P)) {
                    if (theGSH.getConceptExtent(P).containsAll(cptToAdd.getExtent())) {
                        fifoQueue.add(P);
                    }
                }
            }

        }
        // ICI DSC contient tous les parents direct du noeud N a inserer !
        int numCptToAdd = theGSH.addConcept(cptToAdd.getExtent(), cptToAdd.getIntent(), cptToAdd.getRExtent(), cptToAdd.getRIntent());
        for (Iterator<Integer> it = potentialUpperCover.iterator(); it.hasNext();) {
            theGSH.addPrecedenceConnection(numCptToAdd, it.next());
        }

    }

    private void WorkOnLeftPart2(PreConcept addedCpt, ISet allCoveredIntent) throws CloneNotSupportedException {

        // CC vas contenir les objets qui ne sont pas dans l'extension simplifie mais dans l'extension complete
        ArrayList<Integer> lesObjsTrie = new ArrayList<>();
        for (Iterator<Integer> it = addedCpt.getExtent().iterator(); it.hasNext();) {
            int anObject = it.next();
            if (!addedCpt.getRExtent().contains(anObject)) {
                lesObjsTrie.add(anObject);
            }
        }
        // increasing intent-size sort. f(o) and |f(o)| are read many times below, so
        // materialise them once per object instead of calling binCtx.getIntent(o)
        // (and cardinality()) repeatedly.
        int n = lesObjsTrie.size();
        int[] objs = new int[n];
        for (int i = 0; i < n; i++) {
            objs[i] = lesObjsTrie.get(i);
        }
        final ISet[] fo = new ISet[n];
        final int[] foCard = new int[n];
        // sort object indices by |f(o)| ascending, then fill parallel arrays in order
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = objs[i];
        }
        Arrays.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer e, Integer o) {
                return Integer.compare(binCtx.getIntent(e).cardinality(), binCtx.getIntent(o).cardinality());
            }
        });
        for (int i = 0; i < n; i++) {
            objs[i] = order[i];
            fo[i] = binCtx.getIntent(objs[i]);
            foCard[i] = fo[i].cardinality();
        }

        // Ici objs est l'ensemble des objets formel contenus dans CC trie par taille d'intension croissante.
        // consumed[j] marks an object already merged into an earlier object-concept; the
        // original compacted the list with remove((Integer)), this skips instead -- same
        // effect, without the O(n) shift inside the inner loop.
        boolean[] consumed = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (consumed[i]) {
                continue;
            }
            ISet theAssocitedIntent = factory.clone(fo[i]);
            if (allCoveredIntent.containsAll(theAssocitedIntent)) {
                // objs[i] genere donc un nouveau concept objet
                ISet LP = factory.createSet(binCtx.getObjectCount());
                LP.add(objs[i]);
                // L'Intension simplifie est forcement vide puisque ce concept est obligatoirement un concept objet !
                PreConcept theNexCpt = new PreConcept(LP, theAssocitedIntent);
                theNexCpt.getRExtent().add(objs[i]);
                int card = theAssocitedIntent.cardinality();
                for (int j = i + 1; j < n; j++) {
                    if (consumed[j]) {
                        continue;
                    }
                    if (foCard[j] == card) {
                        if (fo[j].equals(theAssocitedIntent)) {
                            theNexCpt.getExtent().add(objs[j]);
                            theNexCpt.getRExtent().add(objs[j]);
                            consumed[j] = true;
                        }
                    } else if (fo[j].containsAll(theAssocitedIntent)) {
                        theNexCpt.getExtent().add(objs[j]);
                    }
                }
                // On vas placer ce nouveau noeud dans le treilli
                Classify(theNexCpt, allCoveredIntent, false);
            }
        }

    }

    private void initMark(int aCpt) {
        marks[aCpt] = theGSH.getUpperCover(aCpt).cardinality();
        cIdentifiers[aCpt] = classifyIdentifier;
    }

    private boolean isReady(int aCpt) {
        if (cIdentifiers[aCpt] != classifyIdentifier) {
            initMark(aCpt);
        }
        return marks[aCpt] == 0;
    }

    private void changeMarkValue(int aCpt) {
        if (cIdentifiers[aCpt] != classifyIdentifier) {
            initMark(aCpt);
        }
        marks[aCpt]--;
    }

    // --------------------------------------
    // --------------------------------------
    //			Inherited Methods
    // --------------------------------------
    /**
     * Gets the description.
     *
     * @return the description
     */
    // --------------------------------------
    public String getDescription() {
        return "Ceres";
    }

    /**
     * Gets the result.
     *
     * @return the result
     */
    @Override
    public IConceptOrder getResult() {
        return theGSH;
    }

    /**
     * Run.
     */
    @Override
    public void run() {
        if (/*binCtx.getAttributeNumber()==0 || */binCtx.getObjectCount() == 0) {
            return;
        }

        theGSH = new ConceptOrder("AOCposetWithCeres", binCtx, getDescription());
        // Concept ids handed out by addConcept stay in [0, |G|+|M|) throughout the
        // build (the single removeConcept happens only at the very end), so flat
        // buffers of this size cover every id Classify will ever mark.
        int maxConcepts = binCtx.getObjectCount() + binCtx.getAttributeCount() + 1;
        marks = new int[maxConcepts];
        cIdentifiers = new int[maxConcepts];
        if (chrono != null) {
            chrono.start("concept/order");
        }
        ISet ext = factory.createSet(binCtx.getObjectCount());
        ext.fill(binCtx.getObjectCount());
        ISet reducedExtent = factory.createSet(binCtx.getObjectCount());
        for (int i = 0; i < binCtx.getObjectCount(); i++) {
            if (binCtx.getIntent(i).cardinality() == 0) {
                reducedExtent.add(i);
            }
        }
        int topInit = theGSH.addConcept(ext, factory.createSet(binCtx.getAttributeCount()), reducedExtent, factory.createSet(binCtx.getAttributeCount()));
        PreConcept[] preCptTab = new PreConcept[binCtx.getAttributeCount()];
        PreConcept aCpt = null;
        for (int i = 0; i < binCtx.getAttributeCount(); i++) {
            ISet preCptInt = factory.createSet(binCtx.getAttributeCount());
            preCptInt.add(i);
            ISet preCptExt = factory.clone(binCtx.getExtent(i));
            aCpt = new PreConcept(preCptExt, preCptInt);
            aCpt.getRIntent().add(i);
            preCptTab[i] = aCpt;
        }
        // Debut Algo
        // decreasing extent sort. cardinality() is O(|G|/64) on a bitset; caching it
        // once on each PreConcept avoids recomputing it at every comparison (a sort is
        // O(K log K) comparisons).
        for (PreConcept p : preCptTab) {
            p.extentCard = p.getExtent().cardinality();
        }
        Arrays.sort(preCptTab, new Comparator<PreConcept>() {
            @Override
            public int compare(PreConcept p1, PreConcept p2) {
                return Integer.compare(p2.extentCard, p1.extentCard);
            }
        });
        boolean preCptDone[] = new boolean[preCptTab.length];
        for (int i = 0; i < preCptDone.length; i++) {
            preCptDone[i] = false;
        }
        int sizeToDo;
        int startIndex = 0; // inclu dans la section
        int endIndex = 1; // exclu de la section

        ISet allCoveredIntent = factory.createSet(binCtx.getAttributeCount());
        while (startIndex < preCptTab.length) {
            sizeToDo = preCptTab[startIndex].extentCard;

            // On rassemle les pre-concepts de taille d'extent identique
            // Si deux pre-concept ont un extent identique on fusionne ces deux concepts dans le premier le dernier ne sera pas considere
            while (endIndex < preCptTab.length && preCptTab[endIndex].extentCard == sizeToDo) {
                for (int i = startIndex; i < endIndex; i++) {
                    if (!preCptDone[i] && preCptTab[i].getExtent().equals(preCptTab[endIndex].getExtent())) {
                        preCptTab[i].getIntent().addAll(preCptTab[endIndex].getIntent());
                        preCptTab[i].getRIntent().addAll(preCptTab[endIndex].getIntent());
                        preCptDone[endIndex] = true;
                    }
                }
                endIndex++;
            }

            boolean doWOLP = false;
            for (int i = startIndex; i < endIndex; i++) {
                if (!preCptDone[i]) {

                    if (sizeToDo < binCtx.getObjectCount()) {
                        Classify(preCptTab[i], allCoveredIntent, true);
                        doWOLP = true;
                    } else {
                        int top = theGSH.getTop();
                        theGSH.getConceptIntent(top).addAll(preCptTab[i].getIntent());
                        // on peut modifier directement l'intension car la SHG est une EXTENT_LEVEL_INDEX
                        theGSH.getConceptReducedIntent(top).addAll(preCptTab[i].getRIntent());
                        // test
//                         preCptTab[i] = ((MyConcept) theGSH.getTopTentative());
//
                        preCptTab[i] = new PreConcept(
                                (ISet) theGSH.getConceptExtent(top),
                                (ISet) theGSH.getConceptIntent(top),
                                (ISet) theGSH.getConceptReducedExtent(top),
                                (ISet) theGSH.getConceptReducedIntent(top));
                        doWOLP = false;
                    }
                    allCoveredIntent.addAll(preCptTab[i].getRIntent());

                    // Mise a Jour de LPs
                    for (Iterator<Integer> it = preCptTab[i].getExtent().iterator(); it.hasNext();) {
                        int o = it.next();
                        ISet intent1 = binCtx.getIntent(o);
                        ISet intent2 = preCptTab[i].getIntent();
                        if (intent1.equals(intent2)) {
                            preCptTab[i].getRExtent().add(o);
                        }
                    }

                    if (doWOLP) {
                        try {
                            WorkOnLeftPart2(preCptTab[i], allCoveredIntent);
                        } catch (CloneNotSupportedException ex) {
                            ex.printStackTrace();
                            return;
                        }
                    }

                    preCptDone[i] = true;
                }
            }

            startIndex = endIndex;
            endIndex++;
        }

        int top = theGSH.getTop();
//        for (Iterator<Integer> it_tops = theGSH.getMaximals().iterator(); it_tops.hasNext();) {
//            int top = it_tops.next();

            if (theGSH.getConceptReducedExtent(top).cardinality() == 0 && theGSH.getConceptReducedIntent(top).cardinality() == 0) {
                theGSH.removeConcept(top);
            }
//        }
        if (chrono != null) {
            chrono.stop("concept/order");
        }        
    }

    private class PreConcept {

        ISet extent, intent, rextent, rintent;
        /** cardinality of extent, cached for the decreasing-extent sort */
        int extentCard;

        PreConcept(ISet extent, ISet intent) {
            this(extent, intent, factory.createSet(binCtx.getObjectCount()), factory.createSet(binCtx.getAttributeCount()));
        }

        PreConcept(ISet extent, ISet intent, ISet rextent, ISet rintent) {
            this.extent = extent;
            this.intent = intent;
            this.rextent = rextent;
            this.rintent = rintent;
        }

        ISet getExtent() {
            return extent;
        }

        ISet getIntent() {
            return intent;
        }

        ISet getRExtent() {
            return rextent;
        }

        ISet getRIntent() {
            return rintent;
        }

    }
}
