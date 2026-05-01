/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.core.operator;

import java.util.Iterator;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;

/**
 * The Class MyForNearlyAllExistentialScaling.
 */
public class MyForNearlyAllExistentialScaling extends AbstractScalingOperator {
	private float x;

	/**
	 * Instantiates a new my for nearly all existential scaling.
	 *
	 * @param parameter the parameter
	 */
	public MyForNearlyAllExistentialScaling(float parameter) {
		super();
		x = parameter;
	}

	/**
	 * Scale.
	 *
	 * @param e       the e
	 * @param c       the c
	 * @param context the context
	 * @return true, if successful
	 */
	public boolean scale(int e, ISet c, IBinaryContext context) {
		if (c.isEmpty()||context.getIntent(e).isEmpty())
			return false;
		ISet inter=context.getIntent(e).newIntersect(c);
		int threshold = (int) ((x * context.getIntent(e).cardinality()) / 100);
		return inter.cardinality() >= threshold;
	}

	/**
	 * Gets the name.
	 *
	 * @return the name
	 */
	@Override
	public String getName() {
		if (Math.ceil(x) == x)
			return "existForallN" + (int) x;
		return "existForallN" + x;
	}

}
