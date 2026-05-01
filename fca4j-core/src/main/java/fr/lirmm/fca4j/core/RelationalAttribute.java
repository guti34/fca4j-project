/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.core;

import fr.lirmm.fca4j.core.RCAFamily.RelationalContext;
import fr.lirmm.fca4j.core.operator.AbstractScalingOperator;

/**
 * The Class RelationalAttribute.
 */
public class RelationalAttribute {
    private final RelationalContext relationalContext;
    private final int concept;
    private final String name;

    /**
     * Instantiates a new relational attribute.
     *
     * @param concept the concept
     * @param rc the rc
     * @param name the name
     */
    public RelationalAttribute(int concept, RelationalContext rc,String name) {
        this.concept = concept;
        this.relationalContext = rc;
        this.name=name;
    }

    /**
     * Gets the relation name.
     *
     * @return the relation name
     */
    public String getRelationName() {
        return relationalContext.getRelationName();
    }
    
    /**
     * Gets the relation.
     *
     * @return the relation
     */
    public IBinaryContext getRelation() {
        return relationalContext.getContext();
    }

    /**
     * Gets the scaling.
     *
     * @return the scaling
     */
    public AbstractScalingOperator getScaling() {
        return relationalContext.getOperator();
    }

    /**
     * Gets the concept.
     *
     * @return the concept
     */
    public int getConcept() {
        return concept;
    }
    
    /**
     * Gets the name.
     *
     * @return the name
     */
    public String getName(){
        return name;
    }

}
