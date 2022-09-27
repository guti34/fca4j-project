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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.util.Chrono;

/**
 * The Class LinCbOWithPruning.
 *
 * @author agutierr
 */
// compute duquenne guigues rules
public class LinCbOWithPruning extends AbstractLinCbo {

	int rules[];
	List<Integer> counts;
	Stack<Integer> pruningStack;

	/**
	 * Instantiates a new lin cb O with pruning.
	 *
	 * @param binCtx the bin ctx
	 * @param chrono the chrono
	 * @param computeIntExtStrategy the compute int ext strategy
	 * @param clarify the clarify
	 */
	public LinCbOWithPruning(IBinaryContext binCtx, Chrono chrono, ClosureStrategy computeIntExtStrategy,
			boolean clarify) {
		super(binCtx, chrono, computeIntExtStrategy, clarify);
	}

	/**
	 * Instantiates a new lin cb O with pruning.
	 *
	 * @param binCtx the bin ctx
	 */
	public LinCbOWithPruning(IBinaryContext binCtx) {
		this(binCtx, null, new ClosureDirect(binCtx), false);
	}

	/**
	 * Inits the.
	 */
	@Override
	protected void init() {
		implications = new ArrayList<>();
		counts = new ArrayList<>();
		defaultConclusion = null;
		rules = new int[matrix.getAttributeCount()];
		list = new ArrayList<>(matrix.getAttributeCount());
		pruningStack = new Stack<>();
	}

	/**
	 * Lin cb O.
	 *
	 * @throws InterruptedException the interrupted exception
	 */
	protected void _LinCbO() throws InterruptedException {
		for (int attr = 0; attr < matrix.getAttributeCount(); attr++) {
			list.add(new ArrayList(matrix.getObjectCount()));
			rules[attr] = -1;
		}
		_LinCbOStep(factory.createSet(matrix.getAttributeCount()), -1, factory.createSet(matrix.getAttributeCount()),
				counts,null,null);
	}

	/**
	 * Lin cb O step.
	 *
	 * @param B the b
	 * @param y the y
	 * @param Z the z
	 * @param prevCount the prev count
	 * @param lastAttrSet the last attr set
	 * @param lastExtent the last extent
	 * @return the int
	 * @throws InterruptedException the interrupted exception
	 */
	protected int _LinCbOStep(ISet B, int y, ISet Z, List<Integer> prevCount, ISet lastAttrSet,ISet lastExtent)
			throws InterruptedException {
		if (Thread.interrupted())
			throw new InterruptedException("interrupted by user");
		int stackSize = pruningStack.size();
		// the linclosure
		Triple<List<Integer>, ISet, Integer> retClosureRC = _LinClosureRC(B, y, Z, prevCount);
		List<Integer> count = retClosureRC.a;
		ISet Bo = retClosureRC.b;
		int fail;
		if (Bo == null) {
			return retClosureRC.c;
		}
		ISet Bclosure = factory.createSet(matrix.getAttributeCount());
		ISet support = closure(Bclosure, Bo, lastAttrSet,lastExtent);
//		System.out.println("closure"+Bo+"\t\t  =\t\t"+Bclosure+"\t supp:"+support);		
		if (!Bo.equals(Bclosure)) {
			addImplication(Bo, Bclosure, support);
			ISet Zp = Bclosure.newDifference(Bo);
			int minZp = min(Zp);
			if (minZp > y) {
				fail = _LinCbOStep(Bclosure, y, Zp, count,lastAttrSet,lastExtent);
			} else {
				fail = minZp;
			}
		} else {
			for (int i = matrix.getAttributeCount() - 1; i > y; i--) {
				if (!Bo.contains(i)) {
					ISet Bp = factory.clone(Bo);
					Bp.add(i);
					ISet Zp = factory.createSet(matrix.getAttributeCount());
					Zp.add(i);
					if (rules[i] == -1 || Bp.contains(rules[i])) {
						if (Bp.cardinality() == 1) {
							fail = _LinCbOStep(Bp, i, Zp, counts, Bclosure,support);
						} else {
							fail = _LinCbOStep(Bp, i, Zp, count, Bclosure,support);
						}
						if (fail >= 0 && fail < i) {
							rules[i] = fail;
							pruningStack.push(i);
						}
					}
				}
			}
			fail = -1;
		}
		while (pruningStack.size() > stackSize) {
			int val = pruningStack.pop();
			rules[val] = -1;
		}
		return fail;
	}

	/**
	 * Lin closure RC.
	 *
	 * @param B the b
	 * @param y the y
	 * @param Z the z
	 * @param prevCount the prev count
	 * @return the triple
	 */
	protected Triple<List<Integer>, ISet, Integer> _LinClosureRC(ISet B, int y, ISet Z, List<Integer> prevCount) {
		ISet D = factory.clone(B);
		if (defaultConclusion != null) {
			D.addAll(defaultConclusion);
		}
		for (int num_implication = prevCount.size(); num_implication < counts.size(); num_implication++) {
			Implication implication = implications.get(num_implication);
			prevCount.add(implication.getPremise().newDifference(B).cardinality());
		}
		List<Integer> count = new ArrayList<>(prevCount);
		int m;
		while ((m = min(Z)) < matrix.getAttributeCount()) {
			Z.remove(m);
			for (Iterator<Integer> it = list.get(m).iterator(); it.hasNext();) {
				int num_implication = it.next();
				Implication implication = implications.get(num_implication);
				int nb = count.get(num_implication);
				count.set(num_implication, nb - 1);
				if (nb == 1) {
					ISet diff = implication.getConclusion().newDifference(D);
					int minDiff = min(diff);
					if (minDiff < y) {
						return new Triple(null, null, minDiff);
					}
					D.addAll(diff);
					Z.addAll(diff);
				}
			}
			if (D.cardinality() == matrix.getAttributeCount()) {
				return new Triple(new ArrayList<Integer>(), D, -1);
			}
		}
		return new Triple(count, D, -1);
	}

	/**
	 * Adds the implication.
	 *
	 * @param premise the premise
	 * @param conclusion the conclusion
	 * @param support the support
	 * @return the int
	 */
	protected int addImplication(ISet premise, ISet conclusion, ISet support) {
		Implication newImplication = new Implication(premise, conclusion, support);
		int num_implication = implications.size();
		implications.add(newImplication);
		if (newImplication.getPremise().isEmpty()) {
			if (defaultConclusion == null) {
				defaultConclusion = factory.createSet(matrix.getAttributeCount());
			}
			defaultConclusion.addAll(newImplication.getConclusion());
		}
		counts.add(newImplication.getPremise().cardinality());
		for (Iterator<Integer> it = premise.iterator(); it.hasNext();) {
			int numattr = it.next();
			list.get(numattr).add(num_implication);
		}
		computeIntExt.notify(newImplication);
		System.out.println(newImplication);
		return num_implication;
	}

	/**
	 * Gets the description.
	 *
	 * @return the description
	 */
	@Override
	public String getDescription() {
		return "LinCbOWithPruning";
	}

	private class Triple<A, B, C> {

		A a;
		B b;
		C c;

		public Triple(A a, B b, C c) {
			this.a = a;
			this.b = b;
			this.c = c;
		}

	}
}
