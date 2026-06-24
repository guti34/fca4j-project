/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.algo;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.lirmm.fca4j.core.IConceptOrder;
import fr.lirmm.fca4j.core.CsrConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

// experimental: DO NOT USE

public class AOC_poset_Athena implements AbstractAlgo<IConceptOrder> {

	private final static int[] MARK=new int[] {-1,-1};
	protected IBinaryContext matrix; //ressource de depart
    protected IConceptOrder gsh = null; //ressource d'arrivee
    protected Chrono chrono = null; // eventually a chrono to store execution time 
    protected ISetFactory factory;

	public AOC_poset_Athena(IBinaryContext bc, Chrono chrono) {
	       super();
	        this.chrono = chrono;
	        matrix = bc;
	        factory = matrix.getFactory();
	}

	public AOC_poset_Athena(IBinaryContext bc) {
		this(bc,null);
	}

	public IConceptOrder computeGSH() throws Exception {
		if (chrono != null) {
			chrono.start("clarify");
		}
		Clarification clarification=new Clarification(matrix, "clarified", true, true, false);
		clarification.run();
		
		List<ISet> attrClasses = clarification.getAttributeClasses();
		List<ISet> objClasses = clarification.getObjectClasses();
		IBinaryContext clarified_matrix=clarification.getResult();
		if (chrono != null) {
			chrono.stop("clarify");
			chrono.start("concept");
		}
		gsh = new CsrConceptOrder("AOCposetWithAthena", clarified_matrix, getDescription());

		// create concept of attributes
		HashMap<Integer,Integer> conceptsA=new HashMap<>();
		for (int indexAttr = 0; indexAttr < clarified_matrix.getAttributeCount(); indexAttr++) {
			ISet reducedIntent=factory.createSet();
			reducedIntent.add(indexAttr);
			int current_concept = gsh.addConcept(
					factory.createSet(), // extent
					reducedIntent.clone(), // intent to compute
					factory.createSet(), // reduced extent to compute 
					reducedIntent); // reduced intent
			conceptsA.put(indexAttr,current_concept);
			}
		// compute
		Queue<int[]> result=computeWithCPU(clarified_matrix);
		// create attributes edges
		do {
			int[] edge=result.poll();
			if(edge==MARK) break;
			else 
				gsh.addPrecedenceConnection(conceptsA.get(edge[0]), conceptsA.get(edge[1]));			
		}while(true);
		// fusion concepts
		HashMap<Integer,Integer> conceptsO=new HashMap<>();
		do {
			int[] fusion=result.poll();
			if(fusion==MARK) break;
			else 
				conceptsO.put(fusion[0],conceptsA.get(fusion[1]));	
		}while(true);
		// create concepts of objects
		for (int indexObj = 0; indexObj < clarified_matrix.getObjectCount(); indexObj++) {
			if(conceptsO.get(indexObj)!=null) {
				gsh.getConceptReducedExtent(conceptsO.get(indexObj)).add(indexObj);
			}
			else {
				ISet reducedExtent=factory.createSet();
				reducedExtent.add(indexObj);
				int concept=gsh.addConcept(reducedExtent, factory.createSet(), reducedExtent.clone(), factory.createSet());
				conceptsO.put(indexObj, concept);
			}
		}
		// create object edges
		do {
			int[] edge=result.poll();
			if(edge==MARK) break;
			else 
				gsh.addPrecedenceConnection(conceptsO.get(edge[0]), conceptsO.get(edge[1]));			
		}while(true);
		// sew the two hierarchies
		do {
			int[] edge=result.poll();
			if(edge==MARK) break;
			else 
				gsh.addPrecedenceConnection(conceptsO.get(edge[0]),conceptsA.get(edge[1]) );			
		}while(true);
		
		if (chrono != null) {
			chrono.stop("concept");
			chrono.start("transitive reduction");
		}
//		gsh.reduce();
		if (chrono != null) {
			chrono.stop("transitive reduction");
			chrono.start("completion");
		}
		ISet minimals=gsh.getMinimals();
		
		for(Iterator<Integer> it=minimals.iterator();it.hasNext();) {
			int concept=it.next();
			gsh.getConceptExtent(concept).addAll(gsh.getConceptReducedExtent(concept));
			completeExtents(concept,objClasses);
		}
		ISet maximals=gsh.getMaximals();
		for(Iterator<Integer> it=maximals.iterator();it.hasNext();) {
			int concept=it.next();
			gsh.getConceptIntent(concept).addAll(gsh.getConceptReducedIntent(concept));
			completeIntents(concept,attrClasses);
		}
		if (chrono != null) {
			chrono.stop("completion");
			chrono.start("substitution");
		}
		
		// substitution to initial matrix
		gsh.substitution(matrix, attrClasses, objClasses);
		if (chrono != null) {
			chrono.stop("substitution");
		}
		for(String serie:chrono.getSerieNames())
			System.out.println(serie+":"+chrono.getResult(serie));
		return gsh;
	}
	private void completeExtents(int concept,List<ISet> objClasses) {
		for(Iterator<Integer> it=gsh.getUpperCoverIterator(concept);it.hasNext();) {
			int parent=it.next();
			gsh.getConceptExtent(parent).addAll(gsh.getConceptExtent(concept));
			completeExtents(parent,objClasses);
		}
	}
	private void completeIntents(int concept,List<ISet> attrClasses) {
		for(Iterator<Integer> it=gsh.getLowerCoverIterator(concept);it.hasNext();) {
			int child=it.next();
			gsh.getConceptIntent(child).addAll(gsh.getConceptIntent(concept));
			completeIntents(child,attrClasses);
		}
	}
	protected Queue<int[]> computeWithCPU(IBinaryContext ctx){
		ISet[] attributeHierarchyExtents=new ISet[ctx.getAttributeCount()];
		for(int numattr=0;numattr<ctx.getAttributeCount();numattr++)
		{
			attributeHierarchyExtents[numattr]=ctx.getExtent(numattr).clone();
		}
		LinkedList<int[]> result=new LinkedList<>();
		// build attributes hierarchy
		for(int numAttr1=ctx.getAttributeCount()-1;numAttr1>=0;numAttr1--)
		{
			boolean to_ignore[]=new boolean[ctx.getAttributeCount()];			
			for(int numAttr2=numAttr1-1;numAttr2>=0;numAttr2--)
			{
				if(!to_ignore[numAttr2]) {
					if(ctx.getExtent(numAttr2).containsAll(ctx.getExtent(numAttr1))) 
					{
						attributeHierarchyExtents[numAttr2].removeAll(ctx.getExtent(numAttr1));						
						for(int numAttr3=numAttr2-1;numAttr3>=0;numAttr3--)
						{
							if(!to_ignore[numAttr3] && ctx.getExtent(numAttr3).containsAll(ctx.getExtent(numAttr2)))
							{
								to_ignore[numAttr3]=true;
							}
						}
					}
					else to_ignore[numAttr2]=true;
				}
			}
				for(int attr=0;attr<numAttr1;attr++)
				{
					if(!to_ignore[attr]) result.add(new int[] {numAttr1,attr});
				}
		}
		// end of attribute graph
		// build sewing intents
		ISet[] sewingIntents=new ISet[ctx.getObjectCount()];
		for(int numobj=0;numobj<ctx.getObjectCount();numobj++)
		{
			sewingIntents[numobj]=factory.createSet();
		}
		for(int numattr=0;numattr<ctx.getAttributeCount();numattr++) {
			for(Iterator<Integer> it=attributeHierarchyExtents[numattr].iterator();it.hasNext();)
				sewingIntents[it.next()].add(numattr);
		}
		result.add(MARK);
		// detect concept fusion
		for(int numobj=0;numobj<ctx.getObjectCount();numobj++) {
			if(sewingIntents[numobj].cardinality()==1) {
				int numattr=sewingIntents[numobj].first();
				result.add(new int[] {numobj,numattr});
				sewingIntents[numobj].remove(numattr);
			}
		}
//		// end of fusion
		result.add(MARK);
//		// build object hierarchy
		ISet[] objectHierarchyIntents=new ISet[ctx.getObjectCount()];
		for (int numobj = 0; numobj < ctx.getObjectCount(); numobj++) {
			objectHierarchyIntents[numobj] = ctx.getIntent(numobj).clone();
		}
		for(int numObj1=ctx.getObjectCount()-1;numObj1>=0;numObj1--)
		{
			boolean to_ignore[]=new boolean[ctx.getObjectCount()];			
			for(int numObj2=numObj1-1;numObj2>=0;numObj2--)
			{
				if(!to_ignore[numObj2]) {
					if(ctx.getIntent(numObj2).containsAll(ctx.getIntent(numObj1))) 
					{
						objectHierarchyIntents[numObj2].removeAll(ctx.getIntent(numObj1));						
						for(int numObj3=numObj2-1;numObj3>=0;numObj3--)
						{
							if(!to_ignore[numObj3] && ctx.getIntent(numObj3).containsAll(ctx.getIntent(numObj2)))
							{
								to_ignore[numObj3]=true;
							}
						}
					}
					else to_ignore[numObj2]=true;
				}
			}
				for(int obj=0;obj<numObj1;obj++)
				{
					if(!to_ignore[obj]) result.add(new int[] {obj,numObj1});
				}
		}
		// end of object hierarchy
		result.add(MARK);
		
		// remove object hierarchy intents elements from sewingIntents
		for(int numobj=0;numobj<ctx.getObjectCount();numobj++)
		{
			sewingIntents[numobj].retainAll(objectHierarchyIntents[numobj]);
		}
		// sewing edges
		for(int numobj=0;numobj<ctx.getObjectCount();numobj++)
			for(Iterator<Integer>it=sewingIntents[numobj].iterator();it.hasNext();) {
				result.add(new int[] {numobj,it.next()});
			}
		// end of sewing
		result.add(MARK);
		return result;
	}
	@Override
	public String getDescription() {
		return "Athena";
	}

	@Override
	public void run() {
        try {
            computeGSH();
        } catch (Exception ex) {
            Logger.getLogger(AOC_poset_Athena.class.getName()).log(Level.SEVERE, null, ex);
        }
	}

	@Override
	public IConceptOrder getResult() {
		return gsh;
	}
}
