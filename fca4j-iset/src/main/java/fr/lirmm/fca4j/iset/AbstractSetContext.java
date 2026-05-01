/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.iset;

import java.util.Collection;
import java.util.LinkedHashMap;

/**
 * The Class AbstractSetContext.
 */
public abstract class AbstractSetContext implements ISetContext {
	
	/** The factories. */
	private LinkedHashMap<String, ISetFactory> factories = new LinkedHashMap<>();

	/**
	 * Register factory.
	 *
	 * @param factory the factory to be registered
	 */
	public void registerFactory(ISetFactory factory) {
		factories.put(factory.name().toUpperCase(),factory);
	}
	
	/**
	 * Gets the default factory.
	 *
	 * @return the default factory
	 */
	@Override
	public ISetFactory getDefaultFactory() {
		return getFactory(getDefaultImplementation());
	}

	/**
	 * Gets the factory depending on implementation.
	 *
	 * @param impl the implementation
	 * @return the factory
	 */
	@Override
	public ISetFactory getFactory(String impl) {
		return factories.get(impl.toUpperCase());
	}

	/**
	 * Gets the different implementations.
	 *
	 * @return the implementations
	 */
	@Override
	public Collection<ISetFactory> getImplementations() {
		return factories.values();
	}

}
