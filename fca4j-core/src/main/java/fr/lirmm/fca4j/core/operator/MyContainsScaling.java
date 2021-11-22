package fr.lirmm.fca4j.core.operator;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;


public class MyContainsScaling extends AbstractScalingOperator{
	@Override
	public boolean scale(int e, ISet c, IBinaryContext context) {
               return context.getIntent(e).containsAll(c);
		
	}

	@Override
	public String getName() {
		return "contains";
	}
    
    
}
