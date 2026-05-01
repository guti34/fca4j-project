/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.algo;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

/**
 * The Class ClosureDirectStream.
 *
 * @author agutierr
 */
public class ClosureDirectStream implements ClosureStrategy {

    protected IBinaryContext matrix;
    protected ISetFactory factory;
    protected boolean parallel;

    /**
     * Instantiates a new closure direct stream.
     *
     * @param matrix the matrix
     * @param parallel the parallel
     */
    public ClosureDirectStream(IBinaryContext matrix, boolean parallel) {
        this.matrix = matrix;
        this.factory = matrix.getFactory();
        this.parallel = parallel;
    }

    /**
     * Closure.
     *
     * @param fermeture the fermeture
     * @param attrSet the attr set
     * @param lastAttrSet the last attr set
     * @param lastExtent the last extent
     * @return the i set
     */
    @Override
    public ISet closure(ISet fermeture, ISet attrSet,ISet lastAttrSet,ISet lastExtent) {
        ISet extent = computeExtent(attrSet);
        fermeture.addAll(attrSet);
        ISet intent = computeIntent(extent);
        fermeture.addAll(intent);
        return extent;
    }

    /**
     * Compute intent.
     *
     * @param objects the objects
     * @return the i set
     */
    public ISet computeIntent(ISet objects) {
        ISet intent = factory.createSet(matrix.getAttributeCount());
        if (parallel) {
            Stream.iterate(0, n -> n + 1)
                    .parallel()
                    .limit(matrix.getAttributeCount())
                    .filter(numattr -> matrix.getExtent(numattr).containsAll(objects))
                    .collect(Collectors.toList())
                    .forEach(attr -> intent.add(attr));
        } else {
            Stream.iterate(0, n -> n + 1)
                    .limit(matrix.getAttributeCount())
                    .filter(numattr -> matrix.getExtent(numattr).containsAll(objects))
                    .collect(Collectors.toList())
                    .forEach(attr -> intent.add(attr));
        }

        return intent;
    }

    /**
     * Compute extent.
     *
     * @param attributes the attributes
     * @return the i set
     */
    public ISet computeExtent(ISet attributes) {
        ISet extent = factory.createSet(matrix.getObjectCount());
        if (parallel) {
            Stream.iterate(0, n -> n + 1)
                    .limit(matrix.getObjectCount())
                    .parallel()
                    .filter(numobj -> matrix.getIntent(numobj).containsAll(attributes))
                    .collect(Collectors.toList()).
                    forEach(numobj -> extent.add(numobj));
        } else {
            Stream.iterate(0, n -> n + 1)
                    .limit(matrix.getObjectCount())
                    .filter(numobj -> matrix.getIntent(numobj).containsAll(attributes))
                    .collect(Collectors.toList()).
                    forEach(numobj -> extent.add(numobj));
        }

        return extent;

    }

    /**
     * Inits the.
     *
     * @param chrono the chrono
     */
    @Override
    public void init(Chrono chrono) {
    }

    /**
     * Name.
     *
     * @return the string
     */
    @Override
    public String name() {
        return "Direct "+(parallel?"parallel":"mono thread");
    }

    /**
     * Notify.
     *
     * @param implication the implication
     */
    @Override
    public void notify(Implication implication) {
    }
	
	/**
	 * Threshold.
	 *
	 * @return the int
	 */
	@Override
	public int threshold() {
		return 0;
	}
	
	/**
	 * Sets the context.
	 *
	 * @param ctx the new context
	 */
	@Override
	public void setContext(IBinaryContext ctx) {
		matrix=ctx;
		
	}
	
	/**
	 * Shutdown.
	 */
	@Override
	public void shutdown() {
		
	}
}
