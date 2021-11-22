/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.lirmm.fca4j.algo;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.util.Chrono;

/**
 *
 * @author agutierr
 */
public interface ClosureStrategy {

    ISet closure(ISet fermeture, ISet attrSet,ISet lastAttrSet,ISet lastExtent);
    
    void init(Chrono chrono);
    
    void notify(Implication implication);
    
    String name();
    
    int threshold();
    
    void setContext(IBinaryContext ctx);

	void shutdown();
}
