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
 *
 * @author agutierr
 */
public class FastUtilFactory <T extends IntSortedSet> extends AbstractSetFactory {
    private Supplier<T> supplier;
    private String name;
    public FastUtilFactory(Supplier<T> supplier,String name) {
        this.supplier=supplier;
        this.name=name;
    }

    @Override
    public ISet createSet() {
        return new SetWithFastUtil();
    }

    @Override
    public ISet createSet(BitSet bitset) {
        return new SetWithFastUtil(bitset);
    }

    @Override
    public ISet createSet(int initialCapacity) {
        return new SetWithFastUtil(initialCapacity);
    }

	@Override
	public boolean ordered() {
		return true;
	}
	@Override
	public boolean fixedSize() {
		return false;
	}
	@Override
	public String name() {
		return name;
	}
    @Override
    public ISet clone(ISet to_clone) {
       BitSet bs = to_clone.toBitSet();
        return new SetWithFastUtil(bs);
    }
       class SetWithFastUtil extends AbstractOrderedSet {
           private T sortedSet;
       SetWithFastUtil() {
            sortedSet = supplier.get();
        }

        SetWithFastUtil(int initialCapacity) {
            sortedSet = supplier.get();
        }

        SetWithFastUtil(T hashSet) {
            this.sortedSet = hashSet;
        }
        SetWithFastUtil(BitSet bitSet) {

            this.sortedSet =supplier.get();
            for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
                add(i);
            }
        }

        @Override
        public void add(int num) {
            sortedSet.add(num);
        }
        @Override
        public void addAll(ISet anotherSet) {
            sortedSet.addAll(((SetWithFastUtil)anotherSet).sortedSet);
        }

        @Override
        public boolean contains(int num) {
            return sortedSet.contains(num);
        }
        
        @Override
        public boolean containsAll(ISet anotherSet) {
            return sortedSet.containsAll(((SetWithFastUtil) anotherSet).sortedSet);
        }

        @Override
        public int capacity() {
            return sortedSet.size();
        }

        @Override
        public int cardinality() {
            return sortedSet.size();
        }

   public void fill(int size) {
        for (int i = 0; i < size; i++) {
            sortedSet.add(i);
        }
    }


        @Override
    public void clear(int size) {
        for (int i = 0; i < size; i++) {
            sortedSet.remove(i);
        }
    }
        @Override
        public void removeAll(ISet anotherSet) {
            sortedSet.removeAll(((SetWithFastUtil) anotherSet).sortedSet);
        }

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

        @Override
        public boolean isEmpty() {
            return sortedSet.isEmpty();
        }

        @Override
        public ISet newIntersect(ISet anotherSet) {
            T bs = supplier.get();
            bs.addAll(((SetWithFastUtil) anotherSet).sortedSet);
            bs.retainAll(((SetWithFastUtil) anotherSet).sortedSet);
            return new SetWithFastUtil(bs);
        }

        @Override
        public ISet newDifference(ISet anotherSet) {
            T bs = supplier.get();
            bs.addAll(sortedSet);
            bs.removeAll(((SetWithFastUtil) anotherSet).sortedSet);
            return new SetWithFastUtil(bs);
        }

        @Override
        public void remove(int num) {
            sortedSet.remove(num);
        }

        @Override
        public void retainAll(ISet anotherSet) {
            sortedSet.retainAll (((SetWithFastUtil)anotherSet).sortedSet);
        }
        @Override
        public int first() {
            try{
            return sortedSet.firstInt();
            }catch(NoSuchElementException ex){
                return -1;
            }
        }

        @Override
        public int hashCode() {
            return sortedSet.hashCode();
        }
    @Override
    public boolean equals(Object aSet) {
        try{
            return sortedSet.equals(((SetWithFastUtil)aSet).sortedSet);
        }catch(Exception e){
            return false;
        }
    }
        
        @Override
        public BitSet toBitSet() {
            BitSet bs=new BitSet();
            for(Iterator<Integer> it=iterator();it.hasNext();)
                bs.set(it.next());
           return bs;
        }


    }
 
}
