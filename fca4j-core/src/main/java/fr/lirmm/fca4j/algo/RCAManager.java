/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.algo;

import java.util.HashMap;
import java.util.Iterator;

import org.jgrapht.graph.DefaultDirectedGraph;

import fr.lirmm.fca4j.core.IConceptOrder;
import fr.lirmm.fca4j.core.RCAFamily;
import fr.lirmm.fca4j.core.RCAFamily.FormalContext;
import fr.lirmm.fca4j.iset.ISet;

public class RCAManager {
	protected enum NODETYPE {CONCEPT,INTENT,EXTENT}
	protected HashMap<String,ConceptGraph> graphs;

	protected RCAFamily rcf;

	public RCAManager(RCAFamily rcf) {
		this.rcf = rcf;
		this.graphs= new HashMap<>();
		for(FormalContext fc:rcf.getFormalContexts())
		{
			graphs.put(fc.getName(), new ConceptGraph(fc));
		}
	}
	public void add(FormalContext fc,int step) {
		ConceptGraph graph=graphs.get(fc.getName());
		IConceptOrder order=fc.getOrder();
		for(int concept:order.getConcepts())
		{
			graph.addIntentEdge(concept, order.getConceptIntent(concept),step);
			graph.addExtentEdge(concept, order.getConceptExtent(concept), step);
		}
		for(Iterator<Integer> it=order.getBasicIterator();it.hasNext();)
		{
			int concept=it.next();
			for(Iterator<Integer> itChildren=order.getLowerCoverIterator(concept);itChildren.hasNext();)
			{
				int child=itChildren.next();
				graph.addEdge(concept,child, step);
			}
		}
	}
/*	public ConceptOrder getConceptOrder(FormalContext fc,String algoName,int step)
	{
		ConceptOrder co=new ConceptOrder(fc.getName()+"-"+step, fc.getContext(), algoName);
		
	}
*/	
	/** Arête représentant une relation créée à une étape donnée */
	public class StepEdge {
		private final int step;

		public StepEdge(int step) {
			this.step = step;
		}

		public int getStep() {
			return step;
		}

		@Override
		public String toString() {
			return "step=" + step;
		}
	}

	/** Graphe monitorant l’évolution des concepts */
	public class ConceptGraph {
		private final DefaultDirectedGraph<String, StepEdge> cGraph;
		protected FormalContext fc;
//		private final HashMap<String,>
		public ConceptGraph(FormalContext fc) {
			this.fc=fc;
			// Graphe orienté avec arêtes étiquetées
			this.cGraph = new DefaultDirectedGraph<>(StepEdge.class);
		}
		public String addIntentNode(ISet intent) {
			String intentId="I_"+intent.hashCode();
			cGraph.addVertex(intentId);
			return intentId;
		}
		public String addExtentNode(ISet extent) {
			String extentId="E_"+extent.hashCode();
			cGraph.addVertex(extentId);
			return extentId;
		}
		public String addConceptNode(int concept) {
			String conceptName=fc.getConceptName(concept);
			cGraph.addVertex(conceptName);
			return conceptName;
		}

		public void addEdge(int from, int to, int step) {
			cGraph.addEdge(fc.getConceptName(from), fc.getConceptName(to), new StepEdge(step));
		}
		public void addExtentEdge(int concept, ISet to, int step) {
			cGraph.addEdge(fc.getConceptName(concept), "E_"+to.hashCode(), new StepEdge(step));
		}
		public void addIntentEdge(int concept, ISet to, int step) {
			cGraph.addEdge(fc.getConceptName(concept), "I_"+to.hashCode(), new StepEdge(step));
		}

		public DefaultDirectedGraph<String, StepEdge> getGraph() {
			return cGraph;
		}

		/** Exemple d’affichage des étapes */
		public void printEdges() {
			for (StepEdge e : cGraph.edgeSet()) {
				String src = cGraph.getEdgeSource(e);
				String tgt = cGraph.getEdgeTarget(e);
				System.out.println(src + " -> " + tgt + " (" + e + ")");
			}
		}
	}

}
