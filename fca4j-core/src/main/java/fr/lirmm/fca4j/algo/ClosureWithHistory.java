/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.lirmm.fca4j.algo;

import java.util.Iterator;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

/**
 *
 * @author agutierr
 */

public class ClosureWithHistory implements ClosureStrategy {

	protected IBinaryContext matrix;
	protected ISetFactory factory;
	
	public ClosureWithHistory(IBinaryContext matrix) {
		this.matrix = matrix;
		this.factory = matrix.getFactory();
	}

	@Override
	public ISet closure(ISet fermeture, ISet attrSet,ISet lastAttrSet,ISet lastExtent) {
		ISet extent;
		if (lastAttrSet == null ) {
			extent = factory.createSet(matrix.getObjectCount());
			extent.fill(matrix.getObjectCount());
		} else {
//			assert attrSet.containsAll(lastAttrSet);
			extent = lastExtent.clone();
			ISet diff_attr = attrSet.newDifference(lastAttrSet);
			for (Iterator<Integer> it = diff_attr.iterator(); it.hasNext();) {
				ISet ext2=matrix.getExtent(it.next());
				extent.retainAll(ext2);
			}
		}
		fermeture.addAll(attrSet);
		ISet intent;
		intent = computeIntent(extent);
		fermeture.addAll(intent);
		return extent;
	}


	public ISet computeIntent(ISet extent) {
		ISet intent = factory.createSet(matrix.getAttributeCount());
		if (extent.cardinality() < matrix.getAttributeCount()) {
			intent.fill(matrix.getAttributeCount());
			for (Iterator<Integer> it = extent.iterator(); it.hasNext();) {
				intent.retainAll(matrix.getIntent(it.next()));
			}
		} else {
			for (int numattr = 0; numattr < matrix.getAttributeCount(); numattr++) {
				if (matrix.getExtent(numattr).containsAll(extent))
					intent.add(numattr);
			}
		}
		return intent;
	}

	@Override
	public void init(Chrono chrono) {
	}

	@Override
	public String name() {
		return "WithHistory";
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
		matrix = ctx;

	}

	@Override
	public void shutdown() {

	}
}
