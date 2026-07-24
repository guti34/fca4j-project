/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

import org.jgrapht.graph.DirectedPseudograph;

import fr.lirmm.fca4j.core.operator.AbstractScalingOperator;
import fr.lirmm.fca4j.core.operator.MyScalingOperatorFactory;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;

/**
 * The Class RCAFamily.
 */
public class RCAFamily {

    private String familyName;
    private ISetFactory factory;
    private boolean nameWithFullIntentRI=false;
    private boolean nameWithReducedIntent=true;
    private boolean nameWithReducedIntent2=false;
    private boolean nativeOnly=false;

    private HashMap<String, Integer> relAttrsIndex = new HashMap<>();
    /**
     * The graph.
     */
//    protected DirectedMultigraph<String, String> graph = new DirectedMultigraph<>(String.class);
    protected DirectedPseudograph<String, String> graph = new DirectedPseudograph<>(String.class);
    /**
     * The contexts
     */
    protected HashMap<String, FormalContext> formalContexts = new LinkedHashMap<>(); 
    protected HashMap<String, RelationalContext> relationalContexts = new LinkedHashMap<>(); 

    /**
     * Instantiates a new RCA family.
     *
     * @param familyName the family name
     * @param factory the factory
     */
    public RCAFamily(String familyName,ISetFactory factory) {
        this.familyName = familyName;
        this.factory=factory;
    }
    
    /**
     * Gets the factory.
     *
     * @return the factory
     */
    public ISetFactory getFactory(){
        return factory;
    }
    
    /**
     * Gets the name.
     *
     * @return the name
     */
    public String getName() {
        return familyName;
    }

    /**
     * Sets the name with reduced intent.
     *
     * @param nameWithReducedIntent the new name with intent
     */
    public void setNameWithReducedIntentRA(boolean nameWithReducedIntent) {
        this.nameWithReducedIntent = nameWithReducedIntent;
    }
    /**
     * Sets the name with reduced intent except for object concepts.
     *
     * @param nameWithIntent the new name with intent
     */
    public void setNameWithReducedIntentRAI(boolean nameWithReducedIntent) {
        this.nameWithReducedIntent2 = nameWithReducedIntent;
    }
    /**
     * Force inherited attributes when the name is built with intent.
     *
     * @param nameWithIntent the new name with intent
     */
    public void setNameWithFullIntentRI(boolean nameWithFullIntent) {
        this.nameWithFullIntentRI = nameWithFullIntent;
    }
    /**
     * set option to limit the build of intents to native attributes when renaming
     */
    public void setNativeOnly(boolean nativeOnly)
    {
    	this.nativeOnly=nativeOnly;
    }
    /**
     * Gets the formal context.
     *
     * @param name the name
     * @return the formal context
     */
    public FormalContext getFormalContext(String name) {
        return formalContexts.get(name);
    }
    
    /**
     * Gets the relational context.
     *
     * @param name the name
     * @param operator the operator
     * @return the relational context
     */
    public RelationalContext getRelationalContext(String name,String operator) {
        return relationalContexts.get(operator+"_"+name);
    }
    
    /**
     * Gets the relational context.
     *
     * @param name the name
     * @return the relational context
     */
    public List<RelationalContext> getRelationalContext(String name) {
    	ArrayList<RelationalContext> list=new ArrayList<>();
    	for(RelationalContext rc:relationalContexts.values()){
    		if(rc.getRelationName().equals(name))
    			list.add(rc);
    	}
        return list;
    }

    /**
     * Adds the formal context.
     *
     * @param context the context
     * @param myGsh the my gsh
     * @return the formal context
     */
    public FormalContext addFormalContext(IBinaryContext context, ConceptOrder myGsh) {
        FormalContext fc = new FormalContext(myGsh, context);
        formalContexts.put(fc.getName(), fc);
        graph.addVertex(fc.getName());
        return fc;
    }

    /**
     * Delete formal context.
     *
     * @param fc the fc
     * @return true, if successful
     */
    public boolean deleteFormalContext(FormalContext fc) {
        if(!(graph.incomingEdgesOf(fc.getName()).isEmpty() && graph.outgoingEdgesOf(fc.getName()).isEmpty()))
            return false;
        else 
        {
            graph.removeVertex(fc.getName());
            formalContexts.remove(fc.getName());
            return true;
        }
    }
    
    /**
     * Delete relational context.
     *
     * @param rc the rc
     * @return true, if successful
     */
    public boolean deleteRelationalContext(RelationalContext rc) {
        if(graph.removeEdge(rc.getName()))
                {
                 relationalContexts.remove(rc.getName());
                 return true;
                }
        else return false;
    }
    
    /**
     * Rename formal context.
     *
     * @param fc the fc
     * @param newName the new name
     * @return true, if successful
     */
    public boolean renameFormalContext(FormalContext fc, String newName) {
        String oldName = fc.getName();
        if (graph.containsVertex(newName)) return false;
        else{
            graph.addVertex(newName);
            ArrayList<String>incomingEdges=new ArrayList<>(graph.incomingEdgesOf(oldName));
            for (String rel : incomingEdges) {
                String source = graph.getEdgeSource(rel);
                graph.removeEdge(rel);
                graph.addEdge(source, newName, rel);
            }
            ArrayList<String>outgoingEdges=new ArrayList<>(graph.outgoingEdgesOf(oldName));
            for (String rel : outgoingEdges) {
                String target = graph.getEdgeTarget(rel);
                graph.removeEdge(rel);
                graph.addEdge(newName, target, rel);
            }
            graph.removeVertex(oldName);
        fc.getContext().setName(newName);
        formalContexts.put(newName, fc);
        formalContexts.remove(oldName);
        return true;
        }
     }
    
    /**
     * Rename relational context.
     *
     * @param rc the rc
     * @param newRelationName the new relation name
     * @return true, if successful
     */
    public boolean renameRelationalContext(RelationalContext rc, String newRelationName) {
        if (graph.containsEdge(newRelationName)) return false;
        else{
                String source = graph.getEdgeSource(rc.getName());
                String target = graph.getEdgeTarget(rc.getName());
                graph.removeEdge(rc.getName());
                relationalContexts.remove(rc.getName());
                rc.setRelationName(newRelationName);
                 graph.addEdge(source,target,rc.getName());
        relationalContexts.put(rc.getName(),rc);
        return true;
        }
     }

    /**
     * Adds the attribute.
     *
     * @param rc the rc
     * @param concept the concept
     * @param extent the extent
     * @return the int
     */
    public int addAttribute(RelationalContext rc, int concept, ISet extent) {
        FormalContext fcSource = getSourceOf(rc);
        return fcSource.addRelationalAttribute(this, concept, rc, extent);
    }

    /**
     * Adds the relational context.
     *
     * @param context the context
     * @param source the source
     * @param target the target
     * @param operator the operator
     */
    public void addRelationalContext(IBinaryContext context, IBinaryContext source, IBinaryContext target, String operator) {
        addRelationalContext(context, source.getName(), target.getName(), operator);
    }

    /**
     * Adds the relational context.
     *
     * @param context the context
     * @param source the source
     * @param target the target
     * @param operator the operator
     */
    public void addRelationalContext(IBinaryContext context, String source, String target, String operator) {
        RelationalContext rc = new RelationalContext(context, context.getName(), operator);
        relationalContexts.put(rc.getName(), rc);
        graph.addEdge(source, target, rc.getName());
    }
    
    /**
     * Adds the family.
     *
     * @param importFamily the import family
     */
    public void addFamily(RCAFamily importFamily)
    {
        for(String vertex:importFamily.graph.vertexSet())
            graph.addVertex(vertex);
        for(String rel:importFamily.graph.edgeSet())
        {
            String source=importFamily.graph.getEdgeSource(rel);
            String target=importFamily.graph.getEdgeTarget(rel);
            graph.addEdge(source, target,rel);
        }
        relAttrsIndex.putAll(importFamily.relAttrsIndex);
        formalContexts.putAll(importFamily.formalContexts);
        relationalContexts.putAll(importFamily.relationalContexts);
    }
    // find concept number from relational attribute name
    public int getRelationalAttributeConcept(String attrName) {
    	Integer numAttr=relAttrsIndex.get(attrName);
    	return numAttr==null?-1:numAttr;
    }
    
    /**
     * Gets the source of.
     *
     * @param key the key
     * @return the source of
     */
    public FormalContext getSourceOf(RelationalContext key) {
        String fcId = graph.getEdgeSource(key.getName());
        return fcId == null ? null : formalContexts.get(fcId);
    }

    /**
     * Gets the target of.
     *
     * @param key the key
     * @return the target of
     */
    public FormalContext getTargetOf(RelationalContext key) {
        String fcId = graph.getEdgeTarget(key.getName());
        return fcId == null ? null : formalContexts.get(fcId);
    }

    /**
     * The Class FRContext.
     */
    public abstract class FRContext {
        IBinaryContext context;

        /**
         * Gets the name.
         *
         * @return the name
         */
        public abstract String getName();
        
        /**
         * Gets the context.
         *
         * @return the context
         */
        public abstract IBinaryContext getContext();

        /**
         * Sets the context.
         *
         * @param context the new context
         */
        public void setContext(IBinaryContext context) {
            this.context=context;
        }
    }

    /**
     * The Class FormalContext.
     */
    public class FormalContext extends FRContext {

        IConceptOrder myGsh;
        HashMap<Integer, RelationalAttribute> relationalAttributes = new HashMap<>();
        int nativeAttributesCount;

        /**
         * Instantiates a new formal context.
         *
         * @param myGsh the my gsh
         * @param context the context
         */
        FormalContext(IConceptOrder myGsh, IBinaryContext context) {
//            this.myGsh = myGsh;
            this.context = context;
            nativeAttributesCount = context.getAttributeCount();
        }

        /**
         * Gets the context.
         *
         * @return the context
         */
        public IBinaryContext getContext() {
            return context;
        }

        /**
         * Gets the order.
         *
         * @return the order
         */
        public IConceptOrder getOrder() {
            return myGsh;
        }
        
        /**
         * Sets the order.
         *
         * @param order the new order
         */
        public void setOrder(IConceptOrder order) {
            myGsh = order;
        }

        /**
         * Gets the name.
         *
         * @return the name
         */
        public String getName() {
            return context.getName();
        }


        /**
         * Gets the relational attribute.
         *
         * @param numattr the numattr
         * @return the relational attribute
         */
        public RelationalAttribute getRelationalAttribute(int numattr) {
            return relationalAttributes.get(numattr);
        }

         public int addRelationalAttribute(RCAFamily family, int concept, RelationalContext rc, ISet extent) {
            ISet rIntent=family.getTargetOf(rc).getOrder().getConceptReducedIntent(concept);
            ISet intent=family.getTargetOf(rc).getOrder().getConceptIntent(concept).clone();
            intent.removeAll(rIntent);
            String attr_name;
    		attr_name = rc.operator+"_"+rc.getRelationName() + "(" + family.getTargetOf(rc).getConceptName(concept) + ")";            			
            Integer numattr = relAttrsIndex.get(attr_name);
            if (numattr == null) {
                RelationalAttribute rAttr = new RelationalAttribute(concept, rc, attr_name);
                numattr = context.addAttribute(attr_name, extent);
                relationalAttributes.put(numattr, rAttr);
                relAttrsIndex.put(attr_name, numattr);              
            }
            return numattr;
        }

        /**
         * Gets the concept name.
         *
         * @param concept the concept
         * @return the concept name
         */
        public String getConceptName(int concept) {
            return "C_" + getName() + "_" + concept;
        }

        /**
         * Gets the native attribute count.
         *
         * @return the native attribute count
         */
        public int getNativeAttributeCount() {
            return nativeAttributesCount;
        }

        /**
         * Checks if is relational attribute.
         *
         * @param attr the attr
         * @return true, if is relational attribute
         */
        public boolean isRelationalAttribute(int attr) {
            return attr >= 0 && attr < context.getAttributeCount() && relationalAttributes.get(attr) != null;
//            return attr>=0 && attr<context.getAttributeCount() && attr>=context.getAttributeCount()-getNativeAttributeCount();
        }
    }

    /**
     * The Class RelationalContext.
     */
    public class RelationalContext extends FRContext {

        String relName;
        String operator;

        /**
         * Instantiates a new relational context.
         *
         * @param context the context
         * @param relName the rel name
         * @param operator the operator
         */
        RelationalContext(IBinaryContext context, String relName, String operator) {
            this.relName = relName;
            this.operator = operator;
            this.context = context;
        }

        /**
         * Gets the name.
         *
         * @return the name
         */
        public String getName() {
            return operator + "_" + relName;
        }

        /**
         * Gets the relation name.
         *
         * @return the relation name
         */
        public String getRelationName() {
            return relName;
        }
        
        /**
         * Sets the relation name.
         *
         * @param newName the new relation name
         */
        public void setRelationName(String newName)
        {
            relName=newName;
        }
        
        /**
         * Gets the operator.
         *
         * @return the operator
         */
        public AbstractScalingOperator getOperator() {
            return MyScalingOperatorFactory.createScalingOperator(operator);
        }

        /**
         * Gets the context.
         *
         * @return the context
         */
        public IBinaryContext getContext() {
            return context;
        }
    }

    /**
     * Gets the formal contexts.
     *
     * @return the formal contexts
     */
    public Collection<FormalContext> getFormalContexts() {
        return formalContexts.values();
    }

    /**
     * Gets the relational contexts.
     *
     * @return the relational contexts
     */
    public Collection<RelationalContext> getRelationalContexts() {
        return relationalContexts.values();
    }

    /**
     * Outgoing relational context of.
     *
     * @param fc the fc
     * @return the collection
     */
    public Collection<RelationalContext> outgoingRelationalContextOf(FormalContext fc) {
        ArrayList<RelationalContext> list = new ArrayList<>();
        for (String rcId : graph.outgoingEdgesOf(fc.getName())) {
            list.add(relationalContexts.get(rcId));
        }
        return list;
    }

    /**
     * Incoming relational context of.
     *
     * @param fc the fc
     * @return the collection
     */
    public Collection<RelationalContext> incomingRelationalContextOf(FormalContext fc) {
        ArrayList<RelationalContext> list = new ArrayList<>();
        for (String rcId : graph.incomingEdgesOf(fc.getName())) {
            list.add(relationalContexts.get(rcId));
        }
        return list;
    }
    public int cleanUnusedRelationalAttributes() {
    	int total=0;
    	for(FormalContext fc:formalContexts.values()) {
    		total+=cleanUnusedRelationalAttributes(fc);
    	}
    	return total;    		
    }
    private int cleanUnusedRelationalAttributes(FormalContext fc) {
    	int total=0;
    	ArrayList<Integer> attrToRemove=new ArrayList<>();
    	for(int numAttr=0;numAttr<fc.getContext().getAttributeCount();numAttr++)
    	{    		
    		String attrName=fc.getContext().getAttributeName(numAttr);
    		// parse concept name
    		int beg = attrName.indexOf("(C_");
    		if (beg < 0)
    			continue; // native attribute
    		beg += 3;
    		int end = beg;
    		while (attrName.charAt(end) != '_') {
    			end++;
    		}
    		String fcName = attrName.substring(beg, end);
    		FormalContext fcTarget=formalContexts.get(fcName);
    		beg = end + 1;
    		end = attrName.indexOf(')', beg);
    		String strConcept = attrName.substring(beg, end);
    		int concept = Integer.valueOf(strConcept);
    		
    		if(!fcTarget.getOrder().getConcepts().contains(concept)) {
    			attrToRemove.add(numAttr);
//    			System.out.println("remove "+attrName+" from "+fc.getName());
    		}
    	}
    	// the last created attributes must be removed at first
    	Collections.sort(attrToRemove, Collections.reverseOrder());    	
    	for(int numAttr:attrToRemove)
    	{
    		fc.getContext().removeAttribute(numAttr);
    		fc.relationalAttributes.remove(numAttr);
    		total++;
    	}
    	return total;
    }

    private int cleanUnusedRelationalAttributes2(FormalContext fc) {
    	int total=0;
    	HashSet<Integer> attrToRemove=new HashSet<>();
    	for(int numAttr:fc.relationalAttributes.keySet())
    	{
    		RelationalAttribute ra=fc.relationalAttributes.get(numAttr);
    		String attrName=ra.getName();
    		// parse concept name
    		int beg = attrName.indexOf("(C_");
    		if (beg < 0)
    			continue; // native attribute
    		beg += 3;
    		int end = beg;
    		while (attrName.charAt(end) != '_') {
    			end++;
    		}
    		String fcName = attrName.substring(beg, end);
    		FormalContext fcTarget=formalContexts.get(fcName);
    		beg = end + 1;
    		end = attrName.indexOf(')', beg);
    		String strConcept = attrName.substring(beg, end);
    		int concept = Integer.valueOf(strConcept);
    		
    		if(!fcTarget.getOrder().getConcepts().contains(concept)) {
    			attrToRemove.add(numAttr);
    			System.out.println("remove "+attrName+" from "+fc.getName());
    		}
    	}
    	for(int numAttr:attrToRemove)
    	{
    		fc.getContext().removeAttribute(numAttr);
    		fc.relationalAttributes.remove(numAttr);
    		relAttrsIndex.remove(fc.getRelationalAttribute(numAttr).getName());
    		total++;
    	}
    	return total;
    }

	public void setOperator(RelationalContext rc, String newOp) {
		rc.operator=newOp;		
	}
}
