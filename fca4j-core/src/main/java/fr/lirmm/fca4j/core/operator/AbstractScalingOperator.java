/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.core.operator;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;


/**
 * The Class AbstractScalingOperator.
 */
public abstract class AbstractScalingOperator {
	
	/**
	 * Scale.
	 *
	 * @param i the i
	 * @param cExtent the c extent
	 * @param context the context
	 * @return true, if successful
	 */
	public abstract boolean scale(int i, ISet cExtent, IBinaryContext context);
	
	/**
	 * Gets the name.
	 *
	 * @return the name
	 */
	public abstract String getName();
	
	/**
	 * Checks for parameter.
	 *
	 * @return true, if successful
	 */
	public boolean hasParameter() {return false;}
	
	/**
	 * Sets the parameter.
	 *
	 * @param param the new parameter
	 */
	public void setParameter(float param){}
    
}
