package fr.lirmm.fca4j.core;

import java.util.ArrayList;

public class ConceptOrderFamily {
	protected ArrayList<ConceptOrder> conceptOrders;
	private int stepNb;

	/**
	 * Instantiates a new my concept order family.
	 */
	public ConceptOrderFamily() {
		super();
		conceptOrders = new ArrayList<>();
	}

	/**
	 * Adds the concept order.
	 *
	 * @param e the e
	 * @return true, if successful
	 */
	public boolean addConceptOrder(ConceptOrder e) {
		return conceptOrders.add(e);
	}

	/**
	 * Gets the concept orders.
	 *
	 * @return the concept orders
	 */
	public ArrayList<ConceptOrder> getConceptOrders() {
		return conceptOrders;
	}

	/**
	 * This information is valuable for the classical RCA as the concept generation
	 * is monotonous.
	 *
	 * @return the int
	 */
	public int totalConceptNb() {
		int result = 0;
		for (ConceptOrder co : conceptOrders) {
			result += co.getConceptCount();
		}
		return result;

	}

	/**
	 * Gets the step nb.
	 *
	 * @return the step nb
	 */
	public int getStepNb() {
		return stepNb;
	}

	/**
	 * Sets the step nb.
	 *
	 * @param stepNb the new step nb
	 */
	public void setStepNb(int stepNb) {
		this.stepNb = stepNb;
	}

}
