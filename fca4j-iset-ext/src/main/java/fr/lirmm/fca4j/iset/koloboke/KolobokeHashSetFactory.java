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
 *
 * @author agutierr
 */
public class KolobokeHashSetFactory extends AbstractSetFactory {

    public KolobokeHashSetFactory() {
    }
    private static synchronized HashIntSet createHashIntSet(){
        return HashIntSets.newMutableSet();
    }
    private static synchronized HashIntSet createHashIntSet(HashIntSet h,boolean mutable){
        if(mutable) return HashIntSets.newMutableSet(h);
        else return HashIntSets.newImmutableSet(h);
    }
    @Override
    public ISet createSet() {
        return new SetWithKolobokeHashSet();
    }
    @Override
    public ISet createSet(BitSet bs) {
        return new SetWithKolobokeHashSet((BitSet)bs.clone());
    }

    @Override
    public ISet createSet(int initialCapacity) {
        return new SetWithKolobokeHashSet(initialCapacity);
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
		return "KOLOBOKE_HASHSET";
	}
    @Override
    public ISet clone(ISet b) {
        HashIntSet bs = createHashIntSet(((SetWithKolobokeHashSet) b).hashSet,true);
        return new SetWithKolobokeHashSet(bs);
    }
    public ISet cloneImmutable(ISet b) {
        HashIntSet bs = createHashIntSet(((SetWithKolobokeHashSet) b).hashSet,false);
        return new SetWithKolobokeHashSet(bs);
    }

    class SetWithKolobokeHashSet extends AbstractSet {

        private HashIntSet hashSet;

        SetWithKolobokeHashSet() {
            hashSet = createHashIntSet();
        }

        SetWithKolobokeHashSet(int initialCapacity) {
            hashSet = createHashIntSet();
        }

        SetWithKolobokeHashSet(HashIntSet hashSet) {
            this.hashSet = hashSet;
        }
        SetWithKolobokeHashSet(BitSet bitSet) {

            this.hashSet = createHashIntSet();
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
            hashSet.addAll(((SetWithKolobokeHashSet)anotherSet).hashSet);
        }

        @Override
        public boolean contains(int num) {
            return hashSet.contains(num);
        }
        
        @Override
        public boolean containsAll(ISet anotherSet) {
            return hashSet.containsAll(((SetWithKolobokeHashSet) anotherSet).hashSet);
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
            hashSet.removeAll(((SetWithKolobokeHashSet) anotherSet).hashSet);
        }

	public Iterator<Integer> iterator() {
            return hashSet.iterator();
	}

        @Override
        public boolean isEmpty() {
            return hashSet.isEmpty();
        }

        @Override
        public ISet newIntersect(ISet anotherSet) {
            HashIntSet bs = createHashIntSet(hashSet,true);
            bs.retainAll(((SetWithKolobokeHashSet) anotherSet).hashSet);
            return new SetWithKolobokeHashSet(bs);
        }

        @Override
        public ISet newDifference(ISet anotherSet) {
            HashIntSet bs = createHashIntSet(hashSet,true);
            bs.removeAll(((SetWithKolobokeHashSet) anotherSet).hashSet);
            return new SetWithKolobokeHashSet(bs);
        }

        @Override
        public void remove(int num) {
            hashSet.remove(num);
        }

        @Override
        public void retainAll(ISet anotherSet) {
            hashSet.retainAll(((SetWithKolobokeHashSet)anotherSet).hashSet);
        }

        @Override
        public int hashCode() {
            return hashSet.hashCode();
        }
    @Override
    public boolean equals(Object aSet) {
        try{
            return hashSet.equals(((SetWithKolobokeHashSet)aSet).hashSet);
        }catch(Exception e){
            return false;
        }
    }
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
