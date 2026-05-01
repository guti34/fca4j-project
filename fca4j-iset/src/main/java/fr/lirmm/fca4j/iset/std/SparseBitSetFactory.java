/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.iset.std;

import java.util.BitSet;
import java.util.Iterator;
import java.util.NoSuchElementException;

import fr.lirmm.fca4j.iset.AbstractSetFactory;
import fr.lirmm.fca4j.iset.AbstractSetFactory.AbstractOrderedSet;
import fr.lirmm.fca4j.iset.std.JavaCollectionSetFactory.SetWithCollection;
import fr.lirmm.fca4j.iset.ISet;

/**
 * A factory for creating sets based on SparseBitSet class.
 *
 * @author agutierr
 */
public class SparseBitSetFactory extends AbstractSetFactory {

    /**
     * Instantiates a new sparse bit set factory.
     */
    public SparseBitSetFactory() {
    }

    /**
     * Creates a new SparseBitSet object.
     *
     * @return the i set
     */
    @Override
    public ISet createSet() {
        return new SetWithSparseBitSet();
    }

    /**
     * Creates a new SparseBitSet object.
     *
     * @param bs the bs
     * @return the i set
     */
    @Override
    public ISet createSet(BitSet bs) {

        return new SetWithSparseBitSet((BitSet) bs.clone());
    }

    /**
     * Creates a new SparseBitSet object.
     *
     * @param initialCapacity the initial capacity
     * @return the i set
     */
    @Override
    public ISet createSet(int initialCapacity) {
        return new SetWithSparseBitSet(initialCapacity);
    }

	/**
	 * Ordered.
	 *
	 * @return true, if successful
	 */
	@Override
	public boolean ordered() {
		return true;
	}
	
	/**
	 * Fixed size.
	 *
	 * @return true, if successful
	 */
	@Override
	public boolean fixedSize() {
		return false;
	}
	
	/**
	 * Name.
	 *
	 * @return the string
	 */
	@Override
	public String name() {
		return "SPARSE_BITSET";
	}
    
    /**
     * Clone.
     *
     * @param b the b
     * @return the i set
     */
    @Override
    public ISet clone(ISet b) {
        SparseBitSet bs = (SparseBitSet) ((SetWithSparseBitSet) b).bitSet.clone();
        return new SetWithSparseBitSet(bs);
    }

    /**
     * The Class SetWithSparseBitSet.
     */
    class SetWithSparseBitSet extends AbstractOrderedSet {

        private SparseBitSet bitSet;

        /**
         * Instantiates a new sets the with sparse bit set.
         */
        SetWithSparseBitSet() {
            bitSet = new SparseBitSet();
        }

        /**
         * Instantiates a new sets the with sparse bit set.
         *
         * @param initialCapacity the initial capacity
         */
        SetWithSparseBitSet(int initialCapacity) {
            bitSet = new SparseBitSet(initialCapacity);
        }

        /**
         * Instantiates a new sets the with sparse bit set.
         *
         * @param bitSet the bit set
         */
        SetWithSparseBitSet(SparseBitSet bitSet) {
            this.bitSet = bitSet;
        }

        /**
         * Instantiates a new sets the with sparse bit set.
         *
         * @param bitSet the bit set
         */
        SetWithSparseBitSet(BitSet bitSet) {

            this.bitSet = new SparseBitSet();
            for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
                add(i);
            }
        }

        /**
         * Adds the.
         *
         * @param num the num
         */
        @Override
        public void add(int num) {
            bitSet.set(num);
        }

        /**
         * Adds the all.
         *
         * @param anotherSet the another set
         */
        @Override
        public void addAll(ISet anotherSet) {
            bitSet.or(((SetWithSparseBitSet)anotherSet).bitSet);
        }

        /**
         * Contains.
         *
         * @param num the num
         * @return true, if successful
         */
        @Override
        public boolean contains(int num) {
            return bitSet.get(num);
        }

        /**
         * Contains all.
         *
         * @param anotherSet the another set
         * @return true, if successful
         */
        @Override
        public boolean containsAll(ISet anotherSet) {
            SparseBitSet intersect = (SparseBitSet) bitSet.clone();
            intersect.and (((SetWithSparseBitSet) anotherSet).bitSet);
            return intersect.cardinality()==anotherSet.cardinality();
        }

        /**
         * Capacity.
         *
         * @return the int
         */
        @Override
        public int capacity() {
            return bitSet.size();
        }

        /**
         * Cardinality.
         *
         * @return the int
         */
        @Override
        public int cardinality() {
            return bitSet.cardinality();
        }

        /**
         * Fill.
         *
         * @param size the size
         */
        @Override
        public void fill(int size) {
            bitSet.set(0, size);
        }

        /**
         * Clear.
         *
         * @param size the size
         */
        @Override
        public void clear(int size) {
            bitSet.clear(0, size);
        }

        /**
         * Removes the all.
         *
         * @param anotherSet the another set
         */
        @Override
        public void removeAll(ISet anotherSet) {
            bitSet.andNot( ((SetWithSparseBitSet) anotherSet).bitSet);
        }

        /**
         * Iterator.
         *
         * @return the iterator
         */
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

        /**
         * Checks if is empty.
         *
         * @return true, if is empty
         */
        @Override
        public boolean isEmpty() {
            return bitSet.isEmpty();
        }

        /**
         * New intersect.
         *
         * @param anotherSet the another set
         * @return the i set
         */
        @Override
        public ISet newIntersect(ISet anotherSet) {
            SparseBitSet bs = (SparseBitSet) bitSet.clone();
            bs.and (((SetWithSparseBitSet) anotherSet).bitSet);
            return new SetWithSparseBitSet(bs);
        }

        /**
         * New difference.
         *
         * @param anotherSet the another set
         * @return the i set
         */
        @Override
        public ISet newDifference(ISet anotherSet) {
            SparseBitSet bs = (SparseBitSet) bitSet.clone();
            bs.andNot (((SetWithSparseBitSet) anotherSet).bitSet);
            return new SetWithSparseBitSet(bs);
        }

        /**
         * Removes the.
         *
         * @param num the num
         */
        @Override
        public void remove(int num) {
            bitSet.set(num, false);
        }

        /**
         * Retain all.
         *
         * @param anotherSet the another set
         */
        @Override
        public void retainAll(ISet anotherSet) {
            bitSet.and(((SetWithSparseBitSet) anotherSet).bitSet);
        }

        /**
         * First.
         *
         * @return the int
         */
        @Override
        public int first() {
            return bitSet.nextSetBit(0);
        }
        
        /**
         * Last.
         *
         * @return the int
         */
        @Override
        public int last() {
            int length=bitSet.length();
            if(length==0) return -1;
            else return bitSet.previousSetBit(length-1);
        }

        /**
         * Hash code.
         *
         * @return the int
         */
        @Override
        public int hashCode() {
            return bitSet.hashCode();
        }
    
    /**
     * Equals.
     *
     * @param aSet the a set
     * @return true, if successful
     */
    @Override
    public boolean equals(Object aSet) {
        try{
            return bitSet.equals(((SetWithSparseBitSet)aSet).bitSet);
        }catch(Exception e){
            return false;
        }
    }

	@Override
	public boolean intersects(ISet anotherSet) {
		return bitSet.intersects(((SetWithSparseBitSet)anotherSet).bitSet);
	}


    }

}
