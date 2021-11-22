package fr.lirmm.fca4j.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

import org.jgrapht.graph.DirectedMultigraph;

import fr.lirmm.fca4j.core.operator.AbstractScalingOperator;
import fr.lirmm.fca4j.core.operator.MyScalingOperatorFactory;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;

public class RCAFamily {

    private String familyName;
    private ISetFactory factory;
    private boolean nameWithIntent=true;

    private HashMap<String, Integer> relAttrsIndex = new HashMap<>();
    /**
     * The graph.
     */
    protected DirectedMultigraph<String, String> graph = new DirectedMultigraph<>(String.class);
    /**
     * The contexts
     */
    protected HashMap<String, FormalContext> formalContexts = new LinkedHashMap<>(); 
    protected HashMap<String, RelationalContext> relationalContexts = new LinkedHashMap<>(); 

    public RCAFamily(String familyName,ISetFactory factory) {
        this.familyName = familyName;
        this.factory=factory;
    }
    public ISetFactory getFactory(){
        return factory;
    }
    public String getName() {
        return familyName;
    }

    public void setNameWithIntent(boolean nameWithIntent) {
        this.nameWithIntent = nameWithIntent;
    }

    public FormalContext getFormalContext(String name) {
        return formalContexts.get(name);
    }
    public RelationalContext getRelationalContext(String name,String operator) {
        return relationalContexts.get(operator+"_"+name);
    }
    public List<RelationalContext> getRelationalContext(String name) {
    	ArrayList<RelationalContext> list=new ArrayList<>();
    	for(RelationalContext rc:relationalContexts.values()){
    		if(rc.getRelationName().equals(name))
    			list.add(rc);
    	}
        return list;
    }

    public FormalContext addFormalContext(IBinaryContext context, ConceptOrder myGsh) {
        FormalContext fc = new FormalContext(myGsh, context);
        formalContexts.put(fc.getName(), fc);
        graph.addVertex(fc.getName());
        return fc;
    }

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
    public boolean deleteRelationalContext(RelationalContext rc) {
        if(graph.removeEdge(rc.getName()))
                {
                 relationalContexts.remove(rc.getName());
                 return true;
                }
        else return false;
    }
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

    public int addAttribute(RelationalContext rc, int concept, ISet extent) {
        FormalContext fcSource = getSourceOf(rc);
        return fcSource.addRelationalAttribute(this, concept, rc, extent);
    }

    public void addRelationalContext(IBinaryContext context, IBinaryContext source, IBinaryContext target, String operator) {
        addRelationalContext(context, source.getName(), target.getName(), operator);
    }

    public void addRelationalContext(IBinaryContext context, String source, String target, String operator) {
        RelationalContext rc = new RelationalContext(context, context.getName(), operator);
        relationalContexts.put(rc.getName(), rc);
        graph.addEdge(source, target, rc.getName());
    }
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
    public FormalContext getSourceOf(RelationalContext key) {
        String fcId = graph.getEdgeSource(key.getName());
        return fcId == null ? null : formalContexts.get(fcId);
    }

    public FormalContext getTargetOf(RelationalContext key) {
        String fcId = graph.getEdgeTarget(key.getName());
        return fcId == null ? null : formalContexts.get(fcId);
    }

    public abstract class FRContext {
        IBinaryContext context;

        public abstract String getName();
        public abstract IBinaryContext getContext();

        public void setContext(IBinaryContext context) {
            this.context=context;
        }
    }

    public class FormalContext extends FRContext {

        ConceptOrder myGsh;
        HashMap<Integer, RelationalAttribute> relationalAttributes = new HashMap<>();
        int nativeAttributesCount;

        FormalContext(ConceptOrder myGsh, IBinaryContext context) {
//            this.myGsh = myGsh;
            this.context = context;
            nativeAttributesCount = context.getAttributeCount();
        }

        public IBinaryContext getContext() {
            return context;
        }

        public ConceptOrder getOrder() {
            return myGsh;
        }
        public void setOrder(ConceptOrder order) {
            myGsh = order;
        }

        public String getName() {
            return context.getName();
        }


        public RelationalAttribute getRelationalAttribute(int numattr) {
            return relationalAttributes.get(numattr);
        }

        public int addRelationalAttribute(RCAFamily family, int concept, RelationalContext rc, ISet extent) {
            ISet rIntent=family.getTargetOf(rc).getOrder().getConceptReducedIntent(concept);
            String attr_name;
            if(nameWithIntent && !rIntent.isEmpty())
            {
                attr_name=rc.operator+"_"+rc.getRelationName() + "(";
                for(Iterator<Integer> it=rIntent.iterator();it.hasNext();)
                {
                    if(!attr_name.endsWith("("))
                        attr_name+="&";
                    attr_name+=family.getTargetOf(rc).getOrder().getContext().getAttributeName(it.next());
                }
                attr_name+=")";
            }
            else attr_name = rc.operator+"_"+rc.getRelationName() + "(" + family.getTargetOf(rc).getConceptName(concept) + ")";
            Integer numattr = relAttrsIndex.get(attr_name);
            if (numattr != null) {
                return numattr;
            } else {
                RelationalAttribute rAttr = new RelationalAttribute(concept, rc, attr_name);
                numattr = context.addAttribute(attr_name, extent);
                relationalAttributes.put(numattr, rAttr);
                relAttrsIndex.put(attr_name, numattr);
                return numattr;
            }
        }

        public String getConceptName(int concept) {
            return "C_" + getName() + "_" + concept;
        }

        public int getNativeAttributeCount() {
            return nativeAttributesCount;
        }

        public boolean isRelationalAttribute(int attr) {
            return attr >= 0 && attr < context.getAttributeCount() && relationalAttributes.get(attr) != null;
//            return attr>=0 && attr<context.getAttributeCount() && attr>=context.getAttributeCount()-getNativeAttributeCount();
        }
    }

    public class RelationalContext extends FRContext {

        String relName;
        String operator;

        RelationalContext(IBinaryContext context, String relName, String operator) {
            this.relName = relName;
            this.operator = operator;
            this.context = context;
        }

        public String getName() {
            return operator + "_" + relName;
        }

        public String getRelationName() {
            return relName;
        }
        public void setRelationName(String newName)
        {
            relName=newName;
        }
        public AbstractScalingOperator getOperator() {
            return MyScalingOperatorFactory.createScalingOperator(operator);
        }

        public IBinaryContext getContext() {
            return context;
        }
    }

    public Collection<FormalContext> getFormalContexts() {
        return formalContexts.values();
    }

    public Collection<RelationalContext> getRelationalContexts() {
        return relationalContexts.values();
    }

    public Collection<RelationalContext> outgoingRelationalContextOf(FormalContext fc) {
        ArrayList<RelationalContext> list = new ArrayList<>();
        for (String rcId : graph.outgoingEdgesOf(fc.getName())) {
            list.add(relationalContexts.get(rcId));
        }
        return list;
    }

    public Collection<RelationalContext> incomingRelationalContextOf(FormalContext fc) {
        ArrayList<RelationalContext> list = new ArrayList<>();
        for (String rcId : graph.incomingEdgesOf(fc.getName())) {
            list.add(relationalContexts.get(rcId));
        }
        return list;
    }
}
