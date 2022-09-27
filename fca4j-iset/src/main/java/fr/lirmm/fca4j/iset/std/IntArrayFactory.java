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
 * A factory for creating sets based on ordered arrays of int.
 *
 * @author agutierr
 */
public class IntArrayFactory extends AbstractSetFactory {
    
    /**
     * Instantiates a new int array factory.
     */
    public IntArrayFactory() {
    }

    /**
     * Creates a new IntArray object.
     *
     * @return the i set
     */
    @Override
    public ISet createSet(){
        throw new UnsupportedOperationException("this kind of set has a fixed size");
    }

    /**
     * Creates a new IntArray object.
     *
     * @param bitset the bitset
     * @return the i set
     */
    @Override
    public ISet createSet(BitSet bitset) {
        return new SetWithIntArray(bitset);
    }
    
    /**
     * Creates a new IntArray object.
     *
     * @param bs the bs
     * @param size the size
     * @return the i set
     */
    @Override
    public ISet createSet(BitSet bs,int size) {
        return new SetWithIntArray(bs,size);
    }

    /**
     * Creates a new IntArray object.
     *
     * @param maxSize the max size
     * @return the i set
     */
    @Override
    public ISet createSet(int maxSize) {
        return new SetWithIntArray(maxSize);
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
		return "INT_ARRAY";
	}
    
    /**
     * Clone.
     *
     * @param to_clone the to clone
     * @return the i set
     */
    @Override
    public ISet clone(ISet to_clone) {
        FixedIntArray bs = ((SetWithIntArray) to_clone).fiArray.clone();
        return new SetWithIntArray(bs);
        
    }
    
    /**
     * The Class SetWithIntArray.
     */
    class SetWithIntArray extends AbstractOrderedSet{
        
        /** The fi array. */
        FixedIntArray fiArray;
        
        /**
         * Instantiates a new sets the with int array.
         *
         * @param maxSize the max size
         */
        SetWithIntArray(int maxSize) {
            fiArray = new FixedIntArray(maxSize);
        }
        
        /**
         * Instantiates a new sets the with int array.
         *
         * @param fiArray the fi array
         */
        SetWithIntArray(FixedIntArray fiArray) {
            this.fiArray = fiArray;
        }

        /**
         * Instantiates a new sets the with int array.
         *
         * @param bitSet the bit set
         */
        SetWithIntArray(BitSet bitSet) {
            this(bitSet,bitSet.cardinality());
        }
        
        /**
         * Instantiates a new sets the with int array.
         *
         * @param bitSet the bit set
         * @param size the size
         */
        SetWithIntArray(BitSet bitSet,int size) {
            fiArray = new FixedIntArray(size);
            for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
                add(i);
            }
        }
        private boolean equals(int[]a,int[]b,int count){
            for(int i=0;i<count;i++)
                if(a[i]!=b[i]) return false;
            return true;
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
                FixedIntArray fiArray2=((SetWithIntArray)other).fiArray;
                return fiArray.count==fiArray2.count  && equals(fiArray.array,fiArray2.array,fiArray.count);
            }catch(Exception e){
                return false;
            }
        }

        /**
         * Adds the.
         *
         * @param num the num
         */
        @Override
        public void add(int num) {
           fiArray.add(num);
        }

        /**
         * Adds the all.
         *
         * @param anotherSet the another set
         */
        @Override
        public void addAll(ISet anotherSet) {
            fiArray.addAll(((SetWithIntArray)anotherSet).fiArray);
        }

        /**
         * Contains.
         *
         * @param num the num
         * @return true, if successful
         */
        @Override
        public boolean contains(int num) {
            return fiArray.contains(num);
        }

        /**
         * Contains all.
         *
         * @param anotherSet the another set
         * @return true, if successful
         */
        @Override
         public boolean containsAll(ISet anotherSet) {
            FixedIntArray fiArray2=((SetWithIntArray)anotherSet).fiArray;
            int startIndex=0;
            for(int i=0;i<fiArray2.count;i++)
            {
                int pos=fiArray.getPosition(fiArray2.array[i],startIndex);
                if(pos<0)return false;
                else startIndex=pos+1;
            }
            return true;
        }
       
       /**
        * Contains all 2.
        *
        * @param anotherSet the another set
        * @return true, if successful
        */
       public boolean containsAll2(ISet anotherSet) {
            FixedIntArray fiArray2=((SetWithIntArray)anotherSet).fiArray;
            boolean ret=fiArray2.isSubsetOf(fiArray);
            return ret;
        }

        /**
         * Capacity.
         *
         * @return the int
         */
        @Override
        public int capacity() {
            return fiArray.array.length;
        }

        /**
         * Cardinality.
         *
         * @return the int
         */
        @Override
        public int cardinality() {
            return fiArray.getCount();
        }

        /**
         * Fill.
         *
         * @param size the size
         */
        @Override
        public void fill(int size) {
            fiArray.fill(size);
        }

        /**
         * Clear.
         *
         * @param size the size
         */
        @Override
        public void clear(int size) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        /**
         * Removes the all.
         *
         * @param anotherSet the another set
         */
        @Override
        public void removeAll(ISet anotherSet) {
            fiArray.clear();
        }

        /**
         * Iterator.
         *
         * @return the iterator
         */
        @Override
        public Iterator<Integer> iterator() {
            return fiArray.iterator();
        }

        /**
         * Checks if is empty.
         *
         * @return true, if is empty
         */
        @Override
        public boolean isEmpty() {
            return fiArray.getCount()==0;
        }

        /**
         * New intersect.
         *
         * @param anotherSet the another set
         * @return the i set
         */
        @Override
        public ISet newIntersect(ISet anotherSet) {
            FixedIntArray clone=fiArray.clone();
            clone.and(((SetWithIntArray) anotherSet).fiArray);
            return new SetWithIntArray(clone);
        }

        /**
         * New difference.
         *
         * @param anotherSet the another set
         * @return the i set
         */
        @Override
        public ISet newDifference(ISet anotherSet) {
            FixedIntArray clone=fiArray.clone();
            clone.andNot(((SetWithIntArray) anotherSet).fiArray);
            return new SetWithIntArray(clone);
        }

        /**
         * Removes the.
         *
         * @param num the num
         */
        @Override
        public void remove(int num) {
            fiArray.remove(num);
        }

        /**
         * Retain all.
         *
         * @param anotherSet the another set
         */
        @Override
        public void retainAll(ISet anotherSet) {
            fiArray.and(((SetWithIntArray) anotherSet).fiArray);            
        }
        
        /**
         * Hash code.
         *
         * @return the int
         */
        @Override
        public int hashCode() {
            return fiArray.array.hashCode();
        }

        /**
         * First.
         *
         * @return the int
         */
        @Override
        public int first() {
            return fiArray.first();
        }
        
        /**
         * Last.
         *
         * @return the int
         */
        @Override
        public int last() {
            return fiArray.last();
        }
        
    }
}
