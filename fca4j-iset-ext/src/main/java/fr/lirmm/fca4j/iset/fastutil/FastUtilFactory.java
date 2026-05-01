/*
 * Copyright (c) 2022 LIRMM — BSD 3-Clause License
 * See LICENSE file in the project root for full license text.
 */
package fr.lirmm.fca4j.iset.fastutil;

import java.util.BitSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import fr.lirmm.fca4j.iset.AbstractSetFactory;
import fr.lirmm.fca4j.iset.ISet;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntSortedSet;

/**
 * A factory for creating sets with FastUtil implementation.
 *
 * @author agutierr
 * @param <T> the generic type
 */
public class FastUtilFactory <T extends IntSortedSet> extends AbstractSetFactory {
    
    /** The supplier. */
    private Supplier<T> supplier;
    
    /** The name. */
    private String name;
    
    /**
     * Instantiates a new FastUtil factory.
     *
     * @param supplier the supplier
     * @param name the name
     */
    public FastUtilFactory(Supplier<T> supplier,String name) {
        this.supplier=supplier;
        this.name=name;
    }

    /**
     * Creates a new FastUtil object.
     *
     * @return the i set
     */
    @Override
    public ISet createSet() {
        return new SetWithFastUtil();
    }

    /**
     * Creates a new FastUtil object.
     *
     * @param bitset the bitset
     * @return the i set
     */
    @Override
    public ISet createSet(BitSet bitset) {
        return new SetWithFastUtil(bitset);
    }

    /**
     * Creates a new FastUtil object.
     *
     * @param initialCapacity the initial capacity
     * @return the i set
     */
    @Override
    public ISet createSet(int initialCapacity) {
        return new SetWithFastUtil(initialCapacity);
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
		return name;
	}
    
    /**
     * Clone.
     *
     * @param to_clone the to clone
     * @return the i set
     */
    @Override
    public ISet clone(ISet to_clone) {
       BitSet bs = to_clone.toBitSet();
        return new SetWithFastUtil(bs);
    }
       
       /**
        * The Class SetWithFastUtil.
        */
       class SetWithFastUtil extends AbstractOrderedSet {
           
           /** The sorted set. */
           private T sortedSet;
       
       /**
        * Instantiates a new sets the with fast util.
        */
       SetWithFastUtil() {
            sortedSet = supplier.get();
        }

        /**
         * Instantiates a new sets the with fast util.
         *
         * @param initialCapacity the initial capacity
         */
        SetWithFastUtil(int initialCapacity) {
            sortedSet = supplier.get();
        }

        /**
         * Instantiates a new sets the with fast util.
         *
         * @param hashSet the hash set
         */
        SetWithFastUtil(T hashSet) {
            this.sortedSet = hashSet;
        }
        
        /**
         * Instantiates a new sets the with fast util.
         *
         * @param bitSet the bit set
         */
        SetWithFastUtil(BitSet bitSet) {

            this.sortedSet =supplier.get();
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
            sortedSet.add(num);
        }
        
        /**
         * Adds the all.
         *
         * @param anotherSet the another set
         */
        @Override
        public void addAll(ISet anotherSet) {
            sortedSet.addAll(((SetWithFastUtil)anotherSet).sortedSet);
        }

        /**
         * Contains.
         *
         * @param num the num
         * @return true, if successful
         */
        @Override
        public boolean contains(int num) {
            return sortedSet.contains(num);
        }
        
        /**
         * Contains all.
         *
         * @param anotherSet the another set
         * @return true, if successful
         */
        @Override
        public boolean containsAll(ISet anotherSet) {
            return sortedSet.containsAll(((SetWithFastUtil) anotherSet).sortedSet);
        }

        /**
         * Capacity.
         *
         * @return the int
         */
        @Override
        public int capacity() {
            return sortedSet.size();
        }

        /**
         * Cardinality.
         *
         * @return the int
         */
        @Override
        public int cardinality() {
            return sortedSet.size();
        }

   /**
    * Fill.
    *
    * @param size the size
    */
   public void fill(int size) {
        for (int i = 0; i < size; i++) {
            sortedSet.add(i);
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
            sortedSet.remove(i);
        }
    }
        
        /**
         * Removes the all.
         *
         * @param anotherSet the another set
         */
        @Override
        public void removeAll(ISet anotherSet) {
            sortedSet.removeAll(((SetWithFastUtil) anotherSet).sortedSet);
        }

	/**
	 * Iterator.
	 *
	 * @return the iterator
	 */
	public Iterator<Integer> iterator() {
		return new Iterator<Integer>(){
			IntIterator it= sortedSet.iterator();
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
            return sortedSet.isEmpty();
        }

        /**
         * New intersect.
         *
         * @param anotherSet the another set
         * @return the i set
         */
        @Override
        public ISet newIntersect(ISet anotherSet) {
            T bs = supplier.get();
            bs.addAll(((SetWithFastUtil) anotherSet).sortedSet);
            bs.retainAll(((SetWithFastUtil) anotherSet).sortedSet);
            return new SetWithFastUtil(bs);
        }

        /**
         * New difference.
         *
         * @param anotherSet the another set
         * @return the i set
         */
        @Override
        public ISet newDifference(ISet anotherSet) {
            T bs = supplier.get();
            bs.addAll(sortedSet);
            bs.removeAll(((SetWithFastUtil) anotherSet).sortedSet);
            return new SetWithFastUtil(bs);
        }

        /**
         * Removes the.
         *
         * @param num the num
         */
        @Override
        public void remove(int num) {
            sortedSet.remove(num);
        }

        /**
         * Retain all.
         *
         * @param anotherSet the another set
         */
        @Override
        public void retainAll(ISet anotherSet) {
            sortedSet.retainAll (((SetWithFastUtil)anotherSet).sortedSet);
        }
        
        /**
         * First.
         *
         * @return the int
         */
        @Override
        public int first() {
            try{
            return sortedSet.firstInt();
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
            return sortedSet.hashCode();
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
            return sortedSet.equals(((SetWithFastUtil)aSet).sortedSet);
        }catch(Exception e){
            return false;
        }
    }
        
        /**
         * To bit set.
         *
         * @return the bit set
         */
        @Override
        public BitSet toBitSet() {
            BitSet bs=new BitSet();
            for(Iterator<Integer> it=iterator();it.hasNext();)
                bs.set(it.next());
           return bs;
        }

		@Override
		public boolean intersects(ISet anotherSet) {
			return !newIntersect(anotherSet).isEmpty();
		}

    }
 
}
