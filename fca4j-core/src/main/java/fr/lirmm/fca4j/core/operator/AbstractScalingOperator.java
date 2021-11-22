/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.lirmm.fca4j.core.operator;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;


public abstract class AbstractScalingOperator {
	
	public abstract boolean scale(int i, ISet cExtent, IBinaryContext context);
	public abstract String getName();
	public boolean hasParameter() {return false;}
	public void setParameter(float param){}
    
}
