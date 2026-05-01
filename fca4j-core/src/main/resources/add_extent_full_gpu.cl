// ============================================================================
// add_extent_full_gpu.cl
//
// Complete AddExtent algorithm running entirely on GPU.
// Single work-item kernel (serial execution) — the parallelism comes from
// eliminating ALL CPU-GPU transfers during lattice construction.
//
// Data layout (all in global memory):
//
// CONTEXT:
//   context_extents[attr * wpe + w]   packed extent of attribute attr
//
// LATTICE:
//   extents[concept * wpe + w]        packed extent of concept
//   children[concept * maxAdj + k]    child concept ids
//   childCount[concept]               number of children
//   conceptCount[0]                   total number of concepts created
//
// STACK (for iterative recursion):
//   Each frame stores the state of one addExtent call:
//   - extent (wpe words)
//   - generator
//   - childList (snapshot of children at entry)
//   - childListLen
//   - childIdx (current position in childList)
//   - newChildren array
//   - newChildrenLen
//   - phase (0=descend, 1=process_children, 2=collect_result)
//
// OUTPUT:
//   reducedIntents[concept * wpi + w]  packed reduced intent bits
//
// ============================================================================

// --- Helper: containsAll (A ⊇ B) ---
// Returns 1 if all bits in B are also in A
int gpu_containsAll(__global const int* A, __global const int* B, int words) {
    for (int w = 0; w < words; w++) {
        if ((B[w] & ~A[w]) != 0) return 0;
    }
    return 1;
}

// Same but B is in private memory
int gpu_containsAll_priv(__global const int* A, int* B, int words) {
    for (int w = 0; w < words; w++) {
        if ((B[w] & ~A[w]) != 0) return 0;
    }
    return 1;
}

// --- Helper: equals ---
int gpu_equals(__global const int* A, int* B, int words) {
    for (int w = 0; w < words; w++) {
        if (A[w] != B[w]) return 0;
    }
    return 1;
}

// --- Helper: intersect into private memory ---
void gpu_intersect(__global const int* A, __global const int* B, int* C, int words) {
    for (int w = 0; w < words; w++) {
        C[w] = A[w] & B[w];
    }
}

// --- Helper: copy from global to private ---
void gpu_copy_to_priv(__global const int* src, int* dst, int words) {
    for (int w = 0; w < words; w++) {
        dst[w] = src[w];
    }
}

// --- Helper: copy from private to global ---
void gpu_copy_to_glob(int* src, __global int* dst, int words) {
    for (int w = 0; w < words; w++) {
        dst[w] = src[w];
    }
}

// --- Helper: addChild ---
void gpu_addChild(
    __global int* children,
    __global int* childCount,
    int parentId, int childId, int maxAdj)
{
    int idx = childCount[parentId];
    children[parentId * maxAdj + idx] = childId;
    childCount[parentId] = idx + 1;
}

// --- Helper: removeChild ---
void gpu_removeChild(
    __global int* children,
    __global int* childCount,
    int parentId, int childId, int maxAdj)
{
    int n = childCount[parentId];
    int base = parentId * maxAdj;
    int writeIdx = 0;
    for (int i = 0; i < n; i++) {
        if (children[base + i] != childId) {
            children[base + writeIdx] = children[base + i];
            writeIdx++;
        }
    }
    childCount[parentId] = writeIdx;
}

// --- Helper: allocate new concept, copy extent ---
int gpu_newConcept(
    __global int* extents,
    int* extent,
    __global int* children,
    __global int* childCount,
    __global int* conceptCount,
    int wpe)
{
    int id = conceptCount[0];
    conceptCount[0] = id + 1;
    // Copy extent
    int base = id * wpe;
    for (int w = 0; w < wpe; w++) {
        extents[base + w] = extent[w];
    }
    // Init children
    childCount[id] = 0;
    return id;
}


// ============================================================================
// MAIN KERNEL: addExtentForAllAttributes
//
// Processes all attributes sequentially. For each attribute, runs the full
// addExtent algorithm iteratively using an explicit stack.
//
// This is a SINGLE work-item kernel. The GPU advantage comes from:
// 1. Zero CPU-GPU transfers during the entire lattice construction
// 2. All data stays in GPU global memory (fast access)
// 3. The boucle externe can be launched once and processes everything
// ============================================================================

// Stack frame layout in global memory:
// Each frame occupies a fixed-size block in the stack buffer.
// Frame fields stored as ints at fixed offsets:

#define FRAME_GENERATOR    0
#define FRAME_CHILD_IDX    1
#define FRAME_CHILD_LEN    2
#define FRAME_NEWCHILD_LEN 3
#define FRAME_PHASE        4
#define FRAME_RETURN_VAL   5
#define FRAME_HEADER_SIZE  6

// After header: extent[wpe], childList[maxAdj], newChildren[maxAdj]
// Total frame size = FRAME_HEADER_SIZE + wpe + maxAdj + maxAdj

__kernel void buildLattice(
    // Context
    __global const int* contextExtents,  // [nbAttributes * wpe]
    const int nbAttributes,
    const int nbObjects,
    const int wpe,                       // words per extent

    // Lattice buffers (read/write)
    __global int* extents,               // [maxConcepts * wpe]
    __global int* children,              // [maxConcepts * maxAdj]
    __global int* childCount,            // [maxConcepts]
    __global int* conceptCount,          // [1] — current concept count

    // Reduced intents output
    __global int* reducedIntents,        // [maxConcepts * wpi]
    const int wpi,                       // words per intent

    // Stack workspace
    __global int* stack,                 // [maxStackDepth * frameSize]
    const int maxAdj,                    // max children per concept
    const int maxStackDepth,
    const int frameSize                  // FRAME_HEADER_SIZE + wpe + maxAdj + maxAdj
)
{
    // Only one work-item
    if (get_global_id(0) != 0) return;

    // -------------------------------------------------------
    // Initialize: top concept with all objects
    // -------------------------------------------------------
    conceptCount[0] = 0;

    // Allocate top concept (id = 0)
    int topId = conceptCount[0];
    conceptCount[0] = 1;
    childCount[topId] = 0;

    // Fill top extent with all objects
    int topBase = topId * wpe;
    for (int w = 0; w < wpe; w++) {
        int bitStart = w * 32;
        if (bitStart + 32 <= nbObjects) {
            extents[topBase + w] = 0xFFFFFFFF;
        } else if (bitStart < nbObjects) {
            extents[topBase + w] = (1 << (nbObjects - bitStart)) - 1;
        } else {
            extents[topBase + w] = 0;
        }
    }

    // Clear all reduced intents
    int maxConcepts_approx = maxStackDepth * 100; // rough
    // We'll set reduced intents as we go

    // -------------------------------------------------------
    // Process each attribute
    // -------------------------------------------------------
    for (int attr = 0; attr < nbAttributes; attr++) {

        // The extent to insert = contextExtents[attr]
        // We use an iterative stack-based addExtent

        int sp = 0; // stack pointer

        // Push initial frame
        int f0 = 0; // frame 0 offset
        stack[f0 + FRAME_GENERATOR] = topId;
        stack[f0 + FRAME_CHILD_IDX] = -1; // will start at 0 after descent phase
        stack[f0 + FRAME_CHILD_LEN] = 0;
        stack[f0 + FRAME_NEWCHILD_LEN] = 0;
        stack[f0 + FRAME_PHASE] = 0; // phase 0 = findSmallestContaining + init
        stack[f0 + FRAME_RETURN_VAL] = -1;

        // Copy extent into frame
        int extOff = f0 + FRAME_HEADER_SIZE;
        for (int w = 0; w < wpe; w++) {
            stack[extOff + w] = contextExtents[attr * wpe + w];
        }

        int resultConcept = -1;

        while (sp >= 0) {
            int fOfs = sp * frameSize;
            int phase = stack[fOfs + FRAME_PHASE];
            int gen = stack[fOfs + FRAME_GENERATOR];

            if (phase == 0) {
                // ===== Phase 0: findSmallestContaining + check equality =====

                // Iterative descent
                int moved = 1;
                while (moved != 0) {
                    moved = 0;
                    int nc = childCount[gen];
                    int cBase = gen * maxAdj;
                    for (int k = 0; k < nc; k++) {
                        int child = children[cBase + k];
                        // Check: extents[child] ⊇ frame.extent ?
                        int childExtBase = child * wpe;
                        int extFrameOfs = fOfs + FRAME_HEADER_SIZE;
                        int contains = 1;
                        for (int w = 0; w < wpe; w++) {
                            if ((stack[extFrameOfs + w] & ~extents[childExtBase + w]) != 0) {
                                contains = 0;
                                break;
                            }
                        }
                        if (contains != 0) {
                            gen = child;
                            moved = 1;
                            break;
                        }
                    }
                }
                stack[fOfs + FRAME_GENERATOR] = gen;

                // Check equality: frame.extent == extents[gen] ?
                int genExtBase = gen * wpe;
                int extFrameOfs = fOfs + FRAME_HEADER_SIZE;
                int equal = 1;
                for (int w = 0; w < wpe; w++) {
                    if (extents[genExtBase + w] != stack[extFrameOfs + w]) {
                        equal = 0;
                        break;
                    }
                }

                if (equal != 0) {
                    // Extent matches — return generator
                    stack[fOfs + FRAME_RETURN_VAL] = gen;
                    // Pop
                    sp--;
                    if (sp >= 0) {
                        // Return value to parent frame
                        int parentOfs = sp * frameSize;
                        stack[parentOfs + FRAME_PHASE] = 3; // phase 3 = got child result
                        stack[parentOfs + FRAME_RETURN_VAL] = gen;
                    } else {
                        resultConcept = gen;
                    }
                    continue;
                }

                // Snapshot children of generator into frame.childList
                int nc = childCount[gen];
                stack[fOfs + FRAME_CHILD_LEN] = nc;
                int childListOfs = fOfs + FRAME_HEADER_SIZE + wpe;
                int cBase = gen * maxAdj;
                for (int k = 0; k < nc; k++) {
                    stack[childListOfs + k] = children[cBase + k];
                }

                stack[fOfs + FRAME_CHILD_IDX] = 0;
                stack[fOfs + FRAME_NEWCHILD_LEN] = 0;
                stack[fOfs + FRAME_PHASE] = 1; // move to phase 1
            }

            if (phase == 1 || phase == 3) {
                // ===== Phase 1/3: iterate over children =====

                int childIdx = stack[fOfs + FRAME_CHILD_IDX];
                int childLen = stack[fOfs + FRAME_CHILD_LEN];
                int childListOfs = fOfs + FRAME_HEADER_SIZE + wpe;
                int newChildOfs = fOfs + FRAME_HEADER_SIZE + wpe + maxAdj;
                int newChildLen = stack[fOfs + FRAME_NEWCHILD_LEN];

                // If phase 3, we're returning from a recursive call
                if (phase == 3) {
                    int candidate = stack[fOfs + FRAME_RETURN_VAL];

                    // Filter: keep only maximal in newChildren
                    int addChild = 1;
                    int candExtBase = candidate * wpe;

                    // Check against existing newChildren
                    int writeIdx = 0;
                    for (int j = 0; j < newChildLen; j++) {
                        int existing = stack[newChildOfs + j];
                        int exExtBase = existing * wpe;

                        // existing ⊇ candidate ?
                        int exContainsCand = 1;
                        for (int w = 0; w < wpe; w++) {
                            if ((extents[candExtBase + w] & ~extents[exExtBase + w]) != 0) {
                                exContainsCand = 0;
                                break;
                            }
                        }
                        if (exContainsCand != 0) {
                            addChild = 0;
                            // Keep all existing newChildren as-is
                            writeIdx = newChildLen;
                            break;
                        }

                        // candidate ⊇ existing ?
                        int candContainsEx = 1;
                        for (int w = 0; w < wpe; w++) {
                            if ((extents[exExtBase + w] & ~extents[candExtBase + w]) != 0) {
                                candContainsEx = 0;
                                break;
                            }
                        }
                        if (candContainsEx == 0) {
                            // Keep existing
                            stack[newChildOfs + writeIdx] = existing;
                            writeIdx++;
                        }
                        // else: candidate contains existing → drop existing
                    }

                    if (addChild != 0) {
                        if (writeIdx < newChildLen) {
                            newChildLen = writeIdx;
                        }
                        stack[newChildOfs + newChildLen] = candidate;
                        newChildLen++;
                    }
                    stack[fOfs + FRAME_NEWCHILD_LEN] = newChildLen;

                    // Move to next child
                    childIdx++;
                    stack[fOfs + FRAME_CHILD_IDX] = childIdx;
                    stack[fOfs + FRAME_PHASE] = 1;
                }

                // Process next child (or finish)
                if (childIdx >= childLen) {
                    // ===== All children processed: create concept + rewire =====
                    stack[fOfs + FRAME_PHASE] = 2;
                } else {
                    int candidate = stack[childListOfs + childIdx];
                    int candExtBase = candidate * wpe;
                    int extFrameOfs = fOfs + FRAME_HEADER_SIZE;

                    // Does candidate.extent ⊇ frame.extent ?
                    int candContains = 1;
                    for (int w = 0; w < wpe; w++) {
                        if ((stack[extFrameOfs + w] & ~extents[candExtBase + w]) != 0) {
                            candContains = 0;
                            break;
                        }
                    }

                    if (candContains != 0) {
                        // candidate already contains extent — use as-is
                        // (same as addChild logic above but inline)
                        int addChild2 = 1;
                        newChildLen = stack[fOfs + FRAME_NEWCHILD_LEN];
                        int writeIdx2 = 0;
                        for (int j = 0; j < newChildLen; j++) {
                            int existing = stack[newChildOfs + j];
                            int exExtBase = existing * wpe;

                            int exContainsCand = 1;
                            for (int w = 0; w < wpe; w++) {
                                if ((extents[candExtBase + w] & ~extents[exExtBase + w]) != 0) {
                                    exContainsCand = 0;
                                    break;
                                }
                            }
                            if (exContainsCand != 0) {
                                addChild2 = 0;
                                writeIdx2 = newChildLen;
                                break;
                            }

                            int candContainsEx = 1;
                            for (int w = 0; w < wpe; w++) {
                                if ((extents[exExtBase + w] & ~extents[candExtBase + w]) != 0) {
                                    candContainsEx = 0;
                                    break;
                                }
                            }
                            if (candContainsEx == 0) {
                                stack[newChildOfs + writeIdx2] = existing;
                                writeIdx2++;
                            }
                        }

                        if (addChild2 != 0) {
                            if (writeIdx2 < newChildLen) newChildLen = writeIdx2;
                            stack[newChildOfs + newChildLen] = candidate;
                            newChildLen++;
                        }
                        stack[fOfs + FRAME_NEWCHILD_LEN] = newChildLen;

                        childIdx++;
                        stack[fOfs + FRAME_CHILD_IDX] = childIdx;
                        // Stay in phase 1
                    } else {
                        // Need recursive call: addExtent(extent ∩ candidate, candidate)
                        // Push new frame
                        sp++;
                        int newFOfs = sp * frameSize;

                        stack[newFOfs + FRAME_GENERATOR] = candidate;
                        stack[newFOfs + FRAME_CHILD_IDX] = -1;
                        stack[newFOfs + FRAME_CHILD_LEN] = 0;
                        stack[newFOfs + FRAME_NEWCHILD_LEN] = 0;
                        stack[newFOfs + FRAME_PHASE] = 0;
                        stack[newFOfs + FRAME_RETURN_VAL] = -1;

                        // Compute intersection: frame.extent ∩ candidate.extent
                        int newExtOfs = newFOfs + FRAME_HEADER_SIZE;
                        for (int w = 0; w < wpe; w++) {
                            stack[newExtOfs + w] = stack[extFrameOfs + w] & extents[candExtBase + w];
                        }

                        // Don't advance childIdx yet — will be advanced when we return (phase 3)
                        continue;
                    }
                }
            }

            if (stack[fOfs + FRAME_PHASE] == 2) {
                // ===== Phase 2: create new concept and rewire =====
                gen = stack[fOfs + FRAME_GENERATOR];
                int extFrameOfs = fOfs + FRAME_HEADER_SIZE;
                int newChildOfs = fOfs + FRAME_HEADER_SIZE + wpe + maxAdj;
                int newChildLen = stack[fOfs + FRAME_NEWCHILD_LEN];

                // Allocate new concept
                int newId = conceptCount[0];
                conceptCount[0] = newId + 1;
                childCount[newId] = 0;

                // Copy extent from stack frame to concept
                int newExtBase = newId * wpe;
                for (int w = 0; w < wpe; w++) {
                    extents[newExtBase + w] = stack[extFrameOfs + w];
                }

                // Rewire: for each newChild, remove from generator, add to newConcept
                for (int j = 0; j < newChildLen; j++) {
                    int child = stack[newChildOfs + j];
                    gpu_removeChild(children, childCount, gen, child, maxAdj);
                    gpu_addChild(children, childCount, newId, child, maxAdj);
                }

                // Add newConcept as child of generator
                gpu_addChild(children, childCount, gen, newId, maxAdj);

                // Return newId
                stack[fOfs + FRAME_RETURN_VAL] = newId;

                // Pop
                sp--;
                if (sp >= 0) {
                    int parentOfs = sp * frameSize;
                    stack[parentOfs + FRAME_PHASE] = 3;
                    stack[parentOfs + FRAME_RETURN_VAL] = newId;
                } else {
                    resultConcept = newId;
                }
            }
        } // end while (sp >= 0)

        // Mark this attribute in the reduced intent of resultConcept
        if (resultConcept >= 0) {
            int word = attr / 32;
            int bit = attr % 32;
            reducedIntents[resultConcept * wpi + word] |= (1 << bit);
        }

    } // end for each attribute
}
