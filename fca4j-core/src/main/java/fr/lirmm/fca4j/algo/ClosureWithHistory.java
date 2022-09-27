/*
BSD 3-Clause License

Copyright (c) 2022 LIRMM
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

   * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
   * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
   * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package fr.lirmm.fca4j.algo;

import java.util.Iterator;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

/**
 * The Class ClosureWithHistory.
 *
 * @author agutierr
 */

public class ClosureWithHistory implements ClosureStrategy {

	protected IBinaryContext matrix;
	protected ISetFactory factory;
	
	/**
	 * Instantiates a new closure with history.
	 *
	 * @param matrix the matrix
	 */
	public ClosureWithHistory(IBinaryContext matrix) {
		this.matrix = matrix;
		this.factory = matrix.getFactory();
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


	/**
	 * Compute intent.
	 *
	 * @param extent the extent
	 * @return the i set
	 */
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
		return "WithHistory";
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
		matrix = ctx;

	}

	/**
	 * Shutdown.
	 */
	@Override
	public void shutdown() {

	}
}
