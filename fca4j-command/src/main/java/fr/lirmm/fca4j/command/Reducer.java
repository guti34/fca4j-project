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

public class Reducer extends MatrixTransform {
	boolean withAttr = false;	
	boolean withObj = false;
	boolean unclarified = false;

	public Reducer(ISetContext setContext) {
		super("reduce", "reduce formal context to irreductibles (--xo option), attributes (--xa option) or both",setContext);
	}

	@Override
	void createOptions() {
		options.addOption(Option.builder("xa").desc("reduce attributes").build());
		options.addOption(Option.builder("xo").desc("reduce objects").build());
		options.addOption(Option.builder("u")
				.desc("equivalent objects and/or attributes are grouped by classes. This option is useful in the case of unclarified contexts")
				.build());
		super.createOptions();
	}

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
