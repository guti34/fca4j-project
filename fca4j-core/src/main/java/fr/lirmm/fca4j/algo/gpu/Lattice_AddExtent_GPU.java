/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.algo.gpu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

import fr.lirmm.fca4j.algo.AbstractAlgo;
import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

/**
 * AddExtent using packed bit vectors for set operations.
 * <p>
 * This version eliminates the GPU overhead for the recursive lattice
 * construction by keeping everything in CPU memory with packed int[] arrays.
 * Packed bits provide a 32x improvement over one-element-per-int arrays.
 * <p>
 * The adjacency (children/parents) is managed by ConceptOrder (JGraphT).
 * Only the extent data uses packed bit vectors stored in a flat array.
 * <p>
 * Architecture:
 * <ul>
 *   <li>extents stored as packed int[] in a growable array (CPU)</li>
 *   <li>adjacency managed by ConceptOrder (CPU, JGraphT)</li>
 *   <li>GPU reserved for future batch operations on very large contexts</li>
 * </ul>
 *
 * @author agutierr
 */
public class Lattice_AddExtent_GPU implements AbstractAlgo<ConceptOrder> {

    private final IBinaryContext matrix;
    private final ISetFactory factory;
    private final Chrono chrono;

    private ConceptOrder order;

    // --- Packed extent storage ---
    // packedExtents[conceptId] = int[] of packed bits for the extent
    private int[][] packedExtents;
    private int conceptCapacity;
    private final int wordsPerExtent;

    // --- Packed context extents (attribute -> packed extent) ---
    private int[][] contextExtents;

    public Lattice_AddExtent_GPU(IBinaryContext matrix, Chrono chrono) {
        this.matrix = matrix;
        this.factory = matrix.getFactory();
        this.chrono = chrono;
        this.wordsPerExtent = (matrix.getObjectCount() + 31) / 32;
    }

    public Lattice_AddExtent_GPU(IBinaryContext matrix) {
        this(matrix, null);
    }

    @Override
    public String getDescription() {
        return "AddExtent_GPU";
    }

    @Override
    public ConceptOrder getResult() {
        return order;
    }

    @Override
    public void run() {
        try {
            // Initial capacity — will grow as needed
            conceptCapacity = Math.max(1024, matrix.getAttributeCount() * 4);
            packedExtents = new int[conceptCapacity][];

            // Pre-pack context extents (one per attribute)
            contextExtents = new int[matrix.getAttributeCount()][];
            for (int attr = 0; attr < matrix.getAttributeCount(); attr++) {
                contextExtents[attr] = packISet(matrix.getExtent(attr));
            }

            // Create ConceptOrder
            order = new ConceptOrder("LatticeWithAddExtentGPU", matrix, getDescription());

            // -------------------------------------------------------
            // Top concept: extent = all objects
            // -------------------------------------------------------
            ISet allObjects = factory.createSet(matrix.getObjectCount());
            allObjects.fill(matrix.getObjectCount());
            int top = order.addConcept(allObjects, factory.createSet());
            storePackedExtent(top, createFullExtent());

            // -------------------------------------------------------
            // Insert each attribute's extent
            // -------------------------------------------------------
            for (int numAttr = 0; numAttr < matrix.getAttributeCount(); numAttr++) {
                int concept = addExtent(contextExtents[numAttr], top);
                order.getConceptReducedIntent(concept).add(numAttr);
            }

            // -------------------------------------------------------
            // Compute reduced extents
            // -------------------------------------------------------
            for (Iterator<Integer> it = order.getBasicIterator(); it.hasNext(); ) {
                int concept = it.next();
                int[] rExtent = packedExtents[concept].clone();

                for (int child : order.getLowerCoverSet(concept)) {
                    removeAll(rExtent, packedExtents[child]);
                }

                ISet rExtentISet = order.getConceptReducedExtent(concept);
                unpackInto(rExtent, rExtentISet);
            }

            // -------------------------------------------------------
            // Compute full intents
            // -------------------------------------------------------
            order.computeIntents();

            // Free packed extents (no longer needed)
            packedExtents = null;
            contextExtents = null;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =====================================================================
    // Core recursive addExtent
    // =====================================================================

    private int addExtent(int[] extent, int generator) throws Exception {
        // Descend to the smallest concept whose extent contains our extent
        generator = findSmallestContaining(extent, generator);

        // If extent matches exactly, no new concept needed
        if (Arrays.equals(extent, packedExtents[generator])) {
            return generator;
        }

        // Process children
        ArrayList<Integer> newChildren = new ArrayList<>();
        Set<Integer> lowerCover = order.getLowerCoverSet(generator);

        for (int candidate : lowerCover) {
            if (!containsAll(packedExtents[candidate], extent)) {
                // Intersection
                int[] intersection = intersect(extent, packedExtents[candidate]);
                candidate = addExtent(intersection, candidate);
            }

            boolean addChild = true;
            ArrayList<Integer> toRemove = null;

            for (int i = 0; i < newChildren.size(); i++) {
                int existing = newChildren.get(i);
                int[] existingExt = packedExtents[existing];
                int[] candidateExt = packedExtents[candidate];

                if (containsAll(existingExt, candidateExt)) {
                    addChild = false;
                    break;
                }
                if (containsAll(candidateExt, existingExt)) {
                    if (toRemove == null) toRemove = new ArrayList<>();
                    toRemove.add(existing);
                }
            }

            if (toRemove != null) newChildren.removeAll(toRemove);
            if (addChild) {
                newChildren.add(candidate);
            }
        }

        // Create new concept
        ISet extentISet = unpackToISet(extent);
        ISet intentISet = factory.clone(order.getConceptIntent(generator));
        int newConcept = order.addConcept(extentISet, intentISet);
        storePackedExtent(newConcept, extent.clone());

        // Rewire edges
        for (int child : newChildren) {
            order.removePrecedenceConnection(child, generator);
            order.addPrecedenceConnection(child, newConcept);
        }
        order.addPrecedenceConnection(newConcept, generator);

        return newConcept;
    }

    // =====================================================================
    // Find smallest containing concept (descend through children)
    // =====================================================================

    private int findSmallestContaining(int[] extent, int generator) {
        boolean moved = true;
        while (moved) {
            moved = false;
            for (int child : order.getLowerCoverSet(generator)) {
                if (containsAll(packedExtents[child], extent)) {
                    generator = child;
                    moved = true;
                    break;
                }
            }
        }
        return generator;
    }

    // =====================================================================
    // Packed bit operations (CPU, inlined for performance)
    // =====================================================================

    /** Does container ⊇ contained? */
    private boolean containsAll(int[] container, int[] contained) {
        for (int w = 0; w < wordsPerExtent; w++) {
            if ((contained[w] & ~container[w]) != 0) return false;
        }
        return true;
    }

    /** Return A ∩ B */
    private int[] intersect(int[] a, int[] b) {
        int[] result = new int[wordsPerExtent];
        for (int w = 0; w < wordsPerExtent; w++) {
            result[w] = a[w] & b[w];
        }
        return result;
    }

    /** In-place A = A \ B */
    private void removeAll(int[] a, int[] b) {
        for (int w = 0; w < wordsPerExtent; w++) {
            a[w] &= ~b[w];
        }
    }

    // =====================================================================
    // Storage management
    // =====================================================================

    private void storePackedExtent(int conceptId, int[] packed) {
        if (conceptId >= conceptCapacity) {
            growCapacity(conceptId + 1);
        }
        packedExtents[conceptId] = packed;
    }

    private void growCapacity(int minCapacity) {
        int newCapacity = Math.max(minCapacity, conceptCapacity * 2);
        int[][] newArray = new int[newCapacity][];
        System.arraycopy(packedExtents, 0, newArray, 0, conceptCapacity);
        packedExtents = newArray;
        conceptCapacity = newCapacity;
    }

    // =====================================================================
    // Bit packing utilities
    // =====================================================================

    private int[] createFullExtent() {
        int[] packed = new int[wordsPerExtent];
        int nbObjects = matrix.getObjectCount();
        for (int w = 0; w < wordsPerExtent; w++) {
            int bitStart = w * 32;
            if (bitStart + 32 <= nbObjects) {
                packed[w] = 0xFFFFFFFF;
            } else if (bitStart < nbObjects) {
                packed[w] = (1 << (nbObjects - bitStart)) - 1;
            }
        }
        return packed;
    }

    private int[] packISet(ISet set) {
        int[] packed = new int[wordsPerExtent];
        for (Iterator<Integer> it = set.iterator(); it.hasNext(); ) {
            int idx = it.next();
            packed[idx / 32] |= (1 << (idx % 32));
        }
        return packed;
    }

    private ISet unpackToISet(int[] packed) {
        ISet set = factory.createSet(matrix.getObjectCount());
        for (int w = 0; w < wordsPerExtent; w++) {
            int word = packed[w];
            while (word != 0) {
                int bit = Integer.numberOfTrailingZeros(word);
                set.add(w * 32 + bit);
                word &= word - 1; // clear lowest set bit
            }
        }
        return set;
    }

    private void unpackInto(int[] packed, ISet target) {
        for (int w = 0; w < wordsPerExtent; w++) {
            int word = packed[w];
            while (word != 0) {
                int bit = Integer.numberOfTrailingZeros(word);
                target.add(w * 32 + bit);
                word &= word - 1;
            }
        }
    }
}
