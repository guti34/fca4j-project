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
package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

/**
 * @author roume
 *
 * TODO To change the template for this generated type comment go to Window -
 * Preferences - Java - Code Style - Code Templates
 */
public class AOC_poset_Ceres implements AbstractAlgo<ConceptOrder> {

    private IBinaryContext binCtx = null;
    ConceptOrder theGSH = null;
    private Chrono chrono = null; // eventually a chrono to store execution time 
    protected ISetFactory factory;

    HashMap<Integer, Integer> cIdentifiers = new HashMap<>();
    HashMap<Integer, Integer> marks = new HashMap<>();
    Random ran = new Random(System.currentTimeMillis());
    int classifyIdentifier;

    //	--------------------------------------
    // --------------------------------------
    //			StartUp
    // --------------------------------------
    // --------------------------------------
    public AOC_poset_Ceres(IBinaryContext binCtx, Chrono chrono) {
        super();
        this.binCtx = binCtx;
        this.factory=binCtx.getFactory();
        this.chrono = chrono;
    }

    public AOC_poset_Ceres(IBinaryContext binCtx) {
        this(binCtx, null);
    }


    private void Classify(PreConcept cptToAdd, ISet allCoveredIntent, boolean isAttributeCpt) {

        classifyIdentifier = ran.nextInt();
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
        Collections.sort(lesObjsTrie, new Comparator<Integer>() {
            @Override
            public int compare(Integer e, Integer o) {
                int tailleFe = binCtx.getIntent(e).cardinality();
                int tailleFo = binCtx.getIntent(o).cardinality();
                return Integer.compare(tailleFe, tailleFo);
            }
        });

        // Ici lesObjsTrie est l'ensemble des objets formel contenus dans CC trie par taille d'intension croissante
        int potentialObjectCptGenerator;
        for (int i = 0; i < lesObjsTrie.size(); i++) {
            potentialObjectCptGenerator = lesObjsTrie.get(i);
            ISet theAssocitedIntent = factory.clone(binCtx.getIntent(potentialObjectCptGenerator));
            if (allCoveredIntent.containsAll(theAssocitedIntent)) {
                // potentialObjectCptGenerator genere donc un nouveau concept objet

                // Creation d'un nouveau noeud
                ISet LP = factory.createSet(binCtx.getObjectCount());
                LP.add(potentialObjectCptGenerator); // Son extension contient l'objet en question, e
                // L'Intension simplifie est forcement vide puisque ce concept est obligatoirement un concept objet !
                PreConcept theNexCpt = new PreConcept(LP, theAssocitedIntent);
                theNexCpt.getRExtent().add(potentialObjectCptGenerator); // L'extension simplifie contient egalement cet objet particulier, e
                int complementaryObjectGenerator;
                for (int j = i + 1; j < lesObjsTrie.size(); j++) {
                    // On vas parcourir les objets suivant pour completer l'extension complete et simplifie
                    // au cas oe ce concept objet representerai plusieurs objets
                    complementaryObjectGenerator = lesObjsTrie.get(j);
                    if (binCtx.getIntent(complementaryObjectGenerator).cardinality() == theAssocitedIntent.cardinality()) {
                        if (binCtx.getIntent(complementaryObjectGenerator).equals(theAssocitedIntent)) {
                            theNexCpt.getExtent().add(complementaryObjectGenerator);
                            theNexCpt.getRExtent().add(complementaryObjectGenerator);
                            lesObjsTrie.remove((Integer) complementaryObjectGenerator);
                            // Comme il y a un retrait il faut re-ajouster le compteur
                            j--;
                        }
                    } else {
                        if (binCtx.getIntent(complementaryObjectGenerator).containsAll(theAssocitedIntent)) {
                            theNexCpt.getExtent().add(complementaryObjectGenerator);
                        }
                    }
                }
                // On vas placer ce nouveau noeud dans le treilli
                Classify(theNexCpt, allCoveredIntent, false);
            }
        }

    }

    private void initMark(int aCpt) {
        marks.put(aCpt, theGSH.getUpperCover(aCpt).cardinality());
        cIdentifiers.put(aCpt, classifyIdentifier);
    }

    private boolean isReady(int aCpt) {
        if (marks.get(aCpt) == null || cIdentifiers.get(aCpt) != classifyIdentifier) {
            initMark(aCpt);
        }
        boolean ret = marks.get(aCpt) == 0;
        return ret;
    }

    private void changeMarkValue(int aCpt) {
        if (marks.get(aCpt) == null || cIdentifiers.get(aCpt) != classifyIdentifier) {
            initMark(aCpt);
        }
        marks.put(aCpt, marks.get(aCpt) - 1);
    }

    // --------------------------------------
    // --------------------------------------
    //			Inherited Methods
    // --------------------------------------
    // --------------------------------------
    public String getDescription() {
        return "Ceres";
    }

    @Override
    public ConceptOrder getResult() {
        return theGSH;
    }

    @Override
    public void run() {
        if (/*binCtx.getAttributeNumber()==0 || */binCtx.getObjectCount() == 0) {
            return;
        }

        theGSH = new ConceptOrder("AOCposetWithCeres", binCtx, getDescription());
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
        // decreeasing extent sort
        Arrays.sort(preCptTab, new Comparator<PreConcept>() {
            @Override
            public int compare(PreConcept p1, PreConcept p2) {
                return Integer.compare(p2.getExtent().cardinality(), p1.getExtent().cardinality());
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
            sizeToDo = preCptTab[startIndex].getExtent().cardinality();

            // On rassemle les pre-concepts de taille d'extent identique
            // Si deux pre-concept ont un extent identique on fusionne ces deux concepts dans le premier le dernier ne sera pas considere
            while (endIndex < preCptTab.length && preCptTab[endIndex].getExtent().cardinality() == sizeToDo) {
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
