/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.iset;

import java.util.BitSet;

/**
 * A factory for creating ISet objects.
 *
 * @author agutierr
 */
public interface ISetFactory {
	
	/**
	 * Ordered.
	 *
	 * @return true, if successful
	 */
	public boolean ordered();
	
	/**
	 * Fixed size.
	 *
	 * @return true, if successful
	 */
	public boolean fixedSize();
	
	/**
	 * Name.
	 *
	 * @return the string
	 */
	public String name();
    
    /**
     * Creates a new ISet object.
     *
     * @return the new set
     */
    public ISet createSet();
    
    /**
     * Creates a new ISet object.
     *
     * @param bitset the bitset
     * @param size the size
     * @return the new set
     */
    public ISet createSet(BitSet bitset,int size);
    
    /**
     * Creates a new ISet object.
     *
     * @param bitset the bitset
     * @return the i set
     */
    public ISet createSet(BitSet bitset);
    
    /**
     * Creates a new ISet object.
     *
     * @param size the size
     * @return the new set
     */
    public ISet createSet(int size);
    
    /**
     * Clone.
     *
     * @param to_clone the set to clone
     * @return the new set
     */
    public ISet clone(ISet to_clone);
}
