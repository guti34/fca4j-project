/*
 * Copyright (c) 2013-2016 ENGEES. All rights reserved.
 * This file is part of RCAExplore.
 * 
 *  RCAExplore is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  RCAExplore is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with RCAExplore.  If not, see <http://www.gnu.org/licenses/>.
 *  
 *  Authors : 
 *  - Xavier Dolques
 */

package fr.lirmm.fca4j.core.operator;

import java.lang.reflect.GenericArrayType;
import java.util.Arrays;
import java.util.List;

/**
 * The scaling operator factory is responsible for recensing all the available
 * scaling operations and creating new operator object
 */
public final class MyScalingOperatorFactory {
	/** return the name of the default scaling operator */
	public static String defaultScalingOperator() {
		return "exist";
	}

	/**
	 * returns the list of available scaling operations some of them may be
	 * parameterized
	 */
	public static List<String> listAvailableScaling() {
		String[] result = new String[] { "exist", "existForall", "existContains", "existForallN", "existContainsN",
				"equality" };
		return Arrays.asList(result);
	}

	/** returns true if the scaling operation is parameterized */
	public static boolean hasParameter(String opName) {
		return opName.startsWith("existContainsN") || opName.startsWith("existForallN");
	}
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
	 * understood, it will return an operator of the default type
	 */
	public static AbstractScalingOperator createScalingOperator(String opName) {
		return createScalingOperator(opName, defaultScalingOperator());
	}

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