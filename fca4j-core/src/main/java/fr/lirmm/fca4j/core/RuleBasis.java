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
*/package fr.lirmm.fca4j.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;

import fr.lirmm.fca4j.iset.ISet;

public class RuleBasis {
    /**
     * The graph.
     */
    protected SimpleDirectedGraph<ISet, SupportEdge> ruleGraph = new SimpleDirectedGraph<>(SupportEdge.class);
	protected IBinaryContext context;
	public RuleBasis(IBinaryContext context) {
		this.context=context;
	}
	public RuleBasis(Collection<Implication> implications,IBinaryContext context) {
		for(Implication implication:implications)
		{
			addRule(implication);
		}
	}
	public Iterator<Implication> iteratorByPremiseSize()
	{
	       List<Implication> sortedList = new ArrayList<>(getImplications());
	        // Trier avec une classe interne anonyme
	        Collections.sort(sortedList, new Comparator<Implication>() {
	            @Override
	            public int compare(Implication a1, Implication a2) {
	                return Integer.compare(a1.getPremise().cardinality(), a2.getPremise().cardinality());
	            }
	        });
	        return sortedList.iterator();
	}
	public boolean addRule(Implication implication) {
			ISet premise=implication.getPremise();
			ISet conclusion=implication.getConclusion();
			ruleGraph.addVertex(premise);
			ruleGraph.addVertex(conclusion);
			SupportEdge edge=new SupportEdge(implication.getSupport());
			return ruleGraph.addEdge(premise, conclusion, edge);
	}
	public boolean isEqual(RuleBasis otherBasis) {
		return this.ruleGraph.equals(otherBasis.ruleGraph);
	}
	public boolean isEquivalent(RuleBasis otherBasis) {
		return otherBasis.isGraphIncluded(this.ruleGraph, otherBasis.ruleGraph)
				&& otherBasis.isGraphIncluded(otherBasis.ruleGraph,this.ruleGraph );
	}
// ne fonctionne pas !
	public boolean isIncludedIn2(RuleBasis otherBasis)
	{
		boolean result=otherBasis.isGraphIncluded(this.ruleGraph, otherBasis.ruleGraph);
		return result;
	}
    /**
     * Propagate implications in a graph from an initial set of facts.
     * @param graph the implication graph.
     * @param initialFacts initial facts represented by an ISet.
     * @return Set of reachable facts (ISet).
     */
    protected Set<ISet> propagateImplications(
    		SimpleDirectedGraph<ISet, SupportEdge> graph, 
            ISet initialFacts
    ) {
        Set<ISet> reachable = new HashSet<>();
        Queue<ISet> queue = new LinkedList<>();

        // Ajouter les ensembles compatibles avec les faits initiaux
        for (ISet vertex : graph.vertexSet()) {
            if (vertex.containsAll(initialFacts)) {
                reachable.add(vertex);
                queue.add(vertex);
            }
        }

        while (!queue.isEmpty()) {
            ISet current = queue.poll();
            for (SupportEdge edge : graph.outgoingEdgesOf(current)) {
                ISet target = graph.getEdgeTarget(edge);
                if (!reachable.contains(target)) {
                    reachable.add(target);
                    queue.add(target);
                }
            }
        }

        return reachable;
    }
    /**
     * Verify if G1 is included in G2.
     * @param graphG1 graph of implications for G1.
     * @param graphG2 graph of implications for G2.
     * @return true if G1 is included in G2.
     */
    protected boolean isGraphIncluded(
    		SimpleDirectedGraph<ISet, SupportEdge> graphG1,
    		SimpleDirectedGraph<ISet, SupportEdge> graphG2
    ) {
        for (SupportEdge edge : graphG1.edgeSet()) {
            // Extraire les ensembles source et cible
            ISet source = graphG1.getEdgeSource(edge);
            ISet target = graphG1.getEdgeTarget(edge);

            // Propager les implications pour la source dans G2
            Set<ISet> derivedFromG2 = propagateImplications(graphG2, source);

            // Vérifier si un ensemble compatible avec le target existe dans les dérivés
            boolean isTargetCovered = derivedFromG2.stream()
                    .anyMatch(derived -> derived.containsAll(target));

            if (!isTargetCovered) {
                return false; // Une règle de G1 n'est pas couverte par G2
            }
        }
        return true; // Toutes les règles de G1 sont couvertes par G2
    }
    public List<Implication> getImplications(){
    	ArrayList<Implication> implications=new ArrayList<>();
        for (SupportEdge edge : ruleGraph.edgeSet()) {
            // Extraire les ensembles source et cible
            ISet source = ruleGraph.getEdgeSource(edge);
            ISet target = ruleGraph.getEdgeTarget(edge);
            implications.add(new Implication(source,target,source)); // TODO manage support
        }
    	return implications;
    }
   
	protected class SupportEdge extends DefaultEdge {
	    private final ISet support;

	    public SupportEdge(ISet support) {	    	
	        this.support = support;
	    }

	    public ISet getSupport() {
	        return support;
	    }

	    @Override
	    public String toString() {
	        return "Support: " + support.cardinality();
	    }
	}
	public IBinaryContext getContext() {
		return context;
	}
	}
