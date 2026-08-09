/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.algo;

import java.util.ArrayList;
import java.util.Iterator;

import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.core.IConceptOrder;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.ISetFactory;
import fr.lirmm.fca4j.util.Chrono;

/**
 * The Class Lattice_AddExtent.
 *
 * <p>Incremental lattice construction: every attribute extent is inserted in
 * turn, splitting the concepts it meets. The order is maintained during the
 * construction, so there is no separate cover phase.
 *
 * <p><b>Full intents are no longer materialised unconditionally.</b> The
 * algorithm itself never reads the full intents — it only clones the generator's
 * (still empty) intent when creating a concept — so {@code computeIntents()} was
 * pure output preparation. It costs a full top-down traversal plus one intent set
 * per concept, for nothing when the consumer reads only the reduced sets, which
 * is the case of the LATTICE to JSON and XML outputs. It now follows the same
 * {@code needFullSets} contract as {@link Lattice_ParallelCbO}: set it for rule
 * extraction, DOT output, datalog descriptors or RCA.
 */
public class Lattice_AddExtent implements AbstractAlgo<IConceptOrder> {

	private IBinaryContext matrix;
	private IConceptOrder order;
	protected ISetFactory factory;
	private Chrono chrono;

	/**
	 * Whether the caller needs the FULL intents. Off by default: the reduced sets
	 * and the Hasse diagram are enough for the JSON and XML outputs. The full
	 * EXTENTS are always available: the algorithm maintains them itself.
	 */
	private boolean needFullSets = false;

	/**
	 * Instantiates a new lattice add extent.
	 *
	 * @param matrix the matrix
	 * @param chrono the chrono
	 */
	public Lattice_AddExtent(IBinaryContext matrix, Chrono chrono) {
		super();
		this.matrix = matrix;
		this.factory = matrix.getFactory();
		this.chrono = chrono;
	}

	public Lattice_AddExtent(IBinaryContext matrix) {
		this(matrix, null);
	}

	/**
	 * Enables/disables materialising the full intents (off by default).
	 *
	 * @param needFullSets whether the full intents are needed downstream
	 */
	public void setNeedFullSets(boolean needFullSets) {
		this.needFullSets = needFullSets;
	}

	public boolean isNeedFullSets() {
		return needFullSets;
	}

	/**
	 * Adds the extent.
	 *
	 * @param extent    the extent
	 * @param generator the generator
	 * @return the int
	 * @throws Exception the exception
	 */
	protected int addExtent(ISet extent, int generator) throws Exception {
		generator = getSmallestContainingConcept(extent, generator);
		if (extent.equals(order.getConceptExtent(generator))) {
			return generator;
		}
		ArrayList<Integer> newChildren = new ArrayList<>();
		Iterator<Integer> it = order.getLowerCoverIterator(generator);
		while (it.hasNext()) {
			int candidate = it.next();
			if (!order.getConceptExtent(candidate).containsAll(extent)) {
				ISet intersection = extent.newIntersect(order.getConceptExtent(candidate));
				candidate = addExtent(intersection, candidate);
			}
			boolean addChild = true;
			ArrayList<Integer> conceptsToDelete = new ArrayList<>();
			for (int child : newChildren) {
				if (order.getConceptExtent(child).containsAll(order.getConceptExtent(candidate))) {
					addChild = false;
					break;
				} else if (order.getConceptExtent(candidate).containsAll(order.getConceptExtent(child))) {
					conceptsToDelete.add(child);
				}
			}
			newChildren.removeAll(conceptsToDelete);
			if (addChild) {
				newChildren.add(candidate);
			}
		}
		int newConcept = order.addConcept(factory.clone(extent), factory.clone(order.getConceptIntent(generator)));
		for (int child : newChildren) {
			order.removePrecedenceConnection(child, generator);
			order.addPrecedenceConnection(child, newConcept);
		}
		order.addPrecedenceConnection(newConcept, generator);
		return newConcept;
	}

	/**
	 * get a concept whose extent contains the extent passed in parameter but whose
	 * children don't
	 *
	 * <p>Walks the lower covers with the iterator rather than
	 * {@code getLowerCoverSet}, which builds a fresh {@code HashSet} of boxed
	 * integers on every call — in the innermost loop of the whole algorithm.
	 */
	private int getSmallestContainingConcept(ISet extent, int generator) {
		boolean isMaximal = true;
		while (isMaximal) {
			isMaximal = false;
			for (Iterator<Integer> it = order.getLowerCoverIterator(generator); it.hasNext();) {
				int child = it.next();
				if (order.getConceptExtent(child).containsAll(extent)) {
					generator = child;
					isMaximal = true;
					break;
				}
			}
		}
		return generator;
	}

	/**
	 * Gets the description.
	 *
	 * @return the description
	 */
	@Override
	public String getDescription() {
		return "AddExtent";
	}

	/**
	 * Gets the result.
	 *
	 * @return the result
	 */
	@Override
	public IConceptOrder getResult() {
		return order;
	}

	/**
	 * Run.
	 */
	@Override
	public void run() {
		try {
			if (chrono != null) {
				chrono.start("enum");
			}
			order = new ConceptOrder("LatticeWithAddExtent", matrix, getDescription());
			ISet allObjects = factory.createSet(matrix.getObjectCount());
			allObjects.fill(matrix.getObjectCount());
			int top = order.addConcept(allObjects, factory.createSet());
			for (int numAttr = 0; numAttr < matrix.getAttributeCount(); numAttr++) {
				int concept = addExtent(matrix.getExtent(numAttr), top);
				order.getConceptReducedIntent(concept).add(numAttr);
			}
			if (chrono != null) {
				chrono.stop("enum");
			}

			// Reduced extents: an object is introduced at the smallest concept still
			// containing it. Same rule as the parallel CbO builder and the C kernel.
			if (chrono != null) {
				chrono.start("rextents");
			}
			for (Iterator<Integer> it = order.getBasicIterator(); it.hasNext();) {
				int concept = it.next();
				ISet rExtent = factory.clone(order.getConceptExtent(concept));
				for (Iterator<Integer> itChild = order.getLowerCoverIterator(concept); itChild.hasNext();) {
					rExtent.removeAll(order.getConceptExtent(itChild.next()));
				}
				order.getConceptReducedExtent(concept).addAll(rExtent);
			}
			if (chrono != null) {
				chrono.stop("rextents");
			}

			// Full intents only on demand: the algorithm never reads them, and the
			// JSON/XML writers use the reduced sets only.
			if (needFullSets) {
				if (chrono != null) {
					chrono.start("intents");
				}
				order.computeIntents();
				if (chrono != null) {
					chrono.stop("intents");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
