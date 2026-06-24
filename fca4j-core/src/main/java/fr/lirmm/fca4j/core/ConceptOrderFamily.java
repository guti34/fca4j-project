/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.core;

import java.util.ArrayList;

public class ConceptOrderFamily {
	protected ArrayList<IConceptOrder> conceptOrders;
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
	public boolean addConceptOrder(IConceptOrder e) {
		return conceptOrders.add(e);
	}

	/**
	 * Gets the concept orders.
	 *
	 * @return the concept orders
	 */
	public ArrayList<IConceptOrder> getConceptOrders() {
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
		for (IConceptOrder co : conceptOrders) {
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
