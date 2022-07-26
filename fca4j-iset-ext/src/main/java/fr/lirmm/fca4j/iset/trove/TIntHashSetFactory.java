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
 *
 * @author agutierr
 */
public class TIntHashSetFactory extends AbstractSetFactory {

    public TIntHashSetFactory() {
    }

    @Override
    public ISet createSet() {
        return new SetWithTIntHashSet();
    }
    @Override
    public ISet createSet(BitSet bs) {
        return new SetWithTIntHashSet((BitSet)bs.clone());
    }

    @Override
    public ISet createSet(int initialCapacity) {
        return new SetWithTIntHashSet(initialCapacity);
    }

	@Override
	public boolean ordered() {
		return false;
	}
	@Override
	public boolean fixedSize() {
		return false;
	}
	@Override
	public String name() {
		return "TROVE_HASHSET";
	}
    @Override
    public ISet clone(ISet b) {
        TIntHashSet bs = new TIntHashSet(((SetWithTIntHashSet) b).hashSet);
        return new SetWithTIntHashSet(bs);
    }

    class SetWithTIntHashSet extends AbstractSet {

        private TIntHashSet hashSet;

        SetWithTIntHashSet() {
            hashSet = new TIntHashSet();
        }

        SetWithTIntHashSet(int initialCapacity) {
            hashSet = new TIntHashSet(initialCapacity);
        }

        SetWithTIntHashSet(TIntHashSet hashSet) {
            this.hashSet = hashSet;
        }
        SetWithTIntHashSet(BitSet bitSet) {

            this.hashSet = new TIntHashSet();
            for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
                add(i);
            }
        }

        @Override
        public void add(int num) {
            hashSet.add(num);
        }
        @Override
        public void addAll(ISet anotherSet) {
            hashSet.addAll (((SetWithTIntHashSet)anotherSet).hashSet);
        }

        @Override
        public boolean contains(int num) {
            return hashSet.contains(num);
        }
        
        @Override
        public boolean containsAll(ISet anotherSet) {
            return hashSet.containsAll(((SetWithTIntHashSet) anotherSet).hashSet);
        }

        @Override
        public int capacity() {
            return hashSet.size();
        }

        @Override
        public int cardinality() {
            return hashSet.size();
        }

   public void fill(int size) {
        for (int i = 0; i < size; i++) {
            hashSet.add(i);
        }
    }


        @Override
    public void clear(int size) {
        for (int i = 0; i < size; i++) {
            hashSet.remove(i);
        }
    }
        @Override
        public void removeAll(ISet anotherSet) {
            hashSet.removeAll(((SetWithTIntHashSet) anotherSet).hashSet);
        }

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

        @Override
        public boolean isEmpty() {
            return hashSet.isEmpty();
        }

        @Override
        public ISet newIntersect(ISet anotherSet) {
            TIntHashSet bs = new TIntHashSet(hashSet);
            bs.retainAll(((SetWithTIntHashSet) anotherSet).hashSet);
            return new SetWithTIntHashSet(bs);
        }

        @Override
        public ISet newDifference(ISet anotherSet) {
            TIntHashSet bs = new TIntHashSet(hashSet);
            bs.removeAll(((SetWithTIntHashSet) anotherSet).hashSet);
            return new SetWithTIntHashSet(bs);
        }

        @Override
        public void remove(int num) {
            hashSet.remove(num);
        }

        @Override
        public void retainAll(ISet anotherSet) {
            hashSet.retainAll (((SetWithTIntHashSet)anotherSet).hashSet);
        }

        @Override
        public int first() {
            try{
            return hashSet.iterator().next();
            }catch(NoSuchElementException ex){
                return -1;
            }
        }

        @Override
        public int hashCode() {
            return hashSet.hashCode();
        }
    @Override
    public boolean equals(Object aSet) {
        try{
            return hashSet.equals(((SetWithTIntHashSet)aSet).hashSet);
        }catch(Exception e){
            return false;
        }
    }
        

    }

}
