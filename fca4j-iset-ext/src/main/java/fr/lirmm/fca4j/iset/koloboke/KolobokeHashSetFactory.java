/*
BSD 3-Clause License

Copyright (c) 2022 LIRMM
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

   * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
   * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
   * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package fr.lirmm.fca4j.iset.koloboke;

import java.util.BitSet;
import java.util.Iterator;

import com.koloboke.collect.set.hash.HashIntSet;
import com.koloboke.collect.set.hash.HashIntSets;

import fr.lirmm.fca4j.iset.AbstractSetFactory;
import fr.lirmm.fca4j.iset.ISet;

/**
 * A factory for creating sets implemented with Koloboke HashSet.
 *
 * @author agutierr
 */
public class KolobokeHashSetFactory extends AbstractSetFactory {

    /**
     * Instantiates a new koloboke hash set factory.
     */
    public KolobokeHashSetFactory() {
    }
    
    /**
     * Creates a new KolobokeHashSet object.
     *
     * @return the hash int set
     */
    private static synchronized HashIntSet createHashIntSet(){
        return HashIntSets.newMutableSet();
    }
    
    /**
     * Creates a new KolobokeHashSet object.
     *
     * @param h the h
     * @param mutable the mutable
     * @return the hash int set
     */
    private static synchronized HashIntSet createHashIntSet(HashIntSet h,boolean mutable){
        if(mutable) return HashIntSets.newMutableSet(h);
        else return HashIntSets.newImmutableSet(h);
    }
    
    /**
     * Creates a new KolobokeHashSet object.
     *
     * @return the i set
     */
    @Override
    public ISet createSet() {
        return new SetWithKolobokeHashSet();
    }
    
    /**
     * Creates a new KolobokeHashSet object.
     *
     * @param bs the bs
     * @return the i set
     */
    @Override
    public ISet createSet(BitSet bs) {
        return new SetWithKolobokeHashSet((BitSet)bs.clone());
    }

    /**
     * Creates a new KolobokeHashSet object.
     *
     * @param initialCapacity the initial capacity
     * @return the i set
     */
    @Override
    public ISet createSet(int initialCapacity) {
        return new SetWithKolobokeHashSet(initialCapacity);
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
		return "KOLOBOKE_HASHSET";
	}
    
    /**
     * Clone.
     *
     * @param b the b
     * @return the i set
     */
    @Override
    public ISet clone(ISet b) {
        HashIntSet bs = createHashIntSet(((SetWithKolobokeHashSet) b).hashSet,true);
        return new SetWithKolobokeHashSet(bs);
    }
    
    /**
     * Clone immutable.
     *
     * @param b the b
     * @return the i set
     */
    public ISet cloneImmutable(ISet b) {
        HashIntSet bs = createHashIntSet(((SetWithKolobokeHashSet) b).hashSet,false);
        return new SetWithKolobokeHashSet(bs);
    }

    /**
     * The Class SetWithKolobokeHashSet.
     */
    class SetWithKolobokeHashSet extends AbstractSet {

        /** The hash set. */
        private HashIntSet hashSet;

        /**
         * Instantiates a new sets the with koloboke hash set.
         */
        SetWithKolobokeHashSet() {
            hashSet = createHashIntSet();
        }

        /**
         * Instantiates a new sets the with koloboke hash set.
         *
         * @param initialCapacity the initial capacity
         */
        SetWithKolobokeHashSet(int initialCapacity) {
            hashSet = createHashIntSet();
        }

        /**
         * Instantiates a new sets the with koloboke hash set.
         *
         * @param hashSet the hash set
         */
        SetWithKolobokeHashSet(HashIntSet hashSet) {
            this.hashSet = hashSet;
        }
        
        /**
         * Instantiates a new sets the with koloboke hash set.
         *
         * @param bitSet the bit set
         */
        SetWithKolobokeHashSet(BitSet bitSet) {

            this.hashSet = createHashIntSet();
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
            hashSet.addAll(((SetWithKolobokeHashSet)anotherSet).hashSet);
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
            return hashSet.containsAll(((SetWithKolobokeHashSet) anotherSet).hashSet);
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
            hashSet.removeAll(((SetWithKolobokeHashSet) anotherSet).hashSet);
        }

	/**
	 * Iterator.
	 *
	 * @return the iterator
	 */
	public Iterator<Integer> iterator() {
            return hashSet.iterator();
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
            HashIntSet bs = createHashIntSet(hashSet,true);
            bs.retainAll(((SetWithKolobokeHashSet) anotherSet).hashSet);
            return new SetWithKolobokeHashSet(bs);
        }

        /**
         * New difference.
         *
         * @param anotherSet the another set
         * @return the i set
         */
        @Override
        public ISet newDifference(ISet anotherSet) {
            HashIntSet bs = createHashIntSet(hashSet,true);
            bs.removeAll(((SetWithKolobokeHashSet) anotherSet).hashSet);
            return new SetWithKolobokeHashSet(bs);
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
            hashSet.retainAll(((SetWithKolobokeHashSet)anotherSet).hashSet);
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
            return hashSet.equals(((SetWithKolobokeHashSet)aSet).hashSet);
        }catch(Exception e){
            return false;
        }
    }
        
        /**
         * First.
         *
         * @return the int
         */
        @Override
        public int first(){
            try{
            return hashSet.cursor().elem();
            }catch(Exception e){
                return -1;
            }
        }

    }

}
