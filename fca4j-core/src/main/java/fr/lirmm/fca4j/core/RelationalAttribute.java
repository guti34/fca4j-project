package fr.lirmm.fca4j.core;

import fr.lirmm.fca4j.core.RCAFamily.RelationalContext;
import fr.lirmm.fca4j.core.operator.AbstractScalingOperator;

public class RelationalAttribute {
    private final RelationalContext relationalContext;
    private final int concept;
    private final String name;

    public RelationalAttribute(int concept, RelationalContext rc,String name) {
        this.concept = concept;
        this.relationalContext = rc;
        this.name=name;
    }

    public String getRelationName() {
        return relationalContext.getRelationName();
    }
    public IBinaryContext getRelation() {
        return relationalContext.getContext();
    }

    public AbstractScalingOperator getScaling() {
        return relationalContext.getOperator();
    }

    public int getConcept() {
        return concept;
    }
    public String getName(){
        return name;
    }

}
