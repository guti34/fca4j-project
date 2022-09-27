/*
BSD 3-Clause License

Copyright (c) 2022 LIRMM
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

   * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
   * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
   * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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