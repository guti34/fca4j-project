/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.core.operator;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;


/**
 * The Class MyExistentialScaling.
 *
 * @author agutierr
 */
public class MyExistentialScaling extends AbstractScalingOperator{
	
	/**
	 * Scale.
	 *
	 * @param e the e
	 * @param c the c
	 * @param context the context
	 * @return true, if successful
	 */
	@Override
	public boolean scale(int e, ISet c, IBinaryContext context) {
		return context.getIntent(e).intersects(c);
		
	}

	/**
	 * Gets the name.
	 *
	 * @return the name
	 */
	@Override
	public String getName() {
		return "exist";
	}
    
}
