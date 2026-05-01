// ============================================================================
// add_extent_gpu.cl
// OpenCL kernels for GPU-accelerated AddExtent lattice construction
//
// All set operations work on packed bit vectors:
//   - Each int holds 32 elements (bits)
//   - A set of N elements uses ceil(N/32) ints
//   - Bit i of word j represents element (j*32 + i)
//
// Memory layout for lattice buffers:
//   extents[concept * wordsPerExtent + w]  = packed extent bits
//   intents[concept * wordsPerIntent + w]  = packed intent bits
//   children[concept * maxChildren + k]    = child concept id (-1 = end)
//   childCount[concept]                    = number of children
// ============================================================================

// ---------------------------------------------------------
// containsAll: does concept's extent contain the given set?
//   result[conceptIndex] = 1 if extent ⊇ testSet, else 0
//
//   Launched with global_size = (numConcepts)
//   Each work-item checks one concept.
// ---------------------------------------------------------
__kernel void containsAll(
    __global const int* extents,     // all concept extents (packed)
    __global const int* testSet,     // the set to test against (packed)
    __const  int wordsPerExtent,     // ceil(nbObjects / 32)
    __global const int* conceptIds,  // array of concept indices to test
    __const  int numConcepts,        // number of concepts to test
    __global int* result             // output: 1=contains, 0=not
)
{
    int gid = get_global_id(0);
    if (gid >= numConcepts) return;

    int conceptId = conceptIds[gid];
    int offset = conceptId * wordsPerExtent;
    int contains = 1;

    for (int w = 0; w < wordsPerExtent; w++) {
        int extWord = extents[offset + w];
        int testWord = testSet[w];
        // If testSet has bits not in extent, containment fails
        if ((testWord & ~extWord) != 0) {
            contains = 0;
            break;
        }
    }

    result[gid] = contains;
}

// ---------------------------------------------------------
// intersect: C = A & B (word-by-word)
//   Launched with global_size = (wordsPerSet)
// ---------------------------------------------------------
__kernel void intersect(
    __global const int* setA,
    __global const int* setB,
    __global       int* setC,
    __const  int wordsPerSet
)
{
    int gid = get_global_id(0);
    if (gid >= wordsPerSet) return;
    setC[gid] = setA[gid] & setB[gid];
}

// ---------------------------------------------------------
// difference: C = A & ~B (word-by-word)
//   Launched with global_size = (wordsPerSet)
// ---------------------------------------------------------
__kernel void difference(
    __global const int* setA,
    __global const int* setB,
    __global       int* setC,
    __const  int wordsPerSet
)
{
    int gid = get_global_id(0);
    if (gid >= wordsPerSet) return;
    setC[gid] = setA[gid] & ~setB[gid];
}

// ---------------------------------------------------------
// bitwiseOr: A = A | B (word-by-word, in-place on A)
//   Launched with global_size = (wordsPerSet)
// ---------------------------------------------------------
__kernel void bitwiseOr(
    __global       int* setA,
    __global const int* setB,
    __const  int wordsPerSet
)
{
    int gid = get_global_id(0);
    if (gid >= wordsPerSet) return;
    setA[gid] = setA[gid] | setB[gid];
}

// ---------------------------------------------------------
// equals: are two packed sets identical?
//   Uses parallel reduction. result[0] = 0 means equal.
//
//   Launched with global_size = (localWorkSize * numWorkGroups)
// ---------------------------------------------------------
__kernel void equals(
    __global const int* setA,
    __global const int* setB,
    __local        int* scratch,
    __const  int wordsPerSet,
    __global       int* result
)
{
    int globalIndex = get_global_id(0);
    int accumulator = 0;

    while (globalIndex < wordsPerSet) {
        int wordA = setA[globalIndex];
        int wordB = setB[globalIndex];
        // Count differing bits via popcount
        accumulator += popcount(wordA ^ wordB);
        globalIndex += get_global_size(0);
    }

    int lid = get_local_id(0);
    scratch[lid] = accumulator;
    barrier(CLK_LOCAL_MEM_FENCE);

    for (int offset = get_local_size(0) / 2; offset > 0; offset /= 2) {
        if (lid < offset) {
            scratch[lid] += scratch[lid + offset];
        }
        barrier(CLK_LOCAL_MEM_FENCE);
    }

    if (lid == 0) {
        result[get_group_id(0)] = scratch[0];
    }
}

// ---------------------------------------------------------
// isEmpty: is a packed set all zeros?
//   Uses parallel reduction. result[0] = 0 means empty.
//
//   Launched with global_size = (localWorkSize * numWorkGroups)
// ---------------------------------------------------------
__kernel void isEmpty(
    __global const int* setA,
    __local        int* scratch,
    __const  int wordsPerSet,
    __global       int* result
)
{
    int globalIndex = get_global_id(0);
    int accumulator = 0;

    while (globalIndex < wordsPerSet) {
        accumulator += popcount(setA[globalIndex]);
        globalIndex += get_global_size(0);
    }

    int lid = get_local_id(0);
    scratch[lid] = accumulator;
    barrier(CLK_LOCAL_MEM_FENCE);

    for (int offset = get_local_size(0) / 2; offset > 0; offset /= 2) {
        if (lid < offset) {
            scratch[lid] += scratch[lid + offset];
        }
        barrier(CLK_LOCAL_MEM_FENCE);
    }

    if (lid == 0) {
        result[get_group_id(0)] = scratch[0];
    }
}

// ---------------------------------------------------------
// copySet: copy one packed set to another
//   Launched with global_size = (wordsPerSet)
// ---------------------------------------------------------
__kernel void copySet(
    __global const int* src,
    __global       int* dst,
    __const  int wordsPerSet
)
{
    int gid = get_global_id(0);
    if (gid >= wordsPerSet) return;
    dst[gid] = src[gid];
}

// ---------------------------------------------------------
// clearSet: zero out a packed set
//   Launched with global_size = (wordsPerSet)
// ---------------------------------------------------------
__kernel void clearSet(
    __global int* set,
    __const  int wordsPerSet
)
{
    int gid = get_global_id(0);
    if (gid >= wordsPerSet) return;
    set[gid] = 0;
}

// ---------------------------------------------------------
// fillSet: set bits 0..nbElements-1 to 1
//   Launched with global_size = (wordsPerSet)
// ---------------------------------------------------------
__kernel void fillSet(
    __global int* set,
    __const  int wordsPerSet,
    __const  int nbElements
)
{
    int gid = get_global_id(0);
    if (gid >= wordsPerSet) return;

    int bitStart = gid * 32;
    if (bitStart >= nbElements) {
        set[gid] = 0;
    } else if (bitStart + 32 <= nbElements) {
        set[gid] = 0xFFFFFFFF;
    } else {
        // partial last word
        int bitsToSet = nbElements - bitStart;
        set[gid] = (1 << bitsToSet) - 1;
    }
}

// ---------------------------------------------------------
// setBit: set a single bit in a packed set
//   Launched with global_size = (1)
// ---------------------------------------------------------
__kernel void setBit(
    __global int* set,
    __const  int bitIndex
)
{
    int word = bitIndex / 32;
    int bit  = bitIndex % 32;
    set[word] = set[word] | (1 << bit);
}

// ---------------------------------------------------------
// batchContainsAll: test containment for all children of a concept
//   For findSmallestContainingConcept: test all children at once,
//   return the first one that contains the testSet.
//
//   Launched with global_size = (numChildren)
// ---------------------------------------------------------
__kernel void batchContainsAll(
    __global const int* extents,       // all concept extents
    __global const int* testSet,       // set to test
    __const  int wordsPerExtent,       // words per extent
    __global const int* childIds,      // children concept ids
    __const  int numChildren,          // number of children
    __global int* result               // output: 1 if child contains testSet
)
{
    int gid = get_global_id(0);
    if (gid >= numChildren) return;

    int childId = childIds[gid];
    int offset = childId * wordsPerExtent;
    int contains = 1;

    for (int w = 0; w < wordsPerExtent; w++) {
        int extWord = extents[offset + w];
        int testWord = testSet[w];
        if ((testWord & ~extWord) != 0) {
            contains = 0;
            break;
        }
    }

    result[gid] = contains;
}

// ---------------------------------------------------------
// removeAll: A = A & ~B (in-place on A)
//   Launched with global_size = (wordsPerSet)
// ---------------------------------------------------------
__kernel void removeAll(
    __global       int* setA,
    __global const int* setB,
    __const  int wordsPerSet
)
{
    int gid = get_global_id(0);
    if (gid >= wordsPerSet) return;
    setA[gid] = setA[gid] & ~setB[gid];
}

// ---------------------------------------------------------
// computeIntentFromContext: intent = intersection of rows of objects in extent
//   For each attribute column, check if ALL objects in extent have it.
//   This is the closure operator: intent(extent) = { m | forall g in extent: (g,m) in I }
//
//   context is stored as: context[obj * nbAttributes + attr] = 0 or 1 (unpacked, one byte per cell)
//   extent is packed bits.
//
//   Launched with global_size = (nbAttributes)
//   Each work-item computes one attribute of the intent.
// ---------------------------------------------------------
__kernel void computeIntentFromContext(
    __global const int* context,       // unpacked context matrix (obj * nbAttr)
    __global const int* extent,        // packed extent bits
    __const  int nbObjects,
    __const  int nbAttributes,
    __global int* intentResult         // packed intent result
)
{
    int attr = get_global_id(0);
    if (attr >= nbAttributes) return;

    int hasAttr = 1;

    for (int obj = 0; obj < nbObjects; obj++) {
        // Check if obj is in extent
        int word = obj / 32;
        int bit  = obj % 32;
        int inExtent = (extent[word] >> bit) & 1;

        if (inExtent == 1) {
            // Check if obj has attribute attr
            if (context[obj * nbAttributes + attr] == 0) {
                hasAttr = 0;
                break;
            }
        }
    }

    // Set bit in intentResult
    int targetWord = attr / 32;
    int targetBit  = attr % 32;
    if (hasAttr == 1) {
        atomic_or(&intentResult[targetWord], 1 << targetBit);
    }
}
