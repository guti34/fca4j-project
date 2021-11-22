/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.lirmm.fca4j.core.operator;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;


/**
 *
 * @author agutierr
 */
public class MyContainsExistNScaling extends AbstractScalingOperator{
	private float x;
	
	public MyContainsExistNScaling(float parameter) {
		super();
		x=parameter;
	}

    @Override
    public boolean scale(int e, ISet c, IBinaryContext context) {
		int maxLinks=c.cardinality();
		int threshold=new Float((x*maxLinks)/100).intValue();
		if (maxLinks==0)
			return false;
		return context.getIntent(e).newIntersect(c).cardinality()<=threshold;
    }

    @Override
    public String getName() {
    	if(Math.ceil(x)==x)
    		return "existContainsN"+(int)x;
        return "existContainsN"+x;
    }
    
}
