/*
BSD 3-Clause License

Copyright (c) 2022 LIRMM
*/
package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import fr.lirmm.fca4j.core.BinaryContext;
import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

/**
 * Parallel lattice construction using a divide-and-conquer strategy.
 * <p>
 * The attribute set is partitioned into N groups. N partial lattices are
 * built in parallel using the standard AddExtent algorithm. Then they are
 * merged pairwise in a binary tree pattern until a single lattice remains.
 * <p>
 * The merge of two lattices L1 (on attributes M1) and L2 (on attributes M2)
 * works by taking L1 as a base and incrementally inserting only the attributes
 * of M2. This avoids rebuilding the lattice from scratch at each merge level.
 * <p>
 * Based on the theory of context apposition (Valtchev, Missaoui, Lebrun 2002).
 *
 * @author agutierr
 */
public class Lattice_AddExtent_Parallel implements AbstractAlgo<ConceptOrder> {

    private final IBinaryContext matrix;
    private final ISetFactory factory;
    private final Chrono chrono;
    private final int nbThreads;
    private ConceptOrder order;

    public Lattice_AddExtent_Parallel(IBinaryContext matrix, Chrono chrono, int nbThreads) {
        this.matrix = matrix;
        this.factory = matrix.getFactory();
        this.chrono = chrono;
        this.nbThreads = nbThreads > 0 ? nbThreads : Runtime.getRuntime().availableProcessors();
    }

    public Lattice_AddExtent_Parallel(IBinaryContext matrix, Chrono chrono) {
        this(matrix, chrono, 0);
    }

    public Lattice_AddExtent_Parallel(IBinaryContext matrix) {
        this(matrix, null, 0);
    }

    @Override
    public String getDescription() {
        return "AddExtent_Parallel(" + nbThreads + " threads)";
    }

    @Override
    public ConceptOrder getResult() {
        return order;
    }

    @Override
    public void run() {
        int nbAttributes = matrix.getAttributeCount();

        if (nbAttributes <= nbThreads || nbAttributes <= 4) {
            Lattice_AddExtent sequential = new Lattice_AddExtent(matrix, chrono);
            sequential.run();
            this.order = sequential.getResult();
            return;
        }

        try {
            // ============================================================
            // Phase 1: Partition attributes into groups
            // ============================================================
            int nbPartitions = Math.min(nbThreads, nbAttributes);
            int[][] partitions = partitionAttributes(nbAttributes, nbPartitions);

            // ============================================================
            // Phase 2: Build partial lattices in parallel
            // ============================================================
            ExecutorService executor = Executors.newFixedThreadPool(nbPartitions);

            List<Future<PartialLattice>> futures = new ArrayList<>();
            for (int[] partition : partitions) {
                futures.add(executor.submit(new PartialLatticeBuilder(matrix, factory, partition)));
            }

            List<PartialLattice> partialLattices = new ArrayList<>();
            for (Future<PartialLattice> future : futures) {
                partialLattices.add(future.get());
            }

            // ============================================================
            // Phase 3: Merge pairwise in binary tree (incremental)
            // ============================================================
            while (partialLattices.size() > 1) {
                List<Future<PartialLattice>> mergeFutures = new ArrayList<>();

                for (int i = 0; i < partialLattices.size(); i += 2) {
                    if (i + 1 < partialLattices.size()) {
                        PartialLattice left = partialLattices.get(i);
                        PartialLattice right = partialLattices.get(i + 1);
                        mergeFutures.add(executor.submit(
                                new LatticeMerger(matrix, factory, left, right)));
                    } else {
                        PartialLattice singleton = partialLattices.get(i);
                        mergeFutures.add(executor.submit(() -> singleton));
                    }
                }

                partialLattices = new ArrayList<>();
                for (Future<PartialLattice> future : mergeFutures) {
                    partialLattices.add(future.get());
                }
            }

            executor.shutdown();

            // ============================================================
            // Phase 4: Final result
            // ============================================================
            this.order = partialLattices.get(0).order;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =====================================================================
    // Attribute partitioning
    // =====================================================================

    private int[][] partitionAttributes(int nbAttributes, int nbPartitions) {
        int[][] partitions = new int[nbPartitions][];
        int baseSize = nbAttributes / nbPartitions;
        int remainder = nbAttributes % nbPartitions;

        int offset = 0;
        for (int p = 0; p < nbPartitions; p++) {
            int size = baseSize + (p < remainder ? 1 : 0);
            partitions[p] = new int[size];
            for (int i = 0; i < size; i++) {
                partitions[p][i] = offset + i;
            }
            offset += size;
        }
        return partitions;
    }

    // =====================================================================
    // PartialLattice
    // =====================================================================

    static class PartialLattice {
        final int[] attributeIndices;
        final ConceptOrder order;

        PartialLattice(int[] attributeIndices, ConceptOrder order) {
            this.attributeIndices = attributeIndices;
            this.order = order;
        }
    }

    // =====================================================================
    // Phase 2: Build a partial lattice from a subset of attributes
    // =====================================================================

    static class PartialLatticeBuilder implements Callable<PartialLattice> {
        private final IBinaryContext fullContext;
        private final ISetFactory factory;
        private final int[] attrIndices;

        PartialLatticeBuilder(IBinaryContext fullContext, ISetFactory factory, int[] attrIndices) {
            this.fullContext = fullContext;
            this.factory = factory;
            this.attrIndices = attrIndices;
        }

        @Override
        public PartialLattice call() throws Exception {
            IBinaryContext subCtx = createSubContext(fullContext, factory, attrIndices);
            Lattice_AddExtent algo = new Lattice_AddExtent(subCtx, null);
            algo.run();
            return new PartialLattice(attrIndices, algo.getResult());
        }
    }

    // =====================================================================
    // Phase 3: Incremental merge — insert right's attributes into left
    // =====================================================================

    /**
     * Merges two partial lattices by taking the larger as base and
     * inserting the other's attributes one by one via AddExtent.
     * <p>
     * This is the key optimization: instead of rebuilding from scratch,
     * we reuse the existing lattice structure and only add the new
     * attributes incrementally. The work done is proportional to the
     * number of NEW concepts created, not the total lattice size.
     */
    static class LatticeMerger implements Callable<PartialLattice> {
        private final IBinaryContext fullContext;
        private final ISetFactory factory;
        private final PartialLattice left;
        private final PartialLattice right;

        LatticeMerger(IBinaryContext fullContext, ISetFactory factory,
                      PartialLattice left, PartialLattice right) {
            this.fullContext = fullContext;
            this.factory = factory;
            this.left = left;
            this.right = right;
        }

        @Override
        public PartialLattice call() throws Exception {
            // Take the larger lattice as base to minimize insertions
            PartialLattice base, other;
            if (left.order.getConceptCount() >= right.order.getConceptCount()) {
                base = left;
                other = right;
            } else {
                base = right;
                other = left;
            }

            ConceptOrder order = base.order;
            int top = order.getTop();

            // Insert each attribute from the 'other' partition
            for (int globalAttr : other.attributeIndices) {
                ISet extent = fullContext.getExtent(globalAttr);
                int concept = addExtent(order, factory, extent, top);
                order.getConceptReducedIntent(concept).add(globalAttr);
            }

            // Recompute reduced extents
            recomputeReducedExtents(order, factory);

            // Recompute full intents
            clearIntents(order);
            order.computeIntents();

            // Combine attribute indices
            int[] mergedAttrs = new int[left.attributeIndices.length + right.attributeIndices.length];
            System.arraycopy(base.attributeIndices, 0, mergedAttrs, 0, base.attributeIndices.length);
            System.arraycopy(other.attributeIndices, 0, mergedAttrs,
                    base.attributeIndices.length, other.attributeIndices.length);

            return new PartialLattice(mergedAttrs, order);
        }
    }

    // =====================================================================
    // AddExtent (standalone, operates on a ConceptOrder directly)
    // =====================================================================

    static int addExtent(ConceptOrder order, ISetFactory factory,
                         ISet extent, int generator) throws Exception {
        generator = getSmallestContainingConcept(order, extent, generator);

        if (extent.equals(order.getConceptExtent(generator))) {
            return generator;
        }

        ArrayList<Integer> newChildren = new ArrayList<>();
        Set<Integer> lowerCover = order.getLowerCoverSet(generator);

        for (int candidate : lowerCover) {
            if (!order.getConceptExtent(candidate).containsAll(extent)) {
                ISet intersection = extent.newIntersect(order.getConceptExtent(candidate));
                candidate = addExtent(order, factory, intersection, candidate);
            }

            boolean addChild = true;
            ISet candidateExt = order.getConceptExtent(candidate);

            Iterator<Integer> it = newChildren.iterator();
            while (it.hasNext()) {
                int existing = it.next();
                ISet existingExt = order.getConceptExtent(existing);

                if (existingExt.containsAll(candidateExt)) {
                    addChild = false;
                    break;
                }
                if (candidateExt.containsAll(existingExt)) {
                    it.remove();
                }
            }

            if (addChild) {
                newChildren.add(candidate);
            }
        }

        int newConcept = order.addConcept(
                factory.clone(extent),
                factory.clone(order.getConceptIntent(generator)));

        for (int child : newChildren) {
            order.removePrecedenceConnection(child, generator);
            order.addPrecedenceConnection(child, newConcept);
        }
        order.addPrecedenceConnection(newConcept, generator);

        return newConcept;
    }

    static int getSmallestContainingConcept(ConceptOrder order, ISet extent, int generator) {
        boolean moved = true;
        while (moved) {
            moved = false;
            for (int child : order.getLowerCoverSet(generator)) {
                if (order.getConceptExtent(child).containsAll(extent)) {
                    generator = child;
                    moved = true;
                    break;
                }
            }
        }
        return generator;
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    static void recomputeReducedExtents(ConceptOrder order, ISetFactory factory) {
        for (Iterator<Integer> it = order.getBasicIterator(); it.hasNext(); ) {
            int concept = it.next();
            ISet rExtent = factory.clone(order.getConceptExtent(concept));
            for (int child : order.getLowerCoverSet(concept)) {
                rExtent.removeAll(order.getConceptExtent(child));
            }
            order.setReducedExtent(concept, rExtent);
        }
    }

    static void clearIntents(ConceptOrder order) {
        for (Iterator<Integer> it = order.getBasicIterator(); it.hasNext(); ) {
            int concept = it.next();
            ISet intent = order.getConceptIntent(concept);
            intent.clear(intent.capacity());
        }
    }

    static IBinaryContext createSubContext(IBinaryContext ctx, ISetFactory factory, int[] attrIndices) {
        int nbObjects = ctx.getObjectCount();

        BinaryContext subCtx = new BinaryContext(nbObjects, 0, "sub", factory);
        for (int i = 0; i < nbObjects; i++) {
            subCtx.addObjectName(ctx.getObjectName(i));
        }
        for (int globalAttr : attrIndices) {
            subCtx.addAttribute(ctx.getAttributeName(globalAttr), ctx.getExtent(globalAttr));
        }

        return subCtx;
    }
}
