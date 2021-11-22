/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.lirmm.fca4j.iset.std;

import java.util.BitSet;
import java.util.Iterator;
import java.util.NoSuchElementException;

import fr.lirmm.fca4j.iset.AbstractSetFactory;
import fr.lirmm.fca4j.iset.AbstractSetFactory.AbstractOrderedSet;
import fr.lirmm.fca4j.iset.ISet;

/**
 *
 * @author agutierr
 */
public class SparseBitSetFactory extends AbstractSetFactory {

    public SparseBitSetFactory() {
    }

    @Override
    public ISet createSet() {
        return new SetWithSparseBitSet();
    }

    @Override
    public ISet createSet(BitSet bs) {

        return new SetWithSparseBitSet((BitSet) bs.clone());
    }

    @Override
    public ISet createSet(int initialCapacity) {
        return new SetWithSparseBitSet(initialCapacity);
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
		return "SPARSE_BITSET";
	}
    @Override
    public ISet clone(ISet b) {
        SparseBitSet bs = (SparseBitSet) ((SetWithSparseBitSet) b).bitSet.clone();
        return new SetWithSparseBitSet(bs);
    }

    class SetWithSparseBitSet extends AbstractOrderedSet {

        private SparseBitSet bitSet;

        SetWithSparseBitSet() {
            bitSet = new SparseBitSet();
        }

        SetWithSparseBitSet(int initialCapacity) {
            bitSet = new SparseBitSet(initialCapacity);
        }

        SetWithSparseBitSet(SparseBitSet bitSet) {
            this.bitSet = bitSet;
        }

        SetWithSparseBitSet(BitSet bitSet) {

            this.bitSet = new SparseBitSet();
            for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
                add(i);
            }
        }

        @Override
        public void add(int num) {
            bitSet.set(num);
        }

        @Override
        public void addAll(ISet anotherSet) {
            bitSet.or(((SetWithSparseBitSet)anotherSet).bitSet);
        }

        @Override
        public boolean contains(int num) {
            return bitSet.get(num);
        }

        @Override
        public boolean containsAll(ISet anotherSet) {
            SparseBitSet intersect = (SparseBitSet) bitSet.clone();
            intersect.and (((SetWithSparseBitSet) anotherSet).bitSet);
            return intersect.cardinality()==anotherSet.cardinality();
        }

        @Override
        public int capacity() {
            return bitSet.size();
        }

        @Override
        public int cardinality() {
            return bitSet.cardinality();
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
            bitSet.andNot( ((SetWithSparseBitSet) anotherSet).bitSet);
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
            SparseBitSet bs = (SparseBitSet) bitSet.clone();
            bs.and (((SetWithSparseBitSet) anotherSet).bitSet);
            return new SetWithSparseBitSet(bs);
        }

        @Override
        public ISet newDifference(ISet anotherSet) {
            SparseBitSet bs = (SparseBitSet) bitSet.clone();
            bs.andNot (((SetWithSparseBitSet) anotherSet).bitSet);
            return new SetWithSparseBitSet(bs);
        }

        @Override
        public void remove(int num) {
            bitSet.set(num, false);
        }

        @Override
        public void retainAll(ISet anotherSet) {
            bitSet.and(((SetWithSparseBitSet) anotherSet).bitSet);
        }

        @Override
        public int first() {
            return bitSet.nextSetBit(0);
        }
        @Override
        public int last() {
            int length=bitSet.length();
            if(length==0) return -1;
            else return bitSet.previousSetBit(length-1);
        }

        @Override
        public int hashCode() {
            return bitSet.hashCode();
        }
    @Override
    public boolean equals(Object aSet) {
        try{
            return bitSet.equals(((SetWithSparseBitSet)aSet).bitSet);
        }catch(Exception e){
            return false;
        }
    }


    }

}
