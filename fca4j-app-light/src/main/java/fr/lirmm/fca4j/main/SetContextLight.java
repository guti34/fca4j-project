/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.main;

import java.util.HashSet;
import java.util.TreeSet;

import fr.lirmm.fca4j.iset.AbstractSetContext;
import fr.lirmm.fca4j.iset.roaringbitmap.RoaringBitMapFactory;
import fr.lirmm.fca4j.iset.std.ArrayListSetFactory;
import fr.lirmm.fca4j.iset.std.BitSetFactory;
import fr.lirmm.fca4j.iset.std.BoolArrayFactory;
import fr.lirmm.fca4j.iset.std.IntArrayFactory;
import fr.lirmm.fca4j.iset.std.JavaCollectionSetFactory;
import fr.lirmm.fca4j.iset.std.SparseBitSetFactory;

/**
 * The Class SetContextLight.
 */
public class SetContextLight extends AbstractSetContext{

	/**
	 * Instantiates set factories.
	 */
	public SetContextLight() {
		registerFactory(new BitSetFactory());
//		registerFactory(new GPUSetFactory());
		registerFactory(new RoaringBitMapFactory());
		registerFactory(new SparseBitSetFactory());
		registerFactory(new JavaCollectionSetFactory<>(() -> new HashSet<>(),false));
		registerFactory( new JavaCollectionSetFactory<>(() -> new TreeSet<>(),true));
		registerFactory( new IntArrayFactory());
		registerFactory( new ArrayListSetFactory());
		registerFactory( new BoolArrayFactory());
	}
	
	/**
	 * Gets the default implementation.
	 *
	 * @return the default implementation
	 */
	@Override
	public String getDefaultImplementation() {
		return "BITSET";
	}

}
