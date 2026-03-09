package fr.lirmm.fca4j.algo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

/**
 * LCM-based direct lattice construction.
 *
 * - Builds ConceptOrder during DFS
 * - No post hierarchy reconstruction
 * - Uses ClosureWithHistory
 * - FCA4J compatible
 */
public class LCMConceptOrderBuilder implements AbstractAlgo<ConceptOrder>{

	    private IBinaryContext context;
	    private ConceptOrder order;
	    private ISetFactory factory;
	    private ClosureWithHistory closure;
	    private Chrono chrono;

	    // Map pour retrouver un concept par son intent
	    private Map<ISet, Integer> intentToConcept;

	    public LCMConceptOrderBuilder(IBinaryContext context, Chrono chrono) {
	        this.context = context;
	        this.factory = context.getFactory();
	        this.closure = new ClosureWithHistory(context);
	        this.chrono = chrono;
	    }

	    @Override
	    public void run() {
	        order = new ConceptOrder("LatticeAddIntentHistory", context, getDescription());
	        intentToConcept = new HashMap<>();

	        // ============================
	        // Top concept
	        // ============================
	        ISet topExtent = factory.createSet();
	        topExtent.fill(context.getObjectCount());

	        ISet topIntent = closure.computeIntent(topExtent);

	        int topId = order.addConcept(topExtent, topIntent, topExtent.clone(), topIntent.clone());
	        intentToConcept.put(topIntent.clone(), topId);

	        // Queue pour DFS
	        Deque<Integer> stack = new ArrayDeque<>();
	        stack.push(topId);

	        while (!stack.isEmpty()) {
	            int parentId = stack.pop();
	            ISet parentIntent = order.getConceptIntent(parentId);
	            ISet parentExtent = order.getConceptExtent(parentId);

	            // Génération des candidats : ajout d’un attribut
	            for (int attr = 0; attr < context.getAttributeCount(); attr++) {
	                if (parentIntent.contains(attr)) continue;

	                ISet newIntent = parentIntent.clone();
	                newIntent.add(attr);

	                ISet newExtent = closure.closure(factory.createSet(), newIntent, parentIntent, parentExtent);

	                // Skip si pas strictement plus petit
	                if (newExtent.equals(parentExtent) && newIntent.equals(parentIntent)) continue;

	                Integer childId = intentToConcept.get(newIntent);

	                if (childId == null) {
	                    ISet reducedExtent = newExtent.clone();
	                    reducedExtent.removeAll(parentExtent);

	                    ISet reducedIntent = newIntent.clone();

	                    childId = order.addConcept(newExtent, newIntent, reducedExtent, reducedIntent);
	                    intentToConcept.put(newIntent.clone(), childId);

	                    stack.push(childId);
	                }

	                // Ajouter l'arête couvrante
	                order.addPrecedenceConnection(parentId, childId);

	                // Mettre à jour reduced sets
	                order.getConceptReducedIntent(parentId).removeAll(order.getConceptIntent(childId));
	                order.getConceptReducedExtent(childId).removeAll(parentExtent);
	            }
	        }
	    }

	    @Override
	    public ConceptOrder getResult() {
	        return order;
	    }

	    @Override
	    public String getDescription() {
	        return "AddIntent avec ClosureWithHistory et Map";
	    }
	}