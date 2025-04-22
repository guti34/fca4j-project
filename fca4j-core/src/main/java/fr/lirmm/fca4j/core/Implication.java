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

}
