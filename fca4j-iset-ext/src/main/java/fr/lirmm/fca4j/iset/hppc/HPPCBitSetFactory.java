/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.iset.hppc;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.carrotsearch.hppc.BitSet;

import fr.lirmm.fca4j.iset.AbstractSetFactory;
import fr.lirmm.fca4j.iset.ISet;

/**
 * A factory for creating sets implemented with HPPCBitSet.
 *
 * @author agutierr
 */
public class HPPCBitSetFactory extends AbstractSetFactory {

    /**
     * Instantiates a new HPPC bit set factory.
     */
    public HPPCBitSetFactory() {
    }
    
    /**
     * Creates a new HPPCBitSet object.
     *
     * @return the i set
     */
    @Override
    public ISet createSet() {
        return new SetWithBitSet();
    }
    
    /**
     * Creates a new HPPCBitSet object.
     *
     * @param bs the bs
     * @return the i set
     */
    @Override
    public ISet createSet(java.util.BitSet bs) {
        return new SetWithBitSet((BitSet)bs.clone());
    }

    /**
     * Creates a new HPPCBitSet object.
     *
     * @param initialCapacity the initial capacity
     * @return the i set
     */
    @Override
    public ISet createSet(int initialCapacity) {
        return new SetWithBitSet(initialCapacity);
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
		return "HPPC_BITSET";
	}
    
    /**
     * Clone.
     *
     * @param b the b
     * @return the i set
     */
    @Override
    public ISet clone(ISet b) {
        BitSet bs = (BitSet) ((SetWithBitSet) b).bitSet.clone();
        return new SetWithBitSet(bs);
    }

    /**
     * The Class SetWithBitSet.
     */
    class SetWithBitSet extends AbstractOrderedSet {

        /** The bit set. */
        private BitSet bitSet;

        /**
         * Instantiates a new sets the with bit set.
         */
        SetWithBitSet() {
            bitSet = new BitSet();
        }

        /**
         * Instantiates a new sets the with bit set.
         *
         * @param initialCapacity the initial capacity
         */
        SetWithBitSet(int initialCapacity) {
            bitSet = new BitSet(initialCapacity);
        }

        /**
         * Instantiates a new sets the with bit set.
         *
         * @param bitSet the bit set
         */
        SetWithBitSet(BitSet bitSet) {
            this.bitSet = bitSet;
        }
        
        /**
         * Instantiates a new sets the with bit set.
         *
         * @param bitSet the bit set
         */
        SetWithBitSet(java.util.BitSet bitSet) {
            
            this.bitSet = new BitSet();
            for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i+1)) {
        this.bitSet.set(i);
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
            bitSet.or(((SetWithBitSet)anotherSet).bitSet);
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
            BitSet intersect = (BitSet) bitSet.clone();
            intersect.and(((SetWithBitSet) anotherSet).bitSet);
            return intersect.cardinality()==anotherSet.cardinality();
        }

        /**
         * Capacity.
         *
         * @return the int
         */
        @Override
        public int capacity() {
            return (int)bitSet.size();
        }

        /**
         * Cardinality.
         *
         * @return the int
         */
        @Override
        public int cardinality() {
            return (int)bitSet.cardinality();
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
            bitSet.andNot(((SetWithBitSet) anotherSet).bitSet);
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
            BitSet bs = (BitSet) bitSet.clone();
            bs.and(((SetWithBitSet) anotherSet).bitSet);
            return new SetWithBitSet(bs);
        }

        /**
         * New difference.
         *
         * @param anotherSet the another set
         * @return the i set
         */
        @Override
        public ISet newDifference(ISet anotherSet) {
            BitSet bs = (BitSet) bitSet.clone();
            bs.andNot(((SetWithBitSet) anotherSet).bitSet);
            return new SetWithBitSet(bs);
        }

        /**
         * Removes the.
         *
         * @param num the num
         */
        @Override
        public void remove(int num) {
            if(bitSet.get(num))
            bitSet.flip(num);
        }

        /**
         * Retain all.
         *
         * @param anotherSet the another set
         */
        @Override
        public void retainAll(ISet anotherSet) {
            bitSet.and(((SetWithBitSet) anotherSet).bitSet);
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
            long length=bitSet.length();
            if(length==0) return -1;
            else return (int)bitSet.length()-1;
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
            return bitSet.equals(((SetWithBitSet)aSet).bitSet);
        }catch(Exception e){
            return false;
        }
    }

	@Override
	public boolean intersects(ISet anotherSet) {
		bitSet.intersects(((SetWithBitSet)anotherSet).bitSet);
		return false;
	}


    }
}
