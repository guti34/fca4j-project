/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.lirmm.fca4j.iset.roaringbitmap;

import java.util.BitSet;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.roaringbitmap.RoaringBitmap;

import fr.lirmm.fca4j.iset.AbstractSetFactory;
import fr.lirmm.fca4j.iset.ISet;

/**
 *
 * @author agutierr
 */
public class RoaringBitMapFactory extends  AbstractSetFactory {
	
    public RoaringBitMapFactory() {
    }

    @Override
    public ISet createSet() {
        return new SetWithRoaringBitMap();
    }
    @Override
    public ISet createSet(BitSet bs) {
        return new SetWithRoaringBitMap((BitSet)bs.clone());
    }

    @Override
    public ISet createSet(int initialCapacity) {
        return new SetWithRoaringBitMap(initialCapacity);
    }

	@Override
	public boolean ordered() {
		return true;
	}
	@Override
	public boolean fixedSize() {
		return false;
	}

	@Override
	public String name() {
		return "ROARING_BITMAP";
	}

    @Override
    public ISet clone(ISet b) {
        RoaringBitmap bs = (RoaringBitmap) ((SetWithRoaringBitMap) b).bitMap.clone();
        return new SetWithRoaringBitMap(bs);
    }

    class SetWithRoaringBitMap extends AbstractOrderedSet {

        private RoaringBitmap bitMap;

        SetWithRoaringBitMap() {
            bitMap = new RoaringBitmap();
        }

        SetWithRoaringBitMap(int initialCapacity) {
            this();
        }
        SetWithRoaringBitMap(RoaringBitmap bm) {
            this();
            this.bitMap=(RoaringBitmap) bm.clone();
        }

        SetWithRoaringBitMap(BitSet bitSet) {
            
            this.bitMap = new RoaringBitmap();
            for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i+1)) {
        this.bitMap.add(i);
        }
        }

        @Override
        public void add(int num) {
            bitMap.add(num);
        }
        @Override
        public void addAll(ISet anotherSet) {
            bitMap.or(((SetWithRoaringBitMap) anotherSet).bitMap);
        }

        @Override
        public boolean contains(int num) {
            return bitMap.contains(num);
        }
        
        @Override
        public boolean containsAll(ISet anotherSet) {
            return bitMap.contains(((SetWithRoaringBitMap) anotherSet).bitMap);
        }

        @Override
        public int capacity() {
            return bitMap.getSizeInBytes()*8;
        }

        @Override
        public int cardinality() {
            return bitMap.getCardinality();
        }

        @Override
        public void fill(int size) {
            bitMap.add(0L, (long)size);
        }

        @Override
        public void clear(int size) {
            bitMap.remove(0L, (long)size);
        }
        @Override
        public void removeAll(ISet anotherSet) {
            bitMap.andNot(((SetWithRoaringBitMap) anotherSet).bitMap);
        }

	public Iterator<Integer> iterator()
	{
            return bitMap.iterator();
	}
	public boolean isEmpty()
	{
		return bitMap.isEmpty();
	}

        @Override
        public ISet newIntersect(ISet anotherSet) {
            RoaringBitmap bs = (RoaringBitmap) bitMap.clone();
            bs.and(((SetWithRoaringBitMap) anotherSet).bitMap);
            return new SetWithRoaringBitMap(bs);
        }

        @Override
        public ISet newDifference(ISet anotherSet) {
            RoaringBitmap bs = (RoaringBitmap) bitMap.clone();
            bs.andNot(((SetWithRoaringBitMap) anotherSet).bitMap);
            return new SetWithRoaringBitMap(bs);
        }

        @Override
        public void remove(int num) {
            bitMap.remove(num);
        }

        @Override
        public void retainAll(ISet anotherSet) {
            bitMap.and(((SetWithRoaringBitMap) anotherSet).bitMap);
        }

        @Override
        public int first() {
            try{
            return bitMap.first();
            }catch(NoSuchElementException ex){
                return -1;
            }
        }
        @Override
        public int last() {
            return bitMap.last();
        }

        @Override
        public int hashCode() {
            return bitMap.hashCode();
        }
    @Override
    public boolean equals(Object aSet) {
        try{
            return bitMap.equals(((SetWithRoaringBitMap)aSet).bitMap);
        }catch(Exception e)
        {
            return false;
        }
    }

    }

}
