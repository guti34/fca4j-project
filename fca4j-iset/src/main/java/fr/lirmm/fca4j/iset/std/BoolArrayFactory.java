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
package fr.lirmm.fca4j.iset.std;

import java.util.BitSet;
import java.util.Iterator;

import fr.lirmm.fca4j.iset.AbstractSetFactory;
import fr.lirmm.fca4j.iset.AbstractSetFactory.AbstractOrderedSet;
import fr.lirmm.fca4j.iset.ISet;

/**
 * A factory for creating sets implemented with boolean arrays.
 *
 * @author agutierr
 */
public class BoolArrayFactory extends AbstractSetFactory {

    /**
     * Instantiates a new bool array factory.
     */
    public BoolArrayFactory() {
    }

    /**
     * Creates a new BoolArray object.
     *
     * @return the i set
     */
    @Override
    public ISet createSet() {
        throw new UnsupportedOperationException("this kind of set has a fixed size");
    }

    /**
     * Creates a new BoolArray object.
     *
     * @param bs the bs
     * @return the i set
     */
    @Override
    public ISet createSet(BitSet bs) {
        return new SetWithBoolArray((BitSet) bs.clone());
    }
    
    /**
     * Creates a new BoolArray object.
     *
     * @param bs the bs
     * @param size the size
     * @return the i set
     */
    @Override
    public ISet createSet(BitSet bs,int size) {
        return new SetWithBoolArray(bs,size);
    }

    /**
     * Creates a new BoolArray object.
     *
     * @param initialCapacity the initial capacity
     * @return the i set
     */
    @Override
    public ISet createSet(int initialCapacity) {
        return new SetWithBoolArray(initialCapacity);
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
		return true;
	}
	
	/**
	 * Name.
	 *
	 * @return the string
	 */
	@Override
	public String name() {
		return "BOOL_ARRAY";
	}
    
    /**
     * Clone.
     *
     * @param b the b
     * @return the i set
     */
    @Override
    public ISet clone(ISet b) {
        BoolArray bs = (BoolArray) ((SetWithBoolArray) b).boolArray.clone();
        return new SetWithBoolArray(bs);
    }

    /**
     * The Class SetWithBoolArray.
     */
    class SetWithBoolArray extends AbstractOrderedSet {

        private BoolArray boolArray;

        /**
         * Instantiates a new sets the with bool array.
         *
         * @param maxSize the max size
         */
        SetWithBoolArray(int maxSize) {
            boolArray = new BoolArray(maxSize);
        }

        /**
         * Instantiates a new sets the with bool array.
         *
         * @param boolArray the bool array
         */
        SetWithBoolArray(BoolArray boolArray) {
            this.boolArray = boolArray;
        }
        
        /**
         * Instantiates a new sets the with bool array.
         *
         * @param bitSet the bit set
         */
        SetWithBoolArray(BitSet bitSet) {
            this(bitSet,bitSet.size());
        }
        
        /**
         * Instantiates a new sets the with bool array.
         *
         * @param bitSet the bit set
         * @param size the size
         */
        SetWithBoolArray(BitSet bitSet,int size) {
            boolArray = new BoolArray(size);
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
            boolArray.add(num);
        }

        /**
         * Adds the all.
         *
         * @param anotherSet the another set
         */
        @Override
        public void addAll(ISet anotherSet) {
            boolArray.addAll(((SetWithBoolArray) anotherSet).boolArray);
        }

        /**
         * Contains.
         *
         * @param num the num
         * @return true, if successful
         */
        @Override
        public boolean contains(int num) {
            return boolArray.contains(num);
        }

        /**
         * Contains all.
         *
         * @param anotherSet the another set
         * @return true, if successful
         */
        @Override

        public boolean containsAll(ISet anotherSet) {
        	return boolArray.containsAll(((SetWithBoolArray) anotherSet).boolArray);
        }
        
        /**
         * Capacity.
         *
         * @return the int
         */
        @Override
        public int capacity() {
            return boolArray.size();
        }

        /**
         * Cardinality.
         *
         * @return the int
         */
        @Override
        public int cardinality() {
            return boolArray.cardinality();
        }

        /**
         * Fill.
         *
         * @param size the size
         */
        @Override
        public void fill(int size) {
            boolArray.fill(size);
        }

        /**
         * Clear.
         *
         * @param size the size
         */
        @Override
        public void clear(int size) {
            boolArray.clear(size);
        }

        /**
         * Removes the all.
         *
         * @param anotherSet the another set
         */
        @Override
        public void removeAll(ISet anotherSet) {
            boolArray.andNot(((SetWithBoolArray) anotherSet).boolArray);
        }

        /**
         * Iterator.
         *
         * @return the iterator
         */
        @Override
        public Iterator<Integer> iterator() {
            return  boolArray.iterator();
    }
        
        /**
         * Checks if is empty.
         *
         * @return true, if is empty
         */
        @Override
        public boolean isEmpty() {
            return boolArray.isEmpty();
        }

        /**
         * New intersect.
         *
         * @param anotherSet the another set
         * @return the i set
         */
        @Override
        public ISet newIntersect(ISet anotherSet) {
            BoolArray bs = (BoolArray) boolArray.clone();
            bs.and(((SetWithBoolArray) anotherSet).boolArray);
            return new SetWithBoolArray(bs);
        }

        /**
         * New difference.
         *
         * @param anotherSet the another set
         * @return the i set
         */
        @Override
        public ISet newDifference(ISet anotherSet) {
            BoolArray bs = (BoolArray) boolArray.clone();
            bs.andNot(((SetWithBoolArray) anotherSet).boolArray);
            return new SetWithBoolArray(bs);
        }

        /**
         * Removes the.
         *
         * @param num the num
         */
        @Override
        public void remove(int num) {
            boolArray.remove(num);
        }

        /**
         * Retain all.
         *
         * @param anotherSet the another set
         */
        @Override
        public void retainAll(ISet anotherSet) {
            boolArray.and(((SetWithBoolArray) anotherSet).boolArray);
        }

        /**
         * First.
         *
         * @return the int
         */
        @Override
        public int first() {
        		return boolArray.first();
        }

        /**
         * Last.
         *
         * @return the int
         */
        @Override
        public int last() {
        	return boolArray.last;
        }

        /**
         * Hash code.
         *
         * @return the int
         */
        @Override
        public int hashCode() {
            return boolArray.array.hashCode();
        }

        /**
         * Equals.
         *
         * @param other the other
         * @return true, if successful
         */
        @Override
        public boolean equals(Object other) {
            try{
                BoolArray boolArray2=((SetWithBoolArray)other).boolArray;
                if(boolArray.last!=boolArray2.last)
                	return false;
                for(int i=0;i<boolArray.last;i++){
                	if(boolArray.array[i]!=boolArray2.array[i])
                		return false;
                }
                return  true;
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
