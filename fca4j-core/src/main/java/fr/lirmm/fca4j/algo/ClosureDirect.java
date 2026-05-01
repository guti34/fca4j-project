/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.algo;

import java.util.Iterator;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

/**
 * The Class ClosureDirect.
 *
 * @author agutierr
 */
public class ClosureDirect implements ClosureStrategy {

    protected IBinaryContext matrix;
    protected ISetFactory factory;

    /**
     * Instantiates a new closure direct.
     *
     * @param matrix the matrix
     */
    public ClosureDirect(IBinaryContext matrix) {
        this.matrix = matrix;
        this.factory = matrix.getFactory();
    }

 /**
  * Closure.
  *
  * @param fermeture the fermeture
  * @param attrSet the attr set
  * @param lastAttrSet the last attr set
  * @param lastExtent the last extent
  * @return the i set
  */
 @Override
    public ISet closure(ISet fermeture, ISet attrSet,ISet lastAttrSet,ISet lastExtent) {
        ISet extent = computeExtent(attrSet);
        fermeture.addAll(attrSet);
        ISet intent = computeIntent(extent);
        fermeture.addAll(intent);
        return extent;
    }
   
 /**
  * Compute extent.
  *
  * @param attributes the attributes
  * @return the i set
  */
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
 
 /**
  * Compute extent 2.
  *
  * @param attributes the attributes
  * @return the i set
  */
 public ISet computeExtent2(ISet attributes) {
     ISet extent = factory.createSet(matrix.getObjectCount());
     extent.fill(matrix.getObjectCount());
     for (Iterator<Integer> it = attributes.iterator(); it.hasNext();) {
         extent.retainAll(matrix.getExtent(it.next()));
     }
     return extent;
 }

/**
 * Compute intent 2.
 *
 * @param extent the extent
 * @return the i set
 */
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
 
 /**
  * Compute intent 3.
  *
  * @param extent the extent
  * @return the i set
  */
 public ISet computeIntent3(ISet extent) {
     ISet intent = factory.createSet(matrix.getAttributeCount());
     for(int numattr=0;numattr<matrix.getAttributeCount();numattr++)
     {
         if(matrix.getExtent(numattr).containsAll(extent))
             intent.add(numattr);
     }            
      return intent;
 }

 
    /**
     * Compute intent.
     *
     * @param extent the extent
     * @return the i set
     */
    public ISet computeIntent(ISet extent) {
        ISet intent = factory.createSet(matrix.getAttributeCount());
        if(extent.cardinality()<matrix.getAttributeCount()){
        intent.fill(matrix.getAttributeCount());
        for (Iterator<Integer> it = extent.iterator(); it.hasNext();) {
        	int obj=it.next();
        	ISet intentOfObj=matrix.getIntent(obj);
            intent.retainAll(intentOfObj);
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


    /**
     * Inits the.
     *
     * @param chrono the chrono
     */
    @Override
    public void init(Chrono chrono) {
    }

    /**
     * Name.
     *
     * @return the string
     */
    @Override
    public String name() {
        return "Direct";
    }

    /**
     * Notify.
     *
     * @param implication the implication
     */
    @Override
    public void notify(Implication implication) {
    }

	/**
	 * Threshold.
	 *
	 * @return the int
	 */
	@Override
	public int threshold() {
		return 0;
	}

	/**
	 * Sets the context.
	 *
	 * @param ctx the new context
	 */
	@Override
	public void setContext(IBinaryContext ctx) {
		matrix=ctx;
		
	}

	/**
	 * Shutdown.
	 */
	@Override
	public void shutdown() {
		
	}
}
