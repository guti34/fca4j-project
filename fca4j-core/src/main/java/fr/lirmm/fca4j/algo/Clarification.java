/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import fr.lirmm.fca4j.core.BinaryContext;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.iset.std.BitSetFactory;

/**
 *
 * @author agutierr
 */
public class Clarification implements AbstractAlgo<IBinaryContext>{
    protected IBinaryContext matrix;
    protected IBinaryContext clarified_matrix=null;
    protected ISetFactory factory;
    String nameContext;
    boolean clarifyAttributes,clarifyObjects;
    boolean rename;
    protected ArrayList<ISet> equivClassAttributes=new ArrayList<>();
    protected ArrayList<ISet> equivClassObjects=new ArrayList<>();
    
    public Clarification(IBinaryContext matrix, String nameContext,boolean clarifyAttributes,boolean clarifyObjects,boolean renameAttributes){
        this.matrix=matrix;
        this.factory=matrix.getFactory();
        this.nameContext=nameContext;
        this.clarifyAttributes=clarifyAttributes;
        this.clarifyObjects=clarifyObjects;
        this.rename=renameAttributes;
    }
    protected ArrayList<RefSet> clarify(ArrayList<RefSet> setToClarify, ArrayList<RefSet> setToSynchronize) {
        // sort RefSets depending on the cardinality
        Collections.sort(setToClarify/*, comparator*/);
        for (int i = setToClarify.size() - 1; i > 0; i--) {
            RefSet setToCompare = setToClarify.get(i);
            for (int j = i - 1; j >= 0; j--) {
                RefSet iSet = setToClarify.get(j);
                int comparison = setToCompare.compareTo(iSet);
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


    @Override
    public String getDescription() {
        return "clarification";
    }

    public static List<ISet> getAttributesByEquivClasses(IBinaryContext context) {
        ArrayList<RefSet> attrSets = new ArrayList<>();
        int sizeSet=Integer.max(context.getAttributeCount(), context.getObjectCount());
        
            for (int numAttr = 0; numAttr < context.getAttributeCount(); numAttr++) {
                attrSets.add(new RefSet(numAttr, context.getExtent(numAttr)));
            }
        Collections.sort(attrSets);
        for (int i = attrSets.size() - 1; i > 0; i--) {
            RefSet setToCompare = attrSets.get(i);
            for (int j = i - 1; j >= 0; j--) {
                RefSet iSet = attrSets.get(j);
                int comparison = setToCompare.compareTo(iSet);
                if (comparison == 0) {
                    if (setToCompare.values.equals(iSet.values)) {
                        iSet.addRef(setToCompare.refs);
                        attrSets.remove(i);
                        break;
                    }
                } else {
                    break;
                }
            }
        }
        ArrayList<ISet> classes=new ArrayList<>();
       for(RefSet ref:attrSets)
       {
           if(!ref.refs.isEmpty())
               classes.add(ref.refs);
       }
       return classes;  
    }
    public static List<ISet> getObjectsByEquivClasses(IBinaryContext context) {
        ArrayList<RefSet> objSets = new ArrayList<>();
        int sizeSet=Integer.max(context.getAttributeCount(), context.getObjectCount());
            for (int numObj = 0; numObj < context.getObjectCount(); numObj++) {
                objSets.add(new RefSet(numObj, context.getIntent(numObj)));
            }
        Collections.sort(objSets);
        for (int i = objSets.size() - 1; i > 0; i--) {
            RefSet setToCompare = objSets.get(i);
            for (int j = i - 1; j >= 0; j--) {
                RefSet iSet = objSets.get(j);
                int comparison = setToCompare.compareTo(iSet);
                if (comparison == 0) {
                    if (setToCompare.values.equals(iSet.values)) {
                        iSet.addRef(setToCompare.refs);
                        objSets.remove(i);
                        break;
                    }
                } else {
                    break;
                }
            }
        }
        ArrayList<ISet> classes=new ArrayList<>();
       for(RefSet ref:objSets)
       {
           if(!ref.refs.isEmpty())
               classes.add(ref.refs);
       }
       return classes;  
    }

    @Override
    public IBinaryContext getResult() {
        return clarified_matrix;
    }

    @Override
    public void run() {
        ArrayList<RefSet> attrSets = new ArrayList<>();
        ArrayList<RefSet> objSets = new ArrayList<>();
        int sizeSet=Integer.max(matrix.getAttributeCount(), matrix.getObjectCount());
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
        }
        if(clarifyObjects) attrSets = clarify(objSets, attrSets);
        if(clarifyAttributes) objSets = clarify(attrSets, objSets);
        ArrayList<ISet> rows=new ArrayList<>();
        ArrayList<ISet> columns=new ArrayList<>();
        for(RefSet ref:attrSets){
        	ISet col=factory.createSet(ref.values.toBitSet(), matrix.getObjectCount());
        	columns.add(col);            
        }
        for(RefSet ref:objSets){
        	ISet row=factory.createSet(ref.values.toBitSet(), matrix.getAttributeCount());
            rows.add(row);   
        }
        BinaryContext newContext=new BinaryContext(rows, columns, nameContext,factory);
        for(RefSet ref:attrSets)
        {
            String attrName=matrix.getAttributeName(ref.refs.iterator().next());
        	ISet refs=factory.createSet(ref.refs.toBitSet(), matrix.getAttributeCount());           
            equivClassAttributes.add(refs);
            if(rename && ref.refs.cardinality()>1) attrName=attrName+"("+ref.refs.cardinality()+")";
            newContext.addAttributeName(attrName);
        }
        for(RefSet ref:objSets)
        {
            String objName=matrix.getObjectName(ref.refs.iterator().next());
        	ISet refs=factory.createSet(ref.refs.toBitSet(), matrix.getObjectCount());           
            equivClassObjects.add(refs);
            if(rename && ref.refs.cardinality()>1) objName=objName+"("+ref.refs.cardinality()+")";
            newContext.addObjectName(objName);
        }
        clarified_matrix=newContext;
    }
    public List<ISet> getAttributeClasses(){
    	return equivClassAttributes;
    }
    public List<ISet> getObjectClasses(){
    	return equivClassObjects;
    }
    private static class RefSet implements Comparable{

        private ISet refs;
        private ISet values;

        RefSet(int ref, ISet values) {
            this.refs = new BitSetFactory().createSet();
            this.refs.add(ref);
            this.values = values.clone();
        }

        RefSet(int ref) {
        	ISetFactory defaultFactory=new BitSetFactory();
            this.refs = defaultFactory.createSet();
            this.refs.add(ref);
            this.values = defaultFactory.createSet();
        }

        RefSet(ISet refs) {
            this.values = new BitSetFactory().createSet();
            this.refs = refs.clone();
        }

        void addRef(ISet refsToAdd) {
            this.refs.addAll(refsToAdd);
        }
        @Override
        public int compareTo(Object o) {
                int card1 = values.cardinality();
                int card2 = ((RefSet)o).values.cardinality();
                if (card1 < card2) {
                    return 1;
                }
                if (card1 == card2) {
                    return 0;
                }
                return -1;
        }
        
    }    
}
