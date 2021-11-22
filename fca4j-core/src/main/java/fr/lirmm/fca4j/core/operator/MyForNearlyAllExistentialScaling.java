package fr.lirmm.fca4j.core.operator;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;


public class MyForNearlyAllExistentialScaling extends AbstractScalingOperator{
	private float x;
	
	public MyForNearlyAllExistentialScaling(float parameter) {
		super();
		x=parameter;
	}

    @Override
    public boolean scale(int e, ISet c, IBinaryContext context) {
        ISet targetEntities=context.getIntent(e);
	int maxLinks=targetEntities.cardinality();
	//TODO check the validity of the rounding
	int threshold= new Float(x*maxLinks/100).intValue();
	if (maxLinks==0)
		return false;
	ISet inter=targetEntities.newIntersect(c);
		return inter.cardinality()<=threshold;		
    }

    @Override
    public String getName() {
    	if(Math.ceil(x)==x)
    		return "existForallN"+(int)x;
        return "existForallN"+x;
    }
    
    
}
