package fr.lirmm.fca4j.iset;

import java.util.Collection;
import java.util.LinkedHashMap;

public abstract class AbstractSetContext implements ISetContext {
	private LinkedHashMap<String, ISetFactory> factories = new LinkedHashMap<>();

	public void registerFactory(ISetFactory factory) {
		factories.put(factory.name().toUpperCase(),factory);
	}
	@Override
	public ISetFactory getDefaultFactory() {
		return getFactory(getDefaultImplementation());
	}

	@Override
	public ISetFactory getFactory(String impl) {
		return factories.get(impl.toUpperCase());
	}

	@Override
	public Collection<ISetFactory> getImplementations() {
		return factories.values();
	}

}
