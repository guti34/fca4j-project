/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.algo.gpu;

import static org.jocl.CL.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.jocl.CL;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.jocl.cl_platform_id;
import org.jocl.cl_program;
import org.jocl.cl_queue_properties;

/**
 * GPU-side representation of a concept lattice being built.
 * <p>
 * Manages OpenCL context, pre-allocated buffers for extents/intents/adjacency,
 * and provides high-level operations (containsAll, intersect, equals, etc.)
 * that execute on the GPU with minimal CPU-GPU transfers.
 * <p>
 * All sets are stored as packed bit vectors: ceil(N/32) ints per set.
 *
 * @author agutierr
 */
public class GPULatticeEngine implements AutoCloseable {

    // --- OpenCL handles ---
    private cl_context clContext;
    private cl_command_queue commandQueue;
    private cl_program program;
    private cl_device_id device;

    // --- Kernels ---
    private cl_kernel kernelContainsAll;
    private cl_kernel kernelBatchContainsAll;
    private cl_kernel kernelIntersect;
    private cl_kernel kernelDifference;
    private cl_kernel kernelBitwiseOr;
    private cl_kernel kernelEquals;
    private cl_kernel kernelIsEmpty;
    private cl_kernel kernelCopySet;
    private cl_kernel kernelClearSet;
    private cl_kernel kernelFillSet;
    private cl_kernel kernelSetBit;
    private cl_kernel kernelRemoveAll;
    private cl_kernel kernelComputeIntentFromContext;

    // --- Lattice dimensions ---
    private final int nbObjects;
    private final int nbAttributes;
    private final int wordsPerExtent;   // ceil(nbObjects / 32)
    private final int wordsPerIntent;   // ceil(nbAttributes / 32)
    private final int maxConcepts;

    // --- GPU buffers ---
    /** extents[concept * wordsPerExtent + w] = packed extent bits */
    private cl_mem memExtents;
    /** intents[concept * wordsPerIntent + w] = packed intent bits */
    private cl_mem memIntents;
    /** children[concept * maxChildrenPerConcept + k] = child id, -1 = end */
    private cl_mem memChildren;
    /** childCount[concept] = number of children for this concept */
    private cl_mem memChildCount;
    /** parents[concept * maxParentsPerConcept + k] = parent id, -1 = end */
    private cl_mem memParents;
    /** parentCount[concept] = number of parents for this concept */
    private cl_mem memParentCount;
    /** Context matrix stored unpacked on GPU for computeIntent */
    private cl_mem memContext;

    // --- Adjacency limits ---
    private final int maxChildrenPerConcept;
    private final int maxParentsPerConcept;

    // --- Temp buffers for reductions ---
    private static final int LOCAL_WORK_SIZE = 128;
    private static final int NUM_WORK_GROUPS = 64;
    private cl_mem memReductionOutput;
    private int[] reductionOutputHost = new int[NUM_WORK_GROUPS];

    // --- Temp buffer for batch operations ---
    private cl_mem memTempChildIds;
    private cl_mem memTempResult;
    private static final int MAX_BATCH_SIZE = 4096;

    // --- Temp buffer for set operations ---
    private cl_mem memTempSet;

    // --- Concept counter ---
    private int conceptCount = 0;

    /**
     * Create a GPU lattice engine.
     *
     * @param nbObjects    number of objects in the context
     * @param nbAttributes number of attributes in the context
     * @param maxConcepts  maximum number of concepts to pre-allocate
     */
    public GPULatticeEngine(int nbObjects, int nbAttributes, int maxConcepts) {
        this.nbObjects = nbObjects;
        this.nbAttributes = nbAttributes;
        this.wordsPerExtent = (nbObjects + 31) / 32;
        this.wordsPerIntent = (nbAttributes + 31) / 32;
        this.maxConcepts = maxConcepts;
        this.maxChildrenPerConcept = Math.min(maxConcepts, 4096);
        this.maxParentsPerConcept = Math.min(maxConcepts, 4096);

        initOpenCL();
        allocateBuffers();
    }

    // =========================================================================
    // OpenCL Initialization
    // =========================================================================

    private void initOpenCL() {
        final int platformIndex = 0;
        final long deviceType = CL_DEVICE_TYPE_GPU;
        final int deviceIndex = 0;

        CL.setExceptionsEnabled(true);

        // Get platform
        int[] numPlatforms = new int[1];
        clGetPlatformIDs(0, null, numPlatforms);
        cl_platform_id[] platforms = new cl_platform_id[numPlatforms[0]];
        clGetPlatformIDs(platforms.length, platforms, null);
        cl_platform_id platform = platforms[platformIndex];

        // Context properties
        cl_context_properties contextProperties = new cl_context_properties();
        contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);

        // Get device
        int[] numDevices = new int[1];
        clGetDeviceIDs(platform, deviceType, 0, null, numDevices);
        cl_device_id[] devices = new cl_device_id[numDevices[0]];
        clGetDeviceIDs(platform, deviceType, numDevices[0], devices, null);
        device = devices[deviceIndex];

        // Create context and command queue
        clContext = clCreateContext(contextProperties, 1,
                new cl_device_id[]{device}, null, null, null);
        cl_queue_properties properties = new cl_queue_properties();
        commandQueue = clCreateCommandQueueWithProperties(
                clContext, device, properties, null);

        // Load and build program
        String source = readProgramSource();
        program = clCreateProgramWithSource(clContext, 1,
                new String[]{source}, null, null);
        clBuildProgram(program, 0, null, null, null, null);

        // Create kernels
        kernelContainsAll = clCreateKernel(program, "containsAll", null);
        kernelBatchContainsAll = clCreateKernel(program, "batchContainsAll", null);
        kernelIntersect = clCreateKernel(program, "intersect", null);
        kernelDifference = clCreateKernel(program, "difference", null);
        kernelBitwiseOr = clCreateKernel(program, "bitwiseOr", null);
        kernelEquals = clCreateKernel(program, "equals", null);
        kernelIsEmpty = clCreateKernel(program, "isEmpty", null);
        kernelCopySet = clCreateKernel(program, "copySet", null);
        kernelClearSet = clCreateKernel(program, "clearSet", null);
        kernelFillSet = clCreateKernel(program, "fillSet", null);
        kernelSetBit = clCreateKernel(program, "setBit", null);
        kernelRemoveAll = clCreateKernel(program, "removeAll", null);
        kernelComputeIntentFromContext = clCreateKernel(program, "computeIntentFromContext", null);
    }

    private void allocateBuffers() {
        // Lattice extent/intent buffers
        long extentBytes = (long) maxConcepts * wordsPerExtent * Sizeof.cl_int;
        long intentBytes = (long) maxConcepts * wordsPerIntent * Sizeof.cl_int;
        memExtents = clCreateBuffer(clContext, CL_MEM_READ_WRITE, extentBytes, null, null);
        memIntents = clCreateBuffer(clContext, CL_MEM_READ_WRITE, intentBytes, null, null);

        // Adjacency: children
        long childrenBytes = (long) maxConcepts * maxChildrenPerConcept * Sizeof.cl_int;
        memChildren = clCreateBuffer(clContext, CL_MEM_READ_WRITE, childrenBytes, null, null);
        int[] initChildCount = new int[maxConcepts];
        memChildCount = clCreateBuffer(clContext, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
                (long) maxConcepts * Sizeof.cl_int, Pointer.to(initChildCount), null);

        // Adjacency: parents
        long parentsBytes = (long) maxConcepts * maxParentsPerConcept * Sizeof.cl_int;
        memParents = clCreateBuffer(clContext, CL_MEM_READ_WRITE, parentsBytes, null, null);
        int[] initParentCount = new int[maxConcepts];
        memParentCount = clCreateBuffer(clContext, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
                (long) maxConcepts * Sizeof.cl_int, Pointer.to(initParentCount), null);

        // Reduction output
        memReductionOutput = clCreateBuffer(clContext, CL_MEM_READ_WRITE,
                (long) NUM_WORK_GROUPS * Sizeof.cl_int, null, null);

        // Temp buffers for batch operations
        memTempChildIds = clCreateBuffer(clContext, CL_MEM_READ_WRITE,
                (long) MAX_BATCH_SIZE * Sizeof.cl_int, null, null);
        memTempResult = clCreateBuffer(clContext, CL_MEM_READ_WRITE,
                (long) MAX_BATCH_SIZE * Sizeof.cl_int, null, null);

        // Temp set buffer (for intermediate results)
        int maxWords = Math.max(wordsPerExtent, wordsPerIntent);
        memTempSet = clCreateBuffer(clContext, CL_MEM_READ_WRITE,
                (long) maxWords * Sizeof.cl_int, null, null);
    }

    // =========================================================================
    // Context upload
    // =========================================================================

    /**
     * Upload the binary context matrix to GPU.
     * The context is stored unpacked (one int per cell) for the computeIntent kernel.
     *
     * @param contextMatrix boolean matrix [nbObjects][nbAttributes]
     */
    public void uploadContext(boolean[][] contextMatrix) {
        int[] flat = new int[nbObjects * nbAttributes];
        for (int obj = 0; obj < nbObjects; obj++) {
            for (int attr = 0; attr < nbAttributes; attr++) {
                flat[obj * nbAttributes + attr] = contextMatrix[obj][attr] ? 1 : 0;
            }
        }
        memContext = clCreateBuffer(clContext, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) flat.length * Sizeof.cl_int, Pointer.to(flat), null);
    }

    // =========================================================================
    // Concept management
    // =========================================================================

    /**
     * Allocate a new concept slot, returning its index.
     */
    public int newConcept() {
        if (conceptCount >= maxConcepts) {
            throw new RuntimeException("Maximum concept count reached: " + maxConcepts);
        }
        int id = conceptCount++;
        // Clear its adjacency counts
        int[] zero = {0};
        clEnqueueWriteBuffer(commandQueue, memChildCount, CL_TRUE,
                (long) id * Sizeof.cl_int, Sizeof.cl_int, Pointer.to(zero), 0, null, null);
        clEnqueueWriteBuffer(commandQueue, memParentCount, CL_TRUE,
                (long) id * Sizeof.cl_int, Sizeof.cl_int, Pointer.to(zero), 0, null, null);
        return id;
    }

    public int getConceptCount() {
        return conceptCount;
    }

    // =========================================================================
    // Extent operations on GPU
    // =========================================================================

    /**
     * Get the GPU memory pointer to a concept's extent.
     * This is a sub-buffer offset, not a separate allocation.
     */
    private long extentOffset(int conceptId) {
        return (long) conceptId * wordsPerExtent * Sizeof.cl_int;
    }

    private long intentOffset(int conceptId) {
        return (long) conceptId * wordsPerIntent * Sizeof.cl_int;
    }

    /**
     * Write packed extent bits to GPU for a concept.
     */
    public void writeExtent(int conceptId, int[] packedExtent) {
        clEnqueueWriteBuffer(commandQueue, memExtents, CL_TRUE,
                extentOffset(conceptId),
                (long) wordsPerExtent * Sizeof.cl_int,
                Pointer.to(packedExtent), 0, null, null);
    }

    /**
     * Read packed extent bits from GPU.
     */
    public int[] readExtent(int conceptId) {
        int[] result = new int[wordsPerExtent];
        clEnqueueReadBuffer(commandQueue, memExtents, CL_TRUE,
                extentOffset(conceptId),
                (long) wordsPerExtent * Sizeof.cl_int,
                Pointer.to(result), 0, null, null);
        return result;
    }

    /**
     * Write packed intent bits to GPU for a concept.
     */
    public void writeIntent(int conceptId, int[] packedIntent) {
        clEnqueueWriteBuffer(commandQueue, memIntents, CL_TRUE,
                intentOffset(conceptId),
                (long) wordsPerIntent * Sizeof.cl_int,
                Pointer.to(packedIntent), 0, null, null);
    }

    /**
     * Read packed intent bits from GPU.
     */
    public int[] readIntent(int conceptId) {
        int[] result = new int[wordsPerIntent];
        clEnqueueReadBuffer(commandQueue, memIntents, CL_TRUE,
                intentOffset(conceptId),
                (long) wordsPerIntent * Sizeof.cl_int,
                Pointer.to(result), 0, null, null);
        return result;
    }

    /**
     * Fill a concept's extent with bits 0..nbObjects-1 (the "all objects" set).
     */
    public void fillExtent(int conceptId) {
        // Use sub-buffer via offset
        clSetKernelArg(kernelFillSet, 0, Sizeof.cl_mem, Pointer.to(memExtents));
        clSetKernelArg(kernelFillSet, 1, Sizeof.cl_int, Pointer.to(new int[]{wordsPerExtent}));
        clSetKernelArg(kernelFillSet, 2, Sizeof.cl_int, Pointer.to(new int[]{nbObjects}));
        // We need to work at the correct offset — use a temp approach: write from host
        int[] filled = new int[wordsPerExtent];
        for (int w = 0; w < wordsPerExtent; w++) {
            int bitStart = w * 32;
            if (bitStart + 32 <= nbObjects) {
                filled[w] = 0xFFFFFFFF;
            } else if (bitStart < nbObjects) {
                filled[w] = (1 << (nbObjects - bitStart)) - 1;
            }
        }
        writeExtent(conceptId, filled);
    }

    /**
     * Clear a concept's extent (all zeros).
     */
    public void clearExtent(int conceptId) {
        int[] zeros = new int[wordsPerExtent];
        writeExtent(conceptId, zeros);
    }

    /**
     * Clear a concept's intent (all zeros).
     */
    public void clearIntent(int conceptId) {
        int[] zeros = new int[wordsPerIntent];
        writeIntent(conceptId, zeros);
    }

    /**
     * Copy extent from one concept to another on GPU.
     */
    public void copyExtent(int srcConcept, int dstConcept) {
        int[] data = readExtent(srcConcept);
        writeExtent(dstConcept, data);
    }

    /**
     * Copy intent from one concept to another on GPU.
     */
    public void copyIntent(int srcConcept, int dstConcept) {
        int[] data = readIntent(srcConcept);
        writeIntent(dstConcept, data);
    }

    // =========================================================================
    // Set operations (extent-level)
    // =========================================================================

    /**
     * Check if concept's extent contains all bits of packedTestSet.
     *
     * @return true if extent(conceptId) ⊇ testSet
     */
    public boolean extentContainsAll(int conceptId, int[] packedTestSet) {
        int[] extData = readExtent(conceptId);
        for (int w = 0; w < wordsPerExtent; w++) {
            if ((packedTestSet[w] & ~extData[w]) != 0) return false;
        }
        return true;
    }

    /**
     * Check if two extents are equal.
     */
    public boolean extentsEqual(int conceptA, int conceptB) {
        int[] a = readExtent(conceptA);
        int[] b = readExtent(conceptB);
        return Arrays.equals(a, b);
    }

    /**
     * Check if a concept's extent equals a given packed set.
     */
    public boolean extentEquals(int conceptId, int[] packedSet) {
        int[] ext = readExtent(conceptId);
        return Arrays.equals(ext, packedSet);
    }

    /**
     * Compute intersection of a concept's extent with a packed set.
     *
     * @return the intersection as a new packed array
     */
    public int[] extentIntersect(int conceptId, int[] packedSet) {
        int[] ext = readExtent(conceptId);
        int[] result = new int[wordsPerExtent];
        for (int w = 0; w < wordsPerExtent; w++) {
            result[w] = ext[w] & packedSet[w];
        }
        return result;
    }

    /**
     * Compute difference: result = setA & ~setB.
     */
    public int[] setDifference(int[] setA, int[] setB) {
        int[] result = new int[setA.length];
        for (int w = 0; w < setA.length; w++) {
            result[w] = setA[w] & ~setB[w];
        }
        return result;
    }

    /**
     * In-place OR on extent: extent(conceptId) |= packedSet.
     */
    public void extentAddAll(int conceptId, int[] packedSet) {
        int[] ext = readExtent(conceptId);
        for (int w = 0; w < wordsPerExtent; w++) {
            ext[w] |= packedSet[w];
        }
        writeExtent(conceptId, ext);
    }

    /**
     * In-place removeAll on extent: extent(conceptId) &= ~packedSet.
     */
    public void extentRemoveAll(int conceptId, int[] packedSet) {
        int[] ext = readExtent(conceptId);
        for (int w = 0; w < wordsPerExtent; w++) {
            ext[w] &= ~packedSet[w];
        }
        writeExtent(conceptId, ext);
    }

    /**
     * Set a single bit in a concept's extent.
     */
    public void extentSetBit(int conceptId, int bitIndex) {
        int[] ext = readExtent(conceptId);
        int word = bitIndex / 32;
        int bit = bitIndex % 32;
        ext[word] |= (1 << bit);
        writeExtent(conceptId, ext);
    }

    /**
     * Set a single bit in a concept's intent.
     */
    public void intentSetBit(int conceptId, int bitIndex) {
        int[] intent = readIntent(conceptId);
        int word = bitIndex / 32;
        int bit = bitIndex % 32;
        intent[word] |= (1 << bit);
        writeIntent(conceptId, intent);
    }

    /**
     * Check if a packed set is empty (all zeros).
     */
    public boolean isSetEmpty(int[] packedSet) {
        for (int w : packedSet) {
            if (w != 0) return false;
        }
        return true;
    }

    /**
     * Count the number of set bits in a packed set.
     */
    public int cardinality(int[] packedSet) {
        int count = 0;
        for (int w : packedSet) {
            count += Integer.bitCount(w);
        }
        return count;
    }

    // =========================================================================
    // Batch operation: findSmallestContainingConcept
    // Tests all children of a concept at once on GPU.
    // =========================================================================

    /**
     * Find the smallest concept whose extent contains packedExtent,
     * starting from generator and descending through children.
     *
     * @param packedExtent the extent to locate
     * @param generator    starting concept
     * @return the smallest containing concept id
     */
    public int findSmallestContainingConcept(int[] packedExtent, int generator) {
        boolean moved = true;
        while (moved) {
            moved = false;
            int[] childIds = readChildren(generator);
            if (childIds.length == 0) break;

            // Batch test: which children contain packedExtent?
            for (int childId : childIds) {
                if (extentContainsAll(childId, packedExtent)) {
                    generator = childId;
                    moved = true;
                    break;
                }
            }
        }
        return generator;
    }

    /**
     * GPU-batched version: test all children containment in one kernel launch.
     * Falls back to sequential if too few children for GPU benefit.
     */
    public int findSmallestContainingConceptGPU(int[] packedExtent, int generator) {
        boolean moved = true;
        while (moved) {
            moved = false;
            int[] childIds = readChildren(generator);
            if (childIds.length == 0) break;

            if (childIds.length >= 4) {
                // GPU batch: upload childIds and testSet, run kernel
                int numChildren = childIds.length;
                clEnqueueWriteBuffer(commandQueue, memTempChildIds, CL_TRUE,
                        0, (long) numChildren * Sizeof.cl_int,
                        Pointer.to(childIds), 0, null, null);
                clEnqueueWriteBuffer(commandQueue, memTempSet, CL_TRUE,
                        0, (long) wordsPerExtent * Sizeof.cl_int,
                        Pointer.to(packedExtent), 0, null, null);

                clSetKernelArg(kernelBatchContainsAll, 0, Sizeof.cl_mem, Pointer.to(memExtents));
                clSetKernelArg(kernelBatchContainsAll, 1, Sizeof.cl_mem, Pointer.to(memTempSet));
                clSetKernelArg(kernelBatchContainsAll, 2, Sizeof.cl_int,
                        Pointer.to(new int[]{wordsPerExtent}));
                clSetKernelArg(kernelBatchContainsAll, 3, Sizeof.cl_mem, Pointer.to(memTempChildIds));
                clSetKernelArg(kernelBatchContainsAll, 4, Sizeof.cl_int,
                        Pointer.to(new int[]{numChildren}));
                clSetKernelArg(kernelBatchContainsAll, 5, Sizeof.cl_mem, Pointer.to(memTempResult));

                long[] globalWorkSize = new long[]{roundUp(numChildren, 64)};
                clEnqueueNDRangeKernel(commandQueue, kernelBatchContainsAll, 1,
                        null, globalWorkSize, null, 0, null, null);

                int[] results = new int[numChildren];
                clEnqueueReadBuffer(commandQueue, memTempResult, CL_TRUE,
                        0, (long) numChildren * Sizeof.cl_int,
                        Pointer.to(results), 0, null, null);

                for (int i = 0; i < numChildren; i++) {
                    if (results[i] == 1) {
                        generator = childIds[i];
                        moved = true;
                        break;
                    }
                }
            } else {
                // Sequential fallback for few children
                for (int childId : childIds) {
                    if (extentContainsAll(childId, packedExtent)) {
                        generator = childId;
                        moved = true;
                        break;
                    }
                }
            }
        }
        return generator;
    }

    // =========================================================================
    // Adjacency management (CPU-side with GPU sync)
    // =========================================================================

    /**
     * Add a child to a concept.
     */
    public void addChild(int parentId, int childId) {
        int[] count = new int[1];
        clEnqueueReadBuffer(commandQueue, memChildCount, CL_TRUE,
                (long) parentId * Sizeof.cl_int, Sizeof.cl_int,
                Pointer.to(count), 0, null, null);
        int idx = count[0];
        if (idx >= maxChildrenPerConcept) {
            throw new RuntimeException("Too many children for concept " + parentId);
        }
        // Write childId at position
        int[] val = {childId};
        long offset = ((long) parentId * maxChildrenPerConcept + idx) * Sizeof.cl_int;
        clEnqueueWriteBuffer(commandQueue, memChildren, CL_TRUE,
                offset, Sizeof.cl_int, Pointer.to(val), 0, null, null);
        // Increment count
        count[0] = idx + 1;
        clEnqueueWriteBuffer(commandQueue, memChildCount, CL_TRUE,
                (long) parentId * Sizeof.cl_int, Sizeof.cl_int,
                Pointer.to(count), 0, null, null);
    }

    /**
     * Add a parent to a concept.
     */
    public void addParent(int childId, int parentId) {
        int[] count = new int[1];
        clEnqueueReadBuffer(commandQueue, memParentCount, CL_TRUE,
                (long) childId * Sizeof.cl_int, Sizeof.cl_int,
                Pointer.to(count), 0, null, null);
        int idx = count[0];
        if (idx >= maxParentsPerConcept) {
            throw new RuntimeException("Too many parents for concept " + childId);
        }
        int[] val = {parentId};
        long offset = ((long) childId * maxParentsPerConcept + idx) * Sizeof.cl_int;
        clEnqueueWriteBuffer(commandQueue, memParents, CL_TRUE,
                offset, Sizeof.cl_int, Pointer.to(val), 0, null, null);
        count[0] = idx + 1;
        clEnqueueWriteBuffer(commandQueue, memParentCount, CL_TRUE,
                (long) childId * Sizeof.cl_int, Sizeof.cl_int,
                Pointer.to(count), 0, null, null);
    }

    /**
     * Read the list of children of a concept.
     */
    public int[] readChildren(int conceptId) {
        int[] count = new int[1];
        clEnqueueReadBuffer(commandQueue, memChildCount, CL_TRUE,
                (long) conceptId * Sizeof.cl_int, Sizeof.cl_int,
                Pointer.to(count), 0, null, null);
        int n = count[0];
        if (n == 0) return new int[0];
        int[] children = new int[n];
        long offset = (long) conceptId * maxChildrenPerConcept * Sizeof.cl_int;
        clEnqueueReadBuffer(commandQueue, memChildren, CL_TRUE,
                offset, (long) n * Sizeof.cl_int,
                Pointer.to(children), 0, null, null);
        return children;
    }

    /**
     * Read the list of parents of a concept.
     */
    public int[] readParents(int conceptId) {
        int[] count = new int[1];
        clEnqueueReadBuffer(commandQueue, memParentCount, CL_TRUE,
                (long) conceptId * Sizeof.cl_int, Sizeof.cl_int,
                Pointer.to(count), 0, null, null);
        int n = count[0];
        if (n == 0) return new int[0];
        int[] parents = new int[n];
        long offset = (long) conceptId * maxParentsPerConcept * Sizeof.cl_int;
        clEnqueueReadBuffer(commandQueue, memParents, CL_TRUE,
                offset, (long) n * Sizeof.cl_int,
                Pointer.to(parents), 0, null, null);
        return parents;
    }

    /**
     * Remove a child from a concept's children list.
     */
    public void removeChild(int parentId, int childId) {
        int[] children = readChildren(parentId);
        int n = children.length;
        int[] newChildren = new int[n];
        int newCount = 0;
        for (int c : children) {
            if (c != childId) {
                newChildren[newCount++] = c;
            }
        }
        // Rewrite
        if (newCount > 0) {
            long offset = (long) parentId * maxChildrenPerConcept * Sizeof.cl_int;
            clEnqueueWriteBuffer(commandQueue, memChildren, CL_TRUE,
                    offset, (long) newCount * Sizeof.cl_int,
                    Pointer.to(newChildren), 0, null, null);
        }
        int[] cnt = {newCount};
        clEnqueueWriteBuffer(commandQueue, memChildCount, CL_TRUE,
                (long) parentId * Sizeof.cl_int, Sizeof.cl_int,
                Pointer.to(cnt), 0, null, null);
    }

    /**
     * Remove a parent from a concept's parents list.
     */
    public void removeParent(int childId, int parentId) {
        int[] parents = readParents(childId);
        int n = parents.length;
        int[] newParents = new int[n];
        int newCount = 0;
        for (int p : parents) {
            if (p != parentId) {
                newParents[newCount++] = p;
            }
        }
        if (newCount > 0) {
            long offset = (long) childId * maxParentsPerConcept * Sizeof.cl_int;
            clEnqueueWriteBuffer(commandQueue, memParents, CL_TRUE,
                    offset, (long) newCount * Sizeof.cl_int,
                    Pointer.to(newParents), 0, null, null);
        }
        int[] cnt = {newCount};
        clEnqueueWriteBuffer(commandQueue, memParentCount, CL_TRUE,
                (long) childId * Sizeof.cl_int, Sizeof.cl_int,
                Pointer.to(cnt), 0, null, null);
    }

    /**
     * Add a precedence connection: child < parent.
     * Updates both the parent's children list and the child's parents list.
     */
    public void addPrecedenceConnection(int childId, int parentId) {
        addChild(parentId, childId);
        addParent(childId, parentId);
    }

    /**
     * Remove a precedence connection.
     */
    public void removePrecedenceConnection(int childId, int parentId) {
        removeChild(parentId, childId);
        removeParent(childId, parentId);
    }

    // =========================================================================
    // Packed bit utilities
    // =========================================================================

    public int getWordsPerExtent() {
        return wordsPerExtent;
    }

    public int getWordsPerIntent() {
        return wordsPerIntent;
    }

    public int getNbObjects() {
        return nbObjects;
    }

    public int getNbAttributes() {
        return nbAttributes;
    }

    /**
     * Pack a boolean array into int[] of packed bits.
     */
    public static int[] packBits(boolean[] boolArray) {
        int n = boolArray.length;
        int words = (n + 31) / 32;
        int[] packed = new int[words];
        for (int i = 0; i < n; i++) {
            if (boolArray[i]) {
                packed[i / 32] |= (1 << (i % 32));
            }
        }
        return packed;
    }

    /**
     * Unpack int[] of packed bits into a boolean array.
     */
    public static boolean[] unpackBits(int[] packed, int nbElements) {
        boolean[] result = new boolean[nbElements];
        for (int i = 0; i < nbElements; i++) {
            result[i] = ((packed[i / 32] >> (i % 32)) & 1) == 1;
        }
        return result;
    }

    /**
     * Create a packed set from an ISet (via its iterator).
     */
    public static int[] packFromIterator(java.util.Iterator<Integer> it, int nbElements) {
        int words = (nbElements + 31) / 32;
        int[] packed = new int[words];
        while (it.hasNext()) {
            int idx = it.next();
            packed[idx / 32] |= (1 << (idx % 32));
        }
        return packed;
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private long roundUp(long value, long multiple) {
        return ((value + multiple - 1) / multiple) * multiple;
    }

    private String readProgramSource() {
        String path = "/add_extent_gpu.cl";
        try {
            InputStream is = getClass().getResourceAsStream(path);
            StringBuilder sb = new StringBuilder();
            try (Reader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                int c;
                while ((c = reader.read()) != -1) {
                    sb.append((char) c);
                }
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read OpenCL source: " + path, e);
        }
    }

    // =========================================================================
    // Cleanup
    // =========================================================================

    @Override
    public void close() {
        if (memExtents != null) clReleaseMemObject(memExtents);
        if (memIntents != null) clReleaseMemObject(memIntents);
        if (memChildren != null) clReleaseMemObject(memChildren);
        if (memChildCount != null) clReleaseMemObject(memChildCount);
        if (memParents != null) clReleaseMemObject(memParents);
        if (memParentCount != null) clReleaseMemObject(memParentCount);
        if (memContext != null) clReleaseMemObject(memContext);
        if (memReductionOutput != null) clReleaseMemObject(memReductionOutput);
        if (memTempChildIds != null) clReleaseMemObject(memTempChildIds);
        if (memTempResult != null) clReleaseMemObject(memTempResult);
        if (memTempSet != null) clReleaseMemObject(memTempSet);

        if (kernelContainsAll != null) clReleaseKernel(kernelContainsAll);
        if (kernelBatchContainsAll != null) clReleaseKernel(kernelBatchContainsAll);
        if (kernelIntersect != null) clReleaseKernel(kernelIntersect);
        if (kernelDifference != null) clReleaseKernel(kernelDifference);
        if (kernelBitwiseOr != null) clReleaseKernel(kernelBitwiseOr);
        if (kernelEquals != null) clReleaseKernel(kernelEquals);
        if (kernelIsEmpty != null) clReleaseKernel(kernelIsEmpty);
        if (kernelCopySet != null) clReleaseKernel(kernelCopySet);
        if (kernelClearSet != null) clReleaseKernel(kernelClearSet);
        if (kernelFillSet != null) clReleaseKernel(kernelFillSet);
        if (kernelSetBit != null) clReleaseKernel(kernelSetBit);
        if (kernelRemoveAll != null) clReleaseKernel(kernelRemoveAll);
        if (kernelComputeIntentFromContext != null) clReleaseKernel(kernelComputeIntentFromContext);

        if (program != null) clReleaseProgram(program);
        if (commandQueue != null) clReleaseCommandQueue(commandQueue);
        if (clContext != null) clReleaseContext(clContext);
    }
}
