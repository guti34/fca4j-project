/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */

package fr.lirmm.fca4j.core.operator;

import java.util.Arrays;
import java.util.List;

/**
 * The scaling operator factory is responsible for recensing all the available
 * scaling operations and creating new operator object.
 */
public final class MyScalingOperatorFactory {
	
	/**
	 *  return the name of the default scaling operator.
	 *
	 * @return the string
	 */
	public static String defaultScalingOperator() {
		return "exist";
	}

	/**
	 * returns the list of available scaling operations some of them may be
	 * parameterized.
	 *
	 * @return the list
	 */
	public static List<String> listAvailableScaling() {
		String[] result = new String[] { "exist", "existForall", "existContains", "existForallN", "existContainsN",
				"equality" };
		return Arrays.asList(result);
	}

	/**
	 *  returns true if the scaling operation is parameterized.
	 *
	 * @param opName the op name
	 * @return true, if successful
	 */
	public static boolean hasParameter(String opName) {
		return opName.startsWith("existContainsN") || opName.startsWith("existForallN");
	}
	
	/**
	 * Gets the parameter.
	 *
	 * @param opName the op name
	 * @return the parameter
	 */
	public static float getParameter(String opName){
		if (opName.startsWith("existForallN")) {
			return Float.valueOf(opName.substring("existForallN".length()));
		} else if (opName.startsWith("existContainsN")) {
			return Float.valueOf(opName.substring("existContainsN".length()));
		}	
		return -1;
	}
	
	/**
	 * create a new scaling operator from the given name if the name cannot be
	 * understood, it will return an operator of the default type.
	 *
	 * @param opName the op name
	 * @return the abstract scaling operator
	 */
	public static AbstractScalingOperator createScalingOperator(String opName) {
		return createScalingOperator(opName, defaultScalingOperator());
	}

	/**
	 * Creates a new MyScalingOperator object.
	 *
	 * @param opName the op name
	 * @param defaultOp the default op
	 * @return the abstract scaling operator
	 */
	public static AbstractScalingOperator createScalingOperator(String opName, String defaultOp) {
		switch (opName) {
		case "exist":
			return new MyExistentialScaling();
		case "existForall":
			return new MyForAllExistentialScaling();
		case "existContains":
			return new MyContainsExistScaling();
		case "equality":
			return new MyEqualityScaling();
		}
		if (opName.startsWith("existForallN")) {
			float parameter = Float.valueOf(opName.substring("existForallN".length()));
			return new MyForNearlyAllExistentialScaling(parameter);
		} else if (opName.startsWith("existContainsN")) {
			float parameter = Float.valueOf(opName.substring("existContainsN".length()));
			return new MyContainsExistNScaling(parameter);

		}
		if (defaultOp == null)
			return null;
		else
			return createScalingOperator(defaultOp);

	}

}