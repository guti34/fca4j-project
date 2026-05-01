/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.command;

import java.util.Iterator;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import fr.lirmm.fca4j.algo.Clarification;
import fr.lirmm.fca4j.core.BinaryContext;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetContext;

/**
 * The Class Clarifier.
 */
public class Clarifier extends MatrixTransform {
	
	/** include attributes. */
	boolean withAttr = false;
	
	/** include objects. */
	boolean withObj = false;

	/**
	 * Instantiates a new clarifier.
	 *
	 * @param setContext the set context
	 */
	public Clarifier(ISetContext setContext) {
		super("clarify", "eliminate any duplicated objets (--xo option), attributes (--xa option) or both",setContext);
	}

	/**
	 * Creates the options.
	 */
	@Override
	void createOptions() {
		options.addOption(Option.builder("xa").desc("clarify attributes").build());
		options.addOption(Option.builder("xo").desc("clarify objects").build());
		super.createOptions();
	}

	/**
	 * Check options.
	 *
	 * @param line the line
	 * @throws Exception the exception
	 */
	@Override
	public void checkOptions(CommandLine line) throws Exception {
		super.checkOptions(line);
		withAttr = line.hasOption("xa");
		withObj = line.hasOption("xo");
		if (!withAttr && !withObj) {
			throw new Exception("option -xo and/or -xa must be specified for clarification");
		}
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
			newContext = clarifyAttr(getContext());
		if (withObj)
			newContext = clarifyObj(newContext);
		if(verbose){
			if(nbAttr!=newContext.getAttributeCount())
				System.out.println("attributes: "+nbAttr+" -> "+newContext.getAttributeCount());
			if(nbObj!=newContext.getObjectCount())
				System.out.println("objects: "+nbObj+" -> "+newContext.getObjectCount());
		}
		return newContext;
	}

	/**
	 * Clarify obj.
	 *
	 * @param context the binary context
	 * @return the resulting binary context
	 * @throws Exception the exception
	 */
	private IBinaryContext clarifyObj(IBinaryContext context) throws Exception {
        List<ISet> equivObjs = Clarification.getObjectsByEquivClasses(context);
        IBinaryContext clarifiedContext = new BinaryContext(0, context.getAttributeCount(), context.getName(),factory);
        for (int numattr = 0; numattr < context.getAttributeCount(); numattr++) {
            clarifiedContext.addAttributeName(context.getAttributeName(numattr));
        }
        ISet equiv = factory.createSet();
        for (ISet classObj : equivObjs) {
            equiv.add(classObj.iterator().next());
        }
        for (Iterator<Integer> it = equiv.iterator(); it.hasNext();) {
            int numobj = it.next();
            clarifiedContext.addObject(context.getObjectName(numobj), context.getIntent(numobj));
        }
		return clarifiedContext;
	}

	/**
	 * Clarify attr.
	 *
	 * @param context the binary context
	 * @return the resulting binary context
	 * @throws Exception the exception
	 */
	protected IBinaryContext clarifyAttr(IBinaryContext context) throws Exception {
		List<ISet> equivAttrs = Clarification.getAttributesByEquivClasses(context);
		IBinaryContext clarifiedContext = new BinaryContext(context.getObjectCount(), 0, context.getName(), factory);
		for (int numobj = 0; numobj < context.getObjectCount(); numobj++) {
			clarifiedContext.addObjectName(context.getObjectName(numobj));
		}
		ISet equiv = factory.createSet();
		for (ISet classAttr : equivAttrs) {
			equiv.add(classAttr.iterator().next());
		}
		for (Iterator<Integer> it = equiv.iterator(); it.hasNext();) {
			int numattr = it.next();
			clarifiedContext.addAttribute(context.getAttributeName(numattr), context.getExtent(numattr));
		}
		return clarifiedContext;
	}
}
