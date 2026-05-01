/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.iset;

import java.util.Collection;

/**
 * The Interface ISetContext.
 */
public interface ISetContext {
	
	/**
	 * Gets the default implementation.
	 *
	 * @return the default implementation
	 */
	public String getDefaultImplementation();
	
	/**
	 * Gets the default factory.
	 *
	 * @return the default factory
	 */
	public ISetFactory getDefaultFactory();
	
	/**
	 * Gets the factory.
	 *
	 * @param impl the impl
	 * @return the factory
	 */
	public ISetFactory getFactory(String impl);

/**
 * Gets the implementations.
 *
 * @return the implementations
 */
//	public ISetFactory createFactory(String impl);
	public Collection<ISetFactory> getImplementations();
	
	/**
	 * Clone to.
	 *
	 * @param set the set
	 * @param impl the impl
	 * @return the i set
	 */
	public default ISet cloneTo(ISet set, String impl) {
		return getFactory(impl).createSet(set.toBitSet(), set.capacity());
	}
}
