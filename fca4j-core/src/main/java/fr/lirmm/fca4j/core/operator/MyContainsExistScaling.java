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
public class MyContainsExistScaling extends AbstractScalingOperator{

    @Override
    public boolean scale(int e, ISet c, IBinaryContext context) {
               return !c.isEmpty() && context.getIntent(e).containsAll(c);       
    }

    @Override
    public String getName() {
        return "existContains";
    }
    
}
