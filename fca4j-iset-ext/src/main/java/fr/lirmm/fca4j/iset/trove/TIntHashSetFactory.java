/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.iset.trove;

import java.util.BitSet;
import java.util.Iterator;
import java.util.NoSuchElementException;

import fr.lirmm.fca4j.iset.AbstractSetFactory;
import fr.lirmm.fca4j.iset.AbstractSetFactory.AbstractSet;
import fr.lirmm.fca4j.iset.ISet;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.set.hash.TIntHashSet;

/**
 * A factory for creating sets implemented with Trove integer hashsets.
 *
 * @author agutierr
 */
public class TIntHashSetFactory extends AbstractSetFactory {

    /**
     * Instantiates a new t int hash set factory.
     */
    public TIntHashSetFactory() {
    }

    /**
     * Creates a new TIntHashSet object.
     *
     * @return the i set
     */
    @Override
    public ISet createSet() {
        return new SetWithTIntHashSet();
    }
    
    /**
     * Creates a new TIntHashSet object.
     *
     * @param bs the bs
     * @return the i set
     */
    @Override
    public ISet createSet(BitSet bs) {
        return new SetWithTIntHashSet((BitSet)bs.clone());
    }

    /**
     * Creates a new TIntHashSet object.
     *
     * @param initialCapacity the initial capacity
     * @return the i set
     */
    @Override
    public ISet createSet(int initialCapacity) {
        return new SetWithTIntHashSet(initialCapacity);
    }

	/**
	 * Ordered.
	 *
	 * @return true, if successful
	 */
	@Override
	public boolean ordered() {
		return false;
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
		return "TROVE_HASHSET";
	}
    
    /**
     * Clone.
     *
     * @param b the b
     * @return the i set
     */
    @Override
    public ISet clone(ISet b) {
        TIntHashSet bs = new TIntHashSet(((SetWithTIntHashSet) b).hashSet);
        return new SetWithTIntHashSet(bs);
    }

    /**
     * The Class SetWithTIntHashSet.
     */
    class SetWithTIntHashSet extends AbstractSet {

        /** The hash set. */
        private TIntHashSet hashSet;

        /**
         * Instantiates a new sets the with T int hash set.
         */
        SetWithTIntHashSet() {
            hashSet = new TIntHashSet();
        }

        /**
         * Instantiates a new sets the with T int hash set.
         *
         * @param initialCapacity the initial capacity
         */
        SetWithTIntHashSet(int initialCapacity) {
            hashSet = new TIntHashSet(initialCapacity);
        }

        /**
         * Instantiates a new sets the with T int hash set.
         *
         * @param hashSet the hash set
         */
        SetWithTIntHashSet(TIntHashSet hashSet) {
            this.hashSet = hashSet;
        }
        
        /**
         * Instantiates a new sets the with T int hash set.
         *
         * @param bitSet the bit set
         */
        SetWithTIntHashSet(BitSet bitSet) {

            this.hashSet = new TIntHashSet();
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
            hashSet.add(num);
        }
        
        /**
         * Adds the all.
         *
         * @param anotherSet the another set
         */
        @Override
        public void addAll(ISet anotherSet) {
            hashSet.addAll (((SetWithTIntHashSet)anotherSet).hashSet);
        }

        /**
         * Contains.
         *
         * @param num the num
         * @return true, if successful
         */
        @Override
        public boolean contains(int num) {
            return hashSet.contains(num);
        }
        
        /**
         * Contains all.
         *
         * @param anotherSet the another set
         * @return true, if successful
         */
        @Override
        public boolean containsAll(ISet anotherSet) {
            return hashSet.containsAll(((SetWithTIntHashSet) anotherSet).hashSet);
        }

        /**
         * Capacity.
         *
         * @return the int
         */
        @Override
        public int capacity() {
            return hashSet.size();
        }

        /**
         * Cardinality.
         *
         * @return the int
         */
        @Override
        public int cardinality() {
            return hashSet.size();
        }

   /**
    * Fill.
    *
    * @param size the size
    */
   public void fill(int size) {
        for (int i = 0; i < size; i++) {
            hashSet.add(i);
        }
    }


        /**
         * Clear.
         *
         * @param size the size
         */
        @Override
    public void clear(int size) {
        for (int i = 0; i < size; i++) {
            hashSet.remove(i);
        }
    }
        
        /**
         * Removes the all.
         *
         * @param anotherSet the another set
         */
        @Override
        public void removeAll(ISet anotherSet) {
            hashSet.removeAll(((SetWithTIntHashSet) anotherSet).hashSet);
        }

	/**
	 * Iterator.
	 *
	 * @return the iterator
	 */
	public Iterator<Integer> iterator() {
		return new Iterator<Integer>(){
			TIntIterator it= hashSet.iterator();
			@Override
			public boolean hasNext() {
				return it.hasNext();
			}

			@Override
			public Integer next() {
				return it.next();
			}

			@Override
			public void remove() {
				it.remove();
			}};
	}

        /**
         * Checks if is empty.
         *
         * @return true, if is empty
         */
        @Override
        public boolean isEmpty() {
            return hashSet.isEmpty();
        }

        /**
         * New intersect.
         *
         * @param anotherSet the another set
         * @return the i set
         */
        @Override
        public ISet newIntersect(ISet anotherSet) {
            TIntHashSet bs = new TIntHashSet(hashSet);
            bs.retainAll(((SetWithTIntHashSet) anotherSet).hashSet);
            return new SetWithTIntHashSet(bs);
        }

        /**
         * New difference.
         *
         * @param anotherSet the another set
         * @return the i set
         */
        @Override
        public ISet newDifference(ISet anotherSet) {
            TIntHashSet bs = new TIntHashSet(hashSet);
            bs.removeAll(((SetWithTIntHashSet) anotherSet).hashSet);
            return new SetWithTIntHashSet(bs);
        }

        /**
         * Removes the.
         *
         * @param num the num
         */
        @Override
        public void remove(int num) {
            hashSet.remove(num);
        }

        /**
         * Retain all.
         *
         * @param anotherSet the another set
         */
        @Override
        public void retainAll(ISet anotherSet) {
            hashSet.retainAll (((SetWithTIntHashSet)anotherSet).hashSet);
        }

        /**
         * First.
         *
         * @return the int
         */
        @Override
        public int first() {
            try{
            return hashSet.iterator().next();
            }catch(NoSuchElementException ex){
                return -1;
            }
        }

        /**
         * Hash code.
         *
         * @return the int
         */
        @Override
        public int hashCode() {
            return hashSet.hashCode();
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
            return hashSet.equals(((SetWithTIntHashSet)aSet).hashSet);
        }catch(Exception e){
            return false;
        }
    }

	@Override
	public boolean intersects(ISet anotherSet) {
		return !newIntersect(anotherSet).isEmpty();
	}
    }

}
