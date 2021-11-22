package fr.lirmm.fca4j.core.operator;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;


/**
 *
 * @author agutierr
 */
public class MyExistentialScaling extends AbstractScalingOperator{
	@Override
	public boolean scale(int e, ISet c, IBinaryContext context) {
		return ! context.getIntent(e).newIntersect(c).isEmpty();
		
	}

	@Override
	public String getName() {
		return "exist";
	}
    
}
