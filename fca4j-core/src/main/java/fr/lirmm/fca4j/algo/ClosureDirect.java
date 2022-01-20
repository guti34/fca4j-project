/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.lirmm.fca4j.algo;

import java.util.Iterator;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

/**
 *
 * @author agutierr
 */
public class ClosureDirect implements ClosureStrategy {

    protected IBinaryContext matrix;
    protected ISetFactory factory;

    public ClosureDirect(IBinaryContext matrix) {
        this.matrix = matrix;
        this.factory = matrix.getFactory();
    }

 @Override
    public ISet closure(ISet fermeture, ISet attrSet,ISet lastAttrSet,ISet lastExtent) {
        ISet extent = computeExtent(attrSet);
        fermeture.addAll(attrSet);
        ISet intent = computeIntent(extent);
        fermeture.addAll(intent);
        return extent;
    }
   
 public ISet computeExtent(ISet attributes) {
     ISet extent = factory.createSet(matrix.getObjectCount());
     if(matrix.getAttributeCount()<matrix.getObjectCount())
     {
     extent.fill(matrix.getObjectCount());
     for (Iterator<Integer> it = attributes.iterator(); it.hasNext();) {
         extent.retainAll(matrix.getExtent(it.next()));
     }
     }
     else{
         for(int numobj=0;numobj<matrix.getObjectCount();numobj++)
         {
             if(matrix.getIntent(numobj).containsAll(attributes))
                 extent.add(numobj);
         }
     }
     return extent;
 }
 public ISet computeExtent2(ISet attributes) {
     ISet extent = factory.createSet(matrix.getObjectCount());
     extent.fill(matrix.getObjectCount());
     for (Iterator<Integer> it = attributes.iterator(); it.hasNext();) {
         extent.retainAll(matrix.getExtent(it.next()));
     }
     return extent;
 }
public ISet computeIntent2(ISet extent) {
     ISet intent = factory.createSet(matrix.getAttributeCount());
     if(!extent.isEmpty()) {
     intent.fill(matrix.getAttributeCount());
     for (Iterator<Integer> it = extent.iterator(); it.hasNext();) {
         intent.retainAll(matrix.getIntent(it.next()));
     }                
     }
      return intent;
 }
 public ISet computeIntent3(ISet extent) {
     ISet intent = factory.createSet(matrix.getAttributeCount());
     for(int numattr=0;numattr<matrix.getAttributeCount();numattr++)
     {
         if(matrix.getExtent(numattr).containsAll(extent))
             intent.add(numattr);
     }            
      return intent;
 }

 
    public ISet computeIntent(ISet extent) {
        ISet intent = factory.createSet(matrix.getAttributeCount());
        if(extent.cardinality()<matrix.getAttributeCount()){
        intent.fill(matrix.getAttributeCount());
        for (Iterator<Integer> it = extent.iterator(); it.hasNext();) {
            intent.retainAll(matrix.getIntent(it.next()));
        }                        
        }
        else{
            for(int numattr=0;numattr<matrix.getAttributeCount();numattr++)
            {
                if(matrix.getExtent(numattr).containsAll(extent))
                    intent.add(numattr);
            }            
        }
        return intent;
    }


    @Override
    public void init(Chrono chrono) {
    }

    @Override
    public String name() {
        return "Direct";
    }

    @Override
    public void notify(Implication implication) {
    }

	@Override
	public int threshold() {
		return 0;
	}

	@Override
	public void setContext(IBinaryContext ctx) {
		matrix=ctx;
		
	}

	@Override
	public void shutdown() {
		
	}
}
