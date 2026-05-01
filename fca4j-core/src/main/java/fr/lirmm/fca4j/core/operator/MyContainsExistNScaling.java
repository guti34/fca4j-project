/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.core.operator;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;


/**
 * The Class MyContainsExistNScaling.
 *
 * @author agutierr
 */
public class MyContainsExistNScaling extends AbstractScalingOperator{
	private float x;
	
	/**
	 * Instantiates a new my contains exist N scaling.
	 *
	 * @param parameter the parameter
	 */
	public MyContainsExistNScaling(float parameter) {
		super();
		x=parameter;
	}

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
		if (c.isEmpty()||context.getIntent(e).isEmpty())
			return false;
		int maxLinks=c.cardinality();
		int threshold=(int)((x*maxLinks)/100);
		return context.getIntent(e).newIntersect(c).cardinality()>=threshold;
    }

    /**
     * Gets the name.
     *
     * @return the name
     */
    @Override
    public String getName() {
    	if(Math.ceil(x)==x)
    		return "existContainsN"+(int)x;
        return "existContainsN"+x;
    }
    
}
