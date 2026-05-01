/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.algo;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.util.Chrono;

/**
 * The Interface ClosureStrategy.
 *
 * @author agutierr
 */
public interface ClosureStrategy {

    /**
     * Closure.
     *
     * @param fermeture the closure
     * @param attrSet the attr set
     * @param lastAttrSet the last attr set
     * @param lastExtent the last extent
     * @return the i set
     */
    ISet closure(ISet fermeture, ISet attrSet,ISet lastAttrSet,ISet lastExtent);
    
    /**
     * Inits the.
     *
     * @param chrono the chrono
     */
    void init(Chrono chrono);
    
    /**
     * Notify.
     *
     * @param implication the implication
     */
    void notify(Implication implication);
    
    /**
     * Name.
     *
     * @return the string
     */
    String name();
    
    /**
     * Threshold.
     *
     * @return the int
     */
    int threshold();
    
    /**
     * Sets the context.
     *
     * @param ctx the new context
     */
    void setContext(IBinaryContext ctx);

	/**
	 * Shutdown.
	 */
	void shutdown();
}
