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

public class SetContextLight extends AbstractSetContext{

	public SetContextLight() {
		registerFactory(new BitSetFactory());
		registerFactory(new RoaringBitMapFactory());
		registerFactory(new SparseBitSetFactory());
		registerFactory(new JavaCollectionSetFactory<>(() -> new HashSet<>(),false));
		registerFactory( new JavaCollectionSetFactory<>(() -> new TreeSet<>(),true));
		registerFactory( new IntArrayFactory());
		registerFactory( new ArrayListSetFactory());
		registerFactory( new BoolArrayFactory());
	}
	
	@Override
	public String getDefaultImplementation() {
		return "BITSET";
	}

}
