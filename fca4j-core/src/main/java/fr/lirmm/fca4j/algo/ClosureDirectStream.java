/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
 *
 * @author agutierr
 */
public class ClosureDirectStream implements ClosureStrategy {

    protected IBinaryContext matrix;
    protected ISetFactory factory;
    protected boolean parallel;

    public ClosureDirectStream(IBinaryContext matrix, boolean parallel) {
        this.matrix = matrix;
        this.factory = matrix.getFactory();
        this.parallel = parallel;
    }

    @Override
    public ISet closure(ISet fermeture, ISet attrSet,ISet lastAttrSet,ISet lastExtent) {
        ISet extent = computeExtent(attrSet);
        fermeture.addAll(attrSet);
        ISet intent = computeIntent(extent);
        fermeture.addAll(intent);
        return extent;
    }

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

    @Override
    public void init(Chrono chrono) {
    }

    @Override
    public String name() {
        return "Direct "+(parallel?"parallel":"mono thread");
    }

    @Override
    public void notify(Implication implication) {
    }
	@Override
	public int threshold() {
		return 0;
	}
	@Override
	public void setContext(IBinaryContext ctx) {
		matrix=ctx;
		
	}
	@Override
	public void shutdown() {
		
	}
}
