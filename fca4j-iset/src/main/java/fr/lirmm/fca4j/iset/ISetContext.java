package fr.lirmm.fca4j.iset;

import java.util.Collection;

public interface ISetContext {
	public String getDefaultImplementation();
	public ISetFactory getDefaultFactory();
	public ISetFactory getFactory(String impl);
//	public ISetFactory createFactory(String impl);
	public Collection<ISetFactory> getImplementations();
	public default ISet cloneTo(ISet set, String impl) {
		return getFactory(impl).createSet(set.toBitSet(), set.capacity());
	}
}
