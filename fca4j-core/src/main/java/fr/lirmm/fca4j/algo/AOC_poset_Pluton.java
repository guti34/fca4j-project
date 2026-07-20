/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.IConceptOrder;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

/**
 * The Class AOC_poset_Pluton.
 */
public class AOC_poset_Pluton implements AbstractAlgo<IConceptOrder> {

    private IBinaryContext matrix; //ressource de depart
    private IConceptOrder gsh = null; //ressource d'arrivee
    protected ISetFactory factory;
    private Chrono chrono = null; // eventually a chrono to store execution time 
    // visited flags for the order phase. A flat boolean[] indexed by concept id
    // replaces the former HashSet<Integer> (boxing + hashing on every get/add/remove),
    // which was hit K^2 times in the order loop and once per node in completeDescendance.
    private boolean[] visited;
    /** reusable DFS stack for completeDescendance, grown on demand */
    private int[] descStack = new int[64];
    protected HashSet<Integer> upperCover = new HashSet<>();
    protected HashSet<Integer> lowerCover = new HashSet<>();

    /**
     * Instantiates a new AO C poset pluton.
     *
     * @param matrix the matrix
     * @param chrono the chrono
     */
    public AOC_poset_Pluton(IBinaryContext matrix, Chrono chrono) {
        super();
        this.matrix = matrix;
        this.factory = matrix.getFactory();
        this.chrono = chrono;
    }

    /**
     * Instantiates a new AO C poset pluton.
     *
     * @param matrix the matrix
     */
    public AOC_poset_Pluton(IBinaryContext matrix) {
        this(matrix, null);
    }
    //se referencer e l'article sur Pluton

    private ArrayList<ISet> maxmodPartitionOA() {
        //resultat de l'algo
        ArrayList<ISet> part = new ArrayList<>();
        //on initilise PART avec tous les objets
        ISet objects = factory.createSet(matrix.getObjectCount());
        objects.fill(matrix.getObjectCount());
        part.add(objects);
        //et on va les partitionner par rapport aux attribus
        for (int numAttr = 0; numAttr < matrix.getAttributeCount(); numAttr++) {
            //displayTrack("PART="+PART);
            ISet r = matrix.getExtent(numAttr);
            //displayTrack("\tR["+matrix.getAttribute(i)+"]="+R);
            //on se sert d'une autre collection pour ne pas avoir e s'emmeler les pinceaux avec les indices
            ArrayList<ISet> newPart = new ArrayList<>();
            for (int j = 0; j < part.size(); j++) {
                ISet k = part.get(j);
                if (k.cardinality() > 1) {
                    ISet k1 = (ISet) k.newIntersect(r);
                    ISet k2 = (ISet) k.newDifference(r);
                    if (!k1.isEmpty()) {
                        newPart.add(k1);
                    }
                    if (!k2.isEmpty()) {
                        newPart.add(k2);
                    }
                } else {
                    newPart.add(k);
                }
            }
            part = newPart;
        }
        //displayTrack("PART="+PART);
        return part;
    }

    //version duale ou les roles des objets et des attributs sont inverses
    //le parametre passe e cette fonction doit etre normalement le retour de maxmodPartitionOA
    private ArrayList<ISet> maxmodPartitionAO(ArrayList<ISet> partObjects) {
        ArrayList<ISet> PART = new ArrayList<>();
        ISet attributes = factory.createSet(matrix.getAttributeCount());
        attributes.fill(matrix.getAttributeCount());
        PART.add(attributes);
        for (int i = partObjects.size() - 1; i >= 0; i--) {
            //displayTrack("PART="+PART);
            int x = partObjects.get(i).first();
            ISet R = matrix.getIntent(x);
            //displayTrack("\tR["+x+"]="+R);
            ArrayList<ISet> newPART = new ArrayList<>();
            for (int j = 0; j < PART.size(); j++) {
                ISet K = PART.get(j);
                if (K.cardinality() > 1) {
                    ISet K1 = (ISet) K.newIntersect(R);
                    ISet K2 = (ISet) K.newDifference(R);
                    if (!K1.isEmpty()) {
                        newPART.add(K1);
                    }
                    if (!K2.isEmpty()) {
                        newPART.add(K2);
                    }
                } else {
                    newPART.add(K);
                }
            }
            PART = newPART;
        }
        //displayTrack("PART="+PART);
        return PART;
    }

    //preparation au calcul de l'extension lineaire
    private BitSet tomThumb(ArrayList<ISet> list) {
        ArrayList<ISet> Y = maxmodPartitionOA();
        ArrayList<ISet> X = maxmodPartitionAO(Y);
        //on a une liste partitionnee d'objets et une autre d'attributs
        //il faut les fusionner dans l'ordre explique dans l'article
        int q = Y.size();
        int r = X.size();
        int j = q - 1;
        int i = 0;
        BitSet isExtentBS = new BitSet();
        while (j >= 0 && i < r /*&& !Y.get(j).isEmpty()&& !X.get(i).isEmpty()*/) {
            ISet Yj = Y.get(j);
            int y = Yj.first();
            ISet Xi = X.get(i);
            int x = Xi.first();
            if (matrix.get(y, x)) {
                list.add(Xi);
                i++;
            } else {
                list.add(Yj);
                isExtentBS.set(list.size() - 1);
                j--;
            }
        }
        //on ajoute le reste des objets (s'il n'y a plus d'attributs) 
        while (j >= 0) {
            list.add(Y.get(j));
            isExtentBS.set(list.size() - 1);
            j--;
        }
        //ou on rajoute le reste des attributs (s'il n'y a plus d'objets)
        while (i < r) {
            list.add(X.get(i));
            i++;
        }
        return isExtentBS;
    }

    /**
     * Compute linext.
     *
     * @return the array list
     * @throws Exception the exception
     */
    //calcul de l'extension lineaire
    public ArrayList<Integer> computeLinext() throws Exception {
        ArrayList<Integer> linext = new ArrayList<>();
        if (matrix.getAttributeCount() == 0) {
            ISet extent = factory.createSet(matrix.getObjectCount());
            extent.fill(matrix.getObjectCount());
            int concept = gsh.addConcept(extent, factory.createSet(matrix.getAttributeCount()), factory.clone(extent), factory.createSet(matrix.getAttributeCount()));
            linext.add(concept);
            return linext;
        }
        ArrayList<ISet> maxmods = new ArrayList<ISet>();
        BitSet isExtentBS = tomThumb(maxmods);
        //on a une liste alternee d'extensions et d'intensions mais non couples en concepts
        //les concepts calcules seront des concepts simplifies, les heritages devront etre calcule une fois l'ordre calcule
        //pour savoir si le dernier maxmod sera seul ou couple
        boolean lastAlone = true;
        for (int i = maxmods.size() - 1; i > 0; i--) {
            ISet set1 = maxmods.get(i);
            ISet set2 = maxmods.get(i - 1);
            //un couple est un ensemble d'objets suivi d'un ensemble d'attributs
            //sinon l'ensemble forme un concept tout seul			
            if (isExtentBS.get(i))// if set1 is an extent
            {
                boolean couple = false;
                ISet objects = set1;
                if (!isExtentBS.get(i - 1)) // if set2 is an intent
                {
                    int o = objects.first();
                    ISet ga = matrix.getExtent(set2.first());
                    //1er test
                    if (ga.contains(o)) {
                        couple = true;
                        ISet fo = matrix.getIntent(o);
                        //cette boucle est le 2e est
                        for (Iterator<Integer> j = ga.iterator(); couple && j.hasNext();) {
                            ISet fj = matrix.getIntent(j.next());
                            if (!fj.containsAll(fo)) {
                                couple = false;
                            }
                        }
                    } else {
                        couple = false;
                        //displayTrack("\t"+o+" is not included in "+ga);
                    }
                } else {
                    couple = false;
                }
//				MyExtent extent=new MyExtent(objects.size());
//				for(Iterator<Integer> j=objects.iterator();j.hasNext();extent.add(j.next()));
                ISet extent = factory.clone(objects);
                ISet intent = factory.createSet(matrix.getAttributeCount());
                if (couple) {
                    //les deux maxmods sont dans un meme concept
                    ISet attributes = set2;
                    intent = factory.clone(attributes);
                    if (i == 1) {
                        lastAlone = false;
                    }
                    i--;
                }//sinon dans le concept simplifie il n'y a que des objets
                int concept = gsh.addConcept(extent, intent, factory.clone(extent), factory.clone(intent));
                linext.add(concept);
            } else //le concept sera un concept avec une extension simplifiee vide
            {
                ISet attributes = set1;
                //displayTrack(""+attributes+" is added alone");
                ISet intent = factory.clone(attributes);
                int concept = gsh.addConcept(factory.createSet(matrix.getObjectCount()), intent, factory.createSet(matrix.getObjectCount()), factory.clone(intent));
                linext.add(concept);
            }
        }
        //displayTrack("linext="+linextToString(linext));
        if (lastAlone) {
            //le dernier ensemble se retrouve donc seul
            ISet maxmod = maxmods.get(0);
            //displayTrack(maxmod+" is remaining, we add it alone");
            ISet extent = factory.createSet(matrix.getObjectCount());
            ISet intent = factory.createSet(matrix.getAttributeCount());
            if (isExtentBS.get(0)) {
                extent = factory.clone(maxmod);
            } else {
                intent = factory.clone(maxmod);
            }
            int concept = gsh.addConcept(extent, intent, factory.clone(extent), factory.clone(intent));
            linext.add(concept);
        }
        //displayTrack("linext="+linextToString(linext));
        return linext;
    }

    //pour determiner si S est le pere de T
    //les donnees relatives a S (calculees une seule fois par la boucle externe) sont passees en parametres:
    //  s_hasObjects : S a-t-il une extension reduite non vide
    //  fs           : f(objet representant de S), si s_hasObjects
    //  gs           : g(attribut representant de S), sinon
    //  s_attr       : attribut representant de S, sinon
    private boolean isParentOf(boolean s_hasObjects, ISet fs, ISet gs, int s_attr, int conceptT) {
        boolean t_hasObjects = !gsh.getConceptReducedExtent(conceptT).isEmpty();
        boolean t_hasAttributes = !gsh.getConceptReducedIntent(conceptT).isEmpty();

        if (s_hasObjects) {
            // si S et T ont des extensions reduites non vide, on compare par inclusion de ces deux ensembles
            if (t_hasObjects) {
                ISet ft = matrix.getIntent(gsh.getConceptReducedExtent(conceptT).first());
                return ft.containsAll(fs);
            } // sinon on verifie que les objets partageant les attributs de S incluent l'ensemble
            // des objets ayant un attribut de T
            else {
                ISet ft = matrix.getExtent(gsh.getConceptReducedIntent(conceptT).first());
                for (Iterator<Integer> it = fs.iterator(); it.hasNext();) {
                    if (!matrix.getExtent(it.next()).containsAll(ft)) {
                        return false;
                    }
                }
                return true;
            }
        } else {
            // si S et T ont des intensions reduites non vide, on compare par inclusion de ces deux ensembles
            if (t_hasAttributes) {
                ISet gt = matrix.getExtent(gsh.getConceptReducedIntent(conceptT).first());
                return gs.containsAll(gt);
            } else {
                int t_obj = gsh.getConceptReducedExtent(conceptT).first();
                return matrix.get(t_obj, s_attr);
            }
        }
    }

    //marquer tous les descendants d'un concept et heriter des attributs des parents (pour eviter les arcs de transitivite)
    // Iterative DFS: the recursion could reach the JVM stack limit on deep lattices,
    // and on a DAG a node shared by several paths must be marked (and inherit) exactly
    // once. The visited flag is the guard, exactly as in the recursive form: the start
    // node is never marked, each descendant is pushed only when first seen, marked and
    // made to inherit intent once. Semantics are identical to the former recursion.
    private void completeDescendance(int concept, ISet intent) {
        int[] stack = descStack;
        int sp = 0;
        for (Iterator<Integer> it = gsh.getLowerCoverIterator(concept); it.hasNext();) {
            int child = it.next();
            if (!isVisited(child)) {
                setVisited(child, true);
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
                if (!isVisited(child)) {
                    setVisited(child, true);
                    gsh.getConceptIntent(child).addAll(intent);
                    if (sp == stack.length) {
                        stack = Arrays.copyOf(stack, stack.length << 1);
                    }
                    stack[sp++] = child;
                }
            }
        }
        descStack = stack; // keep the (possibly grown) buffer for reuse
    }

    /**
     * Compute GSH.
     *
     * @return the concept order
     * @throws Exception the exception
     */
    //fonction principale, celle qui calcule la SHG
    public IConceptOrder computeGSH() throws Exception {
        // Clarify first, exactly like Hermes: equivalent objects (resp. attributes)
        // collapse into one class, Pluton runs on the smaller clarified context, and
        // the reduced sets are expanded back to the original ids at the end via
        // ConceptOrder.substitutionReduced. This removes Pluton's only weak spot --
        // contexts with heavy object redundancy (e.g. ord6magic raw, 19020 objects for
        // 4461 classes) -- without touching the algorithm itself: the clarified context
        // is what the whole computation sees, so correctness is unchanged and is still
        // exercised end-to-end by AocAudit (which calls run() directly).
        IBinaryContext originalCtx = matrix;
        if (chrono != null) {
            chrono.start("clarify");
        }
        Clarification clarification = new Clarification(originalCtx, originalCtx.getName(), true, true, false);
        clarification.run();
        IBinaryContext clarifiedCtx = clarification.getResult();
        List<ISet> objClasses = clarification.getObjectClasses();
        List<ISet> attrClasses = clarification.getAttributeClasses();
        if (chrono != null) {
            chrono.stop("clarify");
        }
        // run the whole algorithm against the clarified context
        matrix = clarifiedCtx;
        gsh = new ConceptOrder("AOCposetWithPluton", clarifiedCtx, getDescription());
        // concept ids stay in [0, |G|+|M|); no removeConcept happens during the build
        visited = new boolean[clarifiedCtx.getObjectCount() + clarifiedCtx.getAttributeCount() + 1];
        if (chrono != null) {
            chrono.start("concept");
        }
        ArrayList<Integer> linext = computeLinext();
        if (chrono != null) {
            chrono.stop("concept");
        }
        if (chrono != null) {
            chrono.start("order");
        }
        //une fois l'extension lineaire calculee (les heritages ne sont pas encore calcules e ce stade)
        //il faut calculer l'ordre
        for (int i = 0; i < linext.size(); i++) {
            int conceptS = linext.get(i);
            // Everything isParentOf needs about S is invariant across the inner loop,
            // so prepare it once here instead of recomputing f(repr(S)) / g(attr(S))
            // for every predecessor T.
            ISet sRExtent = gsh.getConceptReducedExtent(conceptS);
            boolean s_hasObjects = !sRExtent.isEmpty();
            ISet fs = null;   // f(representative object of S), when S has objects
            ISet gs = null;   // g(representative attribute of S), when S has none
            int s_attr = -1;
            if (s_hasObjects) {
                fs = matrix.getIntent(sRExtent.first());
            } else {
                s_attr = gsh.getConceptReducedIntent(conceptS).first();
                gs = matrix.getExtent(s_attr);
            }
            ISet sRIntent = gsh.getConceptReducedIntent(conceptS);
            for (int j = i - 1; j >= 0; j--) {
                //on compare chaque noeud dans l'extension lineaire e ces precedents
                //sauf ceux qui sont marques, afin d'eviter les arcs de transitivite
                int conceptT = linext.get(j);
                if (isVisited(conceptT)) {
                    setVisited(conceptT, false); //on demarque pour le prochain tour de la boucle principale
                } else if (isParentOf(s_hasObjects, fs, gs, s_attr, conceptT)) {
                    //si S est le pere de T, on rajoute l'arc et on marque les descendants de T afin d'eviter les arcs de transitivite
                    gsh.addPrecedenceConnection(conceptT, conceptS);
                    gsh.getConceptExtent(conceptS).addAll(gsh.getConceptExtent(conceptT));
                    gsh.getConceptIntent(conceptT).addAll(sRIntent);
                    completeDescendance(conceptT, sRIntent);
                }
            }
//			setPercentageOfWork((i*100)/linext.size());
        }
        if (chrono != null) {
            chrono.stop("order");
        }
        // expand the reduced extents/intents from clarified-class ids back to the
        // original object/attribute ids, and reset the order's context to the
        // original. substitutionReduced only remaps the reduced sets (and the
        // context): that is what the JSON lattice output and the audit read. If a
        // consumer needs the full extents/intents in original space, call
        // buildExtentIntent() afterwards.
        if (chrono != null) {
            chrono.start("substitution");
        }
        ((ConceptOrder) gsh).substitution(originalCtx, attrClasses, objClasses);
        matrix = originalCtx;
        if (chrono != null) {
            chrono.stop("substitution");
        }
        return gsh;
    }

    private boolean isVisited(int concept) {
        return visited[concept];
    }

    private void setVisited(int concept, boolean b) {
        visited[concept] = b;
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
            Logger.getLogger(AOC_poset_Pluton.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    @Override
    public String getDescription() {
        return "Pluton";
    }

}
