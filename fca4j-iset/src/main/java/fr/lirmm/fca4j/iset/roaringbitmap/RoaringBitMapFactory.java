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
package fr.lirmm.fca4j.iset.roaringbitmap;

import java.util.BitSet;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.roaringbitmap.RoaringBitmap;

import fr.lirmm.fca4j.iset.AbstractSetFactory;
import fr.lirmm.fca4j.iset.ISet;

/**
 * A factory for creating sets based on RoaringBitMap class.
 *
 * @author agutierr
 */
public class RoaringBitMapFactory extends  AbstractSetFactory {
	
    /**
     * Instantiates a new roaring bit map factory.
     */
    public RoaringBitMapFactory() {
    }

    /**
     * Creates a new RoaringBitMap object.
     *
     * @return the new set
     */
    @Override
    public ISet createSet() {
        return new SetWithRoaringBitMap();
    }
    
    /**
     * Creates a new RoaringBitMap object.
     *
     * @param bs the bitset
     * @return the new set
     */
    @Override
    public ISet createSet(BitSet bs) {
        return new SetWithRoaringBitMap((BitSet)bs.clone());
    }

    /**
     * Creates a new RoaringBitMap object.
     *
     * @param initialCapacity the initial capacity
     * @return the new set
     */
    @Override
    public ISet createSet(int initialCapacity) {
        return new SetWithRoaringBitMap(initialCapacity);
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
		return "ROARING_BITMAP";
	}

    /**
     * Clone.
     *
     * @param b the set to clone
     * @return the cloned set
     */
    @Override
    public ISet clone(ISet b) {
        RoaringBitmap bs = (RoaringBitmap) ((SetWithRoaringBitMap) b).bitMap.clone();
        return new SetWithRoaringBitMap(bs);
    }

    /**
     * The Class SetWithRoaringBitMap.
     */
    class SetWithRoaringBitMap extends AbstractOrderedSet {

        /** The bit map. */
        private RoaringBitmap bitMap;

        /**
         * Instantiates a new sets the with roaring bitmap.
         */
        SetWithRoaringBitMap() {
            bitMap = new RoaringBitmap();
        }

        /**
         * Instantiates a new sets the with roaring bitmap.
         *
         * @param initialCapacity the initial capacity
         */
        SetWithRoaringBitMap(int initialCapacity) {
            this();
        }
        
        /**
         * Instantiates a new sets the with roaring bitmap.
         *
         * @param bm the bm
         */
        SetWithRoaringBitMap(RoaringBitmap bm) {
            this();
            this.bitMap=(RoaringBitmap) bm.clone();
        }

        /**
         * Instantiates a new sets the with roaring bitmap.
         *
         * @param bitSet the bit set
         */
        SetWithRoaringBitMap(BitSet bitSet) {
            
            this.bitMap = new RoaringBitmap();
            for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i+1)) {
        this.bitMap.add(i);
        }
        }

        /**
         * Adds an element.
         *
         * @param num the element
         */
        @Override
        public void add(int num) {
            bitMap.add(num);
        }
        
        /**
         * Adds all.
         *
         * @param anotherSet the set to add
         */
        @Override
        public void addAll(ISet anotherSet) {
            bitMap.or(((SetWithRoaringBitMap) anotherSet).bitMap);
        }

        /**
         * Contains.
         *
         * @param num the element
         * @return true, if successful
         */
        @Override
        public boolean contains(int num) {
            return bitMap.contains(num);
        }
        
        /**
         * Contains all.
         *
         * @param anotherSet the another set
         * @return true, if successful
         */
        @Override
        public boolean containsAll(ISet anotherSet) {
            return bitMap.contains(((SetWithRoaringBitMap) anotherSet).bitMap);
        }

        /**
         * Capacity.
         *
         * @return the int
         */
        @Override
        public int capacity() {
            return bitMap.getSizeInBytes()*8;
        }

        /**
         * Cardinality.
         *
         * @return the int
         */
        @Override
        public int cardinality() {
            return bitMap.getCardinality();
        }

        /**
         * Fill.
         *
         * @param size the size
         */
        @Override
        public void fill(int size) {
            bitMap.add(0L, (long)size);
        }

        /**
         * Clear.
         *
         * @param size the size
         */
        @Override
        public void clear(int size) {
            bitMap.remove(0L, (long)size);
        }
        
        /**
         * Removes the all.
         *
         * @param anotherSet the set to remove
         */
        @Override
        public void removeAll(ISet anotherSet) {
            bitMap.andNot(((SetWithRoaringBitMap) anotherSet).bitMap);
        }

	/**
	 * Iterator.
	 *
	 * @return the iterator
	 */
	public Iterator<Integer> iterator()
	{
            return bitMap.iterator();
	}
	
	/**
	 * Checks if is empty.
	 *
	 * @return true, if is empty
	 */
	public boolean isEmpty()
	{
		return bitMap.isEmpty();
	}

        /**
         * New intersect.
         *
         * @param anotherSet the set to intersect
         * @return the i set
         */
        @Override
        public ISet newIntersect(ISet anotherSet) {
            RoaringBitmap bs = (RoaringBitmap) bitMap.clone();
            bs.and(((SetWithRoaringBitMap) anotherSet).bitMap);
            return new SetWithRoaringBitMap(bs);
        }

        /**
         * New difference.
         *
         * @param anotherSet the set to remove
         * @return the i set
         */
        @Override
        public ISet newDifference(ISet anotherSet) {
            RoaringBitmap bs = (RoaringBitmap) bitMap.clone();
            bs.andNot(((SetWithRoaringBitMap) anotherSet).bitMap);
            return new SetWithRoaringBitMap(bs);
        }

        /**
         * Removes the.
         *
         * @param num the element to remove
         */
        @Override
        public void remove(int num) {
            bitMap.remove(num);
        }

        /**
         * Retain all.
         *
         * @param anotherSet the set to retain
         */
        @Override
        public void retainAll(ISet anotherSet) {
            bitMap.and(((SetWithRoaringBitMap) anotherSet).bitMap);
        }

        /**
         * First element.
         *
         * @return the int
         */
        @Override
        public int first() {
            try{
            return bitMap.first();
            }catch(NoSuchElementException ex){
                return -1;
            }
        }
        
        /**
         * Last element.
         *
         * @return the int
         */
        @Override
        public int last() {
            return bitMap.last();
        }

        /**
         * Hash code.
         *
         * @return the int
         */
        @Override
        public int hashCode() {
            return bitMap.hashCode();
        }
    
    /**
     * Equals.
     *
     * @param aSet the set to compare with
     * @return true, if successful
     */
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
