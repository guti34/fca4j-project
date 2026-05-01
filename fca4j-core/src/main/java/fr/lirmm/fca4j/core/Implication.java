/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.core;

import fr.lirmm.fca4j.iset.ISet;

/**
 * The Class Implication.
 *
 * @author agutierr
 */
public class Implication  implements Cloneable {

	ISet premise;
	ISet conclusion;
	ISet support;
	int supportSize;

	/**
	 * Instantiates a new implication.
	 *
	 * @param premise    the premise
	 * @param conclusion the conclusion
	 * @param support    the support
	 */
	public Implication(ISet premise, ISet conclusion, ISet support) {
		this.premise = premise;
		this.conclusion = conclusion.newDifference(premise);
		this.support = support;
		supportSize=support.cardinality();
	}
	/**
	 * Instantiates a new implication.
	 *
	 * @param premise    the premise
	 * @param conclusion the conclusion
	 * @param supportSize    the support size
	 */
	public Implication(ISet premise, ISet conclusion, int supportSize) {
		this.premise = premise;
		this.conclusion = conclusion.newDifference(premise);
		this.support = null;
		this.supportSize=supportSize;
	}

	/**
	 * Gets the support.
	 *
	 * @return the support
	 */
	public ISet getSupport() {
		return support;
	}
	public int getSupportSize() {
		if(support!=null) return support.cardinality();
		else return supportSize;
	}

	/**
	 * Sets the support.
	 *
	 * @param support the new support
	 */
	public void setSupport(ISet support) {
		this.support = support;
		this.supportSize=this.support.cardinality();
	}

	/**
	 * Gets the premise.
	 *
	 * @return the premise
	 */
	public ISet getPremise() {
		return premise;
	}

	/**
	 * Gets the conclusion.
	 *
	 * @return the conclusion
	 */
	public ISet getConclusion() {
		return conclusion;
	}

	public boolean isSubRuleOf(Implication other) {
        	return  
        			other.getPremise().containsAll(getPremise()) 
        			&& getConclusion().containsAll(other.getConclusion());
        	
        }

	/**
	 * To string.
	 *
	 * @return the string
	 */
	@Override
	public String toString() {
		int s=(support==null)?supportSize:support.cardinality();
		return String.format("<%d> %s => %s", s, premise, conclusion);
	}
    /**
    *
    * @return a clone
    */
   public Implication clone() {
	   if(support==null)
		   return new Implication(premise.clone(), conclusion.clone(), supportSize);
	   else return new Implication(premise.clone(), conclusion.clone(), support.clone());
	   
   }
   @Override
   public int hashCode() {
	   return String.format("%s => %s", premise, conclusion).hashCode();
   }
   @Override
   public boolean equals(Object other) {
	   return hashCode()==other.hashCode();
   }
}
