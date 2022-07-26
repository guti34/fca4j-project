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
import java.util.BitSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

public class AOC_poset_Pluton implements AbstractAlgo<ConceptOrder> {

    private IBinaryContext matrix; //ressource de depart
    private ConceptOrder gsh = null; //ressource d'arrivee
    protected ISetFactory factory;
    private Chrono chrono = null; // eventually a chrono to store execution time 
    private final HashSet<Integer> visited = new HashSet<>();
    protected HashSet<Integer> upperCover = new HashSet<>();
    protected HashSet<Integer> lowerCover = new HashSet<>();

    public AOC_poset_Pluton(IBinaryContext matrix, Chrono chrono) {
        super();
        this.matrix = matrix;
        this.factory = matrix.getFactory();
        this.chrono = chrono;
    }

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
    private boolean isParentOf(int conceptS, int conceptT) {
        boolean s_hasObjects = !gsh.getConceptReducedExtent(conceptS).isEmpty();
        boolean t_hasObjects = !gsh.getConceptReducedExtent(conceptT).isEmpty();
        boolean t_hasAttributes = !gsh.getConceptReducedIntent(conceptT).isEmpty();

        if (s_hasObjects) {
            ISet fs = matrix.getIntent(gsh.getConceptReducedExtent(conceptS).first());
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
            int s_attr = gsh.getConceptReducedIntent(conceptS).first();
            ISet gs = matrix.getExtent(s_attr);
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

    //fonction principale, celle qui calcule la SHG
    public ConceptOrder computeGSH() throws Exception {
        gsh = new ConceptOrder("AOCposetWithPluton", matrix, getDescription());
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
            for (int j = i - 1; j >= 0; j--) {
                //on compare chaque noeud dans l'extension lineaire e ces precedents
                //sauf ceux qui sont marques, afin d'eviter les arcs de transitivite
                int conceptT = linext.get(j);
                if (isVisited(conceptT)) {
                    setVisited(conceptT, false); //on demarque pour le prochain tour de la boucle principale
                } else if (isParentOf(conceptS, conceptT)) {
                    //si S est le pere de T, on rajoute l'arc et on marque les descendants de T afin d'eviter les arcs de transitivite
                    gsh.addPrecedenceConnection(conceptT, conceptS);
                    for (Iterator<Integer> k = gsh.getConceptExtent(conceptT).iterator(); k.hasNext();) {
                        gsh.getConceptExtent(conceptS).add(k.next());
                    }
                    //                                           gsh.addObjectToConcept(conceptS,k.next());
                    gsh.getConceptIntent(conceptT).addAll(gsh.getConceptReducedIntent(conceptS));
                    completeDescendance(conceptT, gsh.getConceptReducedIntent(conceptS));
                }
            }
//			setPercentageOfWork((i*100)/linext.size());
        }
        if (chrono != null) {
            chrono.stop("order");
        }
        return gsh;
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

    @Override
    public ConceptOrder getResult() {
        return gsh;
    }

    @Override
    public void run() {
        try {
            computeGSH();
        } catch (Exception ex) {
            Logger.getLogger(AOC_poset_Pluton.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public String getDescription() {
        return "Pluton";
    }

}
