/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.IConceptOrder;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

/**
 * The Class AOC_poset_Hermes.
 *
 * <p>Cette version est issue d'une campagne d'optimisation : sur ord6magic04
 * (19020 x 52), le temps est passe de 2590 a environ 515 ms, a sortie
 * rigoureusement identique — memes concepts, memes aretes, audit vert sur 55978
 * contextes a chaque etape. Trois changements y ont contribue, chacun commente
 * la ou il se trouve avec la mesure qui l'a motive : marques en boolean[],
 * grandeurs invariantes hissees hors de la boucle interne, et regroupement par
 * hachage dans la clarification. L'instrumentation qui a servi a les etablir a
 * ete retiree.
 */
public class AOC_poset_Hermes implements AbstractAlgo<IConceptOrder> {

	protected IBinaryContext matrix; //ressource de depart
    protected IConceptOrder gsh = null; //ressource d'arrivee
    protected Chrono chrono = null; // eventually a chrono to store execution time 
    // Marques de la phase d'ordre. Un boolean[] indexe par identifiant de
    // concept remplace le HashSet<Integer> d'origine : la boucle interne du
    // diagramme de Hasse interroge cette structure une fois par PAIRE de
    // concepts, soit 128,8 millions de fois sur un contexte a 16052 concepts.
    // Chaque interrogation construisait un Integer (autoboxing au-dela de 127)
    // puis le hachait — d'ou les 1,4 Go rendus toutes les cinq secondes que
    // montrait le journal du ramasse-miettes, alors que les ensembles alloues
    // ne totalisaient que 97 Mo. Pluton utilise deja cette representation.
    protected boolean[] visited;

    /** Pile de completeDescendance, conservee d'un appel au suivant. */
    private int[] descStack = new int[64];
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
        // Regroupement par HACHAGE a l'interieur de chaque bloc de cardinalite
        // egale, au lieu d'une comparaison a tous les predecesseurs du bloc.
        //
        // La version precedente comparait chaque element a ses predecesseurs
        // jusqu'a sortir du bloc : un cout en somme des B² sur les blocs. Avec
        // 52 attributs, les cardinalites vont de 0 a 52, donc une cinquantaine
        // de blocs pour 19020 objets — des blocs de plusieurs centaines
        // d'elements. Le profil mesurait 5 819 609 comparaisons sur ord6magic04,
        // et la clarification y pesait 48,8 % du temps total.
        //
        // Le hachage porte sur les MOTS de l'ensemble, avec le finaliseur
        // d'avalanche fmix64 : sans lui, FNV-1a ne diffuse pas vers les bits de
        // POIDS FAIBLE, qui sont pourtant ceux que l'indice de seau retient. Le
        // meme correctif avait fait tomber la phase couvertures de PARALLEL_CBO
        // de 642 a 266 ms.
        //
        // Deux ensembles egaux ont necessairement le meme hache, donc aucune
        // fusion n'est manquee ; l'egalite reste verifiee avant toute fusion,
        // donc aucune fusion abusive. La suppression se fait en fin de bloc et
        // par indices decroissants, ce qui preserve l'ordre relatif des
        // survivants — dont depend la numerotation des classes, et donc la
        // sortie.
        int blockStart = 0;
        while (blockStart < setToClarify.size()) {
            int card = setToClarify.get(blockStart).values.cardinality();
            int blockEnd = blockStart + 1;
            while (blockEnd < setToClarify.size()
                    && setToClarify.get(blockEnd).values.cardinality() == card) {
                blockEnd++;
            }
            int blockSize = blockEnd - blockStart;
            if (blockSize > 1) {
                java.util.HashMap<Long, java.util.ArrayList<Integer>> buckets =
                        new java.util.HashMap<>(blockSize * 2);
                java.util.ArrayList<Integer> doomed = new java.util.ArrayList<>();
                for (int i = blockStart; i < blockEnd; i++) {
                    RefSet cur = setToClarify.get(i);
                    Long h = Long.valueOf(hashValues(cur.values));
                    java.util.ArrayList<Integer> bucket = buckets.get(h);
                    if (bucket == null) {
                        bucket = new java.util.ArrayList<>(2);
                        buckets.put(h, bucket);
                        bucket.add(Integer.valueOf(i));
                        continue;
                    }
                    boolean merged = false;
                    for (int k = 0; k < bucket.size(); k++) {
                        RefSet other = setToClarify.get(bucket.get(k).intValue());
                        if (other.values.equals(cur.values)) {
                            other.addRef(cur.refs);
                            doomed.add(Integer.valueOf(i));
                            merged = true;
                            break;
                        }
                    }
                    if (!merged) {
                        bucket.add(Integer.valueOf(i));
                    }
                }
                for (int d = doomed.size() - 1; d >= 0; d--) {
                    setToClarify.remove(doomed.get(d).intValue());
                }
                blockEnd -= doomed.size();
            }
            blockStart = blockEnd;
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
     * Hache un ensemble par ses mots de 64 bits.
     *
     * <p>FNV-1a sur les mots, suivi de l'avalanche fmix64 de MurmurHash3. Le
     * finaliseur n'est pas cosmetique : dans une multiplication modulo 2^64, le
     * bit j du produit ne depend que des bits 0..j des operandes, si bien que
     * FNV-1a seul ne diffuse pas vers les bits de poids faible — ceux-la memes
     * que retient l'indice de seau. Sur des ensembles fortement structures, la
     * distribution s'effondre.
     *
     * <p>Passe par un itERATEUR faute d'acces aux mots depuis l'interface ISet :
     * le cout est proportionnel au nombre d'ELEMENTS et non a la capacite, ce
     * qui convient ici puisque les ensembles clarifies sont creux.
     *
     * @param set l'ensemble a hacher
     * @return un hache diffuse sur les 64 bits
     */
    private static long hashValues(ISet set) {
        long h = 1469598103934665603L;
        for (Iterator<Integer> it = set.iterator(); it.hasNext();) {
            h = (h ^ it.next().intValue()) * 1099511628211L;
        }
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
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
                if (i != j) {
                }
                boolean b = (i == j || attrSet.isInclude(attr2Set));
                if (b) {
                    newSet.values.add(j);
                }
            }
            domRelation.add(newSet);
        }
        return domRelation;
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

        final int nbConcepts = conceptSets.size();
        // Les identifiants rendus par addConcept restent dans [0, |G|+|A|] : aucun
        // concept n'est supprime pendant la construction.
        visited = new boolean[matrix.getObjectCount() + matrix.getAttributeCount() + 1];
        // int[] plutot qu'ArrayList<Integer> : lu une fois par paire examinee.
        final int[] concepts = new int[nbConcepts];
        // conceptSetArray dupliquait conceptSets, deja trie et indexe a
        // l'identique : conceptSetArray.get(j) valait toujours conceptSets.get(j).
        //
        // Le premier element des ensembles REDUITS est mis en cache. Ces
        // ensembles sont des clones passes a addConcept et ne sont jamais
        // modifies ensuite — seuls les extents et intents COMPLETS le sont —
        // donc la valeur reste juste. Sans ce cache, first() etait recalcule
        // une fois par paire.
        final int[] rextFirst = new int[nbConcepts];

        for (int i = 0; i < nbConcepts; i++) {
            ConceptSet cSet = conceptSets.get(i);
            int conceptS = gsh.addConcept(cSet.extent, cSet.intent, factory.clone(cSet.extent), factory.clone(cSet.intent));
            concepts[i] = conceptS;
            rextFirst[i] = gsh.getConceptReducedExtent(conceptS).first();

            // Tout ce dont isParentOf a besoin au sujet de S est invariant dans
            // la boucle interne : on le prepare une fois ici plutot que de le
            // redemander a gsh pour chacun des i predecesseurs.
            final boolean sHasObject = rextFirst[i] >= 0;
            final ISet sRIntent = gsh.getConceptReducedIntent(conceptS);
            final int sAttr = sHasObject ? -1 : sRIntent.first();
            final ISet sValues = cSet.values;
            final ISet sExtent = gsh.getConceptExtent(conceptS);

            for (int j = i - 1; j >= 0; j--) {
                //on compare chaque noeud dans l'extension lineaire e ces precedents
                //sauf ceux qui sont marques, afin d'eviter les arcs de transitivite
                int conceptT = concepts[j];
                if (visited[conceptT]) {
                    visited[conceptT] = false; //on demarque pour le prochain tour de la boucle principale
                } else if (isParentOf(sHasObject, sAttr, sValues, rextFirst[j], conceptSets.get(j))) {

                    //si S est le pere de T, on rajoute l'arc et on marque les descendants de T afin d'eviter les arcs de transitivite
                    gsh.addPrecedenceConnection(conceptT, conceptS);
                    // Union d'ensembles au lieu d'un parcours element par element :
                    // meme resultat, un OU par mot de 64 bits au lieu d'un
                    // Integer boxe et d'un add() par objet.
                    ISet tExtent = gsh.getConceptExtent(conceptT);
                    sExtent.addAll(tExtent);
                    gsh.getConceptIntent(conceptT).addAll(sRIntent);
                    completeDescendance(conceptT, sRIntent);
                }
            }
//		setPercentageOfWork((i*100)/linext.size());
        }
    }
//pour determiner si S est le pere de T

    /**
     * S est-il pere de T ? Meme decision qu'auparavant, mais les grandeurs
     * relatives a S sont fournies par l'appelant, qui les a calculees une fois
     * pour toute sa boucle interne, et le premier element de l'extent reduit de
     * T vient du cache. La version precedente interrogeait gsh quatre fois par
     * appel, soit 128,8 millions de fois sur un contexte a 16052 concepts, pour
     * des valeurs qui ne dependaient que de S ou ne changeaient jamais.
     *
     * @param sHasObject l'extent reduit de S est-il non vide
     * @param sAttr      premier attribut reduit de S, ou -1 si S a des objets
     * @param sValues    ensemble de comparaison de S
     * @param tObject    premier objet reduit de T, ou -1 s'il n'y en a pas
     * @param tCS        pre-concept de T
     */
    private boolean isParentOf(boolean sHasObject, int sAttr, ISet sValues,
                               int tObject, ConceptSet tCS) {
        if (tObject >= 0 && !sHasObject) {
            return matrix.get(tObject, sAttr);
        } else {
            return tCS.values.containsAll(sValues);
        }
    }

//marquer tous les descendants d'un concept et heriter des attributs des parents (pour eviter les arcs de transitivite)

    // Parcours en profondeur ITERATIF. La recursion pouvait atteindre la limite
    // de pile sur les treillis profonds, et le profil montrait 1 124 664 noeuds
    // visites sur ord6magic04 pour 133,7 ms, soit 119 ns par noeud — le cout
    // d'un appel plus un Integer boxe par enfant. Sur un graphe oriente sans
    // circuit, un noeud partage par plusieurs chemins doit etre marque et heriter
    // exactement une fois : la marque est la garde, comme dans la forme
    // recursive. Le noeud de depart n'est jamais marque, chaque descendant est
    // empile a sa premiere rencontre. Semantique identique. Reprise de Pluton.
    private void completeDescendance(int concept, ISet intent) {
        int[] stack = descStack;
        int sp = 0;
        for (Iterator<Integer> it = gsh.getLowerCoverIterator(concept); it.hasNext();) {
            int child = it.next();
            if (!visited[child]) {
                visited[child] = true;
                gsh.getConceptIntent(child).addAll(intent);
                if (sp == stack.length) {
                    stack = Arrays.copyOf(stack, stack.length << 1);
                }
                stack[sp++] = child;
            }
        }
        while (sp > 0) {
            int node = stack[--sp];
            for (Iterator<Integer> it = gsh.getLowerCoverIterator(node); it.hasNext();) {
                int child = it.next();
                if (!visited[child]) {
                    visited[child] = true;
                    gsh.getConceptIntent(child).addAll(intent);
                    if (sp == stack.length) {
                        stack = Arrays.copyOf(stack, stack.length << 1);
                    }
                    stack[sp++] = child;
                }
            }
        }
        descStack = stack; // on conserve le tampon, eventuellement agrandi
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
