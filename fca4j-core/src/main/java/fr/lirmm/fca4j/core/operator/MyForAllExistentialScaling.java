package fr.lirmm.fca4j.core.operator;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;


public class MyForAllExistentialScaling extends AbstractScalingOperator{
	
	@Override
	public boolean scale(int e, ISet c, IBinaryContext context) {
		if (c.isEmpty())
			return false;
		ISet inter=context.getIntent(e).newIntersect(c);
		return inter.equals(context.getIntent(e));
	}

	@Override
	public String getName() {
		return "existForall";
	}
    
}
