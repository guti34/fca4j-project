/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.IConceptOrder;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

/**
 * The Class AOC_poset_Ares.
 *
 * <p>Phase 1 optimisation (semantics unchanged):
 * <ul>
 * <li>the case dispatch of {@code visit} is driven by three cardinalities read
 * from a single reusable intersection scratch, instead of allocating the four
 * derived sets {@code rc}, {@code ra}, {@code cc}, {@code ec} on every visited
 * concept;</li>
 * <li>the guard no longer allocates an intersection just to test that it is
 * non-empty ({@code intersects} instead of {@code newIntersect().cardinality()});</li>
 * <li>the reduced extent of the visited concept is computed lazily, into a
 * scratch, only in the cases that actually need it;</li>
 * <li>{@code max} performs one shared traversal of the down-set instead of one
 * {@code getAllChildren} call (and one set allocation) per element;</li>
 * <li>the dead bookkeeping on the local linear extension is removed.</li>
 * </ul>
 *
 * <p>Phase 2 optimisation (semantics unchanged, validated on the exhaustive
 * sweep up to 12 cells plus 20000 random contexts):
 * <ul>
 * <li>site A6 answers a targeted reachability question with one epoch-stamped
 * DFS instead of enumerating the whole ancestor set;</li>
 * <li>{@code max(children)} and {@code min(parents)} are gone from A6: covers
 * of a single element are antichains in a reduced diagram, so both calls always
 * returned their own input. They are restored, and throw if they ever remove
 * anything, under {@link AresGuard#ASSERT_A6};</li>
 * <li>case 4 collects the maximal selected descendants in two marker walks
 * instead of {@code getAllChildren} + intersection + {@code max}, removing
 * three set allocations per visit.</li>
 * </ul>
 */
public class AOC_poset_Ares implements AbstractAlgo<IConceptOrder> {

    /** Audit hooks. Off by default; no cost in production. */
    public static boolean SKIP_REDUCE = false;
    public static boolean TRACK_EDGE_ORIGIN = false;
    /** (lower &lt;&lt; 32 | greater) -&gt; creation site, valid for the last run only. */
    public static final Map<Long, String> EDGE_ORIGIN = new HashMap<>();

    private IBinaryContext matrix; //ressource d'entree
    private IConceptOrder gsh; //ressource de sortie
    private Chrono chrono = null; // eventually a chrono to store execution time
    private boolean acposet;
    private boolean ocposet;
    protected ISetFactory factory;

    /** Upper bound used to size the concept-indexed sets. */
    private final int maxNbConcepts;
    /** Scratch holding {@code extent(c) inter g(a)} for the concept being visited. */
    private final ISet interScratch;
    /** Scratch holding the reduced extent of the concept being visited. */
    private final ISet redScratch;

    /** Visited marker for the targeted reachability test; a vertex v has been
     * reached iff {@code reachMark[v] == reachEpoch}. Epoch-stamped, so no reset
     * between calls. Epochs start at 1 and the arrays at 0, so a freshly grown
     * slot can never masquerade as visited. */
    private int[] reachMark;
    private int reachEpoch;
    /** Target marker, same epoch trick: v is a target iff {@code targetMark[v] == targetEpoch}. */
    private int[] targetMark;
    private int targetEpoch;
    /** Explicit DFS stack, grown on demand. */
    private int[] reachStack;

    /** Descendants of the visited concept, epoch-stamped (case 4). */
    private int[] descMark;
    private int descEpoch;
    /** Strict descendants of the selected concepts, epoch-stamped (case 4). */
    private int[] domMark;
    private int domEpoch;
    /** Selected concepts collected by the descending walk, and their count. */
    private int[] selBuf;
    private int selCount;

    /**
     * Instantiates a new AOC poset algorithm: ares.
     *
     * @param matrix the context
     * @param chrono the chrono
     */
    public AOC_poset_Ares(IBinaryContext matrix, Chrono chrono) {
        this(matrix, chrono, null, true, true);
    }

    /**
     * Instantiates a new AOC poset algorithm: ares.
     *
     * @param matrix the context
     * @param chrono the chrono
     * @param gsh the gsh
     * @param ocposet build ocposet
     * @param acposet build acposet
     */
    public AOC_poset_Ares(IBinaryContext matrix, Chrono chrono, IConceptOrder gsh, boolean ocposet, boolean acposet) {
        super();
        this.gsh = gsh;
        this.matrix = matrix;
        this.factory = matrix.getFactory();
        this.chrono = chrono;
        this.ocposet = ocposet;
        this.acposet = acposet;
        this.maxNbConcepts = matrix.getAttributeCount() + matrix.getObjectCount();
        this.interScratch = factory.createSet(matrix.getObjectCount());
        this.redScratch = factory.createSet(matrix.getObjectCount());
        int cap = this.maxNbConcepts + 1;
        this.reachMark = new int[cap];
        this.targetMark = new int[cap];
        this.descMark = new int[cap];
        this.domMark = new int[cap];
        this.selBuf = new int[64];
        this.reachStack = new int[64];
        this.reachEpoch = 0;
        this.targetEpoch = 0;
        this.descEpoch = 0;
        this.domEpoch = 0;
    }

    /**
     * Instantiates a new AOC poset algorithm: ares.
     *
     * @param matrix the context
     */
    public AOC_poset_Ares(IBinaryContext matrix) {
        this(matrix, null);
    }

    private boolean hasAllAConcepts() {
        return acposet;
    }

    private boolean hasAllOConcepts() {
        return ocposet;
    }

    /**
     * Compute.
     *
     * @param newAttributes the new attributes
     * @return the concept order
     * @throws CloneNotSupportedException the clone not supported exception
     */
    public IConceptOrder compute(ISet newAttributes) throws CloneNotSupportedException {
        //for each attribute add it
        for (Iterator<Integer> attrIterator = newAttributes.iterator(); attrIterator.hasNext();) {
            AAresStep aaresStep = new AAresStep(attrIterator.next());
            aaresStep.compute();
        }
        // Ares now produces the transitively reduced diagram directly: the removal
        // of non-introducers reconnects only maximal children to minimal parents,
        // guarded by reachability, so no transitive edge is ever created (verified
        // by AresLeakCensus over the exhaustive sweep of contexts up to 12 cells
        // and 20000 random contexts up to 8x8). The former unconditional
        // gsh.reduce() -- an O(n^3), O(n^2)-memory JGraphT transitive reduction that
        // accounted for a third to a half of the runtime -- is therefore gone.
        // It is kept only as an assertion (enabled with -ea) as a regression guard,
        // and skipped entirely when the audit harness inspects the raw diagram.
        assert SKIP_REDUCE || edgeCountUnchangedByReduce() : "Ares produced a non-reduced diagram";
        return gsh;
    }

    /** @return true iff running the transitive reduction removes no edge. */
    private boolean edgeCountUnchangedByReduce() {
        int before = gsh.getEdgeCount();
        gsh.reduce();
        return gsh.getEdgeCount() == before;
    }

    private static long edgeKey(int lower, int greater) {
        return (((long) lower) << 32) | (greater & 0xffffffffL);
    }

    /** Adds a precedence edge, remembering which site of the algorithm created it. */
    private void link(int lower, int greater, String site) {
        gsh.addPrecedenceConnection(lower, greater);
        if (TRACK_EDGE_ORIGIN) {
            EDGE_ORIGIN.put(edgeKey(lower, greater), site);
        }
    }

    private void unlink(int lower, int greater) {
        gsh.removePrecedenceConnection(lower, greater);
        if (TRACK_EDGE_ORIGIN) {
            EDGE_ORIGIN.remove(edgeKey(lower, greater));
        }
    }

    /**
     * Allocating variant, kept for the (rare) call sites that store the result.
     */
    private ISet computeReducedExtent(int c) throws CloneNotSupportedException {
        ISet extsC = factory.clone(gsh.getConceptExtent(c));
        for (Iterator<Integer> it = gsh.getLowerCoverIterator(c); it.hasNext();) {
            extsC.removeAll(gsh.getConceptExtent(it.next()));
        }
        return extsC;
    }

    /**
     * Allocation-free variant: writes the reduced extent of {@code c} into
     * {@code out}, whose previous content is discarded.
     */
    private void reducedExtentInto(int c, ISet out) {
        out.setTo(gsh.getConceptExtent(c));
        for (Iterator<Integer> it = gsh.getLowerCoverIterator(c); it.hasNext();) {
            out.removeAll(gsh.getConceptExtent(it.next()));
        }
    }

    /** Grows the concept-indexed marker arrays so that index {@code v} is addressable. */
    private void ensureReachCapacity(int v) {
        if (v < reachMark.length) {
            return;
        }
        int newLen = reachMark.length << 1;
        if (newLen <= v) {
            newLen = v + 1;
        }
        reachMark = Arrays.copyOf(reachMark, newLen);
        targetMark = Arrays.copyOf(targetMark, newLen);
        descMark = Arrays.copyOf(descMark, newLen);
        domMark = Arrays.copyOf(domMark, newLen);
    }

    /**
     * Marks which elements of {@code targets} are reachable from {@code source}
     * by walking up the upper covers, reflexively. After the call,
     * {@code isReached(p)} answers for every {@code p} of {@code targets}.
     *
     * <p>Replaces {@code getAllParents(source)} at site A6. The former call
     * allocated an {@code ISet} and enumerated the whole ancestor set on every
     * removed concept's maximal child, when the question asked is only whether a
     * handful of minimal parents are above. Here: one DFS on an epoch-stamped
     * marker array, no allocation, and an early exit as soon as every target has
     * been met.
     *
     * <p>Semantics are those of the code it replaces, deliberately including the
     * fact that the answer is computed once, before the edges of this A6 pass are
     * added: an edge created for one target does not make another target
     * reachable within the same call, exactly as the frozen {@code anc} set did.
     */
    private void computeReachableTargets(int source, ISet targets) {
        targetEpoch++;
        int remaining = 0;
        for (Iterator<Integer> it = targets.iterator(); it.hasNext();) {
            int t = it.next();
            ensureReachCapacity(t);
            if (targetMark[t] != targetEpoch) {
                targetMark[t] = targetEpoch;
                remaining++;
            }
        }
        reachEpoch++;
        ensureReachCapacity(source);
        int scanned = 0;
        int sp = 0;
        reachStack[sp++] = source;
        reachMark[source] = reachEpoch;
        if (targetMark[source] == targetEpoch) {
            remaining--;
        }
        while (sp > 0 && remaining > 0) {
            int v = reachStack[--sp];
            scanned++;
            for (Iterator<Integer> it = gsh.getUpperCoverIterator(v); it.hasNext();) {
                int p = it.next();
                ensureReachCapacity(p);
                if (reachMark[p] == reachEpoch) {
                    continue;
                }
                reachMark[p] = reachEpoch;
                if (targetMark[p] == targetEpoch) {
                    remaining--;
                }
                if (sp == reachStack.length) {
                    reachStack = Arrays.copyOf(reachStack, reachStack.length << 1);
                }
                reachStack[sp++] = p;
            }
        }
    }

    /**
     * Collects into {@code selBuf} the maximal elements of
     * {@code selected inter descendants(c)}, descendants taken reflexively.
     *
     * <p>Replaces {@code max(selected.newIntersect(getAllChildren(c)))} at site
     * A4. That expression walked the descendants of {@code c} to materialise an
     * ISet, allocated a second ISet for the intersection, then walked the same
     * subgraph again inside {@code max}, which itself cloned its input: three
     * allocations and two traversals of one subgraph. Here, two marker walks and
     * no allocation at all. The two sites together accounted for a third of the
     * runtime on ord6magic04.
     *
     * <p>After the call, {@code selCount} entries of {@code selBuf} hold the
     * candidates; entry {@code v} is maximal iff {@code domMark[v] != domEpoch}.
     * Note that the descending walk cannot be cut short on meeting a selected
     * concept: a selected descendant of it may still be reachable by another
     * path avoiding it, so it would be missed.
     */
    private void computeMaximalSelectedDescendants(int c, ISet selected) {
        ensureReachCapacity(c);
        descEpoch++;
        selCount = 0;
        int scanned = 0;
        int sp = 0;
        reachStack[sp++] = c;
        descMark[c] = descEpoch;
        while (sp > 0) {
            int v = reachStack[--sp];
            scanned++;
            if (selected.contains(v)) {
                if (selCount == selBuf.length) {
                    selBuf = Arrays.copyOf(selBuf, selBuf.length << 1);
                }
                selBuf[selCount++] = v;
            }
            for (Iterator<Integer> it = gsh.getLowerCoverIterator(v); it.hasNext();) {
                int w = it.next();
                ensureReachCapacity(w);
                if (descMark[w] == descEpoch) {
                    continue;
                }
                descMark[w] = descEpoch;
                if (sp == reachStack.length) {
                    reachStack = Arrays.copyOf(reachStack, reachStack.length << 1);
                }
                reachStack[sp++] = w;
            }
        }
        // second walk: mark the strict descendants of the selected concepts, so
        // that a selected concept dominated by another is discarded
        domEpoch++;
        sp = 0;
        for (int i = 0; i < selCount; i++) {
            for (Iterator<Integer> it = gsh.getLowerCoverIterator(selBuf[i]); it.hasNext();) {
                int w = it.next();
                ensureReachCapacity(w);
                if (domMark[w] == domEpoch) {
                    continue;
                }
                domMark[w] = domEpoch;
                if (sp == reachStack.length) {
                    reachStack = Arrays.copyOf(reachStack, reachStack.length << 1);
                }
                reachStack[sp++] = w;
            }
        }
        while (sp > 0) {
            int v = reachStack[--sp];
            scanned++;
            for (Iterator<Integer> it = gsh.getLowerCoverIterator(v); it.hasNext();) {
                int w = it.next();
                ensureReachCapacity(w);
                if (domMark[w] == domEpoch) {
                    continue;
                }
                domMark[w] = domEpoch;
                if (sp == reachStack.length) {
                    reachStack = Arrays.copyOf(reachStack, reachStack.length << 1);
                }
                reachStack[sp++] = w;
            }
        }
    }

    /** True iff the vertex was reached by the last {@link #computeReachableTargets}. */
    private boolean isReached(int v) {
        return v < reachMark.length && reachMark[v] == reachEpoch;
    }

    /**
     * Maximal elements of {@code concepts} for the precedence order, i.e.
     * {@code concepts} minus the elements that are strict descendants of another
     * element of {@code concepts}.
     *
     * <p>One traversal of the union of the strict down-sets, with a shared visited
     * marker, instead of one reflexive {@code getAllChildren} call (and one set
     * allocation) per element: O(V + E) instead of O(|S| . (V + E)).
     */
    private ISet max(ISet concepts) {
        ISet max = concepts.clone();
        if (concepts.cardinality() <= 1) {
            return max;
        }
        ISet strictDescendants = factory.createSet(maxNbConcepts);
        int[] stack = new int[16];
        int sp = 0;
        for (Iterator<Integer> it = concepts.iterator(); it.hasNext();) {
            for (Iterator<Integer> ch = gsh.getLowerCoverIterator(it.next()); ch.hasNext();) {
                if (sp == stack.length) {
                    stack = Arrays.copyOf(stack, stack.length << 1);
                }
                stack[sp++] = ch.next();
            }
        }
        while (sp > 0) {
            int v = stack[--sp];
            if (strictDescendants.contains(v)) {
                continue;
            }
            strictDescendants.add(v);
            for (Iterator<Integer> ch = gsh.getLowerCoverIterator(v); ch.hasNext();) {
                if (sp == stack.length) {
                    stack = Arrays.copyOf(stack, stack.length << 1);
                }
                stack[sp++] = ch.next();
            }
        }
        max.removeAll(strictDescendants);
        return max;
    }

    /**
     * Minimal elements of {@code concepts} for the precedence order, i.e.
     * {@code concepts} minus the elements that are strict ancestors of another
     * element of {@code concepts}. Exact dual of {@link #max(ISet)} (upper covers
     * instead of lower covers).
     */
    private ISet min(ISet concepts) {
        ISet min = concepts.clone();
        if (concepts.cardinality() <= 1) {
            return min;
        }
        ISet strictAncestors = factory.createSet(maxNbConcepts);
        int[] stack = new int[16];
        int sp = 0;
        for (Iterator<Integer> it = concepts.iterator(); it.hasNext();) {
            for (Iterator<Integer> pa = gsh.getUpperCoverIterator(it.next()); pa.hasNext();) {
                if (sp == stack.length) {
                    stack = Arrays.copyOf(stack, stack.length << 1);
                }
                stack[sp++] = pa.next();
            }
        }
        while (sp > 0) {
            int v = stack[--sp];
            if (strictAncestors.contains(v)) {
                continue;
            }
            strictAncestors.add(v);
            for (Iterator<Integer> pa = gsh.getUpperCoverIterator(v); pa.hasNext();) {
                if (sp == stack.length) {
                    stack = Arrays.copyOf(stack, stack.length << 1);
                }
                stack[sp++] = pa.next();
            }
        }
        min.removeAll(strictAncestors);
        return min;
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    public String getDescription() {
        return "Ares";
    }

    /**
     * Gets the result.
     *
     * @return the result
     */
    @Override
    public IConceptOrder getResult() {
        return gsh;
    }

    /**
     * Run.
     */
    @Override
    public void run() {
        try {
            if (TRACK_EDGE_ORIGIN) {
                EDGE_ORIGIN.clear();
            }
            if (gsh == null) {
                gsh = new ConceptOrder("AOCposetWithAres", matrix, getDescription());
                ISet extent = factory.createSet(matrix.getObjectCount());
                extent.fill(matrix.getObjectCount());
                ISet rextent = factory.createSet(matrix.getObjectCount());
                rextent.addAll(extent);
                gsh.addConcept(extent, factory.createSet(matrix.getAttributeCount()), rextent, factory.createSet(matrix.getAttributeCount()));
                ISet intent = factory.createSet(matrix.getAttributeCount());
                intent.fill(matrix.getAttributeCount());
                gsh = compute(intent);
            } else {
                int max_attr = -1;
                ISet intent = factory.createSet(matrix.getAttributeCount());
                for (Iterator<Integer> it = gsh.getMinimals().iterator(); it.hasNext();) {
                    intent.addAll(gsh.getConceptIntent(it.next()));
                }
                for (Iterator<Integer> it = intent.iterator(); it.hasNext();) {
                    max_attr = it.next();
                }
                ISet intentToCompute = factory.createSet(matrix.getAttributeCount());
                for (int numattr = max_attr + 1; numattr < matrix.getAttributeCount(); numattr++) {
                    intentToCompute.add(numattr);
                }
                gsh = compute(intentToCompute);
            }
        } catch (CloneNotSupportedException ex) {
            ex.printStackTrace();
        }
    }

    private class AAresStep {

        ISet subConceptsOfA = factory.createSet(maxNbConcepts);
        ISet nonIntroducingConcepts = factory.createSet(maxNbConcepts);
        ISet doNotCheck = factory.createSet(maxNbConcepts);
        ISet extentOfA;
        boolean isCADefined = false;
        int ca = -1;
        int a;
        /** g(a), hoisted out of the visit loop; a copy, so the context column is never aliased. */
        private final ISet extA;
        private final int cardA;

        AAresStep(int a) {
            this.a = a;
            this.extA = factory.clone(matrix.getExtent(a));
            this.cardA = extA.cardinality();
            /* extentOfA aims at being the list of proper entities of the attribute a
             * it starts with all the entities having a as attributes
             */
            this.extentOfA = factory.clone(matrix.getExtent(a));
        }

        void compute() throws CloneNotSupportedException {
            /* a linear extension of the already created concepts from more specific to more general */
            ArrayList<Integer> sortedConcepts = gsh.sortByExtent(true);
            for (int concept : sortedConcepts) {
                if (!visit(concept)) {
                    return;
                }
            }
            if (!isCADefined
                    && (hasAllAConcepts() || extentOfA.cardinality() != 0)//if oc poset then the concept must have proper entities
                    ) {
                ISet caExtent = factory.clone(extA);
                ISet caRIntent = factory.createSet(matrix.getAttributeCount());
                caRIntent.add(a);
                ca = gsh.addConcept(caExtent, factory.createSet(matrix.getAttributeCount()),
                        factory.createSet(matrix.getObjectCount()), caRIntent);
                for (Iterator<Integer> it = max(subConceptsOfA).iterator(); it.hasNext();) {
                    int maxSub = it.next();
                    link(maxSub, ca, "A5");
                }
            } else if (isCADefined && !hasAllAConcepts() && extentOfA.cardinality() == 0) {
                nonIntroducingConcepts.add(ca);
            }
            for (Iterator<Integer> it = nonIntroducingConcepts.iterator(); it.hasNext();) {
                int toRemove = it.next();
                if (!gsh.getConceptReducedIntent(toRemove).isEmpty()) {
                    for (Iterator<Integer> it2 = gsh.getLowerCoverIterator(toRemove); it2.hasNext();) {
                        int child = it2.next();
                        gsh.getConceptReducedIntent(child).addAll(gsh.getConceptReducedIntent(toRemove));
                    }
                }
                ISet parents = factory.createSet(maxNbConcepts);
                ISet children = factory.createSet(maxNbConcepts);
                for (Iterator<Integer> itp = gsh.getUpperCoverIterator(toRemove); itp.hasNext();) {
                    parents.add(itp.next());
                }
                for (Iterator<Integer> itc = gsh.getLowerCoverIterator(toRemove); itc.hasNext();) {
                    children.add(itc.next());
                }
                for (Iterator<Integer> itp = parents.iterator(); itp.hasNext();) {
                    unlink(toRemove, itp.next());
                }
                for (Iterator<Integer> itc = children.iterator(); itc.hasNext();) {
                    unlink(itc.next(), toRemove);
                }
                // Reconnect children to parents, and only when the parent is not already
                // reachable from the child once toRemove is isolated. The former cross
                // product reconnected every (child, parent) pair and only guarded against
                // duplicate edges, so any pair already joined by a lateral path of length
                // >= 2 became a transitive edge -- the sole leak left in the algorithm
                // (site A6). Computing reachability on the current graph is safe even when
                // several non-introducers are removed in turn: the children are pairwise
                // incomparable, so an edge added for one of them cannot create a new path
                // feeding another, and edges already re-added by earlier iterations are
                // visible to the reachability walk here.
                //
                // max(children) and min(parents) used to be applied here. They are
                // identities: children and parents are the covers of toRemove, and in a
                // transitively reduced diagram the elements of a cover are pairwise
                // incomparable, so neither can remove anything. Measured on ord6magic04,
                // the two calls walked 264 and 192 vertices on average, 6932 times each,
                // to return their own input -- about 15% of the total runtime spent
                // proving that nothing had to be dropped. The identity was checked on the
                // exhaustive sweep up to 12 cells, 20000 random contexts and the five
                // benchmark contexts, without a single counterexample.
                //
                // The checks are kept, behind FCA4J_ARES_ASSERT_A6: enabling it restores
                // both calls, which now throw if they ever remove anything. That is the
                // regression guard for an invariant the code silently depends on.
                ISet maxChildren = children;
                ISet minParents = parents;
                if (AresGuard.ASSERT_A6) {
                    maxChildren = max(children);
                    minParents = min(parents);
                    AresGuard.checkA6(children, maxChildren, parents, minParents);
                }
                for (Iterator<Integer> itc = maxChildren.iterator(); itc.hasNext();) {
                    int child = itc.next();
                    // toRemove is already isolated, so the walk cannot go through it
                    computeReachableTargets(child, minParents);
                    for (Iterator<Integer> itp = minParents.iterator(); itp.hasNext();) {
                        int p = itp.next();
                        if (!isReached(p)) {
                            link(child, p, "A6");
                        }
                    }
                }
                gsh.removeConcept(toRemove);
            }
        }

        private boolean visit(int c) throws CloneNotSupportedException {
            if (nonIntroducingConcepts.contains(c) || doNotCheck.contains(c)) {
                return true;
            }
            ISet extC = gsh.getConceptExtent(c);
            int cardC = extC.cardinality();
            // guard: the two extents must meet, unless one of them is empty
            if (cardC != 0 && cardA != 0 && !extC.intersects(extA)) {
                return true;
            }

            // one intersection scan drives the whole dispatch:
            //   rc = extent(c) \ g(a) is empty  <=>  |extent(c) inter g(a)| == |extent(c)|
            //   ra = g(a) \ extent(c) is empty  <=>  |extent(c) inter g(a)| == |g(a)|
            interScratch.setTo(extC);
            interScratch.retainAll(extA);
            int cardInter = interScratch.cardinality();
            boolean cInA = (cardInter == cardC);
            boolean aInC = (cardInter == cardA);

            if (cInA && aInC) { //case 1 extent of c is g(a)
                gsh.getConceptIntent(c).add(a);
                gsh.getConceptReducedIntent(c).add(a);
                return false;
            }

            if (cInA) { //case 2 c subconcept of g(a)
                subConceptsOfA.add(c);
                gsh.getConceptIntent(c).add(a);
                reducedExtentInto(c, redScratch);
                extentOfA.removeAll(redScratch);
                return true;
            }

            if (aInC) { //case 3 c superconcept of g(a)
                if (!isCADefined) {
                            ca = gsh.addConcept(factory.createSet(matrix.getObjectCount()), factory.createSet(matrix.getAttributeCount()));
                    isCADefined = true;
                    gsh.getConceptIntent(ca).add(a);
                    gsh.getConceptReducedIntent(ca).add(a);
                    gsh.getConceptExtent(ca).addAll(extA);
                    reducedExtentInto(c, redScratch);
                    ISet extsC = gsh.getConceptReducedExtent(c);
                    extsC.addAll(redScratch);
                    if (redScratch.intersects(extA)) { // ec non empty
                        extsC.removeAll(extA);
                        if ((gsh.getConceptReducedIntent(c).cardinality() == 0 || !hasAllAConcepts())
                                && (extsC.cardinality() == 0 || !hasAllOConcepts())) {
                            nonIntroducingConcepts.add(c);
                        }
                    }
                    for (Iterator<Integer> it = max(subConceptsOfA).iterator(); it.hasNext();) {
                        int maxSub = it.next();
                        link(maxSub, ca, "A1");
                        unlink(maxSub, c);
                    }
                    // extent(ca) and lowerCover(ca) are both frozen from here to the end
                    // of the step (later case-3 hits only add ca -> c upper edges, and
                    // case 4 only touches c2), so the reduced extent of ca is invariant:
                    // computing it once replaces one clone + |lowerCover| removeAll per
                    // case-3 hit.
                    gsh.setReducedExtent(ca, computeReducedExtent(ca));
                }
                link(ca, c, "A2");
                gsh.getConceptIntent(ca).addAll(gsh.getConceptIntent(c));
                for (Iterator<Integer> it = gsh.getLowerCoverIterator(ca); it.hasNext();) {
                    int child = it.next();
                    unlink(child, c);
                }
                ISet allParents = gsh.getAllParents(c);
                doNotCheck.addAll(allParents);
                return true;
            }

            //case 4 c and g(a) not comparable
            if (!hasAllOConcepts()) {
                return true;
            }
            reducedExtentInto(c, redScratch);
            if (!redScratch.intersects(extA)) { // ec empty
                return true;
            }
            ISet extsC2 = redScratch.newIntersect(extA); // ec
            ISet extsC = gsh.getConceptReducedExtent(c);
            extsC.addAll(redScratch);
            int c2 = gsh.addConcept(factory.clone(interScratch), factory.createSet(matrix.getAttributeCount()),
                    extsC2, factory.createSet(matrix.getAttributeCount()));
            gsh.getConceptIntent(c2).addAll(gsh.getConceptIntent(c));
            gsh.getConceptIntent(c2).add(a);
            link(c2, c, "A3");
            computeMaximalSelectedDescendants(c, subConceptsOfA);
            for (int i = 0; i < selCount; i++) {
                int c3 = selBuf[i];
                if (domMark[c3] == domEpoch) {
                    continue; // dominated by another selected concept
                }
                link(c3, c2, "A4");
                unlink(c3, c);
            }
            subConceptsOfA.add(c2);
            for (Iterator<Integer> it = gsh.getLowerCoverIterator(c2); it.hasNext();) {
                int c2Child = it.next();
                unlink(c2Child, c);
            }
            extsC.removeAll(extsC2);
            if ((gsh.getConceptReducedIntent(c).cardinality() == 0 || !hasAllAConcepts())
                    && extsC.cardinality() == 0) {
                nonIntroducingConcepts.add(c);
            }
            extentOfA.removeAll(extsC2);
            return true;
        }
    }
}
