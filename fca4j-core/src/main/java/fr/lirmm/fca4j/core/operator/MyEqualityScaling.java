package fr.lirmm.fca4j.core.operator;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;

/**
 *
 * @author agutierr
 */
public class MyEqualityScaling extends AbstractScalingOperator{
	@Override
	public boolean scale(int e, ISet c, IBinaryContext context) {
                return c.cardinality()>0 && context.getIntent(e).equals(c);
		
	}

	@Override
	public String getName() {
		return "equality";
	}
    
}
