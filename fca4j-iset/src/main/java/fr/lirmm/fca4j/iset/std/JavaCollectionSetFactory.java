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
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import fr.lirmm.fca4j.iset.AbstractSetFactory;
import fr.lirmm.fca4j.iset.AbstractSetFactory.AbstractSet;
import fr.lirmm.fca4j.iset.ISet;

/**
 *
 * @author agutierr
 * @param <T>
 */
public class JavaCollectionSetFactory<T extends Collection<Integer>> extends AbstractSetFactory {
    private Supplier<T> supplier;
    private boolean ordered;
 
    public JavaCollectionSetFactory(Supplier<T> supplier,boolean ordered) {
        this.supplier=supplier;
        this.ordered=ordered;
    }

    @Override
    public ISet createSet() {
        return new SetWithCollection();
    }

    @Override
    public ISet createSet(BitSet bs) {
        return new SetWithCollection((BitSet) bs.clone());
    }

    @Override
    public ISet createSet(int initialCapacity) {
        return new SetWithCollection(initialCapacity);
    }

	@Override
	public boolean ordered() {
		return ordered;
	}

	@Override
	public boolean fixedSize() {
		// TODO Auto-generated method stub
		return false;
	}
	@Override
	public String name() {
		return supplier.get().getClass().getSimpleName().toUpperCase();
	}
    @Override
    public ISet clone(ISet b) {
        T clone=supplier.get();
        clone.addAll(((SetWithCollection) b).collection);
        return new SetWithCollection(clone);
    }

    class SetWithCollection extends AbstractSet {

        private T collection;

        SetWithCollection() {
            collection = supplier.get();
        }

        SetWithCollection(int initialCapacity) {
            this.collection = supplier.get();
        }

        SetWithCollection(T coll) {
            this.collection = coll;
        }

        SetWithCollection(BitSet bitSet) {

            this.collection = supplier.get();
            for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
                add(i);
            }
        }

        @Override
        public void add(int num) {
            collection.add(num);
        }

        @Override
        public void addAll(ISet anotherSet) {
        	collection.addAll(((SetWithCollection) anotherSet).collection);
        }

        @Override
        public boolean contains(int num) {
            return collection.contains(num);
        }

        @Override
        public boolean containsAll(ISet anotherSet) {
            return collection.containsAll(((SetWithCollection) anotherSet).collection);
        }

        @Override
        public int capacity() {
            return collection.size();
        }

        @Override
        public int cardinality() {
            return collection.size();
        }

        public void fill(int size) {
            for (int i = 0; i < size; i++) {
                collection.add(i);
            }
        }

        @Override
        public void clear(int size) {
            for (int i = 0; i < size; i++) {
                collection.remove(i);
            }
        }

        @Override
        public void removeAll(ISet anotherSet) {
            collection.removeAll (((SetWithCollection) anotherSet).collection);
        }

        @Override
        public Iterator<Integer> iterator() {
            return collection.iterator();
        }

        @Override
        public boolean isEmpty() {
            return collection.isEmpty();
        }

        @Override
        public ISet newIntersect(ISet anotherSet) {
            T clone=supplier.get();
            clone.addAll(collection);
            clone.retainAll (((SetWithCollection) anotherSet).collection);
            return new SetWithCollection(clone);
        }

        @Override
        public ISet newDifference(ISet anotherSet) {
            T clone=supplier.get();
            clone.addAll(collection);
            clone.removeAll (((SetWithCollection) anotherSet).collection);
            return new SetWithCollection(clone);
        }
        @Override
        public void remove(int num) {
            collection.remove(num);
        }

        @Override
        public void retainAll(ISet anotherSet) {
            collection.retainAll(((SetWithCollection) anotherSet).collection);
        }

        @Override
        public int hashCode() {
            return collection.hashCode();
        }

        @Override
        public BitSet toBitSet() {
            BitSet bs = new BitSet();
            for (Iterator<Integer> it = collection.iterator(); it.hasNext();) {
                bs.set(it.next());
            }
            return bs;
        }

        @Override
        public boolean equals(Object aSet) {
            try {
                return collection.equals(((SetWithCollection)aSet).collection);
            }catch(Exception e){
                return false;
            }
        }
        @Override
        public int first(){
            try{
            return collection.iterator().next();
            }catch(NoSuchElementException e){
                return -1;
            }
        }
    }


}
