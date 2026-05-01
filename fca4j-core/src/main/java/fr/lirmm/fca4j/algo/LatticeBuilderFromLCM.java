/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.algo;

import java.util.*;
import java.util.BitSet;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;

/**
 * FCA4J-compatible Lattice builder using LCM intents and Bordat Hasse construction.
 */
public class LatticeBuilderFromLCM implements AbstractAlgo<ConceptOrder> {

    private IBinaryContext context;
    private ConceptOrder order;
    private ISetFactory factory;
    private ClosureWithHistory closure;

    private List<ISet> generatorIntents;
    private Map<ISet,Integer> intentMap;

    public LatticeBuilderFromLCM(IBinaryContext context) {
        this.context = context;
        this.factory = context.getFactory();
        this.closure = new ClosureWithHistory(context);
        this.intentMap = new HashMap<>();
    }

    @Override
    public String getDescription() {
        return "LCM-based lattice builder with Bordat Hasse (FCA4J-compatible)";
    }

    @Override
    public ConceptOrder getResult() {
        return order;
    }

    @Override
    public void run() {
        // 1. Generate closed intents via LCM
        LCMIntentGenerator gen = new LCMIntentGenerator(context);
        generatorIntents = gen.compute();

        // 2. Create lattice structure
        order = new ConceptOrder("LCM_Lattice", context, getDescription());

        // 3. Add all concepts
        buildConcepts();

        // 4. Compute reduced intents and extents
        computeReducedSets();

        // 5. Build Hasse diagram using Bordat
        buildHierarchyBordat();
    }

    // ------------------------------------------------------------
    // BUILD CONCEPTS
    // ------------------------------------------------------------

    private void buildConcepts() {
        ISet emptyIntent = factory.createSet(context.getAttributeCount());
        addConceptFromIntent(emptyIntent);

        for (ISet generator : generatorIntents) {
            addConceptFromIntent(generator);
        }
    }

    private int addConceptFromIntent(ISet generatorIntent) {
        ISet intentClosure = factory.clone(generatorIntent);
        ISet extent = closure.closure(intentClosure, generatorIntent, null, null);

        ISet intentKey = factory.clone(intentClosure);
        Integer existing = intentMap.get(intentKey);
        if (existing != null) return existing;

        int id = order.addConcept(factory.clone(extent), factory.clone(intentClosure));
        intentMap.put(intentKey, id);
        return id;
    }

    // ------------------------------------------------------------
    // REDUCED SETS
    // ------------------------------------------------------------

    private void computeReducedSets() {
        for (int concept : order.getConcepts()) {
            computeReducedIntent(concept);
            computeReducedExtent(concept);
        }
    }

    private void computeReducedIntent(int concept) {
        ISet intent = order.getConceptIntent(concept);
        ISet reduced = order.getConceptReducedIntent(concept);

        // initialize reduced intent to the intent itself
        reduced.removeAll(reduced); // empty
        reduced.addAll(intent);

        Iterator<Integer> parentIt = order.getUpperCoverIterator(concept);
        while (parentIt.hasNext()) {
            int parent = parentIt.next();
            ISet parentIntent = order.getConceptIntent(parent);
            ISet diff = reduced.newDifference(parentIntent);
            reduced.removeAll(reduced); // empty
            reduced.addAll(diff);
        }
    }

    private void computeReducedExtent(int concept) {
        ISet extent = order.getConceptExtent(concept);
        ISet reduced = order.getConceptReducedExtent(concept);

        reduced.removeAll(reduced); // empty
        reduced.addAll(extent);

        Iterator<Integer> childIt = order.getLowerCoverIterator(concept);
        while (childIt.hasNext()) {
            int child = childIt.next();
            ISet childExtent = order.getConceptExtent(child);
            ISet diff = reduced.newDifference(childExtent);
            reduced.removeAll(reduced); // empty
            reduced.addAll(diff);
        }
    }

    // ------------------------------------------------------------
    // HASSE BUILDING (Bordat)
    // ------------------------------------------------------------

    private void buildHierarchyBordat() {
        Map<ISet,Integer> intentLookup = new HashMap<>();
        for (int c : order.getConcepts()) {
            ISet intent = order.getConceptIntent(c);
            intentLookup.put(factory.clone(intent), c);
        }

        for (int c : order.getConcepts()) {
            ISet reducedIntent = order.getConceptReducedIntent(c);
            Iterator<Integer> itAttr = reducedIntent.iterator();
            while (itAttr.hasNext()) {
                int attr = itAttr.next();
                ISet candidateIntent = factory.clone(order.getConceptIntent(c));
                candidateIntent.remove(attr);

                ISet closureIntent = factory.createSet();
                closure.closure(closureIntent, candidateIntent, null, null);

                Integer parentId = intentLookup.get(closureIntent);
                if (parentId != null) {
                    order.addPrecedenceConnection(c, parentId);
                }
            }
        }
    }

    // ------------------------------------------------------------
    // LCM GENERATOR
    // ------------------------------------------------------------

    private class LCMIntentGenerator {
        private List<ISet> closedIntents;

        public LCMIntentGenerator(IBinaryContext context) {
            this.closedIntents = new ArrayList<>();
        }

        public List<ISet> compute() {
            ISet empty = factory.createSet();
            ISet closureIntent = factory.createSet();
            ISet extent = closure.closure(closureIntent, empty, null, null);
            dfs(closureIntent, extent, -1);
            return closedIntents;
        }

        private void dfs(ISet intent, ISet extent, int lastAttr) {
            closedIntents.add(factory.clone(intent));

            int attrCount = context.getAttributeCount();
            for (int attr = lastAttr + 1; attr < attrCount; attr++) {
                if (intent.contains(attr)) continue;

                ISet candidate = factory.clone(intent);
                candidate.add(attr);

                ISet newIntent = factory.createSet();
                ISet newExtent = closure.closure(newIntent, candidate, intent, extent);

                if (!isCanonical(intent, newIntent, attr)) continue;

                dfs(newIntent, newExtent, attr);
            }
        }

        private boolean isCanonical(ISet parentIntent, ISet newIntent, int addedAttr) {
            Iterator<Integer> it = newIntent.iterator();
            while (it.hasNext()) {
                int a = it.next();
                if (a < addedAttr && !parentIntent.contains(a)) return false;
            }
            return true;
        }
    }
}