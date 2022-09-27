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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Iterator;

import fr.lirmm.fca4j.iset.AbstractSetFactory;
import fr.lirmm.fca4j.iset.ISet;

/**
 * A factory for creating ArrayListSet objects.
 *
 * @author agutierr
 */
public class ArrayListSetFactory extends AbstractSetFactory {

    /**
     * Instantiates a new array list set factory.
     */
    public ArrayListSetFactory() {
    }

    /**
     * Creates a new ArrayListSet object.
     *
     * @return the new set
     */
    @Override
    public ISet createSet() {
        return new SetWithArrayList();
    }

    /**
     * Creates a new ArrayListSet object.
     *
     * @param bs the bitset
     * @return the new set
     */
    @Override
    public ISet createSet(BitSet bs) {
        return new SetWithArrayList((BitSet) bs.clone());
    }

    /**
     * Creates a new ArrayListSet object.
     *
     * @param initialCapacity the initial capacity
     * @return the new set
     */
    @Override
    public ISet createSet(int initialCapacity) {
        return new SetWithArrayList(initialCapacity);
    }

	/**
	 * Ordered.
	 *
	 * @return true, if ordered
	 */
	@Override
	public boolean ordered() {
		return true;
	}
	
	/**
	 * Fixed size.
	 *
	 * @return true, if size is fixed
	 */
	@Override
	public boolean fixedSize() {
		return false;
	}
	
	/**
	 * Name.
	 *
	 * @return the name
	 */
	@Override
	public String name() {
		return "ARRAYLIST";
	}
    
    /**
     * Clone.
     *
     * @param b the set to clone
     * @return the cloned set
     */
    @Override
    public ISet clone(ISet b) {
        ArrayList<Integer> clone = new ArrayList<>();
        clone.addAll(((SetWithArrayList) b).collection);
        return new SetWithArrayList(clone);
    }

    /**
     * The Class SetWithArrayList.
     */
    class SetWithArrayList extends AbstractOrderedSet {

        /** The collection. */
        private ArrayList<Integer> collection;

        /**
         * Instantiates a new set.
         */
        SetWithArrayList() {
            collection = new ArrayList<>();
        }

        /**
         * Instantiates a new set.
         *
         * @param initialCapacity the initial capacity
         */
        SetWithArrayList(int initialCapacity) {
            collection = new ArrayList<>(initialCapacity);
        }

        /**
         * Instantiates a new set the with arraylist.
         *
         * @param coll the coll
         */
        SetWithArrayList(ArrayList<Integer> coll) {
            this.collection = coll;
            Collections.sort(collection);
        }

        /**
         * Instantiates a new set from a BitSet.
         *
         * @param bitSet the bit set
         */
        SetWithArrayList(BitSet bitSet) {

            this.collection = new ArrayList<>(bitSet.cardinality());
            for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
                add(i);
            }
        }

        /**
         * Adds element.
         *
         * @param num the element
         */
        @Override
        public void add(int num) {
            int index = Collections.binarySearch(collection, num);
            if (index < 0) {
                collection.add(-index - 1, num);
            }
        }
        
        /**
         * Adds all elements.
         *
         * @param anotherSet the set to add
         */
        @Override
        public void addAll(ISet anotherSet) {
            for (Integer toAdd : ((SetWithArrayList) anotherSet).collection) {
                int index = Collections.binarySearch(collection, toAdd);
                if (index < 0) {
                    collection.add(-index - 1, toAdd);
                }
            }
        }

        /**
         * Contains.
         *
         * @param num the element
         * @return true, if element is found
         */
        @Override
        public boolean contains(int num) {
            return collection.contains(num);
        }

        /**
         * Contains all.
         *
         * @param anotherSet the set
         * @return true, if all elements of the set are contained
         */
        @Override
        public boolean containsAll(ISet anotherSet) {
            ArrayList<Integer> collection2 = ((SetWithArrayList) anotherSet).collection;
            int index1 = 0, index2 = 0;
            while(collection2.size() != index2&&collection.size() != index1) {                
                    int comp=Integer.compare(collection.get(index1), collection2.get(index2)) ;
                    if(comp==0){
                        index1++;
                        index2++;
                    }
                    else if(comp<0){
                        index1++;
                    }
                    else return false;
            }
            return (collection2.size() == index2);
        }

        /**
         * Capacity.
         *
         * @return the capacity of the set
         */
        @Override
        public int capacity() {
            return collection.size();
        }

        /**
         * Cardinality.
         *
         * @return the cardinality of the set
         */
        @Override
        public int cardinality() {
            return collection.size();
        }

        /**
         * Fill.
         *
         * @param size set from 0 to size
         */
        public void fill(int size) {
            for (int i = 0; i < size; i++) {
                collection.add(i);
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
                collection.remove(i);
            }
        }

        /**
         * Removes all elements.
         *
         * @param anotherSet the set
         */
        @Override
        public void removeAll(ISet anotherSet) {
            collection.removeAll(((SetWithArrayList) anotherSet).collection);
        }

        /**
         * Iterator.
         *
         * @return the iterator
         */
        @Override
        public Iterator<Integer> iterator() {
            return collection.iterator();
        }

        /**
         * Checks if is empty.
         *
         * @return true, if is empty
         */
        @Override
        public boolean isEmpty() {
            return collection.isEmpty();
        }
        
        /**
         * And.
         *
         * @param set1 the modified set 
         * @param set2 the set to intersect
         */
        private void and(ArrayList<Integer> set1, ArrayList<Integer> set2) {
            int count=0;
            for(int index2=0,index1=0;index2<set2.size()&& index1<set1.size();)
            {
                int comp=Integer.compare(set1.get(index1), set2.get(index2));
                if(comp==0){
                    set1.set(count++, set2.get(index2));
                    index1++;
                    index2++;
                }else if(comp<0){
                    index1++;
                }else{
                    index2++;
                }
            }
            for(int i=set1.size()-1;i>=count;i--)
                set1.remove(i);
        }

        /**
         * New intersect.
         *
         * @param anotherSet the another set
         * @return a new set containing the intersection
         */
        @Override
        public ISet newIntersect(ISet anotherSet) {
            ArrayList<Integer> clone = new ArrayList<>(collection.size());
            clone.addAll(collection);
            and(clone, ((SetWithArrayList) anotherSet).collection);
            return new SetWithArrayList(clone);
        }

        /**
         * New difference.
         *
         * @param anotherSet the set to diff
         * @return a new set containing result
         */
        @Override
        public ISet newDifference(ISet anotherSet) {
            ArrayList<Integer> clone = new ArrayList<>(collection.size());
            clone.addAll(collection);
            clone.removeAll(((SetWithArrayList) anotherSet).collection);
            return new SetWithArrayList(clone);
        }

        /**
         * Removes element.
         *
         * @param num the element
         */
        @Override
        public void remove(int num) {
            collection.remove((Integer) num);
        }

        /**
         * Retain all.
         *
         * @param anotherSet the set to intersect
         */
        @Override
        public void retainAll(ISet anotherSet) {
            and(collection, ((SetWithArrayList) anotherSet).collection);
        }

        /**
         * Hash code.
         *
         * @return the int
         */
        @Override
        public int hashCode() {
            return collection.hashCode();
        }

        /**
         * build the BitSet representation of the set
         *
         * @return the bitset
         */
        @Override
        public BitSet toBitSet() {
            BitSet bs = new BitSet();
            for (Iterator<Integer> it = collection.iterator(); it.hasNext();) {
                bs.set(it.next());
            }
            return bs;
        }

        /**
         * Equals.
         *
         * @param aSet the set to compare with
         * @return true, if sets are equals
         */
        @Override
        public boolean equals(Object aSet) {
            try {
                return collection.equals(((SetWithArrayList) aSet).collection);
            } catch (Exception e) {
                return false;
            }
        }

        /**
         * First.
         *
         * @return the first element
         */
        @Override
        public int first() {
            if (collection.isEmpty()) {
                return -1;
            } else {
                return collection.get(0);
            }
        }

        /**
         * Last.
         *
         * @return the last element
         */
        @Override
        public int last() {
            if (collection.isEmpty()) {
                return -1;
            } else {
                return collection.get(collection.size() - 1);
            }

        }
    }

}
