/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.lirmm.fca4j.iset.hppc;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.carrotsearch.hppc.BitSet;

import fr.lirmm.fca4j.iset.AbstractSetFactory;
import fr.lirmm.fca4j.iset.ISet;

/**
 *
 * @author agutierr
 */
public class HPPCBitSetFactory extends AbstractSetFactory {

    public HPPCBitSetFactory() {
    }
    
    @Override
    public ISet createSet() {
        return new SetWithBitSet();
    }
    @Override
    public ISet createSet(java.util.BitSet bs) {
        return new SetWithBitSet((BitSet)bs.clone());
    }

    @Override
    public ISet createSet(int initialCapacity) {
        return new SetWithBitSet(initialCapacity);
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
		return "HPPC_BITSET";
	}
    @Override
    public ISet clone(ISet b) {
        BitSet bs = (BitSet) ((SetWithBitSet) b).bitSet.clone();
        return new SetWithBitSet(bs);
    }

    class SetWithBitSet extends AbstractOrderedSet {

        private BitSet bitSet;

        SetWithBitSet() {
            bitSet = new BitSet();
        }

        SetWithBitSet(int initialCapacity) {
            bitSet = new BitSet(initialCapacity);
        }

        SetWithBitSet(BitSet bitSet) {
            this.bitSet = bitSet;
        }
        SetWithBitSet(java.util.BitSet bitSet) {
            
            this.bitSet = new BitSet();
            for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i+1)) {
        this.bitSet.set(i);
        }
        }

        @Override
        public void add(int num) {
            bitSet.set(num);
        }
        @Override
        public void addAll(ISet anotherSet) {
            bitSet.or(((SetWithBitSet)anotherSet).bitSet);
        }

        @Override
        public boolean contains(int num) {
            return bitSet.get(num);
        }
        
        @Override
        public boolean containsAll(ISet anotherSet) {
            BitSet intersect = (BitSet) bitSet.clone();
            intersect.and(((SetWithBitSet) anotherSet).bitSet);
            return intersect.cardinality()==anotherSet.cardinality();
        }

        @Override
        public int capacity() {
            return (int)bitSet.size();
        }

        @Override
        public int cardinality() {
            return (int)bitSet.cardinality();
        }

        @Override
        public void fill(int size) {
            bitSet.set(0, size);
        }

        @Override
        public void clear(int size) {
            bitSet.clear(0, size);
        }
        @Override
        public void removeAll(ISet anotherSet) {
            bitSet.andNot(((SetWithBitSet) anotherSet).bitSet);
        }

        @Override
        public Iterator<Integer> iterator() {
            return new Iterator<Integer>() {
                int index = -2;

                @Override
                public boolean hasNext() {
                    if (index == -1) {
                        return false;
                    }
                    if (index == -2) {
                        return bitSet.nextSetBit(0) >= 0;
                    } else {
                        return bitSet.nextSetBit(index + 1) >= 0;
                    }
                }

                @Override
                public Integer next() {
                    if (index == -1) {
                        throw new NoSuchElementException();
                    }
                    if (index == -2) {
                        index = bitSet.nextSetBit(0);
                    } else {
                        index = bitSet.nextSetBit(index + 1);
                    }
                    if (index == -1) {
                        throw new NoSuchElementException();
                    } else {
                        return index;
                    }
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();

                }
            };
        }

        @Override
        public boolean isEmpty() {
            return bitSet.isEmpty();
        }

        @Override
        public ISet newIntersect(ISet anotherSet) {
            BitSet bs = (BitSet) bitSet.clone();
            bs.and(((SetWithBitSet) anotherSet).bitSet);
            return new SetWithBitSet(bs);
        }

        @Override
        public ISet newDifference(ISet anotherSet) {
            BitSet bs = (BitSet) bitSet.clone();
            bs.andNot(((SetWithBitSet) anotherSet).bitSet);
            return new SetWithBitSet(bs);
        }

        @Override
        public void remove(int num) {
            if(bitSet.get(num))
            bitSet.flip(num);
        }

        @Override
        public void retainAll(ISet anotherSet) {
            bitSet.and(((SetWithBitSet) anotherSet).bitSet);
        }

        @Override
        public int first() {
            return bitSet.nextSetBit(0);
        }
        @Override
        public int last() {
            long length=bitSet.length();
            if(length==0) return -1;
            else return (int)bitSet.length()-1;
        }

        @Override
        public int hashCode() {
            return bitSet.hashCode();
        }

    @Override
    public boolean equals(Object aSet) {
        try{
            return bitSet.equals(((SetWithBitSet)aSet).bitSet);
        }catch(Exception e){
            return false;
        }
    }


    }
}
