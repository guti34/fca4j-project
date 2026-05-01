/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.iset.hppc;

import java.util.BitSet;
import java.util.Iterator;
import java.util.NoSuchElementException;

import com.carrotsearch.hppc.IntHashSet;
import com.carrotsearch.hppc.cursors.IntCursor;

import fr.lirmm.fca4j.iset.AbstractSetFactory;
import fr.lirmm.fca4j.iset.AbstractSetFactory.AbstractSet;
import fr.lirmm.fca4j.iset.ISet;

/**
 * A factory for creating sets implemented with HPPC hashsets.
 *
 * @author agutierr
 */
public class HPPCFactory extends AbstractSetFactory {

    /**
     * Instantiates a new HPPC factory.
     */
    public HPPCFactory() {
    }

    /**
     * Creates a new HPPC object.
     *
     * @return the i set
     */
    @Override
    public ISet createSet() {
        return new SetWithHPPC();
    }

    /**
     * Creates a new HPPC object.
     *
     * @param bitset the bitset
     * @return the i set
     */
    @Override
    public ISet createSet(BitSet bitset) {
        return new SetWithHPPC(bitset);
    }

    /**
     * Creates a new HPPC object.
     *
     * @param initialCapacity the initial capacity
     * @return the i set
     */
    @Override
    public ISet createSet(int initialCapacity) {
        return new SetWithHPPC(initialCapacity);
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
		return "HPPC_HASHSET";
	}
    
    /**
     * Clone.
     *
     * @param to_clone the to clone
     * @return the i set
     */
    @Override
    public ISet clone(ISet to_clone) {
       IntHashSet bs = (IntHashSet) ((SetWithHPPC)to_clone).hashSet.clone();
        return new SetWithHPPC(bs);
    }
       
       /**
        * The Class SetWithHPPC.
        */
       class SetWithHPPC extends AbstractSet {
           
           /** The hash set. */
           private IntHashSet hashSet;
       
       /**
        * Instantiates a new sets the with HPPC.
        */
       SetWithHPPC() {
            hashSet = new IntHashSet();
        }

        /**
         * Instantiates a new sets the with HPPC.
         *
         * @param initialCapacity the initial capacity
         */
        SetWithHPPC(int initialCapacity) {
            hashSet = new IntHashSet(initialCapacity);
        }

        /**
         * Instantiates a new sets the with HPPC.
         *
         * @param hashSet the hash set
         */
        SetWithHPPC(IntHashSet hashSet) {
            this.hashSet = hashSet;
        }
        
        /**
         * Instantiates a new sets the with HPPC.
         *
         * @param bitSet the bit set
         */
        SetWithHPPC(BitSet bitSet) {

            this.hashSet = new IntHashSet();
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
            hashSet.addAll (((SetWithHPPC)anotherSet).hashSet);
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
            for(Iterator<IntCursor> it=((SetWithHPPC) anotherSet).hashSet.iterator();it.hasNext();){
                if(!hashSet.contains(it.next().value)) return false;
            }
            return true;
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
            hashSet.removeAll(((SetWithHPPC) anotherSet).hashSet);
        }

	/**
	 * Iterator.
	 *
	 * @return the iterator
	 */
	public Iterator<Integer> iterator() {
		return new Iterator<Integer>(){
			Iterator<IntCursor> it= hashSet.iterator();
			@Override
			public boolean hasNext() {
				return it.hasNext();
			}

			@Override
			public Integer next() {
				return it.next().value;
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
            IntHashSet bs = new IntHashSet(hashSet);
            bs.retainAll(((SetWithHPPC) anotherSet).hashSet);
            return new SetWithHPPC(bs);
        }

        /**
         * New difference.
         *
         * @param anotherSet the another set
         * @return the i set
         */
        @Override
        public ISet newDifference(ISet anotherSet) {
            IntHashSet bs = new IntHashSet(hashSet);
            bs.removeAll(((SetWithHPPC) anotherSet).hashSet);
            return new SetWithHPPC(bs);
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
            hashSet.retainAll(((SetWithHPPC)anotherSet).hashSet);
        }

        /**
         * First.
         *
         * @return the int
         */
        @Override
        public int first() {
            try{
            return hashSet.iterator().next().value;
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
            return hashSet.equals(((SetWithHPPC)aSet).hashSet);
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
