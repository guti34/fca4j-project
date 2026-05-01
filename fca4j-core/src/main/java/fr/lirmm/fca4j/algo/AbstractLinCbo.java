/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.Implication;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

/**
 * The Class AbstractLinCbo.
 *
 * @author agutierr
 */
public abstract class AbstractLinCbo implements AbstractAlgo<List<Implication>> {

	/** The matrix. */
	protected IBinaryContext matrix; // ressource de depart
	
	/** The factory. */
	protected ISetFactory factory;
	
	protected Chrono chrono = null; // eventually a chrono to store execution
									
									/** The implications. */
									// time
	protected ArrayList<Implication> implications;
	
	protected ISet defaultConclusion;
	
	protected List<List> list;
	
	protected ClosureStrategy computeIntExt;
	
	/** The clarify. */
	protected boolean clarify;

	/**
	 * Instantiates a new abstract lin cbo.
	 *
	 * @param binCtx the context
	 * @param chrono the chrono
	 * @param computeIntExt the closure strategy
	 * @param clarify clarify context before
	 */
	public AbstractLinCbo(IBinaryContext binCtx, Chrono chrono, ClosureStrategy computeIntExt, boolean clarify) {
		super();
		this.matrix = binCtx;
		this.factory = matrix.getFactory();
		this.chrono = chrono;
		this.computeIntExt = computeIntExt;
		this.clarify = clarify;
	}

	/**
	 * Lin cb O.
	 *
	 * @throws InterruptedException the interrupted exception
	 */
	protected abstract void _LinCbO() throws InterruptedException;

	/**
	 * Equals until 2.
	 *
	 * @param set1 the set 1
	 * @param set2 the set 2
	 * @param y the y
	 * @return true, if successful
	 */
	protected boolean equalsUntil2(ISet set1, ISet set2, int y) {
		for (int i = 0; i <= y; i++) {
			if (set1.contains(i) != set2.contains(i)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Equals until.
	 *
	 * @param set1 the set 1
	 * @param set2 the set 2
	 * @param y the y
	 * @return true, if successful
	 */
	protected boolean equalsUntil(ISet set1, ISet set2, int y) {
		Iterator<Integer> it1 = set1.iterator();
		Iterator<Integer> it2 = set2.iterator();
		while (true) {
			if (!it1.hasNext()) {
				return !it2.hasNext() || it2.next() > y;
			}
			if (!it2.hasNext()) {
				return it1.next() > y;
			}
			int i1 = it1.next();
			int i2 = it2.next();
			if (i1 != i2) {
				return i1 > y && i2 > y;
			}
		}
	}

	/**
	 * Min.
	 *
	 * @param set the set
	 * @return the int
	 */
	protected int min(ISet set) {
		if (set.isEmpty()) {
			return Integer.MAX_VALUE;
		} else {
			return set.first();
		}
	}

	/**
	 * Closure.
	 *
	 * @param fermeture the fermeture
	 * @param attrSet the attributes set
	 * @param lastAttrSet the last attributes set
	 * @param lastExtent the last extent
	 * @return the resulting set
	 */
	protected final ISet closure(ISet fermeture, ISet attrSet, ISet lastAttrSet, ISet lastExtent) {
		ISet ret = computeIntExt.closure(fermeture, attrSet, lastAttrSet, lastExtent);
		// System.out.println("closure "+attrSet+" = "+fermeture+"
		// support="+ret);
		return ret;
	}

	/**
	 * Display attributes.
	 *
	 * @param set the set
	 * @return the string
	 */
	protected String displayAttrs(ISet set) {
		StringBuilder sb = new StringBuilder();
		for (Iterator<Integer> it = set.iterator(); it.hasNext();) {
			if (sb.length() != 0) {
				sb.append(",");
			}
			sb.append(matrix.getAttributeName(it.next()));
		}
		return sb.toString();
	}

	/**
	 * Inits.
	 */
	abstract protected void init();

	/**
	 * Run.
	 */
	@Override
	public void run() {
		IBinaryContext clarifiedContext = null;
		IBinaryContext rememberContext = null;
		List<ISet> attrClasses = null;
		List<ISet> objClasses = null;
		if (clarify) {
			Clarification clarificateur = new Clarification(matrix, matrix.getName(), true, true, false);
			clarificateur.run();
			clarifiedContext = clarificateur.getResult();
			rememberContext = matrix;
			matrix = clarifiedContext;
			computeIntExt.setContext(clarifiedContext);
			attrClasses = clarificateur.getAttributeClasses();
			objClasses = clarificateur.getObjectClasses();
		}
		// algo init its environment
		init();
		// compute intent/extent strategy init its own if any
		computeIntExt.init(chrono);
		// execute algo
		try {
			_LinCbO();
			// if clarified implications have to be rewritten
			if (clarify) {
				ISet alwaysTrueAttrs = factory.createSet(rememberContext.getAttributeCount());
				int clarifiedAllwaysTrue=-1;
				ArrayList<Implication> newImplications = new ArrayList<>();
				ISet processedAttrs=factory.createSet(matrix.getAttributeCount());
				for (Implication implication : implications) {
				// support
					ISet support = factory.createSet(rememberContext.getObjectCount());
					for (Iterator<Integer> it = implication.getSupport().iterator(); it.hasNext();) {
						int clarifiedObjNum = it.next();
						support.addAll(objClasses.get(clarifiedObjNum));
					}

					// always true attribute are also processed
					if (implication.getPremise().isEmpty()) {
						clarifiedAllwaysTrue=implication.getConclusion().first();					
						alwaysTrueAttrs.addAll(attrClasses.get(clarifiedAllwaysTrue));
						Implication newImpl=new Implication(factory.createSet(rememberContext.getAttributeCount()), alwaysTrueAttrs, support);
						newImplications.add(newImpl);
						processedAttrs.add(clarifiedAllwaysTrue);
					}
					else{
						ISet premice = factory.createSet(rememberContext.getAttributeCount());
						ISet conclusion = factory.createSet(rememberContext.getAttributeCount());
						// build premise
						for (Iterator<Integer> it = implication.getPremise().iterator(); it.hasNext();) {
							int clarifiedAttrNum = it.next();
							ISet doublons = attrClasses.get(clarifiedAttrNum);
							premice.addAll(doublons);
						}
						// build conclusion
						for (Iterator<Integer> it = implication.getConclusion().iterator(); it.hasNext();) {
							int clarifiedAttrNum = it.next();
							conclusion.addAll(attrClasses.get(clarifiedAttrNum));
						}
						// identify implication type when premise contains only 1 attribute it is processed differently						
						ISet premice2=implication.getPremise().clone();
						if(clarifiedAllwaysTrue>=0)		premice2.remove(clarifiedAllwaysTrue);
						if (premice2.cardinality() == 1) {
							int clarifiedAttr=premice2.first();							
							for (Iterator<Integer> it = attrClasses.get(clarifiedAttr).iterator(); it.hasNext();) {
								int numattr = it.next();
								ISet premice3 = factory.createSet(matrix.getAttributeCount());
								premice3.add(numattr);
								premice3.addAll(alwaysTrueAttrs);
								ISet conclusion2=conclusion.newDifference(alwaysTrueAttrs);
								conclusion2.addAll(attrClasses.get(clarifiedAttr));
								conclusion2.remove(numattr);
								Implication impl = new Implication(premice3, conclusion2, support.clone());
								newImplications.add(impl);
							}	
							// store processed attributes to avoid equiv rules production
							processedAttrs.add(clarifiedAttr);
						}
						else{
							Implication newImpl=new Implication(premice, conclusion, support);
							newImplications.add(newImpl);
						}
					}
				}
				// equivalence rules for not processed doublons
				for (int clarifiedAttr=0;clarifiedAttr<attrClasses.size();clarifiedAttr++) {					
					ISet equivClass = attrClasses.get(clarifiedAttr);
					// when attribute is not processed above
					if (equivClass.cardinality() > 1 && !processedAttrs.contains(clarifiedAttr)) {
								for (Iterator<Integer> it2 = equivClass.iterator(); it2.hasNext();) {
									ISet premice = factory.createSet(matrix.getAttributeCount());
									int numattr = it2.next();
									premice.add(numattr);
									premice.addAll(alwaysTrueAttrs);
									Implication newImpl=new Implication(premice, equivClass.clone(), rememberContext.getExtent(numattr));
									newImplications.add(newImpl);
								}
							}
							
						}
				implications = newImplications;
			}
		} catch (InterruptedException e) {
			implications = null; //
		} finally {
			computeIntExt.shutdown();
			if (rememberContext != null)
				matrix = rememberContext;
		}
	
	}

	/**
	 * Gets the result.
	 *
	 * @return the list of all computed implications
	 */
	@Override
	public ArrayList<Implication> getResult() {
		return (implications);
	}
}
