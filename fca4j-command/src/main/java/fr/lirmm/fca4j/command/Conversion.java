package fr.lirmm.fca4j.command;

import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISetContext;

public class Conversion extends MatrixTransform {

	public Conversion(ISetContext setContext) {
		super("convert", "convert formal context file format",setContext);
	}
		@Override
		protected IBinaryContext transform()  throws Exception{
			return getContext();
		}

}
