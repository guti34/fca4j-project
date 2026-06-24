/*
 * Copyright (c) 2026 LIRMM — BSD 3-Clause License
 */
package fr.lirmm.fca4j.core;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.BitSet;
import java.beans.PropertyChangeListener;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;

/**
 * Memory-lean {@link IConceptOrder} for the build-once / read path (e.g.
 * LATTICE -> JSON): concepts are stored in dense arrays indexed by id (no
 * HashMaps, no boxed keys) and the Hasse diagram is stored as flat CSR
 * {@code int[]} adjacency (no per-edge objects, no JGraphT). On big lattices this
 * removes the two dominant heap sinks: ~76 bytes/edge of JGraphT shrinks to a few
 * {@code int[]}, and the four-HashMap concept store becomes four arrays.
 *
 * <p>Edges are buffered as they are added and the CSR is (re)built lazily on the
 * first structural read. Full extents/intents are optional (kept only if passed /
 * computed); the JSON output uses the reduced sets only. The advanced graph
 * operations the writer never touches on the default path (transitive
 * reduction/closure) are intentionally unsupported.
 */
public class CsrConceptOrder implements IConceptOrder {

	private String id;
	private final String algoName;
	private IBinaryContext context;
	private ISetFactory factory;

	private int n = 0;
	private ISet[] extents;  // full (nullable)
	private ISet[] intents;  // full (nullable)
	private ISet[] rextents;
	private ISet[] rintents;

	// edge buffer (lower -> greater); CSR built lazily
	private int[] bufLo = new int[16];
	private int[] bufGr = new int[16];
	private int bufSize = 0;

	private boolean csrBuilt = false;
	private int[] outPtr, outAdj, inPtr, inAdj;
	private ISet maximals, minimals;

	public CsrConceptOrder(String id, IBinaryContext context, String algoName) {
		this.id = id;
		this.context = context;
		this.algoName = algoName;
		this.factory = context.getFactory();
		int cap = 16;
		extents = new ISet[cap];
		intents = new ISet[cap];
		rextents = new ISet[cap];
		rintents = new ISet[cap];
	}

	// ---- concept storage ---------------------------------------------------

	private void ensureConcept(int idx) {
		if (idx >= rextents.length) {
			int nl = Math.max(idx + 1, rextents.length << 1);
			extents = Arrays.copyOf(extents, nl);
			intents = Arrays.copyOf(intents, nl);
			rextents = Arrays.copyOf(rextents, nl);
			rintents = Arrays.copyOf(rintents, nl);
		}
	}

	@Override
	public int addConcept(ISet extent, ISet intent) {
		return addConcept(extent, intent, factory.createSet(context.getObjectCount()),
				factory.createSet(context.getAttributeCount()));
	}

	@Override
	public int addConcept(ISet extent, ISet intent, ISet rextent, ISet rintent) {
		int idx = n++;
		ensureConcept(idx);
		extents[idx] = extent;
		intents[idx] = intent;
		rextents[idx] = rextent;
		rintents[idx] = rintent;
		return idx;
	}

	@Override public ISet getConceptExtent(int c) { return extents[c]; }
	@Override public ISet getConceptIntent(int c) { return intents[c]; }
	@Override public ISet getConceptReducedExtent(int c) { return rextents[c]; }
	@Override public ISet getConceptReducedIntent(int c) { return rintents[c]; }
	@Override public void setReducedExtent(int c, ISet s) { rextents[c] = s; }
	@Override public void setReducedIntent(int c, ISet s) { rintents[c] = s; }

	@Override
	public void removeConcept(int numConcept) {
		throw new UnsupportedOperationException("CsrConceptOrder is build-once; removeConcept is not supported");
	}

	// ---- edges -------------------------------------------------------------

	@Override
	public void addPrecedenceConnection(int lower, int greater) {
		if (bufSize == bufLo.length) {
			bufLo = Arrays.copyOf(bufLo, bufLo.length << 1);
			bufGr = Arrays.copyOf(bufGr, bufGr.length << 1);
		}
		bufLo[bufSize] = lower;
		bufGr[bufSize] = greater;
		bufSize++;
		csrBuilt = false;
	}

	@Override
	public void addPrecedenceConnections(int[] lowers, int[] greaters) {
		int need = bufSize + lowers.length;
		if (need > bufLo.length) {
			int nl = Math.max(need, bufLo.length << 1);
			bufLo = Arrays.copyOf(bufLo, nl);
			bufGr = Arrays.copyOf(bufGr, nl);
		}
		System.arraycopy(lowers, 0, bufLo, bufSize, lowers.length);
		System.arraycopy(greaters, 0, bufGr, bufSize, greaters.length);
		bufSize += lowers.length;
		csrBuilt = false;
	}

	@Override
	public void removePrecedenceConnection(int lower, int greater) {
		int w = 0;
		for (int i = 0; i < bufSize; i++) {
			if (bufLo[i] == lower && bufGr[i] == greater) {
				continue;
			}
			bufLo[w] = bufLo[i];
			bufGr[w] = bufGr[i];
			w++;
		}
		bufSize = w;
		csrBuilt = false;
	}

	private void buildCsr() {
		outPtr = new int[n + 1];
		inPtr = new int[n + 1];
		for (int i = 0; i < bufSize; i++) {
			outPtr[bufLo[i] + 1]++;
			inPtr[bufGr[i] + 1]++;
		}
		for (int v = 0; v < n; v++) {
			outPtr[v + 1] += outPtr[v];
			inPtr[v + 1] += inPtr[v];
		}
		outAdj = new int[bufSize];
		inAdj = new int[bufSize];
		int[] oc = Arrays.copyOf(outPtr, n + 1);
		int[] ic = Arrays.copyOf(inPtr, n + 1);
		for (int i = 0; i < bufSize; i++) {
			outAdj[oc[bufLo[i]]++] = bufGr[i];
			inAdj[ic[bufGr[i]]++] = bufLo[i];
		}
		maximals = factory.createSet(Math.max(1, n));
		minimals = factory.createSet(Math.max(1, n));
		for (int v = 0; v < n; v++) {
			if (outPtr[v + 1] - outPtr[v] == 0) {
				maximals.add(v);
			}
			if (inPtr[v + 1] - inPtr[v] == 0) {
				minimals.add(v);
			}
		}
		csrBuilt = true;
	}

	private void ensureCsr() {
		if (!csrBuilt) {
			buildCsr();
		}
	}

	@Override public int inDegreeOf(int c) { ensureCsr(); return inPtr[c + 1] - inPtr[c]; }
	@Override public int outDegreeOf(int c) { ensureCsr(); return outPtr[c + 1] - outPtr[c]; }

	@Override
	public ISet getLowerCover(int c) {
		ensureCsr();
		ISet s = factory.createSet(Math.max(1, n));
		for (int k = inPtr[c]; k < inPtr[c + 1]; k++) {
			s.add(inAdj[k]);
		}
		return s;
	}

	@Override
	public ISet getUpperCover(int c) {
		ensureCsr();
		ISet s = factory.createSet(Math.max(1, n));
		for (int k = outPtr[c]; k < outPtr[c + 1]; k++) {
			s.add(outAdj[k]);
		}
		return s;
	}

	@Override public ISet getMaximals() { ensureCsr(); return maximals; }
	@Override public ISet getMinimals() { ensureCsr(); return minimals; }
	@Override public int getTop() { ensureCsr(); return maximals.iterator().next(); }
	@Override public int getBottom() { ensureCsr(); return minimals.iterator().next(); }

	// ---- iterators ---------------------------------------------------------

	private static Iterator<Integer> arrayIterator(final int[] a) {
		return new Iterator<Integer>() {
			private int i = 0;

			@Override public boolean hasNext() { return i < a.length; }

			@Override public Integer next() {
				if (i >= a.length) {
					throw new NoSuchElementException();
				}
				return a[i++];
			}
		};
	}

	@Override
	public Iterator<Integer> getBasicIterator() {
		int[] a = new int[n];
		for (int i = 0; i < n; i++) {
			a[i] = i;
		}
		return arrayIterator(a);
	}

	/** Topological order on edges lower->greater: minimals first (bottom-up). */
	private int[] topoBottomUp() {
		ensureCsr();
		int[] indeg = new int[n];
		for (int v = 0; v < n; v++) {
			indeg[v] = inPtr[v + 1] - inPtr[v];
		}
		int[] order = new int[n];
		int[] queue = new int[n];
		int qh = 0, qt = 0;
		for (int v = 0; v < n; v++) {
			if (indeg[v] == 0) {
				queue[qt++] = v;
			}
		}
		int oi = 0;
		while (qh < qt) {
			int v = queue[qh++];
			order[oi++] = v;
			for (int k = outPtr[v]; k < outPtr[v + 1]; k++) {
				int w = outAdj[k];
				if (--indeg[w] == 0) {
					queue[qt++] = w;
				}
			}
		}
		return order; // oi == n for a DAG
	}

	@Override
	public Iterator<Integer> getBottomUpIterator() {
		return arrayIterator(topoBottomUp());
	}

	@Override
	public Iterator<Integer> getTopDownIterator() {
		int[] bu = topoBottomUp();
		int[] td = new int[bu.length];
		for (int i = 0; i < bu.length; i++) {
			td[i] = bu[bu.length - 1 - i];
		}
		return arrayIterator(td);
	}

	@Override public int getConceptCount() { return n; }
	@Override public int getEdgeCount() { return bufSize; }
	@Override public String getId() { return id; }
	@Override public String getAlgoName() { return algoName; }
	@Override public IBinaryContext getContext() { return context; }

	// reflexive transitive descendants (down via lower covers) / ancestors (up)
	@Override
	public ISet getAllChildren(int concept) {
		return reachable(concept, true);
	}

	@Override
	public ISet getAllParents(int concept) {
		return reachable(concept, false);
	}

	private ISet reachable(int start, boolean down) {
		ensureCsr();
		ISet res = factory.createSet(Math.max(1, n));
		int[] stack = new int[16];
		int sp = 0;
		stack[sp++] = start;
		while (sp > 0) {
			int v = stack[--sp];
			if (res.contains(v)) {
				continue;
			}
			res.add(v);
			int from = down ? inPtr[v] : outPtr[v];
			int to = down ? inPtr[v + 1] : outPtr[v + 1];
			int[] adj = down ? inAdj : outAdj;
			for (int k = from; k < to; k++) {
				if (sp == stack.length) {
					stack = Arrays.copyOf(stack, stack.length << 1);
				}
				stack[sp++] = adj[k];
			}
		}
		return res;
	}

	@Override
	public List<Integer> getShortestPath(int vertex1, int vertex2) {
		ensureCsr();
		int[] prev = new int[n];
		Arrays.fill(prev, -2); // unvisited
		int[] queue = new int[n];
		int qh = 0, qt = 0;
		queue[qt++] = vertex1;
		prev[vertex1] = -1;
		while (qh < qt) {
			int v = queue[qh++];
			if (v == vertex2) {
				break;
			}
			for (int k = outPtr[v]; k < outPtr[v + 1]; k++) {
				int w = outAdj[k];
				if (prev[w] == -2) {
					prev[w] = v;
					queue[qt++] = w;
				}
			}
		}
		if (prev[vertex2] == -2) {
			return null;
		}
		ArrayList<Integer> path = new ArrayList<>();
		for (int v = vertex2; v != -1; v = prev[v]) {
			path.add(0, v);
		}
		return path;
	}

	@Override
	public ConceptOrder clone(ISetFactory newFactory) {
		ConceptOrder copy = new ConceptOrder(id, context, algoName);
		for (int i = 0; i < n; i++) {
			copy.addConcept(remap(extents[i], newFactory), remap(intents[i], newFactory),
					remap(rextents[i], newFactory), remap(rintents[i], newFactory));
		}
		copy.addPrecedenceConnections(Arrays.copyOf(bufLo, bufSize), Arrays.copyOf(bufGr, bufSize));
		return copy;
	}

	private static ISet remap(ISet s, ISetFactory f) {
		return s == null ? null : f.createSet(s.toBitSet(), s.capacity());
	}

	// ---- operations used by the lattice builders (not in IConceptOrder) ----

	private ISet substitution(ISet set, List<ISet> classes) {
		ISet result = factory.createSet(context.getObjectCount() + context.getAttributeCount());
		for (Iterator<Integer> it = set.iterator(); it.hasNext();) {
			result.addAll(classes.get(it.next()));
		}
		return result;
	}

	/** Remap only the reduced sets (clarified -> original); the Hasse is unchanged. */
	@Override
	public void substitutionReduced(IBinaryContext notClarified, List<ISet> attrClasses, List<ISet> objClasses) {
		this.context = notClarified;
		for (int i = 0; i < n; i++) {
			rextents[i] = substitution(rextents[i], objClasses);
			rintents[i] = substitution(rintents[i], attrClasses);
		}
	}

	/** Remap reduced and full sets (when full intents/extents were materialised). */
	@Override
	public void substitution(IBinaryContext notClarified, List<ISet> attrClasses, List<ISet> objClasses) {
		this.context = notClarified;
		for (int i = 0; i < n; i++) {
			if (extents[i] != null) {
				extents[i] = substitution(extents[i], objClasses);
			}
			if (intents[i] != null) {
				intents[i] = substitution(intents[i], attrClasses);
			}
			rextents[i] = substitution(rextents[i], objClasses);
			rintents[i] = substitution(rintents[i], attrClasses);
		}
	}

	/** Turn the order of the transposed context into that of the original. */
	@Override
	public void dual(IBinaryContext originalContext) {
		ISet[] t = extents; extents = intents; intents = t;
		t = rextents; rextents = rintents; rintents = t;
		for (int i = 0; i < bufSize; i++) {
			int tmp = bufLo[i];
			bufLo[i] = bufGr[i];
			bufGr[i] = tmp;
		}
		csrBuilt = false; // CSR + extrema recomputed (roles swapped)
		this.context = originalContext;
	}

	/** Full intents (top-down propagation), only when needed downstream. */
	@Override
	public void computeIntents() {
		ensureCsr();
		for (int i = 0; i < n; i++) {
			if (intents[i] == null) {
				intents[i] = factory.createSet(context.getAttributeCount());
			}
		}
		for (Iterator<Integer> it = getTopDownIterator(); it.hasNext();) {
			int c = it.next();
			intents[c].addAll(rintents[c]);
			for (int k = inPtr[c]; k < inPtr[c + 1]; k++) {
				intents[inAdj[k]].addAll(intents[c]);
			}
		}
	}

	// ===== parity with ConceptOrder's public surface =======================

	@Override
	public Set<Integer> getConcepts() {
		return new AbstractSet<Integer>() {
			@Override public Iterator<Integer> iterator() { return getBasicIterator(); }
			@Override public int size() { return n; }
			@Override public boolean contains(Object o) {
				return (o instanceof Integer) && ((Integer) o) >= 0 && ((Integer) o) < n;
			}
		};
	}

	@Override
	public Set<Integer> getLowerCoverSet(int c) {
		ensureCsr();
		Set<Integer> set = new HashSet<>();
		for (int k = inPtr[c]; k < inPtr[c + 1]; k++) {
			set.add(inAdj[k]);
		}
		return set;
	}

	@Override
	public Set<Integer> getUpperCoverSet(int c) {
		ensureCsr();
		Set<Integer> set = new HashSet<>();
		for (int k = outPtr[c]; k < outPtr[c + 1]; k++) {
			set.add(outAdj[k]);
		}
		return set;
	}

	private static Iterator<Integer> sliceIterator(final int[] a, final int from, final int to) {
		return new Iterator<Integer>() {
			private int i = from;
			@Override public boolean hasNext() { return i < to; }
			@Override public Integer next() {
				if (i >= to) { throw new NoSuchElementException(); }
				return a[i++];
			}
		};
	}

	@Override
	public Iterator<Integer> getLowerCoverIterator(int c) {
		ensureCsr();
		return sliceIterator(inAdj, inPtr[c], inPtr[c + 1]);
	}

	@Override
	public Iterator<Integer> getUpperCoverIterator(int c) {
		ensureCsr();
		return sliceIterator(outAdj, outPtr[c], outPtr[c + 1]);
	}

	@Override public boolean isFusion(int c) { return rextents[c].cardinality() > 1; }
	@Override public boolean isNewConcept(int c) { return rextents[c].cardinality() == 0; }

	@Override
	public boolean isDummy(int c) {
		ISet e = extents[c];
		ISet in = intents[c];
		if (e == null || in == null) {
			throw new IllegalStateException(
					"isDummy needs full extents/intents; call computeExtents()/computeIntents() first");
		}
		return e.cardinality() == 0 || in.cardinality() == 0;
	}

	@Override
	public ArrayList<Integer> sortByExtent(boolean increasing) {
		return sortBy(extents, increasing);
	}

	@Override
	public ArrayList<Integer> sortByIntent(boolean increasing) {
		return sortBy(intents, increasing);
	}

	private ArrayList<Integer> sortBy(final ISet[] sets, final boolean increasing) {
		ArrayList<Integer> list = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			list.add(i);
		}
		list.sort((a, b) -> {
			int d = Integer.compare(sets[a].cardinality(), sets[b].cardinality());
			return increasing ? d : -d;
		});
		return list;
	}

	// ---- traversals (CSR). Reversed-graph orientation = top (maximals) down. ---
	// These visit every concept exactly once; the exact visit order may differ
	// from the JGraphT-backed ConceptOrder, but any consumer that only needs a
	// full traversal (e.g. the implication families, which yield a set) is
	// unaffected.

	private int[] sortedExtrema(ISet extrema) {
		List<Integer> l = extrema.toList();
		int[] a = new int[l.size()];
		for (int i = 0; i < a.length; i++) {
			a[i] = l.get(i);
		}
		Arrays.sort(a);
		return a;
	}

	@Override
	public Iterator<Integer> getTopologicalIterator() {
		return getTopDownIterator();
	}

	@Override
	public Iterator<Integer> getDepthFirstIterator() {
		ensureCsr();
		int[] order = new int[n];
		boolean[] seen = new boolean[n];
		int oi = 0;
		int[] stack = new int[16];
		int[] roots = sortedExtrema(maximals);
		int ri = 0, sweep = 0;
		while (oi < n) {
			int root = -1;
			while (ri < roots.length) {
				if (!seen[roots[ri]]) { root = roots[ri]; break; }
				ri++;
			}
			if (root == -1) {
				while (sweep < n && seen[sweep]) { sweep++; }
				if (sweep < n) { root = sweep; }
			}
			if (root == -1) { break; }
			int sp = 0;
			stack[sp++] = root;
			while (sp > 0) {
				int v = stack[--sp];
				if (seen[v]) { continue; }
				seen[v] = true;
				order[oi++] = v;
				for (int k = inPtr[v]; k < inPtr[v + 1]; k++) {
					int w = inAdj[k];
					if (!seen[w]) {
						if (sp == stack.length) { stack = Arrays.copyOf(stack, stack.length << 1); }
						stack[sp++] = w;
					}
				}
			}
		}
		return arrayIterator(order);
	}

	@Override
	public Iterator<Integer> getBreadthFirstIterator() {
		ensureCsr();
		int[] order = new int[n];
		boolean[] seen = new boolean[n];
		int oi = 0;
		int[] queue = new int[Math.max(1, n)];
		int qh = 0, qt = 0;
		for (int m : sortedExtrema(maximals)) {
			if (!seen[m]) { seen[m] = true; queue[qt++] = m; }
		}
		int sweep = 0;
		while (oi < n) {
			if (qh < qt) {
				int v = queue[qh++];
				order[oi++] = v;
				for (int k = inPtr[v]; k < inPtr[v + 1]; k++) {
					int w = inAdj[k];
					if (!seen[w]) { seen[w] = true; queue[qt++] = w; }
				}
			} else {
				while (sweep < n && seen[sweep]) { sweep++; }
				if (sweep < n) { seen[sweep] = true; queue[qt++] = sweep; } else { break; }
			}
		}
		return arrayIterator(order);
	}

	// ---- implication families (require full intents/extents) ---------------

	private List<Implication> implications(Iterator<Integer> order) {
		ensureCsr();
		ArrayList<Implication> impls = new ArrayList<>();
		while (order.hasNext()) {
			int concept = order.next();
			ISet premise = rintents[concept];
			if (premise == null || premise.isEmpty()) {
				continue;
			}
			ISet support = extents[concept];
			for (int k = outPtr[concept]; k < outPtr[concept + 1]; k++) {
				ISet conclusion = intents[outAdj[k]];
				if (conclusion == null || support == null) {
					throw new IllegalStateException(
							"implications need full intents/extents; call computeIntents()/computeExtents() first");
				}
				impls.add(new Implication(premise, conclusion, support));
			}
		}
		return impls;
	}

	@Override public List<Implication> getDepthFirstImplications() { return implications(getDepthFirstIterator()); }
	@Override public List<Implication> getBreadthFirstImplications() { return implications(getBreadthFirstIterator()); }
	@Override public List<Implication> getTopologicalImplications() { return implications(getTopologicalIterator()); }

	// ---- transitive reduction / closure (rebuild the edge buffer) ----------

	@Override
	public void reduce() {
		ensureCsr();
		ArrayList<int[]> kept = new ArrayList<>();
		boolean[] reach = new boolean[n];
		int[] stack = new int[16];
		for (int u = 0; u < n; u++) {
			Arrays.fill(reach, false);
			int sp = 0;
			// seed with the successors of u's direct successors (paths of length >= 2)
			for (int k = outPtr[u]; k < outPtr[u + 1]; k++) {
				int s = outAdj[k];
				for (int j = outPtr[s]; j < outPtr[s + 1]; j++) {
					if (sp == stack.length) { stack = Arrays.copyOf(stack, stack.length << 1); }
					stack[sp++] = outAdj[j];
				}
			}
			while (sp > 0) {
				int v = stack[--sp];
				if (reach[v]) { continue; }
				reach[v] = true;
				for (int k = outPtr[v]; k < outPtr[v + 1]; k++) {
					int w = outAdj[k];
					if (!reach[w]) {
						if (sp == stack.length) { stack = Arrays.copyOf(stack, stack.length << 1); }
						stack[sp++] = w;
					}
				}
			}
			for (int k = outPtr[u]; k < outPtr[u + 1]; k++) {
				int v = outAdj[k];
				if (!reach[v]) { kept.add(new int[] { u, v }); } // v not reachable via a longer path
			}
		}
		rebuildEdges(kept);
	}

	@Override
	public void closure() {
		ensureCsr();
		ArrayList<int[]> all = new ArrayList<>();
		boolean[] seen = new boolean[n];
		int[] stack = new int[16];
		for (int u = 0; u < n; u++) {
			Arrays.fill(seen, false);
			int sp = 0;
			for (int k = outPtr[u]; k < outPtr[u + 1]; k++) {
				if (sp == stack.length) { stack = Arrays.copyOf(stack, stack.length << 1); }
				stack[sp++] = outAdj[k];
			}
			while (sp > 0) {
				int v = stack[--sp];
				if (seen[v]) { continue; }
				seen[v] = true;
				all.add(new int[] { u, v });
				for (int k = outPtr[v]; k < outPtr[v + 1]; k++) {
					int w = outAdj[k];
					if (!seen[w]) {
						if (sp == stack.length) { stack = Arrays.copyOf(stack, stack.length << 1); }
						stack[sp++] = w;
					}
				}
			}
		}
		rebuildEdges(all);
	}

	private void rebuildEdges(ArrayList<int[]> edges) {
		bufSize = 0;
		if (edges.size() > bufLo.length) {
			bufLo = new int[edges.size()];
			bufGr = new int[edges.size()];
		}
		for (int[] e : edges) {
			bufLo[bufSize] = e[0];
			bufGr[bufSize] = e[1];
			bufSize++;
		}
		csrBuilt = false;
	}

	// ---- full extents / context population ---------------------------------

	@Override
	public void computeExtents() {
		ensureCsr();
		for (int i = 0; i < n; i++) {
			if (extents[i] == null) {
				extents[i] = factory.createSet(context.getObjectCount());
			}
		}
		for (Iterator<Integer> it = getBottomUpIterator(); it.hasNext();) {
			int c = it.next();
			extents[c].addAll(rextents[c]);
			for (int k = outPtr[c]; k < outPtr[c + 1]; k++) {
				extents[outAdj[k]].addAll(extents[c]);
			}
		}
	}

	@Override
	public void buildExtentIntent() {
		computeIntents();
		computeExtents();
		for (int c = 0; c < n; c++) {
			for (Iterator<Integer> it = rintents[c].iterator(); it.hasNext();) {
				context.setExtent(it.next(), extents[c]);
			}
			for (Iterator<Integer> it = rextents[c].iterator(); it.hasNext();) {
				context.setIntent(it.next(), intents[c]);
			}
		}
	}

	@Override
	public void populate(int[] concepts, int[] edges, BitSet[] bitsets) {
		int maxId = -1;
		for (int c : concepts) {
			maxId = Math.max(maxId, c);
		}
		ensureConcept(maxId);
		if (maxId + 1 > n) {
			n = maxId + 1;
		}
		int ex = 0, in = 1;
		for (int i = 0; i < concepts.length; i++, ex += 2, in += 2) {
			int c = concepts[i];
			rextents[c] = factory.createSet(bitsets[ex], context.getObjectCount());
			rintents[c] = factory.createSet(bitsets[in], context.getAttributeCount());
		}
		int[] lo = new int[edges.length / 2];
		int[] gr = new int[edges.length / 2];
		for (int e = 0, j = 0; e + 1 < edges.length; e += 2, j++) {
			lo[j] = edges[e];
			gr[j] = edges[e + 1];
		}
		addPrecedenceConnections(lo, gr);
	}

	@Override public void setId(String name) { this.id = name; }

	@Override
	public ConceptOrder clone() {
		return clone(factory);
	}

	// CsrConceptOrder is build-once and fires no events; listeners are accepted
	// but never notified.
	@Override public void addPropertyChangeListener(PropertyChangeListener l) { }
	@Override public void removePropertyChangeListener(PropertyChangeListener l) { }
}
