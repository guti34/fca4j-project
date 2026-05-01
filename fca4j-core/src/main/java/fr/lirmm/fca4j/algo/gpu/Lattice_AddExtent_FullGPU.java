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
import java.util.Iterator;

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

import fr.lirmm.fca4j.algo.AbstractAlgo;
import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

/**
 * Full GPU AddExtent — the entire algorithm runs in a single OpenCL kernel.
 * <p>
 * Only two CPU-GPU transfers:
 * <ol>
 *   <li>Upload: binary context (attribute extents)</li>
 *   <li>Download: lattice (concept extents, adjacency, reduced intents)</li>
 * </ol>
 * <p>
 * The kernel is single-threaded (one work-item) but runs entirely on GPU memory,
 * eliminating all PCIe round-trips during lattice construction.
 * The addExtent recursion is implemented iteratively with an explicit stack in
 * GPU global memory.
 *
 * @author agutierr
 */
public class Lattice_AddExtent_FullGPU implements AbstractAlgo<ConceptOrder> {

    private final IBinaryContext matrix;
    private final ISetFactory factory;
    private final Chrono chrono;
    private ConceptOrder order;

    // --- Configuration ---
    private static final int MAX_ADJ = 4096;        // max children per concept
    private static final int MAX_STACK_DEPTH = 256;  // max recursion depth

    public Lattice_AddExtent_FullGPU(IBinaryContext matrix, Chrono chrono) {
        this.matrix = matrix;
        this.factory = matrix.getFactory();
        this.chrono = chrono;
    }

    public Lattice_AddExtent_FullGPU(IBinaryContext matrix) {
        this(matrix, null);
    }

    @Override
    public String getDescription() {
        return "AddExtent_FullGPU";
    }

    @Override
    public ConceptOrder getResult() {
        return order;
    }

    @Override
    public void run() {
        int nbObjects = matrix.getObjectCount();
        int nbAttributes = matrix.getAttributeCount();
        int wpe = (nbObjects + 31) / 32;    // words per extent
        int wpi = (nbAttributes + 31) / 32; // words per intent

        // Estimate max concepts
        int maxConcepts = estimateMaxConcepts(nbObjects, nbAttributes);
        int maxAdj = Math.min(maxConcepts, MAX_ADJ);
        int maxStackDepth = MAX_STACK_DEPTH;
        int frameSize = 6 + wpe + maxAdj + maxAdj; // header + extent + childList + newChildren

        // =============================================
        // 1. Pack context extents
        // =============================================
        int[] contextExtents = new int[nbAttributes * wpe];
        for (int attr = 0; attr < nbAttributes; attr++) {
            ISet extent = matrix.getExtent(attr);
            for (Iterator<Integer> it = extent.iterator(); it.hasNext(); ) {
                int idx = it.next();
                contextExtents[attr * wpe + idx / 32] |= (1 << (idx % 32));
            }
        }

        // =============================================
        // 2. Initialize OpenCL
        // =============================================
        CL.setExceptionsEnabled(true);

        int[] numPlatforms = new int[1];
        clGetPlatformIDs(0, null, numPlatforms);
        cl_platform_id[] platforms = new cl_platform_id[numPlatforms[0]];
        clGetPlatformIDs(platforms.length, platforms, null);

        cl_context_properties ctxProps = new cl_context_properties();
        ctxProps.addProperty(CL_CONTEXT_PLATFORM, platforms[0]);

        int[] numDevices = new int[1];
        clGetDeviceIDs(platforms[0], CL_DEVICE_TYPE_GPU, 0, null, numDevices);
        cl_device_id[] devices = new cl_device_id[numDevices[0]];
        clGetDeviceIDs(platforms[0], CL_DEVICE_TYPE_GPU, numDevices[0], devices, null);
        cl_device_id device = devices[0];

        cl_context clContext = clCreateContext(ctxProps, 1,
                new cl_device_id[]{device}, null, null, null);
        cl_command_queue queue = clCreateCommandQueueWithProperties(
                clContext, device, new cl_queue_properties(), null);

        // =============================================
        // 3. Allocate GPU buffers
        // =============================================

        // Context extents (read-only)
        cl_mem memContextExtents = clCreateBuffer(clContext,
                CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) contextExtents.length * Sizeof.cl_int,
                Pointer.to(contextExtents), null);

        // Lattice extents
        cl_mem memExtents = clCreateBuffer(clContext, CL_MEM_READ_WRITE,
                (long) maxConcepts * wpe * Sizeof.cl_int, null, null);

        // Children adjacency
        cl_mem memChildren = clCreateBuffer(clContext, CL_MEM_READ_WRITE,
                (long) maxConcepts * maxAdj * Sizeof.cl_int, null, null);

        // Child counts
        int[] zeroChildCounts = new int[maxConcepts];
        cl_mem memChildCount = clCreateBuffer(clContext,
                CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
                (long) maxConcepts * Sizeof.cl_int,
                Pointer.to(zeroChildCounts), null);

        // Concept count (single int)
        int[] conceptCountInit = {0};
        cl_mem memConceptCount = clCreateBuffer(clContext,
                CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int, Pointer.to(conceptCountInit), null);

        // Reduced intents output
        int[] zeroIntents = new int[maxConcepts * wpi];
        cl_mem memReducedIntents = clCreateBuffer(clContext,
                CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
                (long) maxConcepts * wpi * Sizeof.cl_int,
                Pointer.to(zeroIntents), null);

        // Stack workspace
        long stackBytes = (long) maxStackDepth * frameSize * Sizeof.cl_int;
        cl_mem memStack = clCreateBuffer(clContext, CL_MEM_READ_WRITE,
                stackBytes, null, null);

        // =============================================
        // 4. Build and launch kernel
        // =============================================
        String source = readProgramSource("/add_extent_full_gpu.cl");
        cl_program program = clCreateProgramWithSource(clContext, 1,
                new String[]{source}, null, null);
        clBuildProgram(program, 0, null, null, null, null);

        cl_kernel kernel = clCreateKernel(program, "buildLattice", null);

        int argIdx = 0;
        clSetKernelArg(kernel, argIdx++, Sizeof.cl_mem, Pointer.to(memContextExtents));
        clSetKernelArg(kernel, argIdx++, Sizeof.cl_int, Pointer.to(new int[]{nbAttributes}));
        clSetKernelArg(kernel, argIdx++, Sizeof.cl_int, Pointer.to(new int[]{nbObjects}));
        clSetKernelArg(kernel, argIdx++, Sizeof.cl_int, Pointer.to(new int[]{wpe}));
        clSetKernelArg(kernel, argIdx++, Sizeof.cl_mem, Pointer.to(memExtents));
        clSetKernelArg(kernel, argIdx++, Sizeof.cl_mem, Pointer.to(memChildren));
        clSetKernelArg(kernel, argIdx++, Sizeof.cl_mem, Pointer.to(memChildCount));
        clSetKernelArg(kernel, argIdx++, Sizeof.cl_mem, Pointer.to(memConceptCount));
        clSetKernelArg(kernel, argIdx++, Sizeof.cl_mem, Pointer.to(memReducedIntents));
        clSetKernelArg(kernel, argIdx++, Sizeof.cl_int, Pointer.to(new int[]{wpi}));
        clSetKernelArg(kernel, argIdx++, Sizeof.cl_mem, Pointer.to(memStack));
        clSetKernelArg(kernel, argIdx++, Sizeof.cl_int, Pointer.to(new int[]{maxAdj}));
        clSetKernelArg(kernel, argIdx++, Sizeof.cl_int, Pointer.to(new int[]{maxStackDepth}));
        clSetKernelArg(kernel, argIdx++, Sizeof.cl_int, Pointer.to(new int[]{frameSize}));

        // Single work-item
        long[] globalWorkSize = {1};
        clEnqueueNDRangeKernel(queue, kernel, 1, null, globalWorkSize, null, 0, null, null);
        clFinish(queue);

        // =============================================
        // 5. Download results
        // =============================================
        int[] conceptCountResult = new int[1];
        clEnqueueReadBuffer(queue, memConceptCount, CL_TRUE,
                0, Sizeof.cl_int, Pointer.to(conceptCountResult), 0, null, null);
        int totalConcepts = conceptCountResult[0];

        // Read all extents
        int[] allExtents = new int[totalConcepts * wpe];
        clEnqueueReadBuffer(queue, memExtents, CL_TRUE,
                0, (long) allExtents.length * Sizeof.cl_int,
                Pointer.to(allExtents), 0, null, null);

        // Read all children
        int[] allChildren = new int[totalConcepts * maxAdj];
        clEnqueueReadBuffer(queue, memChildren, CL_TRUE,
                0, (long) allChildren.length * Sizeof.cl_int,
                Pointer.to(allChildren), 0, null, null);

        // Read child counts
        int[] allChildCounts = new int[totalConcepts];
        clEnqueueReadBuffer(queue, memChildCount, CL_TRUE,
                0, (long) totalConcepts * Sizeof.cl_int,
                Pointer.to(allChildCounts), 0, null, null);

        // Read reduced intents
        int[] allReducedIntents = new int[totalConcepts * wpi];
        clEnqueueReadBuffer(queue, memReducedIntents, CL_TRUE,
                0, (long) allReducedIntents.length * Sizeof.cl_int,
                Pointer.to(allReducedIntents), 0, null, null);

        // =============================================
        // 6. Build ConceptOrder from GPU results
        // =============================================
        order = new ConceptOrder("LatticeWithAddExtentFullGPU", matrix, getDescription());

        // Create all concepts with their extents
        for (int c = 0; c < totalConcepts; c++) {
            ISet extent = factory.createSet(nbObjects);
            for (int w = 0; w < wpe; w++) {
                int word = allExtents[c * wpe + w];
                while (word != 0) {
                    int bit = Integer.numberOfTrailingZeros(word);
                    extent.add(w * 32 + bit);
                    word &= word - 1;
                }
            }
            ISet intent = factory.createSet(nbAttributes);
            order.addConcept(extent, intent);

            // Set reduced intent
            ISet rintent = order.getConceptReducedIntent(c);
            for (int w = 0; w < wpi; w++) {
                int word = allReducedIntents[c * wpi + w];
                while (word != 0) {
                    int bit = Integer.numberOfTrailingZeros(word);
                    rintent.add(w * 32 + bit);
                    word &= word - 1;
                }
            }
        }

        // Add edges from children arrays
        for (int c = 0; c < totalConcepts; c++) {
            int nc = allChildCounts[c];
            for (int k = 0; k < nc; k++) {
                int child = allChildren[c * maxAdj + k];
                order.addPrecedenceConnection(child, c);
            }
        }

        // Compute reduced extents
        for (Iterator<Integer> it = order.getBasicIterator(); it.hasNext(); ) {
            int concept = it.next();
            ISet rExtent = factory.clone(order.getConceptExtent(concept));
            for (int child : order.getLowerCoverSet(concept)) {
                rExtent.removeAll(order.getConceptExtent(child));
            }
            order.getConceptReducedExtent(concept).addAll(rExtent);
        }

        // Compute full intents from reduced intents
        order.computeIntents();

        // =============================================
        // 7. Cleanup
        // =============================================
        clReleaseMemObject(memContextExtents);
        clReleaseMemObject(memExtents);
        clReleaseMemObject(memChildren);
        clReleaseMemObject(memChildCount);
        clReleaseMemObject(memConceptCount);
        clReleaseMemObject(memReducedIntents);
        clReleaseMemObject(memStack);
        clReleaseKernel(kernel);
        clReleaseProgram(program);
        clReleaseCommandQueue(queue);
        clReleaseContext(clContext);
    }

    // =============================================
    // Helpers
    // =============================================

    private int estimateMaxConcepts(int nbObjects, int nbAttributes) {
        // 2^min(nbObj, nbAttr) is the theoretical max, but we cap it
        int minDim = Math.min(nbObjects, nbAttributes);
        long theoretical;
        if (minDim <= 20) {
            theoretical = 1L << minDim; // 2^minDim
        } else {
            theoretical = 2_000_000L;
        }
        // At least 10x the number of attributes, at most 2M
        long estimate = Math.max(theoretical, (long) nbAttributes * 10);
        estimate = Math.min(estimate, 2_000_000L);
        return (int) estimate;
    }

    private String readProgramSource(String resourcePath) {
        try {
            InputStream is = getClass().getResourceAsStream(resourcePath);
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
            throw new RuntimeException("Failed to read OpenCL source: " + resourcePath, e);
        }
    }
}
