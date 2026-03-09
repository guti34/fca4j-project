package fr.lirmm.fca4j.algo;

import java.util.*;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

public class LatticeInClose4 implements AbstractAlgo<ConceptOrder> {

    private IBinaryContext context;
    private ConceptOrder order;
    private ISetFactory factory;
    private ClosureWithHistory closure;
    private Chrono chrono;

    // recherche O(1) par intent
    private Map<ISet, Integer> conceptIndex;

    public LatticeInClose4(IBinaryContext context, Chrono chrono) {
        this.context = context;
        this.factory = context.getFactory();
        this.closure = new ClosureWithHistory(context);
        this.chrono = chrono;
    }

    @Override
    public void run() {
        order = new ConceptOrder("TopDownClosureWithHistory", context, getDescription());
        conceptIndex = new HashMap<>();

        // ============================
        // Top concept
        // ============================

        ISet topExtent = factory.createSet(context.getObjectCount());
        topExtent.fill(context.getObjectCount());

        ISet topIntent = closure.computeIntent(topExtent);

        int topId = order.addConcept(
                topExtent.clone(),
                topIntent.clone(),
                topExtent.clone(),
                topIntent.clone());

        conceptIndex.put(topIntent.clone(), topId);

        // ============================
        // lancement récursif Top-Down
        // ============================

        int firstAttr = 0;
        exploreTopDown(topId, topIntent, firstAttr);
    }

    private void exploreTopDown(int parentId, ISet parentIntent, int startAttr) {
        int attrCount = context.getAttributeCount();

        for (int a = startAttr; a < attrCount; a++) {

            if (parentIntent.contains(a))
                continue; // attribut déjà présent dans l'intent parent

            // Création du nouvel intent candidat
            ISet candIntent = parentIntent.clone();
            candIntent.add(a);

            // Calcul du closure
            ISet candExtent = closure.closure(factory.createSet(), candIntent, parentIntent, order.getConceptExtent(parentId));

            // Recherche du concept déjà existant
            Integer candId = conceptIndex.get(candIntent);
            if (candId == null) {
                // ReducedExtent = candExtent - parentExtent
                ISet reducedExtent = candExtent.clone();
                reducedExtent.removeAll(order.getConceptExtent(parentId));

                // ReducedIntent = candIntent - parentIntent
                ISet reducedIntent = candIntent.clone();
                reducedIntent.removeAll(parentIntent);

                // Ajout du concept
                candId = order.addConcept(
                        candExtent.clone(),
                        candIntent.clone(),
                        reducedExtent,
                        reducedIntent);

                conceptIndex.put(candIntent.clone(), candId);

                // arête couvrante
                order.addPrecedenceConnection(parentId, candId);

                // mise à jour du reducedIntent du parent
                order.getConceptReducedIntent(parentId).removeAll(candIntent);

                // mise à jour du reducedExtent de l'enfant
                order.getConceptReducedExtent(candId).removeAll(order.getConceptExtent(parentId));

                // appel récursif
                exploreTopDown(candId, candIntent, a + 1);
            }
        }
    }

    @Override
    public ConceptOrder getResult() {
        return order;
    }

    @Override
    public String getDescription() {
        return "TopDownClosureWithHistory";
    }
}