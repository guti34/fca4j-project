/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.command;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISetContext;

/**
 * The Class Conversion.
 */
public class Conversion extends MatrixTransform {

	/**
	 * Instantiates a new conversion.
	 *
	 * @param setContext the set context
	 */
	public Conversion(ISetContext setContext) {
		super("convert", "convert formal context file format",setContext);
	}
		
		/**
		 * Transform the binary context.
		 *
		 * @return the resulting binary context
		 * @throws Exception the exception
		 */
		@Override
		protected IBinaryContext transform()  throws Exception{
			return getContext();
		}

}
