package fr.lirmm.fca4j.main;

import java.util.HashSet;
import java.util.TreeSet;

import fr.lirmm.fca4j.iset.AbstractSetContext;
import fr.lirmm.fca4j.iset.fastutil.FastUtilFactory;
import fr.lirmm.fca4j.iset.hppc.HPPCBitSetFactory;
import fr.lirmm.fca4j.iset.hppc.HPPCFactory;
import fr.lirmm.fca4j.iset.koloboke.KolobokeHashSetFactory;
import fr.lirmm.fca4j.iset.roaringbitmap.RoaringBitMapFactory;
import fr.lirmm.fca4j.iset.std.ArrayListSetFactory;
import fr.lirmm.fca4j.iset.std.BitSetFactory;
import fr.lirmm.fca4j.iset.std.BoolArrayFactory;
import fr.lirmm.fca4j.iset.std.IntArrayFactory;
import fr.lirmm.fca4j.iset.std.JavaCollectionSetFactory;
import fr.lirmm.fca4j.iset.std.SparseBitSetFactory;
import fr.lirmm.fca4j.iset.trove.TIntHashSetFactory;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;

public class SetContextComplete extends AbstractSetContext{

	public SetContextComplete() {
		registerFactory(new BitSetFactory());
		registerFactory(new RoaringBitMapFactory());
		registerFactory(new SparseBitSetFactory());
		registerFactory(new JavaCollectionSetFactory<>(() -> new HashSet<>(),false));
		registerFactory(new TIntHashSetFactory());
		registerFactory( new KolobokeHashSetFactory());
		registerFactory( new JavaCollectionSetFactory<>(() -> new TreeSet<>(),true));
		registerFactory( new IntArrayFactory());
		registerFactory( new FastUtilFactory<>(() -> new IntAVLTreeSet(),"FASTUTIL_AVL_TREESET"));
		registerFactory( new FastUtilFactory<>(() -> new IntRBTreeSet(),"FASTUTIL_RB_TREESET"));
		registerFactory( new HPPCFactory());
		registerFactory( new HPPCBitSetFactory());
		registerFactory( new ArrayListSetFactory());
		registerFactory( new BoolArrayFactory());
	}
	
	@Override
	public String getDefaultImplementation() {
		return "BITSET";
	}

}
