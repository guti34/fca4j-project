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
package fr.lirmm.fca4j.command;

import java.util.Iterator;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import fr.lirmm.fca4j.algo.FastReduction;
import fr.lirmm.fca4j.core.BinaryContext;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetContext;

/**
 * The Class Reducer.
 */
public class Reducer extends MatrixTransform {
	
	/** The with attributes. */
	boolean withAttr = false;	
	
	/** The with objects. */
	boolean withObj = false;
	
	/** if context is not clarified. */
	boolean unclarified = false;

	/**
	 * Instantiates a new reducer command.
	 *
	 * @param setContext the set context
	 */
	public Reducer(ISetContext setContext) {
		super("reduce", "reduce formal context to irreductibles (--xo option), attributes (--xa option) or both",setContext);
	}

	/**
	 * Creates the options.
	 */
	@Override
	void createOptions() {
		options.addOption(Option.builder("xa").desc("reduce attributes").build());
		options.addOption(Option.builder("xo").desc("reduce objects").build());
		options.addOption(Option.builder("u")
				.desc("equivalent objects and/or attributes are grouped by classes. This option is useful in the case of unclarified contexts")
				.build());
		super.createOptions();
	}

	/**
	 * Check options.
	 *
	 * @param line the command line
	 * @throws Exception the exception
	 */
	@Override
	public void checkOptions(CommandLine line) throws Exception {
		super.checkOptions(line);
		withAttr = line.hasOption("xa");
		withObj = line.hasOption("xo");
		if (!withAttr && !withObj) {
			throw new Exception("option -xo and/or -xa must be specified for reduction");
		}
		unclarified = line.hasOption("u");
	}

	/**
	 * Transform.
	 *
	 * @return the binary context
	 * @throws Exception the exception
	 */
	@Override
	protected IBinaryContext transform() throws Exception {
		IBinaryContext newContext = getContext();
		int nbAttr=newContext.getAttributeCount();
		int nbObj=newContext.getObjectCount();
		if (withAttr)
			newContext = reduceAttr(getContext());
		if (withObj)
			newContext = reduceObj(newContext);
		if(verbose){
			if(nbAttr!=newContext.getAttributeCount())
				System.out.println("attributes: "+nbAttr+" -> "+newContext.getAttributeCount());
			if(nbObj!=newContext.getObjectCount())
				System.out.println("objects: "+nbObj+" -> "+newContext.getObjectCount());
		}
		return newContext;
	}

	/**
	 * Reduce objects.
	 *
	 * @param context the context
	 * @return the binary context
	 * @throws Exception the exception
	 */
	private IBinaryContext reduceObj(IBinaryContext context) throws Exception {
		IBinaryContext reducedContext = new BinaryContext(0, context.getAttributeCount(), context.getName(), factory);
		for (int numattr = 0; numattr < context.getAttributeCount(); numattr++) {
			reducedContext.addAttributeName(context.getAttributeName(numattr));
		}
		ISet irr;
		if (unclarified) {
			List<ISet> irrObjs;
			irrObjs = FastReduction.computeIrreductibleExtent4notClarifiedContext(context);
			irr = factory.createSet();
			for (ISet classObj : irrObjs) {
				irr.add(classObj.iterator().next());
			}
		} else
			irr = FastReduction.computeIrreductibleExtent(context);

		for (Iterator<Integer> it = irr.iterator(); it.hasNext();) {
			int numobj = it.next();
			reducedContext.addObject(context.getObjectName(numobj), context.getIntent(numobj));
		}
		return reducedContext;
	}

	/**
	 * Reduce attributes.
	 *
	 * @param context the context
	 * @return the binary context
	 * @throws Exception the exception
	 */
	protected IBinaryContext reduceAttr(IBinaryContext context) throws Exception {
		IBinaryContext reducedContext = new BinaryContext(context.getObjectCount(), 0, context.getName(), factory);
		for (int numobj = 0; numobj < context.getObjectCount(); numobj++) {
			reducedContext.addObjectName(context.getObjectName(numobj));
		}
		ISet irr;
		if (unclarified) {
			List<ISet> irrObjs;
			irrObjs = FastReduction.computeIrreductibleIntent4notClarifiedContext(context);
			irr = factory.createSet();
			for (ISet classObj : irrObjs) {
				irr.add(classObj.iterator().next());
			}
		} else
			irr = FastReduction.computeIrreductibleIntent(context);
		for (Iterator<Integer> it = irr.iterator(); it.hasNext();) {
			int numattr = it.next();
			reducedContext.addAttribute(context.getAttributeName(numattr), context.getExtent(numattr));
		}
		return reducedContext;
	}
}
