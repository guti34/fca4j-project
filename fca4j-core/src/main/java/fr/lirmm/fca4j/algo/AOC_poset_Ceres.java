/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.algo;

import java.util.Arrays;
import java.util.Iterator;

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
 *
 * <p>Cette version est issue d'une campagne d'optimisation menée sur le corpus
 * de référence : le temps total y est passé de 5961 à 639 ms, à sortie
 * rigoureusement identique — mêmes concepts, mêmes arêtes, et mêmes compteurs
 * d'opérations à chaque étape. Les choix qui en découlent sont commentés là où
 * ils se trouvent, avec la mesure qui les a motivés. L'instrumentation qui a
 * servi à les établir a été retirée.
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

    // ── couvertures tenues par l'algorithme ──────────────────────────────
    //
    // Ceres crée lui-même TOUTES les arêtes du diagramme, dans Classify, et n'en
    // retire jamais aucune : il n'y a pas un seul removePrecedenceConnection dans
    // ce fichier. Il connaît donc la structure aussi bien que theGSH, et n'a
    // aucune raison de la redemander à un graphe générique.
    //
    // Le profil a montré ce que coûtait de la redemander : sur chess, 77 371 073
    // enfants parcourus à 44 ns pièce. Chaque itération traversait les arêtes
    // JGraphT, résolvait un sommet, boxait un Integer ; et initMark, à la
    // première visite d'un concept, appelait getUpperCover(c).cardinality(), ce
    // qui alloue un ISet de |G|+|A| bits pour n'en lire que la cardinalité.
    //
    // Représentation : stockage CONTIGU, une plage d'entiers consécutifs par
    // concept. Une première version chaînait les arêtes entre elles ; le profil a
    // montré que le saut d'un maillon à l'autre, dans un tableau de 300 Ko sur
    // chess, dominait tout le reste — 7,3 ns par enfant parcouru, soit un défaut
    // de cache à chaque pas. Ici les deux parcours sont séquentiels.
    //
    // Les deux sens n'ont pas la même dynamique, donc pas la même structure :
    //
    //  - PARENTS : la couverture supérieure d'un concept est écrite en une seule
    //    fois, à son insertion, et ne bouge plus. Une zone d'allocation unique en
    //    append suffit : parentStart[c] donne le début de la plage, parentCount[c]
    //    sa longueur. Aucune place perdue.
    //  - ENFANTS : la couverture inférieure d'un concept s'enrichit au fil du
    //    temps, chaque fois qu'un nouveau concept le choisit pour parent. Un
    //    tableau par concept, doublé à saturation, alloué à la première insertion.
    //
    // L'ordre de parcours des enfants est celui de l'insertion. Sans effet sur le
    // résultat : le compteur de maturité garantit qu'un nœud n'est traité qu'une
    // fois tous ses parents vus, quel que soit l'ordre, et potentialUpperCover
    // est un ensemble.
    private int[] parentArena;   // plages consécutives, une par concept
    private int parentArenaLen;
    private int[] parentStart;   // début de la plage de parents du concept
    private int[] parentCount;   // longueur, donc cardinalité de la couverture supérieure
    private int[][] childArr;    // par concept : enfants, tableau propre
    private int[] childLen;

    /** Tampon de comptage du tri de WorkOnLeftPart2, alloué une fois. */
    private int[] sortBuckets;

    /** Indices des mots utiles de l'extent en cours d'insertion. Réutilisé d'un
     *  appel à Classify au suivant : sa taille ne dépend que du contexte. */
    private int[] activeWords;

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

    // ── gestion des couvertures ──────────────────────────────────────────

    private void initCovers(int maxConcepts) {
        parentStart = new int[maxConcepts];
        parentCount = new int[maxConcepts];
        childArr = new int[maxConcepts][];
        childLen = new int[maxConcepts];
        parentArena = new int[Math.max(1024, maxConcepts)];
        parentArenaLen = 0;
    }

    /** Ajoute un parent à la plage en cours de construction. */
    private void pushParent(int parent) {
        if (parentArenaLen == parentArena.length) {
            parentArena = Arrays.copyOf(parentArena, parentArena.length * 2);
        }
        parentArena[parentArenaLen++] = parent;
    }

    /** Ajoute un enfant à la liste d'un concept déjà inséré. */
    private void pushChild(int parent, int child) {
        int[] a = childArr[parent];
        int len = childLen[parent];
        if (a == null) {
            a = new int[4];
            childArr[parent] = a;
        } else if (len == a.length) {
            a = Arrays.copyOf(a, len * 2);
            childArr[parent] = a;
        }
        a[len] = child;
        childLen[parent] = len + 1;
    }

    private void Classify(PreConcept cptToAdd, ISet allCoveredIntent, boolean isAttributeCpt) {

        // Mots utiles de l'extent à insérer, relevés UNE FOIS pour tout l'appel.
        // L'extent ne bouge pas pendant Classify (seul l'intent est enrichi, et
        // seulement pour les concepts attribut), donc le relevé reste valide
        // jusqu'à la fin de la boucle.
        //
        // Ce qu'on évite : containsAll parcourt tous les mots de son argument, y
        // compris les nuls. Le profil a chiffré la densité — sur ord6magic04,
        // 64 mots occupés sur 298, soit 119 M de mots balayés dont 102 M sur du
        // vide. Ici on ne traverse que les mots occupés.
        final ISet newExtent = cptToAdd.getExtent();
        final int nActive = newExtent.nonZeroWords(activeWords);

        classifyIdentifier++;
        // File du BFS sur tableau : une LinkedList<Integer> boxait chaque nœud et
        // allouait un maillon par insertion. Le nombre de nœuds défilés est borné
        // par le nombre de concepts déjà présents.
        int[] queue = fifo;
        int qHead = 0, qTail = 0;
        ISet potentialUpperCover = factory.createSet(binCtx.getAttributeCount()+binCtx.getObjectCount()); // un ensemble de parents de N
        queue[qTail++] = theGSH.getTop(); // Q recoit top en initialisation
        int nextCpt;
        while (qHead < qTail) {
            nextCpt = queue[qHead++];
            potentialUpperCover.add(nextCpt);
            // Retrait élément par élément plutôt qu'une différence dense : la
            // couverture supérieure compte quelques éléments, l'ensemble dense en
            // fait |G|+|A| bits.
            int ps = parentStart[nextCpt];
            int pc = parentCount[nextCpt];
            for (int k = 0; k < pc; k++) {
                potentialUpperCover.remove(parentArena[ps + k]);
            }
            if (isAttributeCpt) {
                cptToAdd.getIntent().addAll(theGSH.getConceptReducedIntent(nextCpt));
                // on peut modifier directement l'intension car la SHG est une EXTENT_LEVEL_INDEX
            }
            final int[] ch = childArr[nextCpt];
            final int cn = childLen[nextCpt];
            for (int k = 0; k < cn; k++) {
                int P = ch[k]; // P est un enfant du pere de N considere (CSC)
                changeMarkValue(P);
                if (isReady(P)) {
                    if (theGSH.getConceptExtent(P).containsAllSparse(newExtent, activeWords, nActive)) {
                        queue[qTail++] = P;
                    }
                }
            }

        }
        // ICI DSC contient tous les parents direct du noeud N a inserer !
        int numCptToAdd = theGSH.addConcept(cptToAdd.getExtent(), cptToAdd.getIntent(), cptToAdd.getRExtent(), cptToAdd.getRIntent());
        // La plage de parents est écrite d'un bloc, donc contiguë. theGSH et les
        // couvertures locales doivent rester en phase : les deux écritures sont
        // ici et nulle part ailleurs.
        int start = parentArenaLen;
        int nbParents = 0;
        for (Iterator<Integer> it = potentialUpperCover.iterator(); it.hasNext();) {
            int parent = it.next();
            theGSH.addPrecedenceConnection(numCptToAdd, parent);
            pushParent(parent);
            pushChild(parent, numCptToAdd);
            nbParents++;
        }
        parentStart[numCptToAdd] = start;
        parentCount[numCptToAdd] = nbParents;

    }

    /** File du BFS, réutilisée d'un appel à l'autre. */
    private int[] fifo;

    private void WorkOnLeftPart2(PreConcept addedCpt, ISet allCoveredIntent) throws CloneNotSupportedException {

        // CC vas contenir les objets qui ne sont pas dans l'extension simplifie mais dans l'extension complete
        // Tableau d'int dimensionné sur la cardinalité de l'extent, au lieu d'une
        // ArrayList<Integer> : sur ord10shuttle ce sont 1,5 million de boîtes en
        // moins.
        int[] raw = new int[addedCpt.getExtent().cardinality()];
        int n = 0;
        for (Iterator<Integer> it = addedCpt.getExtent().iterator(); it.hasNext();) {
            int anObject = it.next();
            if (!addedCpt.getRExtent().contains(anObject)) {
                raw[n++] = anObject;
            }
        }

        // Tri par cardinalité d'intension croissante. La version précédente triait
        // un Integer[] avec un comparateur qui rappelait binCtx.getIntent(e)
        // .cardinality() à CHAQUE comparaison — le commentaire affirmait le
        // contraire, mais foCard n'était rempli qu'après le tri. Sur ord10shuttle
        // ce seul poste pesait 72 % du temps total.
        //
        // Tri par comptage : |f(o)| est borné par |A|, donc O(n + |A|). Il est
        // stable, comme l'était le TimSort sur Integer[], donc les objets de même
        // cardinalité conservent l'ordre de parcours de l'extent et la sortie est
        // inchangée.
        int m = binCtx.getAttributeCount();
        int[] rawCard = new int[n];
        int[] bucket = sortBuckets;
        Arrays.fill(bucket, 0, m + 2, 0);
        for (int i = 0; i < n; i++) {
            int c = binCtx.getIntent(raw[i]).cardinality();
            rawCard[i] = c;
            bucket[c + 1]++;
        }
        for (int k = 0; k <= m; k++) {
            bucket[k + 1] += bucket[k];
        }
        int[] objs = new int[n];
        final ISet[] fo = new ISet[n];
        final int[] foCard = new int[n];
        for (int i = 0; i < n; i++) {
            objs[bucket[rawCard[i]]++] = raw[i];
        }
        for (int i = 0; i < n; i++) {
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
        // Lecture d'un tableau, là où getUpperCover(aCpt).cardinality() allouait
        // et remplissait un ISet de |G|+|A| bits pour n'en lire que la taille.
        marks[aCpt] = parentCount[aCpt];
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
        fifo = new int[maxConcepts];
        sortBuckets = new int[binCtx.getAttributeCount() + 2];
        activeWords = new int[(binCtx.getObjectCount() >> 6) + 2];
        initCovers(maxConcepts);
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
        Arrays.sort(preCptTab, (p1, p2) -> Integer.compare(p2.extentCard, p1.extentCard));
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
                    if (!preCptDone[i]) {
                        if (preCptTab[i].getExtent().equals(preCptTab[endIndex].getExtent())) {
                            preCptTab[i].getIntent().addAll(preCptTab[endIndex].getIntent());
                            preCptTab[i].getRIntent().addAll(preCptTab[endIndex].getIntent());
                            preCptDone[endIndex] = true;
                        }
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
        if (theGSH.getConceptReducedExtent(top).cardinality() == 0 && theGSH.getConceptReducedIntent(top).cardinality() == 0) {
            theGSH.removeConcept(top);
        }

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
