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

import fr.lirmm.fca4j.core.BinaryContext;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

public class FastReduction implements AbstractAlgo<IBinaryContext> {

	private IBinaryContext context = null;
	private IBinaryContext result = null;
	private ISetFactory factory;
	private Chrono chrono = null; // eventually a chrono to store execution time

	public FastReduction(IBinaryContext binCtx, Chrono chrono) {
		super();
		this.context = binCtx;
		this.factory = binCtx.getFactory();
		this.chrono = chrono;
	}

	public FastReduction(IBinaryContext binCtx) {
		this(binCtx, null);
	}

	@Override
	public void run() {
		if (context.getAttributeCount() == 0) {
			result = context;
		} else {
			ArrayList<Integer> irreductibleAttrs = new ArrayList<>();
			for (int numattr = 0; numattr < context.getAttributeCount(); numattr++) {
				if (isIrreductible(context, numattr)) {
					irreductibleAttrs.add(numattr);
				}
			}
			IBinaryContext reducedContext = new BinaryContext(context.getObjectCount(), irreductibleAttrs.size(),
					"reduction of " + context.getName(), factory);
			for (int numattr : irreductibleAttrs) {
				int a = reducedContext.addAttributeName(context.getAttributeName(numattr));
				reducedContext.setExtent(a, context.getExtent(numattr));
			}
			reducedContext = reducedContext.transpose();
			irreductibleAttrs.clear();
			for (int numattr = 0; numattr < context.getAttributeCount(); numattr++) {
				if (isIrreductible(reducedContext, numattr)) {
					irreductibleAttrs.add(numattr);
				}
			}
			IBinaryContext reducedContext2 = new BinaryContext(reducedContext.getObjectCount(), 0,
					"reduction of " + context.getName(), factory);
			for (int numattr : irreductibleAttrs) {
				int a = reducedContext.addAttributeName(context.getAttributeName(numattr));
				reducedContext.setExtent(a, context.getExtent(numattr));
			}
			result = reducedContext2.transpose();
		}
	}

	public static ISet computeIrreductibleIntent(IBinaryContext myContext) {
		ISet attrs = myContext.getFactory().createSet();
		for (int numattr = 0; numattr < myContext.getAttributeCount(); numattr++) {
			if (isIrreductible(myContext, numattr)) {
				attrs.add(numattr);
			}
		}
		return attrs;
	}

	public static ISet computeIrreductibleExtent(IBinaryContext myContext) {
		IBinaryContext context2 = ((IBinaryContext) myContext.clone()).transpose();
		return computeIrreductibleIntent(context2);
	}

	public static List<ISet> computeIrreductibleExtent4notClarifiedContext(IBinaryContext myContext) {
		IBinaryContext context2 = ((IBinaryContext) myContext.clone()).transpose();
		return computeIrreductibleIntent4notClarifiedContext(context2);
	}

	public static List<ISet> computeIrreductibleIntent4notClarifiedContext(IBinaryContext myContext) {
		Clarification algoClarification = new Clarification(myContext, myContext.getName(), true, true,true);
		List<ISet> classes = algoClarification.getAttributesByEquivClasses(myContext);
		/*
		 * HashMap<Integer,Integer> representant=new HashMap<>();
		 * for(Iterator<IMySet> it=res.iterator();it.hasNext();) { IMySet
		 * equivClass=it.next(); if(equivClass.cardinality()>1) {
		 * Iterator<Integer> it2=equivClass.iterator(); int rep=it2.next();
		 * while(it2.hasNext()) representant.put(it2.next(), rep); } }
		 */

		ArrayList<ISet> res = new ArrayList<>();
		for (ISet classOfAttr : classes) {
			if (isIrreductible(myContext, classOfAttr.iterator().next(), classOfAttr)) {
				res.add(classOfAttr);
			}
		}
		return res;
	}

	protected static ISet fermeture(IBinaryContext myContext, int numAttr) {
		ISet fermeture = myContext.getFactory().createSet();
		ISet extent = myContext.getExtent(numAttr);
		Iterator<Integer> iterator = extent.iterator();
		if (iterator.hasNext()) {
			fermeture.addAll(myContext.getIntent(iterator.next()));
		}
		while (!fermeture.isEmpty() && iterator.hasNext()) {
			fermeture = fermeture.newIntersect(myContext.getIntent(iterator.next()));
		}
		return fermeture;
	}

	protected static boolean isIrreductible(IBinaryContext myContext, int numAttr) {
		return isIrreductible(myContext, numAttr, null);
	}

	protected static boolean isIrreductible(IBinaryContext myContext, int numAttr, ISet attr2ignore) {
		ISet fermeture = fermeture(myContext, numAttr);
		fermeture.remove(numAttr);
		if (attr2ignore != null) {
			fermeture.removeAll(attr2ignore);
			// fermeture = fermeture.newDifference(attr2ignore);
		}
		if (fermeture.isEmpty() && !myContext.getExtent(numAttr).isEmpty()) {
			return true;
		}
		Iterator<Integer> iterator = fermeture.iterator();
		ISet res = myContext.getFactory().createSet();
		if (iterator.hasNext()) {
			int otherAttr = iterator.next();
			res.addAll(myContext.getExtent(otherAttr));
			res.removeAll(myContext.getExtent(numAttr));
			// res = res.newDifference(myContext.getExtent(numAttr));
		}
		while (!res.isEmpty() && iterator.hasNext()) {
			res = res.newIntersect(myContext.getExtent(iterator.next()));
		}
		return !res.isEmpty();
	}

	@Override
	public String getDescription() {
		return "F_Reduction";
	}

	@Override
	public IBinaryContext getResult() {
		return result;
	}
}
